package com.example.ui.components

import com.example.data.model.AssetClass
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

object FormattingUtils {

    fun formatUsd(amount: Double, showSign: Boolean = false): String {
        val absVal = abs(amount)
        val formatter = NumberFormat.getNumberInstance(Locale.US).apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
        val formatted = formatter.format(absVal)
        return when {
            amount < 0 -> "-$$formatted"
            showSign && amount > 0 -> "+$$formatted"
            else -> "$$formatted"
        }
    }

    fun formatAssetPrice(price: Double, assetClass: AssetClass, symbol: String = ""): String {
        if (price <= 0.0) return "—"
        return when {
            assetClass == AssetClass.FOREX && symbol.contains("JPY") -> String.format(Locale.US, "%.3f", price)
            assetClass == AssetClass.FOREX -> String.format(Locale.US, "%.4f", price)
            price >= 1000.0 -> {
                val nf = NumberFormat.getNumberInstance(Locale.US).apply {
                    minimumFractionDigits = 2
                    maximumFractionDigits = 2
                }
                "$${nf.format(price)}"
            }
            price >= 1.0 -> String.format(Locale.US, "$%.2f", price)
            else -> String.format(Locale.US, "$%.4f", price)
        }
    }

    fun formatPct(pct: Double, showSign: Boolean = true): String {
        val formatted = String.format(Locale.US, "%.2f%%", abs(pct))
        return when {
            pct < 0 -> "-$formatted"
            showSign && pct > 0 -> "+$formatted"
            else -> formatted
        }
    }

    fun formatCompactUsd(amount: Double): String {
        return when {
            amount >= 1_000_000_000.0 -> String.format(Locale.US, "$%.2fB", amount / 1_000_000_000.0)
            amount >= 1_000_000.0 -> String.format(Locale.US, "$%.2fM", amount / 1_000_000.0)
            amount >= 1_000.0 -> String.format(Locale.US, "$%.1fK", amount / 1_000.0)
            else -> formatUsd(amount)
        }
    }

    fun formatTime(epochMs: Long): String {
        if (epochMs <= 0L) return "—"
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.US)
        return sdf.format(Date(epochMs))
    }

    fun formatShortDateTime(epochMs: Long): String {
        if (epochMs <= 0L) return "Pending"
        val sdf = SimpleDateFormat("MMM d, HH:mm", Locale.US)
        return sdf.format(Date(epochMs))
    }
}
