package com.pik.app.domain

/**
 * Result of [SafeToSpendCalculator.compute]. [safeCents] may be negative (over-spent).
 * [perDayCents] is never negative: 0 when nothing is safe to spend.
 */
data class SafeToSpend(
    val plannedIncomeCents: Long,
    val spentCents: Long,
    val billsStillDueCents: Long,
    val debtPaymentsDueCents: Long,
    val safeCents: Long,
    val daysLeft: Int,
    val perDayCents: Long,
    /** Recurring income still expected later in the period (already inside [plannedIncomeCents]). */
    val incomeStillDueCents: Long = 0L,
) {
    val isOverSpent: Boolean get() = safeCents < 0
}

/**
 * The single definition of "safe to spend", used by Home and Plan:
 *
 *   plannedIncome = max(expected monthly income, income received this period + incomeStillDue)
 *   safeToSpend   = plannedIncome - spentSoFar - billsStillDue - debtPaymentsDue
 *   perDay        = safeToSpend / daysLeft   (0 when safeToSpend <= 0)
 *
 * spentSoFar is EXPENSE transactions of the period (transfers and adjustments excluded). Debt
 * payments are EXPENSE transactions too, so what is already paid is inside spentSoFar; the part
 * still to pay this period is [DebtPaymentsDue] (see there).
 *
 * billsStillDue is the sum of the BILL and SUBSCRIPTION occurrences dated from today to the end of
 * the period that are not confirmed or skipped yet; incomeStillDue is the same for INCOME rules
 * (see [RecurringPlanner.stillDue]). DEBT_PAYMENT rules are never part of billsStillDue, because
 * debts are already counted through debtPaymentsDue. A confirmed bill is an expense and moves from
 * "still due" to spentSoFar, so it is never counted twice.
 */
object SafeToSpendCalculator {
    fun compute(
        expectedIncomeCents: Long?,
        incomeReceivedCents: Long,
        spentCents: Long,
        daysLeft: Int,
        billsStillDueCents: Long = 0L,
        debtPaymentsDueCents: Long = 0L,
        incomeStillDueCents: Long = 0L,
    ): SafeToSpend {
        val planned = maxOf(expectedIncomeCents ?: 0L, incomeReceivedCents + incomeStillDueCents)
        val safe = planned - spentCents - billsStillDueCents - debtPaymentsDueCents
        val days = daysLeft.coerceAtLeast(1)
        val perDay = if (safe > 0) safe / days else 0L
        return SafeToSpend(
            plannedIncomeCents = planned,
            spentCents = spentCents,
            billsStillDueCents = billsStillDueCents,
            debtPaymentsDueCents = debtPaymentsDueCents,
            safeCents = safe,
            daysLeft = daysLeft,
            perDayCents = perDay,
            incomeStillDueCents = incomeStillDueCents,
        )
    }
}

/** One open debt as needed for the "still to pay this period" figure. */
data class DebtDueFacts(
    val debtId: Long,
    /** What is still owed now (already lowered by payments, never negative). */
    val remainingCents: Long,
    val minPaymentCents: Long,
    /** Debt payments (EXPENSE transactions with this debtId) logged in the current period. */
    val paidThisPeriodCents: Long,
)

/**
 * debtPaymentsDue, the part of safe-to-spend that is already promised to debts:
 *
 *   for every non-archived debt with remaining > 0:
 *       minDue = max(0, minPayment - paidThisPeriod)         (never more than what is still owed)
 *   extraDue = max(0, extraPerMonth - sum over those debts of max(0, paidThisPeriod - minPayment))
 *              (only when extraPerMonth > 0; never more than what is still owed after the minimums)
 *   debtPaymentsDue = sum(minDue) + extraDue
 *
 * So paying a debt moves money from "still due" to "already spent": safe to spend does not drop
 * twice for the same payment, and paying more than the minimum eats into the extra first.
 */
object DebtPaymentsDue {
    /** An open debt as the screens know it. */
    data class Input(val debtId: Long, val remainingCents: Long, val minPaymentCents: Long)

    /**
     * Convenience for Home and Plan: the figure for the given non-archived [debts], using the EXPENSE
     * transactions that have a debtId among the [periodTransactions] (the current period's).
     */
    fun compute(debts: List<Input>, periodTransactions: List<TxFacts>, extraPerMonthCents: Long): Long {
        val paid = HashMap<Long, Long>()
        for (tx in periodTransactions) {
            val debtId = tx.debtId ?: continue
            if (tx.type != TransactionType.EXPENSE) continue
            paid[debtId] = (paid[debtId] ?: 0L) + tx.amountCents
        }
        val facts = debts.map { DebtDueFacts(it.debtId, it.remainingCents, it.minPaymentCents, paid[it.debtId] ?: 0L) }
        return compute(facts, extraPerMonthCents)
    }

    fun compute(debts: List<DebtDueFacts>, extraPerMonthCents: Long): Long {
        val open = debts.filter { it.remainingCents > 0L }
        var minDue = 0L
        var beyondMinimums = 0L
        for (d in open) {
            val min = d.minPaymentCents.coerceAtLeast(0L)
            minDue += minOf(maxOf(0L, min - d.paidThisPeriodCents), d.remainingCents)
            beyondMinimums += maxOf(0L, d.paidThisPeriodCents - min)
        }
        val extraDue = if (extraPerMonthCents > 0L) {
            val stillOwedAfterMinimums = (open.sumOf { it.remainingCents } - minDue).coerceAtLeast(0L)
            minOf(maxOf(0L, extraPerMonthCents - beyondMinimums), stillOwedAfterMinimums)
        } else {
            0L
        }
        return minDue + extraDue
    }
}
