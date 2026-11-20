package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.data.model.MarketAsset
import com.example.data.model.MarketTarget
import com.example.ui.components.AssetClassPill
import com.example.ui.components.FormattingUtils
import com.example.ui.components.MetricChip
import com.example.ui.components.PriceChartStyle
import com.example.ui.components.RealtimePriceHistoryChart
import com.example.ui.components.RsiGaugeBar
import com.example.ui.components.SparklineChart
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

@Composable
fun MarketsScreen(
    marketFeed: MarketFeedState,
    selectedMarketKey: String?,
    pinnedMarketKeys: Set<String>,
    chartStyle: PriceChartStyle,
    onChartStyleChange: (PriceChartStyle) -> Unit,
    onSelectAsset: (MarketAsset) -> Unit,
    onTogglePinMarket: (MarketAsset) -> Unit,
    onClearPinnedMarkets: () -> Unit,
    onRefreshMarkets: () -> Unit,
    onDeployBotOnMarkets: (List<MarketTarget>) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<AssetClass?>(null) }

    val filteredAssets = remember(marketFeed.assets, selectedCategory, pinnedMarketKeys) {
        val categoryList = if (selectedCategory == null) {
            marketFeed.assets
        } else {
            marketFeed.assets.filter { it.assetClass == selectedCategory }
        }
        categoryList.sortedByDescending { it.marketKey in pinnedMarketKeys }
    }

    val pinnedAssets = remember(marketFeed.assets, pinnedMarketKeys) {
        marketFeed.assets.filter { it.marketKey in pinnedMarketKeys }
    }

    val activeAsset = remember(marketFeed.assets, filteredAssets, selectedMarketKey) {
        marketFeed.assets.firstOrNull { it.marketKey == selectedMarketKey }
            ?: filteredAssets.firstOrNull()
            ?: marketFeed.assets.firstOrNull()
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Live Market Feeds",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
                Text(
                    text = if (marketFeed.activeSources.isNotEmpty()) {
                        marketFeed.activeSources.joinToString(" • ")
                    } else "Connecting to real-time venues...",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
            IconButton(
                onClick = onRefreshMarkets,
                modifier = Modifier.testTag("markets_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Refresh feeds",
                    tint = ElectricCyan
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedCategory == null,
                onClick = { selectedCategory = null },
                label = { Text("All Venues") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                    selectedLabelColor = ElectricCyan
                ),
                modifier = Modifier.testTag("market_tab_all")
            )
            AssetClass.entries.forEach { ac ->
                FilterChip(
                    selected = selectedCategory == ac,
                    onClick = { selectedCategory = ac },
                    label = { Text(ac.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("market_tab_${ac.id.lowercase()}")
                )
            }
        }

        if (pinnedAssets.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            SelectedMarketsBar(
                pinnedAssets = pinnedAssets,
                onSelectAsset = onSelectAsset,
                onRemovePinned = onTogglePinMarket,
                onClearAll = onClearPinnedMarkets,
                onDeploySelected = {
                    onDeployBotOnMarkets(
                        pinnedAssets.map { MarketTarget(it.assetClass, it.exchange, it.symbol) }
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (marketFeed.isLoading && marketFeed.assets.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = ElectricCyan)
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Streaming live Crypto, Metals & Forex quotes...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        } else if (marketFeed.assets.isEmpty() && marketFeed.errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = marketFeed.errorMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = CrimsonLoss
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRefreshMarkets) { Text("Retry Live Feeds") }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (activeAsset != null) {
                    item(key = "selected_terminal_chart") {
                        val isActivePinned = activeAsset.marketKey in pinnedMarketKeys
                        SelectedAssetAnalyticsCard(
                            asset = activeAsset,
                            isPinned = isActivePinned,
                            pinnedCount = pinnedAssets.size,
                            chartStyle = chartStyle,
                            onChartStyleChange = onChartStyleChange,
                            onTogglePin = { onTogglePinMarket(activeAsset) },
                            onDeployBot = {
                                val targets = if (pinnedAssets.isNotEmpty()) {
                                    pinnedAssets.map { MarketTarget(it.assetClass, it.exchange, it.symbol) }
                                } else {
                                    listOf(
                                        MarketTarget(
                                            activeAsset.assetClass,
                                            activeAsset.exchange,
                                            activeAsset.symbol
                                        )
                                    )
                                }
                                onDeployBotOnMarkets(targets)
                            }
                        )
                    }
                }

                items(
                    items = filteredAssets,
                    key = { "${it.exchange.id}_${it.symbol}" }
                ) { asset ->
                    val isSelected = activeAsset != null &&
                        asset.exchange == activeAsset.exchange &&
                        asset.symbol == activeAsset.symbol
                    val isPinned = asset.marketKey in pinnedMarketKeys
                    MarketTickerRow(
                        asset = asset,
                        isSelected = isSelected,
                        isPinned = isPinned,
                        onClick = { onSelectAsset(asset) },
                        onTogglePin = { onTogglePinMarket(asset) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedMarketsBar(
    pinnedAssets: List<MarketAsset>,
    onSelectAsset: (MarketAsset) -> Unit,
    onRemovePinned: (MarketAsset) -> Unit,
    onClearAll: () -> Unit,
    onDeploySelected: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, EmeraldProfit.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = null,
                    tint = EmeraldProfit,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "Selected in Market (${pinnedAssets.size})",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onClearAll,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.testTag("clear_pinned_markets_button")
                ) {
                    Text("Clear", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                }
                Spacer(modifier = Modifier.width(4.dp))
                FilledTonalButton(
                    onClick = onDeploySelected,
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = EmeraldProfit.copy(alpha = 0.2f),
                        contentColor = EmeraldProfit
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("deploy_selected_markets_button")
                ) {
                    Text(
                        text = "Automate (${pinnedAssets.size})",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            pinnedAssets.forEach { asset ->
                Surface(
                    color = SurfaceElevated,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.clickable { onSelectAsset(asset) }
                ) {
                    Row(
                        modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "${asset.symbol} (${asset.exchange.displayName})",
                            style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                            color = TextPrimary
                        )
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Deselect ${asset.symbol}",
                            tint = TextSecondary,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { onRemovePinned(asset) }
                                .padding(2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedAssetAnalyticsCard(
    asset: MarketAsset,
    isPinned: Boolean,
    pinnedCount: Int,
    chartStyle: PriceChartStyle,
    onChartStyleChange: (PriceChartStyle) -> Unit,
    onTogglePin: () -> Unit,
    onDeployBot: () -> Unit
) {
    val isUp = asset.change24hPct >= 0.0
    val changeColor = if (isUp) EmeraldProfit else CrimsonLoss

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .border(1.dp, ElectricCyan.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AssetClassPill(assetClass = asset.assetClass)
                    Text(
                        text = asset.exchange.displayName,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${asset.symbol}  •  ${asset.displayName}",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = FormattingUtils.formatAssetPrice(asset.price, asset.assetClass, asset.symbol),
                    style = MaterialTheme.typography.headlineSmall.merge(MonoNumberStyle),
                    color = TextPrimary
                )
                Surface(
                    color = if (isUp) EmeraldContainer else CrimsonContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = FormattingUtils.formatPct(asset.change24hPct),
                        style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                        color = changeColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Chart style (Candles / Sticks vs Area) & Stick/Select in Market Toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriceChartStyle.entries.forEach { style ->
                    val active = chartStyle == style
                    Surface(
                        color = if (active) ElectricCyan.copy(alpha = 0.2f) else SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { onChartStyleChange(style) }
                            .testTag("markets_chart_style_${style.name.lowercase()}")
                    ) {
                        Text(
                            text = if (style == PriceChartStyle.CANDLES) "Candlesticks" else style.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (active) ElectricCyan else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Surface(
                color = if (isPinned) EmeraldProfit.copy(alpha = 0.18f) else SurfaceElevated,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .clickable(onClick = onTogglePin)
                    .testTag("pin_active_market_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isPinned) "Unpin market" else "Select market",
                        tint = if (isPinned) EmeraldProfit else TextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isPinned) "Selected" else "Select Pair",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isPinned) EmeraldProfit else TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        RealtimePriceHistoryChart(
            candles = asset.candles,
            assetClass = asset.assetClass,
            symbol = asset.symbol,
            livePrice = asset.price,
            chartStyle = chartStyle
        )

        Spacer(modifier = Modifier.height(12.dp))

        RsiGaugeBar(rsi = asset.indicators.rsi14)

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricChip(
                label = "Signal",
                value = asset.indicators.signalLabel,
                valueColor = ElectricCyan,
                modifier = Modifier.weight(1f)
            )
            MetricChip(
                label = "24h Vol",
                value = if (asset.volume24hUsd > 0.0) FormattingUtils.formatCompactUsd(asset.volume24hUsd) else "Spot",
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            MetricChip(
                label = "Volatility",
                value = FormattingUtils.formatPct(asset.indicators.volatilityPct, false),
                valueColor = TextPrimary,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Button(
            onClick = onDeployBot,
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = ObsidianBg
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("deploy_bot_on_selected_asset")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (pinnedCount > 1) {
                    "Deploy Bot on $pinnedCount Selected Markets"
                } else {
                    "Deploy Bot on ${asset.symbol} (${asset.exchange.displayName})"
                },
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun MarketTickerRow(
    asset: MarketAsset,
    isSelected: Boolean,
    isPinned: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit
) {
    val isUp = asset.change24hPct >= 0.0
    val changeColor = if (isUp) EmeraldProfit else CrimsonLoss

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) SurfaceElevated else SurfaceDark)
            .border(
                1.dp,
                when {
                    isSelected -> ElectricCyan
                    isPinned -> EmeraldProfit.copy(alpha = 0.6f)
                    else -> BorderSubtle
                },
                RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1.2f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = asset.symbol,
                    style = MaterialTheme.typography.titleSmall.merge(MonoNumberStyle).copy(fontWeight = FontWeight.Bold),
                    color = TextPrimary
                )
                AssetClassPill(assetClass = asset.assetClass)
            }
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = "${asset.exchange.displayName} • ${asset.indicators.signalLabel}",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        SparklineChart(
            candles = asset.candles,
            isPositive = isUp,
            modifier = Modifier
                .width(64.dp)
                .height(28.dp)
                .padding(horizontal = 4.dp)
        )

        Column(
            modifier = Modifier.weight(0.95f),
            horizontalAlignment = Alignment.End
        ) {
            Text(
                text = FormattingUtils.formatAssetPrice(asset.price, asset.assetClass, asset.symbol),
                style = MaterialTheme.typography.titleSmall.merge(MonoNumberStyle).copy(fontWeight = FontWeight.Bold),
                color = TextPrimary
            )
            Text(
                text = FormattingUtils.formatPct(asset.change24hPct),
                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                color = changeColor
            )
        }

        IconButton(
            onClick = onTogglePin,
            modifier = Modifier.testTag("market_pin_toggle_${asset.exchange.id}_${asset.symbol}")
        ) {
            Icon(
                imageVector = if (isPinned) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = if (isPinned) "Deselect ${asset.symbol}" else "Select ${asset.symbol}",
                tint = if (isPinned) EmeraldProfit else TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
