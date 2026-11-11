package com.pik.app.ui.screens.recurring

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pik.app.R
import com.pik.app.data.db.RecurringRuleEntity
import com.pik.app.domain.DateFormats
import com.pik.app.domain.Money
import com.pik.app.domain.RecurringKind
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikBackTopBar
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.components.PikPrimaryButton
import com.pik.app.ui.components.PikSecondaryButton
import com.pik.app.ui.components.SectionTitle
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles
import java.time.LocalDate

@Composable
fun RecurringScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (ruleId: Long) -> Unit,
    viewModel: RecurringViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refreshToday()
        onPauseOrDispose { }
    }
    var confirming by remember { mutableStateOf<DueItemUi?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        PikBackTopBar(title = stringResource(R.string.recurring_title), onBack = onBack)
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!state.isLoading && !state.hasRules) {
                item { EmptyCard(onAdd) }
            }
            if (state.hasRules) {
                item { WeekStrip(state.week, state.today) }
                if (state.due.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.recurring_due)) }
                    item {
                        PikCard(modifier = Modifier.fillMaxWidth()) {
                            state.due.forEachIndexed { index, item ->
                                if (index > 0) HorizontalDivider(color = PikColors.Outline)
                                DueRow(item, state.today, onClick = { confirming = item })
                            }
                        }
                    }
                }
                if (state.monthlyOutCents > 0L) {
                    item { TotalsCard(state.monthlyOutCents, state.yearlyOutCents) }
                }
                state.groups.forEach { group ->
                    item(key = "title-${group.kind}") { SectionTitle(stringResource(group.kind.groupTitleRes())) }
                    item(key = "group-${group.kind}") {
                        PikCard(modifier = Modifier.fillMaxWidth()) {
                            group.rules.forEachIndexed { index, rule ->
                                if (index > 0) HorizontalDivider(color = PikColors.Outline)
                                RuleRow(rule, onClick = { onEdit(rule.id) })
                            }
                        }
                    }
                }
                item {
                    PikSecondaryButton(
                        text = stringResource(R.string.recurring_add),
                        onClick = onAdd,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item {
                    Text(
                        text = stringResource(R.string.recurring_nothing_automatic),
                        style = MaterialTheme.typography.bodySmall,
                        color = PikColors.TextSecondary,
                    )
                }
            }
        }
    }

    confirming?.let { item ->
        ConfirmDueSheet(
            item = item,
            accounts = state.accounts,
            onDismiss = { confirming = null },
            onConfirm = { amount, accountId, date ->
                viewModel.confirm(item.ruleId, amount, accountId, date)
                confirming = null
            },
            onSkip = {
                viewModel.skip(item.ruleId)
                confirming = null
            },
        )
    }
}

@Composable
private fun EmptyCard(onAdd: () -> Unit) {
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconCircle(Icons.Filled.Autorenew, PikColors.Amber, null, size = 64)
            Text(
                text = stringResource(R.string.recurring_empty_title),
                style = MaterialTheme.typography.titleLarge,
                color = PikColors.TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.recurring_empty_message),
                style = MaterialTheme.typography.bodyMedium,
                color = PikColors.TextSecondary,
                textAlign = TextAlign.Center,
            )
            PikPrimaryButton(
                text = stringResource(R.string.recurring_add),
                onClick = onAdd,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/** The next 7 days with an amber dot for each item due on that day. */
@Composable
private fun WeekStrip(week: List<WeekDayUi>, today: LocalDate) {
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
        ) {
            week.forEach { day ->
                val isToday = day.date == today
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = DateFormats.weekdayShort(day.date.dayOfWeek),
                        style = MaterialTheme.typography.labelSmall,
                        color = PikColors.TextSecondary,
                        maxLines = 1,
                    )
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isToday) PikColors.Amber else PikColors.Surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = day.date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isToday) PikColors.OnLime else PikColors.TextPrimary,
                        )
                    }
                    Row(
                        modifier = Modifier.size(width = 32.dp, height = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(day.dueCount.coerceAtMost(3)) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(PikColors.Amber),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalsCard(monthlyCents: Long, yearlyCents: Long) {
    PikCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TotalLine(stringResource(R.string.recurring_total_month), Money.format(monthlyCents))
            HorizontalDivider(color = PikColors.Outline)
            TotalLine(stringResource(R.string.recurring_total_year), Money.format(yearlyCents))
            Text(
                text = stringResource(R.string.recurring_total_hint),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun TotalLine(label: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = PikColors.TextSecondary)
        Text(text = amount, style = PikNumberStyles.Large, color = PikColors.Expense, maxLines = 1)
    }
}

@Composable
private fun RuleRow(rule: RecurringRuleEntity, onClick: () -> Unit) {
    val second = if (rule.active) {
        stringResource(R.string.recurring_next_date, DateFormats.shortDate(rule.nextDueDate))
    } else {
        stringResource(R.string.recurring_inactive)
    }
    val isIncome = rule.kind == RecurringKind.INCOME
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconCircle(
            icon = rule.kind.icon(),
            tint = if (rule.active) rule.kind.tint() else PikColors.TextSecondary,
            contentDescription = null,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = rule.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (rule.active) PikColors.TextPrimary else PikColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = frequencyText(rule),
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = second,
                style = MaterialTheme.typography.bodySmall,
                color = PikColors.TextSecondary,
                maxLines = 1,
            )
        }
        Text(
            text = if (isIncome) Money.format(rule.amountCents, showPlusSign = true) else Money.format(-rule.amountCents),
            style = PikNumberStyles.Medium,
            color = if (isIncome) PikColors.Income else PikColors.Expense,
            maxLines = 1,
        )
    }
}
