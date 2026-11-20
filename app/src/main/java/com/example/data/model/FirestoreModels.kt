package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

@Keep
@IgnoreExtraProperties
data class TradingBot(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val assetClass: String = AssetClass.CRYPTO.id,
    val exchange: String = ExchangeVenue.BINANCE.id,
    val symbol: String = "BTC/USDT",
    val strategy: String = BotStrategy.EMA_CROSS.id,
    val strategies: List<String> = emptyList(),
    val strategyMode: String = StrategyCombineMode.ANY.id,
    val executionMode: String = ExecutionMode.PAPER.id,
    val status: String = BotStatus.RUNNING.id,
    val allocatedCapitalUsd: Double = 2500.0,
    val leverage: Double = 1.0,
    val stopLossPct: Double = 2.5,
    val takeProfitPct: Double = 6.0,
    val trailingStopPct: Double = 1.5,
    val maxPositionPct: Double = 25.0,
    val rsiOversold: Long = 30L,
    val rsiOverbought: Long = 70L,
    val gridLevels: Long = 10L,
    val realizedPnlUsd: Double = 0.0,
    val totalTrades: Long = 0L,
    val winningTrades: Long = 0L,
    val openPositionSide: String = "NONE",
    val openEntryPrice: Double = 0.0,
    val openQuantity: Double = 0.0,
    val peakUnrealizedPct: Double = 0.0,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun effectiveStrategies(): List<BotStrategy> {
        val parsed = strategies.mapNotNull { id ->
            BotStrategy.entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
        }.distinct()
        return parsed.ifEmpty { listOf(BotStrategy.fromId(strategy)) }
    }

    fun winRatePct(): Double =
        if (totalTrades <= 0L) 0.0 else (winningTrades.toDouble() / totalTrades.toDouble()) * 100.0

    fun unrealizedPnlUsd(currentPrice: Double): Double {
        if (openPositionSide == "NONE" || openQuantity <= 0.0 || openEntryPrice <= 0.0 || currentPrice <= 0.0) {
            return 0.0
        }
        val rawDiff = if (openPositionSide == "SHORT") {
            openEntryPrice - currentPrice
        } else {
            currentPrice - openEntryPrice
        }
        return rawDiff * openQuantity * leverage
    }

    fun unrealizedPnlPct(currentPrice: Double): Double {
        if (openPositionSide == "NONE" || openEntryPrice <= 0.0 || currentPrice <= 0.0) return 0.0
        val pct = if (openPositionSide == "SHORT") {
            ((openEntryPrice - currentPrice) / openEntryPrice) * 100.0
        } else {
            ((currentPrice - openEntryPrice) / openEntryPrice) * 100.0
        }
        return pct * leverage
    }
}

@Keep
@IgnoreExtraProperties
data class TradeExecution(
    @DocumentId val id: String = "",
    val userId: String = "",
    val botId: String = "",
    val botName: String = "",
    val assetClass: String = AssetClass.CRYPTO.id,
    val exchange: String = ExchangeVenue.BINANCE.id,
    val symbol: String = "BTC/USDT",
    val side: String = "BUY",
    val orderType: String = "MARKET",
    val executionMode: String = ExecutionMode.PAPER.id,
    val price: Double = 0.0,
    val quantity: Double = 0.0,
    val notionalUsd: Double = 0.0,
    val feeUsd: Double = 0.0,
    val pnlUsd: Double = 0.0,
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class RiskProfile(
    @DocumentId val id: String = "global",
    val userId: String = "",
    val maxDailyLossUsd: Double = 750.0,
    val maxPortfolioDrawdownPct: Double = 15.0,
    val maxOpenPositions: Long = 6L,
    val maxLeverageCap: Double = 5.0,
    val defaultStopLossPct: Double = 2.5,
    val defaultTakeProfitPct: Double = 6.0,
    val defaultTrailingStopPct: Double = 1.5,
    val slippageTolerancePct: Double = 0.35,
    val cooldownMinutes: Long = 3L,
    val circuitBreakerTriggered: Boolean = false,
    val autoPauseOnHighVolatility: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ExchangeConnection(
    @DocumentId val id: String = "",
    val userId: String = "",
    val exchangeName: String = "",
    val assetClass: String = AssetClass.CRYPTO.id,
    val apiKeyLabel: String = "",
    @get:PropertyName("isPaperMode") @PropertyName("isPaperMode") val isPaperMode: Boolean = true,
    @get:PropertyName("isConnected") @PropertyName("isConnected") val isConnected: Boolean = true,
    val latencyMs: Long = 42L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WalletAccount(
    @DocumentId val id: String = "summary",
    val userId: String = "",
    val availableCashUsd: Double = 10_000.0,
    val totalDepositedUsd: Double = 10_000.0,
    val totalWithdrawnUsd: Double = 0.0,
    val currency: String = "USD",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class WalletTransaction(
    @DocumentId val id: String = "",
    val userId: String = "",
    val type: String = "DEPOSIT",
    val method: String = "ACH Bank Transfer",
    val amountUsd: Double = 0.0,
    val balanceAfterUsd: Double = 0.0,
    val status: String = "COMPLETED",
    val note: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
)
