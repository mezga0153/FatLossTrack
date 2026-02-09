package com.fatlosstrack.data.local

/** In-memory holder for an initial chat message sent from the AiBar. */
object PendingChatStore {
    private var pending: String? = null

    fun store(message: String) { pending = message }
    fun consume(): String? = pending.also { pending = null }
}
