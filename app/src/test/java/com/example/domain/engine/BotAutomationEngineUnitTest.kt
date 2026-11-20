package com.example.domain.engine

import com.example.data.model.AssetClass
import com.example.data.model.BotStatus
import com.example.data.model.BotStrategy
import com.example.data.model.Candle
import com.example.data.model.ExchangeVenue
import com.example.data.model.IndicatorSnapshot
import com.example.data.model.MarketAsset
import com.example.data.model.RiskProfile
import com.example.data.model.StrategyCombineMode
import com.example.data.model.TradingBot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BotAutomationEngineUnitTest {

    @Test
    fun technicalIndicators_uptrendCandles_yieldsBullishEmaAndHighRsi() {
        val candles = (1..30).map { i ->
            val px = 100.0 + i * 2.5
            Candle(openTimeMs = i * 60_000L, open = px - 1.0, high = px + 1.0, low = px - 1.5, close = px)
        }
        val snapshot = TechnicalIndicators.analyzeCandles(candles, 175.0)
        assertTrue(snapshot.ema9 > snapshot.ema21)
        assertTrue(snapshot.rsi14 > 70.0)
    }

    @Test
    fun evaluate_stopLossTriggered_closesOpenPosition() {
        val bot = TradingBot(
            id = "bot_1",
            name = "Gold Trend",
            assetClass = AssetClass.METALS.id,
            exchange = ExchangeVenue.METALS_SPOT.id,
            symbol = "XAU/USD",
            strategy = BotStrategy.EMA_CROSS.id,
            status = BotStatus.RUNNING.id,
            allocatedCapitalUsd = 2000.0,
            leverage = 1.0,
            stopLossPct = 2.0,
            takeProfitPct = 6.0,
            openPositionSide = "LONG",
            openEntryPrice = 2600.0,
            openQuantity = 0.5
        )
        val asset = MarketAsset(
            symbol = "XAU/USD",
            displayName = "Gold Spot",
            assetClass = AssetClass.METALS,
            exchange = ExchangeVenue.METALS_SPOT,
            price = 2530.0, // -2.69% drawdown
            change24hPct = -2.5,
            high24h = 2610.0,
            low24h = 2520.0,
            volume24hUsd = 0.0,
            indicators = IndicatorSnapshot(ema9 = 2550.0, ema21 = 2570.0, rsi14 = 35.0)
        )
        val decision = BotAutomationEngine.evaluate(
            bot = bot,
            asset = asset,
            riskProfile = RiskProfile(),
            openPositionsCount = 1,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = -35.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L
        )
        assertTrue(decision is EngineDecision.ClosePosition)
        val close = decision as EngineDecision.ClosePosition
        assertEquals("STOP_LOSS", close.orderType)
        assertTrue(close.pnlUsd < 0.0)
    }

    @Test
    fun evaluate_dailyLossBreached_tripsGlobalCircuitBreaker() {
        val bot = TradingBot(
            id = "bot_2",
            name = "EUR FX",
            assetClass = AssetClass.FOREX.id,
            exchange = ExchangeVenue.FOREX_INTERBANK.id,
            symbol = "EUR/USD",
            strategy = BotStrategy.RSI_REVERSION.id,
            status = BotStatus.RUNNING.id
        )
        val asset = MarketAsset(
            symbol = "EUR/USD",
            displayName = "Euro",
            assetClass = AssetClass.FOREX,
            exchange = ExchangeVenue.FOREX_INTERBANK,
            price = 1.0820,
            change24hPct = -0.3,
            high24h = 1.086,
            low24h = 1.080,
            volume24hUsd = 0.0,
            indicators = IndicatorSnapshot(rsi14 = 25.0)
        )
        val decision = BotAutomationEngine.evaluate(
            bot = bot,
            asset = asset,
            riskProfile = RiskProfile(maxDailyLossUsd = 500.0),
            openPositionsCount = 1,
            todayRealizedPnlUsd = -420.0,
            totalUnrealizedPnlUsd = -110.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L
        )
        assertTrue(decision is EngineDecision.TripGlobalCircuitBreaker)
    }

    @Test
    fun evaluate_trailingStopTriggered_locksProfit() {
        val bot = TradingBot(
            id = "bot_3",
            name = "BTC Trail",
            assetClass = AssetClass.CRYPTO.id,
            exchange = ExchangeVenue.BINANCE.id,
            symbol = "BTC/USDT",
            strategy = BotStrategy.EMA_CROSS.id,
            status = BotStatus.RUNNING.id,
            stopLossPct = 2.5,
            takeProfitPct = 10.0,
            trailingStopPct = 1.5,
            openPositionSide = "LONG",
            openEntryPrice = 90000.0,
            openQuantity = 0.02,
            peakUnrealizedPct = 5.0
        )
        val asset = MarketAsset(
            symbol = "BTC/USDT",
            displayName = "Bitcoin",
            assetClass = AssetClass.CRYPTO,
            exchange = ExchangeVenue.BINANCE,
            price = 92800.0, // +3.11%, dropped >1.5% from +5.0% peak
            change24hPct = 3.1,
            high24h = 94500.0,
            low24h = 89500.0,
            volume24hUsd = 1_000_000_000.0,
            indicators = IndicatorSnapshot(ema9 = 93000.0, ema21 = 91000.0, rsi14 = 60.0)
        )
        val decision = BotAutomationEngine.evaluate(
            bot = bot,
            asset = asset,
            riskProfile = RiskProfile(),
            openPositionsCount = 1,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = 56.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L
        )
        assertTrue(decision is EngineDecision.ClosePosition)
        assertEquals("TRAILING_STOP", (decision as EngineDecision.ClosePosition).orderType)
    }

    @Test
    fun evaluate_martingale_scalesPositionByConsecutiveLossesWithCap() {
        val bot = TradingBot(
            id = "bot_mart",
            name = "FxPro EUR Martingale",
            assetClass = AssetClass.FOREX.id,
            exchange = ExchangeVenue.FXPRO.id,
            symbol = "EUR/USD",
            strategy = BotStrategy.MARTINGALE.id,
            status = BotStatus.RUNNING.id,
            allocatedCapitalUsd = 2000.0,
            leverage = 1.0,
            maxPositionPct = 20.0 // base = $400
        )
        val asset = MarketAsset(
            symbol = "EUR/USD",
            displayName = "Euro / US Dollar",
            assetClass = AssetClass.FOREX,
            exchange = ExchangeVenue.FOREX_INTERBANK,
            price = 1.0800,
            change24hPct = -0.5,
            high24h = 1.0880,
            low24h = 1.0780,
            volume24hUsd = 0.0,
            indicators = IndicatorSnapshot(ema9 = 1.0810, ema21 = 1.0830, rsi14 = 39.0, bbMiddle = 1.0830)
        )

        val step1 = BotAutomationEngine.evaluate(
            bot = bot,
            asset = asset,
            riskProfile = RiskProfile(),
            openPositionsCount = 0,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = 0.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L,
            consecutiveLosses = 0
        ) as EngineDecision.OpenPosition
        assertEquals(400.0, step1.notionalUsd, 0.5)

        val step3 = BotAutomationEngine.evaluate(
            bot = bot,
            asset = asset,
            riskProfile = RiskProfile(),
            openPositionsCount = 0,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = 0.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L,
            consecutiveLosses = 2
        ) as EngineDecision.OpenPosition
        assertEquals(1600.0, step3.notionalUsd, 0.5)
        assertTrue(step3.reason.contains("4x"))

        // Capped at allocatedCapitalUsd ($2000) even with 3 consecutive losses (base*8 = $3200 -> $2000)
        val capped = BotAutomationEngine.evaluate(
            bot = bot,
            asset = asset,
            riskProfile = RiskProfile(),
            openPositionsCount = 0,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = 0.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L,
            consecutiveLosses = 3
        ) as EngineDecision.OpenPosition
        assertEquals(2000.0, capped.notionalUsd, 0.5)
    }

    @Test
    fun evaluate_tradingView21Strategy_entersAboveEma21AndExitsOnBreakdown() {
        val entryBot = TradingBot(
            id = "bot_tv21",
            name = "OctaFX TV21",
            assetClass = AssetClass.METALS.id,
            exchange = ExchangeVenue.OCTAFX.id,
            symbol = "XAU/USD",
            strategy = BotStrategy.TRADINGVIEW_21.id,
            status = BotStatus.RUNNING.id,
            allocatedCapitalUsd = 3000.0,
            leverage = 1.0,
            maxPositionPct = 30.0
        )
        val bullishAsset = MarketAsset(
            symbol = "XAU/USD",
            displayName = "Gold Spot",
            assetClass = AssetClass.METALS,
            exchange = ExchangeVenue.METALS_SPOT,
            price = 2660.0,
            change24hPct = 0.8,
            high24h = 2668.0,
            low24h = 2630.0,
            volume24hUsd = 0.0,
            indicators = IndicatorSnapshot(ema9 = 2652.0, ema21 = 2645.0, rsi14 = 58.0)
        )
        val openDecision = BotAutomationEngine.evaluate(
            bot = entryBot,
            asset = bullishAsset,
            riskProfile = RiskProfile(),
            openPositionsCount = 0,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = 0.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L
        )
        assertTrue(openDecision is EngineDecision.OpenPosition)
        assertTrue((openDecision as EngineDecision.OpenPosition).reason.contains("TV 21-EMA"))

        val openBot = entryBot.copy(
            openPositionSide = "LONG",
            openEntryPrice = 2620.0,
            openQuantity = 0.5
        )
        val breakdownAsset = bullishAsset.copy(
            price = 2635.0, // +0.57% profit, dipped below EMA21=2645.0
            indicators = IndicatorSnapshot(ema9 = 2642.0, ema21 = 2645.0, rsi14 = 48.0)
        )
        val closeDecision = BotAutomationEngine.evaluate(
            bot = openBot,
            asset = breakdownAsset,
            riskProfile = RiskProfile(),
            openPositionsCount = 1,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = 7.5,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L
        )
        assertTrue(closeDecision is EngineDecision.ClosePosition)
        assertTrue((closeDecision as EngineDecision.ClosePosition).reason.contains("21-EMA"))
    }

    @Test
    fun exchangeVenue_forAssetClass_includesFxProAndOctaFx() {
        val forexVenues = ExchangeVenue.forAssetClass(AssetClass.FOREX)
        assertTrue(forexVenues.contains(ExchangeVenue.FXPRO))
        assertTrue(forexVenues.contains(ExchangeVenue.OCTAFX))

        val metalsVenues = ExchangeVenue.forAssetClass(AssetClass.METALS)
        assertTrue(metalsVenues.contains(ExchangeVenue.FXPRO))
        assertTrue(metalsVenues.contains(ExchangeVenue.OCTAFX))
    }

    @Test
    fun evaluate_multiStrategyAllMode_requiresAllSelectedStrategiesToAgree() {
        val multiBot = TradingBot(
            id = "bot_multi_all",
            name = "BTC Confluence",
            assetClass = AssetClass.CRYPTO.id,
            exchange = ExchangeVenue.BINANCE.id,
            symbol = "BTC/USDT",
            strategy = BotStrategy.EMA_CROSS.id,
            strategies = listOf(BotStrategy.EMA_CROSS.id, BotStrategy.RSI_REVERSION.id),
            strategyMode = StrategyCombineMode.ALL.id,
            status = BotStatus.RUNNING.id,
            allocatedCapitalUsd = 3000.0,
            maxPositionPct = 25.0,
            rsiOversold = 35L
        )
        // Only EMA_CROSS is bullish (RSI=55 > 35), so ALL mode should hold
        val partialSignalAsset = MarketAsset(
            symbol = "BTC/USDT",
            displayName = "Bitcoin",
            assetClass = AssetClass.CRYPTO,
            exchange = ExchangeVenue.BINANCE,
            price = 92000.0,
            change24hPct = 1.2,
            high24h = 92500.0,
            low24h = 90500.0,
            volume24hUsd = 500_000_000.0,
            indicators = IndicatorSnapshot(ema9 = 91800.0, ema21 = 91200.0, rsi14 = 55.0)
        )
        val holdDecision = BotAutomationEngine.evaluate(
            bot = multiBot,
            asset = partialSignalAsset,
            riskProfile = RiskProfile(),
            openPositionsCount = 0,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = 0.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L
        )
        assertTrue(holdDecision is EngineDecision.NoAction)

        // With ANY mode on the same partial signal, bot should enter
        val anyDecision = BotAutomationEngine.evaluate(
            bot = multiBot.copy(strategyMode = StrategyCombineMode.ANY.id),
            asset = partialSignalAsset,
            riskProfile = RiskProfile(),
            openPositionsCount = 0,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = 0.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L
        )
        assertTrue(anyDecision is EngineDecision.OpenPosition)
        assertTrue((anyDecision as EngineDecision.OpenPosition).reason.contains("[1/2]"))

        // When both EMA_CROSS (RSI in 38..72) and TRADINGVIEW_21 agree in ALL mode, opens [2/2]
        val confluenceBot = multiBot.copy(
            strategies = listOf(BotStrategy.EMA_CROSS.id, BotStrategy.TRADINGVIEW_21.id)
        )
        val fullSignalDecision = BotAutomationEngine.evaluate(
            bot = confluenceBot,
            asset = partialSignalAsset,
            riskProfile = RiskProfile(),
            openPositionsCount = 0,
            todayRealizedPnlUsd = 0.0,
            totalUnrealizedPnlUsd = 0.0,
            lastBotTradeEpochMs = 0L,
            nowEpochMs = 1_000_000L
        )
        assertTrue(fullSignalDecision is EngineDecision.OpenPosition)
        assertTrue((fullSignalDecision as EngineDecision.OpenPosition).reason.contains("[2/2]"))
    }
}
