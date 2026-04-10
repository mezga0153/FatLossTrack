package com.fatlosstrack.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Descriptor for a single data series in [MultiSeriesLineChart].
 */
data class ChartSeries(
    val label: String,
    val color: Color,
    val unit: String,
    val data: List<Pair<LocalDate, Double>>,
)

/**
 * Normalized multi-series line chart.
 *
 * Each series is independently scaled to 0–1 so metrics with different units
 * (e.g. weight kg vs sleep h) can be overlaid meaningfully.
 *
 * - Left Y-axis: first series actual values.
 * - Right Y-axis: second series actual values (only when exactly 2 series).
 * - Long-press/drag or tap shows a bubble with all series values at that date.
 */
@Composable
fun MultiSeriesLineChart(
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
) {
    val validSeries = series.filter { it.data.size >= 2 }
    if (validSeries.isEmpty()) return

    val density = LocalDensity.current
    var touchX by remember(validSeries) { mutableStateOf<Float?>(null) }

    val allDates = remember(validSeries) {
        validSeries.flatMap { s -> s.data.map { it.first } }.distinct().sorted()
    }
    if (allDates.size < 2) return

    val minDate = allDates.first()
    val maxDate = allDates.last()
    val totalSpan = ChronoUnit.DAYS.between(minDate, maxDate).toInt().coerceAtLeast(1)

    // per-series (min, max, range)
    val stats = remember(validSeries) {
        validSeries.map { s ->
            val vs = s.data.map { it.second }
            Triple(vs.min(), vs.max(), (vs.max() - vs.min()).coerceAtLeast(0.001))
        }
    }

    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val bubbleBg = MaterialTheme.colorScheme.inverseSurface
    val bubbleText = MaterialTheme.colorScheme.inverseOnSurface

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(validSeries) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { o -> touchX = o.x },
                    onDrag = { c, _ -> c.consume(); touchX = c.position.x },
                    onDragEnd = { touchX = null },
                    onDragCancel = { touchX = null },
                )
            }
            .pointerInput(validSeries) {
                detectTapGestures(onTap = { o -> touchX = if (touchX == null) o.x else null })
            },
    ) {
        val hasRightAxis = validSeries.size == 2
        val padLeft = 38.dp.toPx()
        val padRight = if (hasRightAxis) 38.dp.toPx() else 8.dp.toPx()
        val padTop = 8.dp.toPx()
        val padBottom = 20.dp.toPx()
        val chartW = size.width - padLeft - padRight
        val chartH = size.height - padTop - padBottom
        val d = density.density

        fun xFor(date: LocalDate): Float {
            val offset = ChronoUnit.DAYS.between(minDate, date).toInt()
            return padLeft + offset.toFloat() / totalSpan * chartW
        }

        fun normY(si: Int, v: Double): Float {
            val (min, _, range) = stats[si]
            return (padTop + (1.0 - (v - min) / range) * chartH).toFloat()
        }

        fun normVal(si: Int, v: Double): Double {
            val (min, _, range) = stats[si]
            return (v - min) / range
        }

        // ── Left Y-axis (first series) ──
        val leftTicks = niceTicks(stats[0].first, stats[0].second, 3)
        val leftColor = labelColor.toArgb()
        val leftPaint = android.graphics.Paint().apply {
            color = leftColor
            textSize = 9 * d
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        leftTicks.forEach { tick ->
            val nv = normVal(0, tick)
            if (nv in -0.05..1.05) {
                val y = (padTop + (1.0 - nv) * chartH).toFloat()
                drawLine(gridColor, Offset(padLeft, y), Offset(padLeft + chartW, y), 0.5.dp.toPx())
                drawContext.canvas.nativeCanvas.drawText(
                    formatTickLabel(tick),
                    padLeft - 3 * d,
                    y + 3.5f * d,
                    leftPaint,
                )
            }
        }

        // ── Right Y-axis (second series when exactly 2) ──
        if (hasRightAxis) {
            val rightTicks = niceTicks(stats[1].first, stats[1].second, 3)
            val rightPaint = android.graphics.Paint().apply {
                color = validSeries[1].color.copy(alpha = 0.85f).toArgb()
                textSize = 9 * d
                isAntiAlias = true
                textAlign = android.graphics.Paint.Align.LEFT
            }
            rightTicks.forEach { tick ->
                val nv = normVal(1, tick)
                if (nv in -0.05..1.05) {
                    val y = (padTop + (1.0 - nv) * chartH).toFloat()
                    drawContext.canvas.nativeCanvas.drawText(
                        formatTickLabel(tick),
                        padLeft + chartW + 3 * d,
                        y + 3.5f * d,
                        rightPaint,
                    )
                }
            }
        }

        // ── X-axis labels + vertical grid ──
        val xLabelPaint = android.graphics.Paint().apply {
            color = labelColor.copy(alpha = 0.7f).toArgb()
            textSize = 8 * d
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val maxXLabels = 6
        val xStep = if (allDates.size <= maxXLabels) 1
        else ((allDates.size - 1).toFloat() / (maxXLabels - 1)).toInt().coerceAtLeast(1)
        allDates.forEachIndexed { i, date ->
            if (i % xStep == 0 || i == allDates.size - 1) {
                val x = xFor(date)
                drawLine(gridColor, Offset(x, padTop), Offset(x, padTop + chartH), 0.5.dp.toPx())
                val m = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
                drawContext.canvas.nativeCanvas.drawText(
                    "${date.dayOfMonth}. $m",
                    x,
                    padTop + chartH + 13 * d,
                    xLabelPaint,
                )
            }
        }

        // ── Series lines + dots ──
        validSeries.forEachIndexed { si, s ->
            val sorted = s.data.sortedBy { it.first }
            val path = Path()
            sorted.forEachIndexed { i, (date, v) ->
                val x = xFor(date)
                val y = normY(si, v)
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, s.color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            sorted.forEach { (date, v) ->
                drawCircle(s.color.copy(alpha = 0.5f), 2.5.dp.toPx(), Offset(xFor(date), normY(si, v)))
            }
        }

        // ── Touch indicator ──
        val tx = touchX ?: return@Canvas
        val nearestDate = allDates.minByOrNull { abs(xFor(it) - tx) } ?: return@Canvas
        val nx = xFor(nearestDate)

        drawLine(
            labelColor.copy(alpha = 0.35f),
            Offset(nx, padTop),
            Offset(nx, padTop + chartH),
            1.dp.toPx(),
        )

        validSeries.forEachIndexed { si, s ->
            val v = s.data.firstOrNull { it.first == nearestDate }?.second ?: return@forEachIndexed
            val y = normY(si, v)
            drawCircle(s.color, 5.dp.toPx(), Offset(nx, y))
            drawCircle(bubbleBg, 3.dp.toPx(), Offset(nx, y))
        }

        // Bubble tooltip
        val mName = nearestDate.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .removeSuffix(".").lowercase().replaceFirstChar { it.uppercase() }
        val bubbleLines = buildList {
            add("${nearestDate.dayOfMonth}. $mName")
            validSeries.forEachIndexed { si, s ->
                s.data.firstOrNull { it.first == nearestDate }?.second?.let { v ->
                    val unitStr = if (s.unit.isNotEmpty()) " ${s.unit}" else ""
                    add("${s.label}: ${formatTickLabel(v)}$unitStr")
                }
            }
        }
        if (bubbleLines.size <= 1) return@Canvas

        val bTextPaint = android.graphics.Paint().apply {
            color = bubbleText.toArgb()
            textSize = 10 * d
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        val lineH = 14 * d
        val bH = bubbleLines.size * lineH + 10 * d
        val bW = bubbleLines.maxOf { bTextPaint.measureText(it) } + 16 * d
        val bX = (nx - bW / 2).coerceIn(padLeft, size.width - padRight - bW)
        val bY = 4 * d
        val bgPaint = android.graphics.Paint().apply {
            color = bubbleBg.toArgb()
            isAntiAlias = true
        }
        val rect = android.graphics.RectF(bX, bY, bX + bW, bY + bH)
        drawContext.canvas.nativeCanvas.apply {
            drawRoundRect(rect, 6 * d, 6 * d, bgPaint)
            bubbleLines.forEachIndexed { i, line ->
                drawText(line, bX + 8 * d, bY + (i + 1) * lineH + 2 * d, bTextPaint)
            }
        }
    }
}

// ── Correlation helpers ────────────────────────────────────────────────────────

/**
 * Pearson r between two date-keyed series, using only dates present in both.
 * Returns null when fewer than 5 aligned points exist.
 */
fun alignedPearson(
    a: List<Pair<LocalDate, Double>>,
    b: List<Pair<LocalDate, Double>>,
): Double? {
    val aMap = a.toMap()
    val bMap = b.toMap()
    val common = (aMap.keys intersect bMap.keys).sorted()
    if (common.size < 5) return null
    val xs = common.map { aMap[it]!! }
    val ys = common.map { bMap[it]!! }
    val mx = xs.average()
    val my = ys.average()
    val num = xs.zip(ys).sumOf { (x, y) -> (x - mx) * (y - my) }
    val dx = sqrt(xs.sumOf { (it - mx).pow(2) })
    val dy = sqrt(ys.sumOf { (it - my).pow(2) })
    if (dx < 1e-10 || dy < 1e-10) return null
    return (num / (dx * dy)).coerceIn(-1.0, 1.0)
}

/**
 * Human-readable label for a Pearson r value.
 * Examples: "Strong negative", "Moderate positive", "No correlation"
 */
fun correlationLabel(r: Double): String {
    val direction = if (r >= 0) "positive" else "negative"
    return when {
        abs(r) >= 0.7 -> "Strong $direction"
        abs(r) >= 0.4 -> "Moderate $direction"
        abs(r) >= 0.2 -> "Weak $direction"
        else -> "No correlation"
    }
}
