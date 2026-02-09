package com.fatlosstrack.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Set Goal screen — edit weight target, weekly rate, and AI guidance notes.
 * Persists all values to DataStore via PreferencesManager.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetGoalScreen(
    onBack: () -> Unit,
    preferencesManager: PreferencesManager,
) {
    val scope = rememberCoroutineScope()

    // Load saved values
    val savedStartWeight by preferencesManager.startWeight.collectAsState(initial = null)
    val savedGoalWeight by preferencesManager.goalWeight.collectAsState(initial = null)
    val savedRate by preferencesManager.weeklyRate.collectAsState(initial = null)
    val savedGuidance by preferencesManager.aiGuidance.collectAsState(initial = null)
    val savedHeight by preferencesManager.heightCm.collectAsState(initial = null)
    val savedStartDate by preferencesManager.startDate.collectAsState(initial = null)

    var startWeight by remember { mutableStateOf("") }
    var goalWeight by remember { mutableStateOf("") }
    var weeklyRate by remember { mutableStateOf("") }
    var aiGuidance by remember { mutableStateOf("") }
    var heightCm by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(LocalDate.now()) }
    var initialized by remember { mutableStateOf(false) }

    // Seed fields once from saved values — wait until rate is loaded (non-null) to avoid race
    LaunchedEffect(savedStartWeight, savedGoalWeight, savedRate, savedGuidance, savedHeight, savedStartDate) {
        if (!initialized && savedRate != null) {
            startWeight = savedStartWeight?.let { "%.1f".format(it) } ?: ""
            goalWeight = savedGoalWeight?.let { "%.1f".format(it) } ?: ""
            weeklyRate = "%.2f".format(savedRate).trimEnd('0').trimEnd('.')
            aiGuidance = savedGuidance ?: ""
            heightCm = savedHeight?.toString() ?: ""
            startDate = savedStartDate?.let {
                try { LocalDate.parse(it) } catch (_: Exception) { LocalDate.now() }
            } ?: LocalDate.now()
            initialized = true
        }
    }

    // Date picker state
    var showDatePicker by remember { mutableStateOf(false) }

    // Derived deficit
    val deficit = remember(weeklyRate) {
        val rate = weeklyRate.toFloatOrNull() ?: 0.5f
        // 1 kg fat ≈ 7700 kcal → daily = rate * 7700 / 7 ≈ rate * 1100
        (rate * 1100).toInt()
    }

    // Estimated weeks to goal
    val weeksToGoal = remember(startWeight, goalWeight, weeklyRate) {
        val cw = startWeight.toFloatOrNull() ?: 0f
        val gw = goalWeight.toFloatOrNull() ?: 0f
        val rate = weeklyRate.toFloatOrNull() ?: 0.5f
        if (rate > 0 && cw > gw) ((cw - gw) / rate).toInt() else null
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
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = OnSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.set_goal_title),
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(4.dp))

            // ── Height section ──
            GoalSection(icon = Icons.Default.Height, title = stringResource(R.string.goal_section_height)) {
                GoalTextField(
                    value = heightCm,
                    onValueChange = { heightCm = it },
                    label = stringResource(R.string.field_cm),
                    modifier = Modifier.fillMaxWidth(0.5f),
                )
            }

            // ── Weight section ──
            GoalSection(icon = Icons.Default.Scale, title = stringResource(R.string.goal_section_weight)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GoalTextField(
                        value = startWeight,
                        onValueChange = { startWeight = it },
                        label = stringResource(R.string.field_starting_kg),
                        modifier = Modifier.weight(1f),
                    )
                    GoalTextField(
                        value = goalWeight,
                        onValueChange = { goalWeight = it },
                        label = stringResource(R.string.field_goal_kg),
                        modifier = Modifier.weight(1f),
                    )
                }

                if (weeksToGoal != null && weeksToGoal > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.goal_kg_to_lose, startWeight.toFloatOrNull()?.minus(goalWeight.toFloatOrNull() ?: 0f) ?: 0f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                    )
                }
            }

            // ── Start date section ──
            GoalSection(icon = Icons.Default.CalendarMonth, title = stringResource(R.string.goal_section_start_date)) {
                Text(
                    text = stringResource(R.string.goal_start_date_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text(
                        startDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")),
                        color = Primary,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            // ── Rate section ──
            GoalSection(icon = Icons.Default.Speed, title = stringResource(R.string.goal_section_weekly_rate)) {
                GoalTextField(
                    value = weeklyRate,
                    onValueChange = { weeklyRate = it },
                    label = stringResource(R.string.field_kg_per_week),
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // Preset chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RateChip("0.25", stringResource(R.string.rate_gentle), weeklyRate == "0.25") { weeklyRate = "0.25" }
                    RateChip("0.5", stringResource(R.string.rate_standard), weeklyRate == "0.5") { weeklyRate = "0.5" }
                    RateChip("0.75", stringResource(R.string.rate_aggressive), weeklyRate == "0.75") { weeklyRate = "0.75" }
                    RateChip("1.0", stringResource(R.string.rate_max), weeklyRate == "1.0") { weeklyRate = "1.0" }
                }

                Spacer(Modifier.height(8.dp))

                // Warning for aggressive rates
                val rate = weeklyRate.toFloatOrNull() ?: 0.5f
                if (rate >= 1.0f) {
                    Text(
                        text = stringResource(R.string.rate_warning_aggressive),
                        style = MaterialTheme.typography.bodySmall,
                        color = Tertiary,
                    )
                }
            }

            // ── Calculated deficit ──
            GoalSection(icon = Icons.Default.Flag, title = stringResource(R.string.goal_section_daily_target)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text(
                            text = "~$deficit",
                            style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                            color = Primary,
                        )
                        Text(
                            text = stringResource(R.string.goal_kcal_deficit_day),
                            style = MaterialTheme.typography.bodyMedium,
                            color = OnSurfaceVariant,
                        )
                    }

                    if (weeksToGoal != null && weeksToGoal > 0) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "~$weeksToGoal",
                                style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                                color = Secondary,
                            )
                            Text(
                                text = stringResource(R.string.goal_weeks_to_goal),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── AI guidance freeform ──
            GoalSection(icon = Icons.Default.Psychology, title = stringResource(R.string.goal_section_ai_guidance)) {
                Text(
                    text = stringResource(R.string.goal_ai_guidance_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceVariant,
                )

                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = aiGuidance,
                    onValueChange = { aiGuidance = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp),
                    placeholder = {
                        Text(
                            stringResource(R.string.goal_ai_guidance_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = SurfaceVariant,
                        unfocusedContainerColor = SurfaceVariant,
                        focusedBorderColor = Accent.copy(alpha = 0.5f),
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = Primary,
                    ),
                    shape = RoundedCornerShape(12.dp),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurface),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                )

                Spacer(Modifier.height(4.dp))

                // Suggestion chips
                Text(
                    text = stringResource(R.string.goal_quick_add),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val suggestions = listOf(
                        stringResource(R.string.chip_no_gluten),
                        stringResource(R.string.chip_vegetarian),
                        stringResource(R.string.chip_keto_friendly),
                        stringResource(R.string.chip_low_sodium),
                    )
                    suggestions.forEach { tag ->
                        val alreadyAdded = aiGuidance.contains(tag, ignoreCase = true)
                        SuggestionChip(
                            onClick = {
                                if (!alreadyAdded) {
                                    aiGuidance = if (aiGuidance.isBlank()) tag
                                    else "$aiGuidance\n$tag"
                                }
                            },
                            label = {
                                Text(
                                    tag,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (alreadyAdded) OnSurfaceVariant else Primary,
                                )
                            },
                            enabled = !alreadyAdded,
                        )
                    }
                }
            }

            // ── Save button ──
            Button(
                onClick = {
                    scope.launch {
                        preferencesManager.setGoal(
                            startWeight = startWeight.toFloatOrNull() ?: 0f,
                            goalWeight = goalWeight.toFloatOrNull() ?: 0f,
                            weeklyRate = weeklyRate.toFloatOrNull() ?: 0.5f,
                            aiGuidance = aiGuidance.trim(),
                            heightCm = heightCm.toIntOrNull(),
                            startDate = startDate.toString(),
                        )
                        AppLogger.instance?.user("Goal saved: start=${startWeight}kg, goal=${goalWeight}kg, rate=${weeklyRate}kg/wk, height=${heightCm}cm, startDate=$startDate")
                    }
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    stringResource(R.string.button_save_changes),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }

    // ── Date picker dialog ──
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.toEpochDay() * 86_400_000L,
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        startDate = LocalDate.ofEpochDay(millis / 86_400_000L)
                    }
                    showDatePicker = false
                }) { Text(stringResource(R.string.button_ok), color = Primary) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(stringResource(R.string.button_cancel), color = OnSurfaceVariant)
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ── Building blocks ──

@Composable
private fun GoalSection(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 12.dp),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OnSurface,
            )
        }
        content()
    }
}

@Composable
private fun GoalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Next,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = SurfaceVariant,
            unfocusedContainerColor = SurfaceVariant,
            focusedBorderColor = Primary.copy(alpha = 0.5f),
            unfocusedBorderColor = Color.Transparent,
            cursorColor = Primary,
        ),
        shape = RoundedCornerShape(10.dp),
        textStyle = MaterialTheme.typography.bodyLarge.copy(
            fontWeight = FontWeight.Medium,
            color = OnSurface,
        ),
    )
}

@Composable
private fun RateChip(
    value: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Primary.copy(alpha = 0.18f) else SurfaceVariant)
            .then(
                Modifier.clickable(onClick = onClick)
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = if (selected) Primary else OnSurface,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Primary.copy(alpha = 0.7f) else OnSurfaceVariant,
        )
    }
}
