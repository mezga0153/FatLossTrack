package com.fatlosstrack.ui.mock

import java.time.LocalDate

/**
 * Mock data for UI prototyping. No real persistence.
 */
object MockData {

    // -- Weight entries (last 14 days) --
    val weightEntries: List<Pair<LocalDate, Double>> = listOf(
        LocalDate.now().minusDays(13) to 86.2,
        LocalDate.now().minusDays(12) to 86.0,
        LocalDate.now().minusDays(11) to 86.4,
        LocalDate.now().minusDays(10) to 85.8,
        LocalDate.now().minusDays(9) to 85.5,
        LocalDate.now().minusDays(8) to 85.9,
        LocalDate.now().minusDays(7) to 85.3,
        LocalDate.now().minusDays(6) to 85.1,
        LocalDate.now().minusDays(5) to 85.6,
        LocalDate.now().minusDays(4) to 85.0,
        LocalDate.now().minusDays(3) to 84.8,
        LocalDate.now().minusDays(2) to 85.2,
        LocalDate.now().minusDays(1) to 84.7,
        LocalDate.now() to 84.5,
    )

    val avg7d = 84.97
    val avg14d = 85.38
    val trendDirection = "down"
    val deviationKg = 0.6    // above projected
    val projectedGoalDate = "May 18, 2026"
    val targetKg = 80.0
    val rateKgPerWeek = 0.5
    val confidenceLow = 84.5
    val confidenceHigh = 85.4

    // -- Goal --
    val goalSummary = "Target: 80.0 kg at 0.5 kg/week"
    val dailyDeficit = "~550 kcal/day deficit required"

    // -- Today & Yesterday --
    data class DaySnapshot(
        val date: LocalDate,
        val weight: Double?,
        val mealsLogged: Int,
        val mealCategories: List<String>,
        val steps: Int?,
        val sleepHours: Double?,
        val hasAlcohol: Boolean,
        val offPlan: Boolean,
    )

    val today = DaySnapshot(
        date = LocalDate.now(),
        weight = 84.5,
        mealsLogged = 2,
        mealCategories = listOf("Home", "Restaurant"),
        steps = 7_820,
        sleepHours = 7.2,
        hasAlcohol = false,
        offPlan = false,
    )

    val yesterday = DaySnapshot(
        date = LocalDate.now().minusDays(1),
        weight = 84.7,
        mealsLogged = 3,
        mealCategories = listOf("Home", "Home", "Fast food"),
        steps = 5_430,
        sleepHours = 6.1,
        hasAlcohol = true,
        offPlan = false,
    )

    val olderDays = (2..6).map { daysAgo ->
        DaySnapshot(
            date = LocalDate.now().minusDays(daysAgo.toLong()),
            weight = weightEntries.firstOrNull { it.first == LocalDate.now().minusDays(daysAgo.toLong()) }?.second,
            mealsLogged = listOf(1, 2, 3, 2, 3)[daysAgo - 2],
            mealCategories = listOf("Home"),
            steps = listOf(9_100, 3_200, 8_400, 6_700, 10_200)[daysAgo - 2],
            sleepHours = listOf(7.5, 5.8, 7.0, 6.5, 8.0)[daysAgo - 2],
            hasAlcohol = daysAgo == 3,
            offPlan = daysAgo == 3,
        )
    }

    // -- Status cards --
    data class StatusCard(val label: String, val message: String)

    val statusCards = listOf(
        StatusCard("Consistency", "You logged data on 5 of the last 7 days."),
        StatusCard("Energy Balance", "Estimated intake ~180 kcal/day above plan."),
        StatusCard("Sleep Impact", "<6h sleep correlates with +0.4 kg fluctuations."),
    )

    // -- Pattern insights --
    data class InsightCard(val title: String, val message: String, val date: LocalDate)

    val insights = listOf(
        InsightCard("Weekend pattern", "Weekends erase ~70% of weekday deficit.", LocalDate.now().minusDays(1)),
        InsightCard("Alcohol effect", "Alcohol days delay weight loss by ~48 hours.", LocalDate.now().minusDays(3)),
        InsightCard("Sleep & weight", "Your highest-calorie days correlate with <6h sleep.", LocalDate.now().minusDays(5)),
        InsightCard("Consistency", "You under-eat Mon–Thu, then overeat Fri–Sat.", LocalDate.now().minusDays(7)),
    )
}
