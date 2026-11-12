package com.pik.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ReminderTimeTest {
    private fun at(h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, 11, h, m)

    @Test fun laterTodayWaitsUntilThen() {
        assertEquals(90L, ReminderTime.initialDelayMinutes(at(7, 30), 9))
    }

    @Test fun alreadyPassedWaitsUntilTomorrow() {
        assertEquals(23 * 60L, ReminderTime.initialDelayMinutes(at(10, 0), 9))
        assertEquals(24 * 60L - 30, ReminderTime.initialDelayMinutes(at(9, 30), 9))
    }

    @Test fun exactlyTheHourMeansTomorrow() {
        assertEquals(24 * 60L, ReminderTime.initialDelayMinutes(at(9, 0), 9))
    }

    @Test fun midnightAndLateHours() {
        assertEquals(60L, ReminderTime.initialDelayMinutes(at(23, 0), 0))
        assertEquals(60L, ReminderTime.initialDelayMinutes(at(22, 0), 23))
    }

    @Test fun hourIsClamped() {
        assertEquals(ReminderTime.initialDelayMinutes(at(1), 23), ReminderTime.initialDelayMinutes(at(1), 99))
    }
}
