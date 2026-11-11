package com.pik.app.domain

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * When a recurring rule happens: "every [intervalCount] [unit]s", counted from [startDate].
 *
 * - WEEK: every N * 7 days from [startDate] ([dayOfMonth] is ignored).
 * - MONTH: every N months. The day is [dayOfMonth] (default: the day of [startDate]); in a month
 *   that is too short the last day of that month is used (31 gives 30 Apr, 28 or 29 Feb).
 * - YEAR: the same as MONTH with N * 12 months (an annual date such as 29 Feb falls back to 28 Feb).
 *
 * Occurrence number 0 is the first one, number 1 the next, and so on. The number is what a rule
 * stores as "occurrences done" (confirmed or skipped). The series stops after [endDate] (the last
 * occurrence may fall on it) and after [maxOccurrences] occurrences, whichever comes first.
 *
 * Every date is computed from the start of the series and the occurrence number, never from the
 * previous date, so a clamped day never drifts (31 Jan, 28 Feb, 31 Mar, 30 Apr, ...).
 */
data class RecurrenceSchedule(
    val startDate: LocalDate,
    val intervalCount: Int,
    val unit: RecurrenceUnit,
    val dayOfMonth: Int? = null,
    val endDate: LocalDate? = null,
    val maxOccurrences: Int? = null,
) {
    /** The interval, never below 1. */
    val every: Int get() = intervalCount.coerceAtLeast(1)

    /** The day of the month used by MONTH and YEAR series, 1..31. */
    val effectiveDay: Int get() = (dayOfMonth ?: startDate.dayOfMonth).coerceIn(1, 31)
}

/** One occurrence of a series: its number (0 based) and its date. */
data class Occurrence(val index: Int, val date: LocalDate)

/** Pure date maths for recurring rules (no Android, no database). */
object RecurrenceCalculator {

    /** Longest list of pending occurrences of one rule (a safety net for very old weekly rules). */
    const val MAX_PENDING = 60

    private const val MAX_SCAN = 100_000

    /** The date of occurrence [index], ignoring the end date and the maximum. */
    fun rawDate(schedule: RecurrenceSchedule, index: Int): LocalDate {
        val steps = schedule.every.toLong() * index
        return when (schedule.unit) {
            RecurrenceUnit.WEEK -> schedule.startDate.plusWeeks(steps)
            RecurrenceUnit.MONTH ->
                onDay(YearMonth.from(schedule.startDate).plusMonths(steps), schedule.effectiveDay)
            RecurrenceUnit.YEAR ->
                onDay(YearMonth.from(schedule.startDate).plusYears(steps), schedule.effectiveDay)
        }
    }

    private fun onDay(month: YearMonth, day: Int): LocalDate = month.atDay(minOf(day, month.lengthOfMonth()))

    /** The date of occurrence [index], or null when the series is over by then (end date or maximum). */
    fun dateOf(schedule: RecurrenceSchedule, index: Int): LocalDate? {
        if (index < 0) return null
        val max = schedule.maxOccurrences
        if (max != null && index >= max) return null
        val date = rawDate(schedule, index)
        val end = schedule.endDate
        if (end != null && date > end) return null
        return date
    }

    /** True when occurrence number [done] does not exist: everything was confirmed or skipped. */
    fun isFinished(schedule: RecurrenceSchedule, done: Int): Boolean = dateOf(schedule, done.coerceAtLeast(0)) == null

    /**
     * The date to store as "next due date" after [done] occurrences: the date of the next
     * occurrence, or (when the series is over) the date of the last one.
     */
    fun nextDueDate(schedule: RecurrenceSchedule, done: Int): LocalDate {
        val safeDone = done.coerceAtLeast(0)
        return dateOf(schedule, safeDone) ?: rawDate(schedule, maxOf(safeDone - 1, 0))
    }

    /** The smallest occurrence number whose raw date is on or after [date] (end date and maximum ignored). */
    fun indexOnOrAfter(schedule: RecurrenceSchedule, date: LocalDate): Int {
        val start = schedule.startDate
        val estimate: Long = when (schedule.unit) {
            RecurrenceUnit.WEEK -> ChronoUnit.DAYS.between(start, date) / (7L * schedule.every)
            RecurrenceUnit.MONTH ->
                ChronoUnit.MONTHS.between(start.withDayOfMonth(1), date.withDayOfMonth(1)) / schedule.every
            RecurrenceUnit.YEAR ->
                ChronoUnit.MONTHS.between(start.withDayOfMonth(1), date.withDayOfMonth(1)) / (12L * schedule.every)
        }
        var index = (estimate - 2).coerceIn(0L, MAX_SCAN.toLong()).toInt()
        // The estimate is a lower bound; step back if it overshot, then forward to the first match.
        var guard = 0
        while (index > 0 && rawDate(schedule, index - 1) >= date && guard++ < MAX_SCAN) index--
        while (rawDate(schedule, index) < date && guard++ < MAX_SCAN) index++
        return index
    }

