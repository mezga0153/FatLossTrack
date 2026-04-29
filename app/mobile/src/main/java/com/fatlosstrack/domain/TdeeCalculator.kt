package com.fatlosstrack.domain

/**
 * TDEE (Total Daily Energy Expenditure) calculator using the Mifflin-St Jeor equation.
 *
 * BMR formula:
 *   Male:   (10 × weight_kg) + (6.25 × height_cm) - (5 × age) + 5
 *   Female: (10 × weight_kg) + (6.25 × height_cm) - (5 × age) - 161
 *   "Yes":  average of male & female = (10 × weight_kg) + (6.25 × height_cm) - (5 × age) - 78
 *
 * Activity multipliers are intentionally conservative — standard textbook values
 * (1.375-1.725) consistently overestimate TDEE in practice because people
 * overestimate their activity level. These adjusted values are closer to
 * real-world measurements from doubly-labelled water studies.
 *
 * TDEE = BMR × activity multiplier
 * Daily target = TDEE - daily deficit
 */
object TdeeCalculator {

    private val activityMultipliers = mapOf(
        "sedentary" to 1.2f,    // Desk job, little/no exercise
        "light" to 1.35f,       // Light exercise 1-3 days/week (was 1.375)
        "moderate" to 1.5f,     // Moderate exercise 3-5 days/week (was 1.55)
        "active" to 1.6f,       // Hard exercise 6-7 days/week (was 1.725)
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
        val multiplier = activityMultipliers[activityLevel] ?: 1.35f
        return (bmrVal * multiplier).toInt()
    }

    /** Returns the activity multiplier for a given level. */
    fun multiplierFor(activityLevel: String): Float =
        activityMultipliers[activityLevel] ?: 1.35f

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
     * Derive daily macro targets from calorie target.
     *
     * When lean mass is known (from Health Connect or estimation), protein is set at
     * 2.2 g/kg of lean mass — the evidence-based amount for muscle preservation
     * during a calorie deficit. Remaining calories are split 55% carbs / 45% fat.
     *
     * [goalBodyWeightKg]: goal body weight — lean mass is estimated as 75% of this.
     * [actualLeanMassKg]: measured lean mass (e.g. from smart scale via HC). Takes priority.
     *
     * Without either, falls back to the flat percentage split: 30% protein, 40% carbs, 30% fat.
     *
     * @return Triple(proteinG, carbsG, fatG)
     */
    fun macroTargets(dailyTargetKcal: Int, goalBodyWeightKg: Float? = null, actualLeanMassKg: Float? = null): Triple<Int, Int, Int> {
        val leanMass = actualLeanMassKg ?: goalBodyWeightKg?.let { it * 0.75f }
        if (leanMass != null && leanMass > 0f) {
            val proteinG = (leanMass * 2.2f).toInt()
            val proteinKcal = proteinG * 4
            val remaining = (dailyTargetKcal - proteinKcal).coerceAtLeast(0)
            val carbsG = (remaining * 0.55 / 4).toInt()
            val fatG = (remaining * 0.45 / 9).toInt()
            return Triple(proteinG, carbsG, fatG)
        }
        val proteinKcal = dailyTargetKcal * 0.30
        val carbsKcal = dailyTargetKcal * 0.40
        val fatKcal = dailyTargetKcal * 0.30
        return Triple(
            (proteinKcal / 4).toInt(),
            (carbsKcal / 4).toInt(),
            (fatKcal / 9).toInt(),
        )
    }

    /**
     * Macro targets for diabetes meal control.
     * Carbs are capped at [maxCarbsPerDay]. Remaining calories go to protein and fat.
     * Protein is prioritised at 1.6 g/kg of body weight (or 25% of kcal as fallback).
     * @return Triple(proteinG, carbsG, fatG)
     */
    fun diabetesMacroTargets(
        dailyTargetKcal: Int,
        maxCarbsPerDay: Int,
        bodyWeightKg: Float? = null,
    ): Triple<Int, Int, Int> {
        val carbsG = maxCarbsPerDay.coerceAtMost((dailyTargetKcal * 0.45 / 4).toInt()) // never exceed 45% of kcal even if target is generous
        val carbsKcal = carbsG * 4
        val remaining = (dailyTargetKcal - carbsKcal).coerceAtLeast(0)
        val proteinG = bodyWeightKg?.let { (it * 1.6f).toInt() }
            ?: (remaining * 0.45 / 4).toInt()
        val proteinKcal = proteinG * 4
        val fatG = ((remaining - proteinKcal).coerceAtLeast(0) / 9).toInt()
        return Triple(proteinG, carbsG, fatG)
    }
}
