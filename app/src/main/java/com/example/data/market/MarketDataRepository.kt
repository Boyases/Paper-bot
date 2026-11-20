package com.example.data.market

import android.util.Log
import com.example.data.model.AssetClass
import com.example.data.model.Candle
import com.example.data.model.ExchangeVenue
import com.example.data.model.MarketAsset
import com.example.domain.engine.TechnicalIndicators
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

data class MarketFeedState(
    val assets: List<MarketAsset> = emptyList(),
    val isLoading: Boolean = true,
    val lastSyncEpochMs: Long = 0L,
    val activeSources: List<String> = emptyList(),
    val errorMessage: String? = null
)

class MarketDataRepository {

    private val okHttp = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private fun buildRetrofit(baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttp)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    private val binanceGlobal = buildRetrofit("https://api.binance.com/").create(BinanceApi::class.java)
    private val binanceUs = buildRetrofit("https://api.binance.us/").create(BinanceApi::class.java)
    private val coinbaseApi = buildRetrofit("https://api.exchange.coinbase.com/").create(CoinbaseApi::class.java)
    private val krakenApi = buildRetrofit("https://api.kraken.com/").create(KrakenApi::class.java)
    private val bybitApi = buildRetrofit("https://api.bybit.com/").create(BybitApi::class.java)
    private val okxApi = buildRetrofit("https://www.okx.com/").create(OkxApi::class.java)
    private val coinGeckoApi = buildRetrofit("https://api.coingecko.com/").create(CoinGeckoApi::class.java)
    private val goldApi = buildRetrofit("https://api.gold-api.com/").create(GoldApi::class.java)
    private val openErApi = buildRetrofit("https://open.er-api.com/").create(OpenErApi::class.java)
    private val frankfurterApi = buildRetrofit("https://api.frankfurter.app/").create(FrankfurterApi::class.java)

    private val _feedState = MutableStateFlow(MarketFeedState())
    val feedState: StateFlow<MarketFeedState> = _feedState.asStateFlow()

    // In-memory rolling price history for assets whose spot API doesn't expose intraday klines directly
    private val rollingHistory = mutableMapOf<String, MutableList<Candle>>()

