package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class BalanceCheckTest {
    @Test
    fun matchingBalanceNeedsNoAdjustment() {
        assertEquals(0L, BalanceCheck.adjustmentCents(realCents = 12_345, currentCents = 12_345))
    }

    @Test
    fun realHigherThanPikGivesPositiveAdjustment() {
        assertEquals(1_500L, BalanceCheck.adjustmentCents(realCents = 11_500, currentCents = 10_000))
    }

    @Test
    fun realLowerThanPikGivesNegativeAdjustment() {
        assertEquals(-2_550L, BalanceCheck.adjustmentCents(realCents = 7_450, currentCents = 10_000))
    }

    @Test
    fun worksWithNegativeBalances() {
        assertEquals(-500L, BalanceCheck.adjustmentCents(realCents = -1_500, currentCents = -1_000))
        assertEquals(2_000L, BalanceCheck.adjustmentCents(realCents = 1_000, currentCents = -1_000))
    }

    @Test
    fun adjustmentMakesBalanceMatchThroughCalculator() {
        val txs = listOf(
            TxFacts(TransactionType.INCOME, 50_000, 1, null),
            TxFacts(TransactionType.EXPENSE, 12_345, 1, null),
        )
        val current = BalanceCalculator.balance(1, 10_000, txs)
        val real = Money.parse("480,00")!!
        val adj = BalanceCheck.adjustmentCents(real, current)
        val after = BalanceCalculator.balance(1, 10_000, txs + TxFacts(TransactionType.ADJUSTMENT, adj, 1, null))
        assertEquals(real, after)
    }
}
