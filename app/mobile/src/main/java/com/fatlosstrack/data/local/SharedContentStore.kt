package com.fatlosstrack.data.local

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Content shared into the app from another app's share sheet. */
sealed interface SharedContent {
    /** Photos already copied into cacheDir, so they stay readable after the sender's grant lapses. */
    data class Photos(val uris: List<Uri>) : SharedContent

    /** Plain text, parsed like a meal typed into the AI bar. */
    data class Text(val text: String) : SharedContent
}

/**
 * In-memory store for content received via ACTION_SEND / ACTION_SEND_MULTIPLE.
 * MainActivity fills it; the nav graph takes it once onboarding is out of the way.
 * A newer share replaces one that hasn't been handled yet.
 */
object SharedContentStore {
    private val _pending = MutableStateFlow<SharedContent?>(null)

    val pending: StateFlow<SharedContent?> = _pending.asStateFlow()

    fun store(content: SharedContent) {
        _pending.value = content
    }

    /** Returns the pending share and clears it in one atomic step. */
    fun take(): SharedContent? = _pending.getAndUpdate { null }
}
