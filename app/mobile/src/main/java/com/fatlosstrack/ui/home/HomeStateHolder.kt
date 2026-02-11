package com.fatlosstrack.ui.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
    val fingerprint: String,
)

/** Module-level cache: (dataFingerprint, summary). Survives recomposition & navigation. */
private var periodSummaryCache: Pair<String?, String?> = null to null

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

    // ── Period summary state ──
    var periodSummary: String? by mutableStateOf(periodSummaryCache.second)
        private set
    var periodSummaryLoading: Boolean by mutableStateOf(false)
        private set
    private var lastFingerprint: String? = periodSummaryCache.first

    /**
     * Generate (or serve from cache) the AI period summary.
     * Call from a LaunchedEffect keyed on [PeriodStats.fingerprint].
     */
    fun generatePeriodSummary(stats: PeriodStats) {
        val fp = stats.fingerprint
        // Local in-memory cache hit
        if (fp == lastFingerprint && periodSummary != null) {
            AppLogger.instance?.hc("PeriodSummary: fingerprint unchanged ($fp), skipping")
            return
        }
        // Module-level cache hit
        if (fp == periodSummaryCache.first && periodSummaryCache.second != null) {
            AppLogger.instance?.hc("PeriodSummary: using module-level cache (fingerprint=$fp)")
            periodSummary = periodSummaryCache.second
            lastFingerprint = fp
            return
        }
        if (stats.logCount == 0) {
            AppLogger.instance?.hc("PeriodSummary: skipped (no logs)")
            return
        }

        AppLogger.instance?.hc("PeriodSummary: fingerprint changed ($lastFingerprint → $fp), calling AI")
        periodSummaryLoading = true

        appScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    if (!_openAiService.hasApiKey()) {
                        AppLogger.instance?.hc("PeriodSummary: skipped — no API key")
                        periodSummaryLoading = false
                        return@withContext
                    }
                    val prompt = buildPeriodPrompt(stats)
                    val systemPrompt = PERIOD_SUMMARY_SYSTEM_PROMPT
                    val result = _openAiService.chat(prompt, systemPrompt, feature = "period_summary")
                    result.onSuccess { summary ->
                        val trimmed = summary.trim().removeSurrounding("\"")
                        periodSummary = trimmed
                        lastFingerprint = fp
                        periodSummaryCache = fp to trimmed
                        AppLogger.instance?.hc("PeriodSummary: AI returned ${trimmed.take(60)}…")
                    }.onFailure { e ->
                        AppLogger.instance?.error("PeriodSummary", "AI call failed", e)
                    }
                } catch (_: Exception) { }
                periodSummaryLoading = false
            }
        }
    }
}

// ── Period summary prompt ─────────────────────────────────────────────────────

private const val PERIOD_SUMMARY_SYSTEM_PROMPT = """You are FatLoss Track's weekly coach. Given a user's multi-day stats and their goal, write a SHORT motivational coaching summary (2-3 sentences, under 200 characters).

Rules:
- Be specific about how these days helped or hurt their goal
- Reference actual numbers when relevant
- Supportive but honest tone
- Plain text only, no markdown, no quotes
- Focus on the trend and what to do next"""

private fun buildPeriodPrompt(s: PeriodStats): String = buildString {
    val today = LocalDate.now()
    appendLine("Summarize the user's last ${s.lookbackDays} days of progress toward their fat loss goal.")
    if (s.startDate != null) appendLine("Goal start date: ${s.startDate} (${s.daysSinceStart ?: 0} days ago)")
    appendLine("Today: $today")
    s.goalWeight?.let { appendLine("Goal weight: %.1f kg".format(it)) }
    s.startWeight?.let { appendLine("Start weight: %.1f kg".format(it)) }
    s.latestWeight?.let { appendLine("Current weight: %.1f kg".format(it)) }
    s.weeklyRate?.let { appendLine("Target rate: %.1f kg/week".format(it)) }

    if (s.dailyTargetKcal != null) {
        val mt = com.fatlosstrack.domain.TdeeCalculator.macroTargets(s.dailyTargetKcal)
        appendLine("Daily target: ${s.dailyTargetKcal} kcal (protein ${mt.first}g / carbs ${mt.second}g / fat ${mt.third}g)")
    }

    appendLine()
    appendLine("Period stats (last ${s.lookbackDays} days, excluding today):")
    appendLine("- Meals logged: ${s.totalMeals}")

    if (s.avgKcalPerDay != null) {
        val kcalPct = if (s.dailyTargetKcal != null) " (${s.avgKcalPerDay * 100 / s.dailyTargetKcal}% of target)" else ""
        appendLine("- Avg kcal/day: ${s.avgKcalPerDay}$kcalPct")
    }
    val mt = s.dailyTargetKcal?.let { com.fatlosstrack.domain.TdeeCalculator.macroTargets(it) }
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
}
