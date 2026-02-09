package com.fatlosstrack.data

import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.GoalDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.Goal
import com.fatlosstrack.data.remote.OpenAiService
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates a short AI coaching summary for a given day based on
 * the day's data (weight, meals, steps, sleep, HR, exercises) and
 * the user's current goal. The summary is stored on the DailyLog.
 */
@Singleton
class DaySummaryGenerator @Inject constructor(
    private val openAiService: OpenAiService,
    private val dailyLogDao: DailyLogDao,
    private val mealDao: MealDao,
    private val goalDao: GoalDao,
    private val appLogger: AppLogger,
) {
    /**
     * Generate and persist a day summary for [date].
     * Silently no-ops if no API key is configured or if the day has no data.
     */
    suspend fun generateForDate(date: LocalDate) {
        try {
            if (!openAiService.hasApiKey()) return

            val log = dailyLogDao.getForDate(date)
            val meals = mealDao.getMealsForDate(date).first()
            val goal = goalDao.getCurrentGoal().first()

            // Skip if there's nothing to summarize
            if (log == null && meals.isEmpty()) return

            val prompt = buildPrompt(date, log, meals, goal)
            val result = openAiService.chat(prompt, SUMMARY_SYSTEM_PROMPT)

            result.onSuccess { summary ->
                val trimmed = summary.trim().removeSurrounding("\"")
                if (trimmed.isNotBlank()) {
                    val existing = log ?: DailyLog(date = date)
                    dailyLogDao.upsert(existing.copy(daySummary = trimmed))
                    appLogger.hc("$date summary generated: ${trimmed.take(60)}…")
                }
            }.onFailure { e ->
                appLogger.error("Summary", "Failed for $date", e)
            }
        } catch (e: Exception) {
            appLogger.error("Summary", "generateForDate($date) failed", e)
        }
    }

    /**
     * Generate summaries for multiple dates. Useful after HC sync.
     */
    suspend fun generateForDates(dates: List<LocalDate>) {
        dates.forEach { generateForDate(it) }
    }

    private fun buildPrompt(
        date: LocalDate,
        log: DailyLog?,
        meals: List<MealEntry>,
        goal: Goal?,
    ): String {
        val parts = mutableListOf<String>()
        parts += "Date: $date"

        // If summarizing today, include current time so AI knows the day is still in progress
        if (date == LocalDate.now()) {
            val now = java.time.LocalTime.now()
            parts += "Current time: %02d:%02d (day still in progress — don't flag missing meals/data that haven't happened yet)".format(now.hour, now.minute)
        }

        if (goal != null) {
            parts += "Goal: ${goal.targetKg} kg by ${goal.deadline} at ${goal.rateKgPerWeek} kg/week"
            goal.dailyDeficitKcal?.let { parts += "Target daily deficit: $it kcal" }
        }

        if (log != null) {
            log.weightKg?.let { parts += "Weight: %.1f kg".format(it) }
            log.steps?.let { parts += "Steps: %,d".format(it) }
            log.sleepHours?.let { parts += "Sleep: %.1f hours".format(it) }
            log.restingHr?.let { parts += "Resting HR: $it bpm" }
            log.exercisesJson?.let { parts += "Exercises: $it" }
        }

        if (meals.isNotEmpty()) {
            val totalKcal = meals.sumOf { it.totalKcal }
            parts += "Meals logged: ${meals.size} (total $totalKcal kcal)"
            meals.forEach { m ->
                val desc = "${m.description.take(50)} — ${m.totalKcal} kcal"
                parts += "  • $desc"
            }
        } else {
            parts += "No meals logged"
        }

        return parts.joinToString("\n")
    }

    companion object {
        private const val SUMMARY_SYSTEM_PROMPT = """You are FatLoss Track's daily coach. Given a user's day data and their goal, write a SHORT coaching summary (1-2 sentences max, under 120 characters ideally).

Rules:
- Be direct and specific about how this day helps or hurts their goal
- Reference actual numbers (kcal, steps, sleep hours) when relevant
- Use a supportive but honest tone
- Do NOT use quotes around your response
- Do NOT use markdown or formatting
- Just plain text, 1-2 sentences
- If data is sparse, comment on what's available

Examples:
"Great step count at 12k! 1800 kcal intake keeps you in deficit. Solid day."
"Only 4k steps and 2400 kcal — you're likely over your target today."
"7.5h sleep + 10k steps is a winning combo. Watch dinner portions though."
"No meals logged yet — track everything to stay accountable."""
    }
}
