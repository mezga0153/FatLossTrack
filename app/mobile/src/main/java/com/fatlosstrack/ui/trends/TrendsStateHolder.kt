package com.fatlosstrack.ui.trends

import androidx.compose.runtime.Stable
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.BloodGlucoseDao
import com.fatlosstrack.data.local.db.BookmarkedMealDao
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.data.remote.OpenAiService
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Read-only state holder for [TrendsScreen].
 * Proxies DAO/preference flows so the composable signature needs only one param.
 */
@Stable
class TrendsStateHolder @Inject constructor(
    private val dailyLogDao: DailyLogDao,
    val mealDao: MealDao,
    private val weightDao: WeightDao,
    private val bloodGlucoseDao: BloodGlucoseDao,
    private val _preferencesManager: PreferencesManager,
    val daySummaryGenerator: DaySummaryGenerator,
    val openAiService: OpenAiService,
    val bookmarkedMealDao: BookmarkedMealDao,
) {
    // ── Preference flows ──
    val goalWeight get() = _preferencesManager.goalWeight
    val weeklyRate get() = _preferencesManager.weeklyRate
    val startWeight get() = _preferencesManager.startWeight
    val preferencesManager get() = _preferencesManager

    // ── DAO Flow accessors ──
    fun logsSince(since: LocalDate) = dailyLogDao.getLogsSince(since)
    fun mealsSince(since: LocalDate) = mealDao.getMealsSince(since)
    fun weightsSince(since: LocalDate) = weightDao.getEntriesSince(since)
    fun allLogs() = dailyLogDao.getAllLogs()
    fun allMeals() = mealDao.getAllMeals()
    fun allWeights() = weightDao.getAllEntries()
    val dailyLogDaoForLeanMass get() = dailyLogDao

    // ── Blood glucose readings ──
    fun bgReadingsBetween(from: Instant, to: Instant) = bloodGlucoseDao.getReadingsBetween(from, to)
    fun allBgReadings() = bloodGlucoseDao.getAllReadings()
}
