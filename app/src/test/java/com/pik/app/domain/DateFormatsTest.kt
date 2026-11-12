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

    @Test fun ordinalDay() {
        assertEquals("1st", DateFormats.ordinalDay(1, en))
        assertEquals("2nd", DateFormats.ordinalDay(2, en))
        assertEquals("3rd", DateFormats.ordinalDay(3, en))
        assertEquals("5th", DateFormats.ordinalDay(5, en))
        assertEquals("11th", DateFormats.ordinalDay(11, en))
        assertEquals("12th", DateFormats.ordinalDay(12, en))
        assertEquals("13th", DateFormats.ordinalDay(13, en))
        assertEquals("21st", DateFormats.ordinalDay(21, en))
        assertEquals("22nd", DateFormats.ordinalDay(22, en))
        assertEquals("31st", DateFormats.ordinalDay(31, en))
        assertEquals("1\u00B0", DateFormats.ordinalDay(1, ita))
        assertEquals("5", DateFormats.ordinalDay(5, ita))
        assertEquals("31", DateFormats.ordinalDay(31, ita))
    }

    @Test fun weekdayName() {
        assertEquals("Monday", DateFormats.weekdayName(java.time.DayOfWeek.MONDAY, en))
        assertEquals("luned\u00EC", DateFormats.weekdayName(java.time.DayOfWeek.MONDAY, ita))
    }

    @Test fun monthShortIsCapitalisedWithoutDot() {
        assertEquals("Nov", DateFormats.monthShort(java.time.YearMonth.of(2026, 11), en))
        assertEquals("Nov", DateFormats.monthShort(java.time.YearMonth.of(2026, 11), ita))
        assertEquals("Ott", DateFormats.monthShort(java.time.YearMonth.of(2026, 10), ita))
    }
}
