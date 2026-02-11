package com.fatlosstrack.ui.log

import androidx.compose.runtime.Stable
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.remote.OpenAiService
import javax.inject.Inject

/**
 * State holder for [LogScreen].
 * Proxies DAOs, preferences, and services so the composable
 * signature needs only one dependency param.
 */
@Stable
class LogStateHolder @Inject constructor(
    private val _mealDao: MealDao,
    private val _dailyLogDao: DailyLogDao,
    private val _preferencesManager: PreferencesManager,
    private val _daySummaryGenerator: DaySummaryGenerator,
    private val _openAiService: OpenAiService,
) {
    // ── Preference flows ──
    val startDate get() = _preferencesManager.startDate
    val preferencesManager get() = _preferencesManager

    // ── DAO Flow accessors ──
    fun allMeals() = _mealDao.getAllMeals()
    fun allLogs() = _dailyLogDao.getAllLogs()

    // ── Passthrough for LogSheetHost ──
    val mealDao get() = _mealDao
    val dailyLogDao get() = _dailyLogDao
    val daySummaryGenerator get() = _daySummaryGenerator
    val openAiService get() = _openAiService
}
