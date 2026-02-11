package com.fatlosstrack.domain

import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.WeightDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds a context block describing the user's recent data (last 7 days)
 * for inclusion in AI chat prompts. Extracted from ChatScreen to keep
 * the composable free of direct DAO calls.
 */
@Singleton
class ChatContextUseCase @Inject constructor(
    private val dailyLogDao: DailyLogDao,
    private val mealDao: MealDao,
    private val weightDao: WeightDao,
    private val preferencesManager: PreferencesManager,
) {
    suspend fun build(): String {
        val today = LocalDate.now()
        val sevenDaysAgo = today.minusDays(7)
        val fmt = DateTimeFormatter.ISO_LOCAL_DATE

        // Goal & profile
        val startWeight = preferencesManager.startWeight.first()
        val goalKg = preferencesManager.goalWeight.first()
        val goalRate = preferencesManager.weeklyRate.first()
        val guidance = preferencesManager.aiGuidance.first()
        val coachTone = preferencesManager.coachTone.first()
        val heightCm = preferencesManager.heightCm.first()
        val startDate = preferencesManager.startDate.first()
        val sex = preferencesManager.sex.first()
        val age = preferencesManager.age.first()
        val activityLevel = preferencesManager.activityLevel.first()

        // Weight entries (last 7 days)
        val weights = weightDao.getEntriesSince(sevenDaysAgo).first()

        // Daily logs (last 7 days)
        val logs = dailyLogDao.getLogsSince(sevenDaysAgo).first()

        // Meals (last 7 days)
        val meals = mealDao.getMealsSince(sevenDaysAgo).first()

        val sb = StringBuilder()
        sb.appendLine("=== USER CONTEXT (last 7 days) ===")
        sb.appendLine("Today: ${fmt.format(today)}")

        // Goal & profile info
        val goalParts = mutableListOf<String>()
        if (startWeight != null && startWeight > 0f) goalParts += "start=${startWeight}kg"
        if (goalKg != null && goalKg > 0f) goalParts += "target=${goalKg}kg"
        goalParts += "rate=${goalRate}kg/week"
        if (heightCm != null) goalParts += "height=${heightCm}cm"
        if (!startDate.isNullOrBlank()) goalParts += "since=$startDate"
        sb.appendLine("Goal: ${goalParts.joinToString(", ")}")
        sb.appendLine("Coach tone: $coachTone")
        if (guidance.isNotBlank()) {
            sb.appendLine("User guidance/preferences: $guidance")
        }

        // Daily targets (TDEE-based)
        val dailyTargetKcal = if (sex != null && age != null && heightCm != null && startWeight != null) {
            TdeeCalculator.dailyTarget(startWeight, heightCm, age, sex, activityLevel, goalRate)
        } else null
        val macroTargets = dailyTargetKcal?.let { TdeeCalculator.macroTargets(it) }
        if (dailyTargetKcal != null && macroTargets != null) {
            sb.appendLine("Daily target: $dailyTargetKcal kcal (protein ${macroTargets.first}g / carbs ${macroTargets.second}g / fat ${macroTargets.third}g)")
        }

        if (weights.isNotEmpty()) {
            sb.appendLine("\nWeights:")
            weights.sortedByDescending { it.date }.forEach { w ->
                sb.appendLine("  ${fmt.format(w.date)}: ${w.valueKg} kg")
            }
        }

        if (logs.isNotEmpty()) {
            sb.appendLine("\nDaily logs:")
            logs.sortedByDescending { it.date }.forEach { log ->
                val parts = mutableListOf<String>()
                log.weightKg?.let { parts += "weight=${it}kg" }
                log.steps?.let { parts += "steps=$it" }
                log.sleepHours?.let { parts += "sleep=${it}h" }
                log.restingHr?.let { parts += "restHR=$it" }
                if (log.offPlan) parts += "OFF-PLAN"
                sb.appendLine("  ${fmt.format(log.date)}: ${parts.joinToString(", ")}")
            }
        }

        if (meals.isNotEmpty()) {
            sb.appendLine("\nMeals:")
            val mealsByDate = meals.groupBy { it.date }.toSortedMap(compareByDescending { it })
            mealsByDate.forEach { (date, dayMeals) ->
                val totalKcal = dayMeals.sumOf { it.totalKcal }
                val totalP = dayMeals.sumOf { it.totalProteinG }
                val totalC = dayMeals.sumOf { it.totalCarbsG }
                val totalF = dayMeals.sumOf { it.totalFatG }
                val pctStr = buildString {
                    if (dailyTargetKcal != null) append(" [${totalKcal * 100 / dailyTargetKcal}% kcal")
                    if (macroTargets != null) {
                        if (totalP > 0) append(", ${totalP * 100 / macroTargets.first}% P")
                        if (totalC > 0) append(", ${totalC * 100 / macroTargets.second}% C")
                        if (totalF > 0) append(", ${totalF * 100 / macroTargets.third}% F")
                    }
                    if (dailyTargetKcal != null) append("]")
                }
                sb.appendLine("  ${fmt.format(date)}: ${totalKcal} kcal, ${totalP}g P, ${totalC}g C, ${totalF}g F$pctStr")
                dayMeals.forEach { m ->
                    val prot = if (m.totalProteinG > 0) ", ${m.totalProteinG}g P" else ""
                    val carb = if (m.totalCarbsG > 0) ", ${m.totalCarbsG}g C" else ""
                    val fat = if (m.totalFatG > 0) ", ${m.totalFatG}g F" else ""
                    sb.appendLine("    - ${m.description} (${m.totalKcal} kcal$prot$carb$fat)")
                }
            }
        }

        sb.appendLine("=== END CONTEXT ===")
        return sb.toString()
    }
}
