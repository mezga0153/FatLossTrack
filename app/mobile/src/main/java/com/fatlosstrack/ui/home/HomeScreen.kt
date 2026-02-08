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
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.*
import com.fatlosstrack.ui.components.InfoCard
import com.fatlosstrack.ui.components.TrendChart
import com.fatlosstrack.ui.theme.*
import java.time.LocalDate

/**
 * Home screen — "Am I on track this week?"
 *
 * 1. Goal progress header
 * 2. 7-Day weight trend chart
 * 3. This-week stats (avg weight, meals, steps, sleep)
 * 4. Today & Yesterday summaries
 */
@Composable
fun HomeScreen(
    dailyLogDao: DailyLogDao,
    mealDao: MealDao,
    weightDao: WeightDao,
    preferencesManager: PreferencesManager,
) {
    val since7 = LocalDate.now().minusDays(6)
    val logs by dailyLogDao.getLogsSince(since7).collectAsState(initial = emptyList())
    val meals by mealDao.getMealsSince(since7).collectAsState(initial = emptyList())
    val weightEntries by weightDao.getEntriesSince(since7).collectAsState(initial = emptyList())

    val goalWeight by preferencesManager.goalWeight.collectAsState(initial = null)
    val startWeight by preferencesManager.startWeight.collectAsState(initial = null)
    val weeklyRate by preferencesManager.weeklyRate.collectAsState(initial = null)

    val today = LocalDate.now()
    val todayLog = logs.find { it.date == today }
    val todayMeals = meals.filter { it.date == today }

    // Weight data for chart — prefer DailyLog weights, supplement with WeightEntry
    val weightData = remember(logs, weightEntries) {
        val map = mutableMapOf<LocalDate, Double>()
        weightEntries.forEach { map[it.date] = it.valueKg }
        logs.forEach { log -> log.weightKg?.let { map[log.date] = it } }
        map.entries.sortedBy { it.key }.map { it.key to it.value }
    }

    // Stats
    val weights7d = weightData.map { it.second }
    val avg7d = if (weights7d.isNotEmpty()) weights7d.average() else null
    val latestWeight = weightData.lastOrNull()?.second
    val totalMeals7d = meals.size
    val totalKcal7d = meals.sumOf { it.totalKcal }
    val avgKcalPerDay = if (meals.isNotEmpty()) totalKcal7d / 7 else null
    val avgSteps = logs.mapNotNull { it.steps }.let { if (it.isNotEmpty()) it.average().toInt() else null }
    val avgSleep = logs.mapNotNull { it.sleepHours }.let { if (it.isNotEmpty()) it.average() else null }
    val daysLogged = logs.count { log ->
        log.weightKg != null || log.steps != null || log.sleepHours != null ||
                meals.any { it.date == log.date }
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

        // ── 7-Day Weight Trend ──
        if (weightData.size >= 2) {
            InfoCard(label = "7-Day Trend") {
                val dataPoints = weightData.mapIndexed { i, (_, w) -> i to w }
                TrendChart(
                    dataPoints = dataPoints,
                    avg7d = avg7d ?: 0.0,
                    targetKg = goalW?.toDouble() ?: 80.0,
                    confidenceLow = (avg7d ?: 0.0) - 0.5,
                    confidenceHigh = (avg7d ?: 0.0) + 0.5,
                )
                Spacer(Modifier.height(8.dp))
                if (avg7d != null) {
                    Text(
                        "7-day avg: %.1f kg".format(avg7d),
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurface,
                    )
                }
            }
        } else {
            InfoCard(label = "7-Day Trend") {
                Text(
                    "Not enough weight data yet. Log weight or sync Health Connect.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                )
            }
        }

        // ── Weekly Stats ──
        InfoCard(label = "This Week") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniStat(Icons.Default.Restaurant, "$totalMeals7d", "meals")
                if (avgKcalPerDay != null) MiniStat(Icons.Default.LocalFireDepartment, "$avgKcalPerDay", "kcal/day")
                if (avgSteps != null) MiniStat(Icons.AutoMirrored.Filled.DirectionsWalk, "${avgSteps / 1000}k", "steps/day")
                if (avgSleep != null) MiniStat(Icons.Default.Bedtime, "%.1fh".format(avgSleep), "sleep/day")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Logged data on $daysLogged of 7 days",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
        }

        // ── Today Card ──
        DaySummaryCard("Today", todayLog, todayMeals)

        // ── Yesterday Card ──
        val yesterday = today.minusDays(1)
        val yesterdayLog = logs.find { it.date == yesterday }
        val yesterdayMeals = meals.filter { it.date == yesterday }
        if (yesterdayLog != null || yesterdayMeals.isNotEmpty()) {
            DaySummaryCard("Yesterday", yesterdayLog, yesterdayMeals)
        }

        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun DaySummaryCard(label: String, log: DailyLog?, meals: List<MealEntry>) {
    InfoCard(label = label) {
        if (log == null && meals.isEmpty()) {
            Text(
                "No data logged yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    if (log?.weightKg != null) "%.1f kg".format(log.weightKg) else "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (meals.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${meals.size}", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                            Text("meals", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${meals.sumOf { it.totalKcal }}", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                            Text("kcal", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                    }
                    if (log?.steps != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${log.steps / 1000}k", style = MaterialTheme.typography.titleMedium, color = OnSurface)
                            Text("steps", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                        }
                    }
                }
            }

            if (!log?.daySummary.isNullOrBlank() && log?.daySummary != "⏳") {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(log!!.daySummary!!, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                }
            }
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
