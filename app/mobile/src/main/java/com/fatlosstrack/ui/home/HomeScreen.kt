package com.fatlosstrack.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.*
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.components.InfoCard
import com.fatlosstrack.ui.components.SimpleLineChart
import com.fatlosstrack.ui.components.MacroBarChart
import com.fatlosstrack.ui.components.TrendChart
import com.fatlosstrack.ui.log.*
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Home screen — "Am I on track?"
 *
 * 1. Goal progress header
 * 2. Weight trend chart (compact)
 * 3. Last-N-days stats + AI period summary
 * 4. Today & Yesterday cards (same as Log tab, with edit/add)
 */

/** Module-level cache: (dataFingerprint, summary). Survives recomposition & navigation. */
private var periodSummaryCache: Pair<String?, String?> = null to null

private fun dateLabelFor(date: LocalDate): String {
    val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
    return "${date.dayOfMonth}. $month"
}

private fun xAxisLabelFor(date: LocalDate, use7dDayNames: Boolean): String {
    return if (use7dDayNames) {
        date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .removeSuffix(".").take(3)
    } else {
        val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
        "${date.dayOfMonth}. $month"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    dailyLogDao: DailyLogDao,
    mealDao: MealDao,
    weightDao: WeightDao,
    preferencesManager: PreferencesManager,
    daySummaryGenerator: DaySummaryGenerator? = null,
    openAiService: OpenAiService? = null,
    onCameraForDate: (LocalDate) -> Unit = {},
) {
    val goalWeight by preferencesManager.goalWeight.collectAsState(initial = null)
    val startWeight by preferencesManager.startWeight.collectAsState(initial = null)
    val weeklyRate by preferencesManager.weeklyRate.collectAsState(initial = null)
    val startDateStr by preferencesManager.startDate.collectAsState(initial = null)

    // TDEE / daily target
    val savedSex by preferencesManager.sex.collectAsState(initial = null)
    val savedAge by preferencesManager.age.collectAsState(initial = null)
    val savedHeight by preferencesManager.heightCm.collectAsState(initial = null)
    val savedActivityLevel by preferencesManager.activityLevel.collectAsState(initial = "light")
    val dailyTargetKcal = remember(savedSex, savedAge, savedHeight, startWeight, weeklyRate, savedActivityLevel) {
        val sex = savedSex ?: return@remember null
        val age = savedAge ?: return@remember null
        val height = savedHeight ?: return@remember null
        val weight = startWeight ?: return@remember null
        val rate = weeklyRate ?: return@remember null
        com.fatlosstrack.domain.TdeeCalculator.dailyTarget(weight, height, age, sex, savedActivityLevel, rate)
    }

    val startDate = startDateStr?.let {
        try { LocalDate.parse(it) } catch (_: Exception) { null }
    }

    val today = LocalDate.now()
    val daysSinceStart = startDate?.let { ChronoUnit.DAYS.between(it, today).toInt().coerceAtLeast(1) }
    val lookbackDays = maxOf(7, daysSinceStart ?: 7)

    // Fetch lookbackDays of past data + today (for today's weight & DayCard)
    val since = today.minusDays(lookbackDays.toLong())
    val logs by dailyLogDao.getLogsSince(since).collectAsState(initial = emptyList())
    val meals by mealDao.getMealsSince(since).collectAsState(initial = emptyList())
    val weightEntries by weightDao.getEntriesSince(since).collectAsState(initial = emptyList())

    val scope = rememberCoroutineScope()

    // Sheet states
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mealSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val addMealSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedMeal by remember { mutableStateOf<MealEntry?>(null) }
    var addMealForDate by remember { mutableStateOf<LocalDate?>(null) }

    // Weight data for chart — include today's weight
    val weightData = remember(logs, weightEntries) {
        val map = mutableMapOf<LocalDate, Double>()
        weightEntries.forEach { map[it.date] = it.valueKg }
        logs.forEach { log -> log.weightKg?.let { map[log.date] = it } }
        map.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    val weights = weightData.map { it.second }
    val latestWeight = weightData.lastOrNull()?.second

    // Chart data for calorie/sleep/steps trends
    val kcalByDay = remember(meals) {
        meals.groupBy { it.date }
            .map { (date, dayMeals) -> date to dayMeals.sumOf { it.totalKcal } }
            .sortedBy { it.first }
    }
    val sleepChartData = remember(logs) {
        logs.filter { it.sleepHours != null }
            .sortedBy { it.date }
            .map { it.date to it.sleepHours!! }
    }
    val stepsChartData = remember(logs) {
        logs.filter { it.steps != null }
            .sortedBy { it.date }
            .map { it.date to it.steps!! }
    }
    val macrosByDay = remember(meals) {
        meals.groupBy { it.date }
            .map { (date, dayMeals) ->
                date to Triple(
                    dayMeals.sumOf { it.totalProteinG },
                    dayMeals.sumOf { it.totalCarbsG },
                    dayMeals.sumOf { it.totalFatG },
                )
            }
            .filter { (_, m) -> m.first + m.second + m.third > 0 }
            .sortedBy { it.first }
    }

    // Stats — exclude today (shown separately as DayCard)
    val pastLogs = logs.filter { it.date != today }
    val pastMeals = meals.filter { it.date != today }
    val totalMeals = pastMeals.size
    val totalKcal = pastMeals.sumOf { it.totalKcal }
    val totalProtein = pastMeals.sumOf { it.totalProteinG }
    val totalCarbs = pastMeals.sumOf { it.totalCarbsG }
    val totalFat = pastMeals.sumOf { it.totalFatG }
    // Divide kcal by days that actually have meals logged, not total period
    val daysWithMeals = pastMeals.map { it.date }.distinct().size
    val avgKcalPerDay = if (daysWithMeals > 0) totalKcal / daysWithMeals else null
    val avgProteinPerDay = if (daysWithMeals > 0) totalProtein / daysWithMeals else null
    val avgCarbsPerDay = if (daysWithMeals > 0) totalCarbs / daysWithMeals else null
    val avgFatPerDay = if (daysWithMeals > 0) totalFat / daysWithMeals else null
    val avgSteps = pastLogs.mapNotNull { it.steps }.let { if (it.isNotEmpty()) it.average().toInt() else null }
    val avgSleep = pastLogs.mapNotNull { it.sleepHours }.let { if (it.isNotEmpty()) it.average() else null }
    val daysLogged = pastLogs.count { log ->
        log.weightKg != null || log.steps != null || log.sleepHours != null ||
                pastMeals.any { it.date == log.date }
    }

    // Goal progress
    val startW = startWeight
    val goalW = goalWeight
    val progressPct = if (startW != null && goalW != null && latestWeight != null && startW > goalW) {
        ((startW - latestWeight.toFloat()) / (startW - goalW) * 100).coerceIn(0f, 100f)
    } else null

    val weeksToGoal = if (goalW != null && latestWeight != null && weeklyRate != null && weeklyRate!! > 0 && latestWeight > goalW) {
        ((latestWeight - goalW) / weeklyRate!!).toInt()
    } else null

    // AI period summary — cached by data fingerprint
    // Build a fingerprint from the actual data so we only regenerate when content changes
    // NOTE: deliberately excludes daySummary to avoid feedback loop (summary changes → fingerprint changes → new summary)
    val dataFingerprint = remember(pastLogs, pastMeals) {
        val logSig = pastLogs.sumOf { (it.weightKg?.hashCode() ?: 0) + (it.steps ?: 0) + (it.sleepHours?.hashCode() ?: 0) }
        val mealSig = pastMeals.sumOf { it.totalKcal + it.description.hashCode() }
        "${pastLogs.size}-${pastMeals.size}-$logSig-$mealSig"
    }

    var periodSummary by remember { mutableStateOf(periodSummaryCache.second) }
    var periodSummaryLoading by remember { mutableStateOf(false) }
    var lastFingerprint by remember { mutableStateOf(periodSummaryCache.first) }

    LaunchedEffect(dataFingerprint) {
        // If fingerprint hasn't changed and we have a cached summary, skip
        if (dataFingerprint == lastFingerprint && periodSummary != null) {
            AppLogger.instance?.hc("PeriodSummary: fingerprint unchanged ($dataFingerprint), skipping")
            return@LaunchedEffect
        }
        // If the module-level cache matches, use it without calling AI
        if (dataFingerprint == periodSummaryCache.first && periodSummaryCache.second != null) {
            AppLogger.instance?.hc("PeriodSummary: using module-level cache (fingerprint=$dataFingerprint)")
            periodSummary = periodSummaryCache.second
            lastFingerprint = dataFingerprint
            return@LaunchedEffect
        }
        if (openAiService == null || logs.isEmpty()) {
            AppLogger.instance?.hc("PeriodSummary: skipped (openAiService=${openAiService != null}, logs=${logs.size})")
            return@LaunchedEffect
        }
        AppLogger.instance?.hc("PeriodSummary: fingerprint changed (${lastFingerprint} → $dataFingerprint), calling AI")
        periodSummaryLoading = true
        withContext(Dispatchers.IO) {
            try {
                if (!openAiService.hasApiKey()) {
                    AppLogger.instance?.hc("PeriodSummary: skipped — no API key")
                    periodSummaryLoading = false
                    return@withContext
                }
                val prompt = buildString {
                    appendLine("Summarize the user's last $lookbackDays days of progress toward their fat loss goal.")
                    if (startDate != null) appendLine("Goal start date: $startDate (${daysSinceStart ?: 0} days ago)")
                    appendLine("Today: $today")
                    if (goalW != null) appendLine("Goal weight: %.1f kg".format(goalW))
                    if (startW != null) appendLine("Start weight: %.1f kg".format(startW))
                    if (latestWeight != null) appendLine("Current weight: %.1f kg".format(latestWeight))
                    if (weeklyRate != null) appendLine("Target rate: %.1f kg/week".format(weeklyRate))
                    appendLine()
                    appendLine("Period stats (last $lookbackDays days, excluding today):")
                    appendLine("- Meals logged: $totalMeals")
                    if (avgKcalPerDay != null) appendLine("- Avg kcal/day: $avgKcalPerDay")
                    if (avgProteinPerDay != null && avgProteinPerDay > 0) appendLine("- Avg protein/day: ${avgProteinPerDay}g")
                    if (avgCarbsPerDay != null && avgCarbsPerDay > 0) appendLine("- Avg carbs/day: ${avgCarbsPerDay}g")
                    if (avgFatPerDay != null && avgFatPerDay > 0) appendLine("- Avg fat/day: ${avgFatPerDay}g")
                    if (avgSteps != null) appendLine("- Avg steps/day: $avgSteps")
                    if (avgSleep != null) appendLine("- Avg sleep: %.1fh".format(avgSleep))
                    appendLine("- Days with data: $daysLogged / $lookbackDays")
                    if (weights.size >= 2) {
                        appendLine("- Weight change: %.1f → %.1f kg".format(weights.first(), weights.last()))
                    }
                }
                val systemPrompt = """You are FatLoss Track's weekly coach. Given a user's multi-day stats and their goal, write a SHORT motivational coaching summary (2-3 sentences, under 200 characters).

Rules:
- Be specific about how these days helped or hurt their goal
- Reference actual numbers when relevant
- Supportive but honest tone
- Plain text only, no markdown, no quotes
- Focus on the trend and what to do next"""

                val result = openAiService.chat(prompt, systemPrompt)
                result.onSuccess { summary ->
                    val trimmed = summary.trim().removeSurrounding("\"")
                    periodSummary = trimmed
                    lastFingerprint = dataFingerprint
                    periodSummaryCache = dataFingerprint to trimmed
                    AppLogger.instance?.hc("PeriodSummary: AI returned ${trimmed.take(60)}…")
                }.onFailure { e ->
                    AppLogger.instance?.error("PeriodSummary", "AI call failed", e)
                }
            } catch (_: Exception) { }
            periodSummaryLoading = false
        }
    }

    // Today & yesterday data
    val todayLog = logs.find { it.date == today }
    val todayMeals = meals.filter { it.date == today }
    val yesterday = today.minusDays(1)
    val yesterdayLog = logs.find { it.date == yesterday }
    val yesterdayMeals = meals.filter { it.date == yesterday }

    val mealsByDate = meals.groupBy { it.date }
    val logsByDate = logs.associateBy { it.date }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Goal Progress ──
        if (progressPct != null && goalW != null && latestWeight != null) {
            InfoCard(label = stringResource(R.string.home_goal_progress)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text(
                            "%.1f kg".format(latestWeight),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = OnSurface,
                        )
                        Text(
                            stringResource(R.string.home_goal_arrow, goalW),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "%.0f%%".format(progressPct),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Primary,
                        )
                        if (weeksToGoal != null) {
                            Text(
                                stringResource(R.string.home_weeks_left, weeksToGoal),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { progressPct / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = Primary,
                    trackColor = SurfaceVariant,
                )
            }
        }

        // ── Chart Carousel (Weight / Calories / Sleep / Steps) ──
        run {
            val allWeightEntries by weightDao.getAllEntries().collectAsState(initial = emptyList())
            var chartRange by remember { mutableStateOf("7d") }
            val trendCutoff = when (chartRange) {
                "7d" -> today.minusDays(7)
                "1m" -> today.minusDays(30)
                else -> LocalDate.MIN
            }

            val chartData = remember(chartRange, weightData, allWeightEntries) {
                val cutoff = trendCutoff
                if (chartRange == "all" || chartRange == "1m") {
                    val src = if (chartRange == "all") allWeightEntries else allWeightEntries.filter { it.date >= cutoff }
                    src.sortedBy { it.date }.map { it.date to it.valueKg }
                } else {
                    weightData.filter { (date, _) -> date >= cutoff }
                }
            }
            val filteredKcal = remember(kcalByDay, chartRange) {
                kcalByDay.filter { (d, _) -> d >= trendCutoff }
            }
            val filteredSleep = remember(sleepChartData, chartRange) {
                sleepChartData.filter { (d, _) -> d >= trendCutoff }
            }
            val filteredSteps = remember(stepsChartData, chartRange) {
                stepsChartData.filter { (d, _) -> d >= trendCutoff }
            }
            val filteredMacros = remember(macrosByDay, chartRange) {
                macrosByDay.filter { (d, _) -> d >= trendCutoff }
            }

            // Build the list of available chart pages
            data class ChartPage(val label: String, val icon: ImageVector, val titleRes: Int)
            val pages = remember(chartData, filteredKcal, filteredSleep, filteredSteps, filteredMacros) {
                buildList {
                    if (chartData.size >= 2) add(ChartPage("weight", Icons.Default.MonitorWeight, R.string.home_weight_trend))
                    if (filteredKcal.size >= 2) add(ChartPage("kcal", Icons.Default.LocalFireDepartment, R.string.trends_calories))
                    if (filteredMacros.size >= 2) add(ChartPage("macros", Icons.Default.PieChart, R.string.trends_macros))
                    if (filteredSleep.size >= 2) add(ChartPage("sleep", Icons.Default.Bedtime, R.string.trends_sleep))
                    if (filteredSteps.size >= 2) add(ChartPage("steps", Icons.AutoMirrored.Filled.DirectionsWalk, R.string.trends_steps))
                }
            }

            if (pages.isNotEmpty()) {
                val pagerState = rememberPagerState(pageCount = { pages.size })

                InfoCard(label = null) {
                    // Header: title + page indicator icons + range chips
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Current chart title + page indicator icons
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                stringResource(pages[pagerState.currentPage].titleRes).uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = Primary,
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                pages.forEachIndexed { idx, page ->
                                    val selected = pagerState.currentPage == idx
                                    val scope = rememberCoroutineScope()
                                    Icon(
                                        imageVector = page.icon,
                                        contentDescription = page.label,
                                        tint = if (selected) Primary else OnSurfaceVariant.copy(alpha = 0.4f),
                                        modifier = Modifier
                                            .size(if (selected) 20.dp else 16.dp)
                                            .clickable { scope.launch { pagerState.animateScrollToPage(idx) } },
                                    )
                                }
                            }
                        }
                        // Range toggle chips
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("7d", "1m", "all").forEach { range ->
                                val selected = chartRange == range
                                Text(
                                    text = range.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                                    color = if (selected) Primary else OnSurfaceVariant,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (selected) Primary.copy(alpha = 0.15f) else Color.Transparent)
                                        .clickable { chartRange = range }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }

                    // Swipable chart pager
                    val is7d = chartRange == "7d"
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxWidth(),
                        beyondViewportPageCount = 1,
                    ) { pageIdx ->
                        val page = pages[pageIdx]
                        when (page.label) {
                            "weight" -> {
                                val firstDate = chartData.first().first
                                val dataPoints = chartData.map { (date, w) ->
                                    ChronoUnit.DAYS.between(firstDate, date).toInt() to w
                                }
                                val dateLabels = chartData.map { (date, _) -> dateLabelFor(date) }
                                val xLabels = chartData.map { (date, _) -> xAxisLabelFor(date, is7d) }
                                TrendChart(
                                    dataPoints = dataPoints,
                                    dateLabels = dateLabels,
                                    xAxisLabels = xLabels,
                                    startLineKg = chartData.firstOrNull()?.second,
                                    targetLineKg = when (chartRange) {
                                        "all" -> goalWeight?.toDouble()
                                        else -> {
                                            val firstW = chartData.firstOrNull()?.second
                                            val f = chartData.firstOrNull()?.first
                                            val l = chartData.lastOrNull()?.first
                                            val days = if (f != null && l != null)
                                                ChronoUnit.DAYS.between(f, l).toInt().coerceAtLeast(1) else 7
                                            firstW?.let { it - (weeklyRate ?: 0f).toDouble() * days / 7.0 }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().height(130.dp),
                                )
                            }
                            "kcal" -> {
                                val labels = filteredKcal.map { (d, _) -> dateLabelFor(d) }
                                val xLabels = filteredKcal.map { (d, _) -> xAxisLabelFor(d, is7d) }
                                SimpleLineChart(
                                    data = filteredKcal.mapIndexed { i, (_, kcal) -> i to kcal.toDouble() },
                                    color = Secondary,
                                    dateLabels = labels,
                                    xAxisLabels = xLabels,
                                    unit = "kcal",
                                    refLineValue = dailyTargetKcal?.toDouble(),
                                    refLineColor = Secondary,
                                    refLineLabel = dailyTargetKcal?.let { "$it kcal" },
                                    modifier = Modifier.fillMaxWidth().height(130.dp),
                                )
                            }
                            "macros" -> {
                                val labels = filteredMacros.map { (d, _) -> dateLabelFor(d) }
                                val xLabels = filteredMacros.map { (d, _) -> xAxisLabelFor(d, is7d) }
                                val targets = dailyTargetKcal?.let {
                                    com.fatlosstrack.domain.TdeeCalculator.macroTargets(it)
                                }
                                MacroBarChart(
                                    data = filteredMacros.map { (_, m) -> m },
                                    macroTargets = targets,
                                    dateLabels = labels,
                                    xAxisLabels = xLabels,
                                    colors = Triple(Primary, Tertiary, Accent),
                                    modifier = Modifier.fillMaxWidth().height(130.dp),
                                )
                            }
                            "sleep" -> {
                                val labels = filteredSleep.map { (d, _) -> dateLabelFor(d) }
                                val xLabels = filteredSleep.map { (d, _) -> xAxisLabelFor(d, is7d) }
                                SimpleLineChart(
                                    data = filteredSleep.mapIndexed { i, (_, h) -> i to h },
                                    color = Primary,
                                    dateLabels = labels,
                                    xAxisLabels = xLabels,
                                    unit = "h",
                                    modifier = Modifier.fillMaxWidth().height(130.dp),
                                )
                            }
                            "steps" -> {
                                val labels = filteredSteps.map { (d, _) -> dateLabelFor(d) }
                                val xLabels = filteredSteps.map { (d, _) -> xAxisLabelFor(d, is7d) }
                                SimpleLineChart(
                                    data = filteredSteps.mapIndexed { i, (_, s) -> i to s.toDouble() },
                                    color = Secondary,
                                    dateLabels = labels,
                                    xAxisLabels = xLabels,
                                    unit = "steps",
                                    modifier = Modifier.fillMaxWidth().height(130.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // ── Period Stats ──
        InfoCard(label = stringResource(R.string.home_last_n_days, lookbackDays)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniStat(Icons.Default.Restaurant, "$totalMeals", stringResource(R.string.stat_label_meals))
                if (avgKcalPerDay != null) MiniStat(Icons.Default.LocalFireDepartment, "$avgKcalPerDay", stringResource(R.string.stat_label_kcal_day))
                if (avgProteinPerDay != null && avgProteinPerDay > 0) MiniStat(Icons.Default.FitnessCenter, "${avgProteinPerDay}g", stringResource(R.string.stat_label_protein_day))
                if (avgCarbsPerDay != null && avgCarbsPerDay > 0) MiniStat(Icons.Default.Grain, "${avgCarbsPerDay}g", stringResource(R.string.stat_label_carbs_day))
                if (avgFatPerDay != null && avgFatPerDay > 0) MiniStat(Icons.Default.WaterDrop, "${avgFatPerDay}g", stringResource(R.string.stat_label_fat_day))
                if (avgSteps != null) MiniStat(Icons.AutoMirrored.Filled.DirectionsWalk, stringResource(R.string.stat_steps_k, avgSteps / 1000), stringResource(R.string.stat_label_steps_day))
                if (avgSleep != null) MiniStat(Icons.Default.Bedtime, "%.1fh".format(avgSleep), stringResource(R.string.stat_label_sleep_day))
            }
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.home_logged_days, daysLogged, lookbackDays),
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )

            // AI period summary
            if (periodSummaryLoading) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Primary.copy(alpha = 0.5f),
                    trackColor = Primary.copy(alpha = 0.1f),
                )
            } else if (!periodSummary.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(periodSummary!!, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                }
            }
        }

        // ── Today Card ──
        DayCard(
            date = today,
            log = todayLog,
            meals = todayMeals,
            dailyTargetKcal = dailyTargetKcal,
            onEdit = { editingDate = today },
            onMealClick = { selectedMeal = it },
            onAddMeal = { addMealForDate = today },
        )

        // ── Yesterday Card ──
        if (yesterdayLog != null || yesterdayMeals.isNotEmpty()) {
            DayCard(
                date = yesterday,
                log = yesterdayLog,
                meals = yesterdayMeals,
                dailyTargetKcal = dailyTargetKcal,
                onEdit = { editingDate = yesterday },
                onMealClick = { selectedMeal = it },
                onAddMeal = { addMealForDate = yesterday },
            )
        }

        Spacer(Modifier.height(80.dp))
    }

    // ── Edit sheets (same as Log tab) ──
    if (editingDate != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { editSheetState.hide() }.invokeOnCompletion {
                    editingDate = null
                }
            },
            sheetState = editSheetState,
            containerColor = CardSurface,
        ) {
            DailyLogEditSheet(
                date = editingDate!!,
                existingLog = logsByDate[editingDate!!],
                onSave = { scope.launch {
                    dailyLogDao.upsert(it)
                    launchSummary(it.date, dailyLogDao, daySummaryGenerator, "HomeScreen:dailyLogEdit")
                    editSheetState.hide()
                    editingDate = null
                } },
                onDismiss = {
                    scope.launch { editSheetState.hide() }.invokeOnCompletion {
                        editingDate = null
                    }
                },
            )
        }
    }

    if (selectedMeal != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { mealSheetState.hide() }.invokeOnCompletion {
                    selectedMeal = null
                }
            },
            sheetState = mealSheetState,
            containerColor = CardSurface,
        ) {
            MealEditSheet(
                meal = selectedMeal!!,
                onSave = { updated -> scope.launch {
                    mealDao.update(updated)
                    launchSummary(updated.date, dailyLogDao, daySummaryGenerator, "HomeScreen:mealEdit")
                    mealSheetState.hide()
                    selectedMeal = null
                } },
                onDelete = { scope.launch {
                    val meal = selectedMeal!!
                    mealDao.delete(meal)
                    launchSummary(meal.date, dailyLogDao, daySummaryGenerator, "HomeScreen:mealDelete")
                    mealSheetState.hide()
                    selectedMeal = null
                } },
                onDismiss = {
                    scope.launch { mealSheetState.hide() }.invokeOnCompletion {
                        selectedMeal = null
                    }
                },
                openAiService = openAiService,
            )
        }
    }

    if (addMealForDate != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { addMealSheetState.hide() }.invokeOnCompletion {
                    addMealForDate = null
                }
            },
            sheetState = addMealSheetState,
            containerColor = CardSurface,
        ) {
            AddMealSheet(
                date = addMealForDate!!,
                onSave = { newMeal -> scope.launch {
                    mealDao.insert(newMeal)
                    launchSummary(newMeal.date, dailyLogDao, daySummaryGenerator, "HomeScreen:mealAdd")
                    addMealSheetState.hide()
                    addMealForDate = null
                } },
                onDismiss = {
                    scope.launch { addMealSheetState.hide() }.invokeOnCompletion {
                        addMealForDate = null
                    }
                },
                onCamera = {
                    val date = addMealForDate!!
                    scope.launch { addMealSheetState.hide() }.invokeOnCompletion {
                        addMealForDate = null
                        onCameraForDate(date)
                    }
                },
            )
        }
    }
}

@Composable
private fun MiniStat(icon: ImageVector, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, tint = Primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(2.dp))
        Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
    }
}
