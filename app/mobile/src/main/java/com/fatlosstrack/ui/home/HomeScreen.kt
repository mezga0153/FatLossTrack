package com.fatlosstrack.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.*
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.components.InfoCard
import com.fatlosstrack.ui.components.TrendChart
import com.fatlosstrack.ui.log.*
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.temporal.ChronoUnit

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

    // Stats — exclude today (shown separately as DayCard)
    val pastLogs = logs.filter { it.date != today }
    val pastMeals = meals.filter { it.date != today }
    val totalMeals = pastMeals.size
    val totalKcal = pastMeals.sumOf { it.totalKcal }
    // Divide kcal by days that actually have meals logged, not total period
    val daysWithMeals = pastMeals.map { it.date }.distinct().size
    val avgKcalPerDay = if (daysWithMeals > 0) totalKcal / daysWithMeals else null
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
    val dataFingerprint = remember(pastLogs, pastMeals) {
        val logSig = pastLogs.sumOf { (it.weightKg?.hashCode() ?: 0) + (it.steps ?: 0) + (it.sleepHours?.hashCode() ?: 0) + (it.daySummary?.hashCode() ?: 0) }
        val mealSig = pastMeals.sumOf { it.totalKcal + it.description.hashCode() }
        "${pastLogs.size}-${pastMeals.size}-$logSig-$mealSig"
    }

    var periodSummary by remember { mutableStateOf(periodSummaryCache.second) }
    var periodSummaryLoading by remember { mutableStateOf(false) }
    var lastFingerprint by remember { mutableStateOf(periodSummaryCache.first) }

    LaunchedEffect(dataFingerprint) {
        // If fingerprint hasn't changed and we have a cached summary, skip
        if (dataFingerprint == lastFingerprint && periodSummary != null) return@LaunchedEffect
        // If the module-level cache matches, use it without calling AI
        if (dataFingerprint == periodSummaryCache.first && periodSummaryCache.second != null) {
            periodSummary = periodSummaryCache.second
            lastFingerprint = dataFingerprint
            return@LaunchedEffect
        }
        if (openAiService == null || logs.isEmpty()) return@LaunchedEffect
        periodSummaryLoading = true
        withContext(Dispatchers.IO) {
            try {
                if (!openAiService.hasApiKey()) {
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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Goal Progress ──
        if (progressPct != null && goalW != null && latestWeight != null) {
            InfoCard(label = "Goal Progress") {
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
                            "→ %.1f kg".format(goalW),
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
                                "~$weeksToGoal weeks left",
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

        // ── Weight Trend (compact) ──
        if (weightData.size >= 2) {
            InfoCard(label = "Weight Trend") {
                val dataPoints = weightData.mapIndexed { i, (_, w) -> i to w }
                val avg = if (weights.isNotEmpty()) weights.average() else 0.0
                TrendChart(
                    dataPoints = dataPoints,
                    avg7d = avg,
                    targetKg = goalW?.toDouble() ?: 80.0,
                    confidenceLow = avg - 0.5,
                    confidenceHigh = avg + 0.5,
                    modifier = Modifier.height(120.dp),
                )
            }
        }

        // ── Period Stats ──
        InfoCard(label = "Last $lookbackDays days") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniStat(Icons.Default.Restaurant, "$totalMeals", "meals")
                if (avgKcalPerDay != null) MiniStat(Icons.Default.LocalFireDepartment, "$avgKcalPerDay", "kcal/day")
                if (avgSteps != null) MiniStat(Icons.AutoMirrored.Filled.DirectionsWalk, "${avgSteps / 1000}k", "steps/day")
                if (avgSleep != null) MiniStat(Icons.Default.Bedtime, "%.1fh".format(avgSleep), "sleep/day")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Logged data on $daysLogged of $lookbackDays days",
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
                onEdit = { editingDate = yesterday },
                onMealClick = { selectedMeal = it },
                onAddMeal = { addMealForDate = yesterday },
            )
        }

        Spacer(Modifier.height(80.dp))
    }

    // ── Edit sheets (same as Log tab) ──
    if (editingDate != null) {
        ModalBottomSheet(onDismissRequest = { editingDate = null }, sheetState = editSheetState, containerColor = CardSurface) {
            DailyLogEditSheet(
                date = editingDate!!,
                existingLog = logsByDate[editingDate!!],
                onSave = { scope.launch {
                    dailyLogDao.upsert(it)
                    launchSummary(it.date, dailyLogDao, daySummaryGenerator)
                    editingDate = null
                } },
                onDismiss = { editingDate = null },
            )
        }
    }

    if (selectedMeal != null) {
        ModalBottomSheet(onDismissRequest = { selectedMeal = null }, sheetState = mealSheetState, containerColor = CardSurface) {
            MealEditSheet(
                meal = selectedMeal!!,
                onSave = { updated -> scope.launch {
                    mealDao.update(updated)
                    launchSummary(updated.date, dailyLogDao, daySummaryGenerator)
                    selectedMeal = null
                } },
                onDelete = { scope.launch {
                    val meal = selectedMeal!!
                    mealDao.delete(meal)
                    launchSummary(meal.date, dailyLogDao, daySummaryGenerator)
                    selectedMeal = null
                } },
                onDismiss = { selectedMeal = null },
            )
        }
    }

    if (addMealForDate != null) {
        ModalBottomSheet(onDismissRequest = { addMealForDate = null }, sheetState = addMealSheetState, containerColor = CardSurface) {
            AddMealSheet(
                date = addMealForDate!!,
                onSave = { newMeal -> scope.launch {
                    mealDao.insert(newMeal)
                    launchSummary(newMeal.date, dailyLogDao, daySummaryGenerator)
                    addMealForDate = null
                } },
                onDismiss = { addMealForDate = null },
                onCamera = {
                    val date = addMealForDate!!
                    addMealForDate = null
                    onCameraForDate(date)
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
