package com.fatlosstrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.theme.ConfidenceBand
import com.fatlosstrack.ui.theme.OnSurface
import com.fatlosstrack.ui.theme.OnSurfaceVariant
import com.fatlosstrack.ui.theme.Primary

/**
 * Sparkline-style weight trend chart with drag-to-inspect.
 *
 * Draws a smooth line with subtle dots. When the user touches/drags,
 * a vertical indicator + bubble shows the exact weight and date label for
 * the nearest data point.
 */
@Composable
fun TrendChart(
    dataPoints: List<Pair<Int, Double>>,  // (dayIndex, weight)
    avg7d: Double = 0.0,
    targetKg: Double = 0.0,
    confidenceLow: Double = 0.0,
    confidenceHigh: Double = 0.0,
    dateLabels: List<String> = emptyList(), // optional: matching labels per dataPoint
    modifier: Modifier = Modifier,
) {
    if (dataPoints.size < 2) return

    val density = LocalDensity.current
    var touchX by remember { mutableStateOf<Float?>(null) }

    // Pre-compute layout constants
    val paddingDp = 12.dp
    val lineColor = Primary
    val dotColor = Primary.copy(alpha = 0.5f)
    val indicatorColor = OnSurfaceVariant.copy(alpha = 0.5f)
    val bubbleBg = Color(0xFF252540)
    val bubbleText = OnSurface

    val values = dataPoints.map { it.second }
    val minVal = values.min() - 0.3
    val maxVal = values.max() + 0.3
    val range = (maxVal - minVal).coerceAtLeast(0.1)

    val minIdx = dataPoints.minOf { it.first }
    val maxIdx = dataPoints.maxOf { it.first }
    val idxRange = (maxIdx - minIdx).coerceAtLeast(1)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dataPoints) {
                    detectDragGestures(
                        onDragStart = { offset -> touchX = offset.x },
                        onDrag = { change, _ ->
                            change.consume()
                            touchX = change.position.x
                        },
                        onDragEnd = { touchX = null },
                        onDragCancel = { touchX = null },
                    )
                }
                .pointerInput(dataPoints) {
                    detectTapGestures(
                        onPress = { offset ->
                            touchX = offset.x
                            val released = tryAwaitRelease()
                            if (released) touchX = null
                        },
                    )
                }
        ) {
            val padding = paddingDp.toPx()
            val chartWidth = size.width - padding * 2
            val chartHeight = size.height - padding * 2

            fun xFor(index: Int): Float =
                padding + (index - minIdx).toFloat() / idxRange * chartWidth

            fun yFor(value: Double): Float =
                padding + ((maxVal - value) / range * chartHeight).toFloat()

            // Gradient fill under line
            val fillPath = Path().apply {
                dataPoints.forEachIndexed { i, (dayIdx, value) ->
                    val x = xFor(dayIdx)
                    val y = yFor(value)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                lineTo(xFor(dataPoints.last().first), padding + chartHeight)
                lineTo(xFor(dataPoints.first().first), padding + chartHeight)
                close()
            }
            drawPath(fillPath, color = ConfidenceBand)

            // Trend line
            val linePath = Path().apply {
                dataPoints.forEachIndexed { i, (dayIdx, value) ->
                    val x = xFor(dayIdx)
                    val y = yFor(value)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
            }
            drawPath(
                linePath,
                color = lineColor,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            // Small dots at each data point
            dataPoints.forEach { (dayIdx, value) ->
                drawCircle(
                    color = dotColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(xFor(dayIdx), yFor(value)),
                )
            }

            // Touch indicator
            val tx = touchX
            if (tx != null) {
                // Find nearest data point
                val nearest = dataPoints.minByOrNull {
                    kotlin.math.abs(xFor(it.first) - tx)
                } ?: return@Canvas
                val nearestIdx = dataPoints.indexOf(nearest)
                val nx = xFor(nearest.first)
                val ny = yFor(nearest.second)

                // Vertical indicator line
                drawLine(
                    color = indicatorColor,
                    start = Offset(nx, padding),
                    end = Offset(nx, padding + chartHeight),
                    strokeWidth = 1.dp.toPx(),
                )

                // Highlighted dot
                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(nx, ny))
                drawCircle(color = bubbleBg, radius = 3.dp.toPx(), center = Offset(nx, ny))

                // Bubble with weight text
                val label = "%.1f kg".format(nearest.second)
                val dateLabel = dateLabels.getOrNull(nearestIdx)
                val fullLabel = if (dateLabel != null) "$dateLabel  $label" else label

                drawBubble(
                    text = fullLabel,
                    x = nx,
                    chartWidth = size.width,
                    padding = padding,
                    bgColor = bubbleBg,
                    textColor = bubbleText,
                    density = density.density,
                )
            }
        }
    }
}

private fun DrawScope.drawBubble(
    text: String,
    x: Float,
    chartWidth: Float,
    padding: Float,
    bgColor: Color,
    textColor: Color,
    density: Float,
) {
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(
            (textColor.alpha * 255).toInt(),
            (textColor.red * 255).toInt(),
            (textColor.green * 255).toInt(),
            (textColor.blue * 255).toInt(),
        )
        textSize = 11 * density
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    val textWidth = textPaint.measureText(text)
    val bubbleW = textWidth + 16 * density
    val bubbleH = 22 * density
    val bubbleY = 2 * density  // near top

    // Clamp bubble horizontally so it doesn't go off-screen
    val bubbleX = (x - bubbleW / 2).coerceIn(padding, chartWidth - padding - bubbleW)

    val rect = android.graphics.RectF(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH)
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(
            (bgColor.alpha * 255).toInt(),
            (bgColor.red * 255).toInt(),
            (bgColor.green * 255).toInt(),
            (bgColor.blue * 255).toInt(),
        )
        isAntiAlias = true
    }

    drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(rect, 6 * density, 6 * density, bgPaint)
        drawText(
            text,
            bubbleX + 8 * density,
            bubbleY + bubbleH - 6 * density,
            textPaint,
        )
    }
}
