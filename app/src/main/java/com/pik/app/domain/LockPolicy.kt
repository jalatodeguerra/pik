package com.pik.app.domain

/** When the fingerprint lock asks again. Pure, so it can be tested without Android. */
object LockPolicy {
    /** Coming back after longer than this locks the app again. */
    const val GRACE_MILLIS: Long = 60_000L

    /**
     * [backgroundedAtMillis] is when the app left the screen (null if it did not, for example a
     * fresh start, which is handled by the app starting locked). Times come from one monotonic clock.
     */
    fun shouldRelock(backgroundedAtMillis: Long?, nowMillis: Long, graceMillis: Long = GRACE_MILLIS): Boolean {
        if (backgroundedAtMillis == null) return false
        return nowMillis - backgroundedAtMillis > graceMillis
    }
}
