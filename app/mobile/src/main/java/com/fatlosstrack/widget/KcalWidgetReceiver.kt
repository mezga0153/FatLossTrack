package com.fatlosstrack.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

/**
 * Hosts [KcalWidget] and keeps it on the right day: an inexact alarm at the next
 * midnight resets "kcal left today", and clock/timezone changes refresh it too.
 * The alarm re-arms itself on every refresh and on each system widget update
 * (including the one sent after boot, which clears alarms).
 */
class KcalWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = KcalWidget()

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        scheduleMidnightRefresh(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        // Last widget removed — nothing left to refresh
        alarmManager(context).cancel(midnightIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_MIDNIGHT_REFRESH, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> {
                scheduleMidnightRefresh(context)
                val pending = goAsync()
                CoroutineScope(Dispatchers.Default).launch {
                    try {
                        glanceAppWidget.updateAll(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private fun scheduleMidnightRefresh(context: Context) {
        val nextMidnight = LocalDate.now().plusDays(1)
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        // Inexact window (no exact-alarm permission needed); non-wakeup, so a sleeping
        // device refreshes as soon as it wakes — i.e. before anyone looks at the widget
        alarmManager(context).setWindow(
            AlarmManager.RTC,
            nextMidnight,
            WINDOW_MS,
            midnightIntent(context),
        )
    }

    private fun alarmManager(context: Context): AlarmManager =
        context.getSystemService(AlarmManager::class.java)

    private fun midnightIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, KcalWidgetReceiver::class.java).setAction(ACTION_MIDNIGHT_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        private const val ACTION_MIDNIGHT_REFRESH = "com.fatlosstrack.widget.MIDNIGHT_REFRESH"
        private const val WINDOW_MS = 10 * 60 * 1000L
    }
}
