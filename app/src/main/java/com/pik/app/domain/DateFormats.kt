package com.pik.app.domain

import java.time.LocalDate
import java.time.YearMonth

enum class RelativeDay { TODAY, YESTERDAY, OTHER }

/** Locale independent date formatting (English month names). */
object DateFormats {
    private val MONTHS = listOf(
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    )
    private val DAYS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun monthName(month: YearMonth): String = MONTHS[month.monthValue - 1]

    fun monthYear(month: YearMonth): String = "${monthName(month)} ${month.year}"

    private fun shortMonth(monthValue: Int): String = MONTHS[monthValue - 1].take(3)

    /** "3 Nov 2026" */
    fun shortDate(date: LocalDate): String =
        "${date.dayOfMonth} ${shortMonth(date.monthValue)} ${date.year}"

    /** "Tue 10 Nov" */
    fun dayHeader(date: LocalDate): String =
        "${DAYS[date.dayOfWeek.value - 1]} ${date.dayOfMonth} ${shortMonth(date.monthValue)}"

    fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay = when (date) {
        today -> RelativeDay.TODAY
        today.minusDays(1) -> RelativeDay.YESTERDAY
        else -> RelativeDay.OTHER
    }
}