    suspend fun refreshAllMarkets() {
        try {
            val results = coroutineScope {
                val cryptoDeferred = async { fetchCryptoMarkets() }
                val metalsDeferred = async { fetchMetalsMarkets() }
                val forexDeferred = async { fetchForexMarkets() }
                Triple(cryptoDeferred.await(), metalsDeferred.await(), forexDeferred.await())
            }

            val combined = results.first + results.second + results.third
            val sources = combined.map { it.sourceLabel }.filter { it.isNotBlank() }.distinct()
            if (combined.isNotEmpty()) {
                _feedState.value = MarketFeedState(
                    assets = combined,
                    isLoading = false,
                    lastSyncEpochMs = System.currentTimeMillis(),
                    activeSources = sources,
                    errorMessage = null
                )
            } else {
                _feedState.value = _feedState.value.copy(
                    isLoading = false,
                    errorMessage = "Unable to reach live exchange feeds. Check network connectivity."
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Market refresh failed: ${e.message}", e)
            _feedState.value = _feedState.value.copy(
                isLoading = false,
                errorMessage = e.localizedMessage ?: "Market feed refresh failed"
            )
        }
    }

    private suspend fun fetchCryptoMarkets(): List<MarketAsset> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<MarketAsset>()

        // 1. Coinbase Exchange (BTC/USD, ETH/USD, SOL/USD) with real 15m candles
        val coinbasePairs = listOf(
            Triple("BTC-USD", "BTC/USD", "Bitcoin"),
            Triple("ETH-USD", "ETH/USD", "Ethereum"),
            Triple("SOL-USD", "SOL/USD", "Solana")
        )
        coroutineScope {
            coinbasePairs.map { (productId, displaySym, name) ->
                async {
                    try {
                        val stats = coinbaseApi.getProductStats(productId)
                        val rawCandles = runCatching { coinbaseApi.getCandles(productId, 900) }.getOrDefault(emptyList())
                        val price = stats.last.toDoubleOrNull() ?: 0.0
                        val open = stats.open.toDoubleOrNull() ?: price
                        val high = stats.high.toDoubleOrNull() ?: price
                        val low = stats.low.toDoubleOrNull() ?: price
                        val volUnits = stats.volume.toDoubleOrNull() ?: 0.0
                        val changePct = if (open > 0.0) ((price - open) / open) * 100.0 else 0.0
                        // Coinbase candles: [time, low, high, open, close, volume] newest first
                        val candles = rawCandles.take(48).reversed().mapNotNull { row ->
                            if (row.size >= 6) {
                                Candle(
                                    openTimeMs = (row[0] * 1000.0).toLong(),
                                    low = row[1],
                                    high = row[2],
                                    open = row[3],
                                    close = row[4],
                                    volume = row[5]
                                )
                            } else null
                        }
                        if (price > 0.0) {
                            MarketAsset(
                                symbol = displaySym,
                                displayName = name,
                                assetClass = AssetClass.CRYPTO,
                                exchange = ExchangeVenue.COINBASE,
                                price = price,
                                change24hPct = changePct,
                                high24h = high,
                                low24h = low,
                                volume24hUsd = volUnits * price,
                                candles = candles,
                                indicators = TechnicalIndicators.analyzeCandles(candles, price),
                                sourceLabel = "Coinbase Pro",
                                lastUpdatedMs = now
                            )
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }.mapNotNull { it.await() }.let { out.addAll(it) }
        }

        // 2. Binance (Global or US) for BTC/USDT, ETH/USDT, BNB/USDT, XRP/USDT
        val binanceAssets = fetchBinanceWithFallback(now)
        out.addAll(binanceAssets)

        // 3. Kraken (XBT/USD, ETH/USD)
        runCatching {
            val krakenResp = krakenApi.getTicker("XBTUSD,ETHUSD")
            krakenResp.result.forEach { (pairKey, dto) ->
                val price = dto.c.firstOrNull()?.toDoubleOrNull() ?: 0.0
                val open = dto.o.toDoubleOrNull() ?: price
                val high = dto.h.getOrNull(1)?.toDoubleOrNull() ?: price
                val low = dto.l.getOrNull(1)?.toDoubleOrNull() ?: price
                val vol = dto.v.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                val changePct = if (open > 0.0) ((price - open) / open) * 100.0 else 0.0
                val cleanSym = if (pairKey.contains("XBT")) "BTC/USD" else "ETH/USD"
                val name = if (pairKey.contains("XBT")) "Bitcoin (Kraken)" else "Ethereum (Kraken)"
                if (price > 0.0) {
                    val candles = appendRollingCandle("KRAKEN_$cleanSym", price, high, low, now)
                    out.add(
                        MarketAsset(
                            symbol = cleanSym,
                            displayName = name,
                            assetClass = AssetClass.CRYPTO,
                            exchange = ExchangeVenue.KRAKEN,
                            price = price,
                            change24hPct = changePct,
                            high24h = high,
                            low24h = low,
                            volume24hUsd = vol * price,
                            candles = candles,
                            indicators = TechnicalIndicators.analyzeCandles(candles, price),
                            sourceLabel = "Kraken",
                            lastUpdatedMs = now
                        )
                    )
                }
            }
        }

        // 4. Bybit & OKX spot tickers
        runCatching {
            val bybitResp = bybitApi.getSpotTickers("spot", "BTCUSDT")
            bybitResp.result.list.firstOrNull()?.let { item ->
                val price = item.lastPrice.toDoubleOrNull() ?: 0.0
                if (price > 0.0) {
                    val candles = appendRollingCandle("BYBIT_BTC", price, price, price, now)
                    out.add(
                        MarketAsset(
                            symbol = "BTC/USDT",
                            displayName = "Bitcoin (Bybit)",
                            assetClass = AssetClass.CRYPTO,
                            exchange = ExchangeVenue.BYBIT,
                            price = price,
                            change24hPct = (item.price24hPcnt.toDoubleOrNull() ?: 0.0) * 100.0,
                            high24h = item.highPrice24h.toDoubleOrNull() ?: price,
                            low24h = item.lowPrice24h.toDoubleOrNull() ?: price,
                            volume24hUsd = item.turnover24h.toDoubleOrNull() ?: 0.0,
                            candles = candles,
                            indicators = TechnicalIndicators.analyzeCandles(candles, price),
                            sourceLabel = "Bybit",
                            lastUpdatedMs = now
                        )
                    )
                }
            }
        }

        runCatching {
            val okxResp = okxApi.getTicker("ETH-USDT")
            okxResp.data.firstOrNull()?.let { item ->
                val price = item.last.toDoubleOrNull() ?: 0.0
                val open = item.open24h.toDoubleOrNull() ?: price
                val changePct = if (open > 0.0) ((price - open) / open) * 100.0 else 0.0
                if (price > 0.0) {
                    val candles = appendRollingCandle("OKX_ETH", price, price, price, now)
                    out.add(
                        MarketAsset(
                            symbol = "ETH/USDT",
                            displayName = "Ethereum (OKX)",
                            assetClass = AssetClass.CRYPTO,
                            exchange = ExchangeVenue.OKX,
                            price = price,
                            change24hPct = changePct,
                            high24h = item.high24h.toDoubleOrNull() ?: price,
                            low24h = item.low24h.toDoubleOrNull() ?: price,
                            volume24hUsd = item.volCcy24h.toDoubleOrNull() ?: 0.0,
                            candles = candles,
                            indicators = TechnicalIndicators.analyzeCandles(candles, price),
                            sourceLabel = "OKX",
                            lastUpdatedMs = now
                        )
                    )
                }
            }
        }

        // Fallback via CoinGecko if major direct exchange endpoints were blocked
        if (out.isEmpty()) {
            runCatching {
                val cg = coinGeckoApi.getSimplePrices("bitcoin,ethereum,solana,binancecoin,ripple")
                val mapping = listOf(
                    Triple("bitcoin", "BTC/USDT", "Bitcoin"),
                    Triple("ethereum", "ETH/USDT", "Ethereum"),
                    Triple("solana", "SOL/USDT", "Solana"),
                    Triple("binancecoin", "BNB/USDT", "BNB"),
                    Triple("ripple", "XRP/USDT", "XRP")
                )
                mapping.forEach { (id, sym, name) ->
                    val data = cg[id] ?: return@forEach
                    val price = data["usd"] ?: 0.0
                    val change = data["usd_24h_change"] ?: 0.0
                    val vol = data["usd_24h_vol"] ?: 0.0
                    if (price > 0.0) {
                        val candles = appendRollingCandle("CG_$sym", price, price, price, now)
                        out.add(
                            MarketAsset(
                                symbol = sym,
                                displayName = name,
                                assetClass = AssetClass.CRYPTO,
                                exchange = ExchangeVenue.BINANCE,
                                price = price,
                                change24hPct = change,
                                high24h = price,
                                low24h = price,
                                volume24hUsd = vol,
                                candles = candles,
                                indicators = TechnicalIndicators.analyzeCandles(candles, price),
                                sourceLabel = "CoinGecko",
                                lastUpdatedMs = now
                            )
                        )
                    }
                }
            }
        }

        return out
    }

    private suspend fun fetchBinanceWithFallback(now: Long): List<MarketAsset> {
        val apis = listOf(binanceGlobal to "Binance", binanceUs to "Binance.US")
        val symbolsJson = """["BTCUSDT","ETHUSDT","BNBUSDT","SOLUSDT","XRPUSDT"]"""
        for ((api, label) in apis) {
            try {
                val tickers = api.getTickers(symbolsJson)
                if (tickers.isNotEmpty()) {
                    return coroutineScope {
                        tickers.map { dto ->
                            async {
                                val price = dto.lastPrice.toDoubleOrNull() ?: 0.0
                                val formattedSym = formatCryptoPair(dto.symbol)
                                val rawKlines = runCatching { api.getKlines(dto.symbol, "15m", 48) }.getOrDefault(emptyList())
                                val candles = parseBinanceKlines(rawKlines)
                                MarketAsset(
                                    symbol = formattedSym,
                                    displayName = cryptoName(dto.symbol),
                                    assetClass = AssetClass.CRYPTO,
                                    exchange = ExchangeVenue.BINANCE,
                                    price = price,
                                    change24hPct = dto.priceChangePercent.toDoubleOrNull() ?: 0.0,
                                    high24h = dto.highPrice.toDoubleOrNull() ?: price,
                                    low24h = dto.lowPrice.toDoubleOrNull() ?: price,
                                    volume24hUsd = dto.quoteVolume.toDoubleOrNull() ?: 0.0,
                                    candles = candles,
                                    indicators = TechnicalIndicators.analyzeCandles(candles, price),
                                    sourceLabel = label,
                                    lastUpdatedMs = now
                                )
                            }
                        }.map { it.await() }.filter { it.price > 0.0 }
                    }
                }
            } catch (_: Exception) {
                // Try next Binance endpoint
            }
        }
        return emptyList()
    }

    private fun parseBinanceKlines(raw: List<List<Any>>): List<Candle> =
        raw.mapNotNull { row ->
            if (row.size >= 6) {
                val time = (row[0] as? Number)?.toLong() ?: 0L
                val open = (row[1] as? String)?.toDoubleOrNull() ?: (row[1] as? Number)?.toDouble() ?: 0.0
                val high = (row[2] as? String)?.toDoubleOrNull() ?: (row[2] as? Number)?.toDouble() ?: 0.0
                val low = (row[3] as? String)?.toDoubleOrNull() ?: (row[3] as? Number)?.toDouble() ?: 0.0
                val close = (row[4] as? String)?.toDoubleOrNull() ?: (row[4] as? Number)?.toDouble() ?: 0.0
                val vol = (row[5] as? String)?.toDoubleOrNull() ?: (row[5] as? Number)?.toDouble() ?: 0.0
                if (close > 0.0) Candle(time, open, high, low, close, vol) else null
            } else null
        }

    private suspend fun fetchMetalsMarkets(): List<MarketAsset> {
        val now = System.currentTimeMillis()
        val metals = listOf(
            "XAU" to "Gold Spot",
            "XAG" to "Silver Spot",
            "XPT" to "Platinum Spot",
            "XPD" to "Palladium Spot"
        )
        val out = mutableListOf<MarketAsset>()
        // Also fetch PAXG candles from Binance/Coinbase if available to enrich Gold chart history
        val paxgCandles = runCatching {
            parseBinanceKlines(binanceGlobal.getKlines("PAXGUSDT", "15m", 48))
        }.getOrElse {
            runCatching { parseBinanceKlines(binanceUs.getKlines("PAXGUSD", "15m", 48)) }.getOrDefault(emptyList())
        }

        coroutineScope {
            metals.map { (code, title) ->
                async {
                    try {
                        val dto = goldApi.getMetalPrice(code)
                        if (dto.price > 0.0) {
                            val symbol = "$code/USD"
                            val candles = if (code == "XAU" && paxgCandles.isNotEmpty()) {
                                paxgCandles
                            } else {
                                appendRollingCandle("METAL_$code", dto.price, dto.price, dto.price, now)
                            }
                            val firstClose = candles.firstOrNull()?.close ?: dto.price
                            val changePct = if (firstClose > 0.0) ((dto.price - firstClose) / firstClose) * 100.0 else 0.0
                            MarketAsset(
                                symbol = symbol,
                                displayName = title,
                                assetClass = AssetClass.METALS,
                                exchange = ExchangeVenue.METALS_SPOT,
                                price = dto.price,
                                change24hPct = changePct,
                                high24h = candles.maxOfOrNull { it.high } ?: dto.price,
                                low24h = candles.minOfOrNull { it.low } ?: dto.price,
                                volume24hUsd = 0.0,
                                candles = candles,
                                indicators = TechnicalIndicators.analyzeCandles(candles, dto.price),
                                sourceLabel = "Gold-API Spot",
                                lastUpdatedMs = now
                            )
                        } else null
                    } catch (_: Exception) {
                        null
                    }
                }
            }.mapNotNull { it.await() }.let { out.addAll(it) }
        }

        // Fallback Gold via CoinGecko PAX-Gold if gold-api failed
        if (out.isEmpty()) {
            runCatching {
                val cg = coinGeckoApi.getSimplePrices("pax-gold")
                val data = cg["pax-gold"]
                val price = data?.get("usd") ?: 0.0
                if (price > 0.0) {
                    val candles = appendRollingCandle("METAL_XAU", price, price, price, now)
                    out.add(
                        MarketAsset(
                            symbol = "XAU/USD",
                            displayName = "Gold Spot (PAXG)",
                            assetClass = AssetClass.METALS,
                            exchange = ExchangeVenue.METALS_SPOT,
                            price = price,
                            change24hPct = data?.get("usd_24h_change") ?: 0.0,
                            high24h = price,
                            low24h = price,
                            volume24hUsd = data?.get("usd_24h_vol") ?: 0.0,
                            candles = candles,
                            indicators = TechnicalIndicators.analyzeCandles(candles, price),
                            sourceLabel = "CoinGecko Gold",
                            lastUpdatedMs = now
                        )
                    )
                }
            }
        }
        return out
    }

    private suspend fun fetchForexMarkets(): List<MarketAsset> {
        val now = System.currentTimeMillis()
        val out = mutableListOf<MarketAsset>()
        val latest = runCatching { openErApi.getLatestRates("USD") }.getOrNull()
        val series = runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val endCal = Calendar.getInstance()
            val startCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -45) }
            val range = "${sdf.format(startCal.time)}..${sdf.format(endCal.time)}"
            frankfurterApi.getTimeSeries(range, "USD", "EUR,GBP,JPY,CHF,AUD,CAD")
        }.getOrNull()

        val rates = latest?.rates ?: emptyMap()
        val seriesRates = series?.rates?.toSortedMap() ?: sortedMapOf()

        data class FxSpec(val symbol: String, val name: String, val currency: String, val invertFromUsd: Boolean)
        val fxSpecs = listOf(
            FxSpec("EUR/USD", "Euro / US Dollar", "EUR", true),
            FxSpec("GBP/USD", "British Pound / USD", "GBP", true),
            FxSpec("USD/JPY", "US Dollar / Yen", "JPY", false),
            FxSpec("USD/CHF", "US Dollar / Franc", "CHF", false),
            FxSpec("AUD/USD", "Australian Dollar / USD", "AUD", true),
            FxSpec("USD/CAD", "US Dollar / Canadian", "CAD", false)
        )

        fxSpecs.forEach { spec ->
            val rawSpot = rates[spec.currency] ?: seriesRates.values.lastOrNull()?.get(spec.currency) ?: 0.0
            val spot = if (rawSpot > 0.0) {
                if (spec.invertFromUsd) 1.0 / rawSpot else rawSpot
            } else 0.0

            if (spot > 0.0) {
                val histCandles = seriesRates.entries.mapIndexedNotNull { idx, entry ->
                    val v = entry.value[spec.currency] ?: return@mapIndexedNotNull null
                    val px = if (spec.invertFromUsd && v > 0.0) 1.0 / v else v
                    if (px > 0.0) {
                        Candle(
                            openTimeMs = now - ((seriesRates.size - idx) * 86_400_000L),
                            open = px,
                            high = px,
                            low = px,
                            close = px
                        )
                    } else null
                }
                val candles = if (histCandles.isNotEmpty()) {
                    histCandles + Candle(now, spot, spot, spot, spot)
                } else {
                    appendRollingCandle("FX_${spec.symbol}", spot, spot, spot, now)
                }
                val prevClose = if (candles.size >= 2) candles[candles.size - 2].close else spot
                val changePct = if (prevClose > 0.0) ((spot - prevClose) / prevClose) * 100.0 else 0.0

                out.add(
                    MarketAsset(
                        symbol = spec.symbol,
                        displayName = spec.name,
                        assetClass = AssetClass.FOREX,
                        exchange = ExchangeVenue.FOREX_INTERBANK,
                        price = spot,
                        change24hPct = changePct,
                        high24h = candles.maxOfOrNull { it.high } ?: spot,
                        low24h = candles.minOfOrNull { it.low } ?: spot,
                        volume24hUsd = 0.0,
                        candles = candles,
                        indicators = TechnicalIndicators.analyzeCandles(candles, spot),
                        sourceLabel = if (latest != null) "OpenER + ECB FX" else "Frankfurter ECB",
                        lastUpdatedMs = now
                    )
                )
            }
        }
        return out
    }

    private fun appendRollingCandle(
        key: String,
        price: Double,
        high: Double,
        low: Double,
        nowMs: Long
    ): List<Candle> {
        val list = rollingHistory.getOrPut(key) { mutableListOf() }
        val open = list.lastOrNull()?.close ?: price
        list.add(Candle(nowMs, open, maxOf(high, price), minOf(low, price), price, 0.0))
        if (list.size > 48) {
            list.removeAt(0)
        }
        return list.toList()
    }

    private fun formatCryptoPair(raw: String): String =
        when {
            raw.endsWith("USDT") -> "${raw.removeSuffix("USDT")}/USDT"
            raw.endsWith("USD") -> "${raw.removeSuffix("USD")}/USD"
            else -> raw
        }

    private fun cryptoName(raw: String): String =
        when {
            raw.startsWith("BTC") -> "Bitcoin"
            raw.startsWith("ETH") -> "Ethereum"
            raw.startsWith("BNB") -> "BNB"
            raw.startsWith("SOL") -> "Solana"
            raw.startsWith("XRP") -> "XRP"
            else -> raw
        }

    private companion object {
        const val TAG = "MarketDataRepo"
    }
}
