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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(
    mealDao: MealDao,
    dailyLogDao: DailyLogDao,
    preferencesManager: PreferencesManager,
) {
    val startDateStr by preferencesManager.startDate.collectAsState(initial = null)
    val startDate = startDateStr?.let {
        try { LocalDate.parse(it) } catch (_: Exception) { null }
    }

    if (startDate == null) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 32.dp),
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = OnSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(64.dp),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Set your goal to start logging",
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Go to Settings \u2192 Edit goal to set your start date, then daily logs will appear here.",
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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedMeal by remember { mutableStateOf<MealEntry?>(null) }
    val mealSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Daily Log",
            style = MaterialTheme.typography.headlineMedium,
            color = OnSurface,
        )

        val todayLog = logsByDate[today]
        val todayMeals = mealsByDate[today] ?: emptyList()
        val todayKcal = todayMeals.sumOf { it.totalKcal }

        Card(
            colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                MiniStat("Weight", todayLog?.weightKg?.let { "%.1f".format(it) } ?: "\u2014", "kg")
                MiniStat("Meals", "${todayMeals.size}", "$todayKcal kcal")
                MiniStat("Steps", todayLog?.steps?.let { "%,d".format(it) } ?: "\u2014", "")
                MiniStat("Sleep", todayLog?.sleepHours?.let { "%.1f".format(it) } ?: "\u2014", "hrs")
            }
        }

        allDates.forEach { date ->
            val log = logsByDate[date]
            val dayMeals = mealsByDate[date] ?: emptyList()

            DayCard(
                date = date,
                log = log,
                meals = dayMeals,
                onEdit = { editingDate = date },
                onMealClick = { selectedMeal = it },
            )
        }

        Spacer(Modifier.height(80.dp))
    }

    if (editingDate != null) {
        ModalBottomSheet(
            onDismissRequest = { editingDate = null },
            sheetState = sheetState,
            containerColor = CardSurface,
        ) {
            DailyLogEditSheet(
                date = editingDate!!,
                existingLog = logsByDate[editingDate!!],
                onSave = { updatedLog ->
                    scope.launch {
                        dailyLogDao.upsert(updatedLog)
                        editingDate = null
                    }
                },
                onDismiss = { editingDate = null },
            )
        }
    }

    if (selectedMeal != null) {
        ModalBottomSheet(
            onDismissRequest = { selectedMeal = null },
            sheetState = mealSheetState,
            containerColor = CardSurface,
        ) {
            MealDetailSheet(
                meal = selectedMeal!!,
                onDelete = {
                    scope.launch {
                        mealDao.delete(selectedMeal!!)
                        selectedMeal = null
                    }
                },
                onDismiss = { selectedMeal = null },
            )
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, unit: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = OnSurface,
        )
        if (unit.isNotBlank()) {
            Text(unit, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant.copy(alpha = 0.7f))
    }
}

