package com.pik.app.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.pik.app.MainActivity
import com.pik.app.R
import com.pik.app.data.repo.RecurringRepository
import com.pik.app.domain.Money
import com.pik.app.domain.RecurringPlanner
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Runs about once a day. Posts one notification that lists what is due tomorrow (rules with "remind me the day
 * before") and how many items are overdue. Posts nothing when there is nothing due tomorrow, or when the user did
 * not allow notifications.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: RecurringRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val rules = repository.getActiveRules()
        val summary = RecurringPlanner.reminders(rules.map { it.toFacts() }, LocalDate.now())
        if (summary.isEmpty || !ReminderPermissions.canNotify(appContext)) return Result.success()

        val names = rules.associate { it.id to it.name }
        val lines = summary.tomorrow.map { item ->
            appContext.getString(R.string.reminder_line, names[item.ruleId].orEmpty(), Money.format(item.amountCents))
        }
        val title = appContext.getString(R.string.reminder_title_tomorrow)
        val overdueText = if (summary.overdueCount > 0) {
            appContext.getString(R.string.reminder_overdue, summary.overdueCount)
        } else {
            null
        }
        val body = (lines + listOfNotNull(overdueText)).joinToString("\n")

        ensureChannel(appContext)
        val openIntent = Intent(appContext, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_RECURRING)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pending = PendingIntent.getActivity(
            appContext, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(lines.firstOrNull() ?: body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            NotificationManagerCompat.from(appContext).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // permission revoked between the check and the call: nothing to do
        }
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "reminders"
        private const val NOTIFICATION_ID = 1001

        /** Creates the "Reminders" channel (a no-op when it exists). Android 8+ needs it before posting. */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < 26) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.reminder_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.reminder_channel_description) },
            )
        }
    }
}
