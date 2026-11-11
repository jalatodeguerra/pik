package com.pik.app.domain

import java.time.LocalDate

/**
 * Everything needed to know how much one debt still is. The balance is calculated, never stored:
 *
 *   remaining = startingBalance + sum(entries) - sum(EXPENSE transactions with this debtId)
 *
 * Entries are signed (interest and fees raise the debt, a negative adjustment lowers it).
 */
data class DebtFigures(
    val startingBalanceCents: Long,
    /** What the loan was originally, for "% paid off" of an old loan. null = same as the starting balance. */
    val originalAmountCents: Long?,
    val entriesSumCents: Long,
    val paymentsSumCents: Long,
)

data class DebtProgress(
    /** Paid before Pik (original - starting balance) plus all payments logged in Pik. */
    val paidCents: Long,
    /** Never negative: an over-paid debt shows 0. */
    val remainingCents: Long,
    /** paid / (paid + remaining), 0..1. 0 when there is nothing at all, 1 when paid off. */
    val fraction: Float,
    val paidOff: Boolean,
)

object DebtCalculator {

    /** May be negative when the debt was over-paid. */
    fun rawRemaining(f: DebtFigures): Long =
        f.startingBalanceCents + f.entriesSumCents - f.paymentsSumCents

    /** What the screens show: never below 0. */
    fun remaining(f: DebtFigures): Long = rawRemaining(f).coerceAtLeast(0L)

    /** Amount paid off before the debt was added to Pik: original - starting, when original is larger. */
    fun priorPaid(f: DebtFigures): Long =
        ((f.originalAmountCents ?: f.startingBalanceCents) - f.startingBalanceCents).coerceAtLeast(0L)

    fun paid(f: DebtFigures): Long = priorPaid(f) + f.paymentsSumCents

    fun progress(f: DebtFigures): DebtProgress = progressOf(paid(f), remaining(f))

    /** One ring for many debts: sums of paid and remaining. */
    fun overall(items: List<DebtProgress>): DebtProgress =
        progressOf(items.sumOf { it.paidCents }, items.sumOf { it.remainingCents })

    private fun progressOf(paid: Long, remaining: Long): DebtProgress {
        val total = paid + remaining
        val fraction = when {
            total <= 0L -> 0f
            remaining == 0L -> 1f
            else -> (paid.toDouble() / total).coerceIn(0.0, 1.0).toFloat()
        }
        return DebtProgress(paid, remaining, fraction, paidOff = remaining == 0L && total > 0L)
    }
}

/** One line of a debt's history: a payment (a transaction) or an entry (interest, fee, adjustment). */
sealed interface DebtHistoryItem {
    val date: LocalDate
    val createdAt: Long

    data class Payment(
        val transactionId: Long,
        override val date: LocalDate,
        override val createdAt: Long,
        val amountCents: Long,
        val accountName: String,
        val note: String,
    ) : DebtHistoryItem

    data class Entry(
        val entryId: Long,
        override val date: LocalDate,
        override val createdAt: Long,
        val type: DebtEntryType,
        /** Signed: positive raises the debt. */
        val amountCents: Long,
        val note: String,
    ) : DebtHistoryItem
}

object DebtHistory {
    /** Newest first: by date, then by creation time, then payments before entries on a full tie. */
    fun merge(payments: List<DebtHistoryItem.Payment>, entries: List<DebtHistoryItem.Entry>): List<DebtHistoryItem> =
        (payments + entries).sortedWith(
            compareByDescending<DebtHistoryItem> { it.date }
                .thenByDescending { it.createdAt }
                .thenBy { if (it is DebtHistoryItem.Payment) 0 else 1 },
        )
}

/**
 * "Check balance" for a debt: what the bank says you owe versus what Pik calculates.
 * The ADJUSTMENT entry is the signed difference real - [DebtCalculator.rawRemaining]; 0 means
 * "already matches". The raw (possibly negative) balance is used, not the clamped one, so that
 * after the adjustment the calculated balance equals what the bank says even for an over-paid debt.
 */
object DebtBalanceCheck {
    fun adjustmentCents(realCents: Long, rawRemainingCents: Long): Long = realCents - rawRemainingCents
}
