package com.fatlosstrack.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ── Helpers ──

@Composable
internal fun categoryLabel(c: MealCategory) = when (c) {
    MealCategory.HOME -> stringResource(R.string.category_home)
    MealCategory.RESTAURANT -> stringResource(R.string.category_restaurant)
    MealCategory.FAST_FOOD -> stringResource(R.string.category_fast_food)
}

internal fun categoryIcon(c: MealCategory) = when (c) {
    MealCategory.HOME -> Icons.Default.Home
    MealCategory.RESTAURANT -> Icons.Default.Restaurant
    MealCategory.FAST_FOOD -> Icons.Default.Fastfood
}

internal fun categoryColor(c: MealCategory) = when (c) {
    MealCategory.HOME -> Secondary
    MealCategory.RESTAURANT -> Accent
    MealCategory.FAST_FOOD -> Tertiary
}

@Composable
internal fun mealTypeLabel(t: MealType) = when (t) {
    MealType.BREAKFAST -> stringResource(R.string.meal_type_breakfast)
    MealType.BRUNCH -> stringResource(R.string.meal_type_brunch)
    MealType.LUNCH -> stringResource(R.string.meal_type_lunch)
    MealType.DINNER -> stringResource(R.string.meal_type_dinner)
    MealType.SNACK -> stringResource(R.string.meal_type_snack)
}

// ── Main Screen ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    mealDao: MealDao,
    dailyLogDao: DailyLogDao,
    preferencesManager: PreferencesManager,
    daySummaryGenerator: DaySummaryGenerator? = null,
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
    val scope = rememberCoroutineScope()

    // Sheet states
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mealSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val addMealSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedMeal by remember { mutableStateOf<MealEntry?>(null) }
    var addMealForDate by remember { mutableStateOf<LocalDate?>(null) }

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
                onEdit = { editingDate = date },
                onMealClick = { selectedMeal = it },
                onAddMeal = { addMealForDate = date },
            )
        }

        Spacer(Modifier.height(80.dp))
    }

    // ── Daily log edit sheet ──
    if (editingDate != null) {
        ModalBottomSheet(onDismissRequest = { editingDate = null }, sheetState = editSheetState, containerColor = CardSurface) {
            DailyLogEditSheet(
                date = editingDate!!,
                existingLog = logsByDate[editingDate!!],
                onSave = { scope.launch {
                    dailyLogDao.upsert(it)
                    val parts = mutableListOf<String>()
                    it.weightKg?.let { w -> parts += "weight=%.1f".format(w) }
                    it.steps?.let { s -> parts += "steps=$s" }
                    it.sleepHours?.let { s -> parts += "sleep=${s}h" }
                    it.restingHr?.let { h -> parts += "hr=$h" }
                    AppLogger.instance?.user("DailyLog saved ${it.date}: ${parts.joinToString(", ")}")
                    launchSummary(it.date, dailyLogDao, daySummaryGenerator, "LogScreen:dailyLogEdit")
                    editingDate = null
                } },
                onDismiss = { editingDate = null },
            )
        }
    }

    // ── Meal detail / edit sheet ──
    if (selectedMeal != null) {
        ModalBottomSheet(onDismissRequest = { selectedMeal = null }, sheetState = mealSheetState, containerColor = CardSurface) {
            MealEditSheet(
                meal = selectedMeal!!,
                onSave = { updated -> scope.launch {
                    mealDao.update(updated)
                    AppLogger.instance?.meal("Edited: ${updated.description.take(40)} — ${updated.totalKcal} kcal, date=${updated.date}")
                    launchSummary(updated.date, dailyLogDao, daySummaryGenerator, "LogScreen:mealEdit")
                    selectedMeal = null
                } },
                onDelete = { scope.launch {
                    val meal = selectedMeal!!
                    AppLogger.instance?.meal("Deleted: ${meal.description.take(40)} — ${meal.totalKcal} kcal, date=${meal.date}")
                    mealDao.delete(meal)
                    launchSummary(meal.date, dailyLogDao, daySummaryGenerator, "LogScreen:mealDelete")
                    selectedMeal = null
                } },
                onDismiss = { selectedMeal = null },
            )
        }
    }

    // ── Add meal manually sheet ──
    if (addMealForDate != null) {
        ModalBottomSheet(onDismissRequest = { addMealForDate = null }, sheetState = addMealSheetState, containerColor = CardSurface) {
            AddMealSheet(
                date = addMealForDate!!,
                onSave = { newMeal -> scope.launch {
                    mealDao.insert(newMeal)
                    AppLogger.instance?.meal("Manual add: ${newMeal.description.take(40)} — ${newMeal.totalKcal} kcal, cat=${newMeal.category}, type=${newMeal.mealType}, date=${newMeal.date}")
                    launchSummary(newMeal.date, dailyLogDao, daySummaryGenerator, "LogScreen:mealAdd")
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

// ── Mini stat ──

@Composable
private fun MiniStat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = OnSurface)
        if (unit.isNotBlank()) Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.7f))
    }
}

