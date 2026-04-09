package com.fatlosstrack.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.BookmarkedMealDao
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject

/** Snapshot of period statistics fed into the AI summary prompt. */
data class PeriodStats(
    val lookbackDays: Int,
    val startDate: LocalDate?,
    val daysSinceStart: Int?,
    val goalWeight: Float?,
    val startWeight: Float?,
    val latestWeight: Double?,
    val weeklyRate: Float?,
    val dailyTargetKcal: Int?,
    val totalMeals: Int,
    val avgKcalPerDay: Int?,
    val avgProteinPerDay: Int?,
    val avgCarbsPerDay: Int?,
    val avgFatPerDay: Int?,
    val avgSteps: Int?,
    val avgSleep: Double?,
    val daysLogged: Int,
    val weights: List<Double>,
    val logCount: Int,
    val meals: List<MealEntry> = emptyList(),
    val dailyLogs: List<DailyLog> = emptyList(),
    val fingerprint: String,
)

/** Module-level cache: (fingerprint, text, generatedAtMs). Survives recomposition & navigation. */
private data class PeriodSummaryCache(val fingerprint: String?, val text: String?, val generatedAtMs: Long)
private var periodSummaryCache = PeriodSummaryCache(null, null, 0L)
private const val PERIOD_SUMMARY_COOLDOWN_MS = 30 * 60 * 1000L // 30 minutes

/**
 * Owns business logic for [HomeScreen]: period summary AI call + caching,
 * and provides DAO/service access for data collection.
 */
@Stable
class HomeStateHolder @Inject constructor(
    private val _dailyLogDao: DailyLogDao,
    private val _mealDao: MealDao,
    private val _weightDao: WeightDao,
    private val _preferencesManager: PreferencesManager,
    private val _openAiService: OpenAiService,
    private val _daySummaryGenerator: DaySummaryGenerator,
    private val _bookmarkedMealDao: BookmarkedMealDao,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    // ── Preference flows for composable collection ──
    val goalWeight get() = _preferencesManager.goalWeight
    val startWeight get() = _preferencesManager.startWeight
    val weeklyRate get() = _preferencesManager.weeklyRate
    val startDate get() = _preferencesManager.startDate

    // ── DAO Flow accessors (composable collects via collectAsState) ──
    fun logsSince(since: LocalDate) = _dailyLogDao.getLogsSince(since)
    fun mealsSince(since: LocalDate) = _mealDao.getMealsSince(since)
    fun weightsSince(since: LocalDate) = _weightDao.getEntriesSince(since)
    fun allWeightEntries() = _weightDao.getAllEntries()

    // ── Passthrough for components that still need direct access ──
    val preferencesManager get() = _preferencesManager
    val mealDao get() = _mealDao
    val dailyLogDao get() = _dailyLogDao
    val daySummaryGenerator get() = _daySummaryGenerator
    val openAiService get() = _openAiService
    val bookmarkedMealDao get() = _bookmarkedMealDao

    // ── Period summary state ──
    var periodSummary: String? by mutableStateOf(periodSummaryCache.text)
        private set
    var periodSummaryLoading: Boolean by mutableStateOf(false)
        private set
    private var lastFingerprint: String? = periodSummaryCache.fingerprint

    /**
     * Generate (or serve from cache) the AI period summary.
     * - Same fingerprint + generated <30 min ago → serve from cache, no AI call.
     * - Same fingerprint but stale → show cached text immediately and refresh in background.
     * - New fingerprint → show cached text (if any) immediately and refresh in background.
     * The loading spinner is only shown when there is no existing cached text at all.
     */
    /** Bust the module-level cache so the next generatePeriodSummary() call triggers an AI refresh. */
    fun invalidatePeriodSummaryCache() {
        periodSummaryCache = PeriodSummaryCache(null, null, 0L)
        lastFingerprint = null
        periodSummary = null
    }

    fun generatePeriodSummary(stats: PeriodStats) {
        val fp = stats.fingerprint
        val nowMs = System.currentTimeMillis()
        val cache = periodSummaryCache

        // In-memory: same fingerprint, already displayed — no-op
        if (fp == lastFingerprint && periodSummary != null) {
            AppLogger.instance?.hc("PeriodSummary: in-memory hit ($fp), skipping")
            return
        }

        // Module-level cache hit: same fingerprint, still fresh
        if (fp == cache.fingerprint && cache.text != null && (nowMs - cache.generatedAtMs) < PERIOD_SUMMARY_COOLDOWN_MS) {
            val ageMin = (nowMs - cache.generatedAtMs) / 60_000
            AppLogger.instance?.hc("PeriodSummary: fresh cache hit (age=${ageMin}min, fp=$fp), skipping")
            periodSummary = cache.text
            lastFingerprint = fp
            return
        }

        if (stats.logCount == 0) {
            AppLogger.instance?.hc("PeriodSummary: skipped (no logs)")
            return
        }

        // If we have any cached text (stale or different fingerprint), show it immediately — no spinner
        if (cache.text != null && periodSummary == null) {
            periodSummary = cache.text
        }
        val showSpinner = periodSummary == null
        if (showSpinner) periodSummaryLoading = true

        val ageMin = if (cache.generatedAtMs > 0) (nowMs - cache.generatedAtMs) / 60_000 else -1L
        AppLogger.instance?.hc("PeriodSummary: refreshing (fp=$lastFingerprint→$fp, age=${ageMin}min, hasCache=${cache.text != null})")

        appScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (!_openAiService.hasApiKey()) {
                        AppLogger.instance?.hc("PeriodSummary: skipped — no API key")
                        if (showSpinner) periodSummaryLoading = false
                        return@withContext
                    }
                    val tone = _preferencesManager.coachTone.first()
                    val prompt = buildPeriodPrompt(stats)
                    val result = _openAiService.chat(prompt, periodSummarySystemPrompt(tone), feature = "period_summary")
                    result.onSuccess { summary ->
                        val trimmed = summary.trim().removeSurrounding("\"")
                        periodSummary = trimmed
                        lastFingerprint = fp
                        periodSummaryCache = PeriodSummaryCache(fp, trimmed, System.currentTimeMillis())
                        AppLogger.instance?.hc("PeriodSummary: AI returned ${trimmed.take(60)}…")
                    }.onFailure { e ->
                        AppLogger.instance?.error("PeriodSummary", "AI call failed", e)
                    }
                } catch (_: Exception) { }
                if (showSpinner) periodSummaryLoading = false
            }
        }
    }
}

