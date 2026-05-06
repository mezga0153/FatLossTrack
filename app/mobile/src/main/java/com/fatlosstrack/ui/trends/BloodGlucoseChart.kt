package com.fatlosstrack.ui.trends

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatlosstrack.data.local.db.BloodGlucoseEntry
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.displayTime
import com.fatlosstrack.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

// Normal glucose reference thresholds (mmol/L)
private const val BG_LOW = 3.9    // hypoglycemia boundary
private const val BG_HIGH = 7.8   // post-meal 2h target upper bound (IDF/ADA)
private const val BG_VERY_HIGH = 10.0 // TIR upper boundary

/**
 * Blood glucose line chart with carbohydrate bar chart overlay.
 *
 * - Shaded green band marks the 3.9–7.8 mmol/L target range.
 * - Dashed reference lines at 3.9, 7.8, and 10.0 mmol/L.
 * - Carb bars rise from the bottom; height ∝ grams of carbs.
 *   Color: green < 30g, amber 30–60g, red > 60g.
 * - BG readings drawn as a line on top; dots only for sparse data.
 * - Tap a bar to highlight it; caller owns [selectedMeal]/[onMealSelected] state.
 */
@Composable
fun BloodGlucoseChart(
    readings: List<BloodGlucoseEntry>,
    meals: List<MealEntry>,
    selectedMeal: MealEntry? = null,
    onMealSelected: (MealEntry?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (readings.isEmpty()) return

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }

    // Time axis
    val epochSecs = readings.map { it.timestamp.epochSecond.toDouble() }
    val tMin = epochSecs.min()
    val tMax = epochSecs.max()
    val tRange = (tMax - tMin).coerceAtLeast(3600.0)

    // BG Y axis — always include target range so reference lines are visible
    val bgValues = readings.map { it.valueMmolL }
    val vMin = (minOf(bgValues.min(), BG_LOW) - 0.3).coerceAtLeast(0.0)
    val vMax = maxOf(bgValues.max(), BG_HIGH) + 0.5

    // Meals within the time window (+ 5% padding)
    val mealsInRange = remember(meals, tMin, tMax) {
        meals.filter { m ->
            val t = m.displayTime.epochSecond.toDouble()
            t in (tMin - tRange * 0.05)..(tMax + tRange * 0.05)
        }
    }
    // Carb axis max — at least 50g so bars have room to grow
    val maxCarbs = remember(mealsInRange) {
        mealsInRange.maxOfOrNull { it.totalCarbsG }?.coerceAtLeast(50) ?: 50
    }

    // Only draw BG dots for sparse / manual data; CGM lines are clean without them
    val avgSpacingSec = if (readings.size > 1) tRange / (readings.size - 1) else Double.MAX_VALUE
    val showBgDots = readings.size <= 48 || avgSpacingSec > 270.0

    val lineColor = Color(0xFFE53E3E)
    val targetBandColor = Color(0xFF48BB78).copy(alpha = 0.07f)
    val gridLineColor = OnSurfaceVariant.copy(alpha = 0.12f)
    val dashEffect = remember { PathEffect.dashPathEffect(floatArrayOf(7f, 5f)) }

    // Pre-computed ARGB ints for nativeCanvas (no Compose colors allowed there)
    val gridTextArgb   = android.graphics.Color.argb(150, 139, 139, 163)
    val refLowArgb     = android.graphics.Color.argb(200,  72, 187, 120)  // green
    val refHighArgb    = android.graphics.Color.argb(200, 236, 201,  75)  // amber
    val refVeryHiArgb  = android.graphics.Color.argb(200, 252, 129, 129)  // red

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(mealsInRange) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val padL = 40f; val padR = 36f; val padT = 12f; val padB = 28f
                        val chartW = w - padL - padR
                        val chartH = h - padT - padB
                        val chartBottom = padT + chartH
                        val barHalfW = (chartW / (mealsInRange.size.coerceAtLeast(1) * 2.5f)).coerceIn(5f, 18f)

                        fun toX(t: Double) = padL + ((t - tMin) / tRange * chartW).toFloat()

                        // Hit-test: find nearest bar whose x ± halfW and vertical bar span contain the tap
                        val hit = mealsInRange.minByOrNull { meal ->
                            abs(tap.x - toX(meal.displayTime.epochSecond.toDouble()))
                        }
                        onMealSelected(if (hit != null) {
                            val mx = toX(hit.displayTime.epochSecond.toDouble())
                            val barH = (hit.totalCarbsG.toFloat() / maxCarbs * chartH).coerceAtLeast(6f)
                            val barTop = chartBottom - barH
                            if (abs(tap.x - mx) <= barHalfW + 8f && tap.y in (barTop - 8f)..chartBottom) hit else null
                        } else null)
                    }
                },
        ) {
            val w = size.width; val h = size.height
            val padL = 40f; val padR = 36f; val padT = 12f; val padB = 28f
            val chartW = w - padL - padR
            val chartH = h - padT - padB
            val chartBottom = padT + chartH

            fun toX(t: Double) = padL + ((t - tMin) / tRange * chartW).toFloat()
            fun toY(v: Double) = padT + ((vMax - v) / (vMax - vMin) * chartH).toFloat()

            val barHalfW = (chartW / (mealsInRange.size.coerceAtLeast(1) * 2.5f)).coerceIn(5f, 18f)

            // ── Target range shaded band (3.9–7.8) ──────────────────────────────
            val bandTop = toY(BG_HIGH.coerceAtMost(vMax))
            val bandBot = toY(BG_LOW.coerceAtLeast(vMin))
            drawRect(targetBandColor, topLeft = Offset(padL, bandTop), size = Size(chartW, bandBot - bandTop))

            // ── BG grid lines (left axis, integer mmol/L values) ─────────────────
            val gridStep = if ((vMax - vMin) <= 6) 1.0 else 2.0
            val gridStart = ceil(vMin / gridStep).toInt()
            val gridEnd = floor(vMax / gridStep).toInt()
            for (step in gridStart..gridEnd) {
                val v = step * gridStep
                val y = toY(v)
                drawLine(gridLineColor, Offset(padL, y), Offset(w - padR, y), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.drawText(
                    "%.0f".format(v), padL - 5f, y + 8f,
                    android.graphics.Paint().apply { color = gridTextArgb; textSize = 24f; textAlign = android.graphics.Paint.Align.RIGHT },
                )
            }

            // ── Reference lines ──────────────────────────────────────────────────
            data class RefLine(val value: Double, val label: String, val argb: Int)
            listOf(
                RefLine(BG_LOW,       "3.9", refLowArgb),
                RefLine(BG_HIGH,      "7.8", refHighArgb),
                RefLine(BG_VERY_HIGH, "10",  refVeryHiArgb),
            ).forEach { ref ->
                if (ref.value in vMin..vMax) {
                    val y = toY(ref.value)
                    drawLine(
                        Color(ref.argb),
                        Offset(padL, y), Offset(w - padR, y),
                        strokeWidth = 1.5f,
                        pathEffect = dashEffect,
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        ref.label, w - padR + 3f, y + 7f,
                        android.graphics.Paint().apply { color = ref.argb; textSize = 20f; textAlign = android.graphics.Paint.Align.LEFT },
                    )
                }
            }

            // ── Carb bars (drawn before BG line so line is on top) ───────────────
            mealsInRange.forEach { meal ->
                val x = toX(meal.displayTime.epochSecond.toDouble())
                val barH = (meal.totalCarbsG.toFloat() / maxCarbs * chartH).coerceAtLeast(4f)
                val barTop = chartBottom - barH
                val isSelected = selectedMeal?.id == meal.id
                val carbColor = when {
                    meal.totalCarbsG < 30 -> Color(0xFF48BB78)
                    meal.totalCarbsG < 60 -> Color(0xFFECC94B)
                    else                  -> Color(0xFFFC8181)
                }
                drawRect(
                    color = carbColor.copy(alpha = if (isSelected) 0.80f else 0.45f),
                    topLeft = Offset(x - barHalfW, barTop),
                    size = Size(barHalfW * 2, barH),
                )
                if (isSelected) {
                    drawRect(
                        color = carbColor,
                        topLeft = Offset(x - barHalfW, barTop),
                        size = Size(barHalfW * 2, barH),
                        style = Stroke(width = 1.5f),
                    )
                }
            }

            // ── BG line ──────────────────────────────────────────────────────────
            if (readings.size >= 2) {
                val path = Path()
                readings.forEachIndexed { i, r ->
                    val x = toX(r.timestamp.epochSecond.toDouble())
                    val y = toY(r.valueMmolL)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            }

            // ── BG dots (only for sparse / manual readings) ───────────────────
            if (showBgDots) {
                readings.forEach { r ->
                    val x = toX(r.timestamp.epochSecond.toDouble())
                    val y = toY(r.valueMmolL)
                    drawCircle(lineColor, radius = 3.5f, center = Offset(x, y))
                    drawCircle(Color.White.copy(alpha = 0.8f), radius = 1.8f, center = Offset(x, y))
                }
            }

            // ── X-axis time labels ───────────────────────────────────────────────
            val nLabels = 4
            repeat(nLabels + 1) { i ->
                val t = tMin + tRange * i / nLabels
                val x = toX(t)
                val label = java.time.Instant.ofEpochSecond(t.toLong())
                    .atZone(ZoneId.systemDefault()).format(timeFmt)
                drawContext.canvas.nativeCanvas.drawText(
                    label, x, h - 4f,
                    android.graphics.Paint().apply { color = gridTextArgb; textSize = 22f; textAlign = android.graphics.Paint.Align.CENTER },
                )
            }
        }

    }
}

/** Legend row shown above the chart */
@Composable
fun BloodGlucoseMealLegend() {
    val onSurfaceVariant = OnSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Carb bar levels
        listOf(
            Color(0xFF48BB78) to "< 30g",
            Color(0xFFECC94B) to "30–60g",
            Color(0xFFFC8181) to "> 60g",
        ).forEach { (color, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(width = 8.dp, height = 10.dp)) {
                    drawRect(color.copy(alpha = 0.55f), size = size)
                }
                Text(label, fontSize = 10.sp, color = onSurfaceVariant)
            }
        }
        // BG line swatch
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(width = 14.dp, height = 3.dp)) {
                drawLine(Color(0xFFE53E3E), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height)
            }
            Text("BG", fontSize = 10.sp, color = onSurfaceVariant)
        }
        // Target band swatch
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(width = 10.dp, height = 10.dp)) {
                drawRect(Color(0xFF48BB78).copy(alpha = 0.15f), size = size)
            }
            Text("3.9–7.8", fontSize = 10.sp, color = onSurfaceVariant)
        }
    }
}
