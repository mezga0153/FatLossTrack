package com.fatlosstrack.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.ui.components.InfoCard
import com.fatlosstrack.ui.components.SimpleLineChart
import com.fatlosstrack.ui.components.MacroBarChart
import com.fatlosstrack.ui.components.TrendChart
import com.fatlosstrack.ui.components.rememberDailyTargetKcal
import com.fatlosstrack.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Trends tab — analytical deep-dive with real data.
 *
 * 1. Weight trend chart with time range toggle (7d/30d/90d)
 * 2. Weight stats (7d avg, 30d avg, weekly change, goal)
 * 3. Calorie trend chart
 * 4. Habit patterns (sleep vs weight, consistency, etc.)
 */
@Composable
fun TrendsScreen(
    state: TrendsStateHolder,
) {
    var selectedRange by remember { mutableStateOf("30d") }
    val ranges = listOf("7d", "30d", "90d")

    val daysBack = when (selectedRange) {
        "7d" -> 6L
        "30d" -> 29L
        else -> 89L
    }
    val since = LocalDate.now().minusDays(daysBack)

    val logs by state.logsSince(since).collectAsState(initial = emptyList())
    val meals by state.mealsSince(since).collectAsState(initial = emptyList())
    val weightEntries by state.weightsSince(since).collectAsState(initial = emptyList())

    val goalWeight by state.goalWeight.collectAsState(initial = null)
    val weeklyRate by state.weeklyRate.collectAsState(initial = null)
    val startWeight by state.startWeight.collectAsState(initial = null)

    // TDEE / daily target
    val dailyTargetKcal = rememberDailyTargetKcal(state.preferencesManager)

    // Weight data — merge DailyLog weights + WeightEntry
    val weightData = remember(logs, weightEntries) {
        val map = mutableMapOf<LocalDate, Double>()
        weightEntries.forEach { map[it.date] = it.valueKg }
        logs.forEach { log -> log.weightKg?.let { map[log.date] = it } }
        map.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    val weights = weightData.map { it.second }
    val avg7d = weights.takeLast(7).let { if (it.isNotEmpty()) it.average() else null }
    val avg30d = weights.let { if (it.isNotEmpty()) it.average() else null }

    // Weekly change: compare latest 7d avg to previous 7d avg
    val weeklyChange = if (weights.size >= 14) {
        val recent = weights.takeLast(7).average()
        val prev = weights.dropLast(7).takeLast(7).average()
        recent - prev
    } else if (weights.size >= 2) {
        weights.last() - weights.first()
    } else null

    // Calorie data per day
    val kcalByDay = remember(meals) {
        meals.groupBy { it.date }
            .map { (date, dayMeals) -> date to dayMeals.sumOf { it.totalKcal } }
            .sortedBy { it.first }
    }
    val avgKcal = if (kcalByDay.isNotEmpty()) kcalByDay.map { it.second }.average().toInt() else null

    // Macro data per day
    val macrosByDay = remember(meals) {
        meals.groupBy { it.date }
            .map { (date, dayMeals) ->
                val p = dayMeals.sumOf { it.totalProteinG }
                val c = dayMeals.sumOf { it.totalCarbsG }
                val f = dayMeals.sumOf { it.totalFatG }
                date to Triple(p, c, f)
            }
            .filter { (_, t) -> t.first + t.second + t.third > 0 }
            .sortedBy { it.first }
    }

    // Sleep data
    val sleepData = remember(logs) {
        logs.filter { it.sleepHours != null }
            .sortedBy { it.date }
            .map { it.date to it.sleepHours!! }
    }
    val avgSleep = if (sleepData.isNotEmpty()) sleepData.map { it.second }.average() else null

    // Steps data
    val stepsData = remember(logs) {
        logs.filter { it.steps != null }
            .sortedBy { it.date }
            .map { it.date to it.steps!! }
    }
    val avgSteps = if (stepsData.isNotEmpty()) stepsData.map { it.second }.average().toInt() else null

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = statusBarTop + 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Time range toggle ──
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ranges.forEach { range ->
                val isSelected = range == selectedRange
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Primary.copy(alpha = 0.2f) else CardSurface)
                        .clickable { selectedRange = range }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = range,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) Primary else OnSurfaceVariant,
                    )
                }
            }
        }

        // ── Weight Trend Chart ──
        if (weightData.size >= 2) {
            InfoCard(label = stringResource(R.string.trends_weight)) {
                val firstWeight = weightData.firstOrNull()?.second
                val lastDate = weightData.lastOrNull()?.first
                val firstDate = weightData.firstOrNull()?.first
                val actualDays = if (firstDate != null && lastDate != null)
                    java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate).toInt().coerceAtLeast(1) else 1
                val refTarget = when (selectedRange) {
                    "90d" -> goalWeight?.toDouble()
                    else -> firstWeight?.let { it - (weeklyRate ?: 0f).toDouble() * actualDays / 7.0 }
                }
                val dateLabels = weightData.map { (date, _) ->
                    val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "%d. %s".format(date.dayOfMonth, monthName)
                }
                val xLabels = weightData.map { (date, _) -> xAxisLabel(date, selectedRange == "7d") }
                TrendChart(
                    dataPoints = weightData.mapIndexed { i, (_, w) -> i to w },
                    dateLabels = dateLabels,
                    xAxisLabels = xLabels,
                    startLineKg = firstWeight,
                    targetLineKg = refTarget,
                    modifier = Modifier.height(160.dp),
                )
                Spacer(Modifier.height(12.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (avg7d != null) {
                        StatColumn(stringResource(R.string.trends_7d_avg), "%.1f kg".format(avg7d))
                    }
                    if (avg30d != null && selectedRange != "7d") {
                        StatColumn(stringResource(R.string.trends_period_avg), "%.1f kg".format(avg30d))
                    }
                    if (weeklyChange != null) {
                        StatColumn(
                            stringResource(R.string.trends_weekly_delta),
                            "%+.1f kg".format(weeklyChange),
                            if (weeklyChange < 0) Secondary else Tertiary,
                        )
                    }
                    if (goalWeight != null) {
                        StatColumn(stringResource(R.string.trends_goal), "%.1f kg".format(goalWeight!!), Secondary)
                    }
                }
            }
        } else {
            InfoCard(label = stringResource(R.string.trends_weight)) {
                Text(
                    stringResource(R.string.trends_no_weight_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                )
            }
        }

        // ── Calorie Trend ──
        if (kcalByDay.size >= 2) {
            InfoCard(label = stringResource(R.string.trends_calories)) {
                val labels = kcalByDay.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = kcalByDay.map { (d, _) -> xAxisLabel(d, selectedRange == "7d") }
                SimpleLineChart(
                    data = kcalByDay.mapIndexed { i, (_, kcal) -> i to kcal.toDouble() },
                    color = Secondary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "kcal",
                    refLineValue = dailyTargetKcal?.toDouble(),
                    refLineColor = Secondary,
                    refLineLabel = dailyTargetKcal?.let { "$it kcal" },
                    modifier = Modifier.height(140.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatColumn(stringResource(R.string.trends_avg), stringResource(R.string.format_kcal_day, avgKcal ?: 0))
                    StatColumn(stringResource(R.string.trends_total), "${kcalByDay.sumOf { it.second }} kcal")
                    StatColumn(stringResource(R.string.trends_days_tracked), "${kcalByDay.size}")
                }
            }
        }

        // ── Macros Trend ──
        if (macrosByDay.size >= 2) {
            InfoCard(label = stringResource(R.string.trends_macros)) {
                val labels = macrosByDay.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = macrosByDay.map { (d, _) -> xAxisLabel(d, selectedRange == "7d") }
                val targets = dailyTargetKcal?.let {
                    com.fatlosstrack.domain.TdeeCalculator.macroTargets(it)
                }
                MacroBarChart(
                    data = macrosByDay.map { it.second },
                    macroTargets = targets,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    colors = Triple(Primary, Tertiary, Accent),
                    modifier = Modifier.height(140.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val avgP = macrosByDay.map { it.second.first }.average().toInt()
                    val avgC = macrosByDay.map { it.second.second }.average().toInt()
                    val avgF = macrosByDay.map { it.second.third }.average().toInt()
                    StatColumn(stringResource(R.string.trends_avg) + " P", "${avgP}g", Primary)
                    StatColumn(stringResource(R.string.trends_avg) + " C", "${avgC}g", Tertiary)
                    StatColumn(stringResource(R.string.trends_avg) + " F", "${avgF}g", Accent)
                }
            }
        }

        // ── Sleep Trend ──
        if (sleepData.size >= 2) {
            InfoCard(label = stringResource(R.string.trends_sleep)) {
                val labels = sleepData.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = sleepData.map { (d, _) -> xAxisLabel(d, selectedRange == "7d") }
                SimpleLineChart(
                    data = sleepData.mapIndexed { i, (_, h) -> i to h },
                    color = Primary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "h",
                    modifier = Modifier.height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (avgSleep != null) StatColumn(stringResource(R.string.trends_avg), stringResource(R.string.format_hours, avgSleep))
                    val minSleep = sleepData.minOf { it.second }
                    val maxSleep = sleepData.maxOf { it.second }
                    StatColumn(stringResource(R.string.trends_range), stringResource(R.string.format_sleep_range, minSleep, maxSleep))
                }
            }
        }

        // ── Steps Trend ──
        if (stepsData.size >= 2) {
            InfoCard(label = stringResource(R.string.trends_steps)) {
                val labels = stepsData.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = stepsData.map { (d, _) -> xAxisLabel(d, selectedRange == "7d") }
                SimpleLineChart(
                    data = stepsData.mapIndexed { i, (_, s) -> i to s.toDouble() },
                    color = Secondary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "steps",
                    modifier = Modifier.height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (avgSteps != null) StatColumn(stringResource(R.string.trends_avg), stringResource(R.string.format_steps_k_day, avgSteps / 1000))
                    val totalSteps = stepsData.sumOf { it.second }
                    StatColumn(stringResource(R.string.trends_total), "${totalSteps / 1000}k")
                }
            }
        }

        // ── Habits Summary ──
        val daysWithMeals = kcalByDay.size
        val totalDays = (daysBack + 1).toInt()
        val loggingRate = if (totalDays > 0) daysWithMeals * 100 / totalDays else 0
        val daysWithAlcohol = meals.filter { it.hasAlcohol }.map { it.date }.distinct().size

        InfoCard(label = stringResource(R.string.trends_habits)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$loggingRate%", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Primary)
                    Text(stringResource(R.string.trends_logging_rate), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$daysWithMeals/$totalDays", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = OnSurface)
                    Text(stringResource(R.string.trends_days_tracked_label), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                }
                if (daysWithAlcohol > 0) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$daysWithAlcohol", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Tertiary)
                        Text(stringResource(R.string.trends_alcohol_days), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(80.dp))
    }
}

private fun xAxisLabel(date: LocalDate, use7dDayNames: Boolean): String {
    return if (use7dDayNames) {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .removeSuffix(".").take(3)
    } else {
        val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
        "${date.dayOfMonth}. $month"
    }
}

@Composable
private fun StatColumn(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = OnSurface,
) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = valueColor)
    }
}
