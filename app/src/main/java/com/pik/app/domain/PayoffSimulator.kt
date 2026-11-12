package com.pik.app.domain

import java.time.YearMonth

/**
 * A debt as the payoff simulation sees it. [aprBasisPoints] is the TAN: the rate used for interest.
 * [taegBasisPoints] (optional) is only used to rank debts for HIGHEST_INTEREST.
 */
data class SimDebt(
    val id: Long,
    val balanceCents: Long,
    val aprBasisPoints: Int,
    val minPaymentCents: Long,
    val taegBasisPoints: Int? = null,
) {
    /** The rate that decides the payoff order: the TAEG when set, otherwise the TAN. */
    val rankBasisPoints: Int get() = taegBasisPoints ?: aprBasisPoints
}

data class PayoffResult(
    /** Month of the last payment. null when the debts are already paid ([isDebtFreeNow]) or [neverPaysOff]. */
    val debtFreeMonth: YearMonth?,
    /** Number of simulated months. 0 when there is nothing to pay. Not meaningful when [neverPaysOff]. */
    val months: Int,
    /** Interest added over the whole plan. Not meaningful when [neverPaysOff]. */
    val totalInterestCents: Long,
    /** Month in which each debt reaches 0. Debts that never do are missing. */
    val payoffMonthByDebt: Map<Long, YearMonth>,
    /** The total payment does not cover the interest growth (or it would take more than 50 years). */
    val neverPaysOff: Boolean,
    val isDebtFreeNow: Boolean,
)

data class OrderComparison(
    val highestInterest: PayoffResult,
    val smallestBalance: PayoffResult,
) {
    fun of(order: PayoffOrder): PayoffResult = when (order) {
        PayoffOrder.HIGHEST_INTEREST -> highestInterest
        PayoffOrder.SMALLEST_BALANCE -> smallestBalance
    }
}

/** "What if I paid [extraCents] more per month", compared with paying no extra at all. */
data class WhatIfResult(
    val extraCents: Long,
    val result: PayoffResult,
    val baseline: PayoffResult,
    /** Months saved against the baseline; null unless both plans pay off. */
    val monthsSooner: Int?,
    /** Interest saved against the baseline; null unless both plans pay off. */
    val interestSavedCents: Long?,
) {
    /** The baseline never pays off but this plan does. */
    val makesItPossible: Boolean get() = baseline.neverPaysOff && !result.neverPaysOff
}

/**
 * Month by month payoff simulation (pure, no Android).
 *
 * The first simulated month is [startMonth] (normally the current month). Every month:
 *  1. interest is added to every open debt: balance x TAN / 12, rounded half up to the cent;
 *  2. every minimum payment is paid, capped at the balance;
 *  3. the pool = extra + minimums of debts that are already cleared (snowball roll-over) + whatever
 *     part of a minimum was not needed because the balance was smaller. The pool goes to the
 *     target debt, then cascades to the next target in the same month until it is used up.
 *
 * The target order is fixed from the starting balances: HIGHEST_INTEREST = highest TAEG first (TAN when a debt has no TAEG),
 * SMALLEST_BALANCE = smallest balance first; ties are broken by id. The total paid per month is
 * therefore constant (sum of all minimums + extra) until the last debt is cleared.
 *
 * neverPaysOff: if a month ends with the total balance not lower than before, interest eats the
 * whole payment and, since the payment never changes, it never will; the simulation also stops
 * after [MAX_MONTHS] months (50 years) with the same flag.
 */
object PayoffSimulator {
    const val MAX_MONTHS = 600

    /** balance x apr / 12, rounded half up to the cent. */
    fun monthlyInterestCents(balanceCents: Long, aprBasisPoints: Int): Long {
        if (balanceCents <= 0L || aprBasisPoints <= 0) return 0L
        val denominator = 12L * 10_000L
        return (balanceCents * aprBasisPoints + denominator / 2) / denominator
    }

