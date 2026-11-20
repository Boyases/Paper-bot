package com.example.domain.engine

import com.example.data.model.Candle
import com.example.data.model.IndicatorSnapshot
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

object TechnicalIndicators {

    fun computeEmaSeries(closes: List<Double>, period: Int): List<Double> {
        if (closes.isEmpty() || period <= 0) return emptyList()
        val k = 2.0 / (period + 1.0)
        val result = ArrayList<Double>(closes.size)
        var ema = closes.first()
        result.add(ema)
        for (i in 1 until closes.size) {
            ema = closes[i] * k + ema * (1.0 - k)
            result.add(ema)
        }
        return result
    }

    fun computeRsi(closes: List<Double>, period: Int = 14): Double {
        if (closes.size <= period) return 50.0
        var gainSum = 0.0
        var lossSum = 0.0
        for (i in 1..period) {
            val diff = closes[i] - closes[i - 1]
            if (diff >= 0) gainSum += diff else lossSum += abs(diff)
        }
        var avgGain = gainSum / period
        var avgLoss = lossSum / period
        for (i in (period + 1) until closes.size) {
            val diff = closes[i] - closes[i - 1]
            val gain = if (diff > 0) diff else 0.0
            val loss = if (diff < 0) abs(diff) else 0.0
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period
        }
        if (avgLoss == 0.0) return 100.0
        val rs = avgGain / avgLoss
        return (100.0 - (100.0 / (1.0 + rs))).coerceIn(0.0, 100.0)
    }

    fun computeBollingerBands(
        closes: List<Double>,
        period: Int = 20,
        numStdDev: Double = 2.0
    ): Triple<Double, Double, Double> {
        if (closes.isEmpty()) return Triple(0.0, 0.0, 0.0)
        val window = closes.takeLast(period)
        val mean = window.average()
        val variance = window.sumOf { (it - mean).pow(2) } / window.size.coerceAtLeast(1)
        val std = sqrt(variance)
        return Triple(mean + numStdDev * std, mean, mean - numStdDev * std)
    }

    fun computeVolatilityPct(candles: List<Candle>): Double {
        if (candles.size < 2) return 0.0
        val window = candles.takeLast(14)
        val avgRange = window.map { c ->
            if (c.close > 0.0) ((c.high - c.low) / c.close) * 100.0 else 0.0
        }.average()
        return avgRange.coerceAtLeast(0.0)
    }

    fun analyzeCandles(candles: List<Candle>, fallbackPrice: Double): IndicatorSnapshot {
        val closes = candles.map { it.close }.filter { it > 0.0 }
        if (closes.size < 3) {
            return IndicatorSnapshot(
                ema9 = fallbackPrice,
                ema21 = fallbackPrice,
                rsi14 = 50.0,
                bbUpper = fallbackPrice * 1.01,
                bbMiddle = fallbackPrice,
                bbLower = fallbackPrice * 0.99,
                volatilityPct = 0.0,
                signalLabel = "NEUTRAL"
            )
        }
        val ema9Series = computeEmaSeries(closes, 9)
        val ema21Series = computeEmaSeries(closes, 21)
        val ema9 = ema9Series.last()
        val ema21 = ema21Series.last()
        val rsi14 = computeRsi(closes, 14)
        val (bbUpper, bbMid, bbLower) = computeBollingerBands(closes, 20, 2.0)
        val volPct = computeVolatilityPct(candles)
        val lastPrice = closes.last()

        val signal = when {
            ema9 > ema21 && rsi14 < 68.0 -> "BULLISH"
            rsi14 <= 32.0 || lastPrice <= bbLower -> "OVERSOLD BUY"
            ema9 < ema21 && rsi14 > 35.0 -> "BEARISH"
            rsi14 >= 70.0 || lastPrice >= bbUpper -> "OVERBOUGHT"
            else -> "NEUTRAL"
        }

        return IndicatorSnapshot(
            ema9 = ema9,
            ema21 = ema21,
            rsi14 = rsi14,
            bbUpper = bbUpper,
            bbMiddle = bbMid,
            bbLower = bbLower,
            volatilityPct = volPct,
            signalLabel = signal
        )
    }
}
