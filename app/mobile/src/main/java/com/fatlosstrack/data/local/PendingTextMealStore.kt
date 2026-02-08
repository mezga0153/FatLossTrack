package com.fatlosstrack.data.local

import java.time.LocalDate

/**
 * In-memory store for passing AI-parsed text meal data from AiBar
 * to the AnalysisResultScreen, similar to CapturedPhotoStore.
 * Cleared after consumption.
 */
object PendingTextMealStore {
    var rawJson: String? = null
        private set
    var targetDate: LocalDate? = null
        private set

    fun store(json: String, date: LocalDate) {
        rawJson = json
        targetDate = date
    }

    fun consume(): Pair<String, LocalDate>? {
        val json = rawJson ?: return null
        val date = targetDate ?: LocalDate.now()
        return json to date
    }

    fun clear() {
        rawJson = null
        targetDate = null
    }
}
