package com.fatlosstrack.ui.camera

import com.fatlosstrack.domain.PortionScale

/**
 * This result with every item's nutrition and the totals multiplied by [factor],
 * and item portions annotated ("200g ×1.5"). Totals are scaled directly rather than
 * re-summed, since the AI's totals don't always equal the sum of its items.
 */
internal fun AnalysisResult.scaledBy(factor: Double): AnalysisResult {
    if (factor == 1.0) return this
    return copy(
        items = items.map { item ->
            item.copy(
                portion = PortionScale.annotatePortion(item.portion, factor),
                nutrition = item.nutrition.map { row ->
                    row.amount.toIntOrNull()
                        ?.let { row.copy(amount = PortionScale.scale(it, factor).toString()) }
                        ?: row
                },
            )
        },
        totalCalories = PortionScale.scale(totalCalories, factor),
        totalProteinG = PortionScale.scale(totalProteinG, factor),
        totalCarbsG = PortionScale.scale(totalCarbsG, factor),
        totalFatG = PortionScale.scale(totalFatG, factor),
    )
}
