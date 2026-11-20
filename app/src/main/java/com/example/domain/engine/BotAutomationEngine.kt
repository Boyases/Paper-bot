package com.example.domain.engine

import com.example.data.model.BotStatus
import com.example.data.model.BotStrategy
import com.example.data.model.MarketAsset
import com.example.data.model.RiskProfile
import com.example.data.model.StrategyCombineMode
import com.example.data.model.TradingBot
import kotlin.math.abs
import kotlin.math.pow

sealed interface EngineDecision {
    data class NoAction(val updatedPeakPct: Double? = null) : EngineDecision
    data class OpenPosition(
        val side: String,
        val price: Double,
        val quantity: Double,
        val notionalUsd: Double,
        val feeUsd: Double,
        val reason: String
    ) : EngineDecision
    data class ClosePosition(
        val orderType: String,
        val side: String,
        val price: Double,
        val quantity: Double,
        val notionalUsd: Double,
        val feeUsd: Double,
        val pnlUsd: Double,
        val isWin: Boolean,
        val reason: String
    ) : EngineDecision
    data class HaltBotForRisk(val reason: String) : EngineDecision
    data class TripGlobalCircuitBreaker(val reason: String) : EngineDecision
}

object BotAutomationEngine {

    private const val FEE_RATE = 0.0008 // 8 bps taker fee
    private const val HIGH_VOLATILITY_THRESHOLD_PCT = 6.5

