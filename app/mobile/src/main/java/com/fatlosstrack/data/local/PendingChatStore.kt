package com.fatlosstrack.data.local

import android.net.Uri

/** In-memory holder for an initial chat message sent from the AiBar or camera flow. */
object PendingChatStore {
    private var pending: String? = null
    private var pendingImages: List<Uri> = emptyList()

    fun store(message: String, images: List<Uri> = emptyList()) {
        pending = message
        pendingImages = images
    }
    fun consume(): String? = pending.also { pending = null }
    fun consumeImages(): List<Uri> = pendingImages.also { pendingImages = emptyList() }
}
