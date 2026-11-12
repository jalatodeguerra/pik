package com.pik.app.ui.screens.debts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.pik.app.R
import com.pik.app.data.db.AccountEntity
import com.pik.app.domain.DateFormats
import com.pik.app.domain.DebtBalanceCheck
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.Money
import com.pik.app.domain.RelativeDay
import com.pik.app.ui.components.PikDatePickerDialog
import com.pik.app.ui.components.PikDialog
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.components.PikMoneyField
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.PikSegmented
import com.pik.app.ui.components.pikTextFieldColors
import com.pik.app.ui.theme.PikColors
import java.time.LocalDate

private const val MAX_NOTE_LENGTH = 120

@Composable
private fun dateLabel(date: LocalDate): String =
    when (DateFormats.relativeDay(date, LocalDate.now())) {
        RelativeDay.TODAY -> stringResource(R.string.date_today)
        RelativeDay.YESTERDAY -> stringResource(R.string.date_yesterday)
        RelativeDay.OTHER -> DateFormats.shortDate(date)
    }

/** A button that shows the date and opens the shared date picker. */
@Composable
private fun DateButton(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    PikSecondaryButton(
        text = stringResource(R.string.date_field, dateLabel(date)),
        onClick = { picking = true },
        modifier = Modifier.fillMaxWidth(),
    )
    if (picking) {
        PikDatePickerDialog(
            initial = date,
            onDismiss = { picking = false },
            onConfirm = {
                onDateChange(it)
                picking = false
            },
        )
    }
}

@Composable
private fun NoteField(value: String, onValueChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(MAX_NOTE_LENGTH)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = pikTextFieldColors(),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
    )
}

/**
 * "Log payment": creates an EXPENSE from the chosen account with the debt's id (no category).
 * [defaultAmountCents] is the prefilled amount (the minimum payment, or less when less is owed).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LogPaymentDialog(
    debtName: String,
    defaultAmountCents: Long,
    accounts: List<AccountEntity>,
    defaultAccountId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (accountId: Long, amountCents: Long, date: LocalDate, note: String) -> Unit,
) {
    var amountText by remember { mutableStateOf(if (defaultAmountCents > 0) Money.formatPlain(defaultAmountCents) else "") }
    var accountId by remember { mutableStateOf(defaultAccountId ?: accounts.firstOrNull()?.id) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    val cents = Money.parse(amountText)
    val valid = cents != null && cents > 0 && accountId != null

    PikDialog(
        title = stringResource(R.string.debt_payment_title, debtName),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.debt_payment_save),
        confirmEnabled = valid,
        onConfirm = {
            val account = accountId
            if (cents != null && account != null) onConfirm(account, cents, date, note)
        },
    ) {
        PikMoneyField(
            value = amountText,
            onValueChange = { amountText = it },
            label = stringResource(R.string.debt_payment_amount),
            isError = amountText.isNotBlank() && (cents == null || cents <= 0),
        )
        Text(
            text = stringResource(R.string.debt_payment_account),
            style = MaterialTheme.typography.labelLarge,
            color = PikColors.TextSecondary,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            accounts.forEach { account ->
                PikFilterChip(
                    label = account.name,
                    selected = account.id == accountId,
                    onClick = { accountId = account.id },
                )
            }
        }
        DateButton(date = date, onDateChange = { date = it })
        NoteField(value = note, onValueChange = { note = it }, label = stringResource(R.string.debt_payment_note))
    }
}

/** "Add interest or fee": what the lender charged. Stored as a positive entry that raises the debt. */
@Composable
fun AddDebtEntryDialog(
    onDismiss: () -> Unit,
    onConfirm: (type: DebtEntryType, amountCents: Long, date: LocalDate, note: String) -> Unit,
) {
    val types = listOf(DebtEntryType.INTEREST, DebtEntryType.FEE)
    var typeIndex by remember { mutableStateOf(0) }
    var amountText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var note by remember { mutableStateOf("") }
    val cents = Money.parse(amountText)
    val valid = cents != null && cents > 0

    PikDialog(
        title = stringResource(R.string.debt_entry_title),
        onDismiss = onDismiss,
        confirmLabel = stringResource(R.string.action_save),
        confirmEnabled = valid,
        onConfirm = { if (cents != null) onConfirm(types[typeIndex], cents, date, note) },
    ) {
        Text(
            text = stringResource(R.string.debt_entry_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextSecondary,
        )
        PikSegmented(
            options = listOf(stringResource(R.string.debt_entry_interest), stringResource(R.string.debt_entry_fee)),
            selectedIndex = typeIndex,
            onSelect = { typeIndex = it },
            modifier = Modifier.fillMaxWidth(),
        )
        PikMoneyField(
            value = amountText,
            onValueChange = { amountText = it },
            label = stringResource(R.string.debt_entry_amount),
            isError = amountText.isNotBlank() && !valid,
        )
        DateButton(date = date, onDateChange = { date = it })
        NoteField(value = note, onValueChange = { note = it }, label = stringResource(R.string.debt_entry_note))
    }
}

/**
 * "Check balance" for a debt: the user types what the bank says they owe; if it differs from
 * Pik's calculation an ADJUSTMENT entry with the difference is added ([onConfirm] is only called
 * then). [rawRemainingCents] may be negative for an over-paid debt.
 */
@Composable
fun DebtCheckBalanceDialog(
    debtName: String,
    shownRemainingCents: Long,
    rawRemainingCents: Long,
    onDismiss: () -> Unit,
    onConfirm: (realCents: Long, note: String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val parsed = Money.parse(text)
    val real: Long? = parsed?.takeIf { it >= 0 }
    val adjustment: Long? = real?.let { DebtBalanceCheck.adjustmentCents(it, rawRemainingCents) }
    val note = stringResource(R.string.debt_check_note)

    PikDialog(
        title = stringResource(R.string.debt_check_title, debtName),
        onDismiss = onDismiss,
        confirmLabel = stringResource(
            if (adjustment != null && adjustment != 0L) R.string.debt_check_adjust else R.string.action_ok,
        ),
        confirmEnabled = real != null,
        onConfirm = {
            if (real != null && adjustment != null && adjustment != 0L) onConfirm(real, note) else onDismiss()
        },
    ) {
        Text(
            text = stringResource(R.string.debt_check_pik_says, Money.format(shownRemainingCents)),
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextPrimary,
        )
        PikMoneyField(
            value = text,
            onValueChange = { text = it },
            label = stringResource(R.string.debt_check_field),
            isError = text.isNotBlank() && real == null,
        )
        val (message, color) = when {
            text.isBlank() -> stringResource(R.string.debt_check_hint) to PikColors.TextSecondary
            real == null -> stringResource(R.string.check_balance_invalid) to PikColors.Coral
            adjustment == 0L -> stringResource(R.string.check_balance_matches) to PikColors.Lime
            else -> stringResource(
                R.string.debt_check_difference,
                Money.format(adjustment ?: 0L, showPlusSign = true),
            ) to PikColors.TextPrimary
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
