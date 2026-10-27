package com.fatlosstrack.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TdeeCalculatorTest {

    // Reference person: male, 80 kg, 180 cm, 30 y
    // BMR = 10*80 + 6.25*180 - 5*30 + 5 = 800 + 1125 - 150 + 5 = 1780
    private val refWeight = 80f
    private val refHeight = 180
    private val refAge = 30

    private fun kcalOf(macros: Triple<Int, Int, Int>): Int =
        macros.first * 4 + macros.second * 4 + macros.third * 9

    // ── BMR ──────────────────────────────────────────────────────────────

    @Test
    fun `bmr for male uses plus 5 offset`() {
        assertEquals(1780, TdeeCalculator.bmr(refWeight, refHeight, refAge, "male"))
    }

    @Test
    fun `bmr for female uses minus 161 offset and truncates the fraction`() {
        // 10*65 + 6.25*165 - 5*28 - 161 = 650 + 1031.25 - 140 - 161 = 1380.25 -> 1380
        assertEquals(1380, TdeeCalculator.bmr(65f, 165, 28, "female"))
    }

    @Test
    fun `bmr for yes uses minus 78 average offset and truncates the fraction`() {
        // 10*70 + 6.25*170 - 5*35 - 78 = 700 + 1062.5 - 175 - 78 = 1509.5 -> 1509
        assertEquals(1509, TdeeCalculator.bmr(70f, 170, 35, "yes"))
    }

    @Test
    fun `bmr truncates rather than rounds fractional results`() {
        // 10*80.5 + 6.25*181 - 5*30 + 5 = 805 + 1131.25 - 150 + 5 = 1791.25 -> 1791
        assertEquals(1791, TdeeCalculator.bmr(80.5f, 181, 30, "male"))
        // 10*72.3 + 6.25*175 - 5*40 + 5 = 723 + 1093.75 - 200 + 5 = 1621.75 -> 1621 (not 1622)
        assertEquals(1621, TdeeCalculator.bmr(72.3f, 175, 40, "male"))
    }

    @Test
    fun `bmr sex matching is case-insensitive`() {
        assertEquals(1780, TdeeCalculator.bmr(refWeight, refHeight, refAge, "MALE"))
        assertEquals(1780, TdeeCalculator.bmr(refWeight, refHeight, refAge, "Male"))
        assertEquals(1380, TdeeCalculator.bmr(65f, 165, 28, "FEMALE"))
        assertEquals(1380, TdeeCalculator.bmr(65f, 165, 28, "Female"))
        assertEquals(1509, TdeeCalculator.bmr(70f, 170, 35, "YES"))
    }

    @Test
    fun `bmr for an unrecognised sex falls back to the yes offset`() {
        val yes = TdeeCalculator.bmr(70f, 170, 35, "yes")
        assertEquals(yes, TdeeCalculator.bmr(70f, 170, 35, "other"))
        assertEquals(yes, TdeeCalculator.bmr(70f, 170, 35, ""))
    }

    @Test
    fun `bmr male and female differ by 166 kcal for the same body`() {
        val male = TdeeCalculator.bmr(refWeight, refHeight, refAge, "male")
        val female = TdeeCalculator.bmr(refWeight, refHeight, refAge, "female")
        assertEquals(166, male - female)
    }

    // ── Activity multipliers / TDEE ──────────────────────────────────────

    @Test
    fun `multiplierFor returns the conservative multiplier for each level`() {
        assertEquals(1.2f, TdeeCalculator.multiplierFor("sedentary"), 0f)
        assertEquals(1.35f, TdeeCalculator.multiplierFor("light"), 0f)
        assertEquals(1.5f, TdeeCalculator.multiplierFor("moderate"), 0f)
        assertEquals(1.6f, TdeeCalculator.multiplierFor("active"), 0f)
    }

    @Test
    fun `multiplierFor falls back to 1_35 for an unknown level`() {
        assertEquals(1.35f, TdeeCalculator.multiplierFor("extreme"), 0f)
        assertEquals(1.35f, TdeeCalculator.multiplierFor(""), 0f)
    }

    @Test
    fun `tdee is bmr times the activity multiplier, truncated`() {
        // BMR 1780
        assertEquals(2136, TdeeCalculator.tdee(refWeight, refHeight, refAge, "male", "sedentary")) // 1780*1.2
        assertEquals(2403, TdeeCalculator.tdee(refWeight, refHeight, refAge, "male", "light"))     // 1780*1.35
        assertEquals(2670, TdeeCalculator.tdee(refWeight, refHeight, refAge, "male", "moderate"))  // 1780*1.5
        assertEquals(2848, TdeeCalculator.tdee(refWeight, refHeight, refAge, "male", "active"))    // 1780*1.6
    }

    @Test
    fun `tdee truncates the fractional part`() {
        // "yes" BMR 1509; 1509 * 1.35 = 2037.15 -> 2037, 1509 * 1.2 = 1810.8 -> 1810
        assertEquals(2037, TdeeCalculator.tdee(70f, 170, 35, "yes", "light"))
        assertEquals(1810, TdeeCalculator.tdee(70f, 170, 35, "yes", "sedentary"))
    }

    @Test
    fun `tdee uses the 1_35 fallback multiplier for an unknown level`() {
        assertEquals(
            TdeeCalculator.tdee(refWeight, refHeight, refAge, "male", "light"),
            TdeeCalculator.tdee(refWeight, refHeight, refAge, "male", "couch potato"),
        )
        assertEquals(2403, TdeeCalculator.tdee(refWeight, refHeight, refAge, "male", "couch potato"))
    }

    // ── Daily target ─────────────────────────────────────────────────────

    @Test
    fun `dailyTarget subtracts rate times 1100 from tdee`() {
        // TDEE (moderate) = 2670
        assertEquals(2670, TdeeCalculator.dailyTarget(refWeight, refHeight, refAge, "male", "moderate", 0f))
        assertEquals(2670 - 550, TdeeCalculator.dailyTarget(refWeight, refHeight, refAge, "male", "moderate", 0.5f))
        assertEquals(2670 - 825, TdeeCalculator.dailyTarget(refWeight, refHeight, refAge, "male", "moderate", 0.75f))
        assertEquals(2670 - 1100, TdeeCalculator.dailyTarget(refWeight, refHeight, refAge, "male", "moderate", 1.0f))
    }

    @Test
    fun `dailyTarget is floored at 1200 kcal`() {
        // female 50 kg, 155 cm, 60 y: BMR = 500 + 968.75 - 300 - 161 = 1007.75 -> 1007
        // sedentary TDEE = 1007 * 1.2 = 1208.4 -> 1208; minus 1100 = 108 -> floored to 1200
        assertEquals(1200, TdeeCalculator.dailyTarget(50f, 155, 60, "female", "sedentary", 1.0f))
        // Even an extreme rate never goes below the floor
        assertEquals(1200, TdeeCalculator.dailyTarget(refWeight, refHeight, refAge, "male", "active", 5f))
    }

    @Test
    fun `dailyTarget above the floor is not clamped`() {
        // 1208 - 0 = 1208 stays as-is
        assertEquals(1208, TdeeCalculator.dailyTarget(50f, 155, 60, "female", "sedentary", 0f))
    }

    // ── Macro targets: lean-mass path ────────────────────────────────────

    @Test
    fun `macroTargets sets protein at 2_2 g per kg of measured lean mass`() {
        assertEquals(132, TdeeCalculator.macroTargets(2000, actualLeanMassKg = 60f).first)  // 60 * 2.2
        assertEquals(110, TdeeCalculator.macroTargets(2000, actualLeanMassKg = 50f).first)  // 50 * 2.2
        assertEquals(139, TdeeCalculator.macroTargets(2000, actualLeanMassKg = 63.5f).first) // 139.7 -> 139
    }

    @Test
    fun `macroTargets estimates lean mass as 75 percent of goal weight`() {
        // goal 80 kg -> lean 60 kg -> protein 132 g
        assertEquals(132, TdeeCalculator.macroTargets(2000, goalBodyWeightKg = 80f).first)
        // goal 100 kg -> lean 75 kg -> protein 165 g
        assertEquals(165, TdeeCalculator.macroTargets(2000, goalBodyWeightKg = 100f).first)
    }

    @Test
    fun `macroTargets prefers measured lean mass over goal weight estimate`() {
        // goal 100 kg would give 165 g; measured 60 kg lean mass gives 132 g
        val macros = TdeeCalculator.macroTargets(2000, goalBodyWeightKg = 100f, actualLeanMassKg = 60f)
        assertEquals(132, macros.first)
        assertEquals(TdeeCalculator.macroTargets(2000, actualLeanMassKg = 60f), macros)
    }

    @Test
    fun `macroTargets splits remaining kcal 55 percent carbs with fat filling the rest`() {
        // protein 132 g = 528 kcal; remaining 1472
        // carbs = 1472 * 0.55 / 4 = 202.4 -> 202 g (808 kcal)
        // fat = (1472 - 808) / 9 = 73.8 -> 73 g
        assertEquals(Triple(132, 202, 73), TdeeCalculator.macroTargets(2000, actualLeanMassKg = 60f))
    }

    @Test
    fun `macroTargets lean-mass path lands a few kcal below the target, never above`() {
        val leanMasses = listOf(35f, 42.7f, 50f, 55.5f, 60f, 63.5f, 71.2f, 80f)
        for (target in 1200..3500 step 37) {
            for (lean in leanMasses) {
                val macros = TdeeCalculator.macroTargets(target, actualLeanMassKg = lean)
                val gap = target - kcalOf(macros)
                assertTrue(
                    "target=$target lean=$lean macros=$macros gap=$gap",
                    gap in 0..8,
                )
            }
        }
    }

    @Test
    fun `macroTargets coerces remaining kcal to zero when protein exceeds the target`() {
        // lean 100 kg -> protein 220 g = 880 kcal > 500 kcal target
        val macros = TdeeCalculator.macroTargets(500, actualLeanMassKg = 100f)
        assertEquals(Triple(220, 0, 0), macros)
    }

    // ── Macro targets: flat split ────────────────────────────────────────

    @Test
    fun `macroTargets uses flat 30-40-30 split without lean mass or goal weight`() {
        // protein 600 kcal / 4 = 150, carbs 800 / 4 = 200, fat 600 / 9 = 66.7 -> 66
        assertEquals(Triple(150, 200, 66), TdeeCalculator.macroTargets(2000))
        // protein 540 / 4 = 135, carbs 720 / 4 = 180, fat 540 / 9 = 60
        assertEquals(Triple(135, 180, 60), TdeeCalculator.macroTargets(1800))
    }

    @Test
    fun `macroTargets flat split stays at or below the target`() {
        for (target in 1200..3500 step 37) {
            val macros = TdeeCalculator.macroTargets(target)
            val gap = target - kcalOf(macros)
            assertTrue("target=$target macros=$macros gap=$gap", gap in 0..16)
        }
    }

    @Test
    fun `macroTargets with zero or negative lean mass falls back to flat split`() {
        val flat = TdeeCalculator.macroTargets(2000)
        assertEquals(flat, TdeeCalculator.macroTargets(2000, actualLeanMassKg = 0f))
        assertEquals(flat, TdeeCalculator.macroTargets(2000, actualLeanMassKg = -5f))
        assertEquals(flat, TdeeCalculator.macroTargets(2000, goalBodyWeightKg = 0f))
        assertEquals(flat, TdeeCalculator.macroTargets(2000, goalBodyWeightKg = -10f))
    }

    @Test
    fun `macroTargets with zero measured lean mass does not fall back to goal weight`() {
        // Current behaviour: only a null measured lean mass defers to the goal-weight estimate.
        val flat = TdeeCalculator.macroTargets(2000)
        assertEquals(flat, TdeeCalculator.macroTargets(2000, goalBodyWeightKg = 80f, actualLeanMassKg = 0f))
    }

    // ── Diabetes macro targets ───────────────────────────────────────────

    @Test
    fun `diabetesMacroTargets caps carbs at maxCarbsPerDay`() {
        // 45% cap = 2000 * 0.45 / 4 = 225 g; maxCarbsPerDay 100 is lower
        // remaining = 2000 - 400 = 1600; protein = 80 * 1.6 = 128 g (512 kcal)
        // fat = (1600 - 512) / 9 = 120.9 -> 120 g
        assertEquals(Triple(128, 100, 120), TdeeCalculator.diabetesMacroTargets(2000, 100, bodyWeightKg = 80f))
    }

    @Test
    fun `diabetesMacroTargets caps carbs at 45 percent of kcal when maxCarbsPerDay is generous`() {
        // 45% cap = 1600 * 0.45 / 4 = 180 g < 300 g
        val macros = TdeeCalculator.diabetesMacroTargets(1600, 300, bodyWeightKg = 80f)
        assertEquals(180, macros.second)
        // 2000 kcal -> 225 g cap
        assertEquals(225, TdeeCalculator.diabetesMacroTargets(2000, 500).second)
    }

    @Test
    fun `diabetesMacroTargets sets protein at 1_6 g per kg of body weight when given`() {
        assertEquals(128, TdeeCalculator.diabetesMacroTargets(2000, 100, bodyWeightKg = 80f).first)  // 80 * 1.6
        assertEquals(152, TdeeCalculator.diabetesMacroTargets(2000, 100, bodyWeightKg = 95f).first)  // 95 * 1.6
        assertEquals(116, TdeeCalculator.diabetesMacroTargets(2000, 100, bodyWeightKg = 72.5f).first) // 72.5 * 1.6
    }

    @Test
    fun `diabetesMacroTargets without body weight sets protein at 45 percent of remaining kcal`() {
        // carbs capped at 180 g (720 kcal); remaining 880
        // protein = 880 * 0.45 / 4 = 99 g (396 kcal); fat = (880 - 396) / 9 = 53.8 -> 53 g
        assertEquals(Triple(99, 180, 53), TdeeCalculator.diabetesMacroTargets(1600, 300))
        // carbs 100 g (400 kcal); remaining 1600
        // protein = 1600 * 0.45 / 4 = 180 g (720 kcal); fat = (1600 - 720) / 9 = 97.8 -> 97 g
        assertEquals(Triple(180, 100, 97), TdeeCalculator.diabetesMacroTargets(2000, 100))
    }

    @Test
    fun `diabetesMacroTargets fat is never negative when protein exceeds remaining kcal`() {
        // carbs 50 g (200 kcal); remaining 1000; protein = 200 * 1.6 = 320 g (1280 kcal) > 1000
        val macros = TdeeCalculator.diabetesMacroTargets(1200, 50, bodyWeightKg = 200f)
        assertEquals(Triple(320, 50, 0), macros)
    }

    @Test
    fun `diabetesMacroTargets stays at or below the target when protein fits`() {
        for (target in 1200..3500 step 37) {
            for (maxCarbs in listOf(50, 100, 150, 400)) {
                for (weight in listOf(null, 60f, 80f, 100f)) {
                    val macros = TdeeCalculator.diabetesMacroTargets(target, maxCarbs, weight)
                    val gap = target - kcalOf(macros)
                    assertTrue("target=$target maxCarbs=$maxCarbs weight=$weight macros=$macros gap=$gap", gap in 0..8)
                    assertTrue(macros.third >= 0)
                }
            }
        }
    }
}