    fun evaluate(
        bot: TradingBot,
        asset: MarketAsset,
        riskProfile: RiskProfile,
        openPositionsCount: Int,
        todayRealizedPnlUsd: Double,
        totalUnrealizedPnlUsd: Double,
        lastBotTradeEpochMs: Long,
        nowEpochMs: Long,
        consecutiveLosses: Int = 0
    ): EngineDecision {
        if (bot.status != BotStatus.RUNNING.id) return EngineDecision.NoAction()
        if (asset.price <= 0.0) return EngineDecision.NoAction()

        // 1. Global Circuit Breaker & Daily Loss Guardrail
        if (riskProfile.circuitBreakerTriggered) {
            return EngineDecision.HaltBotForRisk("Global circuit breaker active")
        }

        val netDailyPnl = todayRealizedPnlUsd + totalUnrealizedPnlUsd
        if (netDailyPnl <= -riskProfile.maxDailyLossUsd) {
            return EngineDecision.TripGlobalCircuitBreaker(
                "Daily loss limit -$${"%.2f".format(riskProfile.maxDailyLossUsd)} breached (net $${"%.2f".format(netDailyPnl)})"
            )
        }

        // 2. Leverage Cap Enforcement
        if (bot.leverage > riskProfile.maxLeverageCap) {
            return EngineDecision.HaltBotForRisk(
                "Bot leverage ${bot.leverage}x exceeds max cap ${riskProfile.maxLeverageCap}x"
            )
        }

        // 3. Volatility Guardrail
        if (riskProfile.autoPauseOnHighVolatility && asset.indicators.volatilityPct >= HIGH_VOLATILITY_THRESHOLD_PCT) {
            return EngineDecision.HaltBotForRisk(
                "Extreme volatility (${"%.2f".format(asset.indicators.volatilityPct)}%) paused bot"
            )
        }

        val indicators = asset.indicators
        val price = asset.price

        // 4. Manage Open Position -> Check Stop-Loss, Take-Profit, Trailing Stop, and Strategy Exit
        if (bot.openPositionSide != "NONE" && bot.openQuantity > 0.0 && bot.openEntryPrice > 0.0) {
            val unrealizedPct = bot.unrealizedPnlPct(price)
            val unrealizedUsd = bot.unrealizedPnlUsd(price)
            val notional = bot.openQuantity * price
            val fee = notional * FEE_RATE
            val netPnl = unrealizedUsd - fee
            val newPeak = maxOf(bot.peakUnrealizedPct, unrealizedPct)

            // Stop-Loss Check
            if (unrealizedPct <= -bot.stopLossPct) {
                return EngineDecision.ClosePosition(
                    orderType = "STOP_LOSS",
                    side = "SELL",
                    price = price,
                    quantity = bot.openQuantity,
                    notionalUsd = notional,
                    feeUsd = fee,
                    pnlUsd = netPnl,
                    isWin = netPnl > 0.0,
                    reason = "Stop-Loss triggered at ${"%.2f".format(unrealizedPct)}% (limit -${bot.stopLossPct}%)"
                )
            }

            // Take-Profit Check
            if (unrealizedPct >= bot.takeProfitPct) {
                return EngineDecision.ClosePosition(
                    orderType = "TAKE_PROFIT",
                    side = "SELL",
                    price = price,
                    quantity = bot.openQuantity,
                    notionalUsd = notional,
                    feeUsd = fee,
                    pnlUsd = netPnl,
                    isWin = netPnl > 0.0,
                    reason = "Take-Profit target hit at +${"%.2f".format(unrealizedPct)}%"
                )
            }

            // Trailing Stop Check
            if (bot.trailingStopPct > 0.0 && newPeak >= 0.6 && (newPeak - unrealizedPct) >= bot.trailingStopPct) {
                return EngineDecision.ClosePosition(
                    orderType = "TRAILING_STOP",
                    side = "SELL",
                    price = price,
                    quantity = bot.openQuantity,
                    notionalUsd = notional,
                    feeUsd = fee,
                    pnlUsd = netPnl,
                    isWin = netPnl > 0.0,
                    reason = "Trailing stop from peak +${"%.2f".format(newPeak)}% to +${"%.2f".format(unrealizedPct)}%"
                )
            }

            // Strategy Exit Signal across all active strategies on the bot
            val activeStrategies = bot.effectiveStrategies()
            val exitMatches = activeStrategies.mapNotNull { strat ->
                evaluateSingleExitSignal(strat, bot, price, indicators, unrealizedPct)?.let { strat to it }
            }
            if (exitMatches.isNotEmpty()) {
                val isGridFill = exitMatches.any { it.first == BotStrategy.GRID }
                val combinedReason = if (exitMatches.size == 1) {
                    exitMatches.first().second
                } else {
                    exitMatches.joinToString(" + ") { it.second }.take(155)
                }
                return EngineDecision.ClosePosition(
                    orderType = if (isGridFill) "GRID_FILL" else "MARKET",
                    side = "SELL",
                    price = price,
                    quantity = bot.openQuantity,
                    notionalUsd = notional,
                    feeUsd = fee,
                    pnlUsd = netPnl,
                    isWin = netPnl > 0.0,
                    reason = combinedReason
                )
            }

            return if (newPeak > bot.peakUnrealizedPct + 0.05) {
                EngineDecision.NoAction(updatedPeakPct = newPeak)
            } else {
                EngineDecision.NoAction()
            }
        }

        // 5. No Open Position -> Enforce Concurrency & Cooldown before Strategy Entry
        if (openPositionsCount >= riskProfile.maxOpenPositions) {
            return EngineDecision.NoAction()
        }

        val cooldownMs = riskProfile.cooldownMinutes * 60_000L
        if (lastBotTradeEpochMs > 0L && (nowEpochMs - lastBotTradeEpochMs) < cooldownMs) {
            return EngineDecision.NoAction()
        }

        val activeStrategies = bot.effectiveStrategies()
        val entryReason = evaluateEntrySignal(bot, asset, consecutiveLosses)
        if (entryReason != null) {
            val baseBudget = bot.allocatedCapitalUsd * (bot.maxPositionPct / 100.0)
            val scaledBudget = if (BotStrategy.MARTINGALE in activeStrategies) {
                baseBudget * 2.0.pow(consecutiveLosses.coerceIn(0, 3))
            } else {
                baseBudget
            }
            val positionBudget = scaledBudget.coerceIn(10.0, bot.allocatedCapitalUsd)
            val qty = (positionBudget * bot.leverage) / price
            val notional = qty * price
            val fee = notional * FEE_RATE
            return EngineDecision.OpenPosition(
                side = "BUY",
                price = price,
                quantity = qty,
                notionalUsd = notional,
                feeUsd = fee,
                reason = entryReason
            )
        }

        return EngineDecision.NoAction()
    }

