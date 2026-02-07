package com.fatlosstrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.theme.ConfidenceBand
import com.fatlosstrack.ui.theme.Primary
import com.fatlosstrack.ui.theme.TrendDown

/**
 * Simple Compose Canvas weight trend chart.
 * Shows: EMA line, goal projection, confidence band, daily dots.
 * No MPAndroidChart dependency — pure Compose for the prototype.
 */
@Composable
fun TrendChart(
    dataPoints: List<Pair<Int, Double>>,  // (dayIndex, weight)
    avg7d: Double,
    targetKg: Double,
    confidenceLow: Double,
    confidenceHigh: Double,
    modifier: Modifier = Modifier,
) {
    val lineColor = Primary
    val goalColor = TrendDown
    val dotColor = Color(0xFF666666)
    val bandColor = ConfidenceBand

    Canvas(modifier = modifier.fillMaxWidth().height(200.dp)) {
        if (dataPoints.size < 2) return@Canvas

        val padding = 24.dp.toPx()
        val chartWidth = size.width - padding * 2
        val chartHeight = size.height - padding * 2

        val allValues = dataPoints.map { it.second } + targetKg
        val minVal = allValues.min() - 0.5
        val maxVal = allValues.max() + 0.5
        val range = maxVal - minVal

        fun xFor(index: Int): Float {
            val maxIndex = dataPoints.maxOf { it.first }
            val minIndex = dataPoints.minOf { it.first }
            val idxRange = (maxIndex - minIndex).coerceAtLeast(1)
            return padding + (index - minIndex).toFloat() / idxRange * chartWidth
        }

        fun yFor(value: Double): Float {
            return padding + ((maxVal - value) / range * chartHeight).toFloat()
        }

        // Confidence band
        val bandTop = yFor(confidenceHigh)
        val bandBottom = yFor(confidenceLow)
        drawRect(
            color = bandColor,
            topLeft = Offset(padding, bandTop),
            size = androidx.compose.ui.geometry.Size(chartWidth, bandBottom - bandTop),
        )

        // Goal line (dashed)
        val goalY = yFor(targetKg)
        val dashWidth = 8.dp.toPx()
        val gapWidth = 6.dp.toPx()
        var dashX = padding
        while (dashX < padding + chartWidth) {
            drawLine(
                color = goalColor.copy(alpha = 0.5f),
                start = Offset(dashX, goalY),
                end = Offset((dashX + dashWidth).coerceAtMost(padding + chartWidth), goalY),
                strokeWidth = 1.dp.toPx(),
            )
            dashX += dashWidth + gapWidth
        }

        // 7-day avg line (horizontal)
        val avgY = yFor(avg7d)
        drawLine(
            color = lineColor.copy(alpha = 0.4f),
            start = Offset(padding, avgY),
            end = Offset(padding + chartWidth, avgY),
            strokeWidth = 1.dp.toPx(),
        )

        // Trend line
        val path = Path()
        dataPoints.forEachIndexed { i, (dayIdx, value) ->
            val x = xFor(dayIdx)
            val y = yFor(value)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(
            path = path,
            color = lineColor,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )

        // Daily dots (subtle)
        dataPoints.forEach { (dayIdx, value) ->
            drawCircle(
                color = dotColor,
                radius = 3.dp.toPx(),
                center = Offset(xFor(dayIdx), yFor(value)),
            )
        }
    }
}
