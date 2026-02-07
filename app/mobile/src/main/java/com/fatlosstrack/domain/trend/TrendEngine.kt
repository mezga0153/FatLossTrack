package com.fatlosstrack.domain.trend

import com.fatlosstrack.data.local.db.WeightEntry
import java.time.LocalDate

data class TrendResult(
    val avg7d: Double,
    val avg14d: Double?,
    val projectedGoalDate: LocalDate?,
    val deviationFromPlanKg: Double,
    val direction: TrendDirection,
    val confidenceRange: Pair<Double, Double>,
)

enum class TrendDirection { UP, DOWN, FLAT }

/**
 * Computes exponentially weighted moving average and projections.
 * This runs entirely on-device — no network needed.
 */
object TrendEngine {

    fun calculate(
        entries: List<WeightEntry>,
        targetKg: Double?,
        rateKgPerWeek: Double?,
    ): TrendResult? {
        if (entries.isEmpty()) return null

        val sorted = entries.sortedBy { it.date }

        val avg7d = ema(sorted.takeLast(7).map { it.valueKg })
        val avg14d = if (sorted.size >= 14) ema(sorted.takeLast(14).map { it.valueKg }) else null

        // Direction: compare last 3-day EMA to last 7-day EMA
        val recent = ema(sorted.takeLast(3).map { it.valueKg })
        val direction = when {
            recent < avg7d - 0.1 -> TrendDirection.DOWN
            recent > avg7d + 0.1 -> TrendDirection.UP
            else -> TrendDirection.FLAT
        }

        // Projection
        val projectedGoalDate = if (targetKg != null && rateKgPerWeek != null && rateKgPerWeek > 0) {
            val kgRemaining = avg7d - targetKg
            if (kgRemaining <= 0) {
                LocalDate.now() // Already at goal
            } else {
                val weeksRemaining = kgRemaining / rateKgPerWeek
                LocalDate.now().plusDays((weeksRemaining * 7).toLong())
            }
        } else null

        // Deviation from plan: where should weight be vs where it is
        val deviationFromPlanKg = if (targetKg != null) {
            avg7d - targetKg // positive = above target
        } else 0.0

        // Confidence range: +/- 1 std dev of last 7 entries
        val last7 = sorted.takeLast(7).map { it.valueKg }
        val stdDev = stdDev(last7)
        val confidenceRange = (avg7d - stdDev) to (avg7d + stdDev)

        return TrendResult(
            avg7d = avg7d,
            avg14d = avg14d,
            projectedGoalDate = projectedGoalDate,
            deviationFromPlanKg = deviationFromPlanKg,
            direction = direction,
            confidenceRange = confidenceRange,
        )
    }

    private fun ema(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0
        if (values.size == 1) return values[0]
        val alpha = 2.0 / (values.size + 1)
        var result = values[0]
        for (i in 1 until values.size) {
            result = alpha * values[i] + (1 - alpha) * result
        }
        return result
    }

    private fun stdDev(values: List<Double>): Double {
        if (values.size < 2) return 0.0
        val mean = values.average()
        val variance = values.sumOf { (it - mean) * (it - mean) } / (values.size - 1)
        return kotlin.math.sqrt(variance)
    }
}
