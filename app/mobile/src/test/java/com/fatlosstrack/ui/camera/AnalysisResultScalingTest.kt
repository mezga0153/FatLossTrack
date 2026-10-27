package com.fatlosstrack.ui.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class AnalysisResultScalingTest {

    private val base = AnalysisResult(
        description = "Chicken and rice",
        items = listOf(
            MealItem(
                name = "Chicken",
                portion = "150g",
                nutrition = listOf(
                    NutritionRow("Calories", "248", "kcal"),
                    NutritionRow("Protein", "46", "g"),
                    NutritionRow("Fat", "5", "g"),
                    NutritionRow("Carbs", "0", "g"),
                ),
            ),
        ),
        totalCalories = 508,
        totalProteinG = 51,
        totalCarbsG = 57,
        totalFatG = 6,
        aiNote = "",
    )

    @Test
    fun `scaledBy scales items and totals`() {
        val scaled = base.scaledBy(1.5)
        assertEquals(762, scaled.totalCalories)
        assertEquals(77, scaled.totalProteinG) // 76.5 -> 77
        assertEquals(86, scaled.totalCarbsG) // 85.5 -> 86
        assertEquals(9, scaled.totalFatG)
        assertEquals("150g ×1.5", scaled.items[0].portion)
        assertEquals("372", scaled.items[0].nutrition[0].amount)
        assertEquals("kcal", scaled.items[0].nutrition[0].unit)
        assertEquals("Chicken and rice", scaled.description)
    }

    @Test
    fun `scaledBy leaves the base untouched and 1x returns it`() {
        base.scaledBy(2.0)
        assertEquals("150g", base.items[0].portion)
        assertEquals(508, base.totalCalories)
        assertSame(base, base.scaledBy(1.0))
    }
}
