package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AssetClass
import com.example.data.model.BotStatus
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberMetal
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.IndigoContainer
import com.example.ui.theme.IndigoForex
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun AssetClassPill(assetClass: AssetClass, modifier: Modifier = Modifier) {
    val (bg, fg) = when (assetClass) {
        AssetClass.CRYPTO -> Color(0xFF0A2E3B) to ElectricCyan
        AssetClass.METALS -> AmberContainer to AmberMetal
        AssetClass.FOREX -> IndigoContainer to IndigoForex
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Text(
            text = assetClass.label.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun BotStatusBadge(status: BotStatus, modifier: Modifier = Modifier) {
    val (bg, fg) = when (status) {
        BotStatus.RUNNING -> EmeraldContainer to EmeraldProfit
        BotStatus.PAUSED -> SurfaceElevated to TextSecondary
        BotStatus.HALTED_RISK -> CrimsonContainer to CrimsonLoss
    }
    Surface(
        color = bg,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(fg)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.label.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = fg
            )
        }
    }
}

@Composable
fun RsiGaugeBar(rsi: Double, modifier: Modifier = Modifier) {
    val clamped = rsi.coerceIn(0.0, 100.0)
    val labelColor = when {
        clamped <= 30.0 -> EmeraldProfit
        clamped >= 70.0 -> CrimsonLoss
        else -> ElectricCyan
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "RSI (14)",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
            Text(
                text = String.format(java.util.Locale.US, "%.1f", clamped),
                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                color = labelColor
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            EmeraldProfit.copy(alpha = 0.5f),
                            ElectricCyan.copy(alpha = 0.45f),
                            CrimsonLoss.copy(alpha = 0.55f)
                        )
                    )
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((clamped / 100.0).toFloat().coerceIn(0.03f, 1f))
                    .height(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(TextPrimary)
                        .border(2.dp, labelColor, CircleShape)
                )
            }
        }
    }
}

@Composable
fun MetricChip(
    label: String,
    value: String,
    valueColor: Color = TextPrimary,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceElevated)
            .border(1.dp, BorderSubtle.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall.merge(MonoNumberStyle).copy(fontWeight = FontWeight.Bold),
            color = valueColor
        )
    }
}
