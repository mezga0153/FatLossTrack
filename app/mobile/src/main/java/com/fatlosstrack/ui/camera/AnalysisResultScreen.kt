package com.fatlosstrack.ui.camera

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.ui.theme.*

/**
 * Pure UI renderer for meal analysis results (photo or text mode).
 * All business logic lives in [AnalysisResultStateHolder].
 */
@Composable
fun AnalysisResultScreen(
    state: AnalysisResultStateHolder,
    mode: CaptureMode,
    photoCount: Int,
    targetDate: java.time.LocalDate = java.time.LocalDate.now(),
    isTextMode: Boolean = false,
    onDone: () -> Unit,
    onLogged: () -> Unit,
    onBack: () -> Unit,
) {
    // Initialize state holder on first composition
    LaunchedEffect(Unit) {
        if (isTextMode) {
            state.startTextAnalysis()
        } else {
            state.startPhotoAnalysis(mode, targetDate)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface),
    ) {
        // ── Top bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                state.cleanup()
                onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = OnSurface,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isTextMode) stringResource(R.string.analysis_title_log) else if (mode == CaptureMode.LogMeal) stringResource(R.string.analysis_title_log) else stringResource(R.string.analysis_title_suggest),
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
            )
        }

        when {
            state.analyzing -> AnalyzingState(photoCount)
            state.errorMessage != null -> ErrorState(state.errorMessage!!, onBack = {
                state.cleanup()
                onBack()
            })
            state.result != null -> ResultContent(
                result = state.result!!,
                mode = if (isTextMode) CaptureMode.LogMeal else mode,
                showCorrection = true,
                onDone = {
                    state.cleanup()
                    onDone()
                },
                onLog = { analysisResult, overrideCategory, overrideMealType ->
                    state.logMeal(analysisResult, overrideCategory, overrideMealType) {
                        onLogged()
                    }
                },
                onCorrection = { correction -> state.runAnalysis(correction) },
            )
        }
    }
}

// ── UI states ──

