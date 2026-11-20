package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.WalletAccount
import com.example.ui.components.FormattingUtils
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

enum class WalletTransferMode(val label: String) {
    DEPOSIT("Deposit"),
    WITHDRAW("Withdraw")
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun DepositWithdrawSheet(
    initialMode: WalletTransferMode,
    wallet: WalletAccount,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirmDeposit: (amountUsd: Double, method: String, note: String) -> Unit,
    onConfirmWithdraw: (amountUsd: Double, method: String, note: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var mode by remember { mutableStateOf(initialMode) }
    var amountText by remember { mutableStateOf("1000") }
    var selectedMethod by remember { mutableStateOf("ACH Bank Transfer") }
    var noteText by remember { mutableStateOf("") }

    val methods = remember {
        listOf(
            "ACH Bank Transfer",
            "Wire Transfer",
            "USDT (TRC-20)",
            "Instant Debit Card"
        )
    }

    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
    val isWithdraw = mode == WalletTransferMode.WITHDRAW
    val exceedsBalance = isWithdraw && parsedAmount > wallet.availableCashUsd
    val invalidRange = parsedAmount < 1.0 || parsedAmount > 1_000_000.0
    val validationError = when {
        amountText.isBlank() -> "Enter an amount in USD"
        parsedAmount < 1.0 -> "Minimum transfer is $1.00"
        parsedAmount > 1_000_000.0 -> "Maximum single transfer is $1,000,000.00"
        exceedsBalance -> "Exceeds available cash (${FormattingUtils.formatUsd(wallet.availableCashUsd)})"
        else -> null
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AccountBalanceWallet,
                    contentDescription = null,
                    tint = ElectricCyan
                )
                Text(
                    text = "Treasury Deposit & Withdraw",
                    style = MaterialTheme.typography.headlineSmall,
                    color = TextPrimary
                )
            }
            Text(
                text = "Manage USD trading liquidity synced to your account ledger.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Mode switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WalletTransferMode.entries.forEach { item ->
                    val selected = mode == item
                    val accent = if (item == WalletTransferMode.DEPOSIT) EmeraldProfit else AmberMetal
                    FilterChip(
                        selected = selected,
                        onClick = { mode = item },
                        leadingIcon = {
                            Icon(
                                imageVector = if (item == WalletTransferMode.DEPOSIT) {
                                    Icons.Default.ArrowDownward
                                } else {
                                    Icons.Default.ArrowUpward
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        label = { Text(item.label, style = MaterialTheme.typography.labelLarge) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.2f),
                            selectedLabelColor = accent,
                            selectedLeadingIconColor = accent
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("wallet_mode_${item.name.lowercase()}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Available Cash readout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceElevated)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Available Cash Balance",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = FormattingUtils.formatUsd(wallet.availableCashUsd),
                        style = MaterialTheme.typography.titleLarge.merge(MonoNumberStyle),
                        color = ElectricCyan
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Net Deposits",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    Text(
                        text = FormattingUtils.formatUsd(wallet.totalDepositedUsd - wallet.totalWithdrawnUsd),
                        style = MaterialTheme.typography.labelMedium.merge(MonoNumberStyle),
                        color = TextPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    if (input.isEmpty() || input.matches(Regex("^\\d*\\.?\\d{0,2}$"))) {
                        amountText = input
                    }
                },
                label = { Text("Amount (USD)") },
                prefix = { Text("$", style = MonoNumberStyle) },
                isError = validationError != null,
                supportingText = validationError?.let { err ->
                    { Text(err, color = CrimsonLoss) }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_amount_input")
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Quick amount presets
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(100, 500, 1000, 5000).forEach { preset ->
                    FilterChip(
                        selected = parsedAmount == preset.toDouble(),
                        onClick = { amountText = preset.toString() },
                        label = { Text("$$preset", style = MonoNumberStyle) },
                        modifier = Modifier.testTag("wallet_preset_$preset")
                    )
                }
                if (isWithdraw && wallet.availableCashUsd >= 1.0) {
                    FilterChip(
                        selected = parsedAmount == wallet.availableCashUsd,
                        onClick = {
                            amountText = String.format(java.util.Locale.US, "%.2f", wallet.availableCashUsd)
                        },
                        label = { Text("Max", style = MonoNumberStyle) },
                        modifier = Modifier.testTag("wallet_preset_max")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Text("Transfer Rail", style = MaterialTheme.typography.titleSmall, color = TextPrimary)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                methods.forEach { method ->
                    FilterChip(
                        selected = selectedMethod == method,
                        onClick = { selectedMethod = method },
                        label = { Text(method) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                            selectedLabelColor = ElectricCyan
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= 120) noteText = it },
                label = { Text("Reference Note (optional)") },
                placeholder = { Text(if (isWithdraw) "Profit sweep" else "Trading margin top-up") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("wallet_note_input")
            )

            Spacer(modifier = Modifier.height(18.dp))

            val buttonColor = if (isWithdraw) AmberMetal else EmeraldProfit
            Button(
                onClick = {
                    if (!invalidRange && !exceedsBalance && !isSubmitting) {
                        if (isWithdraw) {
                            onConfirmWithdraw(parsedAmount, selectedMethod, noteText)
                        } else {
                            onConfirmDeposit(parsedAmount, selectedMethod, noteText)
                        }
                    }
                },
                enabled = validationError == null && !isSubmitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = ObsidianBg
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("confirm_wallet_transfer_button")
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        color = ObsidianBg,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Processing...", style = MaterialTheme.typography.labelLarge)
                } else {
                    Text(
                        text = if (isWithdraw) {
                            "Withdraw ${FormattingUtils.formatUsd(parsedAmount)}"
                        } else {
                            "Deposit ${FormattingUtils.formatUsd(parsedAmount)}"
                        },
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
