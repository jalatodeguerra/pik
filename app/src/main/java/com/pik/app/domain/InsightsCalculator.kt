package com.pik.app.domain

import java.time.LocalDate

/** The few facts of a transaction the insights need. */
data class InsightTx(
    val date: LocalDate,
    val type: TransactionType,
    val amountCents: Long,
    val categoryId: Long?,
    /** Set on an EXPENSE that pays a debt. */
    val debtId: Long?,
) {
    val isSpending: Boolean get() = type == TransactionType.EXPENSE
    val isDebtPayment: Boolean get() = type == TransactionType.EXPENSE && debtId != null
}

/** One pair of bars: what came in and what went out in a period. */
data class PeriodBar(
    val period: Period,
    val incomeCents: Long,
    /** All spending, debt payments included. */
    val spendingCents: Long,
    /** The part of [spendingCents] that paid debts. */
    val debtCents: Long,
) {
    val otherSpendingCents: Long get() = spendingCents - debtCents
}

enum class SliceKind { CATEGORY, UNCATEGORISED, OTHER, DEBT }

/** One slice of the "Where it went" donut. [percent] are whole numbers that add up to 100. */
data class SpendingSlice(
    val kind: SliceKind,
    /** Set for [SliceKind.CATEGORY]. */
    val categoryId: Long?,
    val cents: Long,
    val percent: Int,
)

data class BiggestExpense(val amountCents: Long, val categoryId: Long?, val date: LocalDate)

data class InsightsSummary(
    val selected: Period,
    /** The last periods up to the current one, oldest first. */
    val bars: List<PeriodBar>,
    val incomeCents: Long,
    val spentCents: Long,
    /** income - spending of the selected period (can be negative). */
    val keptCents: Long,
    val debtPaidCents: Long,
    /** Average spending of up to 3 previous periods that have data; null when there are none. */
    val previousAverageCents: Long?,
    /** How many previous periods the average is made of (0..3). */
    val previousPeriodCount: Int,
    /** Spending versus that average in whole percent (+12 / -8); null without an average above zero. */
    val changePercent: Int?,
    /** Biggest single expense that is not a debt payment. */
    val biggest: BiggestExpense?,
    val slices: List<SpendingSlice>,
    /** The first period that has any income or expense, or null without data. */
    val firstPeriod: Period?,
) {
    val hasData: Boolean get() = firstPeriod != null
}

/**
 * Pure maths for the Insights screen. Periods are payday months ([Period]). Only INCOME and
 * EXPENSE count: transfers and balance adjustments are ignored. A debt payment is an expense that
 * is also shown on its own (blue segment, "Debt payments" slice).
 */
object InsightsCalculator {
    const val BAR_COUNT = 6
    const val TOP_CATEGORIES = 6
    const val AVERAGE_PERIODS = 3

    fun compute(
        transactions: List<InsightTx>,
        startDay: Int,
        today: LocalDate,
        selected: Period = Period.containing(today, startDay),
        barCount: Int = BAR_COUNT,
        topCategories: Int = TOP_CATEGORIES,
    ): InsightsSummary {
        val relevant = transactions.filter { it.type == TransactionType.INCOME || it.type == TransactionType.EXPENSE }
        val current = Period.containing(today, startDay)
        val firstPeriod = relevant.minOfOrNull { it.date }?.let { Period.containing(it, startDay) }

        val bars = periodsEndingAt(current, barCount).map { barOf(it, relevant) }
        val selectedBar = barOf(selected, relevant)

        // Average of up to 3 previous periods, skipping periods before the first data.
        val previous = (1..AVERAGE_PERIODS)
            .map { stepBack(selected, it) }
            .filter { firstPeriod != null && it.start >= firstPeriod.start }
        val average = if (previous.isEmpty()) {
            null
        } else {
            roundedDivide(previous.sumOf { barOf(it, relevant).spendingCents }, previous.size.toLong())
        }
        val change = if (average != null && average > 0L) {
            roundedPercent(selectedBar.spendingCents - average, average)
        } else {
            null
        }

        val inSelected = relevant.filter { it.date in selected }
        val biggest = inSelected
            .filter { it.isSpending && !it.isDebtPayment }
            .maxWithOrNull(compareBy<InsightTx> { it.amountCents }.thenBy { it.date.toEpochDay() })
            ?.let { BiggestExpense(it.amountCents, it.categoryId, it.date) }

        return InsightsSummary(
            selected = selected,
            bars = bars,
            incomeCents = selectedBar.incomeCents,
            spentCents = selectedBar.spendingCents,
            keptCents = selectedBar.incomeCents - selectedBar.spendingCents,
            debtPaidCents = selectedBar.debtCents,
            previousAverageCents = average,
            previousPeriodCount = previous.size,
            changePercent = change,
            biggest = biggest,
            slices = slices(inSelected, topCategories),
            firstPeriod = firstPeriod,
        )
    }

