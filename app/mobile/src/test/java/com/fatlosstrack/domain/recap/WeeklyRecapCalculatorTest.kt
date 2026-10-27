package com.fatlosstrack.domain.recap

import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyRecapCalculatorTest {

    // Window: 2026-10-19 (Mon) .. 2026-10-25 (Sun)
    private val end = LocalDate.of(2026, 10, 25)
    private val start = end.minusDays(6)

    private fun recap(
        weights: List<WeightEntry> = emptyList(),
        logs: List<DailyLog> = emptyList(),
        meals: List<MealEntry> = emptyList(),
        target: Int? = 2000,
    ) = WeeklyRecapCalculator.calculate(end, weights, logs, meals, target)

    @Test
    fun `window covers the 7 days ending on end`() {
        val r = recap()
        assertEquals(start, r.start)
        assertEquals(end, r.end)
        assertEquals((0L..6L).map { start.plusDays(it) }, r.days.map { it.date })
        assertFalse(r.hasData)
    }

    @Test
    fun `kcal averages over days with meals and counts days at or under target`() {
        val meals = listOf(
            MealEntry(date = start, totalKcal = 1200),
            MealEntry(date = start, totalKcal = 600), // 1800 that day
            MealEntry(date = start.plusDays(1), totalKcal = 2000), // exactly on target
            MealEntry(date = start.plusDays(2), totalKcal = 2600),
            MealEntry(date = start.minusDays(1), totalKcal = 5000), // outside the window
        )
        val r = recap(meals = meals)
        assertEquals(3, r.daysWithMeals)
        assertEquals((1800 + 2000 + 2600) / 3, r.avgKcal)
        assertEquals(2, r.daysOnTarget)
        assertEquals(1800, r.days.first().kcal)
        assertTrue(r.hasData)
    }

    @Test
    fun `days on target is null without a target`() {
        val r = recap(meals = listOf(MealEntry(date = start, totalKcal = 1500)), target = null)
        assertNull(r.daysOnTarget)
        assertEquals(1500, r.avgKcal)
    }

    @Test
    fun `steps and sleep average only the logged days in the window`() {
        val logs = listOf(
            DailyLog(date = start, steps = 8000, sleepHours = 7.0),
            DailyLog(date = start.plusDays(1), steps = 12000),
            DailyLog(date = start.minusDays(3), steps = 30000, sleepHours = 12.0), // before the window
        )
        val r = recap(logs = logs)
        assertEquals(10000, r.avgSteps)
        assertEquals(7.0, r.avgSleepHours!!, 1e-9)
    }

    @Test
    fun `trend change compares the EMA at the end with the EMA before the window`() {
        val weights = listOf(
            WeightEntry(date = start.minusDays(2), valueKg = 80.0),
            WeightEntry(date = start.minusDays(1), valueKg = 80.0),
            WeightEntry(date = start.plusDays(3), valueKg = 79.0),
        )
        val r = recap(weights = weights)
        assertEquals(1, r.weighIns)
        // EMA of [80, 80, 79] with alpha = 0.5 → 79.5; before the window it is flat 80
        assertEquals(79.5, r.trendKg!!, 1e-9)
        assertEquals(-0.5, r.trendChangeKg!!, 1e-9)
    }

    @Test
    fun `trend change is null without a weigh-in in the window`() {
        val weights = listOf(WeightEntry(date = start.minusDays(1), valueKg = 80.0))
        val r = recap(weights = weights)
        assertNull(r.trendChangeKg)
        assertNull(r.trendKg)
        assertEquals(0, r.weighIns)
    }

    @Test
    fun `without history the trend is measured from the first weigh-in in the window`() {
        val weights = listOf(
            WeightEntry(date = start, valueKg = 81.0),
            WeightEntry(date = end, valueKg = 80.0),
        )
        val r = recap(weights = weights)
        // EMA of [81, 80] with alpha = 2/3 → 80.333…
        assertEquals(80.0 + 1.0 / 3, r.trendKg!!, 1e-9)
        assertEquals(-2.0 / 3, r.trendChangeKg!!, 1e-9)
    }

    @Test
    fun `a single weigh-in with no history gives no trend change`() {
        val r = recap(weights = listOf(WeightEntry(date = start, valueKg = 81.0)))
        assertNull(r.trendChangeKg)
        assertTrue(r.hasData)
    }

    @Test
    fun `daily log weight wins over a raw entry on the same day`() {
        val weights = listOf(WeightEntry(date = end, valueKg = 90.0))
        val logs = listOf(DailyLog(date = end, weightKg = 80.0))
        val r = recap(weights = weights, logs = logs)
        assertEquals(80.0, r.days.last().weightKg!!, 1e-9)
        assertEquals(1, r.weighIns)
    }

    @Test
    fun `weigh-ins after end are ignored`() {
        val weights = listOf(
            WeightEntry(date = end, valueKg = 80.0),
            WeightEntry(date = end.plusDays(1), valueKg = 70.0),
        )
        val r = recap(weights = weights)
        assertEquals(80.0, r.trendKg!!, 1e-9)
    }
}
