package com.fatlosstrack.ui.log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.reminders.ReminderDeepLink
import com.fatlosstrack.data.reminders.ReminderType
import com.fatlosstrack.ui.components.rememberDailyTargetKcal
import com.fatlosstrack.ui.components.rememberLatestLeanMassKg
import com.fatlosstrack.ui.theme.*
import com.fatlosstrack.ui.trends.TrendMetric
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.time.LocalDate

/** Days added to the timeline per page. */
private const val PAGE_DAYS = 30

/** Start loading the next page when the last visible item is this close to the end. */
private const val LOAD_AHEAD_ITEMS = 10

// ── Main Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    state: LogStateHolder,
    onCameraForDate: (LocalDate) -> Unit = {},
    onStatClick: ((TrendMetric) -> Unit)? = null,
) {
    val startDateStr by state.startDate.collectAsState(initial = null)
    val startDate = startDateStr?.let {
        try { LocalDate.parse(it) } catch (_: Exception) { null }
    }

    if (startDate == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = OnSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(64.dp))
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.log_set_goal_title), style = MaterialTheme.typography.titleMedium, color = OnSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.log_set_goal_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                )
            }
        }
        return
    }

    // Paging: only the newest [loadedDays] days are built and queried. Scrolling
    // near the bottom extends the window by another page, down to the start date.
    val today = remember { LocalDate.now() }
    var loadedDays by rememberSaveable { mutableIntStateOf(PAGE_DAYS) }
    val windowStart = maxOf(startDate, today.minusDays(loadedDays - 1L))
    val hasMore by rememberUpdatedState(windowStart.isAfter(startDate))

    // Remembered per window so collection survives recomposition; collectAsState
    // keeps the previous list until the wider query emits, so cards don't flash empty.
    val meals by remember(windowStart) { state.mealsSince(windowStart) }.collectAsState(initial = emptyList())
    val dailyLogs by remember(windowStart) { state.logsSince(windowStart) }.collectAsState(initial = emptyList())

    // TDEE / daily target
    val dailyTargetKcal = rememberDailyTargetKcal(state.preferencesManager)
    val latestLeanMassKg = rememberLatestLeanMassKg(state.dailyLogDao)
    val goalWeightKg by state.preferencesManager.goalWeight.collectAsState(initial = null)

    // Sheet state
    val sheetState = rememberLogSheetState()

    var editingDate by sheetState::editingDate
    var selectedMeal by sheetState::selectedMeal
    var addMealForDate by sheetState::addMealForDate

    // Opened from a reminder notification: show the sheet that fixes what it nagged about
    val pendingReminder by ReminderDeepLink.pending.collectAsState()
    LaunchedEffect(pendingReminder) {
        when (ReminderDeepLink.take()) {
            ReminderType.MEAL_GAP -> addMealForDate = LocalDate.now()
            ReminderType.WEIGH_IN -> editingDate = LocalDate.now()
            ReminderType.EVENING_BUDGET, null -> {}
        }
    }

    val visibleDates = remember(windowStart, today) {
        generateSequence(today) { it.minusDays(1) }
            .takeWhile { !it.isBefore(windowStart) }
            .toList()
    }
    val mealsByDate = remember(meals) { meals.groupBy { it.date } }
    val logsByDate = remember(dailyLogs) { dailyLogs.associateBy { it.date } }

    val listState = rememberLazyListState()
    LaunchedEffect(listState) {
        // Emits the item count once each time the last visible item gets within
        // LOAD_AHEAD_ITEMS of the end, so a fast fling keeps loading page after page.
        snapshotFlow {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@snapshotFlow null
            layout.totalItemsCount.takeIf { lastVisible >= it - LOAD_AHEAD_ITEMS }
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { if (hasMore) loadedDays += PAGE_DAYS }
    }

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = statusBarTop + 12.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Today summary
        item(key = "today-summary") {
            val todayLog = logsByDate[today]
            val todayMeals = mealsByDate[today] ?: emptyList()
            val todayKcal = todayMeals.sumOf { it.totalKcal }
            val todayProtein = todayMeals.sumOf { it.totalProteinG }
            val todayCarbs = todayMeals.sumOf { it.totalCarbsG }
            val todayFat = todayMeals.sumOf { it.totalFatG }

            Card(
                colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
                shape = RoundedCornerShape(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MiniStat(stringResource(R.string.stat_weight), todayLog?.weightKg?.let { "%.1f".format(it) } ?: "\u2014", stringResource(R.string.unit_kg))
                    MiniStat(stringResource(R.string.stat_meals), "${todayMeals.size}", stringResource(R.string.format_kcal, todayKcal))
                    MiniStat(stringResource(R.string.stat_protein), if (todayProtein > 0) stringResource(R.string.format_protein_g, todayProtein) else "\u2014", "")
                    MiniStat(stringResource(R.string.stat_carbs), if (todayCarbs > 0) "${todayCarbs}g" else "\u2014", "")
                    MiniStat(stringResource(R.string.stat_fat), if (todayFat > 0) "${todayFat}g" else "\u2014", "")
                    MiniStat(stringResource(R.string.stat_steps), todayLog?.steps?.let { "%,d".format(it) } ?: "\u2014", "")
                    MiniStat(stringResource(R.string.stat_sleep), todayLog?.sleepHours?.let { formatSleepDuration(it) } ?: "—", "")
                }
            }
        }

        items(visibleDates, key = { it.toString() }) { date ->
            DayCard(
                date = date,
                log = logsByDate[date],
                meals = mealsByDate[date] ?: emptyList(),
                dailyTargetKcal = dailyTargetKcal,
                goalWeightKg = goalWeightKg,
                leanMassKg = latestLeanMassKg,
                onEdit = { editingDate = date },
                onMealClick = { selectedMeal = it },
                onAddMeal = { addMealForDate = date },
                onCameraClick = { onCameraForDate(date) },
                onStatClick = onStatClick,
            )
        }

        item(key = "spacer") {
            Spacer(Modifier.height(80.dp))
        }
    }

    LogSheetHost(
        state = sheetState,
        logsByDate = logsByDate,
        mealDao = state.mealDao,
        dailyLogDao = state.dailyLogDao,
        daySummaryGenerator = state.daySummaryGenerator,
        openAiService = state.openAiService,
        bookmarkedMealDao = state.bookmarkedMealDao,
        onCameraForDate = onCameraForDate,
        logTag = "LogScreen",
    )
}

// ── Mini stat ──

@Composable
private fun MiniStat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        if (unit.isNotBlank()) Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.7f))
    }
}
