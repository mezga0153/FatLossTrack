package com.fatlosstrack.data.reminders

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules the daily reminders with WorkManager.
 *
 * Each reminder is a one-time [ReminderWorker] run under its own unique-work
 * name; every run queues the next day's run before it checks anything, so the
 * chain keeps going. One-time runs (rather than periodic work) keep the time
 * exact across DST switches and settings changes.
 *
 * Settings changes go through [setEnabled] / [setMinutes] so the preference
 * write and the reschedule happen together, even if the screen is left.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val appLogger: AppLogger,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Makes sure every enabled reminder has a pending run. Safe to call on every app start. */
    fun ensureScheduled() {
        appScope.launch {
            ReminderType.entries.forEach { type ->
                if (preferencesManager.reminderEnabled(type.id).first()) {
                    enqueue(type, minutesFor(type), ExistingWorkPolicy.KEEP)
                }
            }
        }
    }

    fun setEnabled(type: ReminderType, enabled: Boolean) {
        appScope.launch {
            preferencesManager.setReminderEnabled(type.id, enabled)
            appLogger.user("Reminder ${type.id} ${if (enabled) "enabled" else "disabled"}")
            apply(type)
        }
    }

    fun setMinutes(type: ReminderType, minutes: Int) {
        appScope.launch {
            preferencesManager.setReminderMinutes(type.id, minutes)
            appLogger.user("Reminder ${type.id} time set to ${ReminderTiming.timeOf(minutes)}")
            apply(type)
        }
    }

    /**
     * Queues the run after the one in progress — called from [ReminderWorker].
     * Appends instead of replacing, since replacing would cancel the running worker.
     */
    suspend fun scheduleNextRun(type: ReminderType) {
        enqueue(type, minutesFor(type), ExistingWorkPolicy.APPEND_OR_REPLACE)
    }

    /** Re-applies the stored settings of [type]: replaces its pending run, or cancels it when off. */
    private suspend fun apply(type: ReminderType) {
        if (preferencesManager.reminderEnabled(type.id).first()) {
            enqueue(type, minutesFor(type), ExistingWorkPolicy.REPLACE)
        } else {
            workManager.cancelUniqueWork(type.workName)
        }
    }

    private suspend fun minutesFor(type: ReminderType): Int =
        preferencesManager.reminderMinutes(type.id, type.defaultMinutes).first()

    private fun enqueue(type: ReminderType, minutes: Int, policy: ExistingWorkPolicy) {
        val delay = ReminderTiming.delayUntilNext(ZonedDateTime.now(), minutes)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(ReminderWorker.KEY_TYPE to type.id))
            .build()
        workManager.enqueueUniqueWork(type.workName, policy, request)
    }
}
