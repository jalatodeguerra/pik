package com.pik.app.ui.screens.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pik.app.R
import com.pik.app.data.db.AccountEntity
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.RecurringKind
import com.pik.app.domain.RelativeDay
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikDatePickerDialog
import com.pik.app.ui.components.PikFilterChip
import com.pik.app.ui.components.PikMoneyField
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.theme.PikColors
import java.time.LocalDate

/**
 * The small sheet that confirms (or skips) one due occurrence. The amount is editable because bills vary,
 * the account and the date default to the rule's account and the due date. Confirming creates the
 * transaction; skipping only moves the rule on.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ConfirmDueSheet(
    item: DueItemUi,
    accounts: List<AccountEntity>,
    onDismiss: () -> Unit,
    onConfirm: (amountCents: Long, accountId: Long, date: LocalDate) -> Unit,
    onSkip: () -> Unit,
) {
    var amountText by remember(item.ruleId, item.date) { mutableStateOf(Money.formatPlain(item.amountCents)) }
    var accountId by remember(item.ruleId, item.date) {
        mutableStateOf(item.accountId.takeIf { id -> accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id)
    }
    var date by remember(item.ruleId, item.date) { mutableStateOf(item.date) }
    var pickingDate by remember { mutableStateOf(false) }

    val amount = Money.parse(amountText)?.takeIf { it > 0 }
    val canConfirm = amount != null && accountId != null
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PikColors.SurfaceRaised,
        contentColor = PikColors.TextPrimary,
    ) {
        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconCircle(item.kind.icon(), item.kind.tint(), null)
                Column(Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = PikColors.TextPrimary,
                    )
                    Text(
                        text = stringResource(
                            if (item.isOverdue) R.string.recurring_overdue_since else R.string.recurring_due_on,
                            DateFormats.shortDate(item.date),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.isOverdue) PikColors.Coral else PikColors.TextSecondary,
                    )
                }
            }

            PikMoneyField(
                value = amountText,
                onValueChange = { amountText = it },
                label = stringResource(R.string.recurring_confirm_amount),
                isError = amountText.isNotBlank() && amount == null,
                supportingText = stringResource(R.string.recurring_confirm_amount_hint),
            )

            Text(
                text = stringResource(R.string.add_account),
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

            val dateLabel = when (DateFormats.relativeDay(date, LocalDate.now())) {
                RelativeDay.TODAY -> stringResource(R.string.date_today)
                RelativeDay.YESTERDAY -> stringResource(R.string.date_yesterday)
                RelativeDay.OTHER -> DateFormats.shortDate(date)
            }
            PikCard(modifier = Modifier.fillMaxWidth(), color = PikColors.Surface, onClick = { pickingDate = true }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = PikColors.Amber,
                    )
                    Text(
                        text = stringResource(R.string.add_date),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PikColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = PikColors.TextPrimary,
                    )
                }
            }

            PikPrimaryButton(
                text = stringResource(
                    if (item.kind == RecurringKind.INCOME) R.string.recurring_confirm_income else R.string.recurring_confirm_expense,
                ),
                onClick = {
                    val id = accountId
                    if (amount != null && id != null) onConfirm(amount, id, date)
                },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth(),
            )
            PikSecondaryButton(
                text = stringResource(R.string.recurring_skip),
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.recurring_skip_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
        }
    }

    if (pickingDate) {
        PikDatePickerDialog(
            initial = date,
            onDismiss = { pickingDate = false },
            onConfirm = {
                date = it
                pickingDate = false
            },
        )
    }
}
