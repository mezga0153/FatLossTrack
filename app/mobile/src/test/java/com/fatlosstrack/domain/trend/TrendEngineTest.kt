package com.fatlosstrack.domain.trend

import com.fatlosstrack.data.local.db.WeightEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sqrt
import kotlin.random.Random

class TrendEngineTest {

    private val start = LocalDate.of(2026, 1, 1)
    private val eps = 1e-9

    /** One entry per consecutive day starting at [start], oldest first. */
    private fun weights(vararg kg: Double): List<WeightEntry> =
        kg.mapIndexed { i, v -> WeightEntry(date = start.plusDays(i.toLong()), valueKg = v) }

    private fun calc(
        entries: List<WeightEntry>,
        targetKg: Double? = null,
        rateKgPerWeek: Double? = null,
    ): TrendResult = TrendEngine.calculate(entries, targetKg, rateKgPerWeek)!!

    /** Asserts [actual] is [daysFromToday] days after "today", tolerating a midnight rollover during the call. */
    private fun assertProjected(daysFromToday: Long, before: LocalDate, after: LocalDate, actual: LocalDate?) {
        assertNotNull(actual)
        val lo = before.plusDays(daysFromToday)
        val hi = after.plusDays(daysFromToday)
        assertTrue("expected $lo..$hi but was $actual", !actual!!.isBefore(lo) && !actual.isAfter(hi))
    }

    // ── Basics ───────────────────────────────────────────────────────────

    @Test
    fun `empty list returns null`() {
        assertNull(TrendEngine.calculate(emptyList(), 75.0, 0.5))
    }

    @Test
    fun `single entry gives that value, flat direction and zero-width confidence range`() {
        val r = calc(weights(82.4))
        assertEquals(82.4, r.avg7d, 0.0)
        assertNull(r.avg14d)
        assertEquals(TrendDirection.FLAT, r.direction)
        assertEquals(82.4, r.confidenceRange.first, 0.0)
        assertEquals(82.4, r.confidenceRange.second, 0.0)
        assertEquals(0.0, r.deviationFromPlanKg, 0.0)
        assertNull(r.projectedGoalDate)
    }

    @Test
    fun `avg7d is the EMA of the entries with alpha 2 over n+1`() {
        // alpha = 2 / 4 = 0.5: 80 -> 80.5 -> 81.25
        val r = calc(weights(80.0, 81.0, 82.0))
        assertEquals(81.25, r.avg7d, eps)
    }

    @Test
    fun `avg7d only considers the last 7 entries`() {
        // Older entries are wildly different; the last 7 are constant.
        val r = calc(weights(200.0, 10.0, 150.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0))
        assertEquals(80.0, r.avg7d, eps)
    }

    // ── avg14d ───────────────────────────────────────────────────────────

    @Test
    fun `avg14d is null with fewer than 14 entries`() {
        val r = calc(weights(*DoubleArray(13) { 80.0 + it * 0.1 }))
        assertNull(r.avg14d)
    }

    @Test
    fun `avg14d is present with 14 or more entries`() {
        val r14 = calc(weights(*DoubleArray(14) { 80.0 }))
        assertNotNull(r14.avg14d)
        assertEquals(80.0, r14.avg14d!!, eps)

        val r20 = calc(weights(*DoubleArray(20) { 80.0 }))
        assertNotNull(r20.avg14d)
    }

    @Test
    fun `avg14d only considers the last 14 entries`() {
        // 6 old outliers followed by 14 constant entries
        val values = DoubleArray(6) { 200.0 } + DoubleArray(14) { 80.0 }
        val r = calc(weights(*values))
        assertEquals(80.0, r.avg14d!!, eps)
    }

    // ── Direction ────────────────────────────────────────────────────────

    @Test
    fun `steady decline is DOWN`() {
        // 7-entry EMA ~86.5, 3-entry EMA 84.75
        val r = calc(weights(90.0, 89.0, 88.0, 87.0, 86.0, 85.0, 84.0))
        assertEquals(TrendDirection.DOWN, r.direction)
    }

