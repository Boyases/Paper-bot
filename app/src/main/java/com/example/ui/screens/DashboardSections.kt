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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.market.MarketFeedState
import com.example.data.model.MarketAsset
import com.example.data.model.WalletAccount
import com.example.data.model.WalletTransaction
import com.example.ui.components.AssetClassPill
import com.example.ui.components.FormattingUtils
import com.example.ui.components.MetricChip
import com.example.ui.components.PriceChartStyle
import com.example.ui.components.RealtimePriceHistoryChart
import com.example.ui.components.RsiGaugeBar
import com.example.ui.theme.AmberContainer
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

@Composable
fun WalletCashActionsBar(
    wallet: WalletAccount,
    onOpenDeposit: () -> Unit,
    onOpenWithdraw: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "AVAILABLE CASH TREASURY",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = FormattingUtils.formatUsd(wallet.availableCashUsd),
                        style = MaterialTheme.typography.titleLarge.merge(MonoNumberStyle),
                        color = TextPrimary,
                        modifier = Modifier.testTag("wallet_available_cash_text")
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Deposited ${FormattingUtils.formatCompactUsd(wallet.totalDepositedUsd)}",
                    style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                    color = EmeraldProfit
                )
                Text(
                    text = "Withdrawn ${FormattingUtils.formatCompactUsd(wallet.totalWithdrawnUsd)}",
                    style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = onOpenDeposit,
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldProfit,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("open_deposit_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Deposit",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            OutlinedButton(
                onClick = onOpenWithdraw,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AmberMetal
                ),
                shape = RoundedCornerShape(12.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .testTag("open_withdraw_button")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowUpward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Withdraw",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}

@Composable
fun RealtimeAssetHistoryCard(
    marketFeed: MarketFeedState,
    selectedMarketKey: String?,
    pinnedMarketKeys: Set<String> = emptySet(),
    chartStyle: PriceChartStyle = PriceChartStyle.CANDLES,
    onChartStyleChange: (PriceChartStyle) -> Unit = {},
    onTogglePinMarket: (MarketAsset) -> Unit = {},
    onSelectAsset: (MarketAsset) -> Unit,
    onDeployBotOnAsset: (MarketAsset) -> Unit,
    modifier: Modifier = Modifier
) {
    val orderedAssets = remember(marketFeed.assets, pinnedMarketKeys) {
        marketFeed.assets.sortedByDescending { it.marketKey in pinnedMarketKeys }
    }
    val selectedAsset = remember(orderedAssets, selectedMarketKey) {
        orderedAssets.firstOrNull { it.marketKey == selectedMarketKey }
            ?: orderedAssets.firstOrNull()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .border(1.dp, ElectricCyan.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (selectedAsset != null) EmeraldProfit else AmberMetal)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Real-Time Price History",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PriceChartStyle.entries.forEach { style ->
                    val active = chartStyle == style
                    Surface(
                        color = if (active) ElectricCyan.copy(alpha = 0.2f) else SurfaceElevated,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { onChartStyleChange(style) }
                            .testTag("chart_style_${style.name.lowercase()}")
                    ) {
                        Text(
                            text = style.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (active) ElectricCyan else TextSecondary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (orderedAssets.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (marketFeed.isLoading) {
                        CircularProgressIndicator(color = ElectricCyan, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(
                        text = marketFeed.errorMessage ?: "Connecting live price feed...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (marketFeed.errorMessage != null) CrimsonLoss else TextSecondary
                    )
                }
            }
            return@Column
        }

        // Selectable trading asset chips right on the main screen (pinned markets first)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            orderedAssets.forEach { asset ->
                val isSelected = selectedAsset != null &&
                    asset.exchange == selectedAsset.exchange &&
                    asset.symbol == selectedAsset.symbol
                val isPinned = asset.marketKey in pinnedMarketKeys
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelectAsset(asset) },
                    label = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (isPinned) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = "Selected market",
                                    tint = EmeraldProfit,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                            Text(
                                text = asset.symbol,
                                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle)
                            )
                        }
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("dashboard_asset_chip_${asset.exchange.id}_${asset.symbol}")
                )
            }
        }

        if (selectedAsset != null) {
            val isUp = selectedAsset.change24hPct >= 0.0
            val changeColor = if (isUp) EmeraldProfit else CrimsonLoss

            Spacer(modifier = Modifier.height(10.dp))

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
                        AssetClassPill(assetClass = selectedAsset.assetClass)
                        Text(
                            text = "${selectedAsset.exchange.displayName} • ${selectedAsset.sourceLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${selectedAsset.symbol} • ${selectedAsset.displayName}",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = FormattingUtils.formatAssetPrice(
                            selectedAsset.price,
                            selectedAsset.assetClass,
                            selectedAsset.symbol
                        ),
                        style = MaterialTheme.typography.headlineSmall.merge(MonoNumberStyle),
                        color = TextPrimary,
                        modifier = Modifier.testTag("selected_asset_live_price")
                    )
                    Surface(
                        color = if (isUp) EmeraldContainer else CrimsonContainer,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = FormattingUtils.formatPct(selectedAsset.change24hPct),
                            style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                            color = changeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            RealtimePriceHistoryChart(
                candles = selectedAsset.candles,
                assetClass = selectedAsset.assetClass,
                symbol = selectedAsset.symbol,
                livePrice = selectedAsset.price,
                chartStyle = chartStyle
            )

            Spacer(modifier = Modifier.height(12.dp))

            RsiGaugeBar(rsi = selectedAsset.indicators.rsi14)

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricChip(
                    label = "Signal",
                    value = selectedAsset.indicators.signalLabel,
                    valueColor = ElectricCyan,
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "24h High",
                    value = FormattingUtils.formatAssetPrice(
                        selectedAsset.high24h,
                        selectedAsset.assetClass,
                        selectedAsset.symbol
                    ),
                    valueColor = EmeraldProfit,
                    modifier = Modifier.weight(1f)
                )
                MetricChip(
                    label = "Volatility",
                    value = FormattingUtils.formatPct(selectedAsset.indicators.volatilityPct, false),
                    valueColor = AmberMetal,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isSelectedPinned = selectedAsset.marketKey in pinnedMarketKeys
                OutlinedButton(
                    onClick = { onTogglePinMarket(selectedAsset) },
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("dashboard_pin_selected_asset")
                ) {
                    Icon(
                        imageVector = if (isSelectedPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = if (isSelectedPinned) "Unpin asset" else "Select asset",
                        tint = if (isSelectedPinned) EmeraldProfit else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isSelectedPinned) "Pinned" else "Select",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelectedPinned) EmeraldProfit else TextSecondary
                    )
                }
                FilledTonalButton(
                    onClick = { onDeployBotOnAsset(selectedAsset) },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = SurfaceElevated,
                        contentColor = ElectricCyan
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("dashboard_deploy_selected_asset")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Automate ${selectedAsset.symbol}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

@Composable
fun WalletTransactionRow(
    tx: WalletTransaction,
    modifier: Modifier = Modifier
) {
    val isDeposit = tx.type == "DEPOSIT"
    val accent = if (isDeposit) EmeraldProfit else AmberMetal
    val container = if (isDeposit) EmeraldContainer else AmberContainer

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(color = container, shape = RoundedCornerShape(10.dp)) {
                Icon(
                    imageVector = if (isDeposit) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                    contentDescription = tx.type,
                    tint = accent,
                    modifier = Modifier.padding(8.dp).size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = "${if (isDeposit) "Deposit" else "Withdraw"} • ${tx.method}",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (tx.note.isNotBlank()) {
                        "${tx.note} • ${FormattingUtils.formatShortDateTime((tx.createdAt?.seconds ?: 0L) * 1000L)}"
                    } else {
                        FormattingUtils.formatShortDateTime((tx.createdAt?.seconds ?: 0L) * 1000L)
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = FormattingUtils.formatUsd(
                    if (isDeposit) tx.amountUsd else -tx.amountUsd,
                    showSign = true
                ),
                style = MaterialTheme.typography.titleSmall.merge(MonoNumberStyle),
                color = accent
            )
            Text(
                text = "Bal ${FormattingUtils.formatUsd(tx.balanceAfterUsd)}",
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = TextSecondary
            )
        }
    }
}
