package com.fatlosstrack.data.recap

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.fatlosstrack.R
import com.fatlosstrack.domain.recap.WeeklyRecap
import com.fatlosstrack.ui.MainActivity
import java.time.LocalDate
import java.util.Locale

/**
 * Posts the weekly recap notification and maps its tap back to the recap route.
 * Has its own channel so it can be muted independently of other notifications.
 */
object WeeklyRecapNotifier {

    const val CHANNEL_ID = "weekly_recap"
    const val ROUTE = "weekly_recap"
    private const val NOTIFICATION_ID = 41_001
    private const val EXTRA_RECAP_END = "com.fatlosstrack.extra.WEEKLY_RECAP_END"

    /** Route for the recap screen; [end] pins the window to the notified week. */
    fun recapRoute(end: LocalDate? = null): String = if (end != null) "$ROUTE?end=$end" else ROUTE

    /**
     * The recap route a notification tap asked for, or null for any other launch.
     * Ignores relaunches from Recents, which replay the original intent.
     */
    fun routeFrom(intent: Intent?): String? {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return null
        val end = intent.getStringExtra(EXTRA_RECAP_END) ?: return null
        return runCatching { LocalDate.parse(end) }.getOrNull()?.let { recapRoute(it) }
    }

    /** True when the app may post notifications (runtime permission on Android 13+ and not blocked). */
    fun canPost(context: Context): Boolean {
        val granted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    /**
     * Post the recap. [language] is the in-app language: before Android 13 the per-app
     * locale only reaches activities, so the worker's context would otherwise be English.
     * Returns false when notifications are not allowed.
     */
    fun show(context: Context, recap: WeeklyRecap, language: String): Boolean {
        if (!canPost(context)) return false
        val res = localized(context, language)
        ensureChannel(context, res)

        val parts = summaryParts(res, recap)
        val tapHint = res.getString(R.string.recap_notification_tap)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_RECAP_END, recap.end.toString())
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_recap)
            .setContentTitle(res.getString(R.string.recap_notification_title))
            .setContentText(parts.take(3).joinToString(" · ").ifEmpty { tapHint })
            .setStyle(NotificationCompat.BigTextStyle().bigText((parts + tapHint).joinToString("\n")))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
            true
        } catch (_: SecurityException) {
            false // permission revoked between the check and the post
        }
    }

    private fun summaryParts(res: Context, recap: WeeklyRecap): List<String> = buildList {
        recap.trendChangeKg?.let { add(res.getString(R.string.recap_notif_trend, "%+.1f".format(it))) }
        recap.avgKcal?.let { avg ->
            add(
                if (recap.dailyTargetKcal != null) {
                    res.getString(R.string.recap_notif_kcal_target, "%,d".format(avg), "%,d".format(recap.dailyTargetKcal))
                } else {
                    res.getString(R.string.recap_notif_kcal, "%,d".format(avg))
                },
            )
        }
        recap.daysOnTarget?.takeIf { recap.daysWithMeals > 0 }?.let {
            add(res.getString(R.string.recap_notif_on_target, it, recap.daysWithMeals))
        }
        recap.avgSteps?.let { add(res.getString(R.string.recap_notif_steps, "%,d".format(it))) }
        recap.avgSleepHours?.let { add(res.getString(R.string.recap_notif_sleep, "%.1f".format(it))) }
    }

    private fun ensureChannel(context: Context, res: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            res.getString(R.string.recap_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = res.getString(R.string.recap_channel_description) }
        // Re-creating an existing channel only refreshes its name/description
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun localized(context: Context, language: String): Context {
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(language))
        return context.createConfigurationContext(config)
    }
}
