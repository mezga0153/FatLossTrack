package com.fatlosstrack.data.local

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/** Photos confirmed on the capture screen, plus the optional comment typed there. */
data class CapturedPhotos(val photos: List<Uri>, val comment: String = "")

/**
 * In-memory store for passing captured photo URIs between
 * MealCaptureScreen → AnalysisResultScreen / ChatScreen without serializing through nav args.
 * Backed by a [StateFlow] so ChatScreen can collect new captures instead of polling.
 */
object CapturedPhotoStore {
    private val _captured = MutableStateFlow<CapturedPhotos?>(null)

    /** The pending capture, or null when there is none. Read without clearing — allows re-analysis. */
    val captured: StateFlow<CapturedPhotos?> = _captured.asStateFlow()

    fun store(photos: List<Uri>, comment: String = "") {
        _captured.value = CapturedPhotos(photos.toList(), comment)
    }

    /** Returns the pending capture and clears it in one atomic step. */
    fun take(): CapturedPhotos? = _captured.getAndUpdate { null }

    /** Explicitly clear when done (e.g. after logging or discarding). */
    fun clear() {
        _captured.value = null
    }
}
