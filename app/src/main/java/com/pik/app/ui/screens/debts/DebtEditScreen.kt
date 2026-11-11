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
import com.pik.app.domain.Apr
import com.pik.app.domain.Money
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikConfirmDialog
import com.pik.app.ui.components.PikDialog
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.components.PikMoneyField
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.pikTextFieldColors
import com.pik.app.ui.theme.PikColors
import java.time.LocalDate

private const val MAX_NAME_LENGTH = 40
private const val MAX_APR_TEXT_LENGTH = 7

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
                onSave = { name, balance, original, apr, min, dueDay ->
                    viewModel.save(name, balance, original, apr, min, dueDay, onDone = onBack)
                },
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
    onSave: (name: String, balanceCents: Long, originalCents: Long?, aprBasisPoints: Int, minCents: Long, dueDay: Int) -> Unit,
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
    var aprText by remember { mutableStateOf(existing?.let { Apr.formatPlain(it.aprBasisPoints) }.orEmpty()) }
    var minText by remember { mutableStateOf(existing?.let { Money.formatPlain(it.minPaymentCents) }.orEmpty()) }
    var dueDay by remember { mutableStateOf(existing?.dueDay ?: LocalDate.now().dayOfMonth.coerceIn(1, 28)) }
    var pickingDay by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    val balance = Money.parse(balanceText)?.takeIf { it >= 0 }
    val originalBlank = originalText.isBlank()
    val original = if (originalBlank) null else Money.parse(originalText)?.takeIf { it >= 0 }
    val apr = Apr.parse(aprText)
    val min = Money.parse(minText)?.takeIf { it >= 0 }
    val valid = name.isNotBlank() && balance != null && (originalBlank || original != null) && apr != null && min != null

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
                    OutlinedTextField(
                        value = aprText,
                        onValueChange = { aprText = it.take(MAX_APR_TEXT_LENGTH) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.debt_apr)) },
                        placeholder = { Text(stringResource(R.string.debt_apr_hint)) },
                        suffix = { Text("%") },
                        isError = aprText.isNotBlank() && apr == null,
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        colors = pikTextFieldColors(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                    )
                    PikMoneyField(
                        value = minText,
                        onValueChange = { minText = it },
                        label = stringResource(R.string.debt_min_payment),
                        isError = minText.isNotBlank() && min == null,
                    )
                    PikSecondaryButton(
                        text = stringResource(R.string.debt_due_day_field, dueDay),
                        onClick = { pickingDay = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            if (!valid && (name.isNotBlank() || balanceText.isNotBlank() || aprText.isNotBlank() || minText.isNotBlank())) {
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
                if (valid && balance != null && apr != null && min != null) {
                    onSave(name.trim(), balance, original, apr, min, dueDay)
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
