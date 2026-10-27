package com.fatlosstrack.data.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.fatlosstrack.ui.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * Hands a tapped reminder from [MainActivity] to the UI.
 *
 * Navigation routes to the Log tab while [pending] is set (once the onboarding
 * gates are done); LogScreen then [take]s it and opens the matching sheet.
 */
object ReminderDeepLink {
    private const val EXTRA_REMINDER = "com.fatlosstrack.extra.REMINDER"

    private val _pending = MutableStateFlow<ReminderType?>(null)
    val pending: StateFlow<ReminderType?> = _pending.asStateFlow()

    fun pendingIntent(context: Context, type: ReminderType): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            // Reuse the running activity (onNewIntent) instead of stacking a second one
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_REMINDER, type.id)
        }
        return PendingIntent.getActivity(
            context,
            type.notificationId,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Picks up a reminder tap from an activity intent. Relaunches from Recents are ignored. */
    fun handleIntent(intent: Intent?) {
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val type = ReminderType.fromId(intent.getStringExtra(EXTRA_REMINDER)) ?: return
        intent.removeExtra(EXTRA_REMINDER)
        _pending.value = type
    }

    /** Returns the pending reminder and clears it in one atomic step. */
    fun take(): ReminderType? = _pending.getAndUpdate { null }
}
