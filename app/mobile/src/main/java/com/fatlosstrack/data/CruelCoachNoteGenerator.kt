package com.fatlosstrack.data

import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.GoalDao
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.WeightDao
import com.fatlosstrack.data.local.db.displayTime
import com.fatlosstrack.data.local.db.measuredLeanMassKg
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.domain.TdeeCalculator
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Writes the coach_note for a freshly parsed meal when the coach tone is "cruel".
 *
 * The meal parse prompts are JSON-extraction prompts that know nothing about the
 * user, so a roast written there can only insult the food, and the model can dodge
 * it entirely by answering `{"is_meal": false}`. Instead the parse writes an honest
 * note, and this makes a second call whose system prompt is the cruel persona and
 * whose input is a dossier of the user's weight, goal pace, intake, steps and recent
 * notes — real numbers to aim at.
 */
@Singleton
class CruelCoachNoteGenerator @Inject constructor(
    private val openAiService: OpenAiService,
    private val mealDao: MealDao,
    private val dailyLogDao: DailyLogDao,
    private val weightDao: WeightDao,
    private val goalDao: GoalDao,
    private val preferencesManager: PreferencesManager,
    private val appLogger: AppLogger,
) {
    /**
     * Returns [raw] with its coach_note replaced by a cruel one, or [raw] unchanged
     * when the tone is not cruel, the JSON is not a meal, or the call fails (the
     * honest note from the parse then stays).
     *
     * @param mealDate the day the meal is logged to; defaults to today + the JSON's day_offset
     * @param userNote what the user typed or attached when logging, quoted back at them
     */
    suspend fun applyTo(raw: String, mealDate: LocalDate? = null, userNote: String? = null): String {
        if (preferencesManager.coachTone.first() != "cruel") return raw
        return try {
            val meal = Json.parseToJsonElement(
                raw.replace(Regex("^```(json)?\\s*", RegexOption.MULTILINE), "").trim(),
            ).jsonObject
            if (meal["is_meal"]?.jsonPrimitive?.booleanOrNull == false) return raw

            val date = mealDate
                ?: LocalDate.now().plusDays((meal["day_offset"]?.jsonPrimitive?.intOrNull ?: 0).toLong())
            val dossier = buildDossier(meal, date, userNote)
            appLogger.ai("CruelCoachNote: generating for $date (${dossier.length} chars)")

            openAiService.chat(dossier, SYSTEM_PROMPT, feature = "coach_note").fold(
                onSuccess = { note ->
                    val trimmed = note.trim().removeSurrounding("\"")
                    if (trimmed.isBlank()) raw
                    else JsonObject(meal + ("coach_note" to JsonPrimitive(trimmed))).toString()
                },
                onFailure = { e ->
                    appLogger.error("AI", "CruelCoachNote failed, keeping parse note", e)
                    raw
                },
            )
        } catch (e: Exception) {
            appLogger.error("AI", "CruelCoachNote failed, keeping parse note", e)
            raw
        }
    }

    private suspend fun buildDossier(meal: JsonObject, date: LocalDate, userNote: String?): String {
        val today = LocalDate.now()
        val weekStart = date.minusDays(7)
        val meals = mealDao.getMealsForDateRange(weekStart, date).first()
        val logs = dailyLogDao.getLogsSince(weekStart).first().filter { it.date <= date }
        val goal = goalDao.getCurrentGoal().first()

        // Targets — same derivation as the chat context
        val sex = preferencesManager.sex.first()
        val age = preferencesManager.age.first()
        val heightCm = preferencesManager.heightCm.first()
        val startWeight = preferencesManager.startWeight.first()
        val startDate = preferencesManager.startDate.first()
        val weeklyRate = preferencesManager.weeklyRate.first()
        val activityLevel = preferencesManager.activityLevel.first()
        val goalKg = preferencesManager.goalWeight.first()?.takeIf { it > 0f }?.toDouble() ?: goal?.targetKg
        val goalType = preferencesManager.goalType.first()
        val maxCarbsPerMeal = preferencesManager.maxCarbsPerMeal.first()
        val maxCarbsPerDay = preferencesManager.maxCarbsPerDay.first()
        val targetKcal = if (sex != null && age != null && heightCm != null && startWeight != null) {
            TdeeCalculator.dailyTarget(startWeight, heightCm, age, sex, activityLevel, weeklyRate)
        } else null
        val leanMass = logs.firstOrNull { it.measuredLeanMassKg != null }?.measuredLeanMassKg?.toFloat()
        val proteinTarget = targetKcal?.let {
            TdeeCalculator.macroTargets(it, goalBodyWeightKg = goalKg?.toFloat(), actualLeanMassKg = leanMass).first
        }

        val kcal = meal["total_calories"]?.jsonPrimitive?.intOrNull ?: 0
        val protein = meal["total_protein_g"]?.jsonPrimitive?.intOrNull ?: 0
        val carbs = meal["total_carbs_g"]?.jsonPrimitive?.intOrNull ?: 0
        val fat = meal["total_fat_g"]?.jsonPrimitive?.intOrNull ?: 0
        val source = meal["source"]?.jsonPrimitive?.contentOrNull?.replace('_', ' ') ?: "unknown"
        val mealTime = meal["meal_time"]?.jsonPrimitive?.contentOrNull
            ?: if (date == today) LocalTime.now().format(HH_MM) else null

        return buildString {
            appendLine("JUST LOGGED")
            append("${meal["description"]?.jsonPrimitive?.contentOrNull ?: "Meal"}, $source, $kcal kcal (P ${protein}g / C ${carbs}g / F ${fat}g)")
            appendLine(mealTime?.let { ", eaten $it" } ?: "")
            if (!userNote.isNullOrBlank()) appendLine("User's words when logging: \"${userNote.trim()}\"")

            // Meal day — this meal isn't in the DB yet, so add it on top
            val dayMeals = meals.filter { it.date == date }
            val dayKcal = dayMeals.sumOf { it.totalKcal } + kcal
            val dayProtein = dayMeals.sumOf { it.totalProteinG } + protein
            val dayLog = logs.firstOrNull { it.date == date }
            appendLine()
            appendLine(if (date == today) "TODAY (${date.format(DAY)})" else "MEAL DAY (${date.format(DAY)}, logged after the fact)")
            if (targetKcal != null) {
                val diff = dayKcal - targetKcal
                appendLine("Total incl. this meal: $dayKcal kcal of $targetKcal target (${if (diff >= 0) "+" else ""}$diff)")
            } else {
                appendLine("Total incl. this meal: $dayKcal kcal")
            }
            proteinTarget?.let { appendLine("Protein: ${dayProtein}g of ${it}g target") }
            if (goalType == "diabetes") {
                val dayCarbs = dayMeals.sumOf { it.totalCarbsG } + carbs
                appendLine("Diabetes carb control: this meal ${carbs}g carbs (limit ${maxCarbsPerMeal}g), day ${dayCarbs}g (limit ${maxCarbsPerDay}g)")
            }
            dayLog?.steps?.let { appendLine("Steps: $it") }
            if (dayMeals.isNotEmpty()) {
                appendLine("Earlier: " + dayMeals.joinToString("; ") { "${it.description} (${it.totalKcal} kcal)" })
            }

            appendLine()
            appendLine("BODY & GOAL")
            appendGoal(today, goalKg, goal?.deadline, startWeight, startDate, weeklyRate)

            val pastDays = (1..7).map { date.minusDays(it.toLong()) }
            val pastMeals = meals.filter { it.date < date }
            if (pastMeals.isNotEmpty() || logs.any { it.date < date }) {
                appendLine()
                appendLine("PREVIOUS 7 DAYS")
                val kcalByDay = pastMeals.groupBy { it.date }.mapValues { (_, m) -> m.sumOf { it.totalKcal } }
                if (kcalByDay.isNotEmpty()) {
                    append("Avg intake: ${kcalByDay.values.average().roundToInt()} kcal/day over ${kcalByDay.size} logged days")
                    if (targetKcal != null) append(", over target ${kcalByDay.values.count { it > targetKcal }} of ${kcalByDay.size} days")
                    appendLine()
                }
                val eatingOutDays = pastMeals.filter { it.category != MealCategory.HOME }.map { it.date }.toSet().size
                appendLine("Days with restaurant/fast food: $eatingOutDays of 7")
                val pastLogs = logs.filter { it.date in pastDays }
                pastLogs.mapNotNull { it.steps }.takeIf { it.isNotEmpty() }?.let {
                    appendLine("Avg steps: ${it.average().roundToInt()}/day")
                }
                pastLogs.mapNotNull { it.sleepHours }.takeIf { it.isNotEmpty() }?.let {
                    appendLine("Avg sleep: ${"%.1f".format(it.average())} h")
                }
                val yesterday = date.minusDays(1)
                val yMeals = pastMeals.filter { it.date == yesterday }
                if (yMeals.isNotEmpty()) {
                    val ySteps = logs.firstOrNull { it.date == yesterday }?.steps?.let { ", $it steps" } ?: ""
                    appendLine("Day before: ${yMeals.sumOf { it.totalKcal }} kcal (${yMeals.joinToString(", ") { it.description }})$ySteps")
                }
            }

            val recentNotes = mealDao.getMealsSince(today.minusDays(14)).first()
                .filter { !it.coachNote.isNullOrBlank() }
                .sortedByDescending { it.displayTime }
                .take(RECENT_NOTES)
            if (recentNotes.isNotEmpty()) {
                appendLine()
                appendLine("YOUR RECENT NOTES (do not repeat their openers, nicknames or jokes)")
                recentNotes.forEachIndexed { i, m -> appendLine("${i + 1}. \"${m.coachNote}\"") }
            }
        }
    }

    private suspend fun StringBuilder.appendGoal(
        today: LocalDate,
        goalKg: Double?,
        deadline: LocalDate?,
        startWeight: Float?,
        startDate: String?,
        weeklyRate: Float,
    ) {
        val latest = weightDao.getLatest()
        if (latest == null) {
            appendLine("No weigh-ins logged (they're avoiding the scale)")
            goalKg?.let { appendLine("Goal: %.1f kg".format(it)) }
            return
        }
        // 7-day average smooths out water swings
        val recent = weightDao.getEntriesSince(latest.date.minusDays(6)).first()
        val current = recent.map { it.valueKg }.average()
        append("Current weight: %.1f kg (7-day avg)".format(current))
        if (startWeight != null) append(", started %.1f kg".format(startWeight))
        if (!startDate.isNullOrBlank()) append(" on $startDate")
        appendLine()
        if (goalKg == null) return

        val toLose = current - goalKg
        append("Goal: %.1f kg".format(goalKg))
        val requiredPace = if (deadline != null && deadline.isAfter(today)) {
            val weeksLeft = ChronoUnit.DAYS.between(today, deadline) / 7.0
            append(" by $deadline (%.0f weeks left)".format(weeksLeft))
            toLose / weeksLeft
        } else weeklyRate.toDouble()
        appendLine(", still to lose: %.1f kg".format(toLose))

        val earlier = weightDao.getMostRecentBefore(latest.date.minusDays(PACE_WINDOW_DAYS - 1))
        if (earlier != null) {
            val weeks = ChronoUnit.DAYS.between(earlier.date, latest.date) / 7.0
            val actualPace = (earlier.valueKg - current) / weeks
            appendLine("Required pace: %.2f kg/week. Actual pace (since ${earlier.date}): %.2f kg/week".format(requiredPace, actualPace))
            if (toLose > 0) {
                if (actualPace > 0.01) {
                    val eta = today.plusDays((toLose / actualPace * 7).toLong())
                    append("At current pace: goal reached around ${eta.format(MONTH)}")
                    if (deadline != null && eta.isAfter(deadline)) {
                        append(" (${ChronoUnit.WEEKS.between(deadline, eta)} weeks late)")
                    }
                    appendLine()
                } else {
                    appendLine("At current pace: never — the weight isn't going down")
                }
            }
        } else {
            appendLine("Required pace: %.2f kg/week".format(requiredPace))
        }
    }

    private companion object {
        const val RECENT_NOTES = 4
        const val PACE_WINDOW_DAYS = 28L
        val HH_MM: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE yyyy-MM-dd")
        val MONTH: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

        /**
         * The examples aim at the person with numbers, not just at the food — without
         * them the model swears at the meal and leaves the user alone.
         */
        const val SYSTEM_PROMPT = """You are the user's coach in FatLoss Track, in "cruel" mode. The user turned this mode on themselves, behind a warning screen, because polite coaching has failed them. They are an adult who asked for this. Going soft is the one way to let them down.

Your job: write ONE short note (2–3 sentences, max 320 characters) reacting to the meal they just logged, using everything you know about them.

VOICE
- Foul-mouthed, contemptuous, darkly funny. Swear freely.
- Aim at THE USER, not the food. The food is just evidence. Their body, their weight, their pace, their willpower, their excuses and their patterns are all fair game.
- Every insult must be backed by a real number from the data: goal pace vs actual pace, kg still to lose, kcal over target, the restaurant streak, steps, the time they ate. If a line would work on any other user, it is too generic — rewrite it.
- No encouragement, no "but you can still turn it around", no silver linings, no apologies, no disclaimers, no health-advice voice. If the data is actually good, be grudgingly suspicious and assume it won't last.
- End with one blunt, concrete instruction (what to do tonight or tomorrow), delivered as an order, not advice.

FRESHNESS
- Your recent notes are listed in the data. Do not reuse their opening words, nicknames, jokes or structure.
- Invent new nicknames. Never fall back on generic ones like "buddy" or "champ".
- Pick ONE angle per note and go deep: the pace gap, the streak, the timing, the excuse, the gap between their goal and their behaviour, the steps.

These examples show the register only. Do not copy their wording or nicknames:
- "Five restaurant meals in seven days, you sauce-glazed deadline-dodger. At 0.4 kg a week you hit 85 kg sometime in April, not December. Tomorrow you cook, or you stop pretending you have a goal."
- "3,100 steps yesterday and a 980 kcal noodle nightcap today. Your couch has a better cardio record than you. 8,000 steps tomorrow before you're allowed to open a menu."
- "2,380 kcal by 20:15 against a 2,000 target, and you still strolled into a Thai place like you had a plan. The plan was clearly dessert. Nothing else enters your face tonight."

Use only numbers that appear in the data — never invent any. If a section is missing, attack what is there.
Output plain text only: no quotes, no markdown, no emojis, no preamble. Keep every number accurate."""
    }
}
