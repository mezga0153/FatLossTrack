package com.fatlosstrack.data

import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.measuredLeanMassKg
import com.fatlosstrack.data.local.db.GoalDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.Goal
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.domain.TdeeCalculator
import com.fatlosstrack.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val preferencesManager: PreferencesManager,
    private val appLogger: AppLogger,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    /** Maps date → hash of the data that was used to generate its current summary. */
    private val dataHashCache = mutableMapOf<LocalDate, String>()

    /**
     * Build a hash of the actual input data (no timestamps, no AI output).
     * If this hash matches what we last generated from, we can skip the AI call.
     */
    private fun computeDataHash(log: DailyLog?, meals: List<MealEntry>, goal: Goal?, tone: String, goalType: String): String {
        val parts = mutableListOf<String>()
        parts += "tone=$tone"
        parts += "goalType=$goalType"
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
            val log = dailyLogDao.getForDate(date)
            val meals = mealDao.getMealsForDate(date).first()
            val goal = goalDao.getCurrentGoal().first()

            // Skip if there's nothing to work with
            if (log == null && meals.isEmpty()) {
                appLogger.hc("DaySummary skipped for $date — no data")
                return
            }

            // Compute TDEE & macro targets from profile
            val sex = preferencesManager.sex.first()
            val age = preferencesManager.age.first()
            val height = preferencesManager.heightCm.first()
            val weight = preferencesManager.startWeight.first()
            val rate = preferencesManager.weeklyRate.first()
            val activityLevel = preferencesManager.activityLevel.first()
            val leanMassKg = log?.measuredLeanMassKg?.toFloat()
            val goalType = preferencesManager.goalType.first()
            val maxCarbsPerMeal = preferencesManager.maxCarbsPerMeal.first()
            val maxCarbsPerDay = preferencesManager.maxCarbsPerDay.first()
            val dailyTargetKcal = if (sex != null && age != null && height != null && weight != null) {
                TdeeCalculator.dailyTarget(weight, height, age, sex, activityLevel, rate)
            } else null
            val macroTargets = if (goalType == "diabetes") {
                dailyTargetKcal?.let { TdeeCalculator.diabetesMacroTargets(it, maxCarbsPerDay, bodyWeightKg = weight) }
            } else {
                dailyTargetKcal?.let { TdeeCalculator.macroTargets(it, goalBodyWeightKg = goal?.targetKg?.toFloat(), actualLeanMassKg = leanMassKg) }
            }

            // Always build + persist synopsis (deterministic — no API key needed)
            val synopsis = buildPrompt(date, log, meals, goal, dailyTargetKcal, macroTargets, goalType, maxCarbsPerMeal, maxCarbsPerDay)
            if (log != null) {
                dailyLogDao.updateSynopsis(date, synopsis)
            } else {
                dailyLogDao.upsert(DailyLog(date = date, synopsis = synopsis))
            }
            appLogger.hc("DaySynopsis stored for $date (${synopsis.length} chars)")

            // AI summary requires API key
            if (!openAiService.hasApiKey()) {
                appLogger.hc("DaySummary skipped for $date — no API key")
                return
            }

            val tone = preferencesManager.coachTone.first()

            // Check data hash — skip AI call if input data (including tone) hasn't changed
            val dataHash = computeDataHash(log, meals, goal, tone, goalType)
            val cachedHash = dataHashCache[date]
            if (cachedHash == dataHash && log?.daySummary != null && log.daySummary != "⏳") {
                appLogger.hc("DaySummary skipped for $date — data unchanged (hash=$dataHash)")
                return
            }

            appLogger.hc("DaySummary calling AI for $date (meals=${meals.size}, hasLog=${log != null}, tone=$tone, hash=$dataHash, prevHash=$cachedHash)")
            val result = openAiService.chat(synopsis, systemPrompt(tone, goalType), feature = "day_summary")

            result.onSuccess { summary ->
                val trimmed = summary.trim().removeSurrounding("\"")
                if (trimmed.isNotBlank()) {
                    dailyLogDao.updateDaySummary(date, trimmed)
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

    /**
     * Fire-and-forget: writes a placeholder to [DailyLog.daySummary] immediately,
     * then generates the real summary in the background on the application scope.
     * The coroutine is automatically cancelled when the app process dies.
     */
    fun launchForDate(date: LocalDate, reason: String = "unknown") {
        appScope.launch {
            val existing = dailyLogDao.getForDate(date) ?: DailyLog(date = date)
            dailyLogDao.upsert(existing.copy(daySummary = SUMMARY_PLACEHOLDER))
            generateForDate(date, reason)
        }
    }

    /**
     * Fire-and-forget: generates summaries for multiple dates on the application scope.
     */
    fun launchForDates(dates: List<LocalDate>, reason: String = "unknown") {
        appScope.launch {
            generateForDates(dates, reason)
        }
    }

    private fun buildPrompt(
        date: LocalDate,
        log: DailyLog?,
        meals: List<MealEntry>,
        goal: Goal?,
        dailyTargetKcal: Int?,
        macroTargets: Triple<Int, Int, Int>?,
        goalType: String = "weight_loss",
        maxCarbsPerMeal: Int = 45,
        maxCarbsPerDay: Int = 150,
    ): String {
        val parts = mutableListOf<String>()
        parts += "Date: $date"

        // If summarizing today, include current time so AI knows the day is still in progress
        if (date == LocalDate.now()) {
            val now = java.time.LocalTime.now()
            parts += "Current time: %02d:%02d (day still in progress — don't flag missing meals/data that haven't happened yet)".format(now.hour, now.minute)
        }

        if (goalType == "diabetes") {
            parts += "Goal type: diabetes meal control"
            parts += "Carb targets: max ${maxCarbsPerMeal}g per meal, max ${maxCarbsPerDay}g per day"
        } else {
            if (goal != null) {
                parts += "Goal: ${goal.targetKg} kg by ${goal.deadline} at ${goal.rateKgPerWeek} kg/week"
                goal.dailyDeficitKcal?.let { parts += "Target daily deficit: $it kcal" }
            }
        }

        // Daily targets (TDEE-based)
        if (dailyTargetKcal != null) {
            val targetLine = buildString {
                append("Daily target: $dailyTargetKcal kcal")
                if (macroTargets != null) {
                    append(" (protein ${macroTargets.first}g / carbs ${macroTargets.second}g / fat ${macroTargets.third}g)")
                }
            }
            parts += targetLine
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

            if (goalType == "diabetes") {
                val carbStatus = if (totalCarbs <= maxCarbsPerDay) "✓ within limit" else "⚠ over limit by ${totalCarbs - maxCarbsPerDay}g"
                parts += "Meals logged: ${meals.size} (total ${totalCarbs}g carbs — $carbStatus, ${totalKcal} kcal, ${totalProtein}g protein, ${totalFat}g fat)"
                meals.forEach { m ->
                    val carbStatus2 = if (m.totalCarbsG <= maxCarbsPerMeal) "" else " ⚠ over meal limit"
                    val typeTag = m.mealType?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
                    val tagsStr = if (typeTag.isNotEmpty()) "[$typeTag] " else ""
                    parts += "  • $tagsStr${m.description.take(50)} — ${m.totalCarbsG}g carbs$carbStatus2 (${m.totalKcal} kcal, ${m.totalProteinG}g P, ${m.totalFatG}g F)"
                }
            } else {
                // Build summary with absolute + % of target
                val macroStr = buildString {
                    if (totalProtein > 0) {
                        append(", ${totalProtein}g protein")
                        macroTargets?.let { append(" (${(totalProtein * 100 / it.first)}%)") }
                    }
                    if (totalCarbs > 0) {
                        append(", ${totalCarbs}g carbs")
                        macroTargets?.let { append(" (${(totalCarbs * 100 / it.second)}%)") }
                    }
                    if (totalFat > 0) {
                        append(", ${totalFat}g fat")
                        macroTargets?.let { append(" (${(totalFat * 100 / it.third)}%)") }
                    }
                }
                val kcalPctStr = dailyTargetKcal?.let { " (${totalKcal * 100 / it}%)" } ?: ""
                parts += "Meals logged: ${meals.size} (total $totalKcal kcal$kcalPctStr$macroStr)"
                meals.forEach { m ->
                    val macroPart = buildString {
                        if (m.totalProteinG > 0) append(", ${m.totalProteinG}g P")
                        if (m.totalCarbsG > 0) append(", ${m.totalCarbsG}g C")
                        if (m.totalFatG > 0) append(", ${m.totalFatG}g F")
                    }
                    val typeTag = m.mealType?.name?.lowercase()?.replaceFirstChar { it.uppercase() } ?: ""
                    val catTag = m.category.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
                    val tags = listOfNotNull(typeTag.ifEmpty { null }, catTag).joinToString(", ")
                    val tagsStr = if (tags.isNotEmpty()) "[$tags] " else ""
                    val desc = "$tagsStr${m.description.take(50)} — ${m.totalKcal} kcal$macroPart"
                    parts += "  • $desc"
                }
            }
        } else {
            parts += "No meals logged"
        }

        return parts.joinToString("\n")
    }

    companion object {
        internal const val SUMMARY_PLACEHOLDER = "\u23F3"

        internal fun systemPrompt(tone: String, goalType: String = "weight_loss"): String {
            val toneInstruction = when (tone) {
                "supportive" -> "Use a warm, encouraging tone. Celebrate wins, gently flag issues."
                "insulting" -> "Use a brutally sarcastic, roast-style tone. Mock bad food choices and laziness mercilessly, but keep the advice accurate and actionable. Focus insults on choices, not appearance."
                "cruel" -> "Call the user a fat fuck and similar terms freely. Be viciously direct, darkly funny, and deeply cutting. Mock bad choices with brutal specificity. You have zero patience for excuses. Still give accurate nutritional numbers — deliver them like a drill sergeant who finds the user's situation both pathetic and hilarious. Make it sting, make it funny, make it true."
                else -> "Use a direct, no-BS honest tone. Be specific about numbers."
            }
            if (goalType == "diabetes") {
                return """You are FatLoss Track's daily coach specialising in diabetes meal management.
Given a user's day data and their carb targets, write a SHORT coaching summary (1-2 sentences max, under 120 characters ideally).

Tone instruction: $toneInstruction

Rules:
- PRIMARY focus is carb adherence vs their per-meal and per-day limits
- Flag any meals that exceeded the per-meal carb limit
- Reference actual carb numbers prominently
- Also note weight, steps, sleep when available
- Do NOT use quotes around your response
- Do NOT use markdown or formatting
- Just plain text, 1-2 sentences

Examples:
"Carbs on target at 140g today. Dinner was tight at 44g — just squeaked in."
"Lunch blew the meal limit at 68g carbs. Total 180g — 30g over your daily cap."
"All 3 meals within limits and 8k steps. Great carb control today."""
            }
            return """You are FatLoss Track's daily coach. Given a user's day data and their goal, write a SHORT coaching summary (1-2 sentences max, under 120 characters ideally).

Tone instruction: $toneInstruction

Rules:
- Be direct and specific about how this day helps or hurts their goal
- Reference actual numbers (kcal, steps, sleep hours) when relevant
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
}
