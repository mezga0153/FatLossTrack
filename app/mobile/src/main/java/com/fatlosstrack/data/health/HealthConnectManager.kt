package com.fatlosstrack.data.health

import android.content.Context
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.*
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.fatlosstrack.data.local.AppLogger
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wraps Health Connect client. Reads weight, steps, sleep, resting HR,
 * exercise sessions, and active calories for a given date range.
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
            HealthPermission.getReadPermission(RestingHeartRateRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
        )
    }

    private val client: HealthConnectClient? by lazy {
        try {
            val status = HealthConnectClient.getSdkStatus(context)
            appLogger.hc("SDK status: $status (AVAILABLE=${HealthConnectClient.SDK_AVAILABLE}, UNAVAILABLE_PROVIDER_UPDATE_REQUIRED=${HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED})")
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

    /** Whether the HC SDK is installed and available */
    fun isAvailable(): Boolean = client != null

    /** Check which of our requested permissions are already granted */
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

    /** True if all required permissions are granted */
    suspend fun hasAllPermissions(): Boolean {
        val granted = getGrantedPermissions()
        val missing = PERMISSIONS.filter { it !in granted }
        if (missing.isNotEmpty()) {
            appLogger.hc("Missing permissions: ${missing.joinToString(", ") { it.substringAfterLast('.') }}")
        }
        return missing.isEmpty()
    }

    // ── Read helpers ──

    private fun dayRange(date: LocalDate): TimeRangeFilter {
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        return TimeRangeFilter.between(start, end)
    }

    /** Latest weight (kg) recorded on [date], or null */
    suspend fun getWeight(date: LocalDate): Double? {
        val c = client ?: return null
        return try {
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = WeightRecord::class,
                    timeRangeFilter = dayRange(date),
                )
            )
            val result = response.records.lastOrNull()?.weight?.inKilograms
            appLogger.hc("  $date weight: ${response.records.size} records → ${result?.let { "%.1f kg".format(it) } ?: "null"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getWeight failed", e)
            appLogger.hc("  $date weight: ERROR ${e.javaClass.simpleName}: ${e.message}")
            appLogger.error("HC", "getWeight($date)", e)
            null
        }
    }

    /** Total step count for [date] */
    suspend fun getSteps(date: LocalDate): Int? {
        val c = client ?: return null
        return try {
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = StepsRecord::class,
                    timeRangeFilter = dayRange(date),
                )
            )
            val total = response.records.sumOf { it.count }
            val result = if (total > 0) total.toInt() else null
            appLogger.hc("  $date steps: ${response.records.size} records → ${result ?: "null"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getSteps failed", e)
            appLogger.hc("  $date steps: ERROR ${e.javaClass.simpleName}: ${e.message}")
            appLogger.error("HC", "getSteps($date)", e)
            null
        }
    }

    /** Total sleep hours for the night ending on [date] (looks at prior 18h window) */
    suspend fun getSleepHours(date: LocalDate): Double? {
        val c = client ?: return null
        return try {
            val zone = ZoneId.systemDefault()
            // Sleep ending on this date — look from prior day 6 PM to today noon
            val start = date.minusDays(1).atTime(18, 0).atZone(zone).toInstant()
            val end = date.atTime(14, 0).atZone(zone).toInstant()
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                )
            )
            val result = if (response.records.isEmpty()) {
                null
            } else {
                // Prefer sleep stages (actual sleep) over session duration (time in bed)
                val sleepStages = response.records.flatMap { it.stages }
                val actualSleepMs = if (sleepStages.isNotEmpty()) {
                    // Sum only stages that represent actual sleep (not awake/out of bed)
                    sleepStages.filter { stage ->
                        stage.stage != SleepSessionRecord.STAGE_TYPE_AWAKE &&
                        stage.stage != SleepSessionRecord.STAGE_TYPE_AWAKE_IN_BED &&
                        stage.stage != SleepSessionRecord.STAGE_TYPE_OUT_OF_BED
                    }.sumOf { stage ->
                        java.time.Duration.between(stage.startTime, stage.endTime).toMillis()
                    }
                } else {
                    // No stages available — fall back to session duration
                    response.records.sumOf { record ->
                        java.time.Duration.between(record.startTime, record.endTime).toMillis()
                    }
                }
                val hours = actualSleepMs / 3_600_000.0
                if (hours > 0) String.format(java.util.Locale.US, "%.1f", hours).toDouble() else null
            }
            val stageCount = response.records.sumOf { it.stages.size }
            appLogger.hc("  $date sleep: ${response.records.size} sessions, $stageCount stages → ${result?.let { "${it}h" } ?: "null"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getSleepHours failed", e)
            appLogger.hc("  $date sleep: ERROR ${e.javaClass.simpleName}: ${e.message}")
            appLogger.error("HC", "getSleepHours($date)", e)
            null
        }
    }

    /** Resting heart rate for [date] from RestingHeartRateRecord, or null */
    suspend fun getRestingHr(date: LocalDate): Int? {
        val c = client ?: return null
        return try {
            // Try dedicated RestingHeartRateRecord first (written by watches)
            val restingResponse = c.readRecords(
                ReadRecordsRequest(
                    recordType = RestingHeartRateRecord::class,
                    timeRangeFilter = dayRange(date),
                )
            )
            if (restingResponse.records.isNotEmpty()) {
                val avg = restingResponse.records.map { it.beatsPerMinute }.average().toInt()
                appLogger.hc("  $date hr: ${restingResponse.records.size} resting records → $avg bpm")
                return avg
            }

            // Fallback: use raw HR samples, take median of bottom quartile
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = dayRange(date),
                )
            )
            val allSamples = response.records.flatMap { it.samples }
            val result = if (allSamples.isEmpty()) {
                null
            } else {
                val sorted = allSamples.map { it.beatsPerMinute }.sorted()
                // Take median of bottom quartile as resting HR estimate
                val quartile = sorted.take(maxOf(1, sorted.size / 4))
                quartile[quartile.size / 2].toInt()
            }
            appLogger.hc("  $date hr: ${response.records.size} records, ${allSamples.size} samples → ${result?.let { "$it bpm (fallback)" } ?: "null"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getRestingHr failed", e)
            appLogger.hc("  $date hr: ERROR ${e.javaClass.simpleName}: ${e.message}")
            appLogger.error("HC", "getRestingHr($date)", e)
            null
        }
    }

    /** Exercise sessions for [date] — returns JSON array string */
    suspend fun getExercises(date: LocalDate): String? {
        val c = client ?: return null
        return try {
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = dayRange(date),
                )
            )
            if (response.records.isEmpty()) {
                appLogger.hc("  $date exercises: 0 sessions")
                return null
            }

            // Also get active calories for the day
            val calResponse = c.readRecords(
                ReadRecordsRequest(
                    recordType = ActiveCaloriesBurnedRecord::class,
                    timeRangeFilter = dayRange(date),
                )
            )
            val totalActiveCal = calResponse.records.sumOf {
                it.energy.inKilocalories
            }.toInt()

            val exercises = response.records.map { session ->
                val durationMin = java.time.Duration.between(
                    session.startTime, session.endTime
                ).toMinutes().toInt()
                val name = exerciseTypeName(session.exerciseType)
                """{"name":"$name","durationMin":$durationMin,"kcal":0}"""
            }

            // If we have just one exercise and active cals, assign cals to it
            val result = if (exercises.size == 1 && totalActiveCal > 0) {
                val single = exercises[0].replace("\"kcal\":0", "\"kcal\":$totalActiveCal")
                "[$single]"
            } else {
                "[${exercises.joinToString(",")}]"
            }
            appLogger.hc("  $date exercises: ${response.records.size} sessions, ${totalActiveCal} active kcal")
            result
        } catch (e: Exception) {
            Log.e(TAG, "getExercises failed", e)
            appLogger.hc("  $date exercises: ERROR ${e.javaClass.simpleName}: ${e.message}")
            appLogger.error("HC", "getExercises($date)", e)
            null
        }
    }

    /** Pull all health data for a single date into a DaySummary */
    suspend fun getDaySummary(date: LocalDate): DaySummary {
        appLogger.hc("Reading HC data for $date …")
        val summary = DaySummary(
            date = date,
            weightKg = getWeight(date),
            steps = getSteps(date),
            sleepHours = getSleepHours(date),
            restingHr = getRestingHr(date),
            exercisesJson = getExercises(date),
        )
        val hasAny = summary.weightKg != null || summary.steps != null ||
                summary.sleepHours != null || summary.restingHr != null ||
                summary.exercisesJson != null
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

/** Map Health Connect exercise type int to a readable name */
private fun exerciseTypeName(type: Int): String = when (type) {
    ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "Running"
    ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "Walking"
    ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "Cycling"
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER,
    ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL -> "Swimming"
    ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "Hiking"
    ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "Yoga"
    ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "Weight Training"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING -> "Rowing"
    ExerciseSessionRecord.EXERCISE_TYPE_ROWING_MACHINE -> "Rowing Machine"
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING -> "Stair Climbing"
    ExerciseSessionRecord.EXERCISE_TYPE_STAIR_CLIMBING_MACHINE -> "Stair Machine"
    ExerciseSessionRecord.EXERCISE_TYPE_ELLIPTICAL -> "Elliptical"
    ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "Pilates"
    ExerciseSessionRecord.EXERCISE_TYPE_DANCING -> "Dancing"
    ExerciseSessionRecord.EXERCISE_TYPE_MARTIAL_ARTS -> "Martial Arts"
    ExerciseSessionRecord.EXERCISE_TYPE_FOOTBALL_AMERICAN -> "Football"
    ExerciseSessionRecord.EXERCISE_TYPE_BASKETBALL -> "Basketball"
    ExerciseSessionRecord.EXERCISE_TYPE_TENNIS -> "Tennis"
    ExerciseSessionRecord.EXERCISE_TYPE_GOLF -> "Golf"
    ExerciseSessionRecord.EXERCISE_TYPE_CALISTHENICS -> "Calisthenics"
    ExerciseSessionRecord.EXERCISE_TYPE_STRETCHING -> "Stretching"
    ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING -> "HIIT"
    else -> "Workout"
}
