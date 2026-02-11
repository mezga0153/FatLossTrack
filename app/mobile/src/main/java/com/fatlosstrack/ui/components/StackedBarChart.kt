package com.fatlosstrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Grouped bar chart for daily macros (protein / carbs / fat).
 *
 * Each day gets 3 side-by-side bars. When [macroTargets] is provided,
 * Y-axis shows percentage of daily target (100 % reference line drawn).
 * Otherwise falls back to absolute grams.
 *
 * Tap or long-press-drag to inspect with a multiline bubble showing
 * grams and percentage for each macro.
 *
 * @param data List of (protein, carbs, fat) per day in grams.
 * @param macroTargets Recommended daily (proteinG, carbsG, fatG) or null.
 * @param dateLabels Bubble tooltip labels (e.g. "10. Feb").
 * @param xAxisLabels Bottom axis labels (day names or dates).
 * @param colors Triple of (protein, carbs, fat) colors.
 */
@Composable
fun MacroBarChart(
    data: List<Triple<Int, Int, Int>>,          // (protein, carbs, fat) per day
    macroTargets: Triple<Int, Int, Int>? = null, // daily targets (g) or null
    dateLabels: List<String> = emptyList(),
    xAxisLabels: List<String> = emptyList(),
    colors: Triple<Color, Color, Color>,         // protein, carbs, fat
    modifier: Modifier = Modifier,
) {
    if (data.isEmpty()) return

    val density = LocalDensity.current
    var touchX by remember { mutableStateOf<Float?>(null) }

    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleBg = MaterialTheme.colorScheme.inverseSurface
    val bubbleText = MaterialTheme.colorScheme.inverseOnSurface
    val refLineColor = Color(0xFF59D8A0).copy(alpha = 0.45f)

    // When targets available ⇒ Y is percentage, else absolute grams
    val usePercent = macroTargets != null
    val pctData = data.map { (p, c, f) ->
        if (macroTargets != null) Triple(
            if (macroTargets.first > 0) p * 100.0 / macroTargets.first else 0.0,
            if (macroTargets.second > 0) c * 100.0 / macroTargets.second else 0.0,
            if (macroTargets.third > 0) f * 100.0 / macroTargets.third else 0.0,
        ) else Triple(p.toDouble(), c.toDouble(), f.toDouble())
    }

    val maxVal = pctData.maxOf { maxOf(it.first, it.second, it.third) }.coerceAtLeast(1.0)
    val niceMax = if (usePercent) {
        // Pick a nice ceiling: 100, 120, 150, 200 …
        val candidates = listOf(100, 120, 150, 200, 250, 300)
        (candidates.firstOrNull { it >= maxVal } ?: ((maxVal / 50).toInt() + 1) * 50).toDouble()
    } else {
        val candidates = listOf(50.0, 100.0, 150.0, 200.0, 250.0, 300.0, 400.0, 500.0)
        candidates.firstOrNull { it >= maxVal } ?: ((maxVal / 100).toInt() + 1) * 100.0
    }
    val ticks = remember(niceMax, usePercent) {
        if (usePercent) {
            // E.g. 0, 50, 100 or 0, 50, 100, 150
            val step = if (niceMax <= 100) 50.0 else 50.0
            (0..((niceMax / step).toInt())).map { it * step }
        } else {
            niceTicks(0.0, niceMax, 3)
        }
    }

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
            val d = density.density
            val padLeft = 4.dp.toPx()
            val padRight = if (usePercent) 30.dp.toPx() else 34.dp.toPx()
            val padTop = 4.dp.toPx()
            val padBottom = if (xAxisLabels.isNotEmpty()) 14.dp.toPx() else 2.dp.toPx()
            val chartW = size.width - padLeft - padRight
            val chartH = size.height - padTop - padBottom

            val n = data.size
            // Layout: each day-group = 3 thin bars + tiny inner gaps.
            // Groups separated by wider inter-group gap.
            val innerGap = 1 * d              // gap between bars in a group
            val groupGap = 4 * d              // gap between day groups
            val totalGroupGaps = (n - 1).coerceAtLeast(0) * groupGap
            val availForBars = chartW - totalGroupGaps
            val groupW = availForBars / n.coerceAtLeast(1)
            val barW = ((groupW - 2 * innerGap) / 3f).coerceAtLeast(1 * d)

            fun groupLeft(index: Int): Float = padLeft + index * (groupW + groupGap)
            fun groupCenter(index: Int): Float = groupLeft(index) + groupW / 2
            fun yFor(value: Double): Float = padTop + ((niceMax - value) / niceMax * chartH).toFloat()

            // Y-axis grid + right labels
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
            ticks.forEach { tick ->
                val y = yFor(tick)
                if (y in padTop..padTop + chartH) {
                    drawLine(
                        color = gridColor,
                        start = Offset(padLeft, y),
                        end = Offset(padLeft + chartW, y),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                    val label = if (usePercent) "${tick.toInt()}%" else formatTickLabel(tick)
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        padLeft + chartW + 4 * d,
                        y + 3.5f * d,
                        labelPaint,
                    )
                }
            }

            // 100% reference line when using percentage mode
            if (usePercent && niceMax >= 100) {
                val y100 = yFor(100.0)
                drawLine(
                    color = refLineColor,
                    start = Offset(padLeft, y100),
                    end = Offset(padLeft + chartW, y100),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                        floatArrayOf(6 * d, 4 * d), 0f,
                    ),
                )
            }

            // X-axis labels + vertical grid
            if (xAxisLabels.isNotEmpty()) {
                val xLabelPaint = android.graphics.Paint().apply {
                    color = labelPaint.color
                    textSize = 8 * d
                    isAntiAlias = true
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val maxLabels = 6
                val step = if (n <= maxLabels) 1 else (n - 1 + maxLabels - 2) / (maxLabels - 1)
                var i = 0
                while (i < n && i < xAxisLabels.size) {
                    val cx = groupCenter(i)
                    drawLine(
                        color = gridColor,
                        start = Offset(cx, padTop),
                        end = Offset(cx, padTop + chartH),
                        strokeWidth = 0.5.dp.toPx(),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        xAxisLabels[i],
                        cx,
                        padTop + chartH + 11 * d,
                        xLabelPaint,
                    )
                    i += step
                }
            }

            // Draw grouped bars — 3 per day
            val barBottom = padTop + chartH
            val cornerPx = 2 * d
            pctData.forEachIndexed { i, (pVal, cVal, fVal) ->
                val gl = groupLeft(i)
                // Bar 1: protein
                val h1 = (pVal / niceMax * chartH).toFloat().coerceAtLeast(0f)
                drawRoundRect(
                    color = colors.first,
                    topLeft = Offset(gl, barBottom - h1),
                    size = Size(barW, h1),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
                // Bar 2: carbs
                val h2 = (cVal / niceMax * chartH).toFloat().coerceAtLeast(0f)
                drawRoundRect(
                    color = colors.second,
                    topLeft = Offset(gl + barW + innerGap, barBottom - h2),
                    size = Size(barW, h2),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
                // Bar 3: fat
                val h3 = (fVal / niceMax * chartH).toFloat().coerceAtLeast(0f)
                drawRoundRect(
                    color = colors.third,
                    topLeft = Offset(gl + 2 * (barW + innerGap), barBottom - h3),
                    size = Size(barW, h3),
                    cornerRadius = CornerRadius(cornerPx, cornerPx),
                )
            }

            // Touch indicator + multiline bubble
            val tx = touchX
            if (tx != null && n > 0) {
                val nearestIdx = (0 until n).minByOrNull {
                    kotlin.math.abs(groupCenter(it) - tx)
                } ?: return@Canvas
                val (protein, carbs, fat) = data[nearestIdx]
                val (pPct, cPct, fPct) = pctData[nearestIdx]
                val cx = groupCenter(nearestIdx)

                // Highlight rectangle around the group
                val gl = groupLeft(nearestIdx)
                drawRect(
                    color = bubbleText.copy(alpha = 0.2f),
                    topLeft = Offset(gl - 2 * d, padTop),
                    size = Size(groupW + 4 * d, chartH),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx()),
                )

                // Build bubble lines
                val dateStr = dateLabels.getOrElse(nearestIdx) { "" }
                val lines = mutableListOf<String>()
                if (dateStr.isNotEmpty()) lines.add(dateStr)
                if (usePercent) {
                    lines.add("Protein: ${protein}g (${pPct.toInt()}%)")
                    lines.add("Carbs: ${carbs}g (${cPct.toInt()}%)")
                    lines.add("Fat: ${fat}g (${fPct.toInt()}%)")
                } else {
                    lines.add("Protein: ${protein}g")
                    lines.add("Carbs: ${carbs}g")
                    lines.add("Fat: ${fat}g")
                }

                val textPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        (bubbleText.alpha * 255).toInt(),
                        (bubbleText.red * 255).toInt(),
                        (bubbleText.green * 255).toInt(),
                        (bubbleText.blue * 255).toInt(),
                    )
                    textSize = 10 * d
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                val lineHeight = 13 * d
                val maxLineW = lines.maxOf { textPaint.measureText(it) }
                val bubbleW = maxLineW + 16 * d
                val bubbleH = lines.size * lineHeight + 8 * d
                val bubbleY = 2 * d
                val bubbleX = (cx - bubbleW / 2).coerceIn(padLeft, size.width - padRight - bubbleW)
                val rect = android.graphics.RectF(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH)
                val bgPaint = android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(
                        (bubbleBg.alpha * 255).toInt(),
                        (bubbleBg.red * 255).toInt(),
                        (bubbleBg.green * 255).toInt(),
                        (bubbleBg.blue * 255).toInt(),
                    )
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.apply {
                    drawRoundRect(rect, 6 * d, 6 * d, bgPaint)
                    lines.forEachIndexed { li, line ->
                        drawText(
                            line,
                            bubbleX + 8 * d,
                            bubbleY + 4 * d + (li + 1) * lineHeight,
                            textPaint,
                        )
                    }
                }
            }
        }
    }
}