    private fun evaluateSingleExitSignal(
        strategy: BotStrategy,
        bot: TradingBot,
        price: Double,
        indicators: com.example.data.model.IndicatorSnapshot,
        unrealizedPct: Double
    ): String? = when (strategy) {
        BotStrategy.EMA_CROSS ->
            if (indicators.ema9 < indicators.ema21 && unrealizedPct > 0.3) {
                "Bearish EMA(9)<EMA(21) exit lock"
            } else null
        BotStrategy.RSI_REVERSION ->
            if (indicators.rsi14 >= bot.rsiOverbought.toDouble()) {
                "RSI(${"%.1f".format(indicators.rsi14)}) reached overbought >=${bot.rsiOverbought}"
            } else null
        BotStrategy.BOLLINGER ->
            if (indicators.bbUpper > 0.0 && price >= indicators.bbUpper && unrealizedPct > 0.2) {
                "Price tagged upper Bollinger Band"
            } else null
        BotStrategy.GRID ->
            if (unrealizedPct >= (bot.takeProfitPct / bot.gridLevels.coerceAtLeast(2).toDouble()).coerceAtLeast(0.6)) {
                "Grid Bot level profit fill"
            } else null
        BotStrategy.MARTINGALE ->
            if (unrealizedPct >= (bot.takeProfitPct * 0.6).coerceAtLeast(1.0)) {
                "Martingale recovery profit target hit"
            } else null
        BotStrategy.TRADINGVIEW_21 ->
            if (indicators.ema21 > 0.0 && price < indicators.ema21 && unrealizedPct > 0.15) {
                "TV 21-EMA trend breakdown exit"
            } else null
        BotStrategy.DCA ->
            if (unrealizedPct >= (bot.takeProfitPct * 0.5)) {
                "DCA cycle profit capture"
            } else null
    }

    fun evaluateEntrySignal(
        bot: TradingBot,
        asset: MarketAsset,
        consecutiveLosses: Int = 0
    ): String? {
        val activeStrategies = bot.effectiveStrategies()
        val matched = activeStrategies.mapNotNull { strat ->
            evaluateSingleEntrySignal(strat, bot, asset, consecutiveLosses)?.let { strat to it }
        }
        if (matched.isEmpty()) return null

        val combineMode = StrategyCombineMode.fromId(bot.strategyMode)
        if (combineMode == StrategyCombineMode.ALL && matched.size < activeStrategies.size) {
            return null
        }

        return if (activeStrategies.size == 1) {
            matched.first().second
        } else {
            "[${matched.size}/${activeStrategies.size}] ${matched.joinToString(" + ") { it.second }}".take(155)
        }
    }

    private fun evaluateSingleEntrySignal(
        strategy: BotStrategy,
        bot: TradingBot,
        asset: MarketAsset,
        consecutiveLosses: Int
    ): String? {
        val ind = asset.indicators
        val price = asset.price
        return when (strategy) {
            BotStrategy.EMA_CROSS -> {
                if (ind.ema9 > ind.ema21 && ind.rsi14 in 38.0..72.0) {
                    "EMA(9) > EMA(21) bullish trend (RSI ${"%.1f".format(ind.rsi14)})"
                } else null
            }
            BotStrategy.RSI_REVERSION -> {
                if (ind.rsi14 <= bot.rsiOversold.toDouble()) {
                    "RSI(14)=${"%.1f".format(ind.rsi14)} <= oversold ${bot.rsiOversold}"
                } else null
            }
            BotStrategy.GRID -> {
                if (ind.bbUpper > ind.bbLower && price <= ind.bbMiddle) {
                    "Grid Bot buy zone active below mid-band (${"%.2f".format(ind.bbMiddle)})"
                } else null
            }
            BotStrategy.MARTINGALE -> {
                if (ind.rsi14 <= 46.0 || (ind.bbMiddle > 0.0 && price <= ind.bbMiddle) || asset.change24hPct <= -0.25) {
                    val step = consecutiveLosses.coerceIn(0, 3)
                    val mult = 2.0.pow(step)
                    "Martingale ${"%.0f".format(mult)}x recovery entry (step ${step + 1}, RSI ${"%.1f".format(ind.rsi14)})"
                } else null
            }
            BotStrategy.TRADINGVIEW_21 -> {
                if (ind.ema21 > 0.0 && price > ind.ema21 && ind.ema9 >= ind.ema21 * 0.999 && ind.rsi14 in 45.0..70.0) {
                    "TV 21-EMA bullish confluence (Price > EMA21, RSI ${"%.1f".format(ind.rsi14)})"
                } else null
            }
            BotStrategy.DCA -> {
                if (price <= ind.bbMiddle || asset.change24hPct <= -0.4) {
                    "DCA dip accumulation triggered (${"%.2f".format(asset.change24hPct)}% 24h)"
                } else null
            }
            BotStrategy.BOLLINGER -> {
                if (ind.bbLower > 0.0 && (price <= ind.bbLower * 1.003 || (price > ind.bbMiddle && ind.ema9 > ind.ema21))) {
                    "Bollinger band expansion entry (vol ${"%.2f".format(ind.volatilityPct)}%)"
                } else null
            }
        }
    }

