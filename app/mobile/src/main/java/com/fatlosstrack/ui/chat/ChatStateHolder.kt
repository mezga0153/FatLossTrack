package com.fatlosstrack.ui.chat

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
    fun sendMessage(text: String) {
        if (text.isBlank() || isLoading) return
        AppLogger.instance?.ai("Chat: ${text.take(80)}")
        isLoading = true
        appScope.launch {
            chatMessageDao.insert(ChatMessage(role = "user", content = text))
            streamResponse()
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
     * Consume a pending message from [PendingChatStore] (sent by AiBar).
     * Should be called once when the screen first appears.
     */
    fun consumePending() {
        val pending = PendingChatStore.consume()
        if (!pending.isNullOrBlank()) {
            isLoading = true
            appScope.launch {
                chatMessageDao.insert(ChatMessage(role = "user", content = pending))
                streamResponse()
                isLoading = false
            }
        }
    }

    // ── Private helpers ─────────────────────────────────────────────

    private suspend fun streamResponse() {
        val context = chatContextUseCase.build()
        val recent = chatMessageDao.getRecentMessages(20).reversed()
        val history = recent.map { it.role to it.content }
        streamingContent = ""
        try {
            val sb = StringBuilder()
            openAiService.streamChatWithHistory(history, context).collect { delta ->
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