@Composable
private fun AnalyzingState(photoCount: Int) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = Primary,
                modifier = Modifier.size(48.dp),
                strokeWidth = 3.dp,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(R.string.analysis_analyzing_photos, photoCount),
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface.copy(alpha = alpha),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.analysis_sending_to_ai),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onBack: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = Tertiary,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.analysis_failed),
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            OutlinedButton(onClick = onBack) {
                Text(stringResource(R.string.button_go_back), color = Primary)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResultContent(
    result: AnalysisResult,
    mode: CaptureMode,
    showCorrection: Boolean = true,
    showDateSelector: Boolean = false,
    effectiveDate: java.time.LocalDate = java.time.LocalDate.now(),
    onDateChanged: (java.time.LocalDate) -> Unit = {},
    onDone: () -> Unit,
    onLog: (AnalysisResult, MealCategory, MealType?) -> Unit,
    onCorrection: (String) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    var correctionText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(result.source) }
    var selectedMealType by remember { mutableStateOf(result.mealType) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(visible = visible, enter = fadeIn(tween(500))) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Description
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Restaurant,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (mode == CaptureMode.LogMeal) stringResource(R.string.analysis_what_i_see) else stringResource(R.string.analysis_suggested_meal),
                            style = MaterialTheme.typography.titleMedium,
                            color = Primary,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = result.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface,
                    )
                }
            }

            // Items + nutrition
            if (result.items.isNotEmpty()) {
                result.items.forEach { item ->
                    NutritionCard(item)
                }

                // Total calories & protein
                if (result.totalCalories > 0) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.label_total),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = OnSurface,
                            )
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = stringResource(R.string.format_kcal, result.totalCalories),
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Secondary,
                                )
                                if (result.totalProteinG > 0) {
                                    Text(
                                        text = stringResource(R.string.format_protein_full, result.totalProteinG),
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Primary,
                                    )
                                }
                                if (result.totalCarbsG > 0) {
                                    Text(
                                        "${result.totalCarbsG}g carbs",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Tertiary,
                                    )
                                }
                                if (result.totalFatG > 0) {
                                    Text(
                                        "${result.totalFatG}g fat",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = Accent,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AI note
            if (result.aiNote.isNotBlank()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.border(1.dp, Accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.label_coach_says),
                            style = MaterialTheme.typography.labelLarge,
                            color = Accent,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = result.aiNote,
                            style = MaterialTheme.typography.bodyLarge,
                            color = OnSurface,
                        )
                    }
                }
            }

            // ── Source selector ──
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.section_source), style = MaterialTheme.typography.labelLarge, color = OnSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data class CatOption(val cat: MealCategory, val label: String)
                        listOf(
                            CatOption(MealCategory.HOME, stringResource(R.string.category_home)),
                            CatOption(MealCategory.RESTAURANT, stringResource(R.string.category_restaurant)),
                            CatOption(MealCategory.FAST_FOOD, stringResource(R.string.category_fast_food)),
                        ).forEach { (cat, label) ->
                            FilterChip(
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat },
                                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.15f),
                                    selectedLabelColor = Primary,
                                ),
                            )
                        }
                    }
                }
            }

            // ── Meal type selector ──
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.section_meal_type), style = MaterialTheme.typography.labelLarge, color = OnSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    @Suppress("ktlint")
                    val mealTypes = listOf(
                        MealType.BREAKFAST to stringResource(R.string.meal_type_breakfast),
                        MealType.BRUNCH to stringResource(R.string.meal_type_brunch),
                        MealType.LUNCH to stringResource(R.string.meal_type_lunch),
                        MealType.DINNER to stringResource(R.string.meal_type_dinner),
                        MealType.SNACK to stringResource(R.string.meal_type_snack),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        mealTypes.forEach { (type, label) ->
                            FilterChip(
                                selected = selectedMealType == type,
                                onClick = { selectedMealType = if (selectedMealType == type) null else type },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Accent.copy(alpha = 0.15f),
                                    selectedLabelColor = Accent,
                                ),
                            )
                        }
                    }
                }
            }

            // ── Date selector ──
            if (showDateSelector) {
                var showDatePicker by remember { mutableStateOf(false) }
                val today = java.time.LocalDate.now()
                val yesterday = today.minusDays(1)
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.section_log_date), style = MaterialTheme.typography.labelLarge, color = OnSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = effectiveDate == today,
                                onClick = { onDateChanged(today) },
                                label = { Text(stringResource(R.string.day_today)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.15f),
                                    selectedLabelColor = Primary,
                                ),
                            )
                            FilterChip(
                                selected = effectiveDate == yesterday,
                                onClick = { onDateChanged(yesterday) },
                                label = { Text(stringResource(R.string.day_yesterday)) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Primary.copy(alpha = 0.15f),
                                    selectedLabelColor = Primary,
                                ),
                            )
                            FilterChip(
                                selected = effectiveDate != today && effectiveDate != yesterday,
                                onClick = { showDatePicker = true },
                                label = {
                                    Text(
                                        if (effectiveDate != today && effectiveDate != yesterday)
                                            effectiveDate.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
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
                    }
                }
                if (showDatePicker) {
                    val datePickerState = rememberDatePickerState(
                        initialSelectedDateMillis = effectiveDate
                            .atStartOfDay(java.time.ZoneId.of("UTC"))
                            .toInstant()
                            .toEpochMilli(),
                    )
                    DatePickerDialog(
                        onDismissRequest = { showDatePicker = false },
                        confirmButton = {
                            TextButton(onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    onDateChanged(
                                        java.time.Instant.ofEpochMilli(millis)
                                            .atZone(java.time.ZoneId.of("UTC"))
                                            .toLocalDate(),
                                    )
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
            }

            // ── Correction input ──
            if (showCorrection) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, OnSurfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.analysis_something_wrong),
                        style = MaterialTheme.typography.labelLarge,
                        color = OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.analysis_correction_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceVariant.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = correctionText,
                            onValueChange = { correctionText = it },
                            placeholder = {
                                Text(
                                    stringResource(R.string.analysis_correction_placeholder),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant.copy(alpha = 0.5f),
                                )
                            },
                            modifier = Modifier.weight(1f),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Primary,
                                unfocusedBorderColor = OnSurfaceVariant.copy(alpha = 0.3f),
                                cursorColor = Primary,
                            ),
                        )
                        IconButton(
                            onClick = {
                                if (correctionText.isNotBlank()) {
                                    onCorrection(correctionText.trim())
                                    correctionText = ""
                                }
                            },
                            enabled = correctionText.isNotBlank(),
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (correctionText.isNotBlank()) Primary.copy(alpha = 0.15f)
                                    else Color.Transparent,
                                ),
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                stringResource(R.string.cd_re_analyze),
                                tint = if (correctionText.isNotBlank()) Primary else OnSurfaceVariant.copy(alpha = 0.3f),
                            )
                        }
                    }
                }
            }
            } // end if (showCorrection)

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (mode == CaptureMode.LogMeal) {
                    Button(
                        onClick = { onLog(result, selectedCategory, selectedMealType) },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text(stringResource(R.string.button_log_meal), color = MaterialTheme.colorScheme.onPrimary)
                    }
                } else {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    ) {
                        Text(stringResource(R.string.button_sounds_good), color = Surface)
                    }
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.button_discard), color = OnSurfaceVariant)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun NutritionCard(item: MealItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OnSurface,
            )
            Text(
                text = item.portion,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            // Nutrition table header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                    .background(SurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text(stringResource(R.string.table_nutrient), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.table_amount), style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(72.dp))
            }

            // Rows
            item.nutrition.forEachIndexed { idx, row ->
                val rowColor = when (row.name) {
                    "Calories" -> Secondary
                    "Protein" -> Primary
                    "Carbs" -> Tertiary
                    "Fat" -> Accent
                    else -> OnSurface
                }
                val isBold = row.name == "Calories"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .let {
                            if (idx == item.nutrition.lastIndex)
                                it.clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                            else it
                        }
                        .background(if (idx % 2 == 0) Color.Transparent else SurfaceVariant.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = row.name,
                        style = MaterialTheme.typography.bodyMedium.let {
                            if (isBold) it.copy(fontWeight = FontWeight.SemiBold) else it
                        },
                        color = rowColor,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${row.amount} ${row.unit}",
                        style = MaterialTheme.typography.bodyMedium.let {
                            if (isBold) it.copy(fontWeight = FontWeight.SemiBold) else it
                        },
                        color = rowColor,
                        modifier = Modifier.width(72.dp),
                    )
                }
            }
        }
    }
}
