package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PrecisionManufacturing
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.market.MarketFeedState
import com.example.data.model.AssetClass
import com.example.data.model.BotStatus
import com.example.data.model.BotStrategy
import com.example.data.model.ExchangeVenue
import com.example.data.model.MarketAsset
import com.example.data.model.StrategyCombineMode
import com.example.data.model.TradingBot
import com.example.domain.engine.BotAutomationEngine
import com.example.ui.components.AssetClassPill
import com.example.ui.components.BotStatusBadge
import com.example.ui.components.FormattingUtils
import com.example.ui.theme.AmberMetal
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.UiState

private enum class BotFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    CRYPTO("Crypto"),
    METALS("Metals"),
    FOREX("Forex")
}

@Composable
fun BotsScreen(
    botsState: UiState<List<TradingBot>>,
    marketFeed: MarketFeedState,
    onToggleBotStatus: (TradingBot) -> Unit,
    onEvaluateBotNow: (TradingBot) -> Unit,
    onClosePositionNow: (TradingBot) -> Unit,
    onDeleteBot: (String) -> Unit,
    onOpenCreateBot: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(BotFilter.ALL) }
    val assetMap = remember(marketFeed.assets) {
        marketFeed.assets.associateBy { "${it.exchange.id}:${it.symbol}" }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Automation Bots",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = "Live algorithmic execution across Crypto, Metals & FX",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
            Button(
                onClick = onOpenCreateBot,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("create_bot_header_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Bot", style = MaterialTheme.typography.labelLarge)
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BotFilter.entries.forEach { filter ->
                FilterChip(
                    selected = selectedFilter == filter,
                    onClick = { selectedFilter = filter },
                    label = { Text(filter.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("bot_filter_${filter.name.lowercase()}")
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (botsState) {
            is UiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ElectricCyan)
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                    Text(
                        text = botsState.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CrimsonLoss
                    )
                }
            }
            is UiState.Success -> {
                val filteredBots = remember(botsState.data, selectedFilter) {
                    botsState.data.filter { bot ->
                        when (selectedFilter) {
                            BotFilter.ALL -> true
                            BotFilter.ACTIVE -> bot.status == BotStatus.RUNNING.id
                            BotFilter.CRYPTO -> bot.assetClass == AssetClass.CRYPTO.id
                            BotFilter.METALS -> bot.assetClass == AssetClass.METALS.id
                            BotFilter.FOREX -> bot.assetClass == AssetClass.FOREX.id
                        }
                    }
                }

                if (filteredBots.isEmpty()) {
                    EmptyBotsView(onOpenCreateBot = onOpenCreateBot)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredBots, key = { it.id }) { bot ->
                            val liveAsset = BotAutomationEngine.findMatchingAsset(bot, assetMap)
                            TradingBotCard(
                                bot = bot,
                                liveAsset = liveAsset,
                                onToggleStatus = { onToggleBotStatus(bot) },
                                onEvaluateNow = { onEvaluateBotNow(bot) },
                                onClosePosition = { onClosePositionNow(bot) },
                                onDelete = { onDeleteBot(bot.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TradingBotCard(
    bot: TradingBot,
    liveAsset: MarketAsset?,
    onToggleStatus: () -> Unit,
    onEvaluateNow: () -> Unit,
    onClosePosition: () -> Unit,
    onDelete: () -> Unit
) {
    val assetClass = AssetClass.fromId(bot.assetClass)
    val venue = ExchangeVenue.fromId(bot.exchange)
    val activeStrategies = bot.effectiveStrategies()
    val combineMode = StrategyCombineMode.fromId(bot.strategyMode)
    val status = BotStatus.fromId(bot.status)
    val spotPrice = liveAsset?.price ?: 0.0
    val unrealizedUsd = if (spotPrice > 0.0) bot.unrealizedPnlUsd(spotPrice) else 0.0
    val unrealizedPct = if (spotPrice > 0.0) bot.unrealizedPnlPct(spotPrice) else 0.0

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(
                1.dp,
                if (status == BotStatus.RUNNING) ElectricCyan.copy(alpha = 0.35f) else BorderSubtle,
                RoundedCornerShape(18.dp)
            )
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssetClassPill(assetClass = assetClass)
                    Surface(color = SurfaceElevated, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = venue.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Surface(color = SurfaceElevated, shape = RoundedCornerShape(6.dp)) {
                        Text(
                            text = if (bot.executionMode == "LIVE_API") "LIVE API" else "PAPER",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (bot.executionMode == "LIVE_API") AmberMetal else ElectricCyan,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = bot.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = if (activeStrategies.size == 1) {
                        "${bot.symbol} • ${activeStrategies.first().displayName}"
                    } else {
                        "${bot.symbol} • ${activeStrategies.size} Strategies (${combineMode.label})"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                if (activeStrategies.size > 1) {
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = ElectricCyan.copy(alpha = 0.16f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = combineMode.id,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = ElectricCyan,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        activeStrategies.forEach { strat ->
                            Surface(
                                color = SurfaceElevated,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = strat.shortCode,
                                    style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                                    color = TextPrimary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                BotStatusBadge(status = status)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (spotPrice > 0.0) {
                        FormattingUtils.formatAssetPrice(spotPrice, assetClass, bot.symbol)
                    } else "Syncing...",
                    style = MaterialTheme.typography.titleSmall.merge(MonoNumberStyle),
                    color = ElectricCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Key metrics grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceElevated)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BotMetricItem(
                label = "Capital",
                value = "${FormattingUtils.formatUsd(bot.allocatedCapitalUsd)} (${bot.leverage}x)",
                color = TextPrimary
            )
            BotMetricItem(
                label = "Realized P&L",
                value = FormattingUtils.formatUsd(bot.realizedPnlUsd, showSign = true),
                color = if (bot.realizedPnlUsd >= 0) EmeraldProfit else CrimsonLoss
            )
            BotMetricItem(
                label = "Win / Trades",
                value = "${FormattingUtils.formatPct(bot.winRatePct(), false)} (${bot.totalTrades})",
                color = ElectricCyan
            )
        }

        // Open position banner if active
        if (bot.openPositionSide != "NONE" && bot.openQuantity > 0.0) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (unrealizedUsd >= 0) EmeraldContainer else CrimsonContainer)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "OPEN ${bot.openPositionSide} @ ${FormattingUtils.formatAssetPrice(bot.openEntryPrice, assetClass, bot.symbol)}",
                        style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                        color = TextPrimary
                    )
                    Text(
                        text = "Unrealized: ${FormattingUtils.formatUsd(unrealizedUsd, showSign = true)} (${FormattingUtils.formatPct(unrealizedPct)})",
                        style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                        color = if (unrealizedUsd >= 0) EmeraldProfit else CrimsonLoss
                    )
                }
                OutlinedButton(
                    onClick = onClosePosition,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Close", style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Risk Guardrails summary row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "SL -${bot.stopLossPct}%",
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = CrimsonLoss
            )
            Text(
                text = "TP +${bot.takeProfitPct}%",
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = EmeraldProfit
            )
            Text(
                text = "Trail ${bot.trailingStopPct}%",
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = AmberMetal
            )
            if (liveAsset != null) {
                Text(
                    text = "Signal: ${liveAsset.indicators.signalLabel}",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = ElectricCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = onToggleStatus,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (status == BotStatus.RUNNING) AmberMetal.copy(alpha = 0.2f) else EmeraldContainer,
                        contentColor = if (status == BotStatus.RUNNING) AmberMetal else EmeraldProfit
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("toggle_bot_${bot.id}")
                ) {
                    Icon(
                        imageVector = if (status == BotStatus.RUNNING) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (status == BotStatus.RUNNING) "Pause" else "Resume",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                FilledTonalButton(
                    onClick = onEvaluateNow,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SurfaceElevated,
                        contentColor = ElectricCyan
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("eval_bot_${bot.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Radar,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Scan Now", style = MaterialTheme.typography.labelLarge)
                }
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_bot_${bot.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete bot",
                    tint = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun BotMetricItem(
    label: String,
    value: String,
    color: androidx.compose.ui.graphics.Color
) {
    Column {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = TextSecondary)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle).copy(fontWeight = FontWeight.Bold),
            color = color
        )
    }
}

@Composable
private fun EmptyBotsView(onOpenCreateBot: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(SurfaceDark)
                .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                .padding(26.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PrecisionManufacturing,
                contentDescription = null,
                tint = ElectricCyan,
                modifier = Modifier.size(44.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Trading Bots Matching Filter",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Deploy an automated EMA, RSI, Grid Bot, Martingale, 21 TradingView, DCA, or Bollinger bot on Binance, Coinbase, FxPro, OctaFX, and Metals.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onOpenCreateBot,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBg
                ),
                modifier = Modifier.testTag("empty_bots_deploy_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Trading Bot", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
