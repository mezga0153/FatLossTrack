package com.fatlosstrack.ui.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PendingChatStore
import com.fatlosstrack.data.local.db.ChatMessage
import com.fatlosstrack.data.local.db.ChatMessageDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.di.ApplicationScope
import com.fatlosstrack.domain.ChatContextUseCase
import com.fatlosstrack.ui.camera.AnalysisResult
import com.fatlosstrack.ui.camera.parseAnalysisJson
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import javax.inject.Inject

/**
 * Owns all mutable state and business logic for [ChatScreen].
 * The composable becomes a pure renderer that reads state and calls methods.
 *
 * Not a ViewModel — instantiated via Hilt constructor injection and scoped
 * to the composable's lifecycle via `remember`.
 */
@Stable
class ChatStateHolder @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val chatMessageDao: ChatMessageDao,
    private val mealDao: MealDao,
    private val openAiService: OpenAiService,
    private val chatContextUseCase: ChatContextUseCase,
    private val daySummaryGenerator: DaySummaryGenerator,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    /** All persisted messages (Room Flow — collect in composable). */
    val messages: Flow<List<ChatMessage>> = chatMessageDao.getAllMessages()

    /** Live streaming content; null when idle, empty string when waiting for first token. */
    var streamingContent: String? by mutableStateOf(null)
        private set

    /** True while a send / retry / pending-consume is in progress. */
    var isLoading: Boolean by mutableStateOf(false)
        private set

    // ── Public actions ──────────────────────────────────────────────

    /** Send a new user message and stream the AI response. */
    fun sendMessage(text: String, imageUris: List<Uri> = emptyList()) {
        if (text.isBlank() || isLoading) return
        AppLogger.instance?.ai("Chat: ${text.take(80)}${if (imageUris.isNotEmpty()) " +${imageUris.size} images" else ""}")
        isLoading = true
        appScope.launch {
            val urisString = if (imageUris.isNotEmpty()) imageUris.joinToString(",") { it.toString() } else null
            chatMessageDao.insert(ChatMessage(role = "user", content = text, imageUris = urisString))
            streamResponse(imageUris)
            isLoading = false
        }
    }

    /**
     * Retry: delete the failed AI message and re-stream.
     * [aiMessage] is the assistant message to remove before retrying.
     */
    fun retryMessage(aiMessage: ChatMessage) {
        appScope.launch {
            chatMessageDao.delete(aiMessage)
            isLoading = true
            streamResponse()
            isLoading = false
        }
    }

    /** Clear all chat history. */
    fun clearHistory() {
        appScope.launch { chatMessageDao.clearAll() }
    }

    /** Log a meal from a [MEAL]...[/MEAL] JSON block in a chat response. */
    fun logMealFromChat(mealJson: String) {
        appScope.launch {
            try {
                val obj = Json.parseToJsonElement(mealJson).jsonObject
                val description = obj["description"]?.jsonPrimitive?.content ?: "Chat meal"
                val kcal = obj["kcal"]?.jsonPrimitive?.intOrNull ?: 0
                val proteinG = obj["protein_g"]?.jsonPrimitive?.intOrNull ?: 0
                val carbsG = obj["carbs_g"]?.jsonPrimitive?.intOrNull ?: 0
                val fatG = obj["fat_g"]?.jsonPrimitive?.intOrNull ?: 0
                val itemsArr = obj["items"]?.toString()
                val today = java.time.LocalDate.now()
                mealDao.insert(
                    MealEntry(
                        date = today,
                        description = description,
                        itemsJson = itemsArr,
                        totalKcal = kcal,
                        totalProteinG = proteinG,
                        totalCarbsG = carbsG,
                        totalFatG = fatG,
                    ),
                )
                AppLogger.instance?.meal("Logged from chat: $description — $kcal kcal")
                daySummaryGenerator.launchForDate(today, "Chat:mealLogged")
            } catch (e: Exception) {
                AppLogger.instance?.error("Chat", "Failed to log meal from chat: ${e.message}")
            }
        }
    }

    /** Save a meal entry (used by the review sheet after user edits). */
    fun saveMeal(entry: MealEntry) {
        appScope.launch {
            mealDao.insert(entry)
            AppLogger.instance?.meal("Logged from chat review: ${entry.description.take(50)} — ${entry.totalKcal} kcal")
            daySummaryGenerator.launchForDate(entry.date, "Chat:mealReviewed")
        }
    }

    /** Log from AnalysisResult (used by the analysis-style review sheet). */
    fun saveMealFromAnalysis(
        analysisResult: AnalysisResult,
        date: java.time.LocalDate,
        category: MealCategory,
        mealType: MealType?,
    ) {
        appScope.launch {
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
                    date = date,
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
                "Logged from chat analysis: ${analysisResult.description.take(50)} — " +
                    "${analysisResult.totalCalories} kcal, date=$date",
            )
            daySummaryGenerator.launchForDate(date, "Chat:mealAnalysisLogged")
        }
    }

    /** Correct a meal via AI text edit (no photo re-send). */
    suspend fun correctMealJson(mealJson: String, correction: String): AnalysisResult? {
        return try {
            val result = openAiService.editMealWithAi(mealJson, correction)
            result.getOrNull()?.let { parseAnalysisJson(it) }
        } catch (e: Exception) {
            AppLogger.instance?.error("Chat", "Meal correction failed: ${e.message}")
            null
        }
    }

    /**
     * Consume a pending message from [PendingChatStore] (sent by AiBar or camera suggest).
     * Should be called once when the screen first appears.
     */
    fun consumePending() {
        val pending = PendingChatStore.consume()
        val pendingImages = PendingChatStore.consumeImages()
        if (!pending.isNullOrBlank()) {
            com.fatlosstrack.data.local.CapturedPhotoStore.clear()
            isLoading = true
            appScope.launch {
                val urisString = if (pendingImages.isNotEmpty()) pendingImages.joinToString(",") { it.toString() } else null
                chatMessageDao.insert(ChatMessage(role = "user", content = pending, imageUris = urisString))
                streamResponse(pendingImages)
                isLoading = false
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────

    private suspend fun streamResponse(imageUris: List<Uri> = emptyList()) {
        val context = chatContextUseCase.build()
        val recent = chatMessageDao.getRecentMessages(20).reversed()
        val history = recent.map { it.role to it.content }

        // Load bitmaps from URIs on IO thread
        val bitmaps = if (imageUris.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                imageUris.mapNotNull { uri ->
                    try {
                        appContext.contentResolver.openInputStream(uri)?.use { stream ->
                            BitmapFactory.decodeStream(stream)
                        }
                    } catch (e: Exception) {
                        AppLogger.instance?.error("Chat", "Failed to load image: $uri — ${e.message}")
                        null
                    }
                }
            }
        } else emptyList()

        streamingContent = ""
        try {
            val sb = StringBuilder()
            openAiService.streamChatWithHistory(history, context, bitmaps).collect { delta ->
                sb.append(delta)
                streamingContent = sb.toString()
            }
            chatMessageDao.insert(ChatMessage(role = "assistant", content = sb.toString()))
        } catch (e: Exception) {
            chatMessageDao.insert(
                ChatMessage(
                    role = "assistant",
                    content = "\u26a0\ufe0f ${e.message ?: "Something went wrong"}",
                ),
            )
        }
        streamingContent = null
    }
}
