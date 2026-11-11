package com.pik.app.reminders

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.pik.app.domain.ReminderTime
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminder worker. One unique periodic work ([WORK_NAME]) that repeats every 24 hours;
 * the first run is delayed until the chosen hour. Android may run it later (Doze, battery savers on some
 * phones), which the Settings screen says.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * [replace] false (app start): keep an already scheduled work as it is. [replace] true (the reminder time
     * changed): update the existing work with the new delay.
     */
    fun schedule(hour: Int, replace: Boolean) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(
            REPEAT_HOURS, TimeUnit.HOURS,
            FLEX_HOURS, TimeUnit.HOURS,
        )
            .setInitialDelay(ReminderTime.initialDelayMinutes(LocalDateTime.now(), hour), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    companion object {
        const val WORK_NAME = "pik_daily_reminder"
        private const val REPEAT_HOURS = 24L
        private const val FLEX_HOURS = 2L
    }
}