// ── Day Card ──

@Composable
internal fun DayCard(
    date: LocalDate,
    log: DailyLog?,
    meals: List<MealEntry>,
    onEdit: () -> Unit,
    onMealClick: (MealEntry) -> Unit,
    onAddMeal: () -> Unit,
) {
    val dateLabel = when (date) {
        LocalDate.now() -> stringResource(R.string.day_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                ", " + date.format(DateTimeFormatter.ofPattern("d MMM"))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dateLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit), tint = Primary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stats chips
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatChip(Icons.Default.Scale, log?.weightKg?.let { "%.1f kg".format(it) }, stringResource(R.string.stat_weight))
                StatChip(Icons.AutoMirrored.Filled.DirectionsWalk, log?.steps?.let { "%,d".format(it) }, stringResource(R.string.stat_steps))
                StatChip(Icons.Default.Bedtime, log?.sleepHours?.let { "%.1fh".format(it) }, stringResource(R.string.stat_sleep))
                StatChip(Icons.Default.FavoriteBorder, log?.restingHr?.let { "$it bpm" }, stringResource(R.string.stat_heart_rate))
            }

            // Meals section
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.section_meals), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                IconButton(onClick = onAddMeal, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_meal), tint = Primary, modifier = Modifier.size(18.dp))
                }
            }

            if (meals.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                // Sort: breakfast → brunch → lunch → dinner, snacks between, null-type at end
                val sortedMeals = meals.sortedWith(compareByDescending(nullsFirst()) { it.mealType?.ordinal })
                val dayTotalKcal = meals.sumOf { it.totalKcal }
                val dayTotalProtein = meals.sumOf { it.totalProteinG }
                val dayTotalCarbs = meals.sumOf { it.totalCarbsG }
                val dayTotalFat = meals.sumOf { it.totalFatG }
                sortedMeals.forEach { meal ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                            .clickable { onMealClick(meal) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        // Line 1: icon, meal type, description
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                categoryIcon(meal.category),
                                contentDescription = null,
                                tint = categoryColor(meal.category),
                                modifier = Modifier.size(14.dp),
                            )
                            Spacer(Modifier.width(6.dp))
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
                                meal.description.take(40) + if (meal.description.length > 40) "\u2026" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface,
                                maxLines = 1,
                            )
                        }
                        // Line 2: kcal P C F with percentages
                        Row(
                            modifier = Modifier.padding(start = 20.dp, top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "${meal.totalKcal} kcal",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = Primary,
                            )
                            if (meal.totalProteinG > 0) {
                                val pct = if (dayTotalProtein > 0) (meal.totalProteinG * 100 / dayTotalProtein) else 0
                                Text(
                                    "P ${meal.totalProteinG}g ($pct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Secondary,
                                )
                            }
                            if (meal.totalCarbsG > 0) {
                                val pct = if (dayTotalCarbs > 0) (meal.totalCarbsG * 100 / dayTotalCarbs) else 0
                                Text(
                                    "C ${meal.totalCarbsG}g ($pct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant,
                                )
                            }
                            if (meal.totalFatG > 0) {
                                val pct = if (dayTotalFat > 0) (meal.totalFatG * 100 / dayTotalFat) else 0
                                Text(
                                    "F ${meal.totalFatG}g ($pct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = OnSurfaceVariant,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                val totalKcal = dayTotalKcal
                val totalProtein = dayTotalProtein
                val totalCarbs = dayTotalCarbs
                val totalFat = dayTotalFat
                val macroSuffix = buildString {
                    if (totalProtein > 0) append(" · ${totalProtein}g P")
                    if (totalCarbs > 0) append(" · ${totalCarbs}g C")
                    if (totalFat > 0) append(" · ${totalFat}g F")
                }
                Text(
                    stringResource(R.string.meals_total_kcal, totalKcal) + macroSuffix,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(
                    stringResource(R.string.no_meals_logged),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant.copy(alpha = 0.4f),
                )
            }

            // Exercises
            val exercises = parseExercises(log?.exercisesJson)
            if (exercises.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.section_exercises), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                exercises.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(ex.name, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                        Text(
                            buildString {
                                if (ex.durationMin > 0) append("${ex.durationMin}min")
                                if (ex.kcal > 0) { if (isNotEmpty()) append(" \u00b7 "); append("${ex.kcal} kcal") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Secondary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }

            // Notes
            if (!log?.notes.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(log!!.notes!!, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }

            // AI day summary
            if (!log?.daySummary.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                val isLoading = log!!.daySummary == SUMMARY_PLACEHOLDER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = stringResource(R.string.cd_ai_summary),
                        tint = Primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = Primary.copy(alpha = 0.5f),
                            trackColor = Primary.copy(alpha = 0.1f),
                        )
                    } else {
                        Text(
                            log.daySummary!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RowScope.StatChip(icon: ImageVector, value: String?, label: String) {
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Surface).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (value != null) Primary else OnSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            value ?: "\u2014",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (value != null) OnSurface else OnSurfaceVariant.copy(alpha = 0.3f),
        )
    }
}

// ══════════════════════════════════════════════════
// ── Add Meal Sheet (manual entry) ──
// ══════════════════════════════════════════════════

@Composable
internal fun AddMealSheet(
    date: LocalDate,
    onSave: (MealEntry) -> Unit,
    onDismiss: () -> Unit,
    onCamera: (() -> Unit)? = null,
) {
    var description by remember { mutableStateOf("") }
    var kcalStr by remember { mutableStateOf("") }
    var proteinStr by remember { mutableStateOf("") }
    var carbsStr by remember { mutableStateOf("") }
    var fatStr by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(MealCategory.HOME) }
    var selectedMealType by remember { mutableStateOf<MealType?>(null) }
    var note by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.add_meal_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onCamera != null) {
                    IconButton(onClick = onCamera) {
                        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.cd_log_camera), tint = Accent)
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Primary, modifier = Modifier.size(12.dp).offset(x = (-4).dp, y = (-8).dp))
                    }
                }
                IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = OnSurfaceVariant) }
            }
        }

        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")),
            style = MaterialTheme.typography.bodyMedium,
            color = OnSurfaceVariant,
        )

        // Description
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            label = { Text(stringResource(R.string.field_what_did_you_eat)) },
            placeholder = { Text("e.g. Grilled chicken with rice and salad") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
            colors = editFieldColors(),
            shape = RoundedCornerShape(10.dp),
        )

        // Calories
        EditField(
            icon = Icons.Default.LocalFireDepartment,
            label = stringResource(R.string.field_estimated_calories),
            value = kcalStr,
            onValueChange = { kcalStr = it },
            keyboardType = KeyboardType.Number,
        )

        // Protein
        EditField(
            icon = Icons.Default.FitnessCenter,
            label = stringResource(R.string.field_protein_g),
            value = proteinStr,
            onValueChange = { proteinStr = it },
            keyboardType = KeyboardType.Number,
        )

        // Carbs
        EditField(
            icon = Icons.Default.Grain,
            label = stringResource(R.string.field_carbs_g),
            value = carbsStr,
            onValueChange = { carbsStr = it },
            keyboardType = KeyboardType.Number,
        )

        // Fat
        EditField(
            icon = Icons.Default.WaterDrop,
            label = stringResource(R.string.field_fat_g),
            value = fatStr,
            onValueChange = { fatStr = it },
            keyboardType = KeyboardType.Number,
        )

        // Category selector
        Text(stringResource(R.string.section_source), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MealCategory.entries.forEach { cat ->
                FilterChip(
                    selected = selectedCategory == cat,
                    onClick = { selectedCategory = cat },
                    label = { Text(categoryLabel(cat)) },
                    leadingIcon = { Icon(categoryIcon(cat), contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.15f),
                        selectedLabelColor = Primary,
                        selectedLeadingIconColor = Primary,
                    ),
                )
            }
        }

        // Meal type selector
        Text(stringResource(R.string.section_meal_type), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            MealType.entries.forEach { type ->
                FilterChip(
                    selected = selectedMealType == type,
                    onClick = { selectedMealType = if (selectedMealType == type) null else type },
                    label = { Text(mealTypeLabel(type), style = MaterialTheme.typography.labelSmall) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Accent.copy(alpha = 0.15f),
                        selectedLabelColor = Accent,
                    ),
                )
            }
        }

        // Note
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.field_note_optional)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
            colors = editFieldColors(),
            shape = RoundedCornerShape(10.dp),
        )

        Button(
            onClick = {
                if (description.isNotBlank()) {
                    onSave(
                        MealEntry(
                            date = date,
                            description = description.trim(),
                            totalKcal = kcalStr.toIntOrNull() ?: 0,
                            totalProteinG = proteinStr.toIntOrNull() ?: 0,
                            totalCarbsG = carbsStr.toIntOrNull() ?: 0,
                            totalFatG = fatStr.toIntOrNull() ?: 0,
                            category = selectedCategory,
                            mealType = selectedMealType,
                            note = note.ifBlank { null },
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp),
            enabled = description.isNotBlank(),
        ) {
            Text(stringResource(R.string.button_save_meal), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Surface)
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ══════════════════════════════════════════════════
// ── Meal Edit Sheet (tap existing meal) ──
// ══════════════════════════════════════════════════

@Composable
internal fun MealEditSheet(
    meal: MealEntry,
    onSave: (MealEntry) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var description by remember { mutableStateOf(meal.description) }
    var kcalStr by remember { mutableStateOf(meal.totalKcal.toString()) }
    var proteinStr by remember { mutableStateOf(meal.totalProteinG.toString()) }
    var carbsStr by remember { mutableStateOf(meal.totalCarbsG.toString()) }
    var fatStr by remember { mutableStateOf(meal.totalFatG.toString()) }
    var selectedCategory by remember { mutableStateOf(meal.category) }
    var selectedMealType by remember { mutableStateOf(meal.mealType) }
    var note by remember { mutableStateOf(meal.note ?: "") }
    var editing by remember { mutableStateOf(false) }

    val items = remember { parseItems(meal.itemsJson) }
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM \u00b7 h:mm a")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header + close
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = meal.createdAt.atZone(java.time.ZoneId.systemDefault()).format(dateFmt),
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
            )
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = OnSurfaceVariant) }
        }

        // Category badge
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(categoryIcon(if (editing) selectedCategory else meal.category), contentDescription = null, tint = categoryColor(if (editing) selectedCategory else meal.category), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(categoryLabel(if (editing) selectedCategory else meal.category), style = MaterialTheme.typography.labelMedium, color = categoryColor(if (editing) selectedCategory else meal.category))
        }

        if (editing) {
            // ── Edit mode ──
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp),
                label = { Text(stringResource(R.string.field_description)) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                colors = editFieldColors(),
                shape = RoundedCornerShape(10.dp),
            )

            EditField(
                icon = Icons.Default.LocalFireDepartment,
                label = stringResource(R.string.field_calories_kcal),
                value = kcalStr,
                onValueChange = { kcalStr = it },
                keyboardType = KeyboardType.Number,
            )

            EditField(
                icon = Icons.Default.FitnessCenter,
                label = stringResource(R.string.field_protein_g),
                value = proteinStr,
                onValueChange = { proteinStr = it },
                keyboardType = KeyboardType.Number,
            )

            EditField(
                icon = Icons.Default.Grain,
                label = stringResource(R.string.field_carbs_g),
                value = carbsStr,
                onValueChange = { carbsStr = it },
                keyboardType = KeyboardType.Number,
            )

            EditField(
                icon = Icons.Default.WaterDrop,
                label = stringResource(R.string.field_fat_g),
                value = fatStr,
                onValueChange = { fatStr = it },
                keyboardType = KeyboardType.Number,
            )

            Text(stringResource(R.string.section_source), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MealCategory.entries.forEach { cat ->
                    FilterChip(
                        selected = selectedCategory == cat,
                        onClick = { selectedCategory = cat },
                        label = { Text(categoryLabel(cat)) },
                        leadingIcon = { Icon(categoryIcon(cat), contentDescription = null, modifier = Modifier.size(16.dp)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Primary.copy(alpha = 0.15f),
                            selectedLabelColor = Primary,
                            selectedLeadingIconColor = Primary,
                        ),
                    )
                }
            }

            // Meal type selector
            Text(stringResource(R.string.section_meal_type), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                MealType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedMealType == type,
                        onClick = { selectedMealType = if (selectedMealType == type) null else type },
                        label = { Text(mealTypeLabel(type), style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Accent.copy(alpha = 0.15f),
                            selectedLabelColor = Accent,
                        ),
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_note)) },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                colors = editFieldColors(),
                shape = RoundedCornerShape(10.dp),
            )

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { editing = false; description = meal.description; kcalStr = meal.totalKcal.toString(); proteinStr = meal.totalProteinG.toString(); carbsStr = meal.totalCarbsG.toString(); fatStr = meal.totalFatG.toString(); selectedCategory = meal.category; selectedMealType = meal.mealType; note = meal.note ?: "" },
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.button_cancel)) }

                Button(
                    onClick = {
                        onSave(meal.copy(
                            description = description.trim(),
                            totalKcal = kcalStr.toIntOrNull() ?: meal.totalKcal,
                            totalProteinG = proteinStr.toIntOrNull() ?: meal.totalProteinG,
                            totalCarbsG = carbsStr.toIntOrNull() ?: meal.totalCarbsG,
                            totalFatG = fatStr.toIntOrNull() ?: meal.totalFatG,
                            category = selectedCategory,
                            mealType = selectedMealType,
                            note = note.ifBlank { null },
                        ))
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                ) { Text(stringResource(R.string.button_save), color = Surface) }
            }
        } else {
            // ── View mode ──
            Text(description, style = MaterialTheme.typography.bodyLarge, color = OnSurface)

            if (items.isNotEmpty()) {
                items.forEach { item ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Surface),
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
                                Text(item.portion, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                            }
                            Text("${item.calories} kcal", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Primary)
                        }
                    }
                }
            }

            // Total
            Card(
                colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.label_total), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${meal.totalKcal} kcal", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Primary)
                        if (meal.totalProteinG > 0) {
                            Text("${meal.totalProteinG}g protein", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Secondary)
                        }
                        val macros = buildString {
                            if (meal.totalCarbsG > 0) append("${meal.totalCarbsG}g C")
                            if (meal.totalFatG > 0) { if (isNotEmpty()) append("  "); append("${meal.totalFatG}g F") }
                        }
                        if (macros.isNotEmpty()) {
                            Text(macros, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                        }
                    }
                }
            }

            // Coach note
            if (!meal.coachNote.isNullOrBlank()) {
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(8.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(stringResource(R.string.label_coach_said), style = MaterialTheme.typography.labelLarge, color = Accent)
                        Spacer(Modifier.height(4.dp))
                        Text(meal.coachNote, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    }
                }
            }

            // User note
            if (!meal.note.isNullOrBlank()) {
                Text(stringResource(R.string.label_note_prefix) + meal.note, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }

            // Action buttons
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { editing = true },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Primary),
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.button_edit))
                }
                OutlinedButton(
                    onClick = onDelete,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.button_delete))
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ══════════════════════════════════════════════════
// ── Daily log edit sheet (unchanged from before) ──
// ══════════════════════════════════════════════════

