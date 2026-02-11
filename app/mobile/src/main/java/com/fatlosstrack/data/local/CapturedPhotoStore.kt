package com.fatlosstrack.data.local

import android.net.Uri
import androidx.compose.runtime.mutableIntStateOf

/**
 * Simple in-memory store for passing captured photo URIs between
 * MealCaptureScreen → AnalysisResultScreen / ChatScreen without serializing through nav args.
 * Cleared after consumption.
 */
object CapturedPhotoStore {
    private val _photos = mutableListOf<Uri>()

    /** Incremented on every [store] call so composables can observe changes. */
    var version = mutableIntStateOf(0)
        private set

    fun store(photos: List<Uri>) {
        _photos.clear()
        _photos.addAll(photos)
        version.intValue++
    }

    /** Returns photos without clearing — allows re-analysis. */
    fun consume(): List<Uri> = _photos.toList()

    /** Explicitly clear when done (e.g. after logging or discarding). */
    fun clear() {
        _photos.clear()
    }

    fun peek(): List<Uri> = _photos.toList()
}