    /** The first occurrence on or after [date] with number >= [fromIndex], or null when the series is over. */
    fun nextOnOrAfter(schedule: RecurrenceSchedule, date: LocalDate, fromIndex: Int = 0): Occurrence? {
        val index = maxOf(fromIndex.coerceAtLeast(0), indexOnOrAfter(schedule, date))
        return dateOf(schedule, index)?.let { Occurrence(index, it) }
    }

    /** The first occurrence strictly after [date]. */
    fun nextAfter(schedule: RecurrenceSchedule, date: LocalDate, fromIndex: Int = 0): Occurrence? =
        nextOnOrAfter(schedule, date.plusDays(1), fromIndex)

    /** Every occurrence with a date from [from] to [to], both inclusive, in order (at most [limit]). */
    fun between(
        schedule: RecurrenceSchedule,
        from: LocalDate,
        to: LocalDate,
        limit: Int = 1_000,
    ): List<Occurrence> {
        val result = ArrayList<Occurrence>()
        var index = indexOnOrAfter(schedule, from)
        while (result.size < limit) {
            val date = dateOf(schedule, index) ?: break
            if (date > to) break
            result.add(Occurrence(index, date))
            index++
        }
        return result
    }

    /**
     * The occurrences that are not confirmed or skipped yet: numbers [done] onwards with a date on or
     * before [until] (at most [MAX_PENDING]). Overdue ones come first.
     */
    fun pending(schedule: RecurrenceSchedule, done: Int, until: LocalDate, limit: Int = MAX_PENDING): List<Occurrence> {
        val result = ArrayList<Occurrence>()
        var index = done.coerceAtLeast(0)
        while (result.size < limit) {
            val date = dateOf(schedule, index) ?: break
            if (date > until) break
            result.add(Occurrence(index, date))
            index++
        }
        return result
    }

    /**
     * How many occurrences lie before [cutoff]: used when a rule is created or its schedule changes,
     * so that history is not backfilled (those occurrences count as done). Never more than the
     * occurrences that exist (end date, maximum).
     */
    fun countBefore(schedule: RecurrenceSchedule, cutoff: LocalDate): Int {
        var count = indexOnOrAfter(schedule, cutoff)
        schedule.maxOccurrences?.let { count = minOf(count, it) }
        schedule.endDate?.let { count = minOf(count, indexOnOrAfter(schedule, it.plusDays(1))) }
        return count
    }

    /**
     * For MONTH and YEAR series whose [RecurrenceSchedule.dayOfMonth] is earlier than the start date's day
     * (start 20 Jan, day 5), the first occurrence would lie before the start. The start is moved to the
     * second occurrence, so that occurrence number 0 is never before the start.
     */
    fun normalized(schedule: RecurrenceSchedule): RecurrenceSchedule =
        if (schedule.unit != RecurrenceUnit.WEEK && rawDate(schedule, 0) < schedule.startDate) {
            schedule.copy(startDate = rawDate(schedule, 1))
        } else {
            schedule
        }

    // ---- amounts per month / year ----

    private fun divideHalfUp(numerator: Long, denominator: Long): Long = (2 * numerator + denominator) / (2 * denominator)

    /** What an amount that repeats like this costs per month on average (weekly: x 52 / 12), rounded to the cent. */
    fun monthlyCents(amountCents: Long, every: Int, unit: RecurrenceUnit): Long {
        val n = every.coerceAtLeast(1).toLong()
        return when (unit) {
            RecurrenceUnit.WEEK -> divideHalfUp(amountCents * 52, 12 * n)
            RecurrenceUnit.MONTH -> divideHalfUp(amountCents, n)
            RecurrenceUnit.YEAR -> divideHalfUp(amountCents, 12 * n)
        }
    }

    /** What an amount that repeats like this costs per year on average (weekly: x 52), rounded to the cent. */
    fun yearlyCents(amountCents: Long, every: Int, unit: RecurrenceUnit): Long {
        val n = every.coerceAtLeast(1).toLong()
        return when (unit) {
            RecurrenceUnit.WEEK -> divideHalfUp(amountCents * 52, n)
            RecurrenceUnit.MONTH -> divideHalfUp(amountCents * 12, n)
            RecurrenceUnit.YEAR -> divideHalfUp(amountCents, n)
        }
    }
}

/** A recurring rule as the planner needs it (no database types). */
data class RecurringRuleFacts(
    val id: Long,
    val kind: RecurringKind,
    val amountCents: Long,
    val schedule: RecurrenceSchedule,
    val occurrencesDone: Int,
    val active: Boolean = true,
    val remindDayBefore: Boolean = false,
)

