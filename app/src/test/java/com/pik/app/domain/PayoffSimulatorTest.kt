package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class PayoffSimulatorTest {
    private val start = YearMonth.of(2026, 11)

    private fun debt(id: Long, euros: Long, aprBp: Int, minEuros: Long) =
        SimDebt(id, euros * 100, aprBp, minEuros * 100)

    private fun sim(debts: List<SimDebt>, extraEuros: Long = 0, order: PayoffOrder = PayoffOrder.HIGHEST_INTEREST) =
        PayoffSimulator.simulate(debts, extraEuros * 100, order, start)

    @Test fun emptyListIsDebtFreeNow() {
        val r = sim(emptyList())
        assertTrue(r.isDebtFreeNow)
        assertEquals(0, r.months)
        assertEquals(0L, r.totalInterestCents)
        assertNull(r.debtFreeMonth)
        assertFalse(r.neverPaysOff)
    }

    @Test fun debtsWithZeroBalanceAreDebtFreeNow() {
        val r = sim(listOf(debt(1, 0, 1200, 50)))
        assertTrue(r.isDebtFreeNow)
    }

    /**
     * 1.000,00 EUR at 12 % APR (1 % per month), 100,00 EUR per month.
     * Hand computation with the closed form, rate r = 0,01:
     *   balance after 10 payments = 1000 * 1,01^10 - 100 * (1,01^10 - 1) / 0,01
     *                             = 1104,6221 - 1046,2213 = 58,4009
     *   month 11: interest 0,584009, final payment 58,4009 * 1,01 = 58,9849
     *   total paid = 10 * 100 + 58,9849 = 1058,9849  ->  interest = 58,98 EUR (to the cent)
     * So 11 months (the last one partial) and 58,98 EUR of interest. The first month's interest
     * is exactly 10,00 EUR and the second's 9,10 EUR (910,00 * 1 %).
     */
    @Test fun singleDebtKnownAmortisation() {
        val r = sim(listOf(debt(1, 1000, 1200, 100)))
        assertFalse(r.neverPaysOff)
        assertEquals(11, r.months)
        assertEquals(YearMonth.of(2027, 9), r.debtFreeMonth)
        assertEquals(mapOf(1L to YearMonth.of(2027, 9)), r.payoffMonthByDebt)
        val hand = 5898L
        assertTrue("interest ${r.totalInterestCents} not within a cent of $hand", kotlin.math.abs(r.totalInterestCents - hand) <= 1)
    }

    @Test fun rateOfZeroPaysExactlyTheBalance() {
        val r = sim(listOf(debt(1, 1200, 0, 100)))
        assertEquals(12, r.months)
        assertEquals(0L, r.totalInterestCents)
        assertEquals(YearMonth.of(2027, 10), r.debtFreeMonth)
    }

    @Test fun lastPaymentIsCappedAtTheBalance() {
        val r = sim(listOf(debt(1, 1050, 0, 100)))
        assertEquals(11, r.months)
        assertEquals(0L, r.totalInterestCents)
    }

    @Test fun clearedMinimumRollsOverToTheNextDebt() {
        // A: 1.000 at 0 %, min 500. B: 5.000 at 0 %, min 100. Without roll-over B would take 48 months.
        val r = sim(listOf(debt(1, 1000, 0, 500), debt(2, 5000, 0, 100)))
        assertEquals(YearMonth.of(2026, 12), r.payoffMonthByDebt[1L]) // month 2
        // from month 3 on B gets 100 + 500 = 600 a month: 4.800 left after month 2 -> 8 more months
        assertEquals(10, r.months)
        assertEquals(YearMonth.of(2027, 8), r.payoffMonthByDebt[2L])
        assertEquals(YearMonth.of(2027, 8), r.debtFreeMonth)
    }

    @Test fun leftoverCascadesToTheNextTargetInTheSameMonth() {
        // A: 50, min 10. B: 1.000, min 10. Extra 100, smallest first.
        // Month 1: minimums leave A 40 and B 990; the pool of 100 clears A (40) and puts 60 on B.
        val r = sim(listOf(debt(1, 50, 0, 10), debt(2, 1000, 0, 10)), extraEuros = 100, order = PayoffOrder.SMALLEST_BALANCE)
        assertEquals(start, r.payoffMonthByDebt[1L])
        // B: 930 after month 1, then 120 a month: 7 more months (840) + final 90 -> 8 -> 9 months total
        assertEquals(9, r.months)
    }

    @Test fun bothOrdersDifferInInterest() {
        val a = debt(1, 2000, 2000, 50) // 20 %
        val b = debt(2, 500, 500, 25) // 5 %, smaller
        val cmp = PayoffSimulator.compareOrders(listOf(a, b), 200 * 100, start)
        assertEquals(18094L, cmp.highestInterest.totalInterestCents)
        assertEquals(22170L, cmp.smallestBalance.totalInterestCents)
        assertTrue(cmp.highestInterest.totalInterestCents < cmp.smallestBalance.totalInterestCents)
        // the small debt is cleared much sooner when it is the target
        assertEquals(YearMonth.of(2027, 1), cmp.smallestBalance.payoffMonthByDebt[2L])
        assertEquals(YearMonth.of(2027, 8), cmp.highestInterest.payoffMonthByDebt[2L])
        assertEquals(cmp.highestInterest, cmp.of(PayoffOrder.HIGHEST_INTEREST))
        assertEquals(cmp.smallestBalance, cmp.of(PayoffOrder.SMALLEST_BALANCE))
    }

    @Test fun targetOrderBreaksTiesById() {
        val debts = listOf(debt(7, 100, 1000, 10), debt(3, 100, 1000, 10), debt(5, 50, 1000, 10))
        assertEquals(listOf(3L, 5L, 7L), PayoffSimulator.targetOrder(debts, PayoffOrder.HIGHEST_INTEREST))
        assertEquals(listOf(5L, 3L, 7L), PayoffSimulator.targetOrder(debts, PayoffOrder.SMALLEST_BALANCE))
    }

    @Test fun minimumBelowInterestNeverPaysOff() {
        // 10.000 at 12 % grows by 100 a month, the minimum is 50
        val r = sim(listOf(debt(1, 10_000, 1200, 50)))
        assertTrue(r.neverPaysOff)
        assertNull(r.debtFreeMonth)
        assertFalse(r.isDebtFreeNow)
    }

    @Test fun minimumEqualToInterestNeverPaysOff() {
        val r = sim(listOf(debt(1, 10_000, 1200, 100)))
        assertTrue(r.neverPaysOff)
    }

    @Test fun nothingPaidNeverPaysOffEvenAtZeroRate() {
        assertTrue(sim(listOf(debt(1, 100, 0, 0))).neverPaysOff)
    }

    @Test fun extraRescuesAPlanThatDoesNotCoverTheInterest() {
        val debts = listOf(debt(1, 10_000, 1200, 50))
        assertTrue(sim(debts).neverPaysOff)
        val r = sim(debts, extraEuros = 300)
        assertFalse(r.neverPaysOff)
        assertNotNull(r.debtFreeMonth)
    }

    private fun assertNotNull(value: Any?) = assertTrue(value != null)

    @Test fun stopsAfterFiftyYears() {
        // interest 10.000,00 a month, payment 100,01 more: the balance falls by 1 cent a month
        val r = PayoffSimulator.simulate(
            listOf(SimDebt(1, 1_000_000, 1200, 10_001)),
            0, PayoffOrder.HIGHEST_INTEREST, start,
        )
        assertTrue(r.neverPaysOff)
        assertEquals(PayoffSimulator.MAX_MONTHS, r.months)
    }

    @Test fun interestRoundsHalfUpToTheCent() {
        // 12,00 EUR at 12,5 %: 1200 * 1250 / 120000 = 12,5 cents -> 13
        assertEquals(13L, PayoffSimulator.monthlyInterestCents(1200, 1250))
        // 11,99 EUR: 12,49 cents -> 12
        assertEquals(12L, PayoffSimulator.monthlyInterestCents(1199, 1250))
        // 1.000,00 at 12 % = exactly 10,00
        assertEquals(1000L, PayoffSimulator.monthlyInterestCents(100_000, 1200))
        assertEquals(0L, PayoffSimulator.monthlyInterestCents(100_000, 0))
        assertEquals(0L, PayoffSimulator.monthlyInterestCents(0, 1200))
        // 0,01 EUR at 1 %: far below half a cent
        assertEquals(0L, PayoffSimulator.monthlyInterestCents(1, 100))
    }

    @Test fun whatIfSavesMonthsAndInterest() {
        val debts = listOf(debt(1, 1000, 1200, 100))
        val w = PayoffSimulator.whatIf(debts, 100 * 100, PayoffOrder.HIGHEST_INTEREST, start)
        assertEquals(6, w.result.months)
        assertEquals(3112L, w.result.totalInterestCents)
        assertEquals(11, w.baseline.months)
        assertEquals(5, w.monthsSooner)
        assertEquals(5898L - 3112L, w.interestSavedCents)
        assertFalse(w.makesItPossible)
    }

    @Test fun whatIfZeroExtraSavesNothing() {
        val w = PayoffSimulator.whatIf(listOf(debt(1, 1000, 1200, 100)), 0, PayoffOrder.SMALLEST_BALANCE, start)
        assertEquals(0, w.monthsSooner)
        assertEquals(0L, w.interestSavedCents)
    }

    @Test fun whatIfCanMakeAnImpossiblePlanPossible() {
        val w = PayoffSimulator.whatIf(listOf(debt(1, 10_000, 1200, 50)), 300 * 100, PayoffOrder.HIGHEST_INTEREST, start)
        assertTrue(w.makesItPossible)
        assertNull(w.monthsSooner)
        assertNull(w.interestSavedCents)
    }

    @Test fun negativeExtraIsTreatedAsZero() {
        val a = PayoffSimulator.simulate(listOf(debt(1, 1000, 1200, 100)), -500, PayoffOrder.HIGHEST_INTEREST, start)
        assertEquals(11, a.months)
    }
}
