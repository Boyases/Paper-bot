package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.data.model.AssetClass
import com.example.data.model.ExchangeConnection
import com.example.data.model.RiskProfile
import com.example.ui.components.AssetClassPill
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

@Composable
fun RiskAndSettingsScreen(
    riskProfile: RiskProfile,
    exchanges: List<ExchangeConnection>,
    userEmail: String?,
    onSaveRiskProfile: (RiskProfile) -> Unit,
    onTriggerEmergencyHalt: () -> Unit,
    onResetCircuitBreaker: () -> Unit,
    onConfigureExchange: (ExchangeConnection, String, String, Boolean) -> Unit,
    onDisconnectExchange: (ExchangeConnection) -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    var maxDailyLossUsd by remember { mutableDoubleStateOf(riskProfile.maxDailyLossUsd) }
    var maxDrawdownPct by remember { mutableDoubleStateOf(riskProfile.maxPortfolioDrawdownPct) }
    var maxOpenPositions by remember { mutableLongStateOf(riskProfile.maxOpenPositions) }
    var maxLeverageCap by remember { mutableDoubleStateOf(riskProfile.maxLeverageCap) }
    var defaultSlPct by remember { mutableDoubleStateOf(riskProfile.defaultStopLossPct) }
    var defaultTpPct by remember { mutableDoubleStateOf(riskProfile.defaultTakeProfitPct) }
    var defaultTrailPct by remember { mutableDoubleStateOf(riskProfile.defaultTrailingStopPct) }
    var slippagePct by remember { mutableDoubleStateOf(riskProfile.slippageTolerancePct) }
    var cooldownMinutes by remember { mutableLongStateOf(riskProfile.cooldownMinutes) }
    var autoPauseVolatility by remember { mutableStateOf(riskProfile.autoPauseOnHighVolatility) }

    LaunchedEffect(riskProfile) {
        maxDailyLossUsd = riskProfile.maxDailyLossUsd
        maxDrawdownPct = riskProfile.maxPortfolioDrawdownPct
        maxOpenPositions = riskProfile.maxOpenPositions
        maxLeverageCap = riskProfile.maxLeverageCap
        defaultSlPct = riskProfile.defaultStopLossPct
        defaultTpPct = riskProfile.defaultTakeProfitPct
        defaultTrailPct = riskProfile.defaultTrailingStopPct
        slippagePct = riskProfile.slippageTolerancePct
        cooldownMinutes = riskProfile.cooldownMinutes
        autoPauseVolatility = riskProfile.autoPauseOnHighVolatility
    }

    var editingExchange by remember { mutableStateOf<ExchangeConnection?>(null) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Risk & Exchange Vault",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Text(
                text = "Portfolio circuit breakers, position guardrails, and encrypted venue credentials",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Circuit Breaker Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (riskProfile.circuitBreakerTriggered) CrimsonContainer else SurfaceDark)
                    .border(
                        1.dp,
                        if (riskProfile.circuitBreakerTriggered) CrimsonLoss else BorderSubtle,
                        RoundedCornerShape(18.dp)
                    )
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (riskProfile.circuitBreakerTriggered) Icons.Default.Warning else Icons.Default.Shield,
                        contentDescription = null,
                        tint = if (riskProfile.circuitBreakerTriggered) CrimsonLoss else EmeraldProfit
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (riskProfile.circuitBreakerTriggered) "Circuit Breaker TRIPPED" else "Circuit Breaker Armed",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Text(
                            text = if (riskProfile.circuitBreakerTriggered) {
                                "All bots are halted until manually reset."
                            } else {
                                "Automatically halts all bots if daily loss exceeds ${FormattingUtils.formatUsd(maxDailyLossUsd)}."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                if (riskProfile.circuitBreakerTriggered) {
                    Button(
                        onClick = onResetCircuitBreaker,
                        colors = ButtonDefaults.buttonColors(containerColor = EmeraldProfit, contentColor = ObsidianBg),
                        modifier = Modifier.fillMaxWidth().testTag("reset_circuit_breaker_button")
                    ) {
                        Text("Reset Circuit Breaker", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    FilledTonalButton(
                        onClick = onTriggerEmergencyHalt,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = CrimsonContainer,
                            contentColor = CrimsonLoss
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("emergency_halt_all_button")
                    ) {
                        Text("Activate Emergency Halt All", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        // Global Risk Guardrails Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Global Risk Management Rules",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))

                RiskSettingSlider(
                    label = "Max Daily Loss Limit",
                    valueLabel = FormattingUtils.formatUsd(maxDailyLossUsd),
                    value = maxDailyLossUsd.toFloat(),
                    range = 50f..10000f,
                    accent = CrimsonLoss,
                    onValueChange = { maxDailyLossUsd = (it / 50f).toInt() * 50.0 }
                )
                RiskSettingSlider(
                    label = "Max Portfolio Drawdown",
                    valueLabel = "${maxDrawdownPct.toInt()}%",
                    value = maxDrawdownPct.toFloat(),
                    range = 2f..50f,
                    accent = AmberMetal,
                    onValueChange = { maxDrawdownPct = it.toInt().toDouble() }
                )
                RiskSettingSlider(
                    label = "Max Concurrent Positions",
                    valueLabel = "$maxOpenPositions",
                    value = maxOpenPositions.toFloat(),
                    range = 1f..20f,
                    accent = ElectricCyan,
                    onValueChange = { maxOpenPositions = it.toLong() }
                )
                RiskSettingSlider(
                    label = "Max Leverage Cap",
                    valueLabel = "${"%.1f".format(maxLeverageCap)}x",
                    value = maxLeverageCap.toFloat(),
                    range = 1f..20f,
                    accent = AmberMetal,
                    onValueChange = { maxLeverageCap = ((it * 2).toInt() / 2.0).coerceAtLeast(1.0) }
                )
                RiskSettingSlider(
                    label = "Default Stop-Loss",
                    valueLabel = "-${"%.1f".format(defaultSlPct)}%",
                    value = defaultSlPct.toFloat(),
                    range = 0.5f..25f,
                    accent = CrimsonLoss,
                    onValueChange = { defaultSlPct = ((it * 2).toInt() / 2.0).coerceAtLeast(0.5) }
                )
                RiskSettingSlider(
                    label = "Default Take-Profit",
                    valueLabel = "+${"%.1f".format(defaultTpPct)}%",
                    value = defaultTpPct.toFloat(),
                    range = 1f..50f,
                    accent = EmeraldProfit,
                    onValueChange = { defaultTpPct = ((it * 2).toInt() / 2.0).coerceAtLeast(1.0) }
                )
                RiskSettingSlider(
                    label = "Default Trailing Stop",
                    valueLabel = "${"%.1f".format(defaultTrailPct)}%",
                    value = defaultTrailPct.toFloat(),
                    range = 0f..15f,
                    accent = ElectricCyan,
                    onValueChange = { defaultTrailPct = ((it * 2).toInt() / 2.0) }
                )
                RiskSettingSlider(
                    label = "Slippage Tolerance",
                    valueLabel = "${"%.2f".format(slippagePct)}%",
                    value = slippagePct.toFloat(),
                    range = 0.05f..3f,
                    accent = TextPrimary,
                    onValueChange = { slippagePct = ((it * 20).toInt() / 20.0).coerceAtLeast(0.05) }
                )
                RiskSettingSlider(
                    label = "Trade Cooldown",
                    valueLabel = "$cooldownMinutes min",
                    value = cooldownMinutes.toFloat(),
                    range = 0f..60f,
                    accent = ElectricCyan,
                    onValueChange = { cooldownMinutes = it.toLong() }
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Pause on Volatility Spike",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                        Text(
                            text = "Halt entries when 14-candle ATR exceeds 6.5%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = autoPauseVolatility,
                        onCheckedChange = { autoPauseVolatility = it },
                        colors = SwitchDefaults.colors(checkedThumbColor = ObsidianBg, checkedTrackColor = ElectricCyan)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        onSaveRiskProfile(
                            riskProfile.copy(
                                maxDailyLossUsd = maxDailyLossUsd,
                                maxPortfolioDrawdownPct = maxDrawdownPct,
                                maxOpenPositions = maxOpenPositions,
                                maxLeverageCap = maxLeverageCap,
                                defaultStopLossPct = defaultSlPct,
                                defaultTakeProfitPct = defaultTpPct,
                                defaultTrailingStopPct = defaultTrailPct,
                                slippageTolerancePct = slippagePct,
                                cooldownMinutes = cooldownMinutes,
                                autoPauseOnHighVolatility = autoPauseVolatility
                            )
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("save_risk_profile_button")
                ) {
                    Text("Save Risk Guardrails", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                }
            }
        }

        // Exchange Connectors Header
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = ElectricCyan, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Exchange & Broker Connectors",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
            }
        }

        items(exchanges, key = { it.id }) { exchange ->
            ExchangeConnectorCard(
                connection = exchange,
                onConfigure = { editingExchange = exchange }
            )
        }

        // Account Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = ElectricCyan)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Authenticated Session", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                        Text(
                            text = userEmail ?: "Signed in with Google",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onSignOut,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().testTag("sign_out_button")
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Sign Out", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }

    editingExchange?.let { conn ->
        ExchangeApiConfigDialog(
            connection = conn,
            onDismiss = { editingExchange = null },
            onSave = { key, secret, paperMode ->
                onConfigureExchange(conn, key, secret, paperMode)
                editingExchange = null
            },
            onClearCredentials = {
                onDisconnectExchange(conn)
                editingExchange = null
            }
        )
    }
}

@Composable
private fun RiskSettingSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    accent: Color,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            Text(
                text = valueLabel,
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

@Composable
private fun ExchangeConnectorCard(
    connection: ExchangeConnection,
    onConfigure: () -> Unit
) {
    val assetClass = AssetClass.fromId(connection.assetClass)
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
                Text(
                    text = connection.exchangeName,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                AssetClassPill(assetClass = assetClass)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (connection.isPaperMode) "Paper Engine • ${connection.latencyMs}ms" else "Live API (${connection.apiKeyLabel}) • ${connection.latencyMs}ms",
                style = MaterialTheme.typography.labelSmall.merge(MonoNumberStyle),
                color = if (connection.isPaperMode) ElectricCyan else EmeraldProfit
            )
        }
        FilledTonalButton(
            onClick = onConfigure,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = SurfaceElevated,
                contentColor = TextPrimary
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text("Configure", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun ExchangeApiConfigDialog(
    connection: ExchangeConnection,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit,
    onClearCredentials: () -> Unit
) {
    var apiKey by remember { mutableStateOf("") }
    var apiSecret by remember { mutableStateOf("") }
    var isPaperMode by remember { mutableStateOf(connection.isPaperMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceDark,
        title = {
            Text("${connection.exchangeName} API Vault", color = TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Keys are encrypted locally via Android KeyStore (AES-256-GCM) and never uploaded to Firestore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Paper Trading Mode", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
                    Switch(checked = isPaperMode, onCheckedChange = { isPaperMode = it })
                }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text("API Secret") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(apiKey, apiSecret, isPaperMode) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricCyan, contentColor = ObsidianBg)
            ) {
                Text("Save Vault")
            }
        },
        dismissButton = {
            Row {
                if (!connection.isPaperMode) {
                    TextButton(onClick = onClearCredentials) {
                        Text("Clear Keys", color = CrimsonLoss)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        }
    )
}
