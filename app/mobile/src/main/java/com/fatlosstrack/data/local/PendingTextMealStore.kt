package com.fatlosstrack.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import java.time.LocalDate

/** AI-parsed text meal JSON and the day it should be logged on. */
data class PendingTextMeal(val json: String, val date: LocalDate)

/**
 * In-memory store for passing AI-parsed text meal data from AiBar
 * to the AnalysisResultScreen, similar to CapturedPhotoStore.
 * Cleared after consumption.
 */
object PendingTextMealStore {
    private val pending = MutableStateFlow<PendingTextMeal?>(null)

    fun store(json: String, date: LocalDate) {
        pending.value = PendingTextMeal(json, date)
    }

    /** Returns the pending meal and clears it in one atomic step. */
    fun take(): PendingTextMeal? = pending.getAndUpdate { null }

    fun clear() {
        pending.value = null
    }
}
