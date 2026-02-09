package com.fatlosstrack.ui.trends

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.*
import com.fatlosstrack.ui.components.InfoCard
import com.fatlosstrack.ui.components.TrendChart
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
    dailyLogDao: DailyLogDao,
    mealDao: MealDao,
    weightDao: WeightDao,
    preferencesManager: PreferencesManager,
) {
    var selectedRange by remember { mutableStateOf("30d") }
    val ranges = listOf("7d", "30d", "90d")

    val daysBack = when (selectedRange) {
        "7d" -> 6L
        "30d" -> 29L
        else -> 89L
    }
    val since = LocalDate.now().minusDays(daysBack)

    val logs by dailyLogDao.getLogsSince(since).collectAsState(initial = emptyList())
    val meals by mealDao.getMealsSince(since).collectAsState(initial = emptyList())
    val weightEntries by weightDao.getEntriesSince(since).collectAsState(initial = emptyList())

    val goalWeight by preferencesManager.goalWeight.collectAsState(initial = null)

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header ──
        Text(
            text = stringResource(R.string.trends_title),
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )

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
                val goalW = goalWeight?.toDouble() ?: 80.0
                TrendChart(
                    dataPoints = weightData.mapIndexed { i, (_, w) -> i to w },
                    avg7d = avg7d ?: 0.0,
                    targetKg = goalW,
                    confidenceLow = (avg30d ?: avg7d ?: 0.0) - 0.5,
                    confidenceHigh = (avg30d ?: avg7d ?: 0.0) + 0.5,
                    modifier = Modifier.height(220.dp),
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
                SimpleLineChart(
                    data = kcalByDay.mapIndexed { i, (_, kcal) -> i to kcal.toDouble() },
                    color = Accent,
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

        // ── Sleep Trend ──
        if (sleepData.size >= 2) {
            InfoCard(label = stringResource(R.string.trends_sleep)) {
                SimpleLineChart(
                    data = sleepData.mapIndexed { i, (_, h) -> i to h },
                    color = Primary,
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
                SimpleLineChart(
                    data = stepsData.mapIndexed { i, (_, s) -> i to s.toDouble() },
                    color = Secondary,
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

/**
 * Minimal line chart — just a line + dots, no axes. Used for calories, sleep, steps.
 */
@Composable
private fun SimpleLineChart(
    data: List<Pair<Int, Double>>,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.fillMaxWidth()) {
        if (data.size < 2) return@Canvas

        val padding = 16.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2

        val values = data.map { it.second }
        val minVal = values.min() * 0.9
        val maxVal = values.max() * 1.1
        val range = (maxVal - minVal).coerceAtLeast(1.0)

        fun xFor(index: Int): Float {
            val maxIdx = data.maxOf { it.first }
            val minIdx = data.minOf { it.first }
            val idxRange = (maxIdx - minIdx).coerceAtLeast(1)
            return padding + (index - minIdx).toFloat() / idxRange * chartWidth
        }

        fun yFor(value: Double): Float {
            return padding + ((maxVal - value) / range * chartHeight).toFloat()
        }

        val path = Path()
        data.forEachIndexed { i, (dayIdx, value) ->
            val x = xFor(dayIdx)
            val y = yFor(value)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
        )

        data.forEach { (dayIdx, value) ->
            drawCircle(
                color = color.copy(alpha = 0.5f),
                radius = 2.5.dp.toPx(),
                center = Offset(xFor(dayIdx), yFor(value)),
            )
        }
    }
}
