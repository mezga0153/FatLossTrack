package com.fatlosstrack.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.displayTime
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
import com.fatlosstrack.ui.log.LogSheetHost
import com.fatlosstrack.ui.log.mealTypeLabel
import com.fatlosstrack.ui.log.rememberLogSheetState
import com.fatlosstrack.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendsScreen(
    state: TrendsStateHolder,
) {
    val context = LocalContext.current
    val sheetState = rememberLogSheetState()
    var selectedRange by remember { mutableStateOf("1M") }
    val ranges = listOf("7D", "1M", "All", "Custom")

    // Handle selected metric from navigation (e.g., clicked stat chip)
    val selectedMetric by remember { state.selectedMetric }
    // Set range to 1M when arriving from a stat chip
    LaunchedEffect(selectedMetric) {
        if (selectedMetric != null) selectedRange = "1M"
    }

    // Custom date range state
    var customFrom by remember { mutableStateOf<LocalDate?>(null) }
    var customTo by remember { mutableStateOf<LocalDate?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    // Map TrendMetric to series IDs for auto-selection
    val metricToSeriesId = mapOf(
        TrendMetric.WEIGHT to "weight",
        TrendMetric.STEPS to "steps",
        TrendMetric.SLEEP to "sleep",
        TrendMetric.HEART_RATE to "resting_hr",
        TrendMetric.CALORIES to "kcal",
        TrendMetric.BODY_FAT to "bodyfat",
        TrendMetric.LEAN_MASS to "lean",
        TrendMetric.BODY_WATER to "water",
        TrendMetric.BONE_MASS to "bone",
        TrendMetric.BLOOD_SUGAR to "bloodsugar",
    )
    val autoSelectSeriesId = selectedMetric?.let { metricToSeriesId[it] }

    val since = when (selectedRange) {
        "7D" -> LocalDate.now().minusDays(6L)
        "1M" -> LocalDate.now().minusDays(29L)
        "Custom" -> customFrom ?: LocalDate.now().minusDays(29L)
        else -> LocalDate.of(2000, 1, 1) // All
    }
    val isAllRange = selectedRange == "All"
    val customEndDate = if (selectedRange == "Custom") customTo ?: LocalDate.now() else LocalDate.now()

    val rawLogs by (if (isAllRange) state.allLogs() else state.logsSince(since)).collectAsState(initial = emptyList())
    val rawMeals by (if (isAllRange) state.allMeals() else state.mealsSince(since)).collectAsState(initial = emptyList())
    val rawWeightEntries by (if (isAllRange) state.allWeights() else state.weightsSince(since)).collectAsState(initial = emptyList())

    // Apply customTo filter for Custom range
    val logs = remember(rawLogs, selectedRange, customTo) {
        if (selectedRange == "Custom" && customTo != null) rawLogs.filter { it.date <= customTo!! } else rawLogs
    }
    val meals = remember(rawMeals, selectedRange, customTo) {
        if (selectedRange == "Custom" && customTo != null) rawMeals.filter { it.date <= customTo!! } else rawMeals
    }
    val weightEntries = remember(rawWeightEntries, selectedRange, customTo) {
        if (selectedRange == "Custom" && customTo != null) rawWeightEntries.filter { it.date <= customTo!! } else rawWeightEntries
    }

    // Blood glucose raw readings
    val bgFrom = remember(since, isAllRange) {
        if (isAllRange) Instant.EPOCH else since.atStartOfDay(ZoneId.systemDefault()).toInstant()
    }
    val bgTo = remember(customTo, selectedRange) {
        val toDate = if (selectedRange == "Custom" && customTo != null) customTo!! else LocalDate.now()
        toDate.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant()
    }
    val bgFlow = remember(bgFrom, bgTo, isAllRange) {
        if (isAllRange) state.allBgReadings() else state.bgReadingsBetween(bgFrom, bgTo)
    }
    val bgReadings by bgFlow.collectAsState(initial = emptyList())

    val goalWeight by state.goalWeight.collectAsState(initial = null)
    val weeklyRate by state.weeklyRate.collectAsState(initial = null)
    val startWeight by state.startWeight.collectAsState(initial = null)

    // TDEE / daily target
    val dailyTargetKcal = rememberDailyTargetKcal(state.preferencesManager)
    val sex by state.preferencesManager.sex.collectAsState(initial = null)
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
    val bloodSugarData = remember(logs) {
        logs.filter { it.bloodSugarMmol != null }
            .sortedBy { it.date }
            .map { it.date to it.bloodSugarMmol!! }
    }

    // Resting heart rate data
    val restingHrData = remember(logs) {
        logs.filter { it.restingHr != null }
            .sortedBy { it.date }
            .map { it.date to it.restingHr!!.toDouble() }
    }

    // ── Compare Metrics state ────────────────────────────────────────────────
    // Build available series after all data is computed (see below)
    var selectedSeriesIds by remember { mutableStateOf(setOf<String>()) }    // Lag per selected series in days (positive = shift series later, so it aligns with the next day)
    var lagBySeriesId by remember { mutableStateOf(mapOf<String, Int>()) }    // Smoothing window per series: 0 = raw, 3 = 3-day rolling avg, 7 = 7-day rolling avg
    // Global smoothing: 0 = raw, 3 = 3-day rolling avg, 7 = 7-day weekly buckets
    var avgWindow by remember { mutableStateOf(0) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // ── Custom date range picker dialog ──
    if (showDateRangePicker) {
        val pickerState = rememberDateRangePickerState(
            initialSelectedStartDateMillis = (customFrom ?: LocalDate.now().minusDays(29)).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
            initialSelectedEndDateMillis = (customTo ?: LocalDate.now()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val fromMs = pickerState.selectedStartDateMillis
                    val toMs = pickerState.selectedEndDateMillis
                    if (fromMs != null) {
                        customFrom = java.time.Instant.ofEpochMilli(fromMs).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    if (toMs != null) {
                        customTo = java.time.Instant.ofEpochMilli(toMs).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                    showDateRangePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDateRangePicker = false
                    if (customFrom == null) selectedRange = "1M"
                }) { Text("Cancel") }
            },
        ) {
            DateRangePicker(
                state = pickerState,
                modifier = Modifier.weight(1f),
            )
        }
    }

    val stickyBackground = Surface
    Column(modifier = Modifier.fillMaxSize()) {
        // ── Sticky header: time range chips + avg toggle ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(stickyBackground)
                .padding(top = statusBarTop + 8.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ranges.forEach { range ->
                        val isSelected = range == selectedRange
                        val displayText = if (range == "Custom" && isSelected && customFrom != null && customTo != null) {
                            val mFrom = customFrom!!.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).removeSuffix(".")
                            val mTo = customTo!!.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault()).removeSuffix(".")
                            "${customFrom!!.dayOfMonth} $mFrom – ${customTo!!.dayOfMonth} $mTo"
                        } else range
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Primary.copy(alpha = 0.2f) else CardSurface)
                                .clickable {
                                    selectedRange = range
                                    if (range == "Custom") showDateRangePicker = true
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (isSelected) Primary else OnSurfaceVariant,
                            )
                        }
                    }
                }
                // Smoothing toggle: cycles off → 3d → 7d → off
                val avgActive = avgWindow > 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (avgActive) Primary.copy(alpha = 0.2f) else CardSurface)
                        .clickable { avgWindow = when (avgWindow) { 0 -> 3; 3 -> 7; else -> 0 } }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Default.BarChart, null,
                            modifier = Modifier.size(14.dp),
                            tint = if (avgActive) Primary else OnSurfaceVariant,
                        )
                        Text(
                            when (avgWindow) { 3 -> "3d avg"; 7 -> "7d avg"; else -> "avg off" },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (avgActive) Primary else OnSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── Scrollable chart content ──
        val scrollState = rememberScrollState()
        val chartOffsets = remember { androidx.compose.runtime.mutableStateMapOf<TrendMetric, Int>() }
        val coroutineScope = rememberCoroutineScope()

        // Scroll to the right chart once layout is measured, then clear the metric
        LaunchedEffect(autoSelectSeriesId) {
            if (autoSelectSeriesId != null) {
                // Capture metric before clearSelectedMetric can null it
                val metric = state.selectedMetric.value
                if (metric != null) {
                    // Wait specifically for this chart's offset (up to 1s; may not exist if no data)
                    val offset = withTimeoutOrNull(1000L) {
                        snapshotFlow { chartOffsets[metric] }
                            .filter { it != null && it > 0 }
                            .first()
                    } ?: 0
                    if (offset > 0) scrollState.animateScrollTo(offset)
                }
                state.clearSelectedMetric()
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Compare Metrics card ──────────────────────────────────────────────
        val comparePrimary = Primary
        val compareSecondary = Secondary
        val compareTertiary = Tertiary
        val compareAccent = Accent
        val compareSeriesOptions = remember(
            weightData, bodyFatData, leanMassData, kcalByDay, sleepData, stepsData, bodyWaterData, bloodSugarData,
            comparePrimary, compareSecondary, compareTertiary, compareAccent,
            restingHrData, boneMassData,
        ) {
            buildList {
                if (weightData.size >= 2) add(AvailableSeries("weight", "Weight", comparePrimary, "kg", weightData))
                if (bodyFatData.size >= 2) add(AvailableSeries("bodyfat", "Body Fat %", compareTertiary, "%", bodyFatData))
                if (leanMassData.size >= 2) add(AvailableSeries("lean", "Lean Mass", compareSecondary, "kg", leanMassData))
                if (kcalByDay.size >= 2) add(AvailableSeries("kcal", "Calories", compareAccent, "kcal", kcalByDay.map { (d, v) -> d to v.toDouble() }))
                if (sleepData.size >= 2) add(AvailableSeries("sleep", "Sleep", androidx.compose.ui.graphics.Color(0xFF9F7AEA), "h", sleepData))
                if (stepsData.size >= 2) add(AvailableSeries("steps", "Steps", androidx.compose.ui.graphics.Color(0xFF38B2AC), "k", stepsData.map { (d, v) -> d to v / 1000.0 }))
                if (bodyWaterData.size >= 2) add(AvailableSeries("water", "Body Water", comparePrimary.copy(alpha = 0.65f), "kg", bodyWaterData))
                if (bloodSugarData.size >= 2) add(AvailableSeries("bloodsugar", "Blood Sugar", androidx.compose.ui.graphics.Color(0xFFE53E3E), "mmol/L", bloodSugarData))
                if (restingHrData.size >= 2) add(AvailableSeries("resting_hr", "Resting HR", androidx.compose.ui.graphics.Color(0xFFED8936), "bpm", restingHrData))
                if (boneMassData.size >= 2) add(AvailableSeries("bone", "Bone Mass", androidx.compose.ui.graphics.Color(0xFFB7B7B7), "kg", boneMassData))
            }
        }

        // Drop selections that are no longer available when range changes
        LaunchedEffect(compareSeriesOptions) {
            val validIds = compareSeriesOptions.map { it.id }.toSet()
            selectedSeriesIds = selectedSeriesIds.intersect(validIds)
        }

        // Auto-select series when user clicks a stat chip from home/log
        LaunchedEffect(autoSelectSeriesId, compareSeriesOptions) {
            if (autoSelectSeriesId != null) {
                val availableIds = compareSeriesOptions.map { it.id }.toSet()
                if (autoSelectSeriesId in availableIds) {
                    selectedSeriesIds = setOf(autoSelectSeriesId)
                }
            }
        }

        val selectedChartSeries = remember(selectedSeriesIds, compareSeriesOptions, lagBySeriesId, avgWindow) {
            compareSeriesOptions.filter { it.id in selectedSeriesIds }
                .map { s ->
                    val lag = lagBySeriesId[s.id] ?: 0
                    // Apply global smoothing
                    val smoothed: List<Pair<LocalDate, Double>> = when (avgWindow) {
                        3 -> rollingAvg(s.data, 3)
                        7 -> if (s.data.size >= 2) {
                            val minD = s.data.minOf { it.first }
                            s.data.sortedBy { it.first }
                                .groupBy { (date, _) -> ChronoUnit.DAYS.between(minD, date).toInt() / 7 }
                                .toSortedMap()
                                .map { (weekIdx, entries) ->
                                    minD.plusDays((weekIdx * 7 + 3).toLong()) to entries.map { it.second }.average()
                                }
                        } else s.data
                        else -> s.data
                    }
                    // Shift dates by lag
                    val shiftedData = smoothed.map { (date, v) -> date.plusDays(lag.toLong()) to v }
                    val avgLabel = when (avgWindow) { 3 -> "3d avg"; 7 -> "7d avg"; else -> null }
                    val lagLabel = if (lag != 0) "+${lag}d" else null
                    val suffix = listOfNotNull(avgLabel, lagLabel).joinToString(", ")
                    ChartSeries(
                        label = if (suffix.isNotEmpty()) "${s.label} ($suffix)" else s.label,
                        color = s.color,
                        unit = s.unit,
                        data = shiftedData,
                    )
                }
        }

        // Map from shifted label back to original series for lag UI
        val selectedAvailableSeries = remember(selectedSeriesIds, compareSeriesOptions) {
            compareSeriesOptions.filter { it.id in selectedSeriesIds }
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
            InfoCard(
                label = "Compare Metrics",
                icon = Icons.Default.CompareArrows,
            ) {
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
                                if (isSelected) {
                                    selectedSeriesIds = selectedSeriesIds - s.id
                                    lagBySeriesId = lagBySeriesId - s.id
                                } else if (selectedSeriesIds.size < 3) {
                                    selectedSeriesIds = selectedSeriesIds + s.id
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

                // Lag controls — only shown when 2+ series selected (lag is relative between series)
                if (selectedAvailableSeries.size >= 2) {
                    Spacer(Modifier.height(8.dp))
                    selectedAvailableSeries.forEach { s ->
                        val lag = lagBySeriesId[s.id] ?: 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(s.color),
                                )
                                Text(
                                    s.label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant,
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { if (lag > 0) lagBySeriesId = lagBySeriesId + (s.id to lag - 1) },
                                    modifier = Modifier.size(28.dp),
                                    enabled = lag > 0,
                                ) {
                                    Icon(Icons.Default.Remove, null, modifier = Modifier.size(14.dp), tint = if (lag > 0) s.color else OnSurfaceVariant.copy(alpha = 0.3f))
                                }
                                Text(
                                    if (lag == 0) "0d" else "+${lag}d",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (lag > 0) s.color else OnSurfaceVariant,
                                    modifier = Modifier.widthIn(min = 30.dp),
                                )
                                IconButton(
                                    onClick = { if (lag < 7) lagBySeriesId = lagBySeriesId + (s.id to lag + 1) },
                                    modifier = Modifier.size(28.dp),
                                    enabled = lag < 7,
                                ) {
                                    Icon(Icons.Default.Add, null, modifier = Modifier.size(14.dp), tint = if (lag < 7) s.color else OnSurfaceVariant.copy(alpha = 0.3f))
                                }
                                Text(
                                    "lag",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
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
            InfoCard(
                label = stringResource(R.string.trends_weight),
                icon = Icons.Default.Scale,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.WEIGHT] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Weight",
                            listOf("Date", "Weight (kg)"),
                            weightData.map { (d, v) -> listOf(d.toString(), "%.2f".format(v)) },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val firstWeight = weightData.firstOrNull()?.second
                val lastDate = weightData.lastOrNull()?.first
                val firstDate = weightData.firstOrNull()?.first
                val actualDays = if (firstDate != null && lastDate != null)
                    ChronoUnit.DAYS.between(firstDate, lastDate).toInt().coerceAtLeast(1) else 1
                val refTarget = when (selectedRange) {
                    "All" -> goalWeight?.toDouble()
                    else -> firstWeight?.let { it - (weeklyRate ?: 0f).toDouble() * actualDays / 7.0 }
                }
                val smoothedWeight = if (avgWindow == 3) rollingAvg(weightData, 3) else weightData
                val wwa = if (avgWindow == 7) weeklyOf(smoothedWeight, selectedRange == "7D") else null
                val chartPoints = wwa?.data ?: smoothedWeight.mapIndexed { i, (_, w) -> i to w }
                val dateLabels = wwa?.dateLabels ?: smoothedWeight.map { (date, _) ->
                    val monthName = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "%d. %s".format(date.dayOfMonth, monthName)
                }
                val xLabels = wwa?.xAxisLabels ?: smoothedWeight.map { (date, _) -> xAxisLabel(date, selectedRange == "7D") }
                TrendChart(
                    dataPoints = chartPoints,
                    dateLabels = dateLabels,
                    xAxisLabels = xLabels,
                    startLineKg = firstWeight,
                    targetLineKg = refTarget,
                    bandData = wwa?.bandData,
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
            InfoCard(
                label = "Body Fat %",
                icon = Icons.Default.Percent,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.BODY_FAT] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Body Fat",
                            listOf("Date", "Body Fat (%)"),
                            bodyFatData.map { (d, v) -> listOf(d.toString(), "%.1f".format(v)) },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val smoothedBf = if (avgWindow == 3) rollingAvg(bodyFatData, 3) else bodyFatData
                val wa = if (avgWindow == 7) weeklyOf(smoothedBf, selectedRange == "7D") else null
                val labels = wa?.dateLabels ?: smoothedBf.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = wa?.xAxisLabels ?: smoothedBf.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                val bfNormal = if (sex == "male") 10.0 to 20.0 else if (sex == "female") 18.0 to 28.0 else 10.0 to 25.0
                val bfBad = if (sex == "male") 25.0 else if (sex == "female") 33.0 else 30.0
                SimpleLineChart(
                    data = wa?.data ?: smoothedBf.mapIndexed { i, (_, v) -> i to v },
                    color = Tertiary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "%",
                    bandData = wa?.bandData,
                    normalRange = bfNormal,
                    badLineValue = bfBad,
                    badLineLabel = "%.0f%%".format(bfBad),
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
            InfoCard(
                label = "Lean Mass",
                icon = Icons.Default.FitnessCenter,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.LEAN_MASS] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Lean Mass",
                            listOf("Date", "Lean Mass (kg)"),
                            leanMassData.map { (d, v) -> listOf(d.toString(), "%.2f".format(v)) },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val smoothedLean = if (avgWindow == 3) rollingAvg(leanMassData, 3) else leanMassData
                val wa = if (avgWindow == 7) weeklyOf(smoothedLean, selectedRange == "7D") else null
                val labels = wa?.dateLabels ?: smoothedLean.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = wa?.xAxisLabels ?: smoothedLean.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = wa?.data ?: smoothedLean.mapIndexed { i, (_, v) -> i to v },
                    color = Secondary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "kg",
                    bandData = wa?.bandData,
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
            InfoCard(
                label = "Body Water",
                icon = Icons.Default.WaterDrop,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.BODY_WATER] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Body Water",
                            listOf("Date", "Body Water (kg)"),
                            bodyWaterData.map { (d, v) -> listOf(d.toString(), "%.2f".format(v)) },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val smoothedWater = if (avgWindow == 3) rollingAvg(bodyWaterData, 3) else bodyWaterData
                val wa = if (avgWindow == 7) weeklyOf(smoothedWater, selectedRange == "7D") else null
                val labels = wa?.dateLabels ?: smoothedWater.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = wa?.xAxisLabels ?: smoothedWater.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = wa?.data ?: smoothedWater.mapIndexed { i, (_, v) -> i to v },
                    color = Primary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "kg",
                    bandData = wa?.bandData,
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

        // ── Bone Mass Trend ──
        if (boneMassData.size >= 2) {
            InfoCard(
                label = "Bone Mass",
                icon = Icons.Default.Straighten,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.BONE_MASS] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Bone Mass",
                            listOf("Date", "Bone Mass (kg)"),
                            boneMassData.map { (d, v) -> listOf(d.toString(), "%.2f".format(v)) },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val smoothed = if (avgWindow == 3) rollingAvg(boneMassData, 3) else boneMassData
                val wa = if (avgWindow == 7) weeklyOf(smoothed, selectedRange == "7D") else null
                val labels = wa?.dateLabels ?: smoothed.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = wa?.xAxisLabels ?: smoothed.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = wa?.data ?: smoothed.mapIndexed { i, (_, v) -> i to v },
                    color = androidx.compose.ui.graphics.Color(0xFFB7B7B7),
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "kg",
                    bandData = wa?.bandData,
                    modifier = Modifier.height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    StatColumn("Avg", "%.2f kg".format(boneMassData.map { it.second }.average()))
                    val delta = boneMassData.last().second - boneMassData.first().second
                    StatColumn("Change", "%+.2f kg".format(delta), if (delta >= 0) Secondary else Tertiary)
                    StatColumn("Latest", "%.2f kg".format(boneMassData.last().second))
                }
            }
        }

        // ── Blood Glucose Analysis ──
        if (bgReadings.isNotEmpty()) {
            // ── Daily BG navigation: compute distinct days with readings ──
            val bgDays = remember(bgReadings) {
                bgReadings.map { it.timestamp.atZone(ZoneId.systemDefault()).toLocalDate() }
                    .distinct()
                    .sorted()
            }
            var bgDayIndex by remember(bgDays) { mutableStateOf(bgDays.lastIndex) }
            val bgCurrentDay = bgDays.getOrNull(bgDayIndex) ?: LocalDate.now()
            val bgDayStart = remember(bgCurrentDay) { bgCurrentDay.atStartOfDay(ZoneId.systemDefault()).toInstant() }
            val bgDayEnd = remember(bgCurrentDay) { bgCurrentDay.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant() }
            val bgDayReadings = remember(bgReadings, bgDayStart, bgDayEnd) {
                bgReadings.filter { it.timestamp >= bgDayStart && it.timestamp < bgDayEnd }
            }
            val bgDayMeals = remember(meals, bgCurrentDay) {
                meals.filter { it.date == bgCurrentDay }
            }
            val bgDayFmt = remember { DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault()) }

            InfoCard(
                label = stringResource(R.string.trends_blood_sugar),
                icon = Icons.Default.Bloodtype,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.BLOOD_SUGAR] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        val dateFmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                        // Index meals by their effective timestamp for join
                        val mealsByTime = meals.sortedBy { it.displayTime }
                        // Assign each meal to its single nearest BG reading (within ±2h).
                        // If two meals compete for the same BG reading, the closer one wins.
                        // This ensures each meal appears exactly once in the export.
                        val bgIdxToMeal: Map<Int, MealEntry> = mealsByTime
                            .mapNotNull { meal ->
                                val i = bgReadings.indices.minByOrNull { idx ->
                                    kotlin.math.abs(bgReadings[idx].timestamp.epochSecond - meal.displayTime.epochSecond)
                                } ?: return@mapNotNull null
                                val dist = kotlin.math.abs(bgReadings[i].timestamp.epochSecond - meal.displayTime.epochSecond)
                                if (dist <= 7200) Triple(i, dist, meal) else null
                            }
                            .groupBy { it.first }
                            .mapValues { (_, candidates) -> candidates.minBy { it.second }.third }
                        val bgRows = bgReadings.mapIndexed { i, r ->
                            val nearest = bgIdxToMeal[i]
                            listOf(
                                r.timestamp.atZone(ZoneId.systemDefault()).format(dateFmt),
                                "%.2f".format(r.valueMmolL),
                                nearest?.description ?: "",
                                nearest?.totalKcal?.toString() ?: "",
                                nearest?.totalCarbsG?.toString() ?: "",
                                nearest?.totalProteinG?.toString() ?: "",
                                nearest?.totalFatG?.toString() ?: "",
                            )
                        }
                        // Also add any meals that had no BG reading nearby
                        val pairedMealIds = bgIdxToMeal.values.map { it.id }.toSet()
                        val mealOnlyRows = mealsByTime
                            .filter { it.id !in pairedMealIds }
                            .map { m ->
                                listOf(
                                    m.displayTime.atZone(ZoneId.systemDefault()).format(dateFmt),
                                    "",
                                    m.description,
                                    m.totalKcal.toString(),
                                    m.totalCarbsG.toString(),
                                    m.totalProteinG.toString(),
                                    m.totalFatG.toString(),
                                )
                            }
                        val allRows = (bgRows + mealOnlyRows)
                            .sortedBy { it[0] }
                        OdtExporter.share(
                            context, "Blood Glucose",
                            listOf("Time", "BG (mmol/L)", "Meal", "Kcal", "Carbs (g)", "Protein (g)", "Fat (g)"),
                            allRows,
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                // ── Day navigation row ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { if (bgDayIndex > 0) bgDayIndex-- },
                        enabled = bgDayIndex > 0,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.ChevronLeft, contentDescription = "Previous day", tint = if (bgDayIndex > 0) OnSurface else OnSurfaceVariant.copy(alpha = 0.3f))
                    }
                    Text(
                        bgCurrentDay.format(bgDayFmt),
                        style = MaterialTheme.typography.titleSmall,
                        color = OnSurface,
                    )
                    IconButton(
                        onClick = { if (bgDayIndex < bgDays.lastIndex) bgDayIndex++ },
                        enabled = bgDayIndex < bgDays.lastIndex,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(Icons.Default.ChevronRight, contentDescription = "Next day", tint = if (bgDayIndex < bgDays.lastIndex) OnSurface else OnSurfaceVariant.copy(alpha = 0.3f))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "${bgDayReadings.size} readings" + if (bgDayReadings.size >= 2) " · %.1f–%.1f mmol/L".format(bgDayReadings.minOf { it.valueMmolL }, bgDayReadings.maxOf { it.valueMmolL }) else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                BloodGlucoseMealLegend()
                Spacer(Modifier.height(8.dp))
                var bgSelectedMeal by remember(bgCurrentDay) { mutableStateOf<MealEntry?>(null) }
                BloodGlucoseChart(
                    readings = bgDayReadings,
                    meals = bgDayMeals,
                    selectedMeal = bgSelectedMeal,
                    onMealSelected = { bgSelectedMeal = it },
                    modifier = Modifier.fillMaxWidth().height(200.dp),
                )
                // Selected meal row — outside chart, same style as DayCard
                if (bgSelectedMeal != null) {
                    val meal = bgSelectedMeal!!
                    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                            .clickable { sheetState.selectedMeal = meal }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (meal.mealType != null) {
                                Text(
                                    mealTypeLabel(meal.mealType),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Accent,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("·", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                meal.description.take(40) + if (meal.description.length > 40) "…" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                meal.displayTime.atZone(ZoneId.systemDefault()).format(timeFmt),
                                style = MaterialTheme.typography.labelSmall,
                                color = OnSurfaceVariant,
                            )
                        }
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${meal.totalKcal} kcal",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Secondary,
                            )
                            if (meal.totalProteinG > 0) Text("P ${meal.totalProteinG}g", style = MaterialTheme.typography.labelSmall, color = Primary)
                            if (meal.totalCarbsG > 0) Text("C ${meal.totalCarbsG}g", style = MaterialTheme.typography.labelSmall, color = Tertiary)
                            if (meal.totalFatG > 0) Text("F ${meal.totalFatG}g", style = MaterialTheme.typography.labelSmall, color = Accent)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (bgDayReadings.isNotEmpty()) {
                        StatColumn("Avg", "%.1f mmol/L".format(bgDayReadings.map { it.valueMmolL }.average()))
                        StatColumn("Min", "%.1f".format(bgDayReadings.minOf { it.valueMmolL }), Secondary)
                        StatColumn("Max", "%.1f".format(bgDayReadings.maxOf { it.valueMmolL }), Tertiary)
                        StatColumn("Readings", "${bgDayReadings.size}")
                    }
                }
            }
        }

        // ── Calorie Trend ──
        if (kcalByDay.size >= 2) {
            InfoCard(
                label = stringResource(R.string.trends_calories),
                icon = Icons.Default.LocalFireDepartment,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.CALORIES] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Calories",
                            listOf("Date", "Calories (kcal)"),
                            kcalByDay.map { (d, v) -> listOf(d.toString(), "$v") },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val kcalDoubleData = kcalByDay.map { (d, v) -> d to v.toDouble() }
                val smoothedKcal = if (avgWindow == 3) rollingAvg(kcalDoubleData, 3) else kcalDoubleData
                val wa = if (avgWindow == 7) weeklyOf(smoothedKcal, selectedRange == "7D") else null
                val labels = wa?.dateLabels ?: smoothedKcal.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = wa?.xAxisLabels ?: smoothedKcal.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = wa?.data ?: smoothedKcal.mapIndexed { i, (_, v) -> i to v },
                    color = Secondary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "kcal",
                    refLineValue = dailyTargetKcal?.toDouble(),
                    refLineColor = Secondary,
                    refLineLabel = dailyTargetKcal?.let { "$it kcal" },
                    normalRange = dailyTargetKcal?.let { (it - 200.0) to (it + 200.0) },
                    badLineValue = dailyTargetKcal?.let { it + 500.0 },
                    badLineLabel = dailyTargetKcal?.let { "+500 kcal" },
                    bandData = wa?.bandData,
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
            var showProtein by remember { mutableStateOf(true) }
            var showCarbs by remember { mutableStateOf(true) }
            var showFat by remember { mutableStateOf(true) }
            InfoCard(
                label = stringResource(R.string.trends_macros),
                icon = Icons.Default.DonutSmall,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.MACROS] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Macros",
                            listOf("Date", "Protein (g)", "Carbs (g)", "Fat (g)"),
                            macrosByDay.map { (d, t) -> listOf(d.toString(), "${t.first}", "${t.second}", "${t.third}") },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val labels = macrosByDay.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = macrosByDay.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                val targets = dailyTargetKcal?.let {
                    com.fatlosstrack.domain.TdeeCalculator.macroTargets(it, goalBodyWeightKg = goalWeight, actualLeanMassKg = latestLeanMassKg)
                }
                if (selectedRange == "7D") {
                    MacroBarChart(
                        data = macrosByDay.map { it.second },
                        macroTargets = targets,
                        dateLabels = labels,
                        xAxisLabels = xLabels,
                        colors = Triple(Primary, Tertiary, Accent),
                        modifier = Modifier.height(140.dp),
                    )
                } else {
                    // Toggle chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 8.dp),
                    ) {
                        listOf(
                            Triple("P", Primary, showProtein) to { showProtein = !showProtein },
                            Triple("C", Tertiary, showCarbs) to { showCarbs = !showCarbs },
                            Triple("F", Accent, showFat) to { showFat = !showFat },
                        ).forEach { (chip, toggle) ->
                            val (label, color, active) = chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) color.copy(alpha = 0.18f) else CardSurface)
                                    .clickable { toggle() }
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(if (active) color else OnSurfaceVariant.copy(alpha = 0.3f)),
                                    )
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = if (active) color else OnSurfaceVariant.copy(alpha = 0.4f))
                                }
                            }
                        }
                    }
                    val activeSeries = buildList {
                        if (showProtein) add(ChartSeries("Protein", Primary, "g", macrosByDay.map { (d, m) -> d to m.first.toDouble() }))
                        if (showCarbs) add(ChartSeries("Carbs", Tertiary, "g", macrosByDay.map { (d, m) -> d to m.second.toDouble() }))
                        if (showFat) add(ChartSeries("Fat", Accent, "g", macrosByDay.map { (d, m) -> d to m.third.toDouble() }))
                    }
                    if (activeSeries.isNotEmpty()) {
                        MultiSeriesLineChart(
                            series = activeSeries,
                            modifier = Modifier.height(140.dp),
                        )
                    }
                }
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
            InfoCard(
                label = stringResource(R.string.trends_sleep),
                icon = Icons.Default.Bedtime,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.SLEEP] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Sleep",
                            listOf("Date", "Sleep (h)"),
                            sleepData.map { (d, v) -> listOf(d.toString(), "%.1f".format(v)) },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val smoothedSleep = if (avgWindow == 3) rollingAvg(sleepData, 3) else sleepData
                val wa = if (avgWindow == 7) weeklyOf(smoothedSleep, selectedRange == "7D") else null
                val labels = wa?.dateLabels ?: smoothedSleep.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = wa?.xAxisLabels ?: smoothedSleep.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = wa?.data ?: smoothedSleep.mapIndexed { i, (_, h) -> i to h },
                    color = Primary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "h",
                    bandData = wa?.bandData,
                    normalRange = 7.0 to 9.0,
                    badLineValue = 6.0,
                    badLineLabel = "6h",
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
            InfoCard(
                label = stringResource(R.string.trends_steps),
                icon = Icons.AutoMirrored.Filled.DirectionsWalk,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.STEPS] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Steps",
                            listOf("Date", "Steps"),
                            stepsData.map { (d, v) -> listOf(d.toString(), "$v") },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val stepsDoubleData = stepsData.map { (d, v) -> d to v.toDouble() }
                val smoothedSteps = if (avgWindow == 3) rollingAvg(stepsDoubleData, 3) else stepsDoubleData
                val wa = if (avgWindow == 7) weeklyOf(smoothedSteps, selectedRange == "7D") else null
                val labels = wa?.dateLabels ?: smoothedSteps.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = wa?.xAxisLabels ?: smoothedSteps.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = wa?.data ?: smoothedSteps.mapIndexed { i, (_, s) -> i to s },
                    color = Secondary,
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "steps",
                    bandData = wa?.bandData,
                    normalRange = 7_500.0 to 12_000.0,
                    badLineValue = 5_000.0,
                    badLineLabel = "5k",
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

        // ── Resting Heart Rate Trend ──
        if (restingHrData.size >= 2) {
            InfoCard(
                label = "Resting Heart Rate",
                icon = Icons.Default.FavoriteBorder,
                modifier = Modifier.onGloballyPositioned { chartOffsets[TrendMetric.HEART_RATE] = it.positionInParent().y.toInt() },
                action = {
                    IconButton(onClick = {
                        OdtExporter.share(
                            context, "Resting HR",
                            listOf("Date", "Resting HR (bpm)"),
                            restingHrData.map { (d, v) -> listOf(d.toString(), "${v.toInt()}") },
                        )
                    }) { Icon(Icons.Default.Share, contentDescription = "Export", modifier = Modifier.size(16.dp), tint = OnSurfaceVariant) }
                },
            ) {
                val smoothed = if (avgWindow == 3) rollingAvg(restingHrData, 3) else restingHrData
                val wa = if (avgWindow == 7) weeklyOf(smoothed, selectedRange == "7D") else null
                val labels = wa?.dateLabels ?: smoothed.map { (d, _) ->
                    val m = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                        .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                    "${d.dayOfMonth}. $m"
                }
                val xLabels = wa?.xAxisLabels ?: smoothed.map { (d, _) -> xAxisLabel(d, selectedRange == "7D") }
                SimpleLineChart(
                    data = wa?.data ?: smoothed.mapIndexed { i, (_, v) -> i to v },
                    color = androidx.compose.ui.graphics.Color(0xFFED8936),
                    dateLabels = labels,
                    xAxisLabels = xLabels,
                    unit = "bpm",
                    bandData = wa?.bandData,
                    normalRange = 60.0 to 100.0,
                    badLineValue = 100.0,
                    badLineLabel = "100 bpm",
                    modifier = Modifier.height(120.dp),
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    val avgHr = restingHrData.map { it.second }.average().toInt()
                    StatColumn("Avg", "$avgHr bpm")
                    val minHr = restingHrData.minOf { it.second }.toInt()
                    val maxHr = restingHrData.maxOf { it.second }.toInt()
                    StatColumn("Range", "$minHr–$maxHr bpm")
                    StatColumn("Latest", "${restingHrData.last().second.toInt()} bpm")
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
        } // end scrollable Column
    } // end outer Column

    LogSheetHost(
        state = sheetState,
        logsByDate = emptyMap(),
        mealDao = state.mealDao,
        dailyLogDao = state.dailyLogDaoForLeanMass,
        daySummaryGenerator = state.daySummaryGenerator,
        openAiService = state.openAiService,
        bookmarkedMealDao = state.bookmarkedMealDao,
        logTag = "TrendsScreen",
    )
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

/**
 * Result of weekly aggregation: (index, avg) pairs, (index, min, max) bands, and axis labels.
 * Index = day offset from earliest date in the input series.
 */
private data class WeeklyResult(
    val data: List<Pair<Int, Double>>,
    val bandData: List<Triple<Int, Double, Double>>,
    val dateLabels: List<String>,
    val xAxisLabels: List<String>,
)

/**
 * Groups [raw] into 7-day buckets and returns weekly avg ± min/max band.
 * The x-index for each week is the day-offset of the midpoint of that bucket.
 */
private fun weeklyOf(raw: List<Pair<LocalDate, Double>>, labelFor7D: Boolean = false): WeeklyResult {
    if (raw.size < 2) return WeeklyResult(emptyList(), emptyList(), emptyList(), emptyList())
    val minDate = raw.minOf { it.first }
    val buckets = raw.sortedBy { it.first }
        .groupBy { (date, _) -> ChronoUnit.DAYS.between(minDate, date).toInt() / 7 }
    val data = mutableListOf<Pair<Int, Double>>()
    val bands = mutableListOf<Triple<Int, Double, Double>>()
    val dateLabels = mutableListOf<String>()
    val xLabels = mutableListOf<String>()
    buckets.toSortedMap().forEach { (weekIdx, entries) ->
        val vals = entries.map { it.second }
        val midOffset = weekIdx * 7 + 3
        val midDate = minDate.plusDays(midOffset.toLong())
        data.add(midOffset to vals.average())
        bands.add(Triple(midOffset, vals.min(), vals.max()))
        val m = midDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
        dateLabels.add("${midDate.dayOfMonth}. $m")
        xLabels.add(xAxisLabel(midDate, labelFor7D))
    }
    return WeeklyResult(data, bands, dateLabels, xLabels)
}

/**
 * Centered rolling average with window size [n]. Dates with fewer than
 * [n]/2 neighbours on either side still get computed from available data.
 */
fun rollingAvg(data: List<Pair<LocalDate, Double>>, n: Int): List<Pair<LocalDate, Double>> {
    if (n < 2 || data.size < 2) return data
    val sorted = data.sortedBy { it.first }
    val half = n / 2
    return sorted.mapIndexed { i, (date, _) ->
        val lo = (i - half).coerceAtLeast(0)
        val hi = (i + half).coerceAtMost(sorted.lastIndex)
        date to sorted.subList(lo, hi + 1).map { it.second }.average()
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
