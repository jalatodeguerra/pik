package com.pik.app.lock

import com.pik.app.data.prefs.PikPreferences
import com.pik.app.domain.LockPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The fingerprint / phone PIN lock. The app starts locked when the lock is on, and locks again when
 * it comes back after more than [LockPolicy.GRACE_MILLIS] in the background. While the lock is on the
 * window is marked secure (see MainActivity) so Recent apps and screenshots show nothing.
 * This only hides Pik's screens: the data file itself is not encrypted.
 */
@Singleton
class AppLock @Inject constructor(
    private val prefs: PikPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _enabled = MutableStateFlow<Boolean?>(null)

    /** Whether the lock is switched on; null until the setting is read. */
    val enabled: StateFlow<Boolean?> = _enabled

    private val unlocked = MutableStateFlow(false)
    private var backgroundedAtMillis: Long? = null

    /** true = show the lock screen, false = show the app, null = still reading the setting. */
    val locked: StateFlow<Boolean?> = combine(_enabled, unlocked) { enabled, open ->
        enabled?.let { it && !open }
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch { prefs.lockEnabled.collect { _enabled.value = it } }
    }

    /** The user passed the fingerprint / PIN check. */
    fun markUnlocked() {
        unlocked.value = true
    }

    /** Turns the lock on after a successful check; the app stays open for now. */
    suspend fun enable() {
        prefs.setLockEnabled(true)
        unlocked.value = true
    }

    suspend fun disable() {
        prefs.setLockEnabled(false)
    }

    /** Call from Activity.onStop with a monotonic clock (SystemClock.elapsedRealtime). */
    fun onBackgrounded(nowMillis: Long) {
        backgroundedAtMillis = nowMillis
    }

    /** Call from Activity.onStart. Locks again when the app was away for longer than the grace time. */
    fun onForegrounded(nowMillis: Long) {
        if (_enabled.value == true && LockPolicy.shouldRelock(backgroundedAtMillis, nowMillis)) {
            unlocked.value = false
        }
        backgroundedAtMillis = null
    }
}
