package com.fatlosstrack.data.health

import android.util.Log
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.BloodGlucoseDao
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.data.local.db.WeightEntry
import com.fatlosstrack.data.local.db.WeightSource
import com.fatlosstrack.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Syncs Health Connect data into Room DailyLog + WeightEntry + BloodGlucoseEntry tables.
 * Merges with existing manual entries — HC data fills in only null fields.
 */
@Singleton
class HealthConnectSyncService @Inject constructor(
    private val hcManager: HealthConnectManager,
    private val dailyLogDao: DailyLogDao,
    private val weightDao: WeightDao,
    private val bloodGlucoseDao: BloodGlucoseDao,
    private val appLogger: AppLogger,
    private val daySummaryGenerator: DaySummaryGenerator,
    private val preferencesManager: PreferencesManager,
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

        // Use the most recent weight before the sync window as reference for filtering
        // out weigh-ins from other people on shared scales (picks the value closest to
        // the user's own previous weight when multiple records exist for a day).
        val referenceWeight = weightDao.getMostRecentBefore(from)?.valueKg
        if (referenceWeight != null) {
            appLogger.hc("Sync reference weight: %.1f kg (from before $from)".format(referenceWeight))
        }

        val summaries = hcManager.getSummaries(from, today, referenceWeight)
        for (summary in summaries) {
            if (mergeSummary(summary)) updatedDates.add(summary.date)
        }

        // Sync raw blood glucose readings (replace HC readings for the window)
        try {
            val bgFrom = from.atStartOfDay(ZoneId.systemDefault()).toInstant()
            val bgTo = today.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
            bloodGlucoseDao.deleteHcReadingsBetween(bgFrom, bgTo)
            val readings = hcManager.getBloodGlucoseReadings(from, today)
            if (readings.isNotEmpty()) {
                bloodGlucoseDao.insertAll(readings)
                appLogger.hc("Synced ${readings.size} raw blood glucose readings")
            }
        } catch (e: Exception) {
            appLogger.hc("Blood glucose raw sync error: ${e.message}")
        }

        Log.d(TAG, "Sync complete: ${updatedDates.size} days updated")
        appLogger.hc("Sync complete: ${updatedDates.size}/$days days had data")
        return updatedDates
    }

    /**
     * Fire-and-forget: syncs from the goal start date (or 365 days as fallback) to today.
     */
    fun launchSyncFromStart() {
        appScope.launch {
            val startDateStr = preferencesManager.startDate.first()
            val days = if (startDateStr != null) {
                val start = runCatching { LocalDate.parse(startDateStr) }.getOrNull()
                if (start != null) {
                    (ChronoUnit.DAYS.between(start, LocalDate.now()) + 1).toInt().coerceAtLeast(7)
                } else 365
            } else 365
            appLogger.hc("Full sync: $days days back (start=$startDateStr)")
            val changedDates = syncRecentDays(days)
            if (changedDates.isNotEmpty()) {
                daySummaryGenerator.launchForDates(changedDates, "fullHcSync")
            }
        }
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
     * One-time cleanup: for any date that has multiple weight_entries rows, keep the one
     * closest to the previous day's value and delete the rest.
     * Runs quickly in-memory — no network calls.
     */
    suspend fun cleanupDuplicateWeights() {
        val all = weightDao.getAllEntriesSnapshot()
        // Group by date; only care about dates with duplicates
        val byDate = all.groupBy { it.date }.filter { it.value.size > 1 }
        if (byDate.isEmpty()) return

        appLogger.hc("Weight dedup: ${byDate.size} dates with duplicates")
        val toDelete = mutableListOf<com.fatlosstrack.data.local.db.WeightEntry>()

        // Build a running map of the last accepted weight so we can resolve chains
        val accepted = mutableMapOf<java.time.LocalDate, Double>()
        // Pre-seed with all non-duplicate dates
        all.filter { entry -> byDate[entry.date] == null }.forEach { accepted[it.date] = it.valueKg }

        for ((date, entries) in byDate.entries.sortedBy { it.key }) {
            // Find reference: most recent accepted weight before this date
            val ref = accepted.entries
                .filter { it.key.isBefore(date) }
                .maxByOrNull { it.key }
                ?.value

            val keeper = if (ref != null) {
                entries.minByOrNull { kotlin.math.abs(it.valueKg - ref) }!!
            } else {
                // No prior data — prefer MANUAL source, otherwise last entry
                entries.firstOrNull { it.source == com.fatlosstrack.data.local.db.WeightSource.MANUAL }
                    ?: entries.last()
            }

            toDelete += entries.filter { it.id != keeper.id }
            accepted[date] = keeper.valueKg
            appLogger.hc("  $date: kept %.1f kg (ref=${ref?.let { "%.1f".format(it) } ?: "none"}), deleted ${entries.size - 1}".format(keeper.valueKg))
        }

        if (toDelete.isNotEmpty()) {
            weightDao.deleteEntries(toDelete)
            appLogger.hc("Weight dedup: deleted ${toDelete.size} duplicate entries")
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
                summary.exercisesJson != null || summary.bodyFatPct != null ||
                summary.bodyWaterKg != null || summary.leanBodyMassKg != null ||
                summary.boneMassKg != null || summary.bloodSugarMmol != null

        if (!hasData) return false

        try {
            val existing = dailyLogDao.getForDate(summary.date)
            val merged = if (existing != null) {
                existing.copy(
                    weightKg = if (existing.weightLocked) existing.weightKg else summary.weightKg ?: existing.weightKg,
                    steps = if (existing.stepsLocked) existing.steps else summary.steps ?: existing.steps,
                    sleepHours = summary.sleepHours ?: existing.sleepHours,
                    restingHr = summary.restingHr ?: existing.restingHr,
                    exercisesJson = summary.exercisesJson ?: existing.exercisesJson,
                    bodyFatPct = summary.bodyFatPct ?: existing.bodyFatPct,
                    bodyWaterKg = summary.bodyWaterKg ?: existing.bodyWaterKg,
                    leanBodyMassKg = summary.leanBodyMassKg ?: existing.leanBodyMassKg,
                    boneMassKg = summary.boneMassKg ?: existing.boneMassKg,
                    bloodSugarMmol = summary.bloodSugarMmol ?: existing.bloodSugarMmol,
                )
            } else {
                DailyLog(
                    date = summary.date,
                    weightKg = summary.weightKg,
                    steps = summary.steps,
                    sleepHours = summary.sleepHours,
                    restingHr = summary.restingHr,
                    exercisesJson = summary.exercisesJson,
                    bodyFatPct = summary.bodyFatPct,
                    bodyWaterKg = summary.bodyWaterKg,
                    leanBodyMassKg = summary.leanBodyMassKg,
                    boneMassKg = summary.boneMassKg,
                    bloodSugarMmol = summary.bloodSugarMmol,
                )
            }

            // Check if data actually changed (compare relevant fields, ignore daySummary/notes/offPlan)
            val actuallyChanged = existing == null ||
                    existing.weightKg != merged.weightKg ||
                    existing.steps != merged.steps ||
                    existing.sleepHours != merged.sleepHours ||
                    existing.restingHr != merged.restingHr ||
                    existing.exercisesJson != merged.exercisesJson ||
                    existing.bodyFatPct != merged.bodyFatPct ||
                    existing.bodyWaterKg != merged.bodyWaterKg ||
                    existing.leanBodyMassKg != merged.leanBodyMassKg ||
                    existing.boneMassKg != merged.boneMassKg ||
                    existing.bloodSugarMmol != merged.bloodSugarMmol

            if (!actuallyChanged) {
                appLogger.hc("${summary.date}: HC data unchanged, skipping")
                return false
            }

            dailyLogDao.upsert(merged)

            val parts = mutableListOf<String>()
            summary.weightKg?.let { if (existing?.weightLocked == true) parts += "weight=LOCKED" else parts += "weight=%.1f kg".format(it) }
            summary.steps?.let { if (existing?.stepsLocked == true) parts += "steps=LOCKED" else parts += "steps=$it" }
            summary.sleepHours?.let { parts += "sleep=${it}h" }
            summary.restingHr?.let { parts += "hr=${it} bpm" }
            summary.exercisesJson?.let { parts += "exercises" }
            summary.bodyFatPct?.let { parts += "bodyFat=%.1f%%".format(it) }
            summary.bodyWaterKg?.let { parts += "bodyWater=%.1f kg".format(it) }
            summary.leanBodyMassKg?.let { parts += "leanMass=%.1f kg".format(it) }
            summary.boneMassKg?.let { parts += "boneMass=%.1f kg".format(it) }
            summary.bloodSugarMmol?.let { parts += "bloodSugar=%.1f mmol/L".format(it) }
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
