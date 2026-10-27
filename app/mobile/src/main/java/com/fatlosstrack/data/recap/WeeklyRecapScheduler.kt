package com.fatlosstrack.data.recap

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.fatlosstrack.data.local.PreferencesManager
import kotlinx.coroutines.flow.first
import java.time.DayOfWeek
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit

/**
 * Schedules [WeeklyRecapWorker] for the configured day and time.
 *
 * Uses a one-time request per week rather than a periodic one: each run
 * enqueues the next, so the notification stays on the chosen wall-clock time
 * across DST changes instead of drifting.
 */
object WeeklyRecapScheduler {

    private const val WORK_NAME = "weekly_recap"

    /** After a settings change: replace whatever is queued with the new schedule. */
    suspend fun reschedule(context: Context, prefs: PreferencesManager) =
        schedule(context, prefs, ExistingWorkPolicy.REPLACE)

    /** On app start: queue a run if one is missing (e.g. a previous run failed), never move an existing one. */
    suspend fun ensureScheduled(context: Context, prefs: PreferencesManager) =
        schedule(context, prefs, ExistingWorkPolicy.KEEP)

    /**
     * From inside the running worker: chain the next week's run after this one.
     * REPLACE would cancel the worker that is calling it.
     */
    internal suspend fun scheduleNextRun(context: Context, prefs: PreferencesManager) =
        schedule(context, prefs, ExistingWorkPolicy.APPEND_OR_REPLACE)

    private suspend fun schedule(context: Context, prefs: PreferencesManager, policy: ExistingWorkPolicy) {
        val workManager = WorkManager.getInstance(context)
        if (!prefs.weeklyRecapEnabled.first()) {
            workManager.cancelUniqueWork(WORK_NAME)
            return
        }
        val now = ZonedDateTime.now()
        val next = nextRun(now, DayOfWeek.of(prefs.weeklyRecapDay.first()), prefs.weeklyRecapMinute.first())
        val request = OneTimeWorkRequestBuilder<WeeklyRecapWorker>()
            .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
            .build()
        workManager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    /** The first [day] at [minuteOfDay] strictly after [now], in [now]'s zone. */
    internal fun nextRun(now: ZonedDateTime, day: DayOfWeek, minuteOfDay: Int): ZonedDateTime {
        val candidate = now.with(TemporalAdjusters.nextOrSame(day))
            .toLocalDate()
            .atTime(minuteOfDay / 60, minuteOfDay % 60)
            .atZone(now.zone)
        return if (candidate.isAfter(now)) candidate else candidate.plusWeeks(1)
    }
}