/**
 * One occurrence that waits for the user. [isNext] is true for the oldest pending occurrence of its
 * rule: only that one can be confirmed or skipped (they are handled in order).
 */
data class DueOccurrence(
    val ruleId: Long,
    val kind: RecurringKind,
    val amountCents: Long,
    val index: Int,
    val date: LocalDate,
    val isOverdue: Boolean,
    val isNext: Boolean,
)

/** What is still to happen in the rest of a period, for safe to spend. */
data class StillDue(val billsCents: Long, val incomeCents: Long)

/** The daily reminder: what is due tomorrow (rules with the reminder on) and how many are overdue. */
data class ReminderSummary(val tomorrow: List<DueOccurrence>, val overdueCount: Int) {
    val isEmpty: Boolean get() = tomorrow.isEmpty()
}

/** Pure planning on top of [RecurrenceCalculator]: what is due, what is still due, what to remind. */
object RecurringPlanner {

    /** The "Due soon" list shows everything up to this many days ahead. */
    const val DUE_SOON_DAYS = 7

    private fun pendingOf(rule: RecurringRuleFacts, until: LocalDate): List<Occurrence> =
        if (rule.active) RecurrenceCalculator.pending(rule.schedule, rule.occurrencesDone, until) else emptyList()

    /**
     * Every occurrence that is not confirmed or skipped with a date on or before today + [windowDays]:
     * overdue ones (date before today) and the ones due soon, oldest first.
     */
    fun dueItems(rules: List<RecurringRuleFacts>, today: LocalDate, windowDays: Int = DUE_SOON_DAYS): List<DueOccurrence> {
        val until = today.plusDays(windowDays.toLong())
        val items = ArrayList<DueOccurrence>()
        for (rule in rules) {
            pendingOf(rule, until).forEachIndexed { position, occurrence ->
                items.add(
                    DueOccurrence(
                        ruleId = rule.id,
                        kind = rule.kind,
                        amountCents = rule.amountCents,
                        index = occurrence.index,
                        date = occurrence.date,
                        isOverdue = occurrence.date < today,
                        isNext = position == 0,
                    ),
                )
            }
        }
        return items.sortedWith(compareBy<DueOccurrence> { it.date }.thenBy { it.ruleId }.thenBy { it.index })
    }

    /**
     * For safe to spend: the pending occurrences dated from [today] to [periodEnd] (inclusive).
     * Outgoing BILL and SUBSCRIPTION amounts add up to billsCents, INCOME amounts to incomeCents.
     * DEBT_PAYMENT rules are ignored here on purpose: debts are already counted as debtPaymentsDue.
     * Overdue occurrences (before today) are not included; they show up in the Due list instead.
     */
    fun stillDue(rules: List<RecurringRuleFacts>, today: LocalDate, periodEnd: LocalDate): StillDue {
        var bills = 0L
        var income = 0L
        for (rule in rules) {
            if (!rule.kind.isBill && rule.kind != RecurringKind.INCOME) continue
            // Overdue bills not yet confirmed still have to be paid, so they count; overdue income
            // that was never confirmed is not counted, to stay on the safe side.
            val pending = pendingOf(rule, periodEnd)
            val count = if (rule.kind == RecurringKind.INCOME) pending.count { it.date >= today } else pending.size
            val total = rule.amountCents * count
            if (rule.kind == RecurringKind.INCOME) income += total else bills += total
        }
        return StillDue(bills, income)
    }

    /** What the daily reminder says on [today]. Overdue counts every pending occurrence of every active rule. */
    fun reminders(rules: List<RecurringRuleFacts>, today: LocalDate): ReminderSummary {
        val tomorrowDate = today.plusDays(1)
        val reminding = rules.filter { it.remindDayBefore }.mapTo(HashSet()) { it.id }
        val items = dueItems(rules, today, windowDays = 1)
        val overdue = items.count { it.isOverdue }
        val tomorrow = items.filter { it.date == tomorrowDate && it.ruleId in reminding }
        return ReminderSummary(tomorrow, overdue)
    }

    /** Outgoing rules (bills, subscriptions, debt payments) per month on average. Inactive rules are left out. */
    fun monthlyOutgoingCents(rules: List<RecurringRuleFacts>): Long = rules
        .filter { it.active && it.kind.isOutgoing }
        .sumOf { RecurrenceCalculator.monthlyCents(it.amountCents, it.schedule.every, it.schedule.unit) }

    /** Outgoing rules per year on average. Inactive rules are left out. */
    fun yearlyOutgoingCents(rules: List<RecurringRuleFacts>): Long = rules
        .filter { it.active && it.kind.isOutgoing }
        .sumOf { RecurrenceCalculator.yearlyCents(it.amountCents, it.schedule.every, it.schedule.unit) }
}
