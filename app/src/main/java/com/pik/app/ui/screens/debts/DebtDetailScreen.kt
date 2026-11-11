package com.pik.app.ui.screens.debts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.data.repo.DebtItem
import com.pik.app.domain.Apr
import com.pik.app.domain.DateFormats
import com.pik.app.domain.DebtEntryType
import com.pik.app.domain.DebtHistoryItem
import com.pik.app.domain.Money
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikConfirmDialog
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikProgressBar
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.SectionTitle
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles

@Composable
fun DebtDetailScreen(
    onBack: () -> Unit,
    onEditDebt: (Long) -> Unit,
    onEditPayment: (Long) -> Unit,
    viewModel: DebtDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val item = state.item

    var logging by remember { mutableStateOf(false) }
    var addingEntry by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    var entryToDelete by remember { mutableStateOf<Long?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        PikBackTopBar(title = item?.debt?.name.orEmpty(), onBack = onBack)
        when {
            state.isLoading -> Box(Modifier.weight(1f))
            item == null -> Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.debt_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PikColors.TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            else -> DebtDetailContent(
                item = item,
                history = state.history,
                onLogPayment = { logging = true },
                onAddEntry = { addingEntry = true },
                onCheckBalance = { checking = true },
                onEdit = { onEditDebt(item.debt.id) },
                onPaymentClick = onEditPayment,
                onEntryClick = { entryToDelete = it },
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (item != null && logging) {
        val remaining = item.remainingCents
        val min = item.debt.minPaymentCents
        val defaultAccount = state.lastUsedAccountId?.takeIf { id -> state.accounts.any { it.id == id } }
            ?: state.accounts.firstOrNull()?.id
        LogPaymentDialog(
            debtName = item.debt.name,
            // The minimum, but never more than what is still owed.
            defaultAmountCents = if (remaining in 1 until min) remaining else min,
            accounts = state.accounts,
            defaultAccountId = defaultAccount,
            onDismiss = { logging = false },
            onConfirm = { accountId, cents, date, note ->
                viewModel.logPayment(accountId, cents, date, note)
                logging = false
            },
        )
    }
    if (item != null && addingEntry) {
        AddDebtEntryDialog(
            onDismiss = { addingEntry = false },
            onConfirm = { type, cents, date, note ->
                viewModel.addEntry(type, cents, date, note)
                addingEntry = false
            },
        )
    }
    if (item != null && checking) {
        DebtCheckBalanceDialog(
            debtName = item.debt.name,
            shownRemainingCents = item.remainingCents,
            rawRemainingCents = item.rawRemainingCents,
            onDismiss = { checking = false },
            onConfirm = { real, note ->
                viewModel.checkBalance(real, note)
                checking = false
            },
        )
    }
    entryToDelete?.let { id ->
        PikConfirmDialog(
            title = stringResource(R.string.debt_entry_delete_title),
            message = stringResource(R.string.debt_entry_delete_message),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = {
                entryToDelete = null
                viewModel.deleteEntry(id)
            },
            onDismiss = { entryToDelete = null },
            destructive = true,
        )
    }
}

@Composable
private fun DebtDetailContent(
    item: DebtItem,
    history: List<DebtHistoryItem>,
    onLogPayment: () -> Unit,
    onAddEntry: () -> Unit,
    onCheckBalance: () -> Unit,
    onEdit: () -> Unit,
    onPaymentClick: (Long) -> Unit,
    onEntryClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val debt = item.debt
    val progress = item.progress
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            PikCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.debt_detail_you_owe),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PikColors.TextSecondary,
                    )
                    Text(
                        text = Money.format(item.remainingCents),
                        style = PikNumberStyles.Hero,
                        color = PikColors.Blue,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    if (progress.paidOff) {
                        Text(
                            text = stringResource(R.string.debts_paid_off),
                            style = MaterialTheme.typography.titleMedium,
                            color = PikColors.Lime,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    PikProgressBar(
                        fraction = progress.fraction,
                        color = PikColors.Blue,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.debt_detail_paid_of,
                            Money.format(progress.paidCents),
                            Money.format(progress.paidCents + progress.remainingCents),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = PikColors.TextSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        text = stringResource(
                            R.string.debt_detail_rate_line,
                            Apr.format(debt.aprBasisPoints),
                            Money.format(debt.minPaymentCents),
                            debt.dueDay,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = PikColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PikPrimaryButton(
                    text = stringResource(R.string.debt_detail_log_payment),
                    onClick = onLogPayment,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PikSecondaryButton(
                        text = stringResource(R.string.debt_detail_add_entry),
                        onClick = onAddEntry,
                        modifier = Modifier.weight(1f),
                    )
                    PikSecondaryButton(
                        text = stringResource(R.string.debt_detail_check_balance),
                        onClick = onCheckBalance,
                        modifier = Modifier.weight(1f),
                    )
                }
                PikSecondaryButton(
                    text = stringResource(R.string.debt_detail_edit),
                    onClick = onEdit,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        item { SectionTitle(stringResource(R.string.debt_detail_history)) }
        if (history.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.debt_detail_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.TextSecondary,
                )
            }
        } else {
            item {
                PikCard(modifier = Modifier.fillMaxWidth()) {
                    history.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider(color = PikColors.Outline)
                        HistoryRow(entry, onPaymentClick, onEntryClick)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: DebtHistoryItem,
    onPaymentClick: (Long) -> Unit,
    onEntryClick: (Long) -> Unit,
) {
    val icon: ImageVector
    val tint: Color
    val title: String
    val amountText: String
    val amountColor: Color
    val onClick: () -> Unit
    when (item) {
        is DebtHistoryItem.Payment -> {
            icon = Icons.Filled.AccountBalanceWallet
            tint = PikColors.Blue
            title = stringResource(R.string.debt_history_payment_from, item.accountName)
            // Lowers the debt.
            amountText = Money.format(-item.amountCents)
            amountColor = PikColors.Lime
            onClick = { onPaymentClick(item.transactionId) }
        }
        is DebtHistoryItem.Entry -> {
            icon = when (item.type) {
                DebtEntryType.INTEREST -> Icons.Filled.Percent
                DebtEntryType.FEE -> Icons.Filled.Receipt
                DebtEntryType.ADJUSTMENT -> Icons.Filled.Tune
            }
            tint = if (item.type == DebtEntryType.ADJUSTMENT) PikColors.TextSecondary else PikColors.Coral
            title = stringResource(
                when (item.type) {
                    DebtEntryType.INTEREST -> R.string.debt_entry_interest
                    DebtEntryType.FEE -> R.string.debt_entry_fee
                    DebtEntryType.ADJUSTMENT -> R.string.debt_entry_adjustment
                },
            )
            amountText = Money.format(item.amountCents, showPlusSign = true)
            // Raises the debt: coral. A negative adjustment lowers it: lime.
            amountColor = if (item.amountCents < 0) PikColors.Lime else PikColors.Coral
            onClick = { onEntryClick(item.entryId) }
        }
    }
    val note = when (item) {
        is DebtHistoryItem.Payment -> item.note
        is DebtHistoryItem.Entry -> item.note
    }
    val subtitle = listOfNotNull(note.takeIf { it.isNotBlank() }, DateFormats.shortDate(item.date))
        .joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconCircle(icon = icon, tint = tint, contentDescription = null)
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = PikColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = amountText,
            style = PikNumberStyles.Medium,
            color = amountColor,
            maxLines = 1,
        )
    }
}