@Composable
private fun DayCard(
    date: LocalDate,
    log: DailyLog?,
    meals: List<MealEntry>,
    onEdit: () -> Unit,
    onMealClick: (MealEntry) -> Unit,
) {
    val dateLabel = when (date) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                ", " + date.format(DateTimeFormatter.ofPattern("d MMM"))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    dateLabel,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = OnSurface,
                )
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit",
                        tint = Primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StatChip(Icons.Default.Scale, log?.weightKg?.let { "%.1f kg".format(it) }, "Weight")
                StatChip(Icons.AutoMirrored.Filled.DirectionsWalk, log?.steps?.let { "%,d".format(it) }, "Steps")
                StatChip(Icons.Default.Bedtime, log?.sleepHours?.let { "%.1fh".format(it) }, "Sleep")
                StatChip(Icons.Default.FavoriteBorder, log?.restingHr?.let { "$it bpm" }, "HR")
            }

            if (meals.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Meals", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                meals.forEach { meal ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                            .clickable { onMealClick(meal) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            meal.description.take(40) + if (meal.description.length > 40) "\u2026" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "${meal.totalKcal} kcal",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = Primary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
                val totalKcal = meals.sumOf { it.totalKcal }
                Text(
                    "Total: $totalKcal kcal",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Primary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            val exercises = parseExercises(log?.exercisesJson)
            if (exercises.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("Exercises", style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
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

            if (!log?.notes.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(log!!.notes!!, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }

            if (log == null && meals.isEmpty()) {
                Text(
                    "No data logged",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RowScope.StatChip(icon: ImageVector, value: String?, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Surface)
            .padding(vertical = 6.dp),
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

@Composable
private fun DailyLogEditSheet(
    date: LocalDate,
    existingLog: DailyLog?,
    onSave: (DailyLog) -> Unit,
    onDismiss: () -> Unit,
) {
    var weightStr by remember { mutableStateOf(existingLog?.weightKg?.let { "%.1f".format(it) } ?: "") }
    var stepsStr by remember { mutableStateOf(existingLog?.steps?.toString() ?: "") }
    var sleepStr by remember { mutableStateOf(existingLog?.sleepHours?.let { "%.1f".format(it) } ?: "") }
    var hrStr by remember { mutableStateOf(existingLog?.restingHr?.toString() ?: "") }
    var notes by remember { mutableStateOf(existingLog?.notes ?: "") }

    val exercises = remember { mutableStateListOf<ExerciseItem>() }
    LaunchedEffect(existingLog) {
        exercises.clear()
        exercises.addAll(parseExercises(existingLog?.exercisesJson))
    }
    var newExName by remember { mutableStateOf("") }
    var newExDuration by remember { mutableStateOf("") }
    var newExKcal by remember { mutableStateOf("") }

    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                date.format(dateFmt),
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant)
            }
        }

        EditField(
            icon = Icons.Default.Scale,
            label = "Weight (kg)",
            value = weightStr,
            onValueChange = { weightStr = it },
            keyboardType = KeyboardType.Decimal,
        )

        EditField(
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            label = "Steps",
            value = stepsStr,
            onValueChange = { stepsStr = it },
            keyboardType = KeyboardType.Number,
        )

        EditField(
            icon = Icons.Default.Bedtime,
            label = "Sleep (hours)",
            value = sleepStr,
            onValueChange = { sleepStr = it },
            keyboardType = KeyboardType.Decimal,
        )

        EditField(
            icon = Icons.Default.FavoriteBorder,
            label = "Resting heart rate (bpm)",
            value = hrStr,
            onValueChange = { hrStr = it },
            keyboardType = KeyboardType.Number,
        )

        Text(
            "Exercises",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = OnSurface,
        )

        exercises.forEachIndexed { idx, ex ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSurfaceVariant,
                    )
                }
                IconButton(onClick = { exercises.removeAt(idx) }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = Tertiary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = newExName,
                onValueChange = { newExName = it },
                modifier = Modifier.weight(1f),
                label = { Text("Exercise") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurface),
                colors = editFieldColors(),
                shape = RoundedCornerShape(8.dp),
            )
            OutlinedTextField(
                value = newExDuration,
                onValueChange = { newExDuration = it },
                modifier = Modifier.width(60.dp),
                label = { Text("Min") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurface),
                colors = editFieldColors(),
                shape = RoundedCornerShape(8.dp),
            )
            OutlinedTextField(
                value = newExKcal,
                onValueChange = { newExKcal = it },
                modifier = Modifier.width(70.dp),
                label = { Text("Kcal") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.bodySmall.copy(color = OnSurface),
                colors = editFieldColors(),
                shape = RoundedCornerShape(8.dp),
            )
            FilledIconButton(
                onClick = {
                    if (newExName.isNotBlank()) {
                        exercises.add(ExerciseItem(newExName.trim(), newExDuration.toIntOrNull() ?: 0, newExKcal.toIntOrNull() ?: 0))
                        newExName = ""; newExDuration = ""; newExKcal = ""
                    }
                },
                modifier = Modifier.size(40.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Primary),
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = Surface, modifier = Modifier.size(18.dp))
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 80.dp),
            label = { Text("Notes") },
            placeholder = { Text("How was your day? Any observations\u2026") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
            colors = editFieldColors(),
            shape = RoundedCornerShape(10.dp),
        )

        Button(
            onClick = {
                val exercisesJson = if (exercises.isEmpty()) null else buildJsonArray {
                    exercises.forEach { ex ->
                        add(buildJsonObject {
                            put("name", ex.name)
                            put("durationMin", ex.durationMin)
                            put("kcal", ex.kcal)
                        })
                    }
                }.toString()

                onSave(
                    DailyLog(
                        date = date,
                        weightKg = weightStr.toDoubleOrNull(),
                        steps = stepsStr.toIntOrNull(),
                        sleepHours = sleepStr.toDoubleOrNull(),
                        restingHr = hrStr.toIntOrNull(),
                        exercisesJson = exercisesJson,
                        notes = notes.ifBlank { null },
                        offPlan = existingLog?.offPlan ?: false,
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Save", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = Surface)
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun EditField(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = Primary, modifier = Modifier.size(20.dp)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurface),
        colors = editFieldColors(),
        shape = RoundedCornerShape(10.dp),
    )
}

@Composable
private fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SurfaceVariant,
    unfocusedContainerColor = SurfaceVariant,
    focusedBorderColor = Primary.copy(alpha = 0.5f),
    unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    cursorColor = Primary,
)

@Composable
private fun MealDetailSheet(
    meal: MealEntry,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = meal.createdAt.atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEEE, d MMM \u00b7 h:mm a")),
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant)
            }
        }

        Text(meal.description, style = MaterialTheme.typography.bodyLarge, color = OnSurface)

        val items = parseItems(meal.itemsJson)
        items.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
                        Text(item.portion, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
                    }
                    Text("${item.calories} kcal", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Primary)
                }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Total", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = OnSurface)
                Text("${meal.totalKcal} kcal", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Primary)
            }
        }

        if (!meal.coachNote.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text("Coach said", style = MaterialTheme.typography.labelLarge, color = Accent)
                    Spacer(Modifier.height(4.dp))
                    Text(meal.coachNote, style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                }
            }
        }

        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete meal")
        }

        Spacer(Modifier.height(32.dp))
    }
}

private data class ExerciseItem(val name: String, val durationMin: Int, val kcal: Int)

private data class ParsedMealItem(val name: String, val portion: String, val calories: Int)

private fun parseExercises(json: String?): List<ExerciseItem> {
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
