package com.example.data.market

import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface BinanceApi {
    @GET("api/v3/ticker/24hr")
    suspend fun getTickers(@Query("symbols") symbolsJson: String): List<BinanceTickerDto>

    @GET("api/v3/klines")
    suspend fun getKlines(
        @Query("symbol") symbol: String,
        @Query("interval") interval: String = "15m",
        @Query("limit") limit: Int = 48
    ): List<List<Any>>
}

@JsonClass(generateAdapter = true)
data class BinanceTickerDto(
    val symbol: String = "",
    val lastPrice: String = "0",
    val priceChangePercent: String = "0",
    val highPrice: String = "0",
    val lowPrice: String = "0",
    val quoteVolume: String = "0"
)

interface CoinbaseApi {
    @GET("products/{productId}/stats")
    suspend fun getProductStats(@Path("productId") productId: String): CoinbaseStatsDto

    @GET("products/{productId}/candles")
    suspend fun getCandles(
        @Path("productId") productId: String,
        @Query("granularity") granularity: Int = 900
    ): List<List<Double>>
}

@JsonClass(generateAdapter = true)
data class CoinbaseStatsDto(
    val open: String = "0",
    val high: String = "0",
    val low: String = "0",
    val last: String = "0",
    val volume: String = "0"
)

interface KrakenApi {
    @GET("0/public/Ticker")
    suspend fun getTicker(@Query("pair") pair: String): KrakenTickerResponse

    @GET("0/public/OHLC")
    suspend fun getOhlc(
        @Query("pair") pair: String,
        @Query("interval") interval: Int = 15
    ): KrakenOhlcResponse
}

@JsonClass(generateAdapter = true)
data class KrakenTickerResponse(
    val error: List<String> = emptyList(),
    val result: Map<String, KrakenPairDto> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class KrakenPairDto(
    val c: List<String> = emptyList(), // last trade [price, lot volume]
    val h: List<String> = emptyList(), // high [today, 24h]
    val l: List<String> = emptyList(), // low [today, 24h]
    val o: String = "0",               // today's opening price
    val v: List<String> = emptyList()  // volume [today, 24h]
)

@JsonClass(generateAdapter = true)
data class KrakenOhlcResponse(
    val error: List<String> = emptyList(),
    val result: Map<String, Any> = emptyMap()
)

interface BybitApi {
    @GET("v5/market/tickers")
    suspend fun getSpotTickers(
        @Query("category") category: String = "spot",
        @Query("symbol") symbol: String? = null
    ): BybitTickerResponse
}

@JsonClass(generateAdapter = true)
data class BybitTickerResponse(
    val retCode: Int = -1,
    val result: BybitTickerResult = BybitTickerResult()
)

@JsonClass(generateAdapter = true)
data class BybitTickerResult(
    val list: List<BybitTickerItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class BybitTickerItem(
    val symbol: String = "",
    val lastPrice: String = "0",
    val price24hPcnt: String = "0",
    val highPrice24h: String = "0",
    val lowPrice24h: String = "0",
    val turnover24h: String = "0"
)

interface OkxApi {
    @GET("api/v5/market/ticker")
    suspend fun getTicker(@Query("instId") instId: String): OkxTickerResponse
}

@JsonClass(generateAdapter = true)
data class OkxTickerResponse(
    val code: String = "",
    val data: List<OkxTickerItem> = emptyList()
)

@JsonClass(generateAdapter = true)
data class OkxTickerItem(
    val instId: String = "",
    val last: String = "0",
    val open24h: String = "0",
    val high24h: String = "0",
    val low24h: String = "0",
    val volCcy24h: String = "0"
)

interface CoinGeckoApi {
    @GET("api/v3/simple/price")
    suspend fun getSimplePrices(
        @Query("ids") ids: String,
        @Query("vs_currencies") vsCurrencies: String = "usd",
        @Query("include_24hr_change") include24hChange: Boolean = true,
        @Query("include_24hr_vol") include24hVol: Boolean = true
    ): Map<String, Map<String, Double>>
}

interface GoldApi {
    @GET("price/{symbol}")
    suspend fun getMetalPrice(@Path("symbol") symbol: String): GoldApiPriceDto
}

@JsonClass(generateAdapter = true)
data class GoldApiPriceDto(
    val name: String = "",
    val price: Double = 0.0,
    val symbol: String = "",
    val updatedAt: String = ""
)

interface OpenErApi {
    @GET("v6/latest/{base}")
    suspend fun getLatestRates(@Path("base") base: String = "USD"): OpenErResponse
}

@JsonClass(generateAdapter = true)
data class OpenErResponse(
    val result: String = "",
    val base_code: String = "USD",
    val rates: Map<String, Double> = emptyMap()
)

interface FrankfurterApi {
    @GET("{range}")
    suspend fun getTimeSeries(
        @Path("range") dateRange: String,
        @Query("from") from: String = "USD",
        @Query("to") to: String = "EUR,GBP,JPY,CHF,AUD,CAD"
    ): FrankfurterTimeSeriesDto
}

@JsonClass(generateAdapter = true)
data class FrankfurterTimeSeriesDto(
    val base: String = "USD",
    val rates: Map<String, Map<String, Double>> = emptyMap()
)
