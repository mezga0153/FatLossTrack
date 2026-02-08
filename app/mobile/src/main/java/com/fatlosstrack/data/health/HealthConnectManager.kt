package com.fatlosstrack.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fatlosstrack.data.local.AppLogger
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Health Connect client. Uses the **aggregate API** to avoid an Android 16
 * platform bug where readRecords() crashes with "width and height must be > 0"
 * when processing data-source app icons.
 */
@Singleton
class HealthConnectManager @Inject constructor(
    private val context: Context,
    private val appLogger: AppLogger,
) {
    companion object {
        private const val TAG = "HealthConnect"

        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(WeightRecord::class),
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )
    }

    private val client: HealthConnectClient? by lazy {
        try {
            val status = HealthConnectClient.getSdkStatus(context)
            appLogger.hc("SDK status: $status (AVAILABLE=${HealthConnectClient.SDK_AVAILABLE})")
            if (status == HealthConnectClient.SDK_AVAILABLE) {
                val c = HealthConnectClient.getOrCreate(context)
                appLogger.hc("HealthConnectClient created successfully")
                c
            } else {
                Log.w(TAG, "Health Connect SDK not available, status=$status")
                appLogger.hc("HC SDK not available, status=$status")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create HealthConnectClient", e)
            appLogger.hc("Failed to create HC client: ${e.message}")
            null
        }
    }

    fun isAvailable(): Boolean = client != null

    suspend fun getGrantedPermissions(): Set<String> {
        return try {
            val granted = client?.permissionController?.getGrantedPermissions() ?: emptySet()
            appLogger.hc("Granted permissions (${granted.size}): ${granted.joinToString(", ") { it.substringAfterLast('.') }}")
            granted
        } catch (e: Exception) {
            Log.e(TAG, "getGrantedPermissions failed", e)
            appLogger.hc("getGrantedPermissions failed: ${e.message}")
            emptySet()
        }
    }

    suspend fun hasAllPermissions(): Boolean {
        val granted = getGrantedPermissions()
        val missing = PERMISSIONS.filter { it !in granted }
        if (missing.isNotEmpty()) {
            appLogger.hc("Missing permissions: ${missing.joinToString(", ") { it.substringAfterLast('.') }}")
        }
        return missing.isEmpty()
    }

    // ── Helpers ──

    private fun dayRange(date: LocalDate): TimeRangeFilter {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return TimeRangeFilter.between(start, end)
    }

    private fun sleepRange(date: LocalDate): TimeRangeFilter {
        val zone = ZoneId.systemDefault()
        val start = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
        val end = date.atTime(14, 0).atZone(zone).toInstant()
        return TimeRangeFilter.between(start, end)
    }

    // ── Aggregate-based reads (bypasses Android 16 readRecords icon bug) ──

    /**
     * Pull all health data for a single date using aggregate API.
     * This avoids the platform bug with readRecords on Android 16.
     */
    suspend fun getDaySummary(date: LocalDate): DaySummary {
        val c = client ?: return DaySummary(date = date)
        appLogger.hc("Reading HC data for $date (aggregate API) …")

        var weightKg: Double? = null
        var steps: Int? = null
        var sleepHours: Double? = null
        var restingHr: Int? = null
        var exercisesJson: String? = null

        // ── Weight + Steps + HR in one aggregate call ──
        try {
            val dayAgg = c.aggregate(
                AggregateRequest(
                    metrics = setOf(
                        WeightRecord.WEIGHT_AVG,
                        StepsRecord.COUNT_TOTAL,
                        HeartRateRecord.BPM_MIN,
                        HeartRateRecord.BPM_AVG,
                        HeartRateRecord.MEASUREMENTS_COUNT,
                        ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL,
                    ),
                    timeRangeFilter = dayRange(date),
                )
            )

            dayAgg[WeightRecord.WEIGHT_AVG]?.let { mass ->
                weightKg = mass.inKilograms
                appLogger.hc("  $date weight: %.1f kg (avg)".format(weightKg))
            }

            dayAgg[StepsRecord.COUNT_TOTAL]?.let { count ->
                if (count > 0) {
                    steps = count.toInt()
                    appLogger.hc("  $date steps: $steps")
                }
            }

            // Use BPM_MIN as resting HR proxy (better than bottom-20% from samples)
            dayAgg[HeartRateRecord.BPM_MIN]?.let { min ->
                // BPM_MIN can be too low if there's noise; average of min and avg
                val avg = dayAgg[HeartRateRecord.BPM_AVG]
                restingHr = if (avg != null) {
                    // Resting is roughly between min and avg — lean towards min
                    ((min * 2 + avg) / 3).toInt()
                } else {
                    min.toInt()
                }
                val count = dayAgg[HeartRateRecord.MEASUREMENTS_COUNT] ?: 0
                appLogger.hc("  $date hr: min=$min avg=$avg count=$count → resting≈$restingHr bpm")
            }

            val activeCal = dayAgg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]
            if (activeCal != null) {
                val kcal = activeCal.inKilocalories.toInt()
                if (kcal > 0) {
                    // Store as a generic exercise entry if we have active calories
                    exercisesJson = """[{"name":"Activity","durationMin":0,"kcal":$kcal}]"""
                    appLogger.hc("  $date active calories: $kcal kcal")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Day aggregate failed for $date", e)
            appLogger.hc("  $date day-aggregate: ERROR ${e.javaClass.simpleName}: ${e.message}")
        }

        // ── Sleep in separate aggregate (different time range) ──
        try {
            val sleepAgg = c.aggregate(
                AggregateRequest(
                    metrics = setOf(SleepSessionRecord.SLEEP_DURATION_TOTAL),
                    timeRangeFilter = sleepRange(date),
                )
            )
            sleepAgg[SleepSessionRecord.SLEEP_DURATION_TOTAL]?.let { duration ->
                val hours = duration.toMinutes() / 60.0
                if (hours > 0) {
                    sleepHours = "%.1f".format(hours).toDouble()
                    appLogger.hc("  $date sleep: ${sleepHours}h")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sleep aggregate failed for $date", e)
            appLogger.hc("  $date sleep-aggregate: ERROR ${e.javaClass.simpleName}: ${e.message}")
        }

        if (weightKg == null) appLogger.hc("  $date weight: null")
        if (steps == null) appLogger.hc("  $date steps: null")
        if (sleepHours == null) appLogger.hc("  $date sleep: null")
        if (restingHr == null) appLogger.hc("  $date hr: null")
        if (exercisesJson == null) appLogger.hc("  $date exercises: null")

        val summary = DaySummary(
            date = date,
            weightKg = weightKg,
            steps = steps,
            sleepHours = sleepHours,
            restingHr = restingHr,
            exercisesJson = exercisesJson,
        )
        val hasAny = weightKg != null || steps != null || sleepHours != null ||
                restingHr != null || exercisesJson != null
        appLogger.hc("$date summary: ${if (hasAny) "HAS DATA" else "EMPTY"}")
        return summary
    }

    /** Pull summaries for a date range (inclusive) */
    suspend fun getSummaries(from: LocalDate, to: LocalDate): List<DaySummary> {
        val summaries = mutableListOf<DaySummary>()
        var d = from
        while (!d.isAfter(to)) {
            summaries.add(getDaySummary(d))
            d = d.plusDays(1)
        }
        return summaries
    }
}

/** Container for a day's health data from Health Connect */
data class DaySummary(
    val date: LocalDate,
    val weightKg: Double? = null,
    val steps: Int? = null,
    val sleepHours: Double? = null,
    val restingHr: Int? = null,
    val exercisesJson: String? = null,
)
