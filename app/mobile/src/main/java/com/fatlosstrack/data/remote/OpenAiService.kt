package com.fatlosstrack.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpenAiService @Inject constructor(
    private val client: HttpClient,
    private val prefs: PreferencesManager,
    private val appLogger: AppLogger,
) {
    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
    }

    /** Check if API key is configured */
    suspend fun hasApiKey(): Boolean = prefs.openAiApiKey.first().isNotBlank()

    /** Simple text chat completion */
    suspend fun chat(
        userMessage: String,
        systemPrompt: String = SYSTEM_PROMPT,
    ): Result<String> = runCatching {
        appLogger.ai("Chat request: ${userMessage.take(80)}${if (userMessage.length > 80) "…" else ""}")
        val apiKey = prefs.openAiApiKey.first()
        require(apiKey.isNotBlank()) { "OpenAI API key not set. Go to Settings → AI to configure." }
        val model = prefs.openAiModel.first()

        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                }
            }
            put("max_completion_tokens", 1024)
            put("temperature", 0.7)
        }

        val response = client.post(API_URL) {
            contentType(ContentType.Application.Json)
            bearerAuth(apiKey)
            setBody(Json.encodeToString(body))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            appLogger.error("AI", "Chat API error ${response.status}: ${errorBody.take(200)}")
            error("OpenAI API error ${response.status}: $errorBody")
        }

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val content = json["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
        appLogger.ai("Chat response (${content.length} chars): ${content.take(120)}${if (content.length > 120) "…" else ""}")
        content
    }

    /**
     * Parse a natural-language meal description into structured JSON.
     * Returns the raw JSON string from AI with day_offset, items, etc.
     */
    suspend fun parseTextMeal(userMessage: String): Result<String> {
        appLogger.ai("Text meal parse: ${userMessage.take(80)}")
        val today = java.time.LocalDate.now()
        val dayOfWeek = today.dayOfWeek.getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.ENGLISH)
        val dateContext = "Today is $dayOfWeek, ${today}.\n\n"
        return chat(userMessage, dateContext + TEXT_MEAL_LOG_PROMPT)
    }

    /** Vision-based meal analysis — sends photos + prompt to GPT-5.2 */
    suspend fun analyzeMeal(
        photos: List<Bitmap>,
        mode: String, // "log" or "suggest"
        correction: String? = null,
    ): Result<String> = runCatching {
        appLogger.ai("Vision analysis: ${photos.size} photos, mode=$mode${if (correction != null) ", correction" else ""}")
        val apiKey = prefs.openAiApiKey.first()
        require(apiKey.isNotBlank()) { "OpenAI API key not set. Go to Settings → AI to configure." }
        val model = prefs.openAiModel.first()

        val basePrompt = if (mode == "log") MEAL_LOG_PROMPT else MEAL_SUGGEST_PROMPT
        val prompt = if (correction != null) {
            "$basePrompt\n\nIMPORTANT CORRECTION from user: $correction\nPlease re-analyze with this correction applied."
        } else {
            basePrompt
        }

        val contentArray = buildJsonArray {
            // Text prompt
            addJsonObject {
                put("type", "text")
                put("text", prompt)
            }
            // Photos as base64
            photos.forEach { bitmap ->
                addJsonObject {
                    put("type", "image_url")
                    putJsonObject("image_url") {
                        put("url", "data:image/jpeg;base64,${bitmapToBase64(bitmap)}")
                        put("detail", "low") // saves tokens
                    }
                }
            }
        }

        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", contentArray)
                }
            }
            put("max_completion_tokens", 1500)
            put("temperature", 0.5)
        }

        val response = client.post(API_URL) {
            contentType(ContentType.Application.Json)
            bearerAuth(apiKey)
            setBody(Json.encodeToString(body))
        }

        if (response.status != HttpStatusCode.OK) {
            val errorBody = response.bodyAsText()
            appLogger.error("AI", "Vision API error ${response.status}: ${errorBody.take(200)}")
            error("OpenAI API error ${response.status}: $errorBody")
        }

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        val content = json["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
        appLogger.ai("Vision response (${content.length} chars): ${content.take(120)}${if (content.length > 120) "\u2026" else ""}")
        content
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val stream = ByteArrayOutputStream()
        // Resize if too large to save tokens/bandwidth
        val scaled = if (bitmap.width > 1024 || bitmap.height > 1024) {
            val scale = 1024f / maxOf(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt(),
                (bitmap.height * scale).toInt(),
                true,
            )
        } else {
            bitmap
        }
        scaled.compress(Bitmap.CompressFormat.JPEG, 80, stream)
        return Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
    }
}

