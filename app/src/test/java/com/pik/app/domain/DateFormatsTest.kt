package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class DateFormatsTest {
    @Test fun shortDate() = assertEquals("3 Nov 2026", DateFormats.shortDate(LocalDate.of(2026, 11, 3)))

    @Test fun dayHeader() = assertEquals("Tue 10 Nov", DateFormats.dayHeader(LocalDate.of(2026, 11, 10)))

    @Test fun monthYear() = assertEquals("November 2026", DateFormats.monthYear(YearMonth.of(2026, 11)))

    @Test fun relative() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(RelativeDay.TODAY, DateFormats.relativeDay(today, today))
        assertEquals(RelativeDay.YESTERDAY, DateFormats.relativeDay(today.minusDays(1), today))
        assertEquals(RelativeDay.OTHER, DateFormats.relativeDay(today.minusDays(2), today))
    }
}
