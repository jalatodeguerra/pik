package com.pik.app.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.pik.app.domain.TransactionType
import com.pik.app.ui.components.CheckBalanceDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.domain.AccountType
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.SafeToSpend
import java.time.YearMonth
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.SectionTitle
import com.pik.app.ui.components.TransactionListItem
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles

@Composable
fun HomeScreen(
    onAddClick: () -> Unit,
    onSeeAll: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onPlanClick: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // The month label and totals must follow the calendar if the app stays open across a month change.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshMonth()
        onPauseOrDispose { }
    }
    HomeContent(state, onAddClick, onSeeAll, onTransactionClick, onSettingsClick, onPlanClick, viewModel::checkBalance)
}

@Composable
private fun HomeContent(
    state: HomeUiState,
    onAddClick: () -> Unit,
    onSeeAll: () -> Unit,
    onTransactionClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onPlanClick: () -> Unit,
    onCheckBalance: (accountId: Long, realCents: Long, note: String) -> Unit,
) {
    val periodLabel = state.period.label()
    val headerLabel = if (state.period.startDay == 1) {
        DateFormats.monthYear(YearMonth.from(state.period.start))
    } else {
        periodLabel
    }
    var checking by remember { mutableStateOf<AccountWithBalance?>(null) }
    CheckBalanceHost(
        checking = checking,
        onDismiss = { checking = null },
        onConfirm = { id, real, note ->
            onCheckBalance(id, real, note)
            checking = null
        },
    )
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineLarge,
                    color = PikColors.Lime,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = headerLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = PikColors.TextSecondary,
                    )
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings_title),
                            tint = PikColors.TextPrimary,
                        )
                    }
                }
            }
        }
        item {
            PikCard(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.home_you_have),
                        style = MaterialTheme.typography.bodyMedium,
                        color = PikColors.TextSecondary,
                    )
                    Text(
                        text = Money.format(state.totalCents),
                        style = PikNumberStyles.Hero,
                        color = PikColors.TextPrimary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.home_cash_and_bank),
                        style = MaterialTheme.typography.bodySmall,
                        color = PikColors.TextSecondary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        item {
            PikCard(modifier = Modifier.fillMaxWidth(), color = PikColors.Surface) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.home_you_owe),
                            style = MaterialTheme.typography.bodyMedium,
                            color = PikColors.TextSecondary,
                        )
                        Text(
                            text = stringResource(R.string.home_you_owe_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = PikColors.TextSecondary,
                        )
                    }
                    Text(
                        text = Money.format(0),
                        style = PikNumberStyles.Large,
                        color = PikColors.Blue,
                    )
                }
            }
        }
        item { SafeToSpendCard(state.safeToSpend, onClick = onPlanClick) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                MonthFlowCard(
                    label = stringResource(R.string.home_money_in, periodLabel),
                    amount = Money.format(state.monthInCents),
                    color = PikColors.Income,
                    modifier = Modifier.weight(1f),
                )
                MonthFlowCard(
                    label = stringResource(R.string.home_money_out, periodLabel),
                    amount = Money.format(state.monthOutCents),
                    color = PikColors.Expense,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        item { SectionTitle(stringResource(R.string.home_accounts)) }
        item {
            PikCard(modifier = Modifier.fillMaxWidth()) {
                state.accounts.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = PikColors.Outline)
                    AccountRow(item, onClick = { checking = item })
                }
            }
        }
        if (!state.isLoading) {
            if (state.hasTransactions) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        SectionTitle(stringResource(R.string.home_recent))
                        TextButton(
                            onClick = onSeeAll,
                            colors = ButtonDefaults.textButtonColors(contentColor = PikColors.Lime),
                        ) {
                            Text(stringResource(R.string.home_see_all))
                        }
                    }
                }
                item {
                    PikCard(modifier = Modifier.fillMaxWidth()) {
                        state.recent.forEachIndexed { index, row ->
                            if (index > 0) HorizontalDivider(color = PikColors.Outline)
                            TransactionListItem(
                                row = row,
                                showDate = true,
                                // Balance adjustments are managed in Activity, not edited in the Add screen.
                                onClick = if (row.type == TransactionType.ADJUSTMENT) null else {
                                    { onTransactionClick(row.id) }
                                },
                            )
                        }
                    }
                }
            } else {
                item {
                    PikCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.home_empty_title),
                                style = MaterialTheme.typography.titleLarge,
                                color = PikColors.TextPrimary,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = stringResource(R.string.home_empty_message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = PikColors.TextSecondary,
                                textAlign = TextAlign.Center,
                            )
                            PikPrimaryButton(
                                text = stringResource(R.string.home_empty_button),
                                onClick = onAddClick,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Same number as the Plan tab (SafeToSpendCalculator); tapping opens Plan. */
@Composable
private fun SafeToSpendCard(safe: SafeToSpend, onClick: () -> Unit) {
    val accent = if (safe.isOverSpent) PikColors.Coral else PikColors.Lime
    PikCard(modifier = Modifier.fillMaxWidth(), color = PikColors.Surface, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.safe_to_spend),
                    style = MaterialTheme.typography.bodyMedium,
                    color = PikColors.TextSecondary,
                )
                val detail = when {
                    safe.plannedIncomeCents <= 0L && safe.spentCents <= 0L ->
                        stringResource(R.string.safe_no_income_hint)
                    safe.isOverSpent ->
                        stringResource(R.string.home_safe_to_spend_over, Money.format(-safe.safeCents))
                    safe.daysLeft <= 1 ->
                        stringResource(R.string.safe_per_day_last, Money.format(safe.perDayCents))
                    else ->
                        stringResource(R.string.safe_per_day, Money.format(safe.perDayCents), safe.daysLeft)
                }
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                )
            }
            Text(
                text = Money.format(safe.safeCents),
                style = PikNumberStyles.Large,
                color = accent,
                maxLines = 1,
                modifier = Modifier.padding(start = 12.dp),
            )
        }
    }
}

@Composable
private fun CheckBalanceHost(checking: AccountWithBalance?, onDismiss: () -> Unit, onConfirm: (Long, Long, String) -> Unit) {
    if (checking != null) {
        CheckBalanceDialog(
            accountName = checking.account.name,
            currentCents = checking.balanceCents,
            onDismiss = onDismiss,
            onConfirm = { real, note -> onConfirm(checking.account.id, real, note) },
        )
    }
}

@Composable
private fun MonthFlowCard(label: String, amount: String, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    PikCard(modifier = modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
            Text(
                text = amount,
                style = PikNumberStyles.Large,
                color = color,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun AccountRow(item: AccountWithBalance, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconCircle(
            icon = if (item.account.type == AccountType.CASH) Icons.Filled.Payments else Icons.Filled.AccountBalance,
            tint = PikColors.Lime,
            contentDescription = null,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = item.account.name,
                style = MaterialTheme.typography.bodyLarge,
                color = PikColors.TextPrimary,
                maxLines = 1,
            )
            Text(
                text = stringResource(
                    if (item.account.type == AccountType.CASH) R.string.account_type_cash else R.string.account_type_bank,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
        }
        Text(
            text = Money.format(item.balanceCents),
            style = PikNumberStyles.Medium,
            color = PikColors.TextPrimary,
            maxLines = 1,
        )
    }
}
