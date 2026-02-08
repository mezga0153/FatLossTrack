package com.fatlosstrack.ui.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.data.local.CapturedPhotoStore
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PendingTextMealStore
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

// ── Data models ──

data class NutritionRow(
    val name: String,
    val amount: String,
    val unit: String,
)

data class MealItem(
    val name: String,
    val portion: String,
    val nutrition: List<NutritionRow>,
)

data class AnalysisResult(
    val description: String,
    val items: List<MealItem>,
    val totalCalories: Int,
    val aiNote: String,
    val source: MealCategory = MealCategory.HOME,
    val mealType: MealType? = null,
)

/**
 * Calls OpenAI vision API with captured photos. Parses structured JSON response
 * into meal items with nutrition data. Supports user corrections for re-analysis.
 */
@Composable
fun AnalysisResultScreen(
    mode: CaptureMode,
    photoCount: Int,
    openAiService: OpenAiService,
    mealDao: MealDao,
    targetDate: java.time.LocalDate = java.time.LocalDate.now(),
    isTextMode: Boolean = false,
    onDone: () -> Unit,
    onLogged: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var analyzing by remember { mutableStateOf(!isTextMode) }
    var result by remember { mutableStateOf<AnalysisResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var effectiveDate by remember { mutableStateOf(targetDate) }

    // Keep bitmaps in memory for re-analysis with corrections
    val bitmaps = remember { mutableStateListOf<Bitmap>() }

    // For text mode: load from PendingTextMealStore
    LaunchedEffect(isTextMode) {
        if (isTextMode) {
            val pending = PendingTextMealStore.consume()
            if (pending != null) {
                val (raw, date) = pending
                effectiveDate = date
                try {
                    result = parseAnalysisJson(raw)
                } catch (e: Exception) {
                    Log.e("Analysis", "Text meal parse failed: $raw", e)
                    errorMessage = "Failed to parse meal data"
                }
                PendingTextMealStore.clear()
            } else {
                errorMessage = "No meal data available"
            }
        }
    }

    // Runs analysis (initial or with correction)
    fun runAnalysis(correction: String? = null) {
        analyzing = true
        errorMessage = null
        scope.launch {
            try {
                // Load bitmaps on first run
                if (bitmaps.isEmpty()) {
                    val photoUris = CapturedPhotoStore.consume()
                    if (photoUris.isEmpty()) {
                        errorMessage = "No photos to analyze."
                        analyzing = false
                        return@launch
                    }
                    val loaded = withContext(Dispatchers.IO) {
                        photoUris.mapNotNull { uri ->
                            try {
                                context.contentResolver.openInputStream(uri)?.use { stream ->
                                    BitmapFactory.decodeStream(stream)
                                }
                            } catch (e: Exception) {
                                Log.e("Analysis", "Failed to load photo: $uri", e)
                                null
                            }
                        }
                    }
                    if (loaded.isEmpty()) {
                        errorMessage = "Could not load photos."
                        analyzing = false
                        return@launch
                    }
                    bitmaps.addAll(loaded)
                }

                val modeStr = if (mode == CaptureMode.SuggestMeal) "suggest" else "log"
                val apiResult = openAiService.analyzeMeal(bitmaps.toList(), modeStr, correction)

                apiResult.fold(
                    onSuccess = { raw ->
                        try {
                            result = parseAnalysisJson(raw)
                        } catch (e: Exception) {
                            Log.e("Analysis", "JSON parse failed, raw: $raw", e)
                            result = AnalysisResult(
                                description = raw,
                                items = emptyList(),
                                totalCalories = 0,
                                aiNote = "",
                            )
                        }
                        analyzing = false
                    },
                    onFailure = { e ->
                        errorMessage = e.message ?: "Analysis failed"
                        analyzing = false
                    },
                )
            } catch (e: Exception) {
                errorMessage = e.message ?: "Unexpected error"
                analyzing = false
            }
        }
    }

    // Initial analysis on launch (photo mode only)
    LaunchedEffect(Unit) { if (!isTextMode) runAnalysis() }

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
                CapturedPhotoStore.clear()
                PendingTextMealStore.clear()
                onBack()
            }) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnSurface,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (isTextMode) "Meal Analysis" else if (mode == CaptureMode.LogMeal) "Meal Analysis" else "Meal Suggestion",
                style = MaterialTheme.typography.titleLarge,
                color = OnSurface,
            )
        }

        when {
            analyzing -> AnalyzingState(photoCount)
            errorMessage != null -> ErrorState(errorMessage!!, onBack = {
                CapturedPhotoStore.clear()
                PendingTextMealStore.clear()
                onBack()
            })
            result != null -> ResultContent(
                result = result!!,
                mode = if (isTextMode) CaptureMode.LogMeal else mode,
                showCorrection = !isTextMode,
                onDone = {
                    CapturedPhotoStore.clear()
                    PendingTextMealStore.clear()
                    onDone()
                },
                onLog = { analysisResult, overrideCategory, overrideMealType ->
                    scope.launch {
                        val itemsJson = kotlinx.serialization.json.buildJsonArray {
                            analysisResult.items.forEach { item ->
                                add(kotlinx.serialization.json.buildJsonObject {
                                    put("name", item.name)
                                    put("portion", item.portion)
                                    item.nutrition.forEach { n ->
                                        when (n.name) {
                                            "Calories" -> put("calories", n.amount.toIntOrNull() ?: 0)
                                            "Protein" -> put("protein_g", n.amount.toIntOrNull() ?: 0)
                                            "Fat" -> put("fat_g", n.amount.toIntOrNull() ?: 0)
                                            "Carbs" -> put("carbs_g", n.amount.toIntOrNull() ?: 0)
                                        }
                                    }
                                })
                            }
                        }.toString()
                        mealDao.insert(
                            MealEntry(
                                date = effectiveDate,
                                description = analysisResult.description,
                                itemsJson = itemsJson,
                                totalKcal = analysisResult.totalCalories,
                                coachNote = analysisResult.aiNote,
                                category = overrideCategory,
                                mealType = overrideMealType,
                            )
                        )
                        AppLogger.instance?.meal("Logged via AI: ${analysisResult.description.take(50)} — ${analysisResult.totalCalories} kcal, cat=$overrideCategory, type=$overrideMealType, date=$effectiveDate")
                        CapturedPhotoStore.clear()
                        PendingTextMealStore.clear()
                        onLogged()
                    }
                },
                onCorrection = { correction -> runAnalysis(correction) },
            )
        }
    }
}

