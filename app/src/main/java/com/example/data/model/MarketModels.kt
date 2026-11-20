package com.example.data.model

enum class AssetClass(val id: String, val label: String) {
    CRYPTO("CRYPTO", "Crypto"),
    METALS("METALS", "Metals"),
    FOREX("FOREX", "Forex");

    companion object {
        fun fromId(id: String): AssetClass =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: CRYPTO
    }
}

enum class ExchangeVenue(
    val id: String,
    val displayName: String,
    val assetClass: AssetClass
) {
    BINANCE("BINANCE", "Binance", AssetClass.CRYPTO),
    COINBASE("COINBASE", "Coinbase", AssetClass.CRYPTO),
    KRAKEN("KRAKEN", "Kraken", AssetClass.CRYPTO),
    BYBIT("BYBIT", "Bybit", AssetClass.CRYPTO),
    OKX("OKX", "OKX", AssetClass.CRYPTO),
    METALS_SPOT("METALS_SPOT", "BullionSpot", AssetClass.METALS),
    FOREX_INTERBANK("FOREX_INTERBANK", "Interbank FX", AssetClass.FOREX),
    FXPRO("FXPRO", "FxPro", AssetClass.FOREX),
    OCTAFX("OCTAFX", "OctaFX", AssetClass.FOREX);

    companion object {
        fun fromId(id: String): ExchangeVenue =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: BINANCE

        fun forAssetClass(assetClass: AssetClass): List<ExchangeVenue> =
            when (assetClass) {
                AssetClass.CRYPTO -> entries.filter { it.assetClass == AssetClass.CRYPTO }
                AssetClass.METALS -> listOf(METALS_SPOT, FXPRO, OCTAFX)
                AssetClass.FOREX -> listOf(FOREX_INTERBANK, FXPRO, OCTAFX)
            }
    }
}

enum class BotStrategy(
    val id: String,
    val displayName: String,
    val shortCode: String,
    val description: String
) {
    EMA_CROSS(
        "EMA_CROSS",
        "EMA Crossover (9/21)",
        "EMA",
        "Buys on bullish EMA-9/EMA-21 crossover and exits on bearish reversal or risk targets."
    ),
    RSI_REVERSION(
        "RSI_REVERSION",
        "RSI Mean Reversion",
        "RSI",
        "Enters when 14-period RSI dips below oversold threshold and takes profit near overbought."
    ),
    GRID(
        "GRID",
        "Grid Bot",
        "GRID",
        "Captures range-bound volatility across configured grid bands around live spot."
    ),
    MARTINGALE(
        "MARTINGALE",
        "Martingale Recovery",
        "MART",
        "Scales entry sizing progressively after losing trades to recover drawdown, bounded by risk caps."
    ),
    TRADINGVIEW_21(
        "TRADINGVIEW_21",
        "21 TradingView Strategy",
        "TV21",
        "TradingView 21-EMA trend filter with RSI/momentum pullback confirmation and dynamic EMA-21 exit."
    ),
    DCA(
        "DCA",
        "Smart Dip DCA",
        "DCA",
        "Accumulates on pullbacks below 20-SMA with trailing take-profit protection."
    ),
    BOLLINGER(
        "BOLLINGER",
        "Bollinger Breakout",
        "BB",
        "Trades volatility expansions outside 20-period +/- 2 sigma Bollinger Bands."
    );

    companion object {
        fun fromId(id: String): BotStrategy =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: EMA_CROSS
    }
}

enum class StrategyCombineMode(
    val id: String,
    val label: String,
    val description: String
) {
    ANY("ANY", "Any Signal", "Triggers entry when any selected strategy signals."),
    ALL("ALL", "All Agree", "Requires all selected strategies to confirm entry confluence.");

    companion object {
        fun fromId(id: String): StrategyCombineMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: ANY
    }
}

enum class ExecutionMode(val id: String, val label: String) {
    PAPER("PAPER", "Paper Live Feed"),
    LIVE_API("LIVE_API", "Live Exchange API");

    companion object {
        fun fromId(id: String): ExecutionMode =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PAPER
    }
}

enum class BotStatus(val id: String, val label: String) {
    RUNNING("RUNNING", "Active"),
    PAUSED("PAUSED", "Paused"),
    HALTED_RISK("HALTED_RISK", "Risk Halt");

    companion object {
        fun fromId(id: String): BotStatus =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: PAUSED
    }
}

data class Candle(
    val openTimeMs: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0
)

data class IndicatorSnapshot(
    val ema9: Double = 0.0,
    val ema21: Double = 0.0,
    val rsi14: Double = 50.0,
    val bbUpper: Double = 0.0,
    val bbMiddle: Double = 0.0,
    val bbLower: Double = 0.0,
    val volatilityPct: Double = 0.0,
    val signalLabel: String = "NEUTRAL"
)

data class MarketAsset(
    val symbol: String,
    val displayName: String,
    val assetClass: AssetClass,
    val exchange: ExchangeVenue,
    val price: Double,
    val change24hPct: Double,
    val high24h: Double,
    val low24h: Double,
    val volume24hUsd: Double,
    val candles: List<Candle> = emptyList(),
    val indicators: IndicatorSnapshot = IndicatorSnapshot(),
    val sourceLabel: String = "",
    val lastUpdatedMs: Long = 0L
) {
    val marketKey: String
        get() = "${exchange.id}:$symbol"
}

data class MarketTarget(
    val assetClass: AssetClass,
    val exchange: ExchangeVenue,
    val symbol: String
) {
    val marketKey: String
        get() = "${exchange.id}:$symbol"
}

