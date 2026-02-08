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
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Set Goal screen — edit weight target, weekly rate, and AI guidance notes.
 * Persists all values to DataStore via PreferencesManager.
 */
@Composable
fun SetGoalScreen(
    onBack: () -> Unit,
    preferencesManager: PreferencesManager,
) {
    val scope = rememberCoroutineScope()

    // Load saved values
    val savedCurrentWeight by preferencesManager.currentWeight.collectAsState(initial = null)
    val savedGoalWeight by preferencesManager.goalWeight.collectAsState(initial = null)
    val savedRate by preferencesManager.weeklyRate.collectAsState(initial = 0.5f)
    val savedGuidance by preferencesManager.aiGuidance.collectAsState(initial = "")

    var currentWeight by remember { mutableStateOf("") }
    var goalWeight by remember { mutableStateOf("") }
    var weeklyRate by remember { mutableStateOf("") }
    var aiGuidance by remember { mutableStateOf("") }
    var initialized by remember { mutableStateOf(false) }

    // Seed fields once from saved values
    LaunchedEffect(savedCurrentWeight, savedGoalWeight, savedRate, savedGuidance) {
        if (!initialized) {
            currentWeight = savedCurrentWeight?.let { "%.1f".format(it) } ?: ""
            goalWeight = savedGoalWeight?.let { "%.1f".format(it) } ?: ""
            weeklyRate = "%.2f".format(savedRate).trimEnd('0').trimEnd('.')
            aiGuidance = savedGuidance
            initialized = true
        }
    }

    // Derived deficit
    val deficit = remember(weeklyRate) {
        val rate = weeklyRate.toFloatOrNull() ?: 0.5f
        // 1 kg fat ≈ 7700 kcal → daily = rate * 7700 / 7 ≈ rate * 1100
        (rate * 1100).toInt()
    }

    // Estimated weeks to goal
    val weeksToGoal = remember(currentWeight, goalWeight, weeklyRate) {
        val cw = currentWeight.toFloatOrNull() ?: 84f
        val gw = goalWeight.toFloatOrNull() ?: 80f
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
                    contentDescription = "Back",
                    tint = OnSurface,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                text = "Set Goal",
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

            // ── Weight section ──
            GoalSection(icon = Icons.Default.Scale, title = "Weight") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GoalTextField(
                        value = currentWeight,
                        onValueChange = { currentWeight = it },
                        label = "Current (kg)",
                        modifier = Modifier.weight(1f),
                    )
                    GoalTextField(
                        value = goalWeight,
                        onValueChange = { goalWeight = it },
                        label = "Goal (kg)",
                        modifier = Modifier.weight(1f),
                    )
                }

                if (weeksToGoal != null && weeksToGoal > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "That's ${currentWeight.toFloatOrNull()?.minus(goalWeight.toFloatOrNull() ?: 0f)?.let { "%.1f".format(it) } ?: "?"} kg to lose",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant,
                    )
                }
            }

            // ── Rate section ──
            GoalSection(icon = Icons.Default.Speed, title = "Weekly loss rate") {
                GoalTextField(
                    value = weeklyRate,
                    onValueChange = { weeklyRate = it },
                    label = "kg per week",
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(8.dp))

                // Preset chips
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    RateChip("0.25", "Gentle", weeklyRate == "0.25") { weeklyRate = "0.25" }
                    RateChip("0.5", "Standard", weeklyRate == "0.5") { weeklyRate = "0.5" }
                    RateChip("0.75", "Aggressive", weeklyRate == "0.75") { weeklyRate = "0.75" }
                    RateChip("1.0", "Max", weeklyRate == "1.0") { weeklyRate = "1.0" }
                }

                Spacer(Modifier.height(8.dp))

                // Warning for aggressive rates
                val rate = weeklyRate.toFloatOrNull() ?: 0.5f
                if (rate >= 1.0f) {
                    Text(
                        text = "⚠️ Losing 1+ kg/week is aggressive. Risk of muscle loss and sustainability issues.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Tertiary,
                    )
                }
            }

            // ── Calculated deficit ──
            GoalSection(icon = Icons.Default.Flag, title = "Daily target") {
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
                            text = "kcal deficit / day",
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
                                text = "weeks to goal",
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── AI guidance freeform ──
            GoalSection(icon = Icons.Default.Psychology, title = "AI Coach guidance") {
                Text(
                    text = "Tell the AI about your dietary restrictions, preferences, exercise habits, or anything else that should influence its suggestions.",
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
                            "e.g.\n• Lactose intolerant\n• Vegetarian on weekdays\n• Gym 3×/week (push/pull/legs)\n• No time to cook on Mondays",
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
                    text = "Quick add:",
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    val suggestions = listOf("No gluten", "Vegetarian", "Keto-friendly", "Low sodium")
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
                            currentWeight = currentWeight.toFloatOrNull() ?: 0f,
                            goalWeight = goalWeight.toFloatOrNull() ?: 0f,
                            weeklyRate = weeklyRate.toFloatOrNull() ?: 0.5f,
                            aiGuidance = aiGuidance.trim(),
                        )
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
                    "Save changes",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.Black,
                )
            }

            Spacer(Modifier.height(32.dp))
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
