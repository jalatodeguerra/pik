package com.pik.app.ui.screens.plan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.domain.BudgetProgress
import com.pik.app.domain.BudgetStatus
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.SafeToSpend
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikProgressBar
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.SectionTitle
import com.pik.app.ui.theme.CategoryStyle
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles

/** Bar color: lime, amber from 80 percent, coral when over. */
internal fun BudgetStatus.color(): Color = when (this) {
    BudgetStatus.OK -> PikColors.Lime
    BudgetStatus.WARNING -> PikColors.Amber
    BudgetStatus.OVER -> PikColors.Coral
}

@Composable
fun PlanScreen(
    onSetBudgets: () -> Unit,
    viewModel: PlanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Follow the calendar if the app stays open across midnight / a period change.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    PlanContent(
        state = state,
        onPrevious = viewModel::previousPeriod,
        onNext = viewModel::nextPeriod,
        onSetBudgets = onSetBudgets,
    )
}

@Composable
private fun PlanContent(
    state: PlanUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSetBudgets: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { PeriodHeader(state, onPrevious, onNext) }

        val safe = state.safeToSpend
        if (safe != null) {
            item { SafeToSpendCard(safe) }
        } else if (!state.isLoading) {
            item { TotalsCard(state) }
        }

        state.overall?.let { overall ->
            item { OverallBudgetCard(overall) }
        }

        if (!state.isLoading) {
            if (state.hasAnyBudget) {
                if (state.lines.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.plan_budgets)) }
                    item {
                        PikCard(modifier = Modifier.fillMaxWidth()) {
                            state.lines.forEachIndexed { index, line ->
                                if (index > 0) HorizontalDivider(color = PikColors.Outline)
                                BudgetLineRow(line)
                            }
                        }
                    }
                }
                if (state.isCurrent) {
                    item {
                        PikSecondaryButton(
                            text = stringResource(R.string.plan_edit_budgets),
                            onClick = onSetBudgets,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                item { EmptyBudgetsCard(state.isCurrent, onSetBudgets) }
            }

            if (state.notBudgeted.isNotEmpty()) {
                item { SectionTitle(stringResource(R.string.plan_not_budgeted)) }
                item {
                    PikCard(modifier = Modifier.fillMaxWidth()) {
                        state.notBudgeted.forEachIndexed { index, item ->
                            if (index > 0) HorizontalDivider(color = PikColors.Outline)
                            NotBudgetedRow(item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodHeader(state: PlanUiState, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.previous_period),
                tint = PikColors.TextPrimary,
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = state.period.label(),
                style = MaterialTheme.typography.titleLarge,
                color = PikColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(
                    R.string.plan_period_range,
                    DateFormats.shortDate(state.period.start),
                    DateFormats.shortDate(state.period.endInclusive),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
        }
        IconButton(onClick = onNext, enabled = state.canGoNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.next_period),
                tint = if (state.canGoNext) PikColors.TextPrimary else PikColors.Outline,
            )
        }
    }
}

@Composable
private fun SafeToSpendCard(safe: SafeToSpend) {
    val accent = if (safe.isOverSpent) PikColors.Coral else PikColors.Lime
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text(
                text = stringResource(if (safe.isOverSpent) R.string.safe_over_spent else R.string.safe_to_spend),
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextSecondary,
            )
            Text(
                text = Money.format(safe.safeCents),
                style = PikNumberStyles.Hero,
                color = accent,
                maxLines = 1,
                modifier = Modifier.padding(top = 4.dp),
            )
            val detail = when {
                safe.plannedIncomeCents <= 0L && safe.spentCents <= 0L ->
                    stringResource(R.string.safe_no_income_hint)
                safe.isOverSpent ->
                    stringResource(R.string.safe_over_spent_by, Money.format(-safe.safeCents))
                safe.daysLeft <= 1 ->
                    stringResource(R.string.safe_per_day_last, Money.format(safe.perDayCents))
                else ->
                    stringResource(R.string.safe_per_day, Money.format(safe.perDayCents), safe.daysLeft)
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextPrimary,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (safe.debtPaymentsDueCents > 0L) {
                Text(
                    text = stringResource(R.string.safe_debts_set_aside, Money.format(safe.debtPaymentsDueCents)),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.Blue,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (safe.plannedIncomeCents > 0L) {
                val fraction = (safe.spentCents.toDouble() / safe.plannedIncomeCents).toFloat()
                PikProgressBar(
                    fraction = fraction,
                    color = if (safe.spentCents > safe.plannedIncomeCents) PikColors.Coral else PikColors.Lime,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = stringResource(
                        R.string.safe_spent_of_planned,
                        Money.format(safe.spentCents),
                        Money.format(safe.plannedIncomeCents),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TotalsCard(state: PlanUiState) {
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TotalRow(stringResource(R.string.plan_total_income), Money.format(state.incomeCents), PikColors.Income)
            TotalRow(stringResource(R.string.plan_total_spent), Money.format(state.spentCents), PikColors.Expense)
            HorizontalDivider(color = PikColors.Outline)
            val left = state.incomeCents - state.spentCents
            TotalRow(
                stringResource(R.string.plan_total_left),
                Money.format(left),
                if (left < 0) PikColors.Coral else PikColors.TextPrimary,
            )
        }
    }
}

@Composable
private fun TotalRow(label: String, amount: String, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = PikColors.TextSecondary)
        Text(text = amount, style = PikNumberStyles.Large, color = color, maxLines = 1)
    }
}

@Composable
private fun leftOrOverText(progress: BudgetProgress): String =
    if (progress.leftCents >= 0) {
        stringResource(R.string.plan_left, Money.format(progress.leftCents))
    } else {
        stringResource(R.string.plan_over, Money.format(-progress.leftCents))
    }

@Composable
private fun OverallBudgetCard(progress: BudgetProgress) {
    val color = progress.status.color()
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.plan_overall_budget),
                    style = MaterialTheme.typography.titleMedium,
                    color = PikColors.TextPrimary,
                )
                Text(
                    text = leftOrOverText(progress),
                    style = PikNumberStyles.Medium,
                    color = color,
                    maxLines = 1,
                )
            }
            PikProgressBar(fraction = progress.fraction, color = color, modifier = Modifier.padding(top = 12.dp))
            Text(
                text = stringResource(
                    R.string.plan_spent_of_limit,
                    Money.format(progress.spentCents),
                    Money.format(progress.limitCents),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun BudgetLineRow(line: PlanBudgetLine) {
    val color = line.progress.status.color()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconCircle(
                icon = CategoryStyle.icon(line.iconKey),
                tint = CategoryStyle.color(line.colorKey),
                contentDescription = null,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = line.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = PikColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(
                        R.string.plan_spent_of_limit,
                        Money.format(line.progress.spentCents),
                        Money.format(line.progress.limitCents),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = leftOrOverText(line.progress),
                style = PikNumberStyles.Medium,
                color = color,
                maxLines = 1,
            )
        }
        PikProgressBar(fraction = line.progress.fraction, color = color, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun NotBudgetedRow(item: PlanNotBudgeted) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (item.isDebtPayment) {
            IconCircle(
                icon = Icons.Filled.AccountBalanceWallet,
                tint = PikColors.Blue,
                contentDescription = null,
                size = 36,
            )
        } else {
            IconCircle(
                icon = CategoryStyle.icon(item.iconKey),
                tint = CategoryStyle.color(item.colorKey),
                contentDescription = null,
                size = 36,
            )
        }
        Text(
            text = when {
                item.isDebtPayment -> stringResource(R.string.plan_debt_payments)
                else -> item.name ?: stringResource(R.string.transaction_uncategorized)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = PikColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = Money.format(item.spentCents),
            style = PikNumberStyles.Medium,
            color = PikColors.TextSecondary,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyBudgetsCard(isCurrent: Boolean, onSetBudgets: () -> Unit) {
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.plan_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = PikColors.TextPrimary,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(if (isCurrent) R.string.plan_empty_message else R.string.plan_empty_past),
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            if (isCurrent) {
                PikPrimaryButton(
                    text = stringResource(R.string.plan_set_budgets),
                    onClick = onSetBudgets,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
