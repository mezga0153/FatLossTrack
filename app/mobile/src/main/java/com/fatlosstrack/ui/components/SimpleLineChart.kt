package com.fatlosstrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Line chart with grid, Y-axis labels, and drag-to-inspect bubble.
 * Used for calories, sleep, steps trends on both Home and Trends tabs.
 */
@Composable
fun SimpleLineChart(
    data: List<Pair<Int, Double>>,
    color: Color,
    modifier: Modifier = Modifier,
    dateLabels: List<String> = emptyList(),
    xAxisLabels: List<String> = emptyList(),
    unit: String = "",
    refLineValue: Double? = null,
    refLineColor: Color = Color(0xFF59D8A0),
    refLineLabel: String? = null,
) {
    if (data.size < 2) return

    val density = LocalDensity.current
    var touchX by remember { mutableStateOf<Float?>(null) }

    val gridColor = Color(0xFF8B8BA3).copy(alpha = 0.12f)
    val labelColor = Color(0xFF8B8BA3)
    val indicatorColor = Color(0xFF8B8BA3).copy(alpha = 0.5f)
    val bubbleBg = Color(0xFF252540)
    val bubbleText = Color(0xFFE8E8F0)

    val values = data.map { it.second }
    val allValues = buildList {
        addAll(values)
        refLineValue?.let { add(it) }
    }
    val minVal = allValues.min() * 0.9
    val maxVal = allValues.max() * 1.1
    val range = (maxVal - minVal).coerceAtLeast(1.0)

    val minIdx = data.minOf { it.first }
    val maxIdx = data.maxOf { it.first }
    val idxRange = (maxIdx - minIdx).coerceAtLeast(1)

    val ticks = remember(minVal, maxVal) { niceTicks(minVal, maxVal, 3) }

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(data) {
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
                .pointerInput(data) {
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
                this.color = android.graphics.Color.argb(
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
                    this.color = labelPaint.color
                    textSize = 8 * d
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val count = xAxisLabels.size
                val maxLabels = 6
                val step = if (count <= maxLabels) 1 else (count - 1 + maxLabels - 2) / (maxLabels - 1)
                var i = 0
                while (i < count) {
                    val idx = data[i].first
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

            // Line path
            val path = Path()
            data.forEachIndexed { i, (dayIdx, value) ->
                val x = xFor(dayIdx)
                val y = yFor(value)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )

            // Dots
            data.forEach { (dayIdx, value) ->
                drawCircle(
                    color = color.copy(alpha = 0.5f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(xFor(dayIdx), yFor(value)),
                )
            }

            // Reference line
            if (refLineValue != null) {
                val ry = yFor(refLineValue)
                drawLine(
                    color = refLineColor.copy(alpha = 0.4f),
                    start = Offset(padLeft, ry),
                    end = Offset(padLeft + chartWidth, ry),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
                )
                if (refLineLabel != null) {
                    val textPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.argb(
                            255,
                            (refLineColor.red * 255).toInt(),
                            (refLineColor.green * 255).toInt(),
                            (refLineColor.blue * 255).toInt(),
                        )
                        textSize = 9 * d
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    val bgPaint = android.graphics.Paint().apply {
                        this.color = android.graphics.Color.argb(210, 13, 13, 26)
                        isAntiAlias = true
                    }
                    val tw = textPaint.measureText(refLineLabel)
                    val px = 4 * d
                    val badgeH = 14 * d
                    val badgeX = padLeft + chartWidth - tw - px * 2
                    val badgeY = ry - badgeH - 1 * d
                    val rect = android.graphics.RectF(badgeX, badgeY, badgeX + tw + px * 2, badgeY + badgeH)
                    drawContext.canvas.nativeCanvas.apply {
                        drawRoundRect(rect, 3 * d, 3 * d, bgPaint)
                        drawText(refLineLabel, badgeX + px, badgeY + badgeH - 3.5f * d, textPaint)
                    }
                }
            }

            // Touch indicator + bubble
            val tx = touchX
            if (tx != null) {
                val nearest = data.minByOrNull {
                    kotlin.math.abs(xFor(it.first) - tx)
                } ?: return@Canvas
                val nearestIdx = data.indexOf(nearest)
                val nx = xFor(nearest.first)
                val ny = yFor(nearest.second)

                // Vertical indicator line
                drawLine(
                    color = indicatorColor,
                    start = Offset(nx, padTop),
                    end = Offset(nx, padTop + chartHeight),
                    strokeWidth = 1.dp.toPx(),
                )

                // Highlighted dot
                drawCircle(color = color, radius = 5.dp.toPx(), center = Offset(nx, ny))
                drawCircle(color = bubbleBg, radius = 3.dp.toPx(), center = Offset(nx, ny))

                // Bubble
                val valStr = formatTickLabel(nearest.second)
                val bubbleLabel = buildString {
                    dateLabels.getOrNull(nearestIdx)?.let { append(it); append("  ") }
                    append(valStr)
                    if (unit.isNotEmpty()) { append(" "); append(unit) }
                }

                val textPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.argb(
                        (bubbleText.alpha * 255).toInt(),
                        (bubbleText.red * 255).toInt(),
                        (bubbleText.green * 255).toInt(),
                        (bubbleText.blue * 255).toInt(),
                    )
                    textSize = 11 * d
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                val textWidth = textPaint.measureText(bubbleLabel)
                val bubbleW = textWidth + 16 * d
                val bubbleH = 22 * d
                val bubbleY = 2 * d

                val bubbleX = (nx - bubbleW / 2).coerceIn(padLeft, size.width - padRight - bubbleW)

                val rect = android.graphics.RectF(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH)
                val bgPaint = android.graphics.Paint().apply {
                    this.color = android.graphics.Color.argb(
                        (bubbleBg.alpha * 255).toInt(),
                        (bubbleBg.red * 255).toInt(),
                        (bubbleBg.green * 255).toInt(),
                        (bubbleBg.blue * 255).toInt(),
                    )
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.apply {
                    drawRoundRect(rect, 6 * d, 6 * d, bgPaint)
                    drawText(bubbleLabel, bubbleX + 8 * d, bubbleY + bubbleH - 6 * d, textPaint)
                }
            }
        }
    }
}
