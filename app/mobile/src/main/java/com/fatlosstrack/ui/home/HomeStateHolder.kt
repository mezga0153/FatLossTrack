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

private const val PERIOD_SUMMARY_SYSTEM_PROMPT = """You are FatLoss Track's weekly coach. Given the user's detailed meal and activity data, write a concise coaching summary (3-4 sentences).

Rules:
- Reference specific data points: exact numbers, meal types, patterns you actually see in the data
- If protein is consistently below target, call it out with numbers
- If fast food or restaurant meals are frequent, mention the count
- Note any standout high-calorie days or meals by name/date if visible
- Direct and honest tone — back every statement with the user's actual numbers
- Plain text only, no markdown, no quotes
- End with one actionable recommendation for the next period"""

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

    // ── Meal category distribution ──
    if (s.meals.isNotEmpty()) {
        val home = s.meals.count { it.category == MealCategory.HOME }
        val restaurant = s.meals.count { it.category == MealCategory.RESTAURANT }
        val fastFood = s.meals.count { it.category == MealCategory.FAST_FOOD }
        val total = s.meals.size
        appendLine()
        appendLine("Meal category breakdown ($total meals):")
        appendLine("- Home-cooked: $home (${home * 100 / total}%)")
        if (restaurant > 0) appendLine("- Restaurant: $restaurant (${restaurant * 100 / total}%)")
        if (fastFood > 0) appendLine("- Fast food: $fastFood (${fastFood * 100 / total}%)")
    }

    // ── Meal type patterns ──
    val daysWithMeals = s.meals.map { it.date }.distinct().size
    if (daysWithMeals > 0) {
        val breakfastDays = s.meals.filter { it.mealType == MealType.BREAKFAST }.map { it.date }.distinct().size
        val lunchDays = s.meals.filter { it.mealType == MealType.LUNCH }.map { it.date }.distinct().size
        val dinnerDays = s.meals.filter { it.mealType == MealType.DINNER }.map { it.date }.distinct().size
        val snackDays = s.meals.filter { it.mealType == MealType.SNACK }.map { it.date }.distinct().size
        appendLine()
        appendLine("Meal type patterns (out of $daysWithMeals days with meals):")
        if (breakfastDays > 0) appendLine("- Breakfast: $breakfastDays days")
        if (lunchDays > 0) appendLine("- Lunch: $lunchDays days")
        if (dinnerDays > 0) appendLine("- Dinner: $dinnerDays days")
        if (snackDays > 0) appendLine("- Snack: $snackDays days")
    }

    // ── Per-day meal breakdown ──
    val mealsByDate = s.meals.groupBy { it.date }.entries.sortedBy { it.key }
    if (mealsByDate.isNotEmpty()) {
        appendLine()
        appendLine("Per-day breakdown:")
        for ((date, dayMeals) in mealsByDate) {
            val dailyKcal = dayMeals.sumOf { it.totalKcal }
            val dailyProtein = dayMeals.sumOf { it.totalProteinG }
            val dailyCarbs = dayMeals.sumOf { it.totalCarbsG }
            val dailyFat = dayMeals.sumOf { it.totalFatG }
            val typeStr = dayMeals.mapNotNull { it.mealType?.name?.lowercase() }.distinct().joinToString("+")
            val hasMacros = dailyProtein > 0 || dailyCarbs > 0 || dailyFat > 0
            val macros = if (hasMacros) " [P:${dailyProtein}g C:${dailyCarbs}g F:${dailyFat}g]" else ""
            val dayLog = s.dailyLogs.find { it.date == date }
            val steps = dayLog?.steps?.let { " | ${it} steps" } ?: ""
            appendLine("$date (${dayMeals.size} meal${if (dayMeals.size != 1) "s" else ""}${if (typeStr.isNotEmpty()) ", $typeStr" else ""}): $dailyKcal kcal$macros$steps")
            dayMeals.take(5).forEach { meal ->
                val typeLabel = meal.mealType?.name?.lowercase() ?: "meal"
                val kcalStr = if (meal.totalKcal > 0) " — ${meal.totalKcal} kcal" else ""
                val proteinStr = if (meal.totalProteinG > 0) ", P:${meal.totalProteinG}g" else ""
                appendLine("  • [$typeLabel] ${meal.description.take(45)}$kcalStr$proteinStr")
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