@Composable
internal fun DailyLogEditSheet(
    date: LocalDate,
    existingLog: DailyLog?,
    onSave: (DailyLog) -> Unit,
    onDismiss: () -> Unit,
) {
    var weightStr by remember { mutableStateOf(existingLog?.weightKg?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var stepsStr by remember { mutableStateOf(existingLog?.steps?.toString() ?: "") }
    var sleepStr by remember { mutableStateOf(existingLog?.sleepHours?.let { String.format(Locale.US, "%.1f", it) } ?: "") }
    var hrStr by remember { mutableStateOf(existingLog?.restingHr?.toString() ?: "") }
    var notes by remember { mutableStateOf(existingLog?.notes ?: "") }

    val exercises = remember { mutableStateListOf<ExerciseItem>() }
    LaunchedEffect(existingLog) { exercises.clear(); exercises.addAll(parseExercises(existingLog?.exercisesJson)) }
    var newExName by remember { mutableStateOf("") }
    var newExDuration by remember { mutableStateOf("") }
    var newExKcal by remember { mutableStateOf("") }

    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(date.format(dateFmt), style = MaterialTheme.typography.titleMedium, color = OnSurface)
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close), tint = OnSurfaceVariant) }
        }

        EditField(icon = Icons.Default.Scale, label = stringResource(R.string.field_weight_kg), value = weightStr, onValueChange = { weightStr = it }, keyboardType = KeyboardType.Decimal)
        EditField(icon = Icons.AutoMirrored.Filled.DirectionsWalk, label = stringResource(R.string.field_steps), value = stepsStr, onValueChange = { stepsStr = it }, keyboardType = KeyboardType.Number)
        EditField(icon = Icons.Default.Bedtime, label = stringResource(R.string.field_sleep_hours), value = sleepStr, onValueChange = { sleepStr = it }, keyboardType = KeyboardType.Decimal)
        EditField(icon = Icons.Default.FavoriteBorder, label = stringResource(R.string.field_resting_hr), value = hrStr, onValueChange = { hrStr = it }, keyboardType = KeyboardType.Number)

        Text(stringResource(R.string.section_exercises), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)

        exercises.forEachIndexed { idx, ex ->
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Surface).padding(horizontal = 10.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(ex.name, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Text(
                        buildString {
                            if (ex.durationMin > 0) append("${ex.durationMin} min")
                            if (ex.kcal > 0) { if (isNotEmpty()) append(" \u00b7 "); append("${ex.kcal} kcal") }
                        },
                        style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant,
                    )
                }
                IconButton(onClick = { exercises.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_remove), tint = Tertiary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(value = newExName, onValueChange = { newExName = it }, modifier = Modifier.weight(1f), label = { Text(stringResource(R.string.field_exercise_name)) }, singleLine = true, textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(8.dp))
            OutlinedTextField(value = newExDuration, onValueChange = { newExDuration = it }, modifier = Modifier.width(60.dp), label = { Text(stringResource(R.string.field_exercise_min)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(8.dp))
            OutlinedTextField(value = newExKcal, onValueChange = { newExKcal = it }, modifier = Modifier.width(70.dp), label = { Text(stringResource(R.string.field_exercise_kcal)) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(8.dp))
            FilledIconButton(
                onClick = {
                    if (newExName.isNotBlank()) {
                        exercises.add(ExerciseItem(newExName.trim(), newExDuration.toIntOrNull() ?: 0, newExKcal.toIntOrNull() ?: 0))
                        newExName = ""; newExDuration = ""; newExKcal = ""
                    }
                },
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary),
            ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add), tint = Surface, modifier = Modifier.size(18.dp)) }
        }

        OutlinedTextField(
            value = notes, onValueChange = { notes = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 80.dp),
            label = { Text(stringResource(R.string.field_notes)) }, placeholder = { Text(stringResource(R.string.placeholder_notes)) },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(10.dp),
        )

        Button(
            onClick = {
                val exercisesJson = if (exercises.isEmpty()) null else buildJsonArray {
                    exercises.forEach { ex -> add(buildJsonObject { put("name", ex.name); put("durationMin", ex.durationMin); put("kcal", ex.kcal) }) }
                }.toString()
                onSave(DailyLog(
                    date = date,
                    weightKg = weightStr.replace(',', '.').toDoubleOrNull(),
                    steps = stepsStr.toIntOrNull(),
                    sleepHours = sleepStr.replace(',', '.').toDoubleOrNull(),
                    restingHr = hrStr.toIntOrNull(),
                    exercisesJson = exercisesJson,
                    notes = notes.ifBlank { null },
                    offPlan = existingLog?.offPlan ?: false,
                    daySummary = existingLog?.daySummary,
                ))
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp),
        ) { Text(stringResource(R.string.button_save), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Surface) }

        Spacer(Modifier.height(32.dp))
    }
}

