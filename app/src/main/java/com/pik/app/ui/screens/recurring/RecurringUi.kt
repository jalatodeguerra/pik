package com.pik.app.ui.screens.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pik.app.R
import com.pik.app.data.db.RecurringRuleEntity
import com.pik.app.domain.DateFormats
import com.pik.app.domain.DueOccurrence
import com.pik.app.domain.Money
import com.pik.app.domain.RecurrenceCalculator
import com.pik.app.domain.RecurrenceUnit
import com.pik.app.domain.RecurringKind
import com.pik.app.domain.RecurringPlanner
import com.pik.app.ui.components.IconCircle
import com.pik.app.ui.components.PikCard
import com.pik.app.ui.theme.PikColors
import com.pik.app.ui.theme.PikNumberStyles
import java.time.LocalDate

/** One occurrence that waits for the user, with what the lists need to show it. */
data class DueItemUi(
    val ruleId: Long,
    val name: String,
    val kind: RecurringKind,
    val amountCents: Long,
    val accountId: Long,
    val date: LocalDate,
    val isOverdue: Boolean,
    /** Only the oldest pending occurrence of a rule can be confirmed or skipped. */
    val isNext: Boolean,
)

/** The "Due soon / Overdue" list: every unconfirmed occurrence up to today + 7 days, oldest first. */
fun buildDueItems(rules: List<RecurringRuleEntity>, today: LocalDate): List<DueItemUi> {
    val byId = rules.associateBy { it.id }
    return RecurringPlanner.dueItems(rules.map { it.toFacts() }, today).mapNotNull { due: DueOccurrence ->
        val rule = byId[due.ruleId] ?: return@mapNotNull null
        DueItemUi(
            ruleId = rule.id,
            name = rule.name,
            kind = rule.kind,
            amountCents = rule.amountCents,
            accountId = rule.accountId,
            date = due.date,
            isOverdue = due.isOverdue,
            isNext = due.isNext,
        )
    }
}

fun RecurringKind.icon(): ImageVector = when (this) {
    RecurringKind.BILL -> Icons.Filled.Receipt
    RecurringKind.SUBSCRIPTION -> Icons.Filled.Subscriptions
    RecurringKind.INCOME -> Icons.Filled.Payments
    RecurringKind.DEBT_PAYMENT -> Icons.Filled.AccountBalanceWallet
}

/** Amber means recurring; debt payments keep the debt blue. */
fun RecurringKind.tint(): Color = if (this == RecurringKind.DEBT_PAYMENT) PikColors.Blue else PikColors.Amber

fun RecurringKind.labelRes(): Int = when (this) {
    RecurringKind.BILL -> R.string.recurring_kind_bill
    RecurringKind.SUBSCRIPTION -> R.string.recurring_kind_subscription
    RecurringKind.INCOME -> R.string.recurring_kind_income
    RecurringKind.DEBT_PAYMENT -> R.string.recurring_kind_debt_payment
}

fun RecurringKind.groupTitleRes(): Int = when (this) {
    RecurringKind.BILL -> R.string.recurring_group_bills
    RecurringKind.SUBSCRIPTION -> R.string.recurring_group_subscriptions
    RecurringKind.INCOME -> R.string.recurring_group_income
    RecurringKind.DEBT_PAYMENT -> R.string.recurring_group_debt_payments
}

fun RecurrenceUnit.labelRes(): Int = when (this) {
    RecurrenceUnit.WEEK -> R.string.recurring_unit_week
    RecurrenceUnit.MONTH -> R.string.recurring_unit_month
    RecurrenceUnit.YEAR -> R.string.recurring_unit_year
}

/** "Every month on the 5th", "Every 3 months on the 15th", "Every 2 weeks on Monday", "Every year on 8 Mar". */
@Composable
fun frequencyText(rule: RecurringRuleEntity): String {
    val every = rule.intervalCount.coerceAtLeast(1)
    return when (rule.intervalUnit) {
        RecurrenceUnit.WEEK -> {
            val weekday = DateFormats.weekdayName(rule.startDate.dayOfWeek)
            if (every == 1) {
                stringResource(R.string.recurring_freq_week_one, weekday)
            } else {
                stringResource(R.string.recurring_freq_week_other, every, weekday)
            }
        }
        RecurrenceUnit.MONTH -> {
            val day = DateFormats.ordinalDay(rule.dayOfMonth ?: rule.startDate.dayOfMonth)
            if (every == 1) {
                stringResource(R.string.recurring_freq_month_one, day)
            } else {
                stringResource(R.string.recurring_freq_month_other, every, day)
            }
        }
        RecurrenceUnit.YEAR -> {
            val date = DateFormats.dayMonth(RecurrenceCalculator.rawDate(rule.schedule, 0))
            if (every == 1) {
                stringResource(R.string.recurring_freq_year_one, date)
            } else {
                stringResource(R.string.recurring_freq_year_other, every, date)
            }
        }
    }
}

/** "Today", "Tomorrow" or "Fri 13 Nov". */
@Composable
fun dueDateText(date: LocalDate, today: LocalDate): String = when (date) {
    today -> stringResource(R.string.date_today)
    today.plusDays(1) -> stringResource(R.string.date_tomorrow)
    else -> DateFormats.dayHeader(date)
}

/** A row of the Due list: tap (when it is the oldest pending one) to confirm or skip it. */
@Composable
fun DueRow(item: DueItemUi, today: LocalDate, onClick: () -> Unit) {
    val subtitle = when {
        !item.isNext -> stringResource(R.string.recurring_after_previous)
        item.isOverdue -> stringResource(R.string.recurring_overdue_since, DateFormats.shortDate(item.date))
        else -> dueDateText(item.date, today)
    }
    val amountText = if (item.kind == RecurringKind.INCOME) {
        Money.format(item.amountCents, showPlusSign = true)
    } else {
        Money.format(-item.amountCents)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (item.isNext) 1f else 0.55f)
            .clickable(enabled = item.isNext, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconCircle(item.kind.icon(), item.kind.tint(), null)
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                color = PikColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (item.isOverdue && item.isNext) PikColors.Coral else PikColors.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = amountText,
            style = PikNumberStyles.Medium,
            color = if (item.kind == RecurringKind.INCOME) PikColors.Income else PikColors.Expense,
            maxLines = 1,
        )
    }
}

/** The "Recurring" shortcut card on Home and Plan. */
@Composable
fun RecurringLinkCard(subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    PikCard(modifier = modifier.fillMaxWidth(), color = PikColors.Surface, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconCircle(Icons.Filled.Autorenew, PikColors.Amber, null)
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.recurring_title),
                    style = MaterialTheme.typography.bodyLarge,
                    color = PikColors.TextPrimary,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = PikColors.TextSecondary,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = PikColors.TextSecondary,
            )
        }
    }
}
