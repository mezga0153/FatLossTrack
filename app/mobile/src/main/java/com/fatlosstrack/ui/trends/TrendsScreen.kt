package com.fatlosstrack.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatlosstrack.R
import com.fatlosstrack.ui.components.ChartSeries
import com.fatlosstrack.ui.components.InfoCard
import com.fatlosstrack.ui.components.MultiSeriesLineChart
import com.fatlosstrack.ui.components.SimpleLineChart
import com.fatlosstrack.ui.components.MacroBarChart
import com.fatlosstrack.ui.components.TrendChart
import com.fatlosstrack.ui.components.alignedPearson
import com.fatlosstrack.ui.components.correlationLabel
import com.fatlosstrack.ui.components.rememberDailyTargetKcal
import com.fatlosstrack.ui.components.rememberLatestLeanMassKg
import com.fatlosstrack.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
    var selectedRange by remember { mutableStateOf("1M") }
    val ranges = listOf("7D", "1M", "All")

    val since = when (selectedRange) {
        "7D" -> LocalDate.now().minusDays(6L)
        "1M" -> LocalDate.now().minusDays(29L)
        else -> LocalDate.of(2000, 1, 1) // All
    }
    val isAllRange = selectedRange == "All"

    val logs by (if (isAllRange) state.allLogs() else state.logsSince(since)).collectAsState(initial = emptyList())
    val meals by (if (isAllRange) state.allMeals() else state.mealsSince(since)).collectAsState(initial = emptyList())
    val weightEntries by (if (isAllRange) state.allWeights() else state.weightsSince(since)).collectAsState(initial = emptyList())

    val goalWeight by state.goalWeight.collectAsState(initial = null)
    val weeklyRate by state.weeklyRate.collectAsState(initial = null)
    val startWeight by state.startWeight.collectAsState(initial = null)

    // TDEE / daily target
    val dailyTargetKcal = rememberDailyTargetKcal(state.preferencesManager)
    val latestLeanMassKg = rememberLatestLeanMassKg(state.dailyLogDaoForLeanMass)

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

    // Body composition data
    val bodyFatData = remember(logs) {
        logs.filter { it.bodyFatPct != null }
            .sortedBy { it.date }
            .map { it.date to it.bodyFatPct!! }
    }
    val leanMassData = remember(logs) {
        logs.filter { it.leanBodyMassKg != null }
            .sortedBy { it.date }
            .map { it.date to it.leanBodyMassKg!! }
    }
    val bodyWaterData = remember(logs) {
        logs.filter { it.bodyWaterKg != null }
            .sortedBy { it.date }
            .map { it.date to it.bodyWaterKg!! }
    }
    val boneMassData = remember(logs) {
        logs.filter { it.boneMassKg != null }
            .sortedBy { it.date }
            .map { it.date to it.boneMassKg!! }
    }

    // ── Compare Metrics state ────────────────────────────────────────────────
    // Build available series after all data is computed (see below)
    var selectedSeriesIds by remember { mutableStateOf(setOf<String>()) }

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

        // ── Compare Metrics card ──────────────────────────────────────────────
        val comparePrimary = Primary
        val compareSecondary = Secondary
        val compareTertiary = Tertiary
        val compareAccent = Accent
        val compareSeriesOptions = remember(
            weightData, bodyFatData, leanMassData, kcalByDay, sleepData, stepsData, bodyWaterData,
            comparePrimary, compareSecondary, compareTertiary, compareAccent,
        ) {
            buildList {
                if (weightData.size >= 2) add(AvailableSeries("weight", "Weight", comparePrimary, "kg", weightData))
                if (bodyFatData.size >= 2) add(AvailableSeries("bodyfat", "Body Fat %", compareTertiary, "%", bodyFatData))
                if (leanMassData.size >= 2) add(AvailableSeries("lean", "Lean Mass", compareSecondary, "kg", leanMassData))
                if (kcalByDay.size >= 2) add(AvailableSeries("kcal", "Calories", compareAccent, "kcal", kcalByDay.map { (d, v) -> d to v.toDouble() }))
                if (sleepData.size >= 2) add(AvailableSeries("sleep", "Sleep", androidx.compose.ui.graphics.Color(0xFF9F7AEA), "h", sleepData))
                if (stepsData.size >= 2) add(AvailableSeries("steps", "Steps", androidx.compose.ui.graphics.Color(0xFF38B2AC), "k", stepsData.map { (d, v) -> d to v / 1000.0 }))
                if (bodyWaterData.size >= 2) add(AvailableSeries("water", "Body Water", comparePrimary.copy(alpha = 0.65f), "kg", bodyWaterData))
            }
        }

        // Drop selections that are no longer available when range changes
        LaunchedEffect(compareSeriesOptions) {
            val validIds = compareSeriesOptions.map { it.id }.toSet()
            selectedSeriesIds = selectedSeriesIds.intersect(validIds)
        }

        val selectedChartSeries = remember(selectedSeriesIds, compareSeriesOptions) {
            compareSeriesOptions.filter { it.id in selectedSeriesIds }
                .map { ChartSeries(it.label, it.color, it.unit, it.data) }
        }

        val correlationPairs = remember(selectedChartSeries) {
            if (selectedChartSeries.size < 2) emptyList()
            else buildList {
                for (i in 0 until selectedChartSeries.size) {
                    for (j in i + 1 until selectedChartSeries.size) {
                        val r = alignedPearson(selectedChartSeries[i].data, selectedChartSeries[j].data)
                        if (r != null) add(Triple(selectedChartSeries[i].label, selectedChartSeries[j].label, r))
                    }
                }
            }
        }

        if (compareSeriesOptions.isNotEmpty()) {
            InfoCard(label = "Compare Metrics", icon = Icons.Default.CompareArrows) {
                // Series picker chips
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    compareSeriesOptions.forEach { s ->
                        val isSelected = s.id in selectedSeriesIds
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedSeriesIds = if (isSelected) {
                                    selectedSeriesIds - s.id
                                } else if (selectedSeriesIds.size < 3) {
                                    selectedSeriesIds + s.id
                                } else {
                                    selectedSeriesIds
                                }
                            },
                            label = {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(s.color),
                                    )
                                    Text(s.label, fontSize = 12.sp)
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = s.color.copy(alpha = 0.18f),
                                selectedLabelColor = s.color,
                                selectedLeadingIconColor = s.color,
                            ),
                        )
                    }
                }

                when {
                    selectedChartSeries.size >= 2 -> {
                        Spacer(Modifier.height(8.dp))
                        MultiSeriesLineChart(
                            series = selectedChartSeries,
                            modifier = Modifier.height(180.dp),
                        )
                        // Correlation badges
                        if (correlationPairs.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            correlationPairs.forEach { (labelA, labelB, r) ->
                                val desc = correlationLabel(r)
                                val rColor = when {
                                    abs(r) >= 0.7 -> Secondary
                                    abs(r) >= 0.4 -> Primary
                                    abs(r) >= 0.2 -> OnSurfaceVariant
                                    else -> OnSurfaceVariant.copy(alpha = 0.5f)
                                }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CardSurface)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "$labelA × $labelB",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = OnSurface,
                                    )
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            desc,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = rColor,
                                        )
                                        Text(
                                            "r = ${"%+.2f".format(r)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = rColor,
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                            }
                        }
                    }
                    selectedChartSeries.size == 1 -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Select one more metric to compare",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }
                    else -> {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Select 2–3 metrics to overlay on a single chart with correlation stats",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Weight Trend Chart ──
        if (weightData.size >= 2) {
            InfoCard(label = stringResource(R.string.trends_weight), icon = Icons.Default.Scale) {
                val firstWeight = weightData.firstOrNull()?.second
                val lastDate = weightData.lastOrNull()?.first
                val firstDate = weightData.firstOrNull()?.first
                val actualDays = if (firstDate != null && lastDate != null)
                    java.time.temporal.ChronoUnit.DAYS.between(firstDate, lastDate).toInt().coerceAtLeast(1) else 1
                val refTarget = when (selectedRange) {
                    "All" -> goalWeight?.toDouble()
                    else -> firstWeight?.let { it - (weeklyRate ?: 0f).toDouble() * actualDays / 7.0 }
                }
                val dateLabels = weightData.map { (date, _) ->
                    val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "%d. %s".format(date.dayOfMonth, monthName)
                }
                val xLabels = weightData.map { (date, _) -> xAxisLabel(date, selectedRange == "7D") }
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
            InfoCard(label = stringResource(R.string.trends_weight), icon = Icons.Default.Scale) {
                Text(
                    stringResource(R.string.trends_no_weight_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                )
            }
        }

        // ── Body Fat % Trend ──
        if (bodyFatData.size >= 2) {
            InfoCard(label = "Body Fat %", icon = Icons.Default.Percent) {
                val labels = bodyFatData.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = bodyFatData.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = bodyFatData.mapIndexed { i, (_, v) -> i to v },
                    color = Tertiary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "%",
                    modifier = Modifier.height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatColumn("Avg", "%.1f%%".format(bodyFatData.map { it.second }.average()))
                    val delta = bodyFatData.last().second - bodyFatData.first().second
                    StatColumn("Change", "%+.1f%%".format(delta), if (delta < 0) Secondary else Tertiary)
                    StatColumn("Latest", "%.1f%%".format(bodyFatData.last().second))
                }
            }
        }

        // ── Lean Body Mass Trend ──
        if (leanMassData.size >= 2) {
            InfoCard(label = "Lean Mass", icon = Icons.Default.FitnessCenter) {
                val labels = leanMassData.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = leanMassData.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = leanMassData.mapIndexed { i, (_, v) -> i to v },
                    color = Secondary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "kg",
                    modifier = Modifier.height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatColumn("Avg", "%.1f kg".format(leanMassData.map { it.second }.average()))
                    val delta = leanMassData.last().second - leanMassData.first().second
                    StatColumn("Change", "%+.1f kg".format(delta), if (delta >= 0) Secondary else Tertiary)
                    StatColumn("Latest", "%.1f kg".format(leanMassData.last().second))
                }
            }
        }

        // ── Body Water Trend ──
        if (bodyWaterData.size >= 2) {
            InfoCard(label = "Body Water", icon = Icons.Default.WaterDrop) {
                val labels = bodyWaterData.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = bodyWaterData.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = bodyWaterData.mapIndexed { i, (_, v) -> i to v },
                    color = Primary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "kg",
                    modifier = Modifier.height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatColumn("Avg", "%.1f kg".format(bodyWaterData.map { it.second }.average()))
                    val delta = bodyWaterData.last().second - bodyWaterData.first().second
                    StatColumn("Change", "%+.1f kg".format(delta), if (delta >= 0) Secondary else Tertiary)
                    StatColumn("Latest", "%.1f kg".format(bodyWaterData.last().second))
                }
            }
        }

        // ── Calorie Trend ──
        if (kcalByDay.size >= 2) {
            InfoCard(label = stringResource(R.string.trends_calories), icon = Icons.Default.LocalFireDepartment) {
                val labels = kcalByDay.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = kcalByDay.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
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
            InfoCard(label = stringResource(R.string.trends_macros), icon = Icons.Default.DonutSmall) {
                val labels = macrosByDay.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = macrosByDay.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                val targets = dailyTargetKcal?.let {
                    com.fatlosstrack.domain.TdeeCalculator.macroTargets(it, goalBodyWeightKg = goalWeight, actualLeanMassKg = latestLeanMassKg)
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
            InfoCard(label = stringResource(R.string.trends_sleep), icon = Icons.Default.Bedtime) {
                val labels = sleepData.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = sleepData.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
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
            InfoCard(label = stringResource(R.string.trends_steps), icon = Icons.AutoMirrored.Filled.DirectionsWalk) {
                val labels = stepsData.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = stepsData.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
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
        val totalDays = when (selectedRange) {
            "7D" -> 7
            "1M" -> 30
            else -> logs.size.coerceAtLeast(1)
        }
        val loggingRate = if (totalDays > 0) daysWithMeals * 100 / totalDays else 0
        val daysWithAlcohol = meals.filter { it.hasAlcohol }.map { it.date }.distinct().size

        InfoCard(label = stringResource(R.string.trends_habits), icon = Icons.Default.CheckCircle) {
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

private data class AvailableSeries(
    val id: String,
    val label: String,
    val color: androidx.compose.ui.graphics.Color,
    val unit: String,
    val data: List<Pair<LocalDate, Double>>,
)

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
