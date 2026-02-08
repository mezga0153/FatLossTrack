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
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.mock.MockAiResponses
import com.fatlosstrack.ui.mock.MockAiResponses.AnalysisResult
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.delay

/**
 * Shows the mock AI analysis result after "processing" photos.
 * Includes an analyzing spinner, then reveals description + nutrition table.
 */
@Composable
fun AnalysisResultScreen(
    mode: CaptureMode,
    photoCount: Int,
    onDone: () -> Unit,
    onBack: () -> Unit,
) {
    var analyzing by remember { mutableStateOf(true) }
    val result = remember {
        when (mode) {
            CaptureMode.LogMeal -> MockAiResponses.logMealResults.random()
            CaptureMode.SuggestMeal -> MockAiResponses.suggestMealResults.random()
        }
    }

    // Fake analysis delay
    LaunchedEffect(Unit) {
        delay(2200)
        analyzing = false
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
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (mode == CaptureMode.LogMeal) "Meal Analysis" else "Meal Suggestion",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
            )
        }

        if (analyzing) {
            AnalyzingState(photoCount)
        } else {
            ResultContent(result = result, mode = mode, onDone = onDone)
        }
    }
}

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
                text = "Analyzing $photoCount photo${if (photoCount > 1) "s" else ""}…",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface.copy(alpha = alpha),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Identifying food items and estimating portions",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ResultContent(
    result: AnalysisResult,
    mode: CaptureMode,
    onDone: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
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
                            text = if (mode == CaptureMode.LogMeal) "What I see" else "Suggested meal",
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
            result.items.forEach { item ->
                NutritionCard(item)
            }

            // Total calories
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
                        text = "Total",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = OnSurface,
                    )
                    Text(
                        text = "${result.totalCalories} kcal",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = Primary,
                    )
                }
            }

            // AI note
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, Accent.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Coach says",
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

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (mode == CaptureMode.LogMeal) {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    ) {
                        Text("Log this meal", color = Color.Black)
                    }
                } else {
                    Button(
                        onClick = onDone,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Secondary),
                    ) {
                        Text("Sounds good!", color = Color.Black)
                    }
                }
                OutlinedButton(
                    onClick = onDone,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Discard", color = OnSurfaceVariant)
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun NutritionCard(item: MockAiResponses.MealItem) {
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
                Text("Nutrient", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Amount", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant, modifier = Modifier.width(72.dp))
            }

            // Rows
            item.nutrition.forEachIndexed { idx, row ->
                val isCalories = row.name == "Calories"
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
                            if (isCalories) it.copy(fontWeight = FontWeight.SemiBold) else it
                        },
                        color = if (isCalories) Primary else OnSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${row.amount} ${row.unit}",
                        style = MaterialTheme.typography.bodyMedium.let {
                            if (isCalories) it.copy(fontWeight = FontWeight.SemiBold) else it
                        },
                        color = if (isCalories) Primary else OnSurface,
                        modifier = Modifier.width(72.dp),
                    )
                }
            }
        }
    }
}
