package com.fatlosstrack.ui.trends

import androidx.compose.runtime.Stable
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.WeightDao
import java.time.LocalDate
import javax.inject.Inject

/**
 * Read-only state holder for [TrendsScreen].
 * Proxies DAO/preference flows so the composable signature needs only one param.
 */
@Stable
class TrendsStateHolder @Inject constructor(
    private val dailyLogDao: DailyLogDao,
    private val mealDao: MealDao,
    private val weightDao: WeightDao,
    private val _preferencesManager: PreferencesManager,
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
}
