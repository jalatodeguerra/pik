package com.pik.app.domain

/** Minimal facts about a transaction needed for balance maths. */
data class TxFacts(
    val type: TransactionType,
    val amountCents: Long,
    val accountId: Long,
    val toAccountId: Long?,
    val categoryId: Long? = null,
    /** Set on an EXPENSE that pays a debt. */
    val debtId: Long? = null,
)

data class MonthTotals(val incomeCents: Long, val expenseCents: Long)

/**
 * Balance is calculated, never stored:
 * starting + income in - expense out - transfers out + transfers in + adjustments.
 * The SQL query in AccountDao mirrors this logic.
 */
object BalanceCalculator {

    /** Effect of one transaction on one account's balance. */
    fun delta(accountId: Long, tx: TxFacts): Long = when (tx.type) {
        TransactionType.INCOME -> if (tx.accountId == accountId) tx.amountCents else 0L
        TransactionType.EXPENSE -> if (tx.accountId == accountId) -tx.amountCents else 0L
        TransactionType.TRANSFER -> when (accountId) {
            tx.accountId -> -tx.amountCents
            tx.toAccountId -> tx.amountCents
            else -> 0L
        }
        TransactionType.ADJUSTMENT -> if (tx.accountId == accountId) tx.amountCents else 0L
    }

    fun balance(accountId: Long, startingBalanceCents: Long, transactions: List<TxFacts>): Long =
        startingBalanceCents + transactions.sumOf { delta(accountId, it) }

    /** Money in / out for a period. Transfers and adjustments are excluded. */
    fun monthTotals(transactions: List<TxFacts>): MonthTotals {
        var income = 0L
        var expense = 0L
        for (tx in transactions) {
            when (tx.type) {
                TransactionType.INCOME -> income += tx.amountCents
                TransactionType.EXPENSE -> expense += tx.amountCents
                else -> Unit
            }
        }
        return MonthTotals(income, expense)
    }
}
