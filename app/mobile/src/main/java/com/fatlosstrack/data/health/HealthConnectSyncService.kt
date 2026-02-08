package com.fatlosstrack.data.health

import android.util.Log
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.data.local.db.WeightEntry
import com.fatlosstrack.data.local.db.WeightSource
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
) {
    companion object {
        private const val TAG = "HCSyncService"
    }

    /**
     * Sync the last [days] days of data from Health Connect.
     * Returns the number of days updated.
     */
    suspend fun syncRecentDays(days: Int = 7): Int {
        if (!hcManager.isAvailable()) return 0
        if (!hcManager.hasAllPermissions()) return 0

        val today = LocalDate.now()
        val from = today.minusDays(days.toLong() - 1)
        var updated = 0

        Log.d(TAG, "Syncing $days days from $from to $today")

        val summaries = hcManager.getSummaries(from, today)
        for (summary in summaries) {
            if (mergeSummary(summary)) updated++
        }

        Log.d(TAG, "Sync complete: $updated days updated")
        return updated
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
                    weightKg = existing.weightKg ?: summary.weightKg,
                    steps = existing.steps ?: summary.steps,
                    sleepHours = existing.sleepHours ?: summary.sleepHours,
                    restingHr = existing.restingHr ?: summary.restingHr,
                    exercisesJson = existing.exercisesJson ?: summary.exercisesJson,
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
            dailyLogDao.upsert(merged)

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
            return false
        }
    }
}
