package com.fatlosstrack.ui.log

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.components.rememberDailyTargetKcal
import com.fatlosstrack.ui.theme.*
import java.time.LocalDate

// ── Main Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    mealDao: MealDao,
    dailyLogDao: DailyLogDao,
    preferencesManager: PreferencesManager,
    daySummaryGenerator: DaySummaryGenerator? = null,
    openAiService: OpenAiService? = null,
    onCameraForDate: (LocalDate) -> Unit = {},
) {
    val startDateStr by preferencesManager.startDate.collectAsState(initial = null)
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

    val meals by mealDao.getAllMeals().collectAsState(initial = emptyList())
    val dailyLogs by dailyLogDao.getAllLogs().collectAsState(initial = emptyList())

    // TDEE / daily target
    val dailyTargetKcal = rememberDailyTargetKcal(preferencesManager)

    // Sheet state
    val sheetState = rememberLogSheetState()

    var editingDate by sheetState::editingDate
    var selectedMeal by sheetState::selectedMeal
    var addMealForDate by sheetState::addMealForDate

    val today = LocalDate.now()
    val allDates = generateSequence(startDate) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .toList()
        .reversed()
    val mealsByDate = meals.groupBy { it.date }
    val logsByDate = dailyLogs.associateBy { it.date }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.log_screen_title), style = MaterialTheme.typography.headlineMedium, color = OnSurface)

        // Today summary
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
                MiniStat(stringResource(R.string.stat_sleep), todayLog?.sleepHours?.let { "%.1f".format(it) } ?: "\u2014", stringResource(R.string.unit_hrs))
            }
        }

        allDates.forEach { date ->
            DayCard(
                date = date,
                log = logsByDate[date],
                meals = mealsByDate[date] ?: emptyList(),
                dailyTargetKcal = dailyTargetKcal,
                onEdit = { editingDate = date },
                onMealClick = { selectedMeal = it },
                onAddMeal = { addMealForDate = date },
            )
        }

        Spacer(Modifier.height(80.dp))
    }

    LogSheetHost(
        state = sheetState,
        logsByDate = logsByDate,
        mealDao = mealDao,
        dailyLogDao = dailyLogDao,
        daySummaryGenerator = daySummaryGenerator,
        openAiService = openAiService,
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
