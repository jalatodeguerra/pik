package com.pik.app.ui.screens.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.data.db.DebtEntity
import com.pik.app.data.repo.DebtInput
import com.pik.app.domain.Apr
import com.pik.app.domain.DebtType
import com.pik.app.domain.Money
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikConfirmDialog
import com.pik.app.ui.components.PikDialog
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.components.PikMoneyField
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.PikSwitchRow
import com.pik.app.ui.components.pikTextFieldColors
import com.pik.app.ui.theme.PikColors
import java.time.LocalDate

private const val MAX_NAME_LENGTH = 40
private const val MAX_APR_TEXT_LENGTH = 7
private const val MAX_INSTALMENTS_TEXT_LENGTH = 4
private const val MAX_INSTALMENTS = 1200

/**
 * Add or edit a debt. [onLeaveToList] is used after archiving or deleting: the debt page below
 * this screen would no longer make sense, so the app goes back to the Debts tab.
 */
@Composable
fun DebtEditScreen(
    onBack: () -> Unit,
    onLeaveToList: () -> Unit,
    viewModel: DebtEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isEditing = state.existing != null || state.isLoading
    Column(modifier = Modifier.fillMaxSize()) {
        PikBackTopBar(
            title = stringResource(if (isEditing) R.string.debt_edit_title else R.string.debt_new_title),
            onBack = onBack,
        )
        when {
            state.isLoading -> Box(Modifier.weight(1f))
            state.notFound -> Box(
                Modifier.weight(1f).fillMaxWidth().padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.debt_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PikColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            else -> DebtEditForm(
                existing = state.existing,
                paymentCount = state.paymentCount,
                hasRule = state.hasRule,
                onSave = { input, track -> viewModel.save(input, track, onDone = onBack) },
                onArchive = { archived -> viewModel.setArchived(archived, onDone = onLeaveToList) },
                onDelete = { viewModel.delete { deleted -> if (deleted) onLeaveToList() } },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebtEditForm(
    existing: DebtEntity?,
    paymentCount: Int,
    hasRule: Boolean,
    onSave: (DebtInput, trackPayment: Boolean) -> Unit,
    onArchive: (archived: Boolean) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var balanceText by remember {
        mutableStateOf(existing?.let { Money.formatPlain(it.startingBalanceCents) }.orEmpty())
    }
    var originalText by remember {
        mutableStateOf(existing?.originalAmountCents?.let { Money.formatPlain(it) }.orEmpty())
    }
    var type by remember { mutableStateOf(existing?.type ?: DebtType.OTHER) }
    var tanText by remember { mutableStateOf(existing?.let { Apr.formatPlain(it.aprBasisPoints) }.orEmpty()) }
    var taegText by remember {
        mutableStateOf(existing?.taegBasisPoints?.let { Apr.formatPlain(it) }.orEmpty())
    }
    var totalText by remember { mutableStateOf(existing?.totalInstallments?.toString().orEmpty()) }
    var paidBeforeText by remember { mutableStateOf(existing?.installmentsPaidBefore?.toString().orEmpty()) }
    var minText by remember { mutableStateOf(existing?.let { Money.formatPlain(it.minPaymentCents) }.orEmpty()) }
    var dueDay by remember { mutableStateOf(existing?.dueDay ?: LocalDate.now().dayOfMonth.coerceIn(1, 28)) }
    var pickingDay by remember { mutableStateOf(false) }
    // New debts start with the switch on; an existing debt shows whether it has a rule.
    var trackPayment by remember { mutableStateOf(if (existing == null) true else hasRule) }
    var confirmDelete by remember { mutableStateOf(false) }

    val balance = Money.parse(balanceText)?.takeIf { it >= 0 }
    val originalBlank = originalText.isBlank()
    val original = if (originalBlank) null else Money.parse(originalText)?.takeIf { it >= 0 }
    val tan = Apr.parse(tanText)
    val taegBlank = taegText.isBlank()
    val taeg = if (taegBlank) null else Apr.parse(taegText)
    val min = Money.parse(minText)?.takeIf { it >= 0 }
    val hasInstalments = type.hasInstalments
    val totalBlank = totalText.isBlank()
    val total = if (totalBlank) null else totalText.toIntOrNull()?.takeIf { it in 1..MAX_INSTALMENTS }
    val totalOk = !hasInstalments || totalBlank || total != null
    val paidBlank = paidBeforeText.isBlank()
    val paidBefore = if (paidBlank) null else paidBeforeText.toIntOrNull()?.takeIf { it in 0..MAX_INSTALMENTS }
    val paidOk = !hasInstalments || total == null || paidBlank || (paidBefore != null && paidBefore <= total)
    val taegLowerThanTan = tan != null && taeg != null && taeg < tan
    val valid = name.isNotBlank() && balance != null && (originalBlank || original != null) &&
        tan != null && (taegBlank || taeg != null) && min != null && totalOk && paidOk

    Column(modifier = modifier) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PikCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.debt_name)) },
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = pikTextFieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    )
                    PikMoneyField(
                        value = balanceText,
                        onValueChange = { balanceText = it },
                        label = stringResource(if (existing == null) R.string.debt_balance_now else R.string.debt_balance_when_added),
                        isError = balanceText.isNotBlank() && balance == null,
                        supportingText = if (existing != null) stringResource(R.string.debt_balance_edit_hint) else null,
                    )
                    PikMoneyField(
                        value = originalText,
                        onValueChange = { originalText = it },
                        label = stringResource(R.string.debt_original),
                        isError = !originalBlank && original == null,
                        supportingText = stringResource(R.string.debt_original_hint),
                    )
                    Text(
                        text = stringResource(R.string.debt_type),
                        style = MaterialTheme.typography.labelLarge,
                        color = PikColors.TextSecondary,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (option in DebtType.values()) {
                            PikFilterChip(
                                label = stringResource(option.labelRes()),
                                selected = option == type,
                                onClick = { type = option },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = tanText,
                        onValueChange = { tanText = it.take(MAX_APR_TEXT_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.debt_tan)) },
                        placeholder = { Text(stringResource(R.string.debt_rate_placeholder)) },
                        supportingText = { Text(stringResource(R.string.debt_tan_hint)) },
                        suffix = { Text("%") },
                        isError = tanText.isNotBlank() && tan == null,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = pikTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                    OutlinedTextField(
                        value = taegText,
                        onValueChange = { taegText = it.take(MAX_APR_TEXT_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.debt_taeg)) },
                        placeholder = { Text(stringResource(R.string.debt_rate_placeholder)) },
                        supportingText = {
                            Text(
                                text = stringResource(
                                    if (taegLowerThanTan) R.string.debt_taeg_lower_warning else R.string.debt_taeg_hint,
                                ),
                                // a soft warning: it never blocks saving
                                color = if (taegLowerThanTan) PikColors.Amber else PikColors.TextSecondary,
                            )
                        },
                        suffix = { Text("%") },
                        isError = !taegBlank && taeg == null,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = pikTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                    PikMoneyField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = stringResource(if (hasInstalments) R.string.debt_instalment else R.string.debt_min_payment),
                        isError = minText.isNotBlank() && min == null,
                    )
                    if (hasInstalments) {
                        OutlinedTextField(
                            value = totalText,
                            onValueChange = { totalText = it.filter(Char::isDigit).take(MAX_INSTALMENTS_TEXT_LENGTH) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.debt_total_installments)) },
                            placeholder = { Text(stringResource(R.string.debt_total_installments_hint)) },
                            isError = !totalOk,
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = pikTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        if (total != null) {
                            OutlinedTextField(
                                value = paidBeforeText,
                                onValueChange = {
                                    paidBeforeText = it.filter(Char::isDigit).take(MAX_INSTALMENTS_TEXT_LENGTH)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.debt_installments_paid_before)) },
                                placeholder = { Text("0") },
                                supportingText = { Text(stringResource(R.string.debt_installments_paid_before_hint)) },
                                isError = !paidOk,
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                colors = pikTextFieldColors(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            )
                        }
                    }
                    PikSecondaryButton(
                        text = stringResource(R.string.debt_due_day_field, dueDay),
                        onClick = { pickingDay = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (min != null && min > 0L) {
                        PikSwitchRow(
                            label = stringResource(R.string.debt_track_payment),
                            supporting = stringResource(R.string.debt_track_payment_hint),
                            checked = trackPayment,
                            onCheckedChange = { trackPayment = it },
                            accent = PikColors.Amber,
                        )
                    }
                }
            }
            if (!valid && (name.isNotBlank() || balanceText.isNotBlank() || tanText.isNotBlank() || minText.isNotBlank())) {
                Text(
                    text = stringResource(R.string.debt_invalid),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.Coral,
                )
            }

            if (existing != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.debt_archive_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = PikColors.TextSecondary,
                    )
                    PikSecondaryButton(
                        text = stringResource(if (existing.archived) R.string.action_unarchive else R.string.action_archive),
                        onClick = { onArchive(!existing.archived) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (paymentCount == 0) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Coral),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.action_delete)) }
                    } else {
                        Text(
                            text = stringResource(R.string.debt_delete_blocked),
                            style = MaterialTheme.typography.bodySmall,
                            color = PikColors.TextSecondary,
                        )
                    }
                }
            }
            Box(Modifier.padding(bottom = 8.dp))
        }
        PikPrimaryButton(
            text = stringResource(R.string.action_save),
            onClick = {
                if (valid) {
                    val tracked = hasInstalments && total != null
                    onSave(
                        DebtInput(
                            name = name.trim(),
                            startingBalanceCents = balance,
                            originalAmountCents = original,
                            tanBasisPoints = tan,
                            taegBasisPoints = taeg,
                            type = type,
                            minPaymentCents = min,
                            dueDay = dueDay,
                            totalInstallments = if (tracked) total else null,
                            installmentsPaidBefore = if (tracked) (paidBefore ?: 0) else null,
                        ),
                        trackPayment && min > 0L,
                    )
                }
            },
            enabled = valid,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    if (pickingDay) {
        DueDayDialog(
            current = dueDay,
            onDismiss = { pickingDay = false },
            onSave = {
                dueDay = it
                pickingDay = false
            },
        )
    }
    if (confirmDelete) {
        PikConfirmDialog(
            title = stringResource(R.string.debt_delete_title),
            message = stringResource(R.string.debt_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                confirmDelete = false
                onDelete()
            },
            onDismiss = { confirmDelete = false },
            destructive = true,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DueDayDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var selected by remember { mutableStateOf(current) }
    PikDialog(
        title = stringResource(R.string.debt_due_day),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        onConfirm = { onSave(selected) },
    ) {
        Text(
            text = stringResource(R.string.debt_due_day_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (day in 1..28) {
                PikFilterChip(label = day.toString(), selected = day == selected, onClick = { selected = day })
            }
        }
    }
}
