package com.fatlosstrack.data.remote

import android.graphics.Bitmap
import android.util.Base64
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
            error("OpenAI API error ${response.status}: $errorBody")
        }

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        json["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
    }

    /** Vision-based meal analysis — sends photos + prompt to GPT-4o */
    suspend fun analyzeMeal(
        photos: List<Bitmap>,
        mode: String, // "log" or "suggest"
    ): Result<String> = runCatching {
        val apiKey = prefs.openAiApiKey.first()
        require(apiKey.isNotBlank()) { "OpenAI API key not set. Go to Settings → AI to configure." }
        val model = prefs.openAiModel.first()

        val contentArray = buildJsonArray {
            // Text prompt
            addJsonObject {
                put("type", "text")
                put("text", if (mode == "log") MEAL_LOG_PROMPT else MEAL_SUGGEST_PROMPT)
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
            error("OpenAI API error ${response.status}: $errorBody")
        }

        val json = Json.parseToJsonElement(response.bodyAsText()).jsonObject
        json["choices"]!!.jsonArray[0].jsonObject["message"]!!
            .jsonObject["content"]!!.jsonPrimitive.content
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
  "coach_note": "Brief coaching comment about this meal in context of a fat loss diet"
}
Be specific with portions. Err on the side of slightly overestimating calories."""

private const val MEAL_SUGGEST_PROMPT = """Look at the available ingredients in this photo(s) and suggest a meal.
Respond in this exact JSON format:
{
  "description": "What ingredients you see and what meal you suggest",
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
  "coach_note": "Why this meal is good for fat loss and how to prepare it quickly"
}
Prioritize high-protein, moderate-calorie meals."""
