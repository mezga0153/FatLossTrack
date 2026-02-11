package com.fatlosstrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import com.fatlosstrack.ui.theme.Secondary
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Sparkline-style weight trend chart with drag-to-inspect,
 * faint horizontal grid lines and right-side Y-axis labels.
 */
@Composable
fun TrendChart(
    dataPoints: List<Pair<Int, Double>>,  // (dayIndex, weight)
    dateLabels: List<String> = emptyList(),
    xAxisLabels: List<String> = emptyList(),
    startLineKg: Double? = null,
    targetLineKg: Double? = null,
    modifier: Modifier = Modifier,
) {
    if (dataPoints.size < 2) return

    val density = LocalDensity.current
    var touchX by remember { mutableStateOf<Float?>(null) }

    val lineColor = Primary
    val dotColor = Primary.copy(alpha = 0.5f)
    val indicatorColor = OnSurfaceVariant.copy(alpha = 0.5f)
    val bubbleBg = Color(0xFF252540)
    val bubbleText = OnSurface
    val gridColor = OnSurfaceVariant.copy(alpha = 0.12f)
    val labelColor = OnSurfaceVariant
    val confidenceBandColor = ConfidenceBand
    val refLineDefaultColor = OnSurfaceVariant
    val refLineTargetColor = Secondary

    val values = dataPoints.map { it.second }
    val allValues = buildList {
        addAll(values)
        startLineKg?.let { add(it) }
        targetLineKg?.let { add(it) }
    }
    val minVal = allValues.min() - 0.3
    val maxVal = allValues.max() + 0.3
    val range = (maxVal - minVal).coerceAtLeast(0.1)

    val minIdx = dataPoints.minOf { it.first }
    val maxIdx = dataPoints.maxOf { it.first }
    val idxRange = (maxIdx - minIdx).coerceAtLeast(1)

    // Pre-compute nice ticks
    val ticks = remember(minVal, maxVal) { niceTicks(minVal, maxVal, 4) }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(dataPoints) {
                    detectDragGesturesAfterLongPress(
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
                        onTap = { offset ->
                            touchX = if (touchX == null) offset.x else null
                        },
                    )
                }
        ) {
            val padLeft = 4.dp.toPx()
            val padRight = 34.dp.toPx()
            val padTop = 4.dp.toPx()
            val padBottom = if (xAxisLabels.isNotEmpty()) 14.dp.toPx() else 2.dp.toPx()
            val chartWidth = size.width - padLeft - padRight
            val chartHeight = size.height - padTop - padBottom

            fun xFor(index: Int): Float =
                padLeft + (index - minIdx).toFloat() / idxRange * chartWidth

            fun yFor(value: Double): Float =
                padTop + ((maxVal - value) / range * chartHeight).toFloat()

            // Grid lines + right labels
            val d = density.density
            val labelPaint = android.graphics.Paint().apply {
                color = android.graphics.Color.argb(
                    (labelColor.alpha * 255).toInt(),
                    (labelColor.red * 255).toInt(),
                    (labelColor.green * 255).toInt(),
                    (labelColor.blue * 255).toInt(),
                )
                textSize = 9 * d
                isAntiAlias = true
            }

            // X-axis labels + vertical grid lines
            if (xAxisLabels.isNotEmpty()) {
                val xLabelPaint = android.graphics.Paint().apply {
                    color = labelPaint.color
                    textSize = 8 * d
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val count = xAxisLabels.size
                val maxLabels = 6
                val step = if (count <= maxLabels) 1 else (count - 1 + maxLabels - 2) / (maxLabels - 1)
                var i = 0
                while (i < count) {
                    val idx = dataPoints[i].first
                    val x = xFor(idx)
                    drawLine(
                        color = gridColor,
                        start = Offset(x, padTop),
                        end = Offset(x, padTop + chartHeight),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        xAxisLabels[i],
                        x,
                        padTop + chartHeight + 11 * d,
                        xLabelPaint,
                    )
                    i += step
                }
            }
            ticks.forEach { tick ->
                val y = yFor(tick)
                if (y in padTop..padTop + chartHeight) {
                    drawLine(
                        color = gridColor,
                        start = Offset(padLeft, y),
                        end = Offset(padLeft + chartWidth, y),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                    val lbl = formatTickLabel(tick)
                    drawContext.canvas.nativeCanvas.drawText(
                        lbl,
                        padLeft + chartWidth + 4 * d,
                        y + 3.5f * d,
                        labelPaint,
                    )
                }
            }

            // Gradient fill under line
            val fillPath = Path().apply {
                dataPoints.forEachIndexed { i, (dayIdx, value) ->
                    val x = xFor(dayIdx)
                    val y = yFor(value)
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                lineTo(xFor(dataPoints.last().first), padTop + chartHeight)
                lineTo(xFor(dataPoints.first().first), padTop + chartHeight)
                close()
            }
            drawPath(fillPath, color = confidenceBandColor)

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

            // Small dots
            dataPoints.forEach { (dayIdx, value) ->
                drawCircle(
                    color = dotColor,
                    radius = 2.5.dp.toPx(),
                    center = Offset(xFor(dayIdx), yFor(value)),
                )
            }

            // Reference lines (start & target weight)
            startLineKg?.let { kg ->
                drawRefLine(yFor(kg), "%.1f".format(kg), refLineDefaultColor, padLeft, chartWidth, d)
            }
            targetLineKg?.let { kg ->
                drawRefLine(yFor(kg), "%.1f".format(kg), refLineTargetColor, padLeft, chartWidth, d)
            }

            // Touch indicator
            val tx = touchX
            if (tx != null) {
                val nearest = dataPoints.minByOrNull {
                    kotlin.math.abs(xFor(it.first) - tx)
                } ?: return@Canvas
                val nearestIdx = dataPoints.indexOf(nearest)
                val nx = xFor(nearest.first)
                val ny = yFor(nearest.second)

                drawLine(
                    color = indicatorColor,
                    start = Offset(nx, padTop),
                    end = Offset(nx, padTop + chartHeight),
                    strokeWidth = 1.dp.toPx(),
                )

                drawCircle(color = lineColor, radius = 5.dp.toPx(), center = Offset(nx, ny))
                drawCircle(color = bubbleBg, radius = 3.dp.toPx(), center = Offset(nx, ny))

                val label = "%.1f kg".format(nearest.second)
                val dateLabel = dateLabels.getOrNull(nearestIdx)
                val fullLabel = if (dateLabel != null) "$dateLabel  $label" else label

                drawBubble(
                    text = fullLabel,
                    x = nx,
                    chartWidth = size.width,
                    padding = padLeft,
                    bgColor = bubbleBg,
                    textColor = bubbleText,
                    density = d,
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
    val bubbleY = 2 * density

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

private fun DrawScope.drawRefLine(
    y: Float,
    label: String,
    lineColor: Color,
    padLeft: Float,
    chartWidth: Float,
    density: Float,
) {
    drawLine(
        color = lineColor.copy(alpha = 0.4f),
        start = Offset(padLeft, y),
        end = Offset(padLeft + chartWidth, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
    val textPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(
            255,
            (lineColor.red * 255).toInt(),
            (lineColor.green * 255).toInt(),
            (lineColor.blue * 255).toInt(),
        )
        textSize = 9 * density
        isAntiAlias = true
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    val bgPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(210, 13, 13, 26)
        isAntiAlias = true
    }
    val tw = textPaint.measureText(label)
    val px = 4 * density
    val badgeH = 14 * density
    val badgeX = padLeft + chartWidth - tw - px * 2
    val badgeY = y - badgeH - 1 * density
    val rect = android.graphics.RectF(badgeX, badgeY, badgeX + tw + px * 2, badgeY + badgeH)
    drawContext.canvas.nativeCanvas.apply {
        drawRoundRect(rect, 3 * density, 3 * density, bgPaint)
        drawText(label, badgeX + px, badgeY + badgeH - 3.5f * density, textPaint)
    }
}

/**
 * Compute ~[targetCount] "nice" round tick values spanning [min]..[max].
 */
internal fun niceTicks(min: Double, max: Double, targetCount: Int = 4): List<Double> {
    val r = max - min
    if (r <= 0) return listOf(min)
    val rough = r / targetCount
    val mag = 10.0.pow(floor(log10(rough)))
    val frac = rough / mag
    val niceStep = when {
        frac < 1.5 -> mag
        frac < 3.0 -> 2 * mag
        frac < 7.0 -> 5 * mag
        else -> 10 * mag
    }
    val start = ceil(min / niceStep) * niceStep
    return generateSequence(start) { it + niceStep }
        .takeWhile { it <= max + niceStep * 0.01 }
        .toList()
}

/** Format a tick value: show integers when whole, one decimal otherwise. */
internal fun formatTickLabel(v: Double): String =
    if (v == floor(v) && v < 10_000) "%.0f".format(v) else "%.1f".format(v)
