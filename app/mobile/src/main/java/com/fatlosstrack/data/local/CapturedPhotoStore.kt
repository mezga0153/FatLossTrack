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
    private var _comment: String = ""

    /** Incremented on every [store] call so composables can observe changes. */
    var version = mutableIntStateOf(0)
        private set

    @Synchronized
    fun store(photos: List<Uri>, comment: String = "") {
        _photos.clear()
        _photos.addAll(photos)
        _comment = comment
        version.intValue++
    }

    /** Returns photos without clearing — allows re-analysis. */
    @Synchronized
    fun consume(): List<Uri> = _photos.toList()

    /** Returns the user comment without clearing it. */
    @Synchronized
    fun peekComment(): String = _comment

    /** Explicitly clear when done (e.g. after logging or discarding). */
    @Synchronized
    fun clear() {
        _photos.clear()
        _comment = ""
    }

    @Synchronized
    fun peek(): List<Uri> = _photos.toList()
}