    fun computePortfolioMetrics(
        bots: List<TradingBot>,
        trades: List<com.example.data.model.TradeExecution>,
        assetMap: Map<String, MarketAsset>
    ): PortfolioAnalytics {
        val totalAllocated = bots.sumOf { it.allocatedCapitalUsd }
        val totalRealized = bots.sumOf { it.realizedPnlUsd }
        val totalUnrealized = bots.sumOf { bot ->
            val asset = findMatchingAsset(bot, assetMap)
            if (asset != null) bot.unrealizedPnlUsd(asset.price) else 0.0
        }
        val netEquity = totalAllocated + totalRealized + totalUnrealized
        val totalTradesCount = bots.sumOf { it.totalTrades }
        val totalWins = bots.sumOf { it.winningTrades }
        val winRate = if (totalTradesCount > 0L) (totalWins.toDouble() / totalTradesCount.toDouble()) * 100.0 else 0.0

        val grossProfit = trades.filter { it.pnlUsd > 0.0 }.sumOf { it.pnlUsd }
        val grossLoss = abs(trades.filter { it.pnlUsd < 0.0 }.sumOf { it.pnlUsd })
        val profitFactor = when {
            grossLoss > 0.0 -> grossProfit / grossLoss
            grossProfit > 0.0 -> 9.99
            else -> 0.0
        }

        // Compute max drawdown from cumulative realized curve
        var peak = totalAllocated.coerceAtLeast(1.0)
        var equity = totalAllocated
        var maxDrawdownPct = 0.0
        trades.asReversed().forEach { trade ->
            equity += trade.pnlUsd
            if (equity > peak) peak = equity
            val dd = ((peak - equity) / peak) * 100.0
            if (dd > maxDrawdownPct) maxDrawdownPct = dd
        }

        val equitySeries = buildList {
            var running = totalAllocated.coerceAtLeast(1000.0)
            add(running)
            trades.asReversed().forEach { tr ->
                running += tr.pnlUsd
                add(running)
            }
            if (totalUnrealized != 0.0) {
                add(running + totalUnrealized)
            }
        }

        return PortfolioAnalytics(
            netEquityUsd = netEquity,
            allocatedCapitalUsd = totalAllocated,
            realizedPnlUsd = totalRealized,
            unrealizedPnlUsd = totalUnrealized,
            winRatePct = winRate,
            profitFactor = profitFactor,
            maxDrawdownPct = maxDrawdownPct,
            activeBotsCount = bots.count { it.status == BotStatus.RUNNING.id },
            openPositionsCount = bots.count { it.openPositionSide != "NONE" },
            equityCurve = equitySeries
        )
    }

    fun findMatchingAsset(bot: TradingBot, assetMap: Map<String, MarketAsset>): MarketAsset? {
        val exactKey = "${bot.exchange}:${bot.symbol}"
        return assetMap[exactKey]
            ?: assetMap.values.firstOrNull { it.symbol.equals(bot.symbol, ignoreCase = true) }
    }
}

data class PortfolioAnalytics(
    val netEquityUsd: Double = 0.0,
    val allocatedCapitalUsd: Double = 0.0,
    val realizedPnlUsd: Double = 0.0,
    val unrealizedPnlUsd: Double = 0.0,
    val winRatePct: Double = 0.0,
    val profitFactor: Double = 0.0,
    val maxDrawdownPct: Double = 0.0,
    val activeBotsCount: Int = 0,
    val openPositionsCount: Int = 0,
    val equityCurve: List<Double> = emptyList()
)
