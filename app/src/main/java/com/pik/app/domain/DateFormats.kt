package com.pik.app.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

enum class RelativeDay { TODAY, YESTERDAY, OTHER }

/**
 * Date formatting with the names of a [Locale] (default: the app's current locale, read at call time,
 * so a language change shows up as soon as the screen is drawn again). Month names that start a
 * heading ([monthName], [monthYear], [dayHeader]) are capitalised, because Italian writes months in
 * lower case ("novembre"). Inline dates ([shortDate], [dayMonth]) keep the locale's own case ("3 nov 2026").
 */
object DateFormats {
    private fun capitalise(text: String, locale: Locale): String =
        text.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    private fun shortMonth(month: Month, locale: Locale): String =
        month.getDisplayName(TextStyle.SHORT, locale).trimEnd('.')

    /** "November" / "Novembre" */
    fun monthName(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        capitalise(month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale), locale)

    /** "Nov" / "Nov": a short month name for a chart label (capitalised, no dot). */
    fun monthShort(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        capitalise(shortMonth(month.month, locale), locale)

    /** "November 2026" / "Novembre 2026" */
    fun monthYear(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        "${monthName(month, locale)} ${month.year}"

    /** For the middle of a sentence: "November 2026" / "novembre 2026" (the locale's own case). */
    fun monthYearInline(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale)} ${month.year}"

    /** "3 Nov 2026" / "3 nov 2026" */
    fun shortDate(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        "${date.dayOfMonth} ${shortMonth(date.month, locale)} ${date.year}"

    /** "27 Oct" / "27 ott" */
    fun dayMonth(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        "${date.dayOfMonth} ${shortMonth(date.month, locale)}"

    /** "Tue 10 Nov" / "Mar 10 nov" */
    fun dayHeader(date: LocalDate, locale: Locale = Locale.getDefault()): String {
        val day: DayOfWeek = date.dayOfWeek
        return "${capitalise(day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.'), locale)} " +
            "${date.dayOfMonth} ${shortMonth(date.month, locale)}"
    }

    /** The day of the month as a short ordinal: "1st", "2nd", "3rd", "5th", "11th" / "1°", "5". */
    fun ordinalDay(day: Int, locale: Locale = Locale.getDefault()): String {
        if (locale.language == "it") return if (day == 1) "1\u00B0" else day.toString()
        val suffix = when {
            day % 100 in 11..13 -> "th"
            day % 10 == 1 -> "st"
            day % 10 == 2 -> "nd"
            day % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$day$suffix"
    }

    /** "Mon" / "lun": a short weekday name for a week strip. */
    fun weekdayShort(day: DayOfWeek, locale: Locale = Locale.getDefault()): String =
        capitalise(day.getDisplayName(TextStyle.SHORT, locale).trimEnd('.'), locale)

    /** "Monday" / "lunedì": the weekday name in the middle of a sentence (the locale's own case). */
    fun weekdayName(day: DayOfWeek, locale: Locale = Locale.getDefault()): String =
        day.getDisplayName(TextStyle.FULL, locale)

    fun relativeDay(date: LocalDate, today: LocalDate): RelativeDay = when (date) {
        today -> RelativeDay.TODAY
        today.minusDays(1) -> RelativeDay.YESTERDAY
        else -> RelativeDay.OTHER
    }
}
