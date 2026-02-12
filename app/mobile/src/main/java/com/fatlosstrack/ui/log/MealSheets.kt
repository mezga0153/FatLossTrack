package com.fatlosstrack.ui.log

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ══════════════════════════════════════════════════
// ── Add Meal Sheet (manual entry) ──
// ══════════════════════════════════════════════════

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMealSheet(
    date: LocalDate,
    onSave: (MealEntry) -> Unit,
    onDismiss: () -> Unit,
    onCamera: (() -> Unit)? = null,
    prefillDescription: String = "",
    prefillKcal: Int? = null,
    prefillProteinG: Int? = null,
    prefillCarbsG: Int? = null,
    prefillFatG: Int? = null,
    prefillCategory: MealCategory = MealCategory.HOME,
    prefillMealType: MealType? = null,
    prefillItemsJson: String? = null,
    showDateSelector: Boolean = false,
) {
    var description by remember { mutableStateOf(prefillDescription) }
    var kcalStr by remember { mutableStateOf(prefillKcal?.toString() ?: "") }
    var proteinStr by remember { mutableStateOf(prefillProteinG?.toString() ?: "") }
    var carbsStr by remember { mutableStateOf(prefillCarbsG?.toString() ?: "") }
    var fatStr by remember { mutableStateOf(prefillFatG?.toString() ?: "") }
    var selectedCategory by remember { mutableStateOf(prefillCategory) }
    var selectedMealType by remember { mutableStateOf(prefillMealType) }
    var note by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(date) }
    var showDatePicker by remember { mutableStateOf(false) }

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

        // Date display / selector
        if (showDateSelector) {
            val today = LocalDate.now()
            val yesterday = today.minusDays(1)
            Text(stringResource(R.string.section_log_date), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = selectedDate == today,
                    onClick = { selectedDate = today },
                    label = { Text(stringResource(R.string.day_today)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.15f),
                        selectedLabelColor = Primary,
                    ),
                )
                FilterChip(
                    selected = selectedDate == yesterday,
                    onClick = { selectedDate = yesterday },
                    label = { Text(stringResource(R.string.day_yesterday)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.15f),
                        selectedLabelColor = Primary,
                    ),
                )
                FilterChip(
                    selected = selectedDate != today && selectedDate != yesterday,
                    onClick = { showDatePicker = true },
                    label = {
                        Text(
                            if (selectedDate != today && selectedDate != yesterday)
                                selectedDate.format(DateTimeFormatter.ofPattern("MMM d"))
                            else
                                stringResource(R.string.meal_date_other),
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Primary.copy(alpha = 0.15f),
                        selectedLabelColor = Primary,
                    ),
                )
            }
        } else {
            Text(
                selectedDate.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy")),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )
        }

        // Date picker dialog
        if (showDatePicker) {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = selectedDate
                    .atStartOfDay(java.time.ZoneId.of("UTC"))
                    .toInstant()
                    .toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            selectedDate = java.time.Instant.ofEpochMilli(millis)
                                .atZone(java.time.ZoneId.of("UTC"))
                                .toLocalDate()
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.chat_clear_no))
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }

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
            iconTint = Secondary,
        )

        // Protein
        EditField(
            icon = Icons.Default.FitnessCenter,
            label = stringResource(R.string.field_protein_g),
            value = proteinStr,
            onValueChange = { proteinStr = it },
            keyboardType = KeyboardType.Number,
            iconTint = Primary,
        )

        // Carbs
        EditField(
            icon = Icons.Default.Grain,
            label = stringResource(R.string.field_carbs_g),
            value = carbsStr,
            onValueChange = { carbsStr = it },
            keyboardType = KeyboardType.Number,
            iconTint = Tertiary,
        )

        // Fat
        EditField(
            icon = Icons.Default.WaterDrop,
            label = stringResource(R.string.field_fat_g),
            value = fatStr,
            onValueChange = { fatStr = it },
            keyboardType = KeyboardType.Number,
            iconTint = Accent,
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
                            date = selectedDate,
                            description = description.trim(),
                            itemsJson = prefillItemsJson,
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
    openAiService: OpenAiService? = null,
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
    var aiEditing by remember { mutableStateOf(false) }
    var aiPrompt by remember { mutableStateOf("") }
    var aiLoading by remember { mutableStateOf(false) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val aiFocusRequester = remember { FocusRequester() }

    val items = remember { parseItems(meal.itemsJson) }
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, d MMM \u00b7 h:mm a")
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when AI edit card appears
    LaunchedEffect(aiEditing) {
        if (aiEditing) scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(scrollState),
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
                iconTint = Secondary,
            )

            EditField(
                icon = Icons.Default.FitnessCenter,
                label = stringResource(R.string.field_protein_g),
                value = proteinStr,
                onValueChange = { proteinStr = it },
                keyboardType = KeyboardType.Number,
                iconTint = Primary,
            )

            EditField(
                icon = Icons.Default.Grain,
                label = stringResource(R.string.field_carbs_g),
                value = carbsStr,
                onValueChange = { carbsStr = it },
                keyboardType = KeyboardType.Number,
                iconTint = Tertiary,
            )

            EditField(
                icon = Icons.Default.WaterDrop,
                label = stringResource(R.string.field_fat_g),
                value = fatStr,
                onValueChange = { fatStr = it },
                keyboardType = KeyboardType.Number,
                iconTint = Accent,
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
                            Text("${item.calories} kcal", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Secondary)
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
                        Text("${meal.totalKcal} kcal", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Secondary)
                        if (meal.totalProteinG > 0) {
                            Text("${meal.totalProteinG}g protein", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = Primary)
                        }
                        if (meal.totalCarbsG > 0) {
                            Text("${meal.totalCarbsG}g carbs", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Tertiary)
                        }
                        if (meal.totalFatG > 0) {
                            Text("${meal.totalFatG}g fat", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Accent)
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

            if (!aiEditing) {
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
                    if (openAiService != null) {
                        OutlinedButton(
                            onClick = { aiEditing = true; aiError = null },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Accent),
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.button_ai_edit))
                        }
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
            } else {
                // AI edit card (replaces action buttons)
                Card(colors = CardDefaults.cardColors(containerColor = Surface), shape = RoundedCornerShape(12.dp)) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ai_edit_label), style = MaterialTheme.typography.labelMedium, color = Accent)
                        OutlinedTextField(
                            value = aiPrompt,
                            onValueChange = { aiPrompt = it },
                            placeholder = { Text(stringResource(R.string.ai_edit_placeholder), color = OnSurfaceVariant.copy(alpha = 0.5f)) },
                            modifier = Modifier.fillMaxWidth().focusRequester(aiFocusRequester),
                            colors = editFieldColors(),
                            minLines = 2,
                            maxLines = 4,
                            enabled = !aiLoading,
                        )
                        if (aiError != null) {
                            Text(aiError!!, style = MaterialTheme.typography.bodySmall, color = Tertiary)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { aiEditing = false; aiPrompt = ""; aiError = null }) {
                                Text(stringResource(R.string.button_cancel), color = OnSurfaceVariant)
                            }
                            Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (aiPrompt.isBlank()) return@Button
                                aiLoading = true
                                aiError = null
                                val mealJson = buildJsonObject {
                                    put("description", meal.description)
                                    put("source", meal.category.name.lowercase())
                                    meal.mealType?.let { put("meal_type", it.name.lowercase()) }
                                    meal.itemsJson?.let { put("items", Json.parseToJsonElement(it)) }
                                    put("total_calories", meal.totalKcal)
                                    put("total_protein_g", meal.totalProteinG)
                                    put("total_carbs_g", meal.totalCarbsG)
                                    put("total_fat_g", meal.totalFatG)
                                    meal.coachNote?.let { put("coach_note", it) }
                                }.toString()
                                scope.launch {
                                    val result = openAiService!!.editMealWithAi(mealJson, aiPrompt)
                                    result.onSuccess { raw ->
                                        try {
                                            val clean = raw.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
                                            val obj = Json.parseToJsonElement(clean).jsonObject
                                            description = obj["description"]?.jsonPrimitive?.content ?: description
                                            kcalStr = (obj["total_calories"]?.jsonPrimitive?.intOrNull ?: meal.totalKcal).toString()
                                            proteinStr = (obj["total_protein_g"]?.jsonPrimitive?.intOrNull ?: meal.totalProteinG).toString()
                                            carbsStr = (obj["total_carbs_g"]?.jsonPrimitive?.intOrNull ?: meal.totalCarbsG).toString()
                                            fatStr = (obj["total_fat_g"]?.jsonPrimitive?.intOrNull ?: meal.totalFatG).toString()
                                            val src = obj["source"]?.jsonPrimitive?.content
                                            if (src != null) selectedCategory = when (src) {
                                                "restaurant" -> MealCategory.RESTAURANT
                                                "fast_food" -> MealCategory.FAST_FOOD
                                                else -> MealCategory.HOME
                                            }
                                            val mt = obj["meal_type"]?.jsonPrimitive?.content
                                            if (mt != null) selectedMealType = when (mt) {
                                                "breakfast" -> MealType.BREAKFAST
                                                "brunch" -> MealType.BRUNCH
                                                "lunch" -> MealType.LUNCH
                                                "dinner" -> MealType.DINNER
                                                "snack" -> MealType.SNACK
                                                else -> selectedMealType
                                            }
                                            val coachNote = obj["coach_note"]?.jsonPrimitive?.content
                                            val itemsArr = obj["items"]?.jsonArray
                                            onSave(meal.copy(
                                                description = description,
                                                totalKcal = kcalStr.toIntOrNull() ?: meal.totalKcal,
                                                totalProteinG = proteinStr.toIntOrNull() ?: meal.totalProteinG,
                                                totalCarbsG = carbsStr.toIntOrNull() ?: meal.totalCarbsG,
                                                totalFatG = fatStr.toIntOrNull() ?: meal.totalFatG,
                                                category = selectedCategory,
                                                mealType = selectedMealType,
                                                coachNote = coachNote ?: meal.coachNote,
                                                itemsJson = itemsArr?.toString() ?: meal.itemsJson,
                                            ))
                                        } catch (e: Exception) {
                                            aiError = e.message ?: "Failed to parse AI response"
                                        }
                                    }.onFailure { e ->
                                        aiError = e.message ?: "AI request failed"
                                    }
                                    aiLoading = false
                                }
                            },
                            enabled = !aiLoading && aiPrompt.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(containerColor = Accent),
                        ) {
                            if (aiLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Surface, strokeWidth = 2.dp)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(if (aiLoading) stringResource(R.string.ai_edit_loading) else stringResource(R.string.ai_edit_send))
                        }
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    aiFocusRequester.requestFocus()
                    // Wait for layout to settle before scrolling
                    kotlinx.coroutines.delay(150)
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}
