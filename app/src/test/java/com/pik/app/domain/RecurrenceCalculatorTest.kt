package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RecurrenceCalculatorTest {

    private fun d(y: Int, m: Int, day: Int) = LocalDate.of(y, m, day)

    private fun monthly(start: LocalDate, every: Int = 1, day: Int? = null, end: LocalDate? = null, max: Int? = null) =
        RecurrenceSchedule(start, every, RecurrenceUnit.MONTH, day, end, max)

    private fun dates(s: RecurrenceSchedule, count: Int) = (0 until count).map { RecurrenceCalculator.dateOf(s, it) }

    // ---- months and clamping ----

    @Test fun monthlyOnThe5th() {
        val s = monthly(d(2026, 1, 5), day = 5)
        assertEquals(
            listOf(d(2026, 1, 5), d(2026, 2, 5), d(2026, 3, 5), d(2026, 4, 5)),
            dates(s, 4),
        )
    }

    @Test fun the31stIsClampedToShortMonthsAndNeverDrifts() {
        val s = monthly(d(2026, 1, 31), day = 31)
        assertEquals(
            listOf(
                d(2026, 1, 31), d(2026, 2, 28), d(2026, 3, 31), d(2026, 4, 30), d(2026, 5, 31),
                d(2026, 6, 30), d(2026, 7, 31), d(2026, 8, 31), d(2026, 9, 30), d(2026, 10, 31),
                d(2026, 11, 30), d(2026, 12, 31),
            ),
            dates(s, 12),
        )
    }

    @Test fun the29thGoesBackToTheLeapDay() {
        val s = monthly(d(2027, 12, 29), day = 29)
        assertEquals(d(2028, 1, 29), RecurrenceCalculator.dateOf(s, 1))
        assertEquals(d(2028, 2, 29), RecurrenceCalculator.dateOf(s, 2)) // 2028 is a leap year
        assertEquals(d(2028, 3, 29), RecurrenceCalculator.dateOf(s, 3))
        val plain = monthly(d(2026, 12, 29), day = 29)
        assertEquals(d(2027, 2, 28), RecurrenceCalculator.dateOf(plain, 2)) // 2027 is not
    }

    @Test fun the30thIsClampedInFebruaryOnly() {
        val s = monthly(d(2026, 1, 30), day = 30)
        assertEquals(d(2026, 2, 28), RecurrenceCalculator.dateOf(s, 1))
        assertEquals(d(2026, 3, 30), RecurrenceCalculator.dateOf(s, 2))
        assertEquals(d(2026, 4, 30), RecurrenceCalculator.dateOf(s, 3))
    }

    @Test fun dayOfMonthDefaultsToTheStartDay() {
        val s = monthly(d(2026, 3, 17))
        assertEquals(d(2026, 4, 17), RecurrenceCalculator.dateOf(s, 1))
    }

    @Test fun quarterlyIsEveryThreeMonths() {
        val s = monthly(d(2026, 1, 15), every = 3, day = 15)
        assertEquals(
            listOf(d(2026, 1, 15), d(2026, 4, 15), d(2026, 7, 15), d(2026, 10, 15), d(2027, 1, 15)),
            dates(s, 5),
        )
    }

    @Test fun everyTwoMonthsCrossesTheYear() {
        val s = monthly(d(2026, 11, 10), every = 2, day = 10)
        assertEquals(listOf(d(2026, 11, 10), d(2027, 1, 10), d(2027, 3, 10)), dates(s, 3))
    }

    // ---- weeks and years ----

    @Test fun everyWeekAndEveryTwoWeeks() {
        val week = RecurrenceSchedule(d(2026, 11, 2), 1, RecurrenceUnit.WEEK)
        assertEquals(listOf(d(2026, 11, 2), d(2026, 11, 9), d(2026, 11, 16)), dates(week, 3))
        val fortnight = RecurrenceSchedule(d(2026, 12, 28), 2, RecurrenceUnit.WEEK)
        assertEquals(listOf(d(2026, 12, 28), d(2027, 1, 11), d(2027, 1, 25), d(2027, 2, 8)), dates(fortnight, 4))
    }

    @Test fun weeklyIgnoresDayOfMonth() {
        val s = RecurrenceSchedule(d(2026, 11, 2), 1, RecurrenceUnit.WEEK, dayOfMonth = 20)
        assertEquals(d(2026, 11, 9), RecurrenceCalculator.dateOf(s, 1))
    }

    @Test fun yearlyKeepsTheDate() {
        val s = RecurrenceSchedule(d(2026, 3, 8), 1, RecurrenceUnit.YEAR, 8)
        assertEquals(listOf(d(2026, 3, 8), d(2027, 3, 8), d(2028, 3, 8)), dates(s, 3))
        val every2 = RecurrenceSchedule(d(2026, 3, 8), 2, RecurrenceUnit.YEAR, 8)
        assertEquals(d(2030, 3, 8), RecurrenceCalculator.dateOf(every2, 2))
    }

    @Test fun leapDayYearly() {
        val s = RecurrenceSchedule(d(2024, 2, 29), 1, RecurrenceUnit.YEAR, 29)
        assertEquals(d(2025, 2, 28), RecurrenceCalculator.dateOf(s, 1))
        assertEquals(d(2026, 2, 28), RecurrenceCalculator.dateOf(s, 2))
        assertEquals(d(2028, 2, 29), RecurrenceCalculator.dateOf(s, 4))
    }

    // ---- ends ----

    @Test fun endsAfterNTimes() {
        val s = monthly(d(2026, 1, 5), day = 5, max = 3)
        assertEquals(d(2026, 3, 5), RecurrenceCalculator.dateOf(s, 2))
        assertNull(RecurrenceCalculator.dateOf(s, 3))
        assertFalse(RecurrenceCalculator.isFinished(s, 2))
        assertTrue(RecurrenceCalculator.isFinished(s, 3))
        // done counts confirmed and skipped ones together
        assertEquals(d(2026, 3, 5), RecurrenceCalculator.nextDueDate(s, 3))
    }

    @Test fun endsOnADateInclusive() {
        val s = monthly(d(2026, 1, 5), day = 5, end = d(2026, 3, 5))
        assertEquals(d(2026, 3, 5), RecurrenceCalculator.dateOf(s, 2))
        assertNull(RecurrenceCalculator.dateOf(s, 3))
        val before = monthly(d(2026, 1, 5), day = 5, end = d(2026, 3, 4))
        assertNull(RecurrenceCalculator.dateOf(before, 2))
    }

    @Test fun earliestEndWins() {
        val s = monthly(d(2026, 1, 5), day = 5, end = d(2026, 12, 31), max = 2)
        assertEquals(2, RecurrenceCalculator.between(s, d(2026, 1, 1), d(2026, 12, 31)).size)
        val t = monthly(d(2026, 1, 5), day = 5, end = d(2026, 2, 28), max = 10)
        assertEquals(2, RecurrenceCalculator.between(t, d(2026, 1, 1), d(2026, 12, 31)).size)
    }

    @Test fun nextDueDateOfAnUnfinishedRuleIsTheNextOccurrence() {
        val s = monthly(d(2026, 1, 5), day = 5)
        assertEquals(d(2026, 1, 5), RecurrenceCalculator.nextDueDate(s, 0))
        assertEquals(d(2026, 6, 5), RecurrenceCalculator.nextDueDate(s, 5))
    }

    // ---- between / next ----

    @Test fun betweenIsInclusiveOnBothEnds() {
        val s = monthly(d(2026, 1, 5), day = 5)
        val list = RecurrenceCalculator.between(s, d(2026, 2, 5), d(2026, 4, 5))
        assertEquals(listOf(d(2026, 2, 5), d(2026, 3, 5), d(2026, 4, 5)), list.map { it.date })
        assertEquals(listOf(1, 2, 3), list.map { it.index })
    }

    @Test fun betweenWeeklyLongAfterTheStart() {
        val s = RecurrenceSchedule(d(2020, 1, 6), 1, RecurrenceUnit.WEEK)
        val list = RecurrenceCalculator.between(s, d(2026, 11, 1), d(2026, 11, 30))
        assertEquals(listOf(d(2026, 11, 2), d(2026, 11, 9), d(2026, 11, 16), d(2026, 11, 23), d(2026, 11, 30)), list.map { it.date })
        assertEquals(RecurrenceCalculator.indexOnOrAfter(s, d(2026, 11, 2)), list.first().index)
    }

    @Test fun betweenBeforeTheStartAndAfterTheEndIsEmpty() {
        val s = monthly(d(2026, 6, 5), day = 5, max = 2)
        assertTrue(RecurrenceCalculator.between(s, d(2026, 1, 1), d(2026, 5, 31)).isEmpty())
        assertTrue(RecurrenceCalculator.between(s, d(2026, 8, 1), d(2026, 12, 31)).isEmpty())
        assertEquals(2, RecurrenceCalculator.between(s, d(2026, 1, 1), d(2026, 12, 31)).size)
    }

    @Test fun nextAfterIsStrict() {
        val s = monthly(d(2026, 1, 31), day = 31)
        assertEquals(d(2026, 2, 28), RecurrenceCalculator.nextAfter(s, d(2026, 1, 31))?.date)
        assertEquals(d(2026, 3, 31), RecurrenceCalculator.nextAfter(s, d(2026, 2, 28))?.date)
        assertEquals(d(2026, 2, 28), RecurrenceCalculator.nextOnOrAfter(s, d(2026, 2, 1))?.date)
        assertEquals(d(2026, 2, 28), RecurrenceCalculator.nextOnOrAfter(s, d(2026, 2, 28))?.date)
    }

    @Test fun nextAfterTheEndIsNull() {
        val s = monthly(d(2026, 1, 5), day = 5, max = 2)
        assertNull(RecurrenceCalculator.nextAfter(s, d(2026, 2, 5)))
    }

    @Test fun nextHonoursFromIndex() {
        val s = monthly(d(2026, 1, 5), day = 5)
        // occurrences 0..2 are done: the next one is number 3 even if the date asked for is earlier
        val next = RecurrenceCalculator.nextOnOrAfter(s, d(2026, 1, 1), fromIndex = 3)
        assertEquals(3, next?.index)
        assertEquals(d(2026, 4, 5), next?.date)
    }

    @Test fun indexOnOrAfterMatchesABruteForceScan() {
        val schedules = listOf(
            monthly(d(2026, 1, 31), day = 31),
            monthly(d(2026, 1, 15), every = 3, day = 15),
            RecurrenceSchedule(d(2026, 1, 1), 2, RecurrenceUnit.WEEK),
            RecurrenceSchedule(d(2024, 2, 29), 1, RecurrenceUnit.YEAR, 29),
            RecurrenceSchedule(d(2026, 5, 20), 5, RecurrenceUnit.MONTH, 20),
        )
        for (s in schedules) {
            var date = d(2025, 6, 1)
            while (date < d(2031, 1, 1)) {
                var expected = 0
                while (RecurrenceCalculator.rawDate(s, expected) < date) expected++
                assertEquals("$s $date", expected, RecurrenceCalculator.indexOnOrAfter(s, date))
                date = date.plusDays(11)
            }
        }
    }

    // ---- pending / countBefore / normalized ----

    @Test fun pendingStartsAtDoneAndStopsAtUntil() {
        val s = monthly(d(2026, 1, 5), day = 5)
        val p = RecurrenceCalculator.pending(s, done = 9, until = d(2026, 12, 31))
        assertEquals(listOf(d(2026, 10, 5), d(2026, 11, 5), d(2026, 12, 5)), p.map { it.date })
        assertEquals(listOf(9, 10, 11), p.map { it.index })
        assertTrue(RecurrenceCalculator.pending(s, done = 9, until = d(2026, 10, 4)).isEmpty())
    }

    @Test fun pendingIsLimited() {
        val s = RecurrenceSchedule(d(2020, 1, 6), 1, RecurrenceUnit.WEEK)
        assertEquals(RecurrenceCalculator.MAX_PENDING, RecurrenceCalculator.pending(s, 0, d(2026, 12, 31)).size)
    }

    @Test fun countBeforeSkipsHistory() {
        val s = monthly(d(2026, 1, 5), day = 5)
        // cutoff 1 Nov 2026: Jan..Oct = 10 occurrences are before it
        assertEquals(10, RecurrenceCalculator.countBefore(s, d(2026, 11, 1)))
        assertEquals(0, RecurrenceCalculator.countBefore(s, d(2026, 1, 1)))
        assertEquals(0, RecurrenceCalculator.countBefore(s, d(2025, 1, 1)))
    }

    @Test fun countBeforeNeverExceedsTheSeries() {
        val byCount = monthly(d(2026, 1, 5), day = 5, max = 4)
        assertEquals(4, RecurrenceCalculator.countBefore(byCount, d(2026, 11, 1)))
        val byDate = monthly(d(2026, 1, 5), day = 5, end = d(2026, 3, 31))
        assertEquals(3, RecurrenceCalculator.countBefore(byDate, d(2026, 11, 1)))
        assertTrue(RecurrenceCalculator.isFinished(byDate, RecurrenceCalculator.countBefore(byDate, d(2026, 11, 1))))
    }

    @Test fun normalizedMovesAStartThatIsAfterItsFirstDay() {
        // start 20 Jan but pay on the 5th: the first payment is 5 Feb, not 5 Jan
        val s = monthly(d(2026, 1, 20), day = 5)
        val n = RecurrenceCalculator.normalized(s)
        assertEquals(d(2026, 2, 5), n.startDate)
        assertEquals(d(2026, 2, 5), RecurrenceCalculator.dateOf(n, 0))
        assertEquals(d(2026, 3, 5), RecurrenceCalculator.dateOf(n, 1))
        // normalising twice changes nothing
        assertEquals(n, RecurrenceCalculator.normalized(n))
    }

    @Test fun normalizedQuarterlyMovesByTheWholeInterval() {
        val s = monthly(d(2026, 1, 20), every = 3, day = 5)
        assertEquals(d(2026, 4, 5), RecurrenceCalculator.normalized(s).startDate)
    }

    @Test fun normalizedLeavesGoodSchedulesAlone() {
        val a = monthly(d(2026, 1, 5), day = 5)
        assertEquals(a, RecurrenceCalculator.normalized(a))
        val b = monthly(d(2026, 1, 5), day = 20)
        assertEquals(b, RecurrenceCalculator.normalized(b))
        val clamped = monthly(d(2026, 4, 30), day = 31)
        assertEquals(clamped, RecurrenceCalculator.normalized(clamped))
        val week = RecurrenceSchedule(d(2026, 1, 20), 1, RecurrenceUnit.WEEK, 5)
        assertEquals(week, RecurrenceCalculator.normalized(week))
    }

    // ---- amounts per month and year ----

    @Test fun monthlyAndYearlyEquivalents() {
        assertEquals(1000L, RecurrenceCalculator.monthlyCents(1000, 1, RecurrenceUnit.MONTH))
        assertEquals(12000L, RecurrenceCalculator.yearlyCents(1000, 1, RecurrenceUnit.MONTH))
        // quarterly 30,00 = 10,00 per month
        assertEquals(1000L, RecurrenceCalculator.monthlyCents(3000, 3, RecurrenceUnit.MONTH))
        assertEquals(12000L, RecurrenceCalculator.yearlyCents(3000, 3, RecurrenceUnit.MONTH))
        // yearly 120,00 = 10,00 per month
        assertEquals(1000L, RecurrenceCalculator.monthlyCents(12000, 1, RecurrenceUnit.YEAR))
        assertEquals(12000L, RecurrenceCalculator.yearlyCents(12000, 1, RecurrenceUnit.YEAR))
        // weekly 10,00 = 520,00 per year = 43,33 per month
        assertEquals(52000L, RecurrenceCalculator.yearlyCents(1000, 1, RecurrenceUnit.WEEK))
        assertEquals(4333L, RecurrenceCalculator.monthlyCents(1000, 1, RecurrenceUnit.WEEK))
        // every two weeks 10,00 = 260,00 per year
        assertEquals(26000L, RecurrenceCalculator.yearlyCents(1000, 2, RecurrenceUnit.WEEK))
        assertEquals(2167L, RecurrenceCalculator.monthlyCents(1000, 2, RecurrenceUnit.WEEK))
    }

    @Test fun equivalentsAreRoundedHalfUpAndSafeForBadInterval() {
        // 1 cent every 2 months = 0,5 per month: rounds up to 1
        assertEquals(1L, RecurrenceCalculator.monthlyCents(1, 2, RecurrenceUnit.MONTH))
        assertEquals(0L, RecurrenceCalculator.monthlyCents(0, 1, RecurrenceUnit.MONTH))
        assertEquals(1000L, RecurrenceCalculator.monthlyCents(1000, 0, RecurrenceUnit.MONTH))
    }
}
