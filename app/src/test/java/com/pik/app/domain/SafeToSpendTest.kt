package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafeToSpendTest {
    @Test fun expectedIncomeWinsWhenHigher() {
        val s = SafeToSpendCalculator.compute(200_000, 50_000, 80_000, 10)
        assertEquals(200_000L, s.plannedIncomeCents)
        assertEquals(120_000L, s.safeCents)
        assertEquals(12_000L, s.perDayCents)
        assertFalse(s.isOverSpent)
    }

    @Test fun receivedIncomeWinsWhenHigher() {
        val s = SafeToSpendCalculator.compute(100_000, 150_000, 40_000, 11)
        assertEquals(150_000L, s.plannedIncomeCents)
        assertEquals(110_000L, s.safeCents)
        assertEquals(10_000L, s.perDayCents)
    }

    @Test fun noExpectedIncomeUsesReceived() {
        val s = SafeToSpendCalculator.compute(null, 90_000, 30_000, 6)
        assertEquals(90_000L, s.plannedIncomeCents)
        assertEquals(60_000L, s.safeCents)
    }

    @Test fun nothingKnownIsZeroPlanned() {
        val s = SafeToSpendCalculator.compute(null, 0, 0, 5)
        assertEquals(0L, s.safeCents)
        assertEquals(0L, s.perDayCents)
        assertFalse(s.isOverSpent)
    }

    @Test fun overSpentHasNegativeSafeAndZeroPerDay() {
        val s = SafeToSpendCalculator.compute(100_000, 0, 130_000, 10)
        assertEquals(-30_000L, s.safeCents)
        assertEquals(0L, s.perDayCents)
        assertTrue(s.isOverSpent)
    }

    @Test fun perDayRoundsDown() {
        assertEquals(333L, SafeToSpendCalculator.compute(1_000, 0, 0, 3).perDayCents)
    }

    @Test fun lastDayUsesWholeAmount() {
        assertEquals(5_000L, SafeToSpendCalculator.compute(5_000, 0, 0, 1).perDayCents)
    }

    @Test fun zeroDaysLeftDoesNotDivideByZero() {
        assertEquals(5_000L, SafeToSpendCalculator.compute(5_000, 0, 0, 0).perDayCents)
    }

    @Test fun billsAndDebtPaymentsReduceSafe() {
        val s = SafeToSpendCalculator.compute(100_000, 0, 20_000, 10, billsStillDueCents = 30_000, debtPaymentsDueCents = 10_000)
        assertEquals(40_000L, s.safeCents)
        assertEquals(4_000L, s.perDayCents)
    }

    @Test fun recurringIncomeStillDueRaisesPlannedIncome() {
        // nothing expected in Settings: 500 received + 2.000 salary still to come
        val s = SafeToSpendCalculator.compute(null, 50_000, 30_000, 10, incomeStillDueCents = 200_000)
        assertEquals(250_000L, s.plannedIncomeCents)
        assertEquals(200_000L, s.incomeStillDueCents)
        assertEquals(220_000L, s.safeCents)
    }

    @Test fun expectedIncomeStillWinsWhenHigherThanReceivedPlusStillDue() {
        val s = SafeToSpendCalculator.compute(300_000, 50_000, 0, 10, incomeStillDueCents = 200_000)
        assertEquals(300_000L, s.plannedIncomeCents)
        assertEquals(300_000L, s.safeCents)
    }

    @Test fun billsStillDueAreSubtractedOnceAndMoveToSpentWhenConfirmed() {
        // 1.000 planned, a 100 bill still due
        val before = SafeToSpendCalculator.compute(100_000, 0, 0, 10, billsStillDueCents = 10_000)
        assertEquals(90_000L, before.safeCents)
        // after confirming the bill it is an expense and no longer still due: same safe amount
        val after = SafeToSpendCalculator.compute(100_000, 0, 10_000, 10, billsStillDueCents = 0)
        assertEquals(90_000L, after.safeCents)
    }

    @Test fun billsDebtsAndSpentAreAllSubtracted() {
        val s = SafeToSpendCalculator.compute(
            100_000, 0, 20_000, 10, billsStillDueCents = 10_000, debtPaymentsDueCents = 5_000,
        )
        assertEquals(65_000L, s.safeCents)
        assertEquals(6_500L, s.perDayCents)
    }
}
