package com.pik.app.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LockPolicyTest {
    @Test
    fun neverBackgroundedDoesNotRelock() {
        assertFalse(LockPolicy.shouldRelock(null, 1_000_000))
    }

    @Test
    fun shortAbsenceKeepsTheApp_unlocked() {
        assertFalse(LockPolicy.shouldRelock(1_000, 1_000 + 59_000))
        assertFalse(LockPolicy.shouldRelock(1_000, 1_000 + 60_000))
    }

    @Test
    fun longerThanAMinuteRelocks() {
        assertTrue(LockPolicy.shouldRelock(1_000, 1_000 + 60_001))
        assertTrue(LockPolicy.shouldRelock(0, 10 * 60_000))
    }
}
