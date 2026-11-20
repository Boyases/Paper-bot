package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.AssetClass
import com.example.data.model.BotStrategy
import com.example.data.model.ExchangeVenue
import com.example.data.model.ExecutionMode
import com.example.data.model.MarketTarget
import com.example.data.model.RiskProfile
import com.example.data.model.StrategyCombineMode
import com.example.ui.theme.AmberMetal
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CrimsonLoss
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldProfit
import com.example.ui.theme.MonoNumberStyle
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CreateBotSheet(
    riskProfile: RiskProfile,
    initialTargets: List<MarketTarget> = listOf(
        MarketTarget(AssetClass.CRYPTO, ExchangeVenue.BINANCE, "BTC/USDT")
    ),
    onDismiss: () -> Unit,
    onCreateBots: (
        name: String,
        targets: List<MarketTarget>,
        strategies: List<BotStrategy>,
        strategyMode: StrategyCombineMode,
        executionMode: ExecutionMode,
        allocatedCapitalUsd: Double,
        leverage: Double,
        stopLossPct: Double,
        takeProfitPct: Double,
        trailingStopPct: Double,
        maxPositionPct: Double,
        rsiOversold: Long,
        rsiOverbought: Long,
        gridLevels: Long
    ) -> Unit
) {
    val safeInitialTargets = remember(initialTargets) {
        initialTargets.distinctBy { it.marketKey }.ifEmpty {
            listOf(MarketTarget(AssetClass.CRYPTO, ExchangeVenue.BINANCE, "BTC/USDT"))
        }
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var botName by remember { mutableStateOf("") }
    var selectedAssetClass by remember { mutableStateOf(safeInitialTargets.first().assetClass) }
    var selectedExchange by remember { mutableStateOf(safeInitialTargets.first().exchange) }
    var selectedTargets by remember { mutableStateOf(safeInitialTargets) }
    var selectedStrategies by remember { mutableStateOf(setOf(BotStrategy.EMA_CROSS)) }
    var strategyMode by remember { mutableStateOf(StrategyCombineMode.ANY) }
    var selectedMode by remember { mutableStateOf(ExecutionMode.PAPER) }
    var capitalText by remember { mutableStateOf("2500") }
    var leverage by remember { mutableDoubleStateOf(1.0) }
    var stopLossPct by remember { mutableDoubleStateOf(riskProfile.defaultStopLossPct) }
    var takeProfitPct by remember { mutableDoubleStateOf(riskProfile.defaultTakeProfitPct) }
    var trailingStopPct by remember { mutableDoubleStateOf(riskProfile.defaultTrailingStopPct) }
    var maxPositionPct by remember { mutableDoubleStateOf(30.0) }
    var rsiOversold by remember { mutableLongStateOf(30L) }
    var rsiOverbought by remember { mutableLongStateOf(70L) }
    var gridLevels by remember { mutableLongStateOf(10L) }

    val availableExchanges = remember(selectedAssetClass) {
        ExchangeVenue.forAssetClass(selectedAssetClass)
    }

    val availableSymbols = remember(selectedAssetClass, selectedExchange) {
        when (selectedAssetClass) {
            AssetClass.CRYPTO -> when (selectedExchange) {
                ExchangeVenue.COINBASE -> listOf("BTC/USD", "ETH/USD", "SOL/USD")
                ExchangeVenue.KRAKEN -> listOf("BTC/USD", "ETH/USD")
                ExchangeVenue.BYBIT -> listOf("BTC/USDT")
                ExchangeVenue.OKX -> listOf("ETH/USDT")
                else -> listOf("BTC/USDT", "ETH/USDT", "SOL/USDT", "BNB/USDT", "XRP/USDT")
            }
            AssetClass.METALS -> listOf("XAU/USD", "XAG/USD", "XPT/USD", "XPD/USD")
            AssetClass.FOREX -> listOf("EUR/USD", "GBP/USD", "USD/JPY", "USD/CHF", "AUD/USD", "USD/CAD")
        }
    }

    fun toggleTarget(target: MarketTarget) {
        val exists = selectedTargets.any { it.marketKey == target.marketKey }
        selectedTargets = if (exists) {
            if (selectedTargets.size > 1) {
                selectedTargets.filterNot { it.marketKey == target.marketKey }
            } else {
                selectedTargets
            }
        } else {
            selectedTargets + target
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark,
        contentColor = TextPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Deploy Automated Trading Bot",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Text(
                text = "Select 1 or more market instruments and combine multiple strategies.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(16.dp))

            val primarySym = selectedTargets.first().symbol.substringBefore("/")
            val primaryCode = selectedStrategies.first().shortCode
            OutlinedTextField(
                value = botName,
                onValueChange = { if (it.length <= 50) botName = it },
                label = { Text("Bot Name (optional)") },
                placeholder = { Text("$primarySym $primaryCode Alpha") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("bot_name_input")
            )

            Spacer(modifier = Modifier.height(14.dp))
            Text("Asset Class", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssetClass.entries.forEach { ac ->
                    FilterChip(
                        selected = selectedAssetClass == ac,
                        onClick = {
                            selectedAssetClass = ac
                            val exList = ExchangeVenue.forAssetClass(ac)
                            selectedExchange = exList.first()
                        },
                        label = { Text(ac.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("asset_class_chip_${ac.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Exchange / Broker", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableExchanges.forEach { venue ->
                    FilterChip(
                        selected = selectedExchange == venue,
                        onClick = { selectedExchange = venue },
                        label = { Text(venue.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldProfit.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldProfit
                        ),
                        modifier = Modifier.testTag("venue_chip_${venue.id}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Market Instruments (${selectedTargets.size} selected)",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                val allVenueSelected = availableSymbols.all { sym ->
                    val key = "${selectedExchange.id}:$sym"
                    selectedTargets.any { it.marketKey == key }
                }
                FilterChip(
                    selected = allVenueSelected,
                    onClick = {
                        val venueTargets = availableSymbols.map { sym ->
                            MarketTarget(selectedAssetClass, selectedExchange, sym)
                        }
                        selectedTargets = if (allVenueSelected) {
                            val remaining = selectedTargets.filterNot { t ->
                                venueTargets.any { it.marketKey == t.marketKey }
                            }
                            remaining.ifEmpty { listOf(venueTargets.first()) }
                        } else {
                            (selectedTargets + venueTargets).distinctBy { it.marketKey }
                        }
                    },
                    label = { Text(if (allVenueSelected) "Venue Full" else "Select All") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ElectricCyan.copy(alpha = 0.18f),
                        selectedLabelColor = ElectricCyan
                    ),
                    modifier = Modifier.testTag("select_all_pairs_chip")
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                availableSymbols.forEach { sym ->
                    val target = MarketTarget(selectedAssetClass, selectedExchange, sym)
                    val isSelected = selectedTargets.any { it.marketKey == target.marketKey }
                    FilterChip(
                        selected = isSelected,
                        onClick = { toggleTarget(target) },
                        label = { Text(sym, style = MonoNumberStyle) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan
                        ),
                        modifier = Modifier.testTag("pair_chip_${selectedExchange.id}_$sym")
                    )
                }
            }

            if (selectedTargets.size > 1) {
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    selectedTargets.forEach { target ->
                        Surface(
                            color = SurfaceElevated,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "${target.symbol} • ${target.exchange.displayName}",
                                    style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                                    color = EmeraldProfit
                                )
                                if (selectedTargets.size > 1) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Remove ${target.symbol}",
                                        tint = TextSecondary,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { toggleTarget(target) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Algorithmic Strategies (${selectedStrategies.size} selected)",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Text(
                    text = "Multi-select",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BotStrategy.entries.forEach { strat ->
                    val isSelected = strat in selectedStrategies
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) ElectricCyan.copy(alpha = 0.12f) else SurfaceElevated)
                            .border(
                                1.dp,
                                if (isSelected) ElectricCyan else BorderSubtle,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                selectedStrategies = if (isSelected) {
                                    if (selectedStrategies.size > 1) selectedStrategies - strat else selectedStrategies
                                } else {
                                    selectedStrategies + strat
                                }
                            }
                            .padding(12.dp)
                            .testTag("strategy_card_${strat.id}"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = if (isSelected) "${strat.displayName} selected" else "Select ${strat.displayName}",
                            tint = if (isSelected) ElectricCyan else TextSecondary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = strat.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) ElectricCyan else TextPrimary
                            )
                            Text(
                                text = strat.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }

            if (selectedStrategies.size >= 2) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Multi-Strategy Execution Logic",
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StrategyCombineMode.entries.forEach { mode ->
                        val active = strategyMode == mode
                        FilterChip(
                            selected = active,
                            onClick = { strategyMode = mode },
                            label = { Text(mode.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                selectedLabelColor = ElectricCyan
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("strategy_mode_${mode.id}")
                        )
                    }
                }
                Text(
                    text = strategyMode.description,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = capitalText,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() || it == '.' }) capitalText = input
                    },
                    label = { Text("Capital / Bot (USD)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Mode",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ExecutionMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedMode == mode,
                                onClick = { selectedMode = mode },
                                label = { Text(if (mode == ExecutionMode.PAPER) "Paper" else "Live") }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Risk & Position Guardrails", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))

            RiskSliderRow(
                label = "Leverage Cap (max ${riskProfile.maxLeverageCap}x)",
                valueText = "${"%.1f".format(leverage)}x",
                value = leverage.toFloat(),
                range = 1f..riskProfile.maxLeverageCap.toFloat().coerceAtLeast(1f),
                accent = AmberMetal,
                onValueChange = { leverage = it.toDouble() }
            )
            RiskSliderRow(
                label = "Stop-Loss",
                valueText = "-${"%.1f".format(stopLossPct)}%",
                value = stopLossPct.toFloat(),
                range = 0.5f..25f,
                accent = CrimsonLoss,
                onValueChange = { stopLossPct = it.toDouble() }
            )
            RiskSliderRow(
                label = "Take-Profit",
                valueText = "+${"%.1f".format(takeProfitPct)}%",
                value = takeProfitPct.toFloat(),
                range = 1f..50f,
                accent = EmeraldProfit,
                onValueChange = { takeProfitPct = it.toDouble() }
            )
            RiskSliderRow(
                label = "Trailing Stop",
                valueText = "${"%.1f".format(trailingStopPct)}%",
                value = trailingStopPct.toFloat(),
                range = 0f..15f,
                accent = ElectricCyan,
                onValueChange = { trailingStopPct = it.toDouble() }
            )
            RiskSliderRow(
                label = "Max Position Size",
                valueText = "${maxPositionPct.toInt()}%",
                value = maxPositionPct.toFloat(),
                range = 5f..100f,
                accent = ElectricCyan,
                onValueChange = { maxPositionPct = it.toDouble() }
            )

            if (BotStrategy.RSI_REVERSION in selectedStrategies) {
                RiskSliderRow(
                    label = "RSI Oversold Entry",
                    valueText = "$rsiOversold",
                    value = rsiOversold.toFloat(),
                    range = 15f..42f,
                    accent = EmeraldProfit,
                    onValueChange = { rsiOversold = it.toLong() }
                )
                RiskSliderRow(
                    label = "RSI Overbought Exit",
                    valueText = "$rsiOverbought",
                    value = rsiOverbought.toFloat(),
                    range = 58f..85f,
                    accent = CrimsonLoss,
                    onValueChange = { rsiOverbought = it.toLong() }
                )
            }

            if (BotStrategy.GRID in selectedStrategies) {
                RiskSliderRow(
                    label = "Grid Levels",
                    valueText = "$gridLevels",
                    value = gridLevels.toFloat(),
                    range = 2f..30f,
                    accent = ElectricCyan,
                    onValueChange = { gridLevels = it.toLong() }
                )
            }

            if (BotStrategy.MARTINGALE in selectedStrategies) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Martingale Ladder: Base ${maxPositionPct.toInt()}% -> 2x -> 4x -> 8x on consecutive losses (strictly capped at 100% allocated capital).",
                    style = MaterialTheme.typography.labelSmall,
                    color = AmberMetal,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .padding(10.dp)
                )
            }

            if (BotStrategy.TRADINGVIEW_21 in selectedStrategies) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "21 TradingView Engine: Requires Spot > 21-EMA, EMA-9 trend alignment, and RSI(14) 45–70 momentum filter.",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceElevated)
                        .padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val botCountLabel = if (selectedTargets.size == 1) "1 Bot" else "${selectedTargets.size} Bots"
            val stratLabel = if (selectedStrategies.size == 1) "1 Strategy" else "${selectedStrategies.size} Strategies"
            Button(
                onClick = {
                    val capital = (capitalText.toDoubleOrNull() ?: 2500.0).coerceIn(10.0, 10_000_000.0)
                    onCreateBots(
                        botName,
                        selectedTargets,
                        selectedStrategies.toList(),
                        strategyMode,
                        selectedMode,
                        capital,
                        leverage,
                        stopLossPct,
                        takeProfitPct,
                        trailingStopPct,
                        maxPositionPct,
                        rsiOversold,
                        rsiOverbought,
                        gridLevels
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = ElectricCyan,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_deploy_bot_button")
            ) {
                Text(
                    text = "Launch $botCountLabel • $stratLabel",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RiskSliderRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: androidx.compose.ui.graphics.Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                color = accent
            )
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = SurfaceElevated
            )
        )
    }
}
