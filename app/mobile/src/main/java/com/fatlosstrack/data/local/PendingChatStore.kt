package com.fatlosstrack.data.local

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** An initial chat message and the photos sent with it. */
data class PendingChat(val message: String, val images: List<Uri> = emptyList())

/** In-memory holder for an initial chat message sent from the AiBar or camera flow. */
object PendingChatStore {
    private val pending = MutableStateFlow<PendingChat?>(null)

    fun store(message: String, images: List<Uri> = emptyList()) {
        pending.value = PendingChat(message, images.toList())
    }

    /** Returns the pending message and clears it in one atomic step. */
    fun take(): PendingChat? = pending.getAndUpdate { null }
}
