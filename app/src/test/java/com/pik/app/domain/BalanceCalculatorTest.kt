package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCalculatorTest {
    private val a = 1L
    private val b = 2L

    private fun tx(type: TransactionType, cents: Long, from: Long = a, to: Long? = null) =
        TxFacts(type, cents, from, to)

    @Test fun startingBalanceOnly() =
        assertEquals(5000L, BalanceCalculator.balance(a, 5000, emptyList()))

    @Test fun expenseReduces() =
        assertEquals(4000L, BalanceCalculator.balance(a, 5000, listOf(tx(TransactionType.EXPENSE, 1000))))

    @Test fun incomeIncreases() =
        assertEquals(6500L, BalanceCalculator.balance(a, 5000, listOf(tx(TransactionType.INCOME, 1500))))

    @Test fun transferOutAndIn() {
        val t = tx(TransactionType.TRANSFER, 2000, from = a, to = b)
        assertEquals(3000L, BalanceCalculator.balance(a, 5000, listOf(t)))
        assertEquals(2500L, BalanceCalculator.balance(b, 500, listOf(t)))
    }

    @Test fun transferDoesNotAffectOtherAccounts() {
        val t = tx(TransactionType.TRANSFER, 2000, from = a, to = b)
        assertEquals(7000L, BalanceCalculator.balance(3L, 7000, listOf(t)))
    }

    @Test fun adjustmentIsSigned() {
        assertEquals(5300L, BalanceCalculator.balance(a, 5000, listOf(tx(TransactionType.ADJUSTMENT, 300))))
        assertEquals(4700L, BalanceCalculator.balance(a, 5000, listOf(tx(TransactionType.ADJUSTMENT, -300))))
    }

    @Test fun otherAccountTransactionsIgnored() {
        assertEquals(5000L, BalanceCalculator.balance(a, 5000, listOf(tx(TransactionType.EXPENSE, 999, from = b))))
    }

    @Test fun mixed() {
        val list = listOf(
            tx(TransactionType.INCOME, 200000),
            tx(TransactionType.EXPENSE, 4550),
            tx(TransactionType.TRANSFER, 10000, from = a, to = b),
            tx(TransactionType.TRANSFER, 2500, from = b, to = a),
            tx(TransactionType.ADJUSTMENT, -50),
        )
        // 1000 + 200000 - 4550 - 10000 + 2500 - 50 = 188900
        assertEquals(188900L, BalanceCalculator.balance(a, 1000, list))
    }

    @Test fun monthTotalsExcludeTransfersAndAdjustments() {
        val totals = BalanceCalculator.monthTotals(
            listOf(
                tx(TransactionType.INCOME, 100000),
                tx(TransactionType.EXPENSE, 2500),
                tx(TransactionType.EXPENSE, 500),
                tx(TransactionType.TRANSFER, 9999, from = a, to = b),
                tx(TransactionType.ADJUSTMENT, 777),
            ),
        )
        assertEquals(100000L, totals.incomeCents)
        assertEquals(3000L, totals.expenseCents)
    }
}
