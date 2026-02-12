package com.fatlosstrack.ui.components

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PendingTextMealStore
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.time.LocalDate
import javax.inject.Inject

/**
 * Owns all mutable state and business logic for [AiBar].
 * Handles text-meal parsing, fallback chat, and direct meal insertion.
 */
@Stable
class AiBarStateHolder @Inject constructor(
    private val openAiService: OpenAiService,
    private val mealDao: MealDao,
    private val daySummaryGenerator: DaySummaryGenerator,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var aiResponse: String? by mutableStateOf(null)
        private set
    var aiError: String? by mutableStateOf(null)
        private set
    var mealLogged: Boolean by mutableStateOf(false)
        private set

    fun dismiss() {
        aiResponse = null
        aiError = null
        mealLogged = false
    }

    /**
     * Process user input: try to parse as a meal first, fall back to chat.
     *
     * @param query       user text
     * @param errorFallback  fallback error string (resolved from resources by the composable)
     * @param onTextMealAnalyzed  navigate to analysis screen for the parsed meal
     * @param onChatOpen  navigate to chat screen with the query
     */
    fun submit(
        query: String,
        errorFallback: String,
        onTextMealAnalyzed: ((LocalDate) -> Unit)?,
        onChatOpen: ((String) -> Unit)?,
    ) {
        if (query.isBlank() || isLoading) return
        isLoading = true
        aiResponse = null
        aiError = null
        mealLogged = false

        appScope.launch {
            AppLogger.instance?.ai("AiBar: text query — ${query.take(60)}")
            val mealResult = openAiService.parseTextMeal(query)
            mealResult.fold(
                onSuccess = { raw ->
                    val parsed = tryParseMealJson(raw)
                    if (parsed != null && onTextMealAnalyzed != null) {
                        // Meal parsed — store for analysis screen
                        val targetDate = LocalDate.now().plusDays(parsed.dayOffset.toLong())
                        AppLogger.instance?.ai("AiBar: text meal parsed — ${parsed.description.take(40)}, ${parsed.totalCalories} kcal")
                        PendingTextMealStore.store(raw, targetDate)
                        isLoading = false
                        withContext(Dispatchers.Main) {
                            onTextMealAnalyzed(targetDate)
                        }
                    } else if (parsed != null) {
                        // Direct insert fallback
                        val targetDate = LocalDate.now().plusDays(parsed.dayOffset.toLong())
                        AppLogger.instance?.meal("AiBar direct: ${parsed.description.take(40)} — ${parsed.totalCalories} kcal, ${parsed.totalProteinG}g P, ${parsed.totalCarbsG}g C, ${parsed.totalFatG}g F")
                        mealDao.insert(
                            MealEntry(
                                date = targetDate,
                                description = parsed.description,
                                itemsJson = parsed.itemsJson,
                                totalKcal = parsed.totalCalories,
                                totalProteinG = parsed.totalProteinG,
                                totalCarbsG = parsed.totalCarbsG,
                                totalFatG = parsed.totalFatG,
                                coachNote = parsed.coachNote,
                                category = parsed.source,
                            ),
                        )
                        daySummaryGenerator.launchForDate(targetDate, "AiBar:textMealLogged")
                        isLoading = false
                        mealLogged = true
                        aiResponse = "${parsed.description} — ${parsed.totalCalories} kcal" +
                            if (parsed.coachNote.isNotBlank()) "\n\n${parsed.coachNote}" else ""
                    } else {
                        // Not a meal — open chat screen
                        AppLogger.instance?.ai("AiBar: not meal, opening chat")
                        isLoading = false
                        if (onChatOpen != null) {
                            withContext(Dispatchers.Main) { onChatOpen(query) }
                        } else {
                            val chatResult = openAiService.chat(query)
                            chatResult.fold(
                                onSuccess = { aiResponse = it },
                                onFailure = { aiError = it.message ?: errorFallback },
                            )
                        }
                    }
                },
                onFailure = {
                    // parseTextMeal failed — open chat screen
                    isLoading = false
                    if (onChatOpen != null) {
                        withContext(Dispatchers.Main) { onChatOpen(query) }
                    } else {
                        val chatResult = openAiService.chat(query)
                        chatResult.fold(
                            onSuccess = { aiResponse = it },
                            onFailure = { e -> aiError = e.message ?: errorFallback },
                        )
                    }
                },
            )
        }
    }
}

// ── Parsed meal helper ──

private data class ParsedMeal(
    val dayOffset: Int,
    val description: String,
    val source: MealCategory,
    val itemsJson: String,
    val totalCalories: Int,
    val totalProteinG: Int,
    val totalCarbsG: Int,
    val totalFatG: Int,
    val coachNote: String,
)

private fun tryParseMealJson(raw: String): ParsedMeal? {
    return try {
        val cleaned = raw
            .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^```\\s*", RegexOption.MULTILINE), "")
            .trim()
        val json = Json.parseToJsonElement(cleaned).jsonObject
        val isMeal = json["is_meal"]?.jsonPrimitive?.boolean ?: return null
        if (!isMeal) return null

        val dayOffset = json["day_offset"]?.jsonPrimitive?.int ?: 0
        val description = json["description"]?.jsonPrimitive?.content ?: ""
        val totalCalories = json["total_calories"]?.jsonPrimitive?.int ?: 0
        val coachNote = json["coach_note"]?.jsonPrimitive?.content ?: ""
        val sourceStr = json["source"]?.jsonPrimitive?.content ?: "home"
        val source = when (sourceStr.lowercase()) {
            "restaurant" -> MealCategory.RESTAURANT
            "fast_food", "fastfood", "fast food" -> MealCategory.FAST_FOOD
            else -> MealCategory.HOME
        }

        val itemsJson = json["items"]?.jsonArray?.let { items ->
            buildJsonArray {
                items.forEach { itemEl ->
                    val item = itemEl.jsonObject
                    add(buildJsonObject {
                        put("name", item["name"]?.jsonPrimitive?.content ?: "Unknown")
                        put("portion", item["portion"]?.jsonPrimitive?.content ?: "")
                        put("calories", item["calories"]?.jsonPrimitive?.int ?: 0)
                        put("protein_g", item["protein_g"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("fat_g", item["fat_g"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("carbs_g", item["carbs_g"]?.jsonPrimitive?.intOrNull ?: 0)
                    })
                }
            }.toString()
        } ?: "[]"

        ParsedMeal(
            dayOffset = dayOffset,
            description = description,
            source = source,
            itemsJson = itemsJson,
            totalCalories = totalCalories,
            totalProteinG = json["total_protein_g"]?.jsonPrimitive?.intOrNull ?: 0,
            totalCarbsG = json["total_carbs_g"]?.jsonPrimitive?.intOrNull ?: 0,
            totalFatG = json["total_fat_g"]?.jsonPrimitive?.intOrNull ?: 0,
            coachNote = coachNote,
        )
    } catch (_: Exception) {
        null
    }
}