    @Test
    fun `steady rise is UP`() {
        val r = calc(weights(84.0, 85.0, 86.0, 87.0, 88.0, 89.0, 90.0))
        assertEquals(TrendDirection.UP, r.direction)
    }

    @Test
    fun `constant weight is FLAT`() {
        val r = calc(weights(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0))
        assertEquals(TrendDirection.FLAT, r.direction)
    }

    @Test
    fun `small dip within the 0_1 kg threshold is FLAT`() {
        // avg7d = 0.25 * 79.8 + 0.75 * 80 = 79.95; recent = 0.5 * 79.8 + 0.5 * 80 = 79.9 -> diff -0.05
        val r = calc(weights(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 79.8))
        assertEquals(TrendDirection.FLAT, r.direction)
    }

    @Test
    fun `dip beyond the 0_1 kg threshold is DOWN`() {
        // avg7d = 0.25 * 79.4 + 0.75 * 80 = 79.85; recent = 0.5 * 79.4 + 0.5 * 80 = 79.7 -> diff -0.15
        val r = calc(weights(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 79.4))
        assertEquals(TrendDirection.DOWN, r.direction)
    }

    @Test
    fun `small bump within the threshold is FLAT and beyond it is UP`() {
        assertEquals(TrendDirection.FLAT, calc(weights(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.2)).direction)
        assertEquals(TrendDirection.UP, calc(weights(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 80.6)).direction)
    }

    @Test
    fun `three or fewer entries are always FLAT because both EMAs cover the same values`() {
        assertEquals(TrendDirection.FLAT, calc(weights(90.0, 85.0, 80.0)).direction)
        assertEquals(TrendDirection.FLAT, calc(weights(80.0, 90.0)).direction)
    }

    // ── Ordering ─────────────────────────────────────────────────────────

    @Test
    fun `input order does not matter`() {
        val ordered = weights(90.0, 89.2, 88.7, 88.9, 87.5, 87.8, 86.9, 86.4, 86.6, 85.9, 85.1, 85.4, 84.8, 84.2, 84.5)
        val expected = calc(ordered, targetKg = 75.0)
        assertEquals(expected, calc(ordered.reversed(), targetKg = 75.0))
        assertEquals(expected, calc(ordered.shuffled(Random(42)), targetKg = 75.0))
        assertEquals(expected, calc(ordered.shuffled(Random(7)), targetKg = 75.0))
    }

    @Test
    fun `unsorted input is sorted by date before taking the latest entries`() {
        // Newest entry (day 7) placed first in the list; it must still count as the latest.
        val entries = weights(80.0, 80.0, 80.0, 80.0, 80.0, 80.0, 79.4)
        val r = calc(listOf(entries.last()) + entries.dropLast(1))
        assertEquals(TrendDirection.DOWN, r.direction)
        assertEquals(79.85, r.avg7d, eps)
    }

    // ── Projection ───────────────────────────────────────────────────────

    @Test
    fun `projection is null without a target`() {
        assertNull(calc(weights(80.0), targetKg = null, rateKgPerWeek = 0.5).projectedGoalDate)
    }

    @Test
    fun `projection is null without a rate`() {
        assertNull(calc(weights(80.0), targetKg = 75.0, rateKgPerWeek = null).projectedGoalDate)
    }

    @Test
    fun `projection is null when rate is zero or negative`() {
        assertNull(calc(weights(80.0), targetKg = 75.0, rateKgPerWeek = 0.0).projectedGoalDate)
        assertNull(calc(weights(80.0), targetKg = 75.0, rateKgPerWeek = -0.5).projectedGoalDate)
    }

    @Test
    fun `projection is today when already at goal`() {
        val before = LocalDate.now()
        val r = calc(weights(80.0), targetKg = 80.0, rateKgPerWeek = 0.5)
        val after = LocalDate.now()
        assertProjected(0, before, after, r.projectedGoalDate)
    }

