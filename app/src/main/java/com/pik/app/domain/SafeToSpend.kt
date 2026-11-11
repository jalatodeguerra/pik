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
) {
    val isOverSpent: Boolean get() = safeCents < 0
}

/**
 * The single definition of "safe to spend", used by Home and Plan:
 *
 *   plannedIncome = max(expected monthly income, income actually received this period)
 *   safeToSpend   = plannedIncome - spentSoFar - billsStillDue - debtPaymentsDue
 *   perDay        = safeToSpend / daysLeft   (0 when safeToSpend <= 0)
 *
 * spentSoFar is EXPENSE transactions of the period (transfers and adjustments excluded).
 * Bills and debt payments are 0 until the later milestones provide them.
 */
object SafeToSpendCalculator {
    fun compute(
        expectedIncomeCents: Long?,
        incomeReceivedCents: Long,
        spentCents: Long,
        daysLeft: Int,
        billsStillDueCents: Long = 0L,
        debtPaymentsDueCents: Long = 0L,
    ): SafeToSpend {
        val planned = maxOf(expectedIncomeCents ?: 0L, incomeReceivedCents)
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
        )
    }
}
