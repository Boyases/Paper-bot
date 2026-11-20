package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.data.model.AssetClass
import com.example.data.model.Candle
import com.example.domain.engine.TechnicalIndicators
import com.example.ui.theme.AmberMetal
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import kotlin.math.roundToInt

@Composable
fun EquityCurveChart(
    points: List<Double>,
    modifier: Modifier = Modifier
) {
    val safePoints = if (points.size >= 2) points else listOf(1000.0, 1000.0)
    val isPositive = safePoints.last() >= safePoints.first()
    val lineColor = if (isPositive) EmeraldProfit else CrimsonLoss
    var selectedIdx by remember(safePoints) { mutableStateOf<Int?>(null) }

    Column(modifier = modifier) {
        if (selectedIdx != null && selectedIdx!! in safePoints.indices) {
            Text(
                text = "Point Equity: ${FormattingUtils.formatUsd(safePoints[selectedIdx!!])}",
                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                color = lineColor,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(safePoints) {
                        detectTapGestures { offset ->
                            val idx = ((offset.x / size.width.coerceAtLeast(1)) * (safePoints.size - 1))
                                .roundToInt()
                                .coerceIn(0, safePoints.lastIndex)
                            selectedIdx = idx
                        }
                    }
                    .pointerInput(safePoints) {
                        detectHorizontalDragGestures(
                            onDragEnd = { selectedIdx = null },
                            onDragCancel = { selectedIdx = null }
                        ) { change, _ ->
                            val idx = ((change.position.x / size.width.coerceAtLeast(1)) * (safePoints.size - 1))
                                .roundToInt()
                                .coerceIn(0, safePoints.lastIndex)
                            selectedIdx = idx
                        }
                    }
            ) {
                val minVal = safePoints.minOrNull() ?: 0.0
                val maxVal = safePoints.maxOrNull() ?: 1.0
                val range = (maxVal - minVal).takeIf { it > 0.0001 } ?: 1.0
                val padTop = 10f
                val padBottom = 10f
                val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)
                val stepX = if (safePoints.size > 1) size.width / (safePoints.size - 1).toFloat() else size.width

                // Baseline grid
                val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                for (i in 0..2) {
                    val y = padTop + (chartH / 2f) * i
                    drawLine(
                        color = BorderSubtle.copy(alpha = 0.6f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                        pathEffect = dashEffect
                    )
                }

                val linePath = Path()
                val fillPath = Path()

                safePoints.forEachIndexed { index, value ->
                    val x = index * stepX
                    val norm = ((value - minVal) / range).toFloat()
                    val y = padTop + chartH * (1f - norm)
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }
                fillPath.lineTo(size.width, size.height)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            lineColor.copy(alpha = 0.28f),
                            lineColor.copy(alpha = 0.0f)
                        )
                    )
                )

                drawPath(
                    path = linePath,
                    color = lineColor,
                    style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                selectedIdx?.let { idx ->
                    val x = idx * stepX
                    val norm = ((safePoints[idx] - minVal) / range).toFloat()
                    val y = padTop + chartH * (1f - norm)
                    drawLine(
                        color = ElectricCyan.copy(alpha = 0.65f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 2f
                    )
                    drawCircle(
                        color = lineColor,
                        radius = 7f,
                        center = Offset(x, y)
                    )
                }
            }
        }
    }
}

