package com.pik.app.domain

import java.time.Duration
import java.time.LocalDateTime

/** When the daily reminder should first run. Pure, so it is unit tested. */
object ReminderTime {
    const val DEFAULT_HOUR = 9

    /** Minutes from [now] until the next [hour]:00 (tomorrow when that time is not in the future), at least 1. */
    fun initialDelayMinutes(now: LocalDateTime, hour: Int): Long {
        var target = now.toLocalDate().atTime(hour.coerceIn(0, 23), 0)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMinutes().coerceAtLeast(1L)
    }
}
