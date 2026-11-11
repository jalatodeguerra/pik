package com.pik.app.domain

import java.time.YearMonth

/** Where a loan with instalments (rate) stands. */
data class InstallmentStatus(
    val total: Int,
    val paid: Int,
    val left: Int,
    /** Last month with a rata, or null when there is nothing left to pay. */
    val endMonth: YearMonth?,
) {
    val fraction: Float get() = if (total <= 0) 0f else (paid.coerceAtMost(total).toFloat() / total)
}

/**
 * Instalment counting (pure). Paid so far = instalments paid before the loan was added to Pik
 * + the payments logged in Pik that count as a rata. A payment counts as a rata when it is at
 * least 90 % of the monthly instalment ([isRata]); smaller payments still lower the balance but are
 * not counted. Nothing is stored per payment: the count is recomputed from the transactions.
 */
object Installments {
    /** A payment counts as one rata when it is >= 90 % of [rataCents] (and the rata is not 0). */
    fun isRata(paymentCents: Long, rataCents: Long): Boolean =
        rataCents > 0L && paymentCents * 10L >= rataCents * 9L

    /**
     * [paidBefore] = instalments paid before Pik (null = 0), [countedInPik] = qualifying payments in Pik.
     * [rataPaidThisMonth] = a qualifying payment is dated in the calendar month of [now].
     * End month = now + left - 1 when this month's rata is already paid, otherwise now + left
     * (the current month's rata is still to come). null when [total] is null or nothing is left.
     */
    fun status(
        total: Int?,
        paidBefore: Int?,
        countedInPik: Int,
        rataPaidThisMonth: Boolean,
        now: YearMonth,
    ): InstallmentStatus? {
        if (total == null || total <= 0) return null
        val paid = ((paidBefore ?: 0) + countedInPik.coerceAtLeast(0)).coerceAtMost(total)
        val left = (total - paid).coerceAtLeast(0)
        val end = when {
            left <= 0 -> null
            rataPaidThisMonth -> now.plusMonths((left - 1).toLong())
            else -> now.plusMonths(left.toLong())
        }
        return InstallmentStatus(total, paid, left, end)
    }
}
