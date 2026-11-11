package com.pik.app.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Can Pik post notifications right now? (Android 13+ needs the runtime permission, and the user may mute the app.) */
object ReminderPermissions {

    /** True on Android 13+, where POST_NOTIFICATIONS must be requested at runtime. */
    val needsRuntimePermission: Boolean get() = Build.VERSION.SDK_INT >= 33

    fun canNotify(context: Context): Boolean {
        if (needsRuntimePermission &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
}
