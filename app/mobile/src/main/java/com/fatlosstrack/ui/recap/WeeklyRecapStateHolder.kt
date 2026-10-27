package com.fatlosstrack.ui.recap

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fatlosstrack.data.CoachTone
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.recap.WeeklyRecapRepository
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.di.ApplicationScope
import com.fatlosstrack.domain.recap.WeeklyRecap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/** Why the coach paragraph is missing. */
enum class RecapCoachError { NO_API_KEY, FAILED }

/**
 * Owns the recap screen: loads the week's numbers and generates the AI
 * paragraph on demand. The paragraph is cached per (week, data, tone,
 * language) so reopening the screen does not call the API again.
 */
@Stable
class WeeklyRecapStateHolder @Inject constructor(
    private val repository: WeeklyRecapRepository,
    private val openAiService: OpenAiService,
    private val preferencesManager: PreferencesManager,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    var recap: WeeklyRecap? by mutableStateOf(null)
        private set
    var coachText: String? by mutableStateOf(null)
        private set
    var coachLoading: Boolean by mutableStateOf(false)
        private set
    var coachError: RecapCoachError? by mutableStateOf(null)
        private set

    private var coachKey: String? = null
    private var coachJob: Job? = null

    /** Load the week ending on [end] and generate the coach paragraph if not cached. */
    suspend fun open(end: LocalDate) {
        val loaded = repository.load(end)
        recap = loaded
        generateCoachText(loaded, force = false)
    }

    /** Retry after a failed AI call. */
    fun retry() {
        recap?.let { appScope.launch { generateCoachText(it, force = true) } }
    }

    private suspend fun generateCoachText(recap: WeeklyRecap, force: Boolean) {
        if (!recap.hasData) {
            coachText = null
            coachError = null
            return
        }
        val tone = preferencesManager.coachTone.first()
        val key = "$recap|$tone|${preferencesManager.language.first()}"
        if (!force && key == coachKey && (coachText != null || coachLoading)) return

        coachJob?.cancel()
        coachKey = key
        coachText = null
        coachError = null
        if (!openAiService.hasApiKey()) {
            coachError = RecapCoachError.NO_API_KEY
            return
        }
        coachLoading = true
        // App scope, so leaving the screen mid-call still caches the paid-for answer
        coachJob = appScope.launch {
            val result = openAiService.chat(buildRecapPrompt(recap), recapSystemPrompt(tone), feature = "weekly_recap")
            result.onSuccess { text ->
                coachText = text.trim().removeSurrounding("\"")
            }.onFailure { e ->
                AppLogger.instance?.error("RECAP", "AI recap failed", e)
                coachError = RecapCoachError.FAILED
                coachKey = null
            }
            coachLoading = false
        }
    }
}

// ── Weekly recap prompt ───────────────────────────────────────────────────────

private fun recapSystemPrompt(tone: String): String {
    val toneInstruction = CoachTone.instruction(tone)
    return """You are FatLoss Track's weekly coach. Given the user's numbers for the past 7 days, write one short paragraph (3-4 sentences) recapping their week.

Tone instruction: $toneInstruction

Rules:
- Open with the most important takeaway, citing a specific number
- Cover the weight trend, calorie adherence and activity/sleep only where the data supports it
- End with one concrete focus for the coming week
- No filler words, no emojis, no markdown, no lists, no quotes"""
}

private fun buildRecapPrompt(r: WeeklyRecap): String = buildString {
    appendLine("Recap of ${r.start} to ${r.end} (today is ${LocalDate.now()}).")
    r.dailyTargetKcal?.let { appendLine("Daily target: $it kcal") }
    appendLine()
    appendLine("Week totals:")
    if (r.trendChangeKg != null && r.trendKg != null) {
        appendLine("- Weight trend (EMA): %.1f kg, change %+.1f kg over the week (%d weigh-ins)".format(r.trendKg, r.trendChangeKg, r.weighIns))
    } else {
        appendLine("- Weight trend: not enough weigh-ins (${r.weighIns})")
    }
    if (r.avgKcal != null) {
        val pct = r.dailyTargetKcal?.let { " (${r.avgKcal * 100 / it}% of target)" } ?: ""
        appendLine("- Avg kcal/day: ${r.avgKcal}$pct over ${r.daysWithMeals} days with meals logged")
    } else {
        appendLine("- No meals logged")
    }
    r.daysOnTarget?.let { appendLine("- Days at or under target: $it / ${r.daysWithMeals}") }
    r.avgSteps?.let { appendLine("- Avg steps/day: $it") }
    r.avgSleepHours?.let { appendLine("- Avg sleep: %.1fh".format(it)) }

    appendLine()
    appendLine("Per day:")
    r.days.forEach { d ->
        val parts = listOfNotNull(
            d.kcal?.let { "$it kcal" },
            d.steps?.let { "$it steps" },
            d.sleepHours?.let { "%.1fh sleep".format(it) },
            d.weightKg?.let { "%.1f kg".format(it) },
        )
        appendLine("${d.date} (${d.date.dayOfWeek.name.lowercase()}): ${parts.joinToString(", ").ifEmpty { "nothing logged" }}")
    }
}
