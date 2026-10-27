package com.fatlosstrack.ui

import android.content.Context
import android.content.Intent

/**
 * Quick-logging entry points opened from the home screen widget and the static
 * launcher shortcuts (res/xml/shortcuts.xml — keep the action strings in sync).
 */
enum class QuickAction(val intentAction: String) {
    /** Open the camera in Log mode (`capture/log`). */
    SnapMeal("com.fatlosstrack.action.SNAP_MEAL"),

    /** Focus the AI bar with the keyboard up, ready to type a meal. */
    LogMeal("com.fatlosstrack.action.LOG_MEAL");

    /**
     * Explicit [MainActivity] intent for this action. CLEAR_TOP + the activity's
     * singleTop launch mode deliver it to a running instance via onNewIntent.
     */
    fun intent(context: Context): Intent =
        Intent(context, MainActivity::class.java)
            .setAction(intentAction)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

    companion object {
        fun fromIntent(intent: Intent?): QuickAction? =
            entries.firstOrNull { it.intentAction == intent?.action }
    }
}