// ---- Prompts ----

private const val SYSTEM_PROMPT = """You are FatLoss Track's AI coach — a no-BS weight loss advisor.
You have access to the user's weight trend data, meals, and goals.
Be concise, data-driven, and actionable. Use metric units (kg, kcal).
When analyzing meals, provide specific calorie and macro estimates.
Format responses in a mobile-friendly way — short paragraphs, bullet points when useful."""

private const val MEAL_LOG_PROMPT = """Analyze this meal photo(s). Respond in this exact JSON format:
{
  "description": "Brief description of what you see",
  "source": "home|restaurant|fast_food",
  "meal_type": "breakfast|brunch|lunch|dinner|snack",
  "items": [
    {
      "name": "Item name",
      "portion": "Estimated portion size",
      "calories": 0,
      "protein_g": 0,
      "fat_g": 0,
      "carbs_g": 0
    }
  ],
  "total_calories": 0,
  "total_protein_g": 0,
  "coach_note": "Brief coaching comment about this meal in context of a fat loss diet"
}
For "source", determine if the meal is: "home" (home-cooked), "restaurant" (dine-in/takeout from a restaurant), or "fast_food" (fast food chain). Look at plating, packaging, and food style to decide.
For "meal_type", infer from the food and current time of day: "breakfast", "brunch", "lunch", "dinner", or "snack".
Be specific with portions. Err on the side of slightly overestimating calories."""

private const val MEAL_SUGGEST_PROMPT = """Look at the available ingredients in this photo(s) and suggest a meal.
Respond in this exact JSON format:
{
  "description": "What ingredients you see and what meal you suggest",
  "source": "home",
  "items": [
    {
      "name": "Dish component",
      "portion": "Recommended portion",
      "calories": 0,
      "protein_g": 0,
      "fat_g": 0,
      "carbs_g": 0
    }
  ],
  "total_calories": 0,
  "total_protein_g": 0,
  "coach_note": "Why this meal is good for fat loss and how to prepare it quickly"
}
Prioritize high-protein, moderate-calorie meals."""

private const val TEXT_MEAL_LOG_PROMPT = """You are a calorie-tracking assistant inside FatLoss Track.
The user will describe what they ate in natural language. They may mention timing like "this morning", "yesterday evening", "for lunch", etc.

First decide: is this a meal log or a general question?

If it IS a meal description, respond with ONLY this JSON (no markdown fences, no extra text):
{
  "is_meal": true,
  "day_offset": 0,
  "description": "Brief summary of the meal",
  "source": "home|restaurant|fast_food",
  "meal_type": "breakfast|brunch|lunch|dinner|snack",
  "items": [
    {
      "name": "Item name",
      "portion": "Estimated portion",
      "calories": 0,
      "protein_g": 0,
      "fat_g": 0,
      "carbs_g": 0
    }
  ],
  "total_calories": 0,
  "total_protein_g": 0,
  "coach_note": "Brief coaching comment about this meal"
}

Rules for day_offset:
- "today", "this morning", "for lunch", "just now", or no time mention → 0
- "yesterday", "last night", "yesterday evening" → -1
- "two days ago" → -2
- Named weekdays like "on Friday", "last Monday" → calculate the negative offset from today's date (provided above). Always pick the most recent past occurrence. For example if today is Sunday and user says "on Friday", day_offset = -2.
- and so on

For "meal_type", infer from context: "this morning" or "for breakfast" → "breakfast", "for lunch" → "lunch", "for dinner" / "evening" → "dinner", etc. If unclear, infer from food type or default to "snack".

If it is NOT a meal description (general question, greeting, etc.), respond with:
{"is_meal": false}

Estimate portions generously. Err on the side of slightly overestimating calories.
"""
