package com.fatlosstrack.domain

/**
 * TDEE (Total Daily Energy Expenditure) calculator using the Mifflin-St Jeor equation.
 *
 * BMR formula:
 *   Male:   (10 × weight_kg) + (6.25 × height_cm) - (5 × age) + 5
 *   Female: (10 × weight_kg) + (6.25 × height_cm) - (5 × age) - 161
 *   "Yes":  average of male & female = (10 × weight_kg) + (6.25 × height_cm) - (5 × age) - 78
 *
 * TDEE = BMR × activity multiplier
 * Daily target = TDEE - daily deficit
 */
object TdeeCalculator {

    private val activityMultipliers = mapOf(
        "sedentary" to 1.2f,    // Little/no exercise
        "light" to 1.375f,      // Light exercise 1-3 days/week
        "moderate" to 1.55f,    // Moderate exercise 3-5 days/week
        "active" to 1.725f,     // Hard exercise 6-7 days/week
    )

    /**
     * Calculate BMR using Mifflin-St Jeor equation.
     * @param sex "male", "female", or "yes"
     */
    fun bmr(weightKg: Float, heightCm: Int, age: Int, sex: String): Int {
        val base = (10 * weightKg) + (6.25f * heightCm) - (5 * age)
        val offset = when (sex.lowercase()) {
            "male" -> 5f
            "female" -> -161f
            else -> -78f // "yes" — average of male & female
        }
        return (base + offset).toInt()
    }

    /**
     * Calculate TDEE = BMR × activity multiplier.
     */
    fun tdee(weightKg: Float, heightCm: Int, age: Int, sex: String, activityLevel: String): Int {
        val bmrVal = bmr(weightKg, heightCm, age, sex)
        val multiplier = activityMultipliers[activityLevel] ?: 1.375f
        return (bmrVal * multiplier).toInt()
    }

    /**
     * Calculate daily calorie target = TDEE - deficit.
     * @param weeklyRateKg kg loss per week. Deficit ≈ rate × 1100 kcal.
     */
    fun dailyTarget(
        weightKg: Float,
        heightCm: Int,
        age: Int,
        sex: String,
        activityLevel: String,
        weeklyRateKg: Float,
    ): Int {
        val tdeeVal = tdee(weightKg, heightCm, age, sex, activityLevel)
        val deficit = (weeklyRateKg * 1100).toInt()
        return (tdeeVal - deficit).coerceAtLeast(1200) // floor at 1200 kcal for safety
    }

    /**
     * Derive daily macro targets from calorie target using standard split:
     * 30% protein, 40% carbs, 30% fat.
     *
     * @return Triple(proteinG, carbsG, fatG)
     */
    fun macroTargets(dailyTargetKcal: Int): Triple<Int, Int, Int> {
        val proteinKcal = dailyTargetKcal * 0.30
        val carbsKcal = dailyTargetKcal * 0.40
        val fatKcal = dailyTargetKcal * 0.30
        return Triple(
            (proteinKcal / 4).toInt(),  // 4 kcal per gram protein
            (carbsKcal / 4).toInt(),    // 4 kcal per gram carbs
            (fatKcal / 9).toInt(),      // 9 kcal per gram fat
        )
    }
}
