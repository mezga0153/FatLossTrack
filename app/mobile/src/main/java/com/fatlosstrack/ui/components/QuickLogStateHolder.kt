package com.fatlosstrack.ui.components

import androidx.compose.runtime.Stable
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.backup.AutoBackupManager
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.db.BookmarkedMeal
import com.fatlosstrack.data.local.db.BookmarkedMealDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** A meal that can be logged again with one tap. [template] holds the fields to copy. */
data class QuickMeal(
    val key: String,
    val label: String,
    val bookmarked: Boolean,
    val template: MealEntry,
)

/**
 * Owns the one-tap repeat chips shown above [AiBar]: bookmarked meals plus meals
 * repeated recently (or eaten yesterday), logged as copies without an AI call.
 */
@Stable
class QuickLogStateHolder @Inject constructor(
    private val mealDao: MealDao,
    private val bookmarkedMealDao: BookmarkedMealDao,
    private val daySummaryGenerator: DaySummaryGenerator,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    /** Bookmarks first (in the user's order), then frequent recent meals not already bookmarked. */
    fun suggestions(today: LocalDate): Flow<List<QuickMeal>> = combine(
        bookmarkedMealDao.getAll(),
        mealDao.getFrequentMeals(
            since = today.minusDays(RECENT_DAYS),
            yesterday = today.minusDays(1),
            minUses = MIN_USES,
            limit = MAX_RECENT,
        ),
    ) { bookmarks, frequent ->
        val bookmarked = bookmarks.map { bm ->
            QuickMeal(key = "bm-${bm.id}", label = bm.name, bookmarked = true, template = bm.toTemplate())
        }
        val bookmarkedDescriptions = bookmarks.map { it.description.normalized() }.toSet()
        val recent = frequent
            .filter { it.description.normalized() !in bookmarkedDescriptions }
            .map { meal ->
                QuickMeal(key = "meal-${meal.id}", label = meal.description.trim(), bookmarked = false, template = meal)
            }
        bookmarked + recent
    }

    /**
     * Copy of [template] dated today at the current time. Per-instance fields
     * (photo, note, coach note) are dropped since they described the original meal.
     */
    fun copyForNow(template: MealEntry): MealEntry {
        val now = Instant.now()
        return template.copy(
            id = 0,
            date = LocalDate.now(),
            coachNote = null,
            photoUri = null,
            note = null,
            createdAt = now,
            loggedAt = now,
        )
    }

    /**
     * Inserts [entry] on the application scope (so leaving the screen can't cancel it),
     * then triggers the day summary and auto-backup like other meal adds.
     * Returns the stored entry with its generated id, for undo.
     */
    suspend fun log(entry: MealEntry, reason: String): MealEntry = appScope.async {
        val id = mealDao.insert(entry)
        AppLogger.instance?.meal("QuickLog ($reason): ${entry.description.take(40)} — ${entry.totalKcal} kcal, ${entry.totalProteinG}g P, ${entry.totalCarbsG}g C, ${entry.totalFatG}g F, date=${entry.date}")
        daySummaryGenerator.launchForDate(entry.date, "QuickLog:$reason")
        AutoBackupManager.instance?.runBackup()
        entry.copy(id = id)
    }.await()

    /** Removes a meal logged by [log] and refreshes that day's summary. */
    fun undo(entry: MealEntry) {
        appScope.launch {
            mealDao.delete(entry)
            AppLogger.instance?.meal("QuickLog undone: ${entry.description.take(40)}, date=${entry.date}")
            daySummaryGenerator.launchForDate(entry.date, "QuickLog:undo")
        }
    }

    private companion object {
        const val RECENT_DAYS = 30L
        const val MIN_USES = 2
        const val MAX_RECENT = 6
    }
}

private fun String.normalized() = trim().lowercase()

private fun BookmarkedMeal.toTemplate() = MealEntry(
    date = LocalDate.now(),
    description = description.ifBlank { name },
    itemsJson = itemsJson,
    totalKcal = totalKcal,
    totalProteinG = totalProteinG,
    totalCarbsG = totalCarbsG,
    totalFatG = totalFatG,
    category = category,
    mealType = mealType,
)
