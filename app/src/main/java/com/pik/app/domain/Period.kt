package com.pik.app.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * A "payday month": [start]..[endInclusive], both calendar days. With startDay 1 it is a
 * calendar month; with startDay 27 it runs from the 27th of one month to the 26th of the next.
 * startDay is limited to 1..28 so the start day exists in every month.
 */
data class Period(val start: LocalDate, val startDay: Int) {

    /** Last day of the period (inclusive). */
    val endInclusive: LocalDate get() = start.plusMonths(1).minusDays(1)

    /** The calendar month (yyyy-MM) the period starts in. Budgets are keyed by it. */
    val key: String get() = periodKey(YearMonth.from(start))

    val lengthDays: Int get() = (endInclusive.toEpochDay() - start.toEpochDay() + 1).toInt()

    operator fun contains(date: LocalDate): Boolean = date >= start && date <= endInclusive

    fun previous(): Period = Period(start.minusMonths(1), startDay)

    fun next(): Period = Period(start.plusMonths(1), startDay)

    /**
     * Days left in the period counting [today] itself: the last day returns 1, a day after the
     * period returns 0, a day before the period returns the whole length.
     */
    fun daysLeft(today: LocalDate): Int = when {
        today > endInclusive -> 0
        today < start -> lengthDays
        else -> (endInclusive.toEpochDay() - today.toEpochDay() + 1).toInt()
    }

    /** "November" when startDay is 1, otherwise "27 Oct - 26 Nov" (with an en dash). */
    fun label(): String =
        if (startDay == 1) {
            DateFormats.monthName(YearMonth.from(start))
        } else {
            "${DateFormats.dayMonth(start)} \u2013 ${DateFormats.dayMonth(endInclusive)}"
        }

    companion object {
        const val MIN_START_DAY = 1
        const val MAX_START_DAY = 28

        fun clampStartDay(day: Int): Int = day.coerceIn(MIN_START_DAY, MAX_START_DAY)

        /** The period that contains [date]. */
        fun containing(date: LocalDate, startDay: Int): Period {
            val day = clampStartDay(startDay)
            val month = YearMonth.from(date)
            val startMonth = if (date.dayOfMonth >= day) month else month.minusMonths(1)
            return Period(startMonth.atDay(day), day)
        }

        fun periodKey(month: YearMonth): String =
            "${month.year.toString().padStart(4, '0')}-${month.monthValue.toString().padStart(2, '0')}"
    }
}
