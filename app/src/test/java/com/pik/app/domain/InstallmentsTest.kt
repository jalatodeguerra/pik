package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

class InstallmentsTest {
    private val now = YearMonth.of(2026, 11)

    @Test fun ninetyPercentOfTheRataCounts() {
        assertTrue(Installments.isRata(25_000, 25_000))
        assertTrue(Installments.isRata(22_500, 25_000)) // exactly 90 %
        assertFalse(Installments.isRata(22_499, 25_000))
        assertTrue(Installments.isRata(40_000, 25_000)) // more than the rata still counts once
        assertFalse(Installments.isRata(100, 0))
    }

    @Test fun noTotalMeansNoStatus() {
        assertNull(Installments.status(null, 5, 3, false, now))
        assertNull(Installments.status(0, null, 0, false, now))
    }

    @Test fun paidIsBeforePlusCounted() {
        val s = Installments.status(60, 12, 6, true, now)!!
        assertEquals(18, s.paid)
        assertEquals(42, s.left)
        assertEquals(60, s.total)
        assertEquals(0.3f, s.fraction, 0.0001f)
    }

    @Test fun paidBeforeDefaultsToZero() {
        val s = Installments.status(60, null, 2, false, now)!!
        assertEquals(2, s.paid)
        assertEquals(58, s.left)
    }

    @Test fun endMonthWhenThisMonthsRataIsPaid() {
        // 42 left, November paid: Nov is the first of the 42 -> the last one is Nov + 41 months = April 2030
        val s = Installments.status(60, 12, 6, true, now)!!
        assertEquals(YearMonth.of(2030, 4), s.endMonth)
    }

    @Test fun endMonthWhenThisMonthsRataIsStillToPay() {
        // 42 left, November not paid yet: Nov + 42 months = May 2030
        val s = Installments.status(60, 12, 6, false, now)!!
        assertEquals(YearMonth.of(2030, 5), s.endMonth)
    }

    @Test fun lastRataPaidThisMonthEndsNow() {
        val s = Installments.status(12, null, 11, true, now)!!
        assertEquals(1, s.left)
        assertEquals(now, s.endMonth)
    }

    @Test fun finishedLoanHasNoEndMonthAndNeverGoesNegative() {
        val s = Installments.status(12, 10, 5, true, now)!!
        assertEquals(12, s.paid)
        assertEquals(0, s.left)
        assertNull(s.endMonth)
        assertEquals(1f, s.fraction, 0.0001f)
    }
}
