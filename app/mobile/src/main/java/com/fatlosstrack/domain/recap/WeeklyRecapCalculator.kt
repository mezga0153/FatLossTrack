package com.fatlosstrack.domain.recap

import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.WeightEntry
import com.fatlosstrack.domain.trend.TrendEngine
import java.time.LocalDate

/** One day of the recap window. Null fields mean nothing was logged for that metric. */
data class RecapDay(
    val date: LocalDate,
    val kcal: Int?,
    val steps: Int?,
    val sleepHours: Double?,
    val weightKg: Double?,
)

/** Numbers for the 7 days ending on [end] (inclusive). */
data class WeeklyRecap(
    val start: LocalDate,
    val end: LocalDate,
    /** EMA weight trend at the end of the window. */
    val trendKg: Double?,
    /** Change in EMA trend across the window; null without a weigh-in inside it. */
    val trendChangeKg: Double?,
    val weighIns: Int,
    val avgKcal: Int?,
    val dailyTargetKcal: Int?,
    val daysWithMeals: Int,
    /** Days with meals logged at or under [dailyTargetKcal]; null without a target. */
    val daysOnTarget: Int?,
    val avgSteps: Int?,
    val avgSleepHours: Double?,
    val days: List<RecapDay>,
) {
    val hasData: Boolean
        get() = daysWithMeals > 0 || weighIns > 0 || avgSteps != null || avgSleepHours != null
}

/**
 * Builds the weekly recap from raw entries. Pure — no I/O — so the worker
 * (notification text) and the recap screen always agree on the numbers.
 */
object WeeklyRecapCalculator {

    const val DAYS = 7

    /**
     * @param weightEntries weigh-ins on or before [end]; include a few weeks before the
     *   window so the trend at its start is meaningful.
     * @param logs daily logs covering the same span as [weightEntries].
     * @param meals meals inside the window (others are ignored).
     */
    fun calculate(
        end: LocalDate,
        weightEntries: List<WeightEntry>,
        logs: List<DailyLog>,
        meals: List<MealEntry>,
        dailyTargetKcal: Int?,
    ): WeeklyRecap {
        val start = end.minusDays(DAYS - 1L)
        fun inWindow(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

        // Same merge as HomeScreen: a daily-log weight wins over raw entries for that day
        val weightByDate = sortedMapOf<LocalDate, Double>()
        weightEntries.filter { !it.date.isAfter(end) }.forEach { weightByDate[it.date] = it.valueKg }
        logs.filter { !it.date.isAfter(end) }.forEach { log -> log.weightKg?.let { weightByDate[log.date] = it } }

        val windowWeights = weightByDate.filterKeys(::inWindow)
        val trendEnd = emaTrend(weightByDate)
        val before = weightByDate.headMap(start)
        // No history before the window: measure from its first weigh-in instead
        val trendStart = when {
            before.isNotEmpty() -> emaTrend(before)
            windowWeights.size >= 2 -> windowWeights.values.first()
            else -> null
        }
        val trendChange = if (windowWeights.isNotEmpty() && trendEnd != null && trendStart != null) {
            trendEnd - trendStart
        } else null

        val kcalByDate = meals.filter { inWindow(it.date) }
            .groupBy { it.date }
            .mapValues { (_, dayMeals) -> dayMeals.sumOf { it.totalKcal } }
        val windowLogs = logs.filter { inWindow(it.date) }.associateBy { it.date }
        val steps = windowLogs.values.mapNotNull { it.steps }
        val sleep = windowLogs.values.mapNotNull { it.sleepHours }

        val days = (0 until DAYS).map { offset ->
            val date = start.plusDays(offset.toLong())
            val log = windowLogs[date]
            RecapDay(
                date = date,
                kcal = kcalByDate[date],
                steps = log?.steps,
                sleepHours = log?.sleepHours,
                weightKg = windowWeights[date],
            )
        }

        return WeeklyRecap(
            start = start,
            end = end,
            trendKg = if (windowWeights.isNotEmpty()) trendEnd else null,
            trendChangeKg = trendChange,
            weighIns = windowWeights.size,
            // Divide by days that actually have meals logged, like the Home stats
            avgKcal = if (kcalByDate.isNotEmpty()) kcalByDate.values.sum() / kcalByDate.size else null,
            dailyTargetKcal = dailyTargetKcal,
            daysWithMeals = kcalByDate.size,
            daysOnTarget = dailyTargetKcal?.let { target -> kcalByDate.values.count { it <= target } },
            avgSteps = if (steps.isNotEmpty()) steps.average().toInt() else null,
            avgSleepHours = if (sleep.isNotEmpty()) sleep.average() else null,
            days = days,
        )
    }

    /** EMA of the most recent weigh-ins, as used for the Home/Trends trend line. */
    private fun emaTrend(weights: Map<LocalDate, Double>): Double? =
        TrendEngine.calculate(
            weights.map { (date, kg) -> WeightEntry(date = date, valueKg = kg) },
            targetKg = null,
            rateKgPerWeek = null,
        )?.avg7d
}
