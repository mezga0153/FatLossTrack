package com.fatlosstrack.ui.chat

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PendingChatStore
import com.fatlosstrack.data.local.db.ChatMessage
import com.fatlosstrack.data.local.db.ChatMessageDao
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.di.ApplicationScope
import com.fatlosstrack.domain.ChatContextUseCase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val openAiService: OpenAiService,
    private val chatContextUseCase: ChatContextUseCase,
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
