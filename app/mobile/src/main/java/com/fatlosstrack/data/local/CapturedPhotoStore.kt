package com.fatlosstrack.data.local

import android.net.Uri

/**
 * Simple in-memory store for passing captured photo URIs between
 * MealCaptureScreen → AnalysisResultScreen without serializing through nav args.
 * Cleared after consumption.
 */
object CapturedPhotoStore {
    private val _photos = mutableListOf<Uri>()

    fun store(photos: List<Uri>) {
        _photos.clear()
        _photos.addAll(photos)
    }

    /** Returns photos without clearing — allows re-analysis. */
    fun consume(): List<Uri> = _photos.toList()

    /** Explicitly clear when done (e.g. after logging or discarding). */
    fun clear() {
        _photos.clear()
    }

    fun peek(): List<Uri> = _photos.toList()
}