// ── JSON parsing ──

private fun parseAnalysisJson(raw: String): AnalysisResult {
    // Strip markdown code fences if present
    val cleaned = raw
        .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^```\\s*", RegexOption.MULTILINE), "")
        .trim()

    val json = Json.parseToJsonElement(cleaned).jsonObject

    val description = json["description"]?.jsonPrimitive?.content ?: ""
    val totalCalories = json["total_calories"]?.jsonPrimitive?.int ?: 0
    val aiNote = json["coach_note"]?.jsonPrimitive?.content ?: ""
    val sourceStr = json["source"]?.jsonPrimitive?.content ?: "home"
    val source = when (sourceStr.lowercase()) {
        "restaurant" -> MealCategory.RESTAURANT
        "fast_food", "fastfood", "fast food" -> MealCategory.FAST_FOOD
        else -> MealCategory.HOME
    }

    val mealTypeStr = json["meal_type"]?.jsonPrimitive?.content ?: ""
    val mealType = when (mealTypeStr.lowercase()) {
        "breakfast" -> MealType.BREAKFAST
        "brunch" -> MealType.BRUNCH
        "lunch" -> MealType.LUNCH
        "dinner" -> MealType.DINNER
        "snack" -> MealType.SNACK
        else -> null
    }

    val items = json["items"]?.jsonArray?.map { itemEl ->
        val item = itemEl.jsonObject
        val name = item["name"]?.jsonPrimitive?.content ?: "Unknown"
        val portion = item["portion"]?.jsonPrimitive?.content ?: ""
        val calories = item["calories"]?.jsonPrimitive?.int ?: 0
        val protein = item["protein_g"]?.jsonPrimitive?.intOrNull ?: item["protein_g"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0
        val fat = item["fat_g"]?.jsonPrimitive?.intOrNull ?: item["fat_g"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0
        val carbs = item["carbs_g"]?.jsonPrimitive?.intOrNull ?: item["carbs_g"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0

        MealItem(
            name = name,
            portion = portion,
            nutrition = listOf(
                NutritionRow("Calories", "$calories", "kcal"),
                NutritionRow("Protein", "$protein", "g"),
                NutritionRow("Fat", "$fat", "g"),
                NutritionRow("Carbs", "$carbs", "g"),
            ),
        )
    } ?: emptyList()

    return AnalysisResult(
        description = description,
        items = items,
        totalCalories = totalCalories,
        aiNote = aiNote,
        source = source,
        mealType = mealType,
    )
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
                text = "Analyzing $photoCount photo${if (photoCount > 1) "s" else ""}…",
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface.copy(alpha = alpha),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Sending to AI for identification",
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
                text = "Analysis failed",
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
                Text("Go back", color = Primary)
            }
        }
    }
}

@Composable
private fun ResultContent(
    result: AnalysisResult,
    mode: CaptureMode,
    showCorrection: Boolean = true,
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
            if (result.items.isNotEmpty()) {
                result.items.forEach { item ->
                    NutritionCard(item)
                }

                // Total calories
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
            }

            // ── Source selector ──
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Source", style = MaterialTheme.typography.labelLarge, color = OnSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        data class CatOption(val cat: MealCategory, val label: String)
                        listOf(
                            CatOption(MealCategory.HOME, "Home"),
                            CatOption(MealCategory.RESTAURANT, "Restaurant"),
                            CatOption(MealCategory.FAST_FOOD, "Fast food"),
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
                    Text("Meal", style = MaterialTheme.typography.labelLarge, color = OnSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    @Suppress("ktlint")
                    val mealTypes = listOf(
                        MealType.BREAKFAST to "Breakfast",
                        MealType.BRUNCH to "Brunch",
                        MealType.LUNCH to "Lunch",
                        MealType.DINNER to "Dinner",
                        MealType.SNACK to "Snack",
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

            // ── Correction input ──
            if (showCorrection) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.border(1.dp, OnSurfaceVariant.copy(alpha = 0.15f), RoundedCornerShape(12.dp)),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = "Something wrong?",
                        style = MaterialTheme.typography.labelLarge,
                        color = OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Tell the AI what to fix and it will re-analyze",
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
                                    "e.g. \"That's turkey, not chicken\"",
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
                                "Re-analyze",
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
private fun NutritionCard(item: MealItem) {
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
