package com.fatlosstrack.data.reminders

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fatlosstrack.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Posts reminder notifications on the "logging reminders" channel. */
@Singleton
class ReminderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "logging_reminders"
    }

    /** Whether the app may post notifications (always true below Android 13). */
    fun canNotify(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    fun show(type: ReminderType, title: String, text: String) {
        if (!canNotify()) return
        val manager = NotificationManagerCompat.from(context)
        // Creating an existing channel is a no-op, but it refreshes the localized name
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName(context.getString(R.string.reminders_channel_name))
                .setDescription(context.getString(R.string.reminders_channel_desc))
                .build(),
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_reminder)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(ReminderDeepLink.pendingIntent(context, type))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(type.notificationId, notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post
        }
    }
}
