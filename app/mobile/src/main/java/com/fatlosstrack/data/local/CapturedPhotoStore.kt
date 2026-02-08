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

    fun consume(): List<Uri> {
        val copy = _photos.toList()
        _photos.clear()
        return copy
    }

    fun peek(): List<Uri> = _photos.toList()
}
