package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InsightsCalculatorTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun expense(date: LocalDate, cents: Long, category: Long? = 1L, debt: Long? = null) =
        InsightTx(date, TransactionType.EXPENSE, cents, if (debt == null) category else null, debt)

    private fun income(date: LocalDate, cents: Long) =
        InsightTx(date, TransactionType.INCOME, cents, 99L, null)

    private val today = d(2026, 11, 11)

    @Test
    fun barsAreSixPeriodsOldestFirstEndingAtTheCurrentOne() {
        val summary = InsightsCalculator.compute(listOf(expense(d(2026, 11, 3), 1_000)), 1, today)
        assertEquals(6, summary.bars.size)
        assertEquals(d(2026, 6, 1), summary.bars.first().period.start)
        assertEquals(d(2026, 11, 1), summary.bars.last().period.start)
    }

    @Test
    fun paydayPeriodsBucketAroundTheStartDay() {
        // start day 27: 26 Oct belongs to 27 Sep - 26 Oct, 27 Oct to 27 Oct - 26 Nov
        val txs = listOf(
            expense(d(2026, 10, 26), 1_000),
            expense(d(2026, 10, 27), 2_000),
            expense(d(2026, 11, 10), 500),
            income(d(2026, 11, 26), 300_000),
            income(d(2026, 11, 27), 100_000),
        )
        val summary = InsightsCalculator.compute(txs, 27, today)
        val last = summary.bars.last()
        assertEquals(d(2026, 10, 27), last.period.start)
        assertEquals(2_500L, last.spendingCents)
        assertEquals(300_000L, last.incomeCents)
        val before = summary.bars[summary.bars.size - 2]
        assertEquals(1_000L, before.spendingCents)
        assertEquals(0L, before.incomeCents)
    }

    @Test
    fun transfersAndAdjustmentsAreIgnored() {
        val txs = listOf(
            expense(d(2026, 11, 2), 1_000),
            InsightTx(d(2026, 11, 2), TransactionType.TRANSFER, 50_000, null, null),
            InsightTx(d(2026, 11, 2), TransactionType.ADJUSTMENT, -7_000, null, null),
            InsightTx(d(2026, 11, 2), TransactionType.ADJUSTMENT, 9_000, null, null),
        )
        val summary = InsightsCalculator.compute(txs, 1, today)
        assertEquals(1_000L, summary.spentCents)
        assertEquals(0L, summary.incomeCents)
        assertEquals(-1_000L, summary.keptCents)
        assertEquals(1_000L, summary.slices.sumOf { it.cents })
    }

    @Test
    fun onlyTransfersMeansNoData() {
        val txs = listOf(InsightTx(d(2026, 11, 2), TransactionType.TRANSFER, 50_000, null, null))
        val summary = InsightsCalculator.compute(txs, 1, today)
        assertFalse(summary.hasData)
        assertNull(summary.firstPeriod)
        assertTrue(summary.slices.isEmpty())
    }

    @Test
    fun debtPaymentsCountAsSpendingAndAreSplitOut() {
        val txs = listOf(
            expense(d(2026, 11, 2), 4_000),
            expense(d(2026, 11, 5), 20_000, debt = 7L),
        )
        val summary = InsightsCalculator.compute(txs, 1, today)
        val bar = summary.bars.last()
        assertEquals(24_000L, bar.spendingCents)
        assertEquals(20_000L, bar.debtCents)
        assertEquals(4_000L, bar.otherSpendingCents)
        assertEquals(20_000L, summary.debtPaidCents)
        assertEquals(24_000L, summary.spentCents)
        val debtSlice = summary.slices.single { it.kind == SliceKind.DEBT }
        assertEquals(20_000L, debtSlice.cents)
        assertEquals(83, debtSlice.percent)
        assertEquals(100, summary.slices.sumOf { it.percent })
        // the debt payment is never "the biggest expense"
        assertEquals(4_000L, summary.biggest?.amountCents)
    }

    @Test
    fun topSixCategoriesAndOther() {
        val txs = (1L..9L).map { expense(d(2026, 11, 3), it * 1_000, category = it) } +
            expense(d(2026, 11, 4), 500, category = null)
        val slices = InsightsCalculator.slices(txs)
        // categories 9..4 are the top six, then Other (3+2+1 thousand and the 500 uncategorised)
        assertEquals(listOf<Long?>(9, 8, 7, 6, 5, 4), slices.take(6).map { it.categoryId })
        assertTrue(slices.take(6).all { it.kind == SliceKind.CATEGORY })
        val other = slices.last()
        assertEquals(SliceKind.OTHER, other.kind)
        assertEquals(6_500L, other.cents)
        assertEquals(7, slices.size)
        assertEquals(100, slices.sumOf { it.percent })
    }

    @Test
    fun uncategorisedIsItsOwnSliceWhenBigEnough() {
        val txs = listOf(expense(d(2026, 11, 3), 3_000, category = null), expense(d(2026, 11, 3), 1_000, category = 5))
        val slices = InsightsCalculator.slices(txs)
        assertEquals(SliceKind.UNCATEGORISED, slices[0].kind)
        assertEquals(75, slices[0].percent)
        assertEquals(25, slices[1].percent)
    }

    @Test
    fun percentagesAlwaysAddUpTo100() {
        assertEquals(listOf(34, 33, 33), InsightsCalculator.percentages(listOf(1, 1, 1)))
        assertEquals(listOf(50, 50), InsightsCalculator.percentages(listOf(5, 5)))
        val p = InsightsCalculator.percentages(listOf(1, 2, 3, 4, 5, 6, 7))
        assertEquals(100, p.sum())
        assertEquals(listOf(0, 0), InsightsCalculator.percentages(listOf(0, 0)))
        assertEquals(emptyList<Int>(), InsightsCalculator.percentages(emptyList()))
        assertEquals(listOf(100), InsightsCalculator.percentages(listOf(123_456)))
        // a tiny slice may show 0 but the sum stays 100
        val tiny = InsightsCalculator.percentages(listOf(1, 100_000))
        assertEquals(100, tiny.sum())
    }

    @Test
    fun averageOfThreePreviousPeriods() {
        val txs = listOf(
            expense(d(2026, 8, 5), 10_000),
            expense(d(2026, 9, 5), 20_000),
            expense(d(2026, 10, 5), 30_000),
            expense(d(2026, 11, 5), 30_000),
        )
        val summary = InsightsCalculator.compute(txs, 1, today)
        assertEquals(3, summary.previousPeriodCount)
        assertEquals(20_000L, summary.previousAverageCents)
        assertEquals(50, summary.changePercent)
    }

    @Test
    fun averageWithFewerThanThreePeriods() {
        val txs = listOf(expense(d(2026, 10, 5), 10_000), expense(d(2026, 11, 5), 5_000))
        val summary = InsightsCalculator.compute(txs, 1, today)
        assertEquals(1, summary.previousPeriodCount)
        assertEquals(10_000L, summary.previousAverageCents)
        assertEquals(-50, summary.changePercent)
    }

    @Test
    fun noPreviousPeriodMeansNoAverage() {
        val summary = InsightsCalculator.compute(listOf(expense(d(2026, 11, 5), 5_000)), 1, today)
        assertEquals(0, summary.previousPeriodCount)
        assertNull(summary.previousAverageCents)
        assertNull(summary.changePercent)
    }

    @Test
    fun emptyPeriodsInsideTheDataStillCountInTheAverage() {
        val txs = listOf(expense(d(2026, 8, 5), 9_000), expense(d(2026, 11, 5), 3_000))
        val summary = InsightsCalculator.compute(txs, 1, today)
        assertEquals(3, summary.previousPeriodCount)
        assertEquals(3_000L, summary.previousAverageCents)
        assertEquals(0, summary.changePercent)
    }

    @Test
    fun averageIsZeroGivesNoPercent() {
        val txs = listOf(income(d(2026, 9, 5), 1_000), expense(d(2026, 11, 5), 3_000))
        val summary = InsightsCalculator.compute(txs, 1, today)
        assertEquals(0L, summary.previousAverageCents)
        assertNull(summary.changePercent)
    }

    @Test
    fun selectedPeriodCanBeAPastOne() {
        val txs = listOf(
            expense(d(2026, 9, 5), 8_000), income(d(2026, 9, 6), 20_000),
            expense(d(2026, 11, 5), 1_000),
        )
        val selected = Period.containing(d(2026, 9, 15), 1)
        val summary = InsightsCalculator.compute(txs, 1, today, selected)
        assertEquals(8_000L, summary.spentCents)
        assertEquals(20_000L, summary.incomeCents)
        assertEquals(12_000L, summary.keptCents)
        assertEquals(6, summary.bars.size)
        assertEquals(d(2026, 11, 1), summary.bars.last().period.start)
        // no data before September: no average
        assertEquals(0, summary.previousPeriodCount)
    }

    @Test
    fun biggestExpenseOfThePeriod() {
        val txs = listOf(
            expense(d(2026, 11, 2), 1_000, category = 1),
            expense(d(2026, 11, 3), 7_500, category = 2),
            expense(d(2026, 10, 3), 90_000, category = 3),
        )
        val summary = InsightsCalculator.compute(txs, 1, today)
        assertEquals(7_500L, summary.biggest?.amountCents)
        assertEquals(2L, summary.biggest?.categoryId)
    }

    @Test
    fun cumulativeSpendingForTheSparkline() {
        val period = Period.containing(today, 1)
        val txs = listOf(
            expense(d(2026, 11, 1), 1_000),
            expense(d(2026, 11, 3), 500),
            expense(d(2026, 11, 3), 250),
            expense(d(2026, 11, 20), 9_999), // after today: not shown
            income(d(2026, 11, 2), 50_000),
        )
        val line = InsightsCalculator.cumulativeSpending(txs, period, today)
        assertEquals(11, line.size)
        assertEquals(1_000L, line[0])
        assertEquals(1_000L, line[1])
        assertEquals(1_750L, line[2])
        assertEquals(1_750L, line.last())
    }

    @Test
    fun cumulativeSpendingOfAPastPeriodCoversAllItsDays() {
        val past = Period.containing(d(2026, 9, 10), 1)
        val line = InsightsCalculator.cumulativeSpending(listOf(expense(d(2026, 9, 30), 100)), past, today)
        assertEquals(30, line.size)
        assertEquals(100L, line.last())
        assertTrue(InsightsCalculator.cumulativeSpending(emptyList(), Period.containing(d(2026, 12, 5), 1), today).isEmpty())
    }
}
