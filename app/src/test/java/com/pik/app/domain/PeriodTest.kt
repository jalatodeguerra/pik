package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class PeriodTest {
    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    @Test fun startDayOneIsCalendarMonth() {
        val p = Period.containing(d(2026, 11, 11), 1)
        assertEquals(d(2026, 11, 1), p.start)
        assertEquals(d(2026, 11, 30), p.endInclusive)
        assertEquals("November", p.label(java.util.Locale.ENGLISH))
        assertEquals("2026-11", p.key)
        assertEquals(30, p.lengthDays)
    }

    @Test fun todayAfterStartDay() {
        val p = Period.containing(d(2026, 11, 27), 27)
        assertEquals(d(2026, 11, 27), p.start)
        assertEquals(d(2026, 12, 26), p.endInclusive)
        assertEquals("27 Nov \u2013 26 Dec", p.label(java.util.Locale.ENGLISH))
        assertEquals("2026-11", p.key)
    }

    @Test fun todayBeforeStartDay() {
        val p = Period.containing(d(2026, 11, 11), 27)
        assertEquals(d(2026, 10, 27), p.start)
        assertEquals(d(2026, 11, 26), p.endInclusive)
        assertEquals("27 Oct \u2013 26 Nov", p.label(java.util.Locale.ENGLISH))
        assertEquals("2026-10", p.key)
    }

    @Test fun labelInItalian() {
        assertEquals("Novembre", Period.containing(d(2026, 11, 11), 1).label(java.util.Locale.ITALIAN))
        assertEquals(
            "27 ott \u2013 26 nov",
            Period.containing(d(2026, 11, 11), 27).label(java.util.Locale.ITALIAN),
        )
    }

    @Test fun lastDayOfPeriodAndFirstOfNext() {
        assertEquals(d(2026, 10, 27), Period.containing(d(2026, 11, 26), 27).start)
        assertEquals(d(2026, 11, 27), Period.containing(d(2026, 11, 27), 27).start)
    }

    @Test fun startDay28InFebruary() {
        val p = Period.containing(d(2027, 2, 15), 28)
        assertEquals(d(2027, 1, 28), p.start)
        assertEquals(d(2027, 2, 27), p.endInclusive)
        val feb = Period.containing(d(2027, 2, 28), 28)
        assertEquals(d(2027, 2, 28), feb.start)
        assertEquals(d(2027, 3, 27), feb.endInclusive)
        assertEquals(28, feb.lengthDays)
    }

    @Test fun startDay28InLeapFebruary() {
        val p = Period.containing(d(2028, 2, 28), 28)
        assertEquals(d(2028, 3, 27), p.endInclusive)
        assertEquals(29, p.lengthDays)
        assertEquals(31, Period.containing(d(2028, 1, 31), 28).lengthDays)
    }

    @Test fun yearBoundaryBefore() {
        val p = Period.containing(d(2027, 1, 5), 27)
        assertEquals(d(2026, 12, 27), p.start)
        assertEquals(d(2027, 1, 26), p.endInclusive)
        assertEquals("2026-12", p.key)
    }

    @Test fun yearBoundaryAfter() {
        val p = Period.containing(d(2026, 12, 30), 27)
        assertEquals(d(2026, 12, 27), p.start)
        assertEquals(d(2027, 1, 26), p.endInclusive)
    }

    @Test fun previousAndNext() {
        val p = Period.containing(d(2027, 1, 5), 27)
        assertEquals(d(2026, 11, 27), p.previous().start)
        assertEquals(d(2026, 12, 26), p.previous().endInclusive)
        assertEquals(d(2027, 1, 27), p.next().start)
        assertEquals(d(2027, 2, 26), p.next().endInclusive)
        assertEquals(p, p.next().previous())
        assertEquals(p, p.previous().next())
    }

    @Test fun periodsAreContiguousForEveryStartDay() {
        for (day in 1..28) {
            var p = Period.containing(d(2026, 6, 15), day)
            repeat(30) {
                val n = p.next()
                assertEquals(p.endInclusive.plusDays(1), n.start)
                assertEquals(p, Period.containing(p.start, day))
                assertEquals(p, Period.containing(p.endInclusive, day))
                p = n
            }
        }
    }

    @Test fun contains() {
        val p = Period.containing(d(2026, 11, 11), 27)
        assertTrue(d(2026, 10, 27) in p)
        assertTrue(d(2026, 11, 26) in p)
        assertFalse(d(2026, 10, 26) in p)
        assertFalse(d(2026, 11, 27) in p)
    }

    @Test fun daysLeftIncludesToday() {
        val p = Period.containing(d(2026, 11, 1), 1)
        assertEquals(30, p.daysLeft(d(2026, 11, 1)))
        assertEquals(20, p.daysLeft(d(2026, 11, 11)))
        assertEquals(1, p.daysLeft(d(2026, 11, 30)))
        assertEquals(0, p.daysLeft(d(2026, 12, 1)))
        assertEquals(30, p.daysLeft(d(2026, 10, 20)))
    }

    @Test fun startDayIsClamped() {
        assertEquals(28, Period.containing(d(2026, 11, 29), 31).startDay)
        assertEquals(1, Period.containing(d(2026, 11, 29), 0).startDay)
    }

    @Test fun keyIsZeroPadded() {
        assertEquals("2026-03", Period.containing(d(2026, 3, 9), 1).key)
    }
}
