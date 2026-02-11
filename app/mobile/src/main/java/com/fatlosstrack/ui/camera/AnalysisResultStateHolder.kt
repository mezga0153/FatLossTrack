package com.fatlosstrack.ui.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.CapturedPhotoStore
import com.fatlosstrack.data.local.PendingTextMealStore
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.LocalDate
import javax.inject.Inject

// ── Data models ──────────────────────────────────────────────────────────────

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
    val totalProteinG: Int = 0,
    val totalCarbsG: Int = 0,
    val totalFatG: Int = 0,
    val aiNote: String,
    val source: MealCategory = MealCategory.HOME,
    val mealType: MealType? = null,
)

// ── State holder ─────────────────────────────────────────────────────────────

/**
 * Owns all mutable state and business logic for [AnalysisResultScreen].
 * Handles photo bitmap loading, OpenAI vision analysis, text meal parsing,
 * correction re-analysis, and meal logging.
 */
@Stable
class AnalysisResultStateHolder @Inject constructor(
    private val openAiService: OpenAiService,
    private val mealDao: MealDao,
    private val daySummaryGenerator: DaySummaryGenerator,
    @ApplicationContext private val context: Context,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    var analyzing: Boolean by mutableStateOf(false)
        private set
    var result: AnalysisResult? by mutableStateOf(null)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var effectiveDate: LocalDate by mutableStateOf(LocalDate.now())
        private set

    private val bitmaps = mutableListOf<Bitmap>()
    private var currentMode: CaptureMode = CaptureMode.LogMeal

    /** Initialize for photo analysis. Resets state and starts analysis. */
    fun startPhotoAnalysis(mode: CaptureMode, targetDate: LocalDate) {
        reset()
        currentMode = mode
        effectiveDate = targetDate
        runAnalysis()
    }

    /** Initialize for text meal mode. Loads from [PendingTextMealStore]. */
    fun startTextAnalysis() {
        reset()
        currentMode = CaptureMode.LogMeal
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

    /** Run or re-run photo analysis, optionally with a user correction. */
    fun runAnalysis(correction: String? = null) {
        analyzing = true
        errorMessage = null
        appScope.launch {
            try {
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

                val modeStr = if (currentMode == CaptureMode.SuggestMeal) "suggest" else "log"
                AppLogger.instance?.ai(
                    "Image analysis: mode=$modeStr, photos=${bitmaps.size}" +
                        if (correction != null) ", correction" else "",
                )
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

    /** Log the analyzed meal to Room and trigger day summary generation. */
    fun logMeal(
        analysisResult: AnalysisResult,
        category: MealCategory,
        mealType: MealType?,
        onComplete: () -> Unit,
    ) {
        appScope.launch {
            val itemsJson = buildJsonArray {
                analysisResult.items.forEach { item ->
                    add(buildJsonObject {
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
                    totalProteinG = analysisResult.totalProteinG,
                    totalCarbsG = analysisResult.totalCarbsG,
                    totalFatG = analysisResult.totalFatG,
                    coachNote = analysisResult.aiNote,
                    category = category,
                    mealType = mealType,
                ),
            )
            AppLogger.instance?.meal(
                "Logged via AI: ${analysisResult.description.take(50)} — " +
                    "${analysisResult.totalCalories} kcal, cat=$category, type=$mealType, date=$effectiveDate",
            )
            daySummaryGenerator.launchForDate(effectiveDate, "AnalysisResult:cameraMealLogged")
            cleanup()
            onComplete()
        }
    }

    /** Clear photo/text stores and reset all state. */
    fun cleanup() {
        CapturedPhotoStore.clear()
        PendingTextMealStore.clear()
        reset()
    }

    private fun reset() {
        analyzing = false
        result = null
        errorMessage = null
        bitmaps.clear()
    }
}

// ── JSON parsing ─────────────────────────────────────────────────────────────

internal fun parseAnalysisJson(raw: String): AnalysisResult {
    val cleaned = raw
        .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^```\\s*", RegexOption.MULTILINE), "")
        .trim()

    val json = Json.parseToJsonElement(cleaned).jsonObject

    val description = json["description"]?.jsonPrimitive?.content ?: ""
    val totalCalories = json["total_calories"]?.jsonPrimitive?.int ?: 0
    val totalProteinFromJson = json["total_protein_g"]?.jsonPrimitive?.intOrNull
    val totalCarbsFromJson = json["total_carbs_g"]?.jsonPrimitive?.intOrNull
    val totalFatFromJson = json["total_fat_g"]?.jsonPrimitive?.intOrNull
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
        val protein = item["protein_g"]?.jsonPrimitive?.intOrNull
            ?: item["protein_g"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0
        val fat = item["fat_g"]?.jsonPrimitive?.intOrNull
            ?: item["fat_g"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0
        val carbs = item["carbs_g"]?.jsonPrimitive?.intOrNull
            ?: item["carbs_g"]?.jsonPrimitive?.floatOrNull?.toInt() ?: 0

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

    val totalProteinG = totalProteinFromJson
        ?: items.sumOf { item ->
            item.nutrition.find { it.name == "Protein" }?.amount?.toIntOrNull() ?: 0
        }
    val totalCarbsG = totalCarbsFromJson
        ?: items.sumOf { item ->
            item.nutrition.find { it.name == "Carbs" }?.amount?.toIntOrNull() ?: 0
        }
    val totalFatG = totalFatFromJson
        ?: items.sumOf { item ->
            item.nutrition.find { it.name == "Fat" }?.amount?.toIntOrNull() ?: 0
        }

    return AnalysisResult(
        description = description,
        items = items,
        totalCalories = totalCalories,
        totalProteinG = totalProteinG,
        totalCarbsG = totalCarbsG,
        totalFatG = totalFatG,
        aiNote = aiNote,
        source = source,
        mealType = mealType,
    )
}