// ── Period summary prompt ─────────────────────────────────────────────────────

private fun periodSummarySystemPrompt(tone: String): String {
    val toneInstruction = when (tone) {
        "supportive" -> "Use a warm, encouraging tone. Celebrate wins and gently suggest improvements."
        "insulting" -> "Use a brutally sarcastic, roast-style tone. Mock bad choices and inaction mercilessly, but keep the advice accurate and actionable."
        "cruel" -> "Call the user a fat fuck and similar terms freely. Be viciously direct, darkly funny, and deeply cutting. Mock bad choices with brutal specificity. You have zero patience for excuses. Make it sting, make it funny, make it true."
        else -> "Use a direct, no-BS honest tone. Be specific about numbers."
    }
    return """You are FatLoss Track's weekly coach. Given the user's data, write a 2-sentence coaching summary.

Tone instruction: $toneInstruction

Rules:
- Sentence 1: one key observation with a specific number (kcal, protein, weight, fast food count, etc.)
- Sentence 2: one concrete action for the next period
- No filler words, no emojis, no markdown, no quotes"""
}

private fun buildPeriodPrompt(s: PeriodStats): String = buildString {
    val today = LocalDate.now()
    appendLine("Summarize the user's last ${s.lookbackDays} days of progress toward their fat loss goal.")
    if (s.startDate != null) appendLine("Goal start date: ${s.startDate} (${s.daysSinceStart ?: 0} days ago)")
    appendLine("Today: $today")
    s.goalWeight?.let { appendLine("Goal weight: %.1f kg".format(it)) }
    s.startWeight?.let { appendLine("Start weight: %.1f kg".format(it)) }
    s.latestWeight?.let { appendLine("Current weight: %.1f kg".format(it)) }
    s.weeklyRate?.let { appendLine("Target rate: %.1f kg/week".format(it)) }

    val mt = s.dailyTargetKcal?.let { com.fatlosstrack.domain.TdeeCalculator.macroTargets(it) }
    if (s.dailyTargetKcal != null && mt != null) {
        appendLine("Daily target: ${s.dailyTargetKcal} kcal (protein ${mt.first}g / carbs ${mt.second}g / fat ${mt.third}g)")
    }

    appendLine()
    appendLine("Period averages (last ${s.lookbackDays} days, excluding today):")
    appendLine("- Meals logged: ${s.totalMeals}")
    if (s.avgKcalPerDay != null) {
        val kcalPct = if (s.dailyTargetKcal != null) " (${s.avgKcalPerDay * 100 / s.dailyTargetKcal}% of target)" else ""
        appendLine("- Avg kcal/day: ${s.avgKcalPerDay}$kcalPct")
    }
    if (s.avgProteinPerDay != null && s.avgProteinPerDay > 0) {
        val pct = mt?.let { " (${s.avgProteinPerDay * 100 / it.first}% of target)" } ?: ""
        appendLine("- Avg protein/day: ${s.avgProteinPerDay}g$pct")
    }
    if (s.avgCarbsPerDay != null && s.avgCarbsPerDay > 0) {
        val pct = mt?.let { " (${s.avgCarbsPerDay * 100 / it.second}% of target)" } ?: ""
        appendLine("- Avg carbs/day: ${s.avgCarbsPerDay}g$pct")
    }
    if (s.avgFatPerDay != null && s.avgFatPerDay > 0) {
        val pct = mt?.let { " (${s.avgFatPerDay * 100 / it.third}% of target)" } ?: ""
        appendLine("- Avg fat/day: ${s.avgFatPerDay}g$pct")
    }
    if (s.avgSteps != null) appendLine("- Avg steps/day: ${s.avgSteps}")
    if (s.avgSleep != null) appendLine("- Avg sleep: %.1fh".format(s.avgSleep))
    appendLine("- Days with data: ${s.daysLogged} / ${s.lookbackDays}")
    if (s.weights.size >= 2) {
        appendLine("- Weight change: %.1f → %.1f kg".format(s.weights.first(), s.weights.last()))
    }

    // ── Per-day detail: use pre-computed synopses when available ──
    val synopsized = s.dailyLogs.filter { !it.synopsis.isNullOrBlank() }.sortedBy { it.date }
    if (synopsized.isNotEmpty()) {
        appendLine()
        appendLine("Per-day synopses:")
        synopsized.forEach { log ->
            appendLine()
            appendLine(log.synopsis!!)
        }
    } else if (s.meals.isNotEmpty()) {
        // Fallback: rebuild from raw meals when synopses haven't been generated yet

        // Meal category distribution
        val home = s.meals.count { it.category == MealCategory.HOME }
        val restaurant = s.meals.count { it.category == MealCategory.RESTAURANT }
        val fastFood = s.meals.count { it.category == MealCategory.FAST_FOOD }
        val total = s.meals.size
        appendLine()
        appendLine("Meal category breakdown ($total meals):")
        appendLine("- Home-cooked: $home (${home * 100 / total}%)")
        if (restaurant > 0) appendLine("- Restaurant: $restaurant (${restaurant * 100 / total}%)")
        if (fastFood > 0) appendLine("- Fast food: $fastFood (${fastFood * 100 / total}%)")

        // Per-day breakdown
        val mealsByDate = s.meals.groupBy { it.date }.entries.sortedBy { it.key }
        appendLine()
        appendLine("Per-day breakdown:")
        for ((date, dayMeals) in mealsByDate) {
            val dailyKcal = dayMeals.sumOf { it.totalKcal }
            val dailyProtein = dayMeals.sumOf { it.totalProteinG }
            val dailyCarbs = dayMeals.sumOf { it.totalCarbsG }
            val dailyFat = dayMeals.sumOf { it.totalFatG }
            val hasMacros = dailyProtein > 0 || dailyCarbs > 0 || dailyFat > 0
            val macros = if (hasMacros) " [P:${dailyProtein}g C:${dailyCarbs}g F:${dailyFat}g]" else ""
            val dayLog = s.dailyLogs.find { it.date == date }
            val steps = dayLog?.steps?.let { " | ${it} steps" } ?: ""
            appendLine("$date: $dailyKcal kcal$macros$steps")
            dayMeals.take(4).forEach { meal ->
                val typeLabel = meal.mealType?.name?.lowercase() ?: "meal"
                appendLine("  • [$typeLabel] ${meal.description.take(45)} — ${meal.totalKcal} kcal")
            }
        }
    }

    // ── Existing day summaries (if available) ──
    val summaries = s.dailyLogs
        .filter { !it.daySummary.isNullOrBlank() && it.daySummary != "\u23F3" }
        .sortedBy { it.date }
    if (summaries.isNotEmpty()) {
        appendLine()
        appendLine("AI day summaries already generated for this period:")
        summaries.forEach { log -> appendLine("${log.date}: ${log.daySummary}") }
    }
}
