package com.fatlosstrack.data.recap

import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.domain.TdeeCalculator
import com.fatlosstrack.domain.recap.WeeklyRecap
import com.fatlosstrack.domain.recap.WeeklyRecapCalculator
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Loads the data for a [WeeklyRecap] — shared by the notification worker and the recap screen. */
@Singleton
class WeeklyRecapRepository @Inject constructor(
    private val weightDao: WeightDao,
    private val dailyLogDao: DailyLogDao,
    private val mealDao: MealDao,
    private val preferencesManager: PreferencesManager,
) {
    suspend fun load(end: LocalDate): WeeklyRecap {
        val start = end.minusDays(WeeklyRecapCalculator.DAYS - 1L)
        // Weigh-ins from before the window seed the EMA at its start
        val historyStart = start.minusDays(TREND_HISTORY_DAYS)
        return WeeklyRecapCalculator.calculate(
            end = end,
            weightEntries = weightDao.getEntriesSince(historyStart).first(),
            logs = dailyLogDao.getLogsSince(historyStart).first(),
            meals = mealDao.getMealsForDateRange(start, end).first(),
            dailyTargetKcal = dailyTargetKcal(),
        )
    }

    /** Same target as rememberDailyTargetKcal(), read once outside Compose. */
    private suspend fun dailyTargetKcal(): Int? {
        val sex = preferencesManager.sex.first() ?: return null
        val age = preferencesManager.age.first() ?: return null
        val height = preferencesManager.heightCm.first() ?: return null
        val weight = preferencesManager.startWeight.first() ?: return null
        return TdeeCalculator.dailyTarget(
            weight, height, age, sex,
            preferencesManager.activityLevel.first(),
            preferencesManager.weeklyRate.first(),
        )
    }

    private companion object {
        const val TREND_HISTORY_DAYS = 60L
    }
}