    @Test
    fun `projection is today when already below goal`() {
        val before = LocalDate.now()
        val r = calc(weights(80.0), targetKg = 85.0, rateKgPerWeek = 0.5)
        val after = LocalDate.now()
        assertProjected(0, before, after, r.projectedGoalDate)
    }

    @Test
    fun `projection is today plus weeks remaining times 7 days`() {
        // 5 kg / 0.5 kg per week = 10 weeks = 70 days
        val before = LocalDate.now()
        val r = calc(weights(80.0), targetKg = 75.0, rateKgPerWeek = 0.5)
        val after = LocalDate.now()
        assertProjected(70, before, after, r.projectedGoalDate)
    }

    @Test
    fun `projection truncates fractional days`() {
        // 3 kg / 0.4 kg per week = 7.5 weeks = 52.5 days -> 52
        val before = LocalDate.now()
        val r = calc(weights(80.0), targetKg = 77.0, rateKgPerWeek = 0.4)
        val after = LocalDate.now()
        assertProjected(52, before, after, r.projectedGoalDate)
    }

    @Test
    fun `projection is measured from avg7d, not the latest raw entry`() {
        // avg7d = 81.25 (EMA of 80, 81, 82); 81.25 - 80 = 1.25 kg / 0.25 kg per week = 5 weeks = 35 days
        val before = LocalDate.now()
        val r = calc(weights(80.0, 81.0, 82.0), targetKg = 80.0, rateKgPerWeek = 0.25)
        val after = LocalDate.now()
        assertProjected(35, before, after, r.projectedGoalDate)
    }

    // ── Deviation from plan ──────────────────────────────────────────────

    @Test
    fun `deviation is avg7d minus target`() {
        assertEquals(5.0, calc(weights(80.0), targetKg = 75.0).deviationFromPlanKg, eps)
        assertEquals(-5.0, calc(weights(80.0), targetKg = 85.0).deviationFromPlanKg, eps)
        assertEquals(1.25, calc(weights(80.0, 81.0, 82.0), targetKg = 80.0).deviationFromPlanKg, eps)
    }

    @Test
    fun `deviation is computed even without a rate`() {
        val r = calc(weights(80.0), targetKg = 75.0, rateKgPerWeek = null)
        assertEquals(5.0, r.deviationFromPlanKg, eps)
        assertNull(r.projectedGoalDate)
    }

    @Test
    fun `deviation is zero without a target`() {
        assertEquals(0.0, calc(weights(90.0, 85.0, 80.0)).deviationFromPlanKg, 0.0)
    }

    // ── Confidence range ─────────────────────────────────────────────────

    @Test
    fun `confidence range is avg7d plus or minus sample std dev`() {
        // [80, 81, 82]: mean 81, sample variance (1 + 0 + 1) / 2 = 1 -> std dev 1
        val r = calc(weights(80.0, 81.0, 82.0))
        assertEquals(81.25 - 1.0, r.confidenceRange.first, eps)
        assertEquals(81.25 + 1.0, r.confidenceRange.second, eps)
    }

    @Test
    fun `confidence range uses only the last 7 entries`() {
        // last 7 = 80..86: mean 83, squared deviations sum 28, sample variance 28 / 6
        val sd = sqrt(28.0 / 6.0)
        val withoutHistory = calc(weights(80.0, 81.0, 82.0, 83.0, 84.0, 85.0, 86.0))
        val withHistory = calc(weights(150.0, 20.0, 80.0, 81.0, 82.0, 83.0, 84.0, 85.0, 86.0))

        for (r in listOf(withoutHistory, withHistory)) {
            assertEquals(r.avg7d - sd, r.confidenceRange.first, eps)
            assertEquals(r.avg7d + sd, r.confidenceRange.second, eps)
        }
        assertEquals(withoutHistory, withHistory)
    }

    @Test
    fun `confidence range is zero-width for constant weight`() {
        val r = calc(weights(80.0, 80.0, 80.0, 80.0, 80.0))
        assertEquals(r.avg7d, r.confidenceRange.first, eps)
        assertEquals(r.avg7d, r.confidenceRange.second, eps)
    }
}
