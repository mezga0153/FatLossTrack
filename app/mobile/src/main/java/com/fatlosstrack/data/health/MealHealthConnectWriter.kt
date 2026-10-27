package com.fatlosstrack.data.health

import android.content.Context
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.room.withTransaction
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.FatLossDatabase
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.data.local.db.displayTime
import com.fatlosstrack.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import androidx.health.connect.client.records.MealType as HcMealType

/**
 * Mirrors logged meals into Health Connect as [NutritionRecord]s so other apps
 * (Fit, Samsung Health, …) see the intake.
 *
 * Room stays the source of truth: every call is fire-and-forget on the app scope,
 * failures are logged and never thrown. Nothing is written unless the user turned on
 * the Settings toggle AND granted WRITE_NUTRITION.
 *
 * Each record's clientRecordId is "meal-<roomId>", so writing an edited meal again
 * updates its record instead of adding a duplicate. The HC record id is kept on
 * [MealEntry.hcRecordId]; null means "not written yet" and is retried on app start.
 */
@Singleton
class MealHealthConnectWriter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val hcManager: HealthConnectManager,
    private val database: FatLossDatabase,
    private val mealDao: MealDao,
    private val preferencesManager: PreferencesManager,
    private val appLogger: AppLogger,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    companion object {
        private const val BATCH_SIZE = 100
        private const val RESYNC_MARKER = "hc_meal_resync_pending"

        /** Nutrition records are intervals (end must be after start); a meal is written as 15 min. */
        private val MEAL_DURATION = Duration.ofMinutes(15)

        fun clientRecordId(mealId: Long) = "meal-$mealId"

        /**
         * Called after a backup restore: the restored meals no longer match what we wrote to HC,
         * so the next app start rewrites them all. A plain file rather than a DataStore pref,
         * because a restore overwrites the prefs file too.
         */
        fun markFullResyncPending(context: Context) {
            runCatching { File(context.filesDir, RESYNC_MARKER).createNewFile() }
        }
    }

    /** Serialises HC writes so a delete can't overtake the insert of the same meal. */
    private val mutex = Mutex()

    /** Write [meal] to HC, or update its record if it was written before. [meal] must carry its Room id. */
    fun launchUpsert(meal: MealEntry, reason: String) {
        if (meal.id == 0L) return
        appScope.launch {
            mutex.withLock {
                if (!isEnabled()) return@withLock
                try {
                    val hcId = hcManager.upsertNutrition(listOf(meal.toNutritionRecord())).firstOrNull()
                    if (hcId != null) mealDao.setHcRecordId(meal.id, hcId)
                    appLogger.hc("Meal ${meal.id} written to HC ($reason)")
                } catch (e: Exception) {
                    appLogger.error("HC", "Meal ${meal.id} write failed ($reason)", e)
                }
            }
        }
    }

    /** Remove [meal]'s record from HC. Safe to call for meals that were never written. */
    fun launchDelete(meal: MealEntry, reason: String) {
        appScope.launch {
            mutex.withLock {
                if (!isEnabled()) return@withLock
                try {
                    hcManager.deleteNutrition(recordIds = emptyList(), clientRecordIds = listOf(clientRecordId(meal.id)))
                    appLogger.hc("Meal ${meal.id} deleted from HC ($reason)")
                } catch (e: Exception) {
                    appLogger.error("HC", "Meal ${meal.id} delete failed ($reason)", e)
                }
            }
        }
    }

    /** Rewrite every meal to HC. Runs when the toggle is turned on (the one-time backfill). */
    fun launchBackfill(reason: String) {
        appScope.launch {
            mutex.withLock {
                if (isEnabled()) backfillAll(reason)
            }
        }
    }

    /** App start: run a full resync after a restore, otherwise retry meals that never reached HC. */
    fun launchStartupSync() {
        appScope.launch {
            mutex.withLock {
                if (!isEnabled()) return@withLock
                if (File(context.filesDir, RESYNC_MARKER).exists()) {
                    backfillAll("restore")
                } else {
                    val pending = mealDao.getMealsWithoutHcRecord()
                    if (pending.isNotEmpty()) writeMeals(pending, "pending")
                }
            }
        }
    }

    private suspend fun isEnabled(): Boolean =
        preferencesManager.hcWriteMeals.first() && hcManager.hasNutritionWritePermission()

    /**
     * Deletes everything this app wrote to HC and writes all meals again — this also drops
     * records of meals that no longer exist (e.g. after restoring an older backup).
     */
    private suspend fun backfillAll(reason: String) {
        try {
            hcManager.deleteAllOwnNutrition()
            mealDao.clearHcRecordIds()
        } catch (e: Exception) {
            appLogger.error("HC", "Meal backfill: clearing old records failed ($reason)", e)
            return
        }
        File(context.filesDir, RESYNC_MARKER).delete()
        writeMeals(mealDao.getAllMeals().first(), reason)
    }

    /** Writes [meals] in batches; a failed batch keeps its meals' hcRecordId null so they're retried later. */
    private suspend fun writeMeals(meals: List<MealEntry>, reason: String) {
        var written = 0
        meals.chunked(BATCH_SIZE).forEach { batch ->
            try {
                val ids = hcManager.upsertNutrition(batch.map { it.toNutritionRecord() })
                database.withTransaction {
                    batch.zip(ids).forEach { (meal, hcId) -> mealDao.setHcRecordId(meal.id, hcId) }
                }
                written += batch.size
            } catch (e: Exception) {
                appLogger.error("HC", "Meal batch write failed ($reason, ${batch.size} meals)", e)
            }
        }
        appLogger.hc("Meals written to HC ($reason): $written/${meals.size}")
    }

    private fun MealEntry.toNutritionRecord(): NutritionRecord {
        val zone = ZoneId.systemDefault()
        val start = startOnMealDate(zone)
        val end = start.plus(MEAL_DURATION)
        return NutritionRecord(
            startTime = start,
            startZoneOffset = zone.rules.getOffset(start),
            endTime = end,
            endZoneOffset = zone.rules.getOffset(end),
            metadata = Metadata.manualEntry(
                clientRecordId = clientRecordId(id),
                // Must increase on every write for HC to replace the existing record
                clientRecordVersion = System.currentTimeMillis(),
            ),
            // Zero means "unknown" for older meals (macros were added later), so leave those out
            energy = totalKcal.takeIf { it > 0 }?.let { Energy.kilocalories(it.toDouble()) },
            protein = totalProteinG.takeIf { it > 0 }?.let { Mass.grams(it.toDouble()) },
            totalCarbohydrate = totalCarbsG.takeIf { it > 0 }?.let { Mass.grams(it.toDouble()) },
            totalFat = totalFatG.takeIf { it > 0 }?.let { Mass.grams(it.toDouble()) },
            name = description.ifBlank { null },
            mealType = mealType.toHcMealType(),
        )
    }

    /**
     * When the meal was eaten, on its own [MealEntry.date]. [displayTime] can fall on another day
     * (e.g. yesterday's dinner logged this morning without a time), so then keep the user-set
     * time of day if there is one, else use a typical time for the meal type.
     */
    private fun MealEntry.startOnMealDate(zone: ZoneId): Instant {
        val time = displayTime.atZone(zone)
        if (time.toLocalDate() == date) return time.toInstant()
        val timeOfDay = if (loggedAt != null) time.toLocalTime() else typicalTime(mealType)
        return date.atTime(timeOfDay).atZone(zone).toInstant()
    }

    private fun typicalTime(type: MealType?): LocalTime = when (type) {
        MealType.BREAKFAST -> LocalTime.of(8, 0)
        MealType.BRUNCH -> LocalTime.of(10, 30)
        MealType.LUNCH -> LocalTime.of(12, 30)
        MealType.SNACK -> LocalTime.of(15, 30)
        MealType.DINNER -> LocalTime.of(19, 0)
        null -> LocalTime.NOON
    }

    private fun MealType?.toHcMealType(): Int = when (this) {
        MealType.BREAKFAST -> HcMealType.MEAL_TYPE_BREAKFAST
        MealType.BRUNCH, MealType.LUNCH -> HcMealType.MEAL_TYPE_LUNCH // HC has no brunch
        MealType.DINNER -> HcMealType.MEAL_TYPE_DINNER
        MealType.SNACK -> HcMealType.MEAL_TYPE_SNACK
        null -> HcMealType.MEAL_TYPE_UNKNOWN
    }
}