    /** Debt ids in the order the extra money is applied. Debts without a balance are left out. */
    fun targetOrder(debts: List<SimDebt>, order: PayoffOrder): List<Long> {
        val open = debts.filter { it.balanceCents > 0L }
        val sorted = when (order) {
            PayoffOrder.HIGHEST_INTEREST ->
                open.sortedWith(compareByDescending<SimDebt> { it.rankBasisPoints }.thenBy { it.id })
            PayoffOrder.SMALLEST_BALANCE ->
                open.sortedWith(compareBy<SimDebt> { it.balanceCents }.thenBy { it.id })
        }
        return sorted.map { it.id }
    }

    fun simulate(
        debts: List<SimDebt>,
        extraPerMonthCents: Long,
        order: PayoffOrder,
        startMonth: YearMonth,
    ): PayoffResult {
        val open = debts.filter { it.balanceCents > 0L }
        if (open.isEmpty()) {
            return PayoffResult(null, 0, 0L, emptyMap(), neverPaysOff = false, isDebtFreeNow = true)
        }
        val extra = extraPerMonthCents.coerceAtLeast(0L)
        val targets = targetOrder(open, order)
        val balance = HashMap<Long, Long>().also { map -> open.forEach { map[it.id] = it.balanceCents } }
        val byId = open.associateBy { it.id }
        val totalBudget = extra + open.sumOf { it.minPaymentCents.coerceAtLeast(0L) }
        val paidOffMonth = LinkedHashMap<Long, YearMonth>()
        var totalInterest = 0L
        var months = 0

        while (months < MAX_MONTHS) {
            val month = startMonth.plusMonths(months.toLong())
            months++
            val before = balance.values.sum()

            // 1. interest on every open debt
            for (id in balance.keys.toList()) {
                val b = balance.getValue(id)
                if (b <= 0L) continue
                val interest = monthlyInterestCents(b, byId.getValue(id).aprBasisPoints)
                balance[id] = b + interest
                totalInterest += interest
            }

            // 2. minimums, capped at the balance
            var pool = totalBudget
            for (debt in open) {
                val b = balance.getValue(debt.id)
                if (b <= 0L) continue
                val pay = minOf(debt.minPaymentCents.coerceAtLeast(0L), b)
                balance[debt.id] = b - pay
                pool -= pay
            }

            // 3. the pool goes to the targets in order, cascading
            for (id in targets) {
                if (pool <= 0L) break
                val b = balance.getValue(id)
                if (b <= 0L) continue
                val pay = minOf(pool, b)
                balance[id] = b - pay
                pool -= pay
            }

            for (id in targets) {
                if (balance.getValue(id) <= 0L && id !in paidOffMonth) paidOffMonth[id] = month
            }

            val after = balance.values.sum()
            if (after <= 0L) {
                return PayoffResult(month, months, totalInterest, paidOffMonth, neverPaysOff = false, isDebtFreeNow = false)
            }
            if (after >= before) break // interest eats the whole payment: it will never go down
        }
        return PayoffResult(null, months, totalInterest, paidOffMonth, neverPaysOff = true, isDebtFreeNow = false)
    }

    fun compareOrders(debts: List<SimDebt>, extraPerMonthCents: Long, startMonth: YearMonth): OrderComparison =
        OrderComparison(
            highestInterest = simulate(debts, extraPerMonthCents, PayoffOrder.HIGHEST_INTEREST, startMonth),
            smallestBalance = simulate(debts, extraPerMonthCents, PayoffOrder.SMALLEST_BALANCE, startMonth),
        )

    /** Simulates with [extraCents] and compares with no extra payment at all. */
    fun whatIf(
        debts: List<SimDebt>,
        extraCents: Long,
        order: PayoffOrder,
        startMonth: YearMonth,
    ): WhatIfResult {
        val result = simulate(debts, extraCents, order, startMonth)
        val baseline = simulate(debts, 0L, order, startMonth)
        val bothPay = !result.neverPaysOff && !baseline.neverPaysOff
        return WhatIfResult(
            extraCents = extraCents,
            result = result,
            baseline = baseline,
            monthsSooner = if (bothPay) baseline.months - result.months else null,
            interestSavedCents = if (bothPay) baseline.totalInterestCents - result.totalInterestCents else null,
        )
    }
}
