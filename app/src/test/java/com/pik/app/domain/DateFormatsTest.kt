package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class DateFormatsTest {
    private val en = Locale.ENGLISH
    private val ita = Locale.ITALIAN

    @Test fun shortDate() {
        assertEquals("3 Nov 2026", DateFormats.shortDate(LocalDate.of(2026, 11, 3), en))
        assertEquals("3 nov 2026", DateFormats.shortDate(LocalDate.of(2026, 11, 3), ita))
    }

    @Test fun dayHeader() {
        assertEquals("Tue 10 Nov", DateFormats.dayHeader(LocalDate.of(2026, 11, 10), en))
        assertEquals("Mar 10 nov", DateFormats.dayHeader(LocalDate.of(2026, 11, 10), ita))
    }

    @Test fun monthYear() {
        assertEquals("November 2026", DateFormats.monthYear(YearMonth.of(2026, 11), en))
        assertEquals("Novembre 2026", DateFormats.monthYear(YearMonth.of(2026, 11), ita))
    }

    @Test fun monthYearInlineKeepsTheLocaleCase() {
        assertEquals("March 2030", DateFormats.monthYearInline(YearMonth.of(2030, 3), en))
        assertEquals("marzo 2030", DateFormats.monthYearInline(YearMonth.of(2030, 3), ita))
    }

    @Test fun dayMonth() {
        assertEquals("27 Oct", DateFormats.dayMonth(LocalDate.of(2026, 10, 27), en))
        assertEquals("27 ott", DateFormats.dayMonth(LocalDate.of(2026, 10, 27), ita))
    }

    @Test fun defaultLocaleIsUsedAtCallTime() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(ita)
            assertEquals("Marzo 2030", DateFormats.monthYear(YearMonth.of(2030, 3)))
            Locale.setDefault(en)
            assertEquals("March 2030", DateFormats.monthYear(YearMonth.of(2030, 3)))
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test fun relative() {
        val today = LocalDate.of(2026, 11, 10)
        assertEquals(RelativeDay.TODAY, DateFormats.relativeDay(today, today))
        assertEquals(RelativeDay.YESTERDAY, DateFormats.relativeDay(today.minusDays(1), today))
        assertEquals(RelativeDay.OTHER, DateFormats.relativeDay(today.minusDays(2), today))
    }
}