    /** The [count] periods ending at [last], oldest first. */
    fun periodsEndingAt(last: Period, count: Int): List<Period> =
        (count - 1 downTo 0).map { stepBack(last, it) }

    private fun stepBack(period: Period, steps: Int): Period =
        Period(period.start.minusMonths(steps.toLong()), period.startDay)

    private fun barOf(period: Period, txs: List<InsightTx>): PeriodBar {
        var income = 0L
        var spending = 0L
        var debt = 0L
        for (tx in txs) {
            if (tx.date !in period) continue
            when (tx.type) {
                TransactionType.INCOME -> income += tx.amountCents
                TransactionType.EXPENSE -> {
                    spending += tx.amountCents
                    if (tx.debtId != null) debt += tx.amountCents
                }
                else -> Unit
            }
        }
        return PeriodBar(period, income, spending, debt)
    }

    /**
     * Spending of one period split in slices: the [topCategories] biggest categories (uncategorised
     * spending counts as one category), "Other" for the rest and "Debt payments" on its own.
     * Biggest first; Other and Debt payments last. Percentages add up to exactly 100.
     */
    fun slices(periodTxs: List<InsightTx>, topCategories: Int = TOP_CATEGORIES): List<SpendingSlice> {
        val expenses = periodTxs.filter { it.isSpending }
        val debtCents = expenses.filter { it.isDebtPayment }.sumOf { it.amountCents }
        val byCategory = expenses
            .filter { !it.isDebtPayment }
            .groupBy { it.categoryId }
            .map { (id, list) -> id to list.sumOf { it.amountCents } }
            .filter { it.second > 0L }
            // biggest first; equal amounts keep a stable order by id (uncategorised last)
            .sortedWith(compareByDescending<Pair<Long?, Long>> { it.second }.thenBy { it.first ?: Long.MAX_VALUE })

        val top = byCategory.take(topCategories)
        val otherCents = byCategory.drop(topCategories).sumOf { it.second }

        val raw = mutableListOf<Triple<SliceKind, Long?, Long>>()
        top.forEach { (id, cents) ->
            raw += Triple(if (id == null) SliceKind.UNCATEGORISED else SliceKind.CATEGORY, id, cents)
        }
        if (otherCents > 0L) raw += Triple(SliceKind.OTHER, null, otherCents)
        if (debtCents > 0L) raw += Triple(SliceKind.DEBT, null, debtCents)

        val percents = percentages(raw.map { it.third })
        return raw.mapIndexed { i, (kind, id, cents) -> SpendingSlice(kind, id, cents, percents[i]) }
    }

    /**
     * Whole-number percentages of [values] that always add up to 100 (largest remainder method;
     * ties go to the earlier value). All zero or empty gives zeros.
     */
    fun percentages(values: List<Long>): List<Int> {
        val total = values.sum()
        if (values.isEmpty() || total <= 0L) return values.map { 0 }
        val floors = values.map { (it * 100) / total }
        val remainders = values.map { (it * 100) % total }
        var missing = (100 - floors.sum()).toInt()
        val result = floors.map { it.toInt() }.toMutableList()
        val order = values.indices.sortedWith(compareByDescending<Int> { remainders[it] }.thenBy { it })
        for (i in order) {
            if (missing <= 0) break
            result[i] += 1
            missing--
        }
        return result
    }

    /**
     * Cumulative spending per day of [period], from its first day up to [today] (or its last day
     * for a past period). Index 0 is the first day. Used for the sparkline on Home.
     */
    fun cumulativeSpending(transactions: List<InsightTx>, period: Period, today: LocalDate): List<Long> {
        if (today < period.start) return emptyList()
        val last = if (today > period.endInclusive) period.endInclusive else today
        val days = (last.toEpochDay() - period.start.toEpochDay() + 1).toInt()
        val perDay = LongArray(days)
        for (tx in transactions) {
            if (!tx.isSpending || tx.date < period.start || tx.date > last) continue
            perDay[(tx.date.toEpochDay() - period.start.toEpochDay()).toInt()] += tx.amountCents
        }
        val result = ArrayList<Long>(days)
        var running = 0L
        for (d in perDay) {
            running += d
            result += running
        }
        return result
    }

    /** a / b rounded half away from zero; b must be positive. */
    private fun roundedDivide(a: Long, b: Long): Long = (a + b / 2) / b

    /** part * 100 / whole rounded half away from zero; whole must be positive. */
    private fun roundedPercent(part: Long, whole: Long): Int {
        val scaled = part * 100
        val half = whole / 2
        return if (scaled >= 0) ((scaled + half) / whole).toInt() else (-((-scaled + half) / whole)).toInt()
    }
}
