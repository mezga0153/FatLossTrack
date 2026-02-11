package com.fatlosstrack.data.health

import android.util.Log
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.data.local.db.WeightEntry
import com.fatlosstrack.data.local.db.WeightSource
import com.fatlosstrack.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs Health Connect data into Room DailyLog + WeightEntry tables.
 * Merges with existing manual entries — HC data fills in only null fields.
 */
@Singleton
class HealthConnectSyncService @Inject constructor(
    private val hcManager: HealthConnectManager,
    private val dailyLogDao: DailyLogDao,
    private val weightDao: WeightDao,
    private val appLogger: AppLogger,
    private val daySummaryGenerator: DaySummaryGenerator,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    companion object {
        private const val TAG = "HCSyncService"
    }

    /**
     * Sync the last [days] days of data from Health Connect.
     * Returns the list of dates that were updated.
     */
    suspend fun syncRecentDays(days: Int = 7): List<LocalDate> {
        if (!hcManager.isAvailable()) {
            appLogger.hc("Sync skipped — HC not available")
            return emptyList()
        }
        if (!hcManager.hasAllPermissions()) {
            appLogger.hc("Sync skipped — missing permissions")
            return emptyList()
        }

        val today = LocalDate.now()
        val from = today.minusDays(days.toLong() - 1)
        val updatedDates = mutableListOf<LocalDate>()

        appLogger.hc("Starting sync: $days days ($from → $today)")
        Log.d(TAG, "Syncing $days days from $from to $today")

        val summaries = hcManager.getSummaries(from, today)
        for (summary in summaries) {
            if (mergeSummary(summary)) updatedDates.add(summary.date)
        }

        Log.d(TAG, "Sync complete: ${updatedDates.size} days updated")
        appLogger.hc("Sync complete: ${updatedDates.size}/$days days had data")
        return updatedDates
    }

    /**
     * Fire-and-forget: syncs recent days on the application scope and
     * triggers summary generation for any dates that changed.
     */
    fun launchSync(days: Int = 7, reason: String = "unknown") {
        appScope.launch {
            val changedDates = syncRecentDays(days)
            if (changedDates.isNotEmpty()) {
                daySummaryGenerator.launchForDates(changedDates, reason)
            }
        }
    }

    /**
     * Sync a single date.
     */
    suspend fun syncDate(date: LocalDate): Boolean {
        if (!hcManager.isAvailable()) return false
        if (!hcManager.hasAllPermissions()) return false
        return mergeSummary(hcManager.getDaySummary(date))
    }

    /**
     * Merge a DaySummary into the existing DailyLog.
     * Only overwrites fields that are currently null (manual entries take priority).
     */
    private suspend fun mergeSummary(summary: DaySummary): Boolean {
        val hasData = summary.weightKg != null || summary.steps != null ||
                summary.sleepHours != null || summary.restingHr != null ||
                summary.exercisesJson != null

        if (!hasData) return false

        try {
            val existing = dailyLogDao.getForDate(summary.date)
            val merged = if (existing != null) {
                existing.copy(
                    weightKg = summary.weightKg ?: existing.weightKg,
                    steps = summary.steps ?: existing.steps,
                    sleepHours = summary.sleepHours ?: existing.sleepHours,
                    restingHr = summary.restingHr ?: existing.restingHr,
                    exercisesJson = summary.exercisesJson ?: existing.exercisesJson,
                )
            } else {
                DailyLog(
                    date = summary.date,
                    weightKg = summary.weightKg,
                    steps = summary.steps,
                    sleepHours = summary.sleepHours,
                    restingHr = summary.restingHr,
                    exercisesJson = summary.exercisesJson,
                )
            }

            // Check if data actually changed (compare relevant fields, ignore daySummary/notes/offPlan)
            val actuallyChanged = existing == null ||
                    existing.weightKg != merged.weightKg ||
                    existing.steps != merged.steps ||
                    existing.sleepHours != merged.sleepHours ||
                    existing.restingHr != merged.restingHr ||
                    existing.exercisesJson != merged.exercisesJson

            if (!actuallyChanged) {
                appLogger.hc("${summary.date}: HC data unchanged, skipping")
                return false
            }

            dailyLogDao.upsert(merged)

            val parts = mutableListOf<String>()
            summary.weightKg?.let { parts += "weight=%.1f kg".format(it) }
            summary.steps?.let { parts += "steps=$it" }
            summary.sleepHours?.let { parts += "sleep=${it}h" }
            summary.restingHr?.let { parts += "hr=${it} bpm" }
            summary.exercisesJson?.let { parts += "exercises" }
            val isNew = existing == null
            appLogger.hc("${summary.date}: ${if (isNew) "created" else "merged"} — ${parts.joinToString(", ")}")

            // Also save weight to weight_entries if present
            if (summary.weightKg != null) {
                weightDao.insert(
                    WeightEntry(
                        date = summary.date,
                        valueKg = summary.weightKg,
                        source = WeightSource.HEALTH_CONNECT,
                    )
                )
            }

            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to merge summary for ${summary.date}", e)
            appLogger.error("HC", "Failed to merge ${summary.date}", e)
            return false
        }
    }
}