@Composable
fun MarketPriceChart(
    candles: List<Candle>,
    assetClass: AssetClass,
    symbol: String,
    modifier: Modifier = Modifier
) {
    val closes = remember(candles) { candles.map { it.close }.filter { it > 0.0 } }
    val ema9 = remember(closes) { TechnicalIndicators.computeEmaSeries(closes, 9) }
    val ema21 = remember(closes) { TechnicalIndicators.computeEmaSeries(closes, 21) }
    var touchedIndex by remember(closes) { mutableStateOf<Int?>(null) }

    if (closes.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Gathering live ticks for $symbol...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        return
    }

    val isUp = closes.last() >= closes.first()
    val priceColor = if (isUp) EmeraldProfit else CrimsonLoss

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "● Price",
                    style = MaterialTheme.typography.labelSmall,
                    color = priceColor
                )
                Text(
                    text = "● EMA 9",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
                Text(
                    text = "● EMA 21",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberMetal
                )
            }
            val displayPrice = touchedIndex?.let { closes.getOrNull(it) } ?: closes.last()
            Text(
                text = FormattingUtils.formatAssetPrice(displayPrice, assetClass, symbol),
                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                color = ElectricCyan
            )
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .pointerInput(closes) {
                    detectTapGestures { offset ->
                        val idx = ((offset.x / size.width.coerceAtLeast(1)) * (closes.size - 1))
                            .roundToInt()
                            .coerceIn(0, closes.lastIndex)
                        touchedIndex = idx
                    }
                }
                .pointerInput(closes) {
                    detectHorizontalDragGestures(
                        onDragEnd = { touchedIndex = null },
                        onDragCancel = { touchedIndex = null }
                    ) { change, _ ->
                        val idx = ((change.position.x / size.width.coerceAtLeast(1)) * (closes.size - 1))
                            .roundToInt()
                            .coerceIn(0, closes.lastIndex)
                        touchedIndex = idx
                    }
                }
        ) {
            val allVals = closes + ema9 + ema21
            val minVal = allVals.minOrNull() ?: 0.0
            val maxVal = allVals.maxOrNull() ?: 1.0
            val range = (maxVal - minVal).takeIf { it > 1e-9 } ?: 1.0
            val pad = 12f
            val h = (size.height - pad * 2f).coerceAtLeast(1f)
            val stepX = size.width / (closes.size - 1).coerceAtLeast(1).toFloat()

            // Horizontal dashed grid
            val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
            for (i in 0..3) {
                val y = pad + (h / 3f) * i
                drawLine(
                    color = BorderSubtle.copy(alpha = 0.5f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    pathEffect = dash
                )
            }

            fun buildPath(series: List<Double>): Path {
                val p = Path()
                series.forEachIndexed { idx, v ->
                    val x = idx * stepX
                    val y = pad + h * (1f - ((v - minVal) / range).toFloat())
                    if (idx == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                return p
            }

            val fillPath = buildPath(closes).apply {
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    listOf(priceColor.copy(alpha = 0.22f), Color.Transparent)
                )
            )
            drawPath(
                path = buildPath(closes),
                color = priceColor,
                style = Stroke(width = 3.5f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            )
            if (ema9.size == closes.size) {
                drawPath(
                    path = buildPath(ema9),
                    color = ElectricCyan.copy(alpha = 0.85f),
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                )
            }
            if (ema21.size == closes.size) {
                drawPath(
                    path = buildPath(ema21),
                    color = AmberMetal.copy(alpha = 0.85f),
                    style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                )
            }

            touchedIndex?.let { idx ->
                val x = idx * stepX
                val y = pad + h * (1f - ((closes[idx] - minVal) / range).toFloat())
                drawLine(
                    color = Color.White.copy(alpha = 0.4f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.5f
                )
                drawCircle(color = priceColor, radius = 7f, center = Offset(x, y))
            }
        }
    }
}

@Composable
fun SparklineChart(
    candles: List<Candle>,
    isPositive: Boolean,
    modifier: Modifier = Modifier
) {
    val closes = candles.map { it.close }.filter { it > 0.0 }
    val color = if (isPositive) EmeraldProfit else CrimsonLoss
    Canvas(modifier = modifier) {
        if (closes.size < 2) {
            drawLine(
                color = TextMuted.copy(alpha = 0.4f),
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 2f
            )
            return@Canvas
        }
        val minVal = closes.minOrNull() ?: 0.0
        val maxVal = closes.maxOrNull() ?: 1.0
        val range = (maxVal - minVal).takeIf { it > 1e-9 } ?: 1.0
        val stepX = size.width / (closes.size - 1).toFloat()
        val path = Path()
        closes.forEachIndexed { idx, v ->
            val x = idx * stepX
            val y = 4f + (size.height - 8f) * (1f - ((v - minVal) / range).toFloat())
            if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

enum class PriceChartStyle(val label: String) {
    AREA("Area"),
    CANDLES("Candles")
}

@Composable
fun RealtimePriceHistoryChart(
    candles: List<Candle>,
    assetClass: AssetClass,
    symbol: String,
    livePrice: Double,
    chartStyle: PriceChartStyle,
    modifier: Modifier = Modifier
) {
    val safeCandles = remember(candles, livePrice) {
        val base = candles.filter { it.close > 0.0 }
        when {
            base.size >= 2 -> base
            livePrice > 0.0 -> listOf(
                Candle(System.currentTimeMillis() - 60_000L, livePrice, livePrice, livePrice, livePrice),
                Candle(System.currentTimeMillis(), livePrice, livePrice, livePrice, livePrice)
            )
            else -> emptyList()
        }
    }

    if (safeCandles.size < 2) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceElevated),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Streaming real-time ticks for $symbol...",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }
        return
    }

    val closes = remember(safeCandles) { safeCandles.map { it.close } }
    val ema9 = remember(closes) { TechnicalIndicators.computeEmaSeries(closes, 9) }
    val ema21 = remember(closes) { TechnicalIndicators.computeEmaSeries(closes, 21) }
    var touchedIndex by remember(safeCandles) { mutableStateOf<Int?>(null) }

    val firstClose = closes.first()
    val lastClose = if (livePrice > 0.0) livePrice else closes.last()
    val isUp = lastClose >= firstClose
    val priceColor = if (isUp) EmeraldProfit else CrimsonLoss

    val minLow = remember(safeCandles) { safeCandles.minOf { minOf(it.low, it.close) } }
    val maxHigh = remember(safeCandles) { safeCandles.maxOf { maxOf(it.high, it.close) } }
    val activeCandle = touchedIndex?.let { safeCandles.getOrNull(it) } ?: safeCandles.last()
    val deltaFromStartPct = if (firstClose > 0.0) ((activeCandle.close - firstClose) / firstClose) * 100.0 else 0.0

    Column(modifier = modifier) {
        // Legend & Active Scrub Readout
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (chartStyle == PriceChartStyle.CANDLES) 4.dp else 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("● Spot", style = MaterialTheme.typography.labelSmall, color = priceColor)
                Text("● EMA9", style = MaterialTheme.typography.labelSmall, color = ElectricCyan)
                Text("● EMA21", style = MaterialTheme.typography.labelSmall, color = AmberMetal)
            }
            val timeLabel = if (activeCandle.openTimeMs > 0L) {
                FormattingUtils.formatTime(activeCandle.openTimeMs)
            } else "LIVE"
            Text(
                text = "$timeLabel • ${FormattingUtils.formatAssetPrice(activeCandle.close, assetClass, symbol)} (${FormattingUtils.formatPct(deltaFromStartPct)})",
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = if (touchedIndex != null) ElectricCyan else priceColor
            )
        }

        if (chartStyle == PriceChartStyle.CANDLES) {
            val stickColor = if (activeCandle.close >= activeCandle.open) EmeraldProfit else CrimsonLoss
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "O ${FormattingUtils.formatAssetPrice(activeCandle.open, assetClass, symbol)}",
                    style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                    color = TextSecondary
                )
                Text(
                    text = "H ${FormattingUtils.formatAssetPrice(activeCandle.high, assetClass, symbol)}",
                    style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                    color = EmeraldProfit
                )
                Text(
                    text = "L ${FormattingUtils.formatAssetPrice(activeCandle.low, assetClass, symbol)}",
                    style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                    color = CrimsonLoss
                )
                Text(
                    text = "C ${FormattingUtils.formatAssetPrice(activeCandle.close, assetClass, symbol)}",
                    style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                    color = stickColor
                )
            }
        }

        // Price Axis High + Chart Canvas + Price Axis Low
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceElevated.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 10.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(safeCandles) {
                        detectTapGestures { offset ->
                            val idx = ((offset.x / size.width.coerceAtLeast(1)) * (safeCandles.size - 1))
                                .roundToInt()
                                .coerceIn(0, safeCandles.lastIndex)
                            touchedIndex = idx
                        }
                    }
                    .pointerInput(safeCandles) {
                        detectHorizontalDragGestures(
                            onDragEnd = { touchedIndex = null },
                            onDragCancel = { touchedIndex = null }
                        ) { change, _ ->
                            val idx = ((change.position.x / size.width.coerceAtLeast(1)) * (safeCandles.size - 1))
                                .roundToInt()
                                .coerceIn(0, safeCandles.lastIndex)
                            touchedIndex = idx
                        }
                    }
            ) {
                val allVals = safeCandles.flatMap { listOf(it.high, it.low, it.close) } + ema9 + ema21
                val minVal = allVals.minOrNull() ?: 0.0
                val maxVal = allVals.maxOrNull() ?: 1.0
                val range = (maxVal - minVal).takeIf { it > 1e-9 } ?: (maxVal * 0.002).coerceAtLeast(0.0001)
                val padTop = 14f
                val padBottom = 14f
                val chartH = (size.height - padTop - padBottom).coerceAtLeast(1f)
                val count = safeCandles.size
                val stepX = size.width / (count - 1).coerceAtLeast(1).toFloat()

                fun yFor(value: Double): Float {
                    val norm = ((value - minVal) / range).toFloat().coerceIn(0f, 1f)
                    return padTop + chartH * (1f - norm)
                }

                // Grid lines
                val gridDash = PathEffect.dashPathEffect(floatArrayOf(6f, 8f))
                for (i in 0..3) {
                    val y = padTop + (chartH / 3f) * i
                    drawLine(
                        color = BorderSubtle.copy(alpha = 0.55f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f,
                        pathEffect = gridDash
                    )
                }

                fun buildLinePath(series: List<Double>): Path {
                    val path = Path()
                    series.forEachIndexed { idx, v ->
                        val x = idx * stepX
                        val y = yFor(v)
                        if (idx == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }
                    return path
                }

                if (chartStyle == PriceChartStyle.AREA) {
                    val linePath = buildLinePath(closes)
                    val fillPath = Path().apply {
                        addPath(linePath)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(priceColor.copy(alpha = 0.28f), Color.Transparent)
                        )
                    )
                    drawPath(
                        path = linePath,
                        color = priceColor,
                        style = Stroke(width = 3.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                } else {
                    val candleWidth = (stepX * 0.56f).coerceIn(3f, 18f)
                    safeCandles.forEachIndexed { idx, c ->
                        val cx = idx * stepX
                        val bullish = c.close >= c.open
                        val barColor = if (bullish) EmeraldProfit else CrimsonLoss
                        val yHigh = yFor(c.high)
                        val yLow = yFor(c.low)
                        val yOpen = yFor(c.open)
                        val yClose = yFor(c.close)
                        val isActiveStick = touchedIndex == idx
                        if (isActiveStick) {
                            drawRect(
                                color = ElectricCyan.copy(alpha = 0.16f),
                                topLeft = Offset(cx - candleWidth, padTop),
                                size = androidx.compose.ui.geometry.Size(candleWidth * 2f, chartH)
                            )
                        }
                        drawLine(
                            color = if (isActiveStick) ElectricCyan else barColor.copy(alpha = 0.88f),
                            start = Offset(cx, yHigh),
                            end = Offset(cx, yLow),
                            strokeWidth = if (isActiveStick) 2.8f else 2f
                        )
                        val top = minOf(yOpen, yClose)
                        val bodyH = kotlin.math.abs(yClose - yOpen).coerceAtLeast(2.5f)
                        drawRect(
                            color = barColor,
                            topLeft = Offset(cx - candleWidth / 2f, top),
                            size = androidx.compose.ui.geometry.Size(candleWidth, bodyH)
                        )
                    }
                }

                // EMA overlays
                if (ema9.size == count) {
                    drawPath(
                        path = buildLinePath(ema9),
                        color = ElectricCyan.copy(alpha = 0.88f),
                        style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                    )
                }
                if (ema21.size == count) {
                    drawPath(
                        path = buildLinePath(ema21),
                        color = AmberMetal.copy(alpha = 0.88f),
                        style = Stroke(width = 2.2f, cap = StrokeCap.Round)
                    )
                }

                // Live current spot reference line + pulsing end beacon
                val lastY = yFor(closes.last())
                drawLine(
                    color = priceColor.copy(alpha = 0.45f),
                    start = Offset(0f, lastY),
                    end = Offset(size.width, lastY),
                    strokeWidth = 1.5f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f))
                )
                drawCircle(
                    color = priceColor.copy(alpha = 0.25f),
                    radius = 12f,
                    center = Offset(size.width, lastY)
                )
                drawCircle(
                    color = priceColor,
                    radius = 5.5f,
                    center = Offset(size.width, lastY)
                )

                // Interactive crosshair
                touchedIndex?.let { idx ->
                    val x = idx * stepX
                    val y = yFor(safeCandles[idx].close)
                    drawLine(
                        color = Color.White.copy(alpha = 0.5f),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = 1.6f
                    )
                    drawCircle(color = Color.White, radius = 6.5f, center = Offset(x, y))
                    drawCircle(color = priceColor, radius = 4.5f, center = Offset(x, y))
                }
            }

            // High / Low price labels anchored cleanly on left
            Text(
                text = "H ${FormattingUtils.formatAssetPrice(maxHigh, assetClass, symbol)}",
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = TextMuted,
                modifier = Modifier.align(Alignment.TopStart)
            )
            Text(
                text = "L ${FormattingUtils.formatAssetPrice(minLow, assetClass, symbol)}",
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = TextMuted,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // Time axis strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val midCandle = safeCandles[safeCandles.size / 2]
            Text(
                text = FormattingUtils.formatTime(safeCandles.first().openTimeMs),
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = TextMuted
            )
            Text(
                text = FormattingUtils.formatTime(midCandle.openTimeMs),
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = TextMuted
            )
            Text(
                text = "LIVE ${FormattingUtils.formatTime(safeCandles.last().openTimeMs)}",
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = ElectricCyan
            )
        }
    }
}
