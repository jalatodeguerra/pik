package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BudgetCalculatorTest {
    private fun b(cat: Long?, limit: Long, period: String) = BudgetFact(cat, limit, period)

    @Test fun latestStartPeriodNotAfterKeyWins() {
        val rows = listOf(b(1, 10_000, "2026-01"), b(1, 20_000, "2026-06"), b(1, 30_000, "2026-11"))
        assertEquals(10_000L, BudgetCalculator.effectiveLimits(rows, "2026-05")[1])
        assertEquals(20_000L, BudgetCalculator.effectiveLimits(rows, "2026-06")[1])
        assertEquals(20_000L, BudgetCalculator.effectiveLimits(rows, "2026-10")[1])
        assertEquals(30_000L, BudgetCalculator.effectiveLimits(rows, "2027-02")[1])
    }

    @Test fun noBudgetBeforeFirstRow() {
        val rows = listOf(b(1, 10_000, "2026-06"))
        assertTrue(BudgetCalculator.effectiveLimits(rows, "2026-05").isEmpty())
    }

    @Test fun zeroMeansNoBudgetFromThatMonthOn() {
        val rows = listOf(b(1, 10_000, "2026-01"), b(1, 0, "2026-09"))
        assertEquals(10_000L, BudgetCalculator.effectiveLimits(rows, "2026-08")[1])
        assertFalse(BudgetCalculator.effectiveLimits(rows, "2026-09").containsKey(1L))
        assertFalse(BudgetCalculator.effectiveLimits(rows, "2027-01").containsKey(1L))
    }

    @Test fun overallBudgetIsSeparateFromCategories() {
        val rows = listOf(b(null, 100_000, "2026-01"), b(1, 10_000, "2026-01"))
        val limits = BudgetCalculator.effectiveLimits(rows, "2026-11")
        assertEquals(100_000L, limits[null])
        assertEquals(10_000L, limits[1])
    }

    @Test fun yearBoundaryOrdering() {
        val rows = listOf(b(1, 1, "2025-12"), b(1, 2, "2026-01"))
        assertEquals(1L, BudgetCalculator.effectiveLimits(rows, "2025-12")[1])
        assertEquals(2L, BudgetCalculator.effectiveLimits(rows, "2026-01")[1])
    }

    @Test fun progressStatuses() {
        val ok = BudgetCalculator.progress(7_900, 10_000)
        assertEquals(BudgetStatus.OK, ok.status)
        assertEquals(2_100L, ok.leftCents)
        assertEquals(BudgetStatus.WARNING, BudgetCalculator.progress(8_000, 10_000).status)
        assertEquals(BudgetStatus.WARNING, BudgetCalculator.progress(10_000, 10_000).status)
        val over = BudgetCalculator.progress(12_500, 10_000)
        assertEquals(BudgetStatus.OVER, over.status)
        assertEquals(-2_500L, over.leftCents)
        assertEquals(1f, over.fraction, 0f)
        assertEquals(0f, BudgetCalculator.progress(0, 10_000).fraction, 0f)
        assertEquals(0.5f, BudgetCalculator.progress(5_000, 10_000).fraction, 0.0001f)
    }

    @Test fun spendByCategoryOnlyCountsExpenses() {
        val txs = listOf(
            TxFacts(TransactionType.EXPENSE, 1_000, 1, null, 5),
            TxFacts(TransactionType.EXPENSE, 500, 1, null, 5),
            TxFacts(TransactionType.EXPENSE, 200, 1, null, null),
            TxFacts(TransactionType.INCOME, 9_999, 1, null, 7),
            TxFacts(TransactionType.TRANSFER, 9_999, 1, 2, null),
            TxFacts(TransactionType.ADJUSTMENT, -9_999, 1, null, null),
        )
        val spend = BudgetCalculator.spendByCategory(txs)
        assertEquals(setOf(CategorySpend(5, 1_500), CategorySpend(null, 200)), spend.toSet())
    }

    @Test fun overviewSplitsBudgetedAndNot() {
        val limits = mapOf<Long?, Long>(null to 100_000, 1L to 10_000, 2L to 50_000)
        val spend = listOf(CategorySpend(1, 12_000), CategorySpend(3, 4_000), CategorySpend(null, 1_000))
        val o = BudgetCalculator.overview(limits, spend)
        assertEquals(17_000L, o.overall!!.spentCents)
        assertEquals(listOf(1L, 2L), o.lines.map { it.categoryId })
        assertEquals(BudgetStatus.OVER, o.lines[0].progress.status)
        assertEquals(0L, o.lines[1].progress.spentCents)
        assertEquals(listOf(CategorySpend(3, 4_000), CategorySpend(null, 1_000)), o.notBudgeted)
        assertTrue(o.hasAnyBudget)
    }

    @Test fun overviewWithoutBudgets() {
        val o = BudgetCalculator.overview(emptyMap(), listOf(CategorySpend(1, 100)))
        assertNull(o.overall)
        assertTrue(o.lines.isEmpty())
        assertFalse(o.hasAnyBudget)
        assertEquals(1, o.notBudgeted.size)
    }

    @Test fun zeroSpendIsNotListedAsNotBudgeted() {
        val o = BudgetCalculator.overview(emptyMap(), listOf(CategorySpend(1, 0)))
        assertTrue(o.notBudgeted.isEmpty())
    }

    @Test fun debtPaymentsHaveTheirOwnBucketButCountInTheTotal() {
        val txs = listOf(
            TxFacts(TransactionType.EXPENSE, 1_000, 1, null, 5),
            TxFacts(TransactionType.EXPENSE, 200, 1, null, null),
            TxFacts(TransactionType.EXPENSE, 5_000, 1, null, null, debtId = 3),
            TxFacts(TransactionType.EXPENSE, 2_500, 1, null, null, debtId = 4),
            TxFacts(TransactionType.INCOME, 9_999, 1, null, null, debtId = 3),
        )
        val spend = BudgetCalculator.spendByCategory(txs)
        assertEquals(
            setOf(CategorySpend(5, 1_000), CategorySpend(null, 200), CategorySpend(null, 7_500, isDebtPayment = true)),
            spend.toSet(),
        )
        val overview = BudgetCalculator.overview(mapOf(null to 10_000L, 5L to 2_000L), spend)
        assertEquals(8_700L, overview.overall?.spentCents)
        assertEquals(
            listOf(CategorySpend(null, 7_500, isDebtPayment = true), CategorySpend(null, 200)),
            overview.notBudgeted,
        )
    }
}
