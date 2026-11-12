package com.pik.app.importing

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.pik.app.data.prefs.PikPreferences
import com.pik.app.data.repo.ImportRepository
import com.pik.app.data.repo.forPackage
import com.pik.app.domain.importing.ImportKeys
import com.pik.app.domain.importing.NotificationParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/**
 * Reads the notifications of the apps the user picked in Settings and stores the payments it finds in the
 * Inbox. It looks only at apps on that list, never sends anything anywhere, and never writes notification
 * text to the log. The user turns it on in the system's notification access settings.
 */
@AndroidEntryPoint
class ImportNotificationListener : NotificationListenerService() {

    @Inject lateinit var imports: ImportRepository
    @Inject lateinit var preferences: PikPreferences

    // goAsync is not available in a service: this scope lives as long as the service and is cancelled in onDestroy.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null || sbn.packageName == packageName) return
        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val body = bigText.ifBlank { text }
        if (title.isBlank() && body.isBlank()) return

        val pkg = sbn.packageName
        val key = sbn.key.orEmpty()
        val postTime = sbn.postTime
        scope.launch {
            try {
                val app = preferences.importApps.first().forPackage(pkg) ?: return@launch
                val date = Instant.ofEpochMilli(postTime).atZone(ZoneId.systemDefault()).toLocalDate()
                val payment = NotificationParser.parse(app.source, title, body, date) ?: return@launch
                val raw = (title + "\n" + body).trim()
                val sourceKey = ImportKeys.notificationKey(pkg, key, postTime, raw)
                imports.addNotification(sourceKey, app.source, app.accountId, payment, date, raw)
            } catch (e: Exception) {
                // A notification that cannot be stored is simply skipped. Nothing is logged: it holds payment details.
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