// ── Shared field composables ──

@Composable
internal fun EditField(icon: ImageVector, label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType = KeyboardType.Text) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp)) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(10.dp),
    )
}

@Composable
internal fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SurfaceVariant, unfocusedContainerColor = SurfaceVariant,
    focusedBorderColor = Primary.copy(alpha = 0.5f), unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    cursorColor = Primary,
)

// ── JSON helpers ──

internal data class ExerciseItem(val name: String, val durationMin: Int, val kcal: Int)
private data class ParsedMealItem(val name: String, val portion: String, val calories: Int)

internal fun parseExercises(json: String?): List<ExerciseItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        Json.parseToJsonElement(json).jsonArray.map { el ->
            val obj = el.jsonObject
            ExerciseItem(
                name = obj["name"]?.jsonPrimitive?.content ?: "Exercise",
                durationMin = obj["durationMin"]?.jsonPrimitive?.intOrNull ?: 0,
                kcal = obj["kcal"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    } catch (_: Exception) { emptyList() }
}

private fun parseItems(json: String?): List<ParsedMealItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        Json.parseToJsonElement(json).jsonArray.map { el ->
            val obj = el.jsonObject
            ParsedMealItem(
                name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                portion = obj["portion"]?.jsonPrimitive?.content ?: "",
                calories = obj["calories"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    } catch (_: Exception) { emptyList() }
}

internal const val SUMMARY_PLACEHOLDER = "⏳"
internal val summaryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Writes a placeholder to the daySummary field immediately so the UI shows
 * a loading indicator, then generates the real summary in the background.
 */
internal fun launchSummary(date: LocalDate, dailyLogDao: DailyLogDao, generator: DaySummaryGenerator?, reason: String = "unknown") {
    if (generator == null) return
    summaryScope.launch {
        val existing = dailyLogDao.getForDate(date) ?: DailyLog(date = date)
        dailyLogDao.upsert(existing.copy(daySummary = SUMMARY_PLACEHOLDER))
        generator.generateForDate(date, reason)
    }
}
