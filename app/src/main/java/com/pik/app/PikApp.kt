package com.pik.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.reminders.ReminderScheduler
import com.pik.app.reminders.ReminderWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WorkManager is configured here (Configuration.Provider) so that workers can be created by Hilt; the
 * default WorkManager initializer is removed in the manifest.
 */
@HiltAndroidApp
class PikApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var preferences: PikPreferences

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        ReminderWorker.ensureChannel(this)
        // Make sure the daily reminder is scheduled (KEEP: an existing schedule is left alone).
        appScope.launch {
            try {
                reminderScheduler.schedule(preferences.reminderHour.first(), replace = false)
            } catch (e: Exception) {
                // scheduling is best effort; it is tried again the next time the app starts
            }
        }
    }
}
