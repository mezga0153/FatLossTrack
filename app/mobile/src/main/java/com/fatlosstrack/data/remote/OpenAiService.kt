package com.fatlosstrack.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.AiUsageDao
import com.fatlosstrack.data.local.db.AiUsageEntry
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.utils.io.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
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
    private val aiUsageDao: AiUsageDao,
) {
    companion object {
        private const val API_URL = "https://api.openai.com/v1/chat/completions"
    }

    /** Check if API key is configured */
    suspend fun hasApiKey(): Boolean = prefs.openAiApiKey.first().isNotBlank()

    /** Get the language instruction suffix based on user preference */
    private suspend fun languageSuffix(): String {
        val lang = prefs.language.first()
        return when (lang) {
            "en" -> ""
            "sl" -> "\n\nIMPORTANT: Respond in Slovenian (slovenščina)."
            "hu" -> "\n\nIMPORTANT: Respond in Hungarian (magyar)."
            else -> ""
        }
    }

    /** Record token usage from an API response */
    private suspend fun recordUsage(json: JsonObject, feature: String) {
        try {
            val usage = json["usage"]?.jsonObject ?: return
            val model = json["model"]?.jsonPrimitive?.content ?: "unknown"
            val promptTokens = usage["prompt_tokens"]?.jsonPrimitive?.int ?: 0
            val completionTokens = usage["completion_tokens"]?.jsonPrimitive?.int ?: 0
            aiUsageDao.insert(AiUsageEntry(
                feature = feature,
                model = model,
                promptTokens = promptTokens,
                completionTokens = completionTokens,
            ))
        } catch (e: Exception) {
            appLogger.error("AI", "Failed to record usage: ${e.message}")
        }
    }

    /** Simple text chat completion */
    suspend fun chat(
        userMessage: String,
        systemPrompt: String = SYSTEM_PROMPT,
        feature: String = "chat",
    ): Result<String> = runCatching {
        appLogger.ai("Chat request: ${userMessage.take(80)}${if (userMessage.length > 80) "…" else ""}")
        val apiKey = prefs.openAiApiKey.first()
        require(apiKey.isNotBlank()) { "OpenAI API key not set. Go to Settings → AI to configure." }
        val model = prefs.openAiModel.first()
        val langSuffix = languageSuffix()

        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemPrompt + langSuffix)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                }
            }
            put("max_completion_tokens", 4096)
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
        recordUsage(json, feature)
        val content = json["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
        appLogger.ai("Chat response (${content.length} chars): ${content.take(120)}${if (content.length > 120) "…" else ""}")
        content
    }

    /**
     * Chat completion with full message history for conversational context.
     * [history] is a list of role→content pairs (oldest first).
     * [contextBlock] is prepended to the system prompt with user's current data.
     */
    suspend fun chatWithHistory(
        history: List<Pair<String, String>>,
        contextBlock: String,
    ): Result<String> = runCatching {
        appLogger.ai("Chat with history (${history.size} messages)")
        val apiKey = prefs.openAiApiKey.first()
        require(apiKey.isNotBlank()) { "OpenAI API key not set. Go to Settings → AI to configure." }
        val model = prefs.openAiModel.first()
        val langSuffix = languageSuffix()

        val systemContent = SYSTEM_PROMPT + "\n\n" + contextBlock + langSuffix

        val body = buildJsonObject {
            put("model", model)
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemContent)
                }
                // Include last N messages of history to stay within token limits
                val recentHistory = if (history.size > 30) history.takeLast(30) else history
                recentHistory.forEach { (role, content) ->
                    addJsonObject {
                        put("role", role)
                        put("content", content)
                    }
                }
            }
            put("max_completion_tokens", 4096)
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
        recordUsage(json, "chat")
        val content = json["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
        appLogger.ai("Chat response (${content.length} chars): ${content.take(120)}${if (content.length > 120) "…" else ""}")
        content
    }

    /**
     * Streaming chat completion — emits content deltas as they arrive.
     * Collects SSE events from the OpenAI streaming API.
     */
    fun streamChatWithHistory(
        history: List<Pair<String, String>>,
        contextBlock: String,
        photos: List<Bitmap> = emptyList(),
    ): Flow<String> = flow {
        appLogger.ai("Streaming chat with history (${history.size} messages)${if (photos.isNotEmpty()) ", ${photos.size} photos" else ""}")
        val apiKey = prefs.openAiApiKey.first()
        require(apiKey.isNotBlank()) { "OpenAI API key not set. Go to Settings → AI to configure." }
        val model = prefs.openAiModel.first()
        val langSuffix = languageSuffix()

        val systemContent = SYSTEM_PROMPT + "\n\n" + contextBlock + langSuffix

        val body = buildJsonObject {
            put("model", model)
            put("stream", true)
            putJsonObject("stream_options") {
                put("include_usage", true)
            }
            putJsonArray("messages") {
                addJsonObject {
                    put("role", "system")
                    put("content", systemContent)
                }
                val recentHistory = if (history.size > 30) history.takeLast(30) else history
                recentHistory.forEachIndexed { index, (role, content) ->
                    addJsonObject {
                        put("role", role)
                        // Attach photos only to the last user message
                        if (photos.isNotEmpty() && role == "user" && index == recentHistory.lastIndex) {
                            put("content", buildJsonArray {
                                addJsonObject {
                                    put("type", "text")
                                    put("text", content)
                                }
                                photos.forEach { bitmap ->
                                    addJsonObject {
                                        put("type", "image_url")
                                        putJsonObject("image_url") {
                                            put("url", "data:image/jpeg;base64,${bitmapToBase64(bitmap)}")
                                            put("detail", "low")
                                        }
                                    }
                                }
                            })
                        } else {
                            put("content", content)
                        }
                    }
                }
            }
            put("max_completion_tokens", 4096)
        }

        val statement = client.preparePost(API_URL) {
            contentType(ContentType.Application.Json)
            bearerAuth(apiKey)
            setBody(Json.encodeToString(body))
        }

        statement.execute { response ->
            if (response.status != HttpStatusCode.OK) {
                val errorBody = response.bodyAsText()
                appLogger.error("AI", "Stream API error ${response.status}: ${errorBody.take(200)}")
                error("OpenAI API error ${response.status}: $errorBody")
            }

            val channel = response.bodyAsChannel()
            val sb = StringBuilder()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data: ")) continue
                val data = line.removePrefix("data: ").trim()
                if (data == "[DONE]") break
                try {
                    val chunk = Json.parseToJsonElement(data).jsonObject
                    val delta = chunk["choices"]?.jsonArray?.get(0)?.jsonObject
                        ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.content
                    if (delta != null) {
                        sb.append(delta)
                        emit(delta)
                    }
                    // Record usage from final chunk (has usage field when stream_options.include_usage=true)
                    val usage = chunk["usage"]?.jsonObject
                    if (usage != null) {
                        recordUsage(chunk, "chat")
                    }
                } catch (_: Exception) { /* skip malformed chunks */ }
            }
            appLogger.ai("Stream complete (${sb.length} chars): ${sb.take(120)}${if (sb.length > 120) "…" else ""}")
        }
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
        return chat(userMessage, dateContext + TEXT_MEAL_LOG_PROMPT, feature = "meal_text")
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
        val langSuffix = languageSuffix()

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
                    put("content", SYSTEM_PROMPT + langSuffix)
                }
                addJsonObject {
                    put("role", "user")
                    put("content", contentArray)
                }
            }
            put("max_completion_tokens", 4096)
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
        val visionFeature = if (mode == "log") "meal_photo" else "meal_suggest"
        recordUsage(json, visionFeature)
        val content = json["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
        appLogger.ai("Vision response (${content.length} chars): ${content.take(120)}${if (content.length > 120) "\u2026" else ""}")
        content
    }

    /**
     * AI-powered meal correction: sends the current meal definition + user comment
     * describing what's wrong, returns corrected JSON in the same format as meal log.
     */
    suspend fun editMealWithAi(
        mealJson: String,
        userCorrection: String,
    ): Result<String> {
        appLogger.ai("AI meal edit: ${userCorrection.take(80)}")
        return chat(
            userMessage = "Here is my current meal entry:\n$mealJson\n\nCorrection: $userCorrection",
            systemPrompt = AI_MEAL_EDIT_PROMPT,
            feature = "meal_edit",
        )
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
Format responses using markdown — use **bold** for emphasis, bullet lists, numbered lists, tables when comparing data, and headers for sections. Keep it mobile-friendly.

IMPORTANT: Whenever the user tells you what they ate (e.g. "I had pizza", "yesterday I ate chocolate"), ALWAYS include a [MEAL] block for that food so they can log it. Estimate the calories and macros for what they described. This is the #1 priority — the user is telling you about their intake and expects to be able to log it.

When you suggest or describe a specific meal, OR when the user reports something they already ate, include a machine-readable block so the user can log it with one tap. Use this exact format:

[MEAL]{"description":"Short meal name","kcal":123,"protein_g":10,"carbs_g":20,"fat_g":5,"meal_type":"lunch","day_offset":0,"items":[{"name":"Item","portion":"100g","calories":123,"protein_g":10,"fat_g":5,"carbs_g":20}]}[/MEAL]

Fields: meal_type is one of breakfast|brunch|lunch|dinner|snack (pick the most appropriate). day_offset is 0 for today, -1 for yesterday, -2 for two days ago, etc. — use 0 unless the user explicitly mentions a past day.
Place each [MEAL]...[/MEAL] block on its own line right after describing that meal. You can include multiple blocks if suggesting multiple meals. The block must be valid JSON. Do NOT put the block inside a markdown code fence."""

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
  "total_carbs_g": 0,
  "total_fat_g": 0,
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
  "total_carbs_g": 0,
  "total_fat_g": 0,
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
  "total_carbs_g": 0,
  "total_fat_g": 0,
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

private const val AI_MEAL_EDIT_PROMPT = """You are FatLoss Track's meal correction assistant.
The user will provide an existing meal entry (as JSON) and a correction comment describing what's wrong.
Apply the correction to the meal and respond with ONLY the corrected JSON (no markdown fences, no extra text).

Use this exact JSON format:
{
  "description": "Updated meal description",
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
  "total_carbs_g": 0,
  "total_fat_g": 0,
  "coach_note": "Brief coaching comment about the corrected meal"
}

Rules:
- Keep fields the user did NOT mention unchanged unless they logically need updating (e.g. recalculate totals).
- Recalculate total_calories, total_protein_g, total_carbs_g, total_fat_g from the items array.
- If the user says to add an item, add it. If they say to remove one, remove it.
- If the user says portions were different, adjust the specific item's portion, calories, and macros.
- Err on the side of slightly overestimating calories.
"""
