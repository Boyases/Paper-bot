package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.market.MarketFeedState
import com.example.data.model.AssetClass
import com.example.data.model.MarketAsset
import com.example.data.model.RiskProfile
import com.example.data.model.TradeExecution
import com.example.data.model.TradingBot
import com.example.data.model.WalletAccount
import com.example.data.model.WalletTransaction
import com.example.domain.engine.PortfolioAnalytics
import com.example.ui.components.AssetClassPill
import com.example.ui.components.EquityCurveChart
import com.example.ui.components.FormattingUtils
import com.example.ui.components.MetricChip
import com.example.ui.components.PriceChartStyle
import com.example.ui.theme.AmberContainer
import com.example.ui.theme.AmberMetal
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonContainer
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldContainer
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.IndigoForex
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.viewmodel.UiState

@Composable
fun DashboardScreen(
    analytics: PortfolioAnalytics,
    marketFeed: MarketFeedState,
    selectedMarketKey: String?,
    pinnedMarketKeys: Set<String> = emptySet(),
    chartStyle: PriceChartStyle = PriceChartStyle.CANDLES,
    botsState: UiState<List<TradingBot>>,
    tradesState: UiState<List<TradeExecution>>,
    riskProfile: RiskProfile,
    wallet: WalletAccount,
    walletTransactions: List<WalletTransaction>,
    onSelectMarketAsset: (MarketAsset) -> Unit,
    onTogglePinMarket: (MarketAsset) -> Unit = {},
    onChartStyleChange: (PriceChartStyle) -> Unit = {},
    onRefreshMarkets: () -> Unit,
    onEmergencyHaltAll: () -> Unit,
    onResetCircuitBreaker: () -> Unit,
    onOpenDeposit: () -> Unit,
    onOpenWithdraw: () -> Unit,
    onDeployBotOnAsset: (MarketAsset) -> Unit,
    onNavigateToBots: () -> Unit,
    onOpenCreateBot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bots = (botsState as? UiState.Success)?.data ?: emptyList()
    val trades = (tradesState as? UiState.Success)?.data ?: emptyList()
    val netPnl = analytics.realizedPnlUsd + analytics.unrealizedPnlUsd
    val pnlColor = if (netPnl >= 0.0) EmeraldProfit else CrimsonLoss

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Live Terminal Status Bar + Emergency Kill Switch
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (marketFeed.errorMessage == null) EmeraldProfit else AmberMetal)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "QuantPulse Terminal",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = if (marketFeed.lastSyncEpochMs > 0L) {
                                "${marketFeed.activeSources.size} feeds live • ${FormattingUtils.formatTime(marketFeed.lastSyncEpochMs)}"
                            } else {
                                "Syncing live exchanges..."
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onRefreshMarkets,
                        modifier = Modifier.testTag("refresh_markets_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh live market feeds",
                            tint = ElectricCyan
                        )
                    }
                    if (analytics.activeBotsCount > 0 && !riskProfile.circuitBreakerTriggered) {
                        FilledTonalButton(
                            onClick = onEmergencyHaltAll,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CrimsonContainer,
                                contentColor = CrimsonLoss
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("kill_switch_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PowerSettingsNew,
                                contentDescription = "Emergency Halt All Bots",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "HALT",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Circuit Breaker Banner
        item {
            AnimatedVisibility(visible = riskProfile.circuitBreakerTriggered) {
                Surface(
                    color = CrimsonContainer,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, CrimsonLoss.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Circuit breaker active",
                                tint = CrimsonLoss
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "CIRCUIT BREAKER ACTIVE",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = CrimsonLoss
                                )
                                Text(
                                    text = "Automated order execution is halted.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextPrimary
                                )
                            }
                        }
                        FilledTonalButton(
                            onClick = onResetCircuitBreaker,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = CrimsonLoss,
                                contentColor = Color.White
                            ),
                            modifier = Modifier.testTag("reset_breaker_banner_button")
                        ) {
                            Text("Reset")
                        }
                    }
                }
            }
        }

        // Portfolio Net Equity Hero Card + Real-time Equity Curve
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(SurfaceDark, SurfaceElevated)
                        )
                    )
                    .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                    .padding(18.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TOTAL BOT EQUITY",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        Surface(
                            color = if (netPnl >= 0) EmeraldContainer else CrimsonContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Net P&L ${FormattingUtils.formatUsd(netPnl, showSign = true)}",
                                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                                color = pnlColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = FormattingUtils.formatUsd(analytics.netEquityUsd + wallet.availableCashUsd),
                        style = MaterialTheme.typography.displaySmall.merge(MonoNumberStyle),
                        color = TextPrimary
                    )
                    Text(
                        text = "Bot Equity ${FormattingUtils.formatUsd(analytics.netEquityUsd)} • Cash ${FormattingUtils.formatUsd(wallet.availableCashUsd)}",
                        style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                        color = TextSecondary
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    EquityCurveChart(points = analytics.equityCurve)

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricChip(
                            label = "Realized",
                            value = FormattingUtils.formatUsd(analytics.realizedPnlUsd, showSign = true),
                            valueColor = if (analytics.realizedPnlUsd >= 0) EmeraldProfit else CrimsonLoss,
                            modifier = Modifier.weight(1f)
                        )
                        MetricChip(
                            label = "Unrealized",
                            value = FormattingUtils.formatUsd(analytics.unrealizedPnlUsd, showSign = true),
                            valueColor = if (analytics.unrealizedPnlUsd >= 0) EmeraldProfit else CrimsonLoss,
                            modifier = Modifier.weight(1f)
                        )
                        MetricChip(
                            label = "Win Rate",
                            value = FormattingUtils.formatPct(analytics.winRatePct, showSign = false),
                            valueColor = ElectricCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricChip(
                            label = "Active Bots",
                            value = "${analytics.activeBotsCount} / ${bots.size}",
                            valueColor = EmeraldProfit,
                            modifier = Modifier.weight(1f)
                        )
                        MetricChip(
                            label = "Profit Factor",
                            value = String.format(java.util.Locale.US, "%.2f", analytics.profitFactor),
                            valueColor = TextPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        MetricChip(
                            label = "Max DD",
                            value = FormattingUtils.formatPct(-analytics.maxDrawdownPct, showSign = false),
                            valueColor = AmberMetal,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Treasury Cash Balance + Deposit & Withdraw
        item {
            WalletCashActionsBar(
                wallet = wallet,
                onOpenDeposit = onOpenDeposit,
                onOpenWithdraw = onOpenWithdraw
            )
        }

        // Real-Time Price History Visualization for the Selected Trading Asset
        item {
            RealtimeAssetHistoryCard(
                marketFeed = marketFeed,
                selectedMarketKey = selectedMarketKey,
                pinnedMarketKeys = pinnedMarketKeys,
                chartStyle = chartStyle,
                onChartStyleChange = onChartStyleChange,
                onTogglePinMarket = onTogglePinMarket,
                onSelectAsset = onSelectMarketAsset,
                onDeployBotOnAsset = onDeployBotOnAsset
            )
        }

        // Capital Allocation by Asset Class (Crypto / Metals / Forex)
        item {
            CapitalAllocationCard(bots = bots, onOpenCreateBot = onOpenCreateBot)
        }

        // Recent Deposit & Withdraw Transfers
        if (walletTransactions.isNotEmpty()) {
            item {
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
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Recent Treasury Transfers",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                    }
                    Text(
                        text = "${walletTransactions.size} entries",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
            items(walletTransactions.take(5), key = { it.id }) { tx ->
                WalletTransactionRow(tx = tx)
            }
        }

        // Automated Trade Executions Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = ElectricCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Automated Execution Log",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextPrimary
                    )
                }
                Text(
                    text = "${trades.size} fills",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        }

        when (tradesState) {
            is UiState.Loading -> {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ElectricCyan)
                    }
                }
            }
            is UiState.Error -> {
                item {
                    Surface(
                        color = CrimsonContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = tradesState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = CrimsonLoss,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            }
            is UiState.Success -> {
                if (trades.isEmpty()) {
                    item {
                        EmptyAutomationCard(
                            hasBots = bots.isNotEmpty(),
                            onOpenCreateBot = onOpenCreateBot,
                            onNavigateToBots = onNavigateToBots
                        )
                    }
                } else {
                    items(trades.take(25), key = { it.id }) { trade ->
                        TradeExecutionCard(trade = trade)
                    }
                }
            }
        }
    }
}

@Composable
private fun CapitalAllocationCard(
    bots: List<TradingBot>,
    onOpenCreateBot: () -> Unit
) {
    val total = bots.sumOf { it.allocatedCapitalUsd }.coerceAtLeast(1.0)
    val cryptoTotal = bots.filter { it.assetClass == AssetClass.CRYPTO.id }.sumOf { it.allocatedCapitalUsd }
    val metalsTotal = bots.filter { it.assetClass == AssetClass.METALS.id }.sumOf { it.allocatedCapitalUsd }
    val forexTotal = bots.filter { it.assetClass == AssetClass.FOREX.id }.sumOf { it.allocatedCapitalUsd }

    Column(
        modifier = Modifier
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
            Text(
                text = "Multi-Asset Bot Exposure",
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = FormattingUtils.formatUsd(bots.sumOf { it.allocatedCapitalUsd }),
                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                color = ElectricCyan
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        AllocationBarRow(
            label = "Crypto Exchanges",
            amount = cryptoTotal,
            fraction = if (bots.isEmpty()) 0f else (cryptoTotal / total).toFloat(),
            color = ElectricCyan
        )
        Spacer(modifier = Modifier.height(8.dp))
        AllocationBarRow(
            label = "Precious Metals",
            amount = metalsTotal,
            fraction = if (bots.isEmpty()) 0f else (metalsTotal / total).toFloat(),
            color = AmberMetal
        )
        Spacer(modifier = Modifier.height(8.dp))
        AllocationBarRow(
            label = "Interbank Forex",
            amount = forexTotal,
            fraction = if (bots.isEmpty()) 0f else (forexTotal / total).toFloat(),
            color = IndigoForex
        )

        if (bots.isEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onOpenCreateBot,
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dashboard_deploy_first_bot")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Deploy First Automated Bot", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun AllocationBarRow(
    label: String,
    amount: Double,
    fraction: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(
                text = FormattingUtils.formatUsd(amount),
                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                color = TextPrimary
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            color = color,
            trackColor = SurfaceElevated,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
        )
    }
}

@Composable
private fun TradeExecutionCard(trade: TradeExecution) {
    val assetClass = AssetClass.fromId(trade.assetClass)
    val isBuy = trade.side == "BUY"
    val sideColor = if (isBuy) EmeraldProfit else CrimsonLoss
    val pnlColor = if (trade.pnlUsd >= 0) EmeraldProfit else CrimsonLoss

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = if (isBuy) EmeraldContainer else CrimsonContainer,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = "${trade.side} • ${trade.orderType}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = sideColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                AssetClassPill(assetClass = assetClass)
                Text(
                    text = trade.symbol,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${trade.botName} • ${trade.exchange}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = trade.reason,
                style = MaterialTheme.typography.labelSmall,
                color = ElectricCyan.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = FormattingUtils.formatAssetPrice(trade.price, assetClass, trade.symbol),
                style = MaterialTheme.typography.titleSmall.merge(MonoNumberStyle),
                color = TextPrimary
            )
            if (trade.side == "SELL") {
                Text(
                    text = FormattingUtils.formatUsd(trade.pnlUsd, showSign = true),
                    style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                    color = pnlColor
                )
            } else {
                Text(
                    text = FormattingUtils.formatUsd(trade.notionalUsd),
                    style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                    color = TextSecondary
                )
            }
            Text(
                text = FormattingUtils.formatShortDateTime((trade.createdAt?.seconds ?: 0L) * 1000L),
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun EmptyAutomationCard(
    hasBots: Boolean,
    onOpenCreateBot: () -> Unit,
    onNavigateToBots: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SurfaceDark)
            .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
            .padding(22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ShowChart,
            contentDescription = null,
            tint = ElectricCyan,
            modifier = Modifier.size(34.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (hasBots) "Monitoring Live Feeds" else "No Automated Trades Yet",
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (hasBots) {
                "Your active bots are analyzing live market candles and waiting for strategy triggers."
            } else {
                "Launch an automated Crypto, Precious Metals, or Forex bot to start real-time execution."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
        Spacer(modifier = Modifier.height(14.dp))
        FilledTonalButton(
            onClick = if (hasBots) onNavigateToBots else onOpenCreateBot,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = SurfaceElevated,
                contentColor = ElectricCyan
            ),
            modifier = Modifier.testTag("empty_state_action_button")
        ) {
            Text(if (hasBots) "Inspect Bots" else "Create Trading Bot")
        }
    }
}
