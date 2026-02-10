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
    /** Maps date → hash of the data that was used to generate its current summary. */
    private val dataHashCache = mutableMapOf<LocalDate, String>()

    /**
     * Build a hash of the actual input data (no timestamps, no AI output).
     * If this hash matches what we last generated from, we can skip the AI call.
     */
    private fun computeDataHash(log: DailyLog?, meals: List<MealEntry>, goal: Goal?): String {
        val parts = mutableListOf<String>()
        if (log != null) {
            parts += "w=${log.weightKg}"
            parts += "s=${log.steps}"
            parts += "sl=${log.sleepHours}"
            parts += "hr=${log.restingHr}"
            parts += "ex=${log.exercisesJson}"
            parts += "off=${log.offPlan}"
            parts += "notes=${log.notes}"
        }
        meals.sortedBy { it.id }.forEach { m ->
            parts += "m:${m.description}|${m.totalKcal}|${m.totalProteinG}|${m.totalCarbsG}|${m.totalFatG}|${m.category}|${m.mealType}|${m.itemsJson}"
        }
        if (goal != null) {
            parts += "g:${goal.targetKg}|${goal.rateKgPerWeek}|${goal.dailyDeficitKcal}"
        }
        return parts.joinToString(";").hashCode().toString(16)
    }

    /**
     * Generate and persist a day summary for [date].
     * Silently no-ops if no API key is configured or if the day has no data.
     * Skips AI call if input data hash matches the last generation.
     */
    suspend fun generateForDate(date: LocalDate, reason: String = "unknown") {
        appLogger.hc("DaySummary requested for $date — reason: $reason")
        try {
            if (!openAiService.hasApiKey()) {
                appLogger.hc("DaySummary skipped for $date — no API key")
                return
            }

            val log = dailyLogDao.getForDate(date)
            val meals = mealDao.getMealsForDate(date).first()
            val goal = goalDao.getCurrentGoal().first()

            // Skip if there's nothing to summarize
            if (log == null && meals.isEmpty()) {
                appLogger.hc("DaySummary skipped for $date — no data")
                return
            }

            // Check data hash — skip if input data hasn't changed since last generation
            val dataHash = computeDataHash(log, meals, goal)
            val cachedHash = dataHashCache[date]
            if (cachedHash == dataHash && log?.daySummary != null && log.daySummary != "⏳") {
                appLogger.hc("DaySummary skipped for $date — data unchanged (hash=$dataHash)")
                return
            }

            appLogger.hc("DaySummary calling AI for $date (meals=${meals.size}, hasLog=${log != null}, hash=$dataHash, prevHash=$cachedHash)")
            val prompt = buildPrompt(date, log, meals, goal)
            val result = openAiService.chat(prompt, SUMMARY_SYSTEM_PROMPT)

            result.onSuccess { summary ->
                val trimmed = summary.trim().removeSurrounding("\"")
                if (trimmed.isNotBlank()) {
                    val existing = log ?: DailyLog(date = date)
                    dailyLogDao.upsert(existing.copy(daySummary = trimmed))
                    dataHashCache[date] = dataHash
                    appLogger.hc("DaySummary generated for $date: ${trimmed.take(60)}… (hash=$dataHash)")
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
    suspend fun generateForDates(dates: List<LocalDate>, reason: String = "unknown") {
        appLogger.hc("DaySummary batch requested for ${dates.size} dates — reason: $reason")
        dates.forEach { generateForDate(it, reason) }
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
            val totalProtein = meals.sumOf { it.totalProteinG }
            val totalCarbs = meals.sumOf { it.totalCarbsG }
            val totalFat = meals.sumOf { it.totalFatG }
            val macroStr = buildString {
                if (totalProtein > 0) append(", ${totalProtein}g protein")
                if (totalCarbs > 0) append(", ${totalCarbs}g carbs")
                if (totalFat > 0) append(", ${totalFat}g fat")
            }
            parts += "Meals logged: ${meals.size} (total $totalKcal kcal$macroStr)"
            meals.forEach { m ->
                val macroPart = buildString {
                    if (m.totalProteinG > 0) append(", ${m.totalProteinG}g P")
                    if (m.totalCarbsG > 0) append(", ${m.totalCarbsG}g C")
                    if (m.totalFatG > 0) append(", ${m.totalFatG}g F")
                }
                val desc = "${m.description.take(50)} — ${m.totalKcal} kcal$macroPart"
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
