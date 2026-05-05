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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatlosstrack.data.local.db.BloodGlucoseEntry
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.local.db.displayTime
import com.fatlosstrack.ui.theme.*
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Blood glucose line chart with meal markers.
 *
 * - Draws BG readings as a connected line with dots.
 * - Overlays meal markers colored by carb content:
 *     green = low (< 30g), amber = medium (30–60g), red = high (> 60g)
 * - Tap a meal dot to see carb count in a tooltip.
 */
@Composable
fun BloodGlucoseChart(
    readings: List<BloodGlucoseEntry>,
    meals: List<MealEntry>,
    modifier: Modifier = Modifier,
) {
    if (readings.isEmpty()) return

    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var selectedMeal by remember { mutableStateOf<MealEntry?>(null) }

    // Convert to epoch-seconds for math
    val epochSecs = readings.map { it.timestamp.epochSecond.toDouble() }
    val tMin = epochSecs.min()
    val tMax = epochSecs.max()
    val tRange = (tMax - tMin).coerceAtLeast(3600.0) // at least 1h

    val bgValues = readings.map { it.valueMmolL }
    val vMin = (bgValues.min() - 0.5).coerceAtLeast(0.0)
    val vMax = bgValues.max() + 0.5

    // Meal data in range
    val mealsInRange = remember(meals, tMin, tMax) {
        meals.filter { m ->
            val t = m.displayTime.epochSecond.toDouble()
            t in (tMin - tRange * 0.05)..(tMax + tRange * 0.05)
        }
    }

    val lineColor = Color(0xFFE53E3E)
    // Capture @Composable theme colors before Canvas
    val gridLineColor = OnSurfaceVariant.copy(alpha = 0.12f)
    val gridTextArgb = android.graphics.Color.argb(150, 139, 139, 163)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(mealsInRange) {
                    detectTapGestures { tap ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        val padLeft = 40f; val padRight = 8f; val padTop = 12f; val padBot = 28f
                        val chartW = w - padLeft - padRight
                        val chartH = h - padTop - padBot

                        fun toX(t: Double) = padLeft + ((t - tMin) / tRange * chartW).toFloat()
                        fun toY(v: Double) = padTop + ((vMax - v) / (vMax - vMin) * chartH).toFloat()

                        val hit = mealsInRange.minByOrNull { meal ->
                            val mx = toX(meal.displayTime.epochSecond.toDouble())
                            val my = toY(readings.minByOrNull { abs(it.timestamp.epochSecond - meal.displayTime.epochSecond) }?.valueMmolL ?: ((vMin + vMax) / 2))
                            val dx = tap.x - mx; val dy = tap.y - my
                            dx * dx + dy * dy
                        }
                        selectedMeal = if (hit != null) {
                            val mx = toX(hit.displayTime.epochSecond.toDouble())
                            val my = toY(readings.minByOrNull { abs(it.timestamp.epochSecond - hit.displayTime.epochSecond) }?.valueMmolL ?: ((vMin + vMax) / 2))
                            val dist = Math.hypot((tap.x - mx).toDouble(), (tap.y - my).toDouble())
                            if (dist < 40) hit else null
                        } else null
                    }
                },
        ) {
            val w = size.width; val h = size.height
            val padLeft = 40f; val padRight = 8f; val padTop = 12f; val padBot = 28f
            val chartW = w - padLeft - padRight
            val chartH = h - padTop - padBot

            fun toX(t: Double) = padLeft + ((t - tMin) / tRange * chartW).toFloat()
            fun toY(v: Double) = padTop + ((vMax - v) / (vMax - vMin) * chartH).toFloat()

            // Grid lines
            val gridSteps = listOf(vMin, (vMin + vMax) / 2, vMax)
            gridSteps.forEach { v ->
                val y = toY(v)
                drawLine(gridLineColor, Offset(padLeft, y), Offset(w - padRight, y), strokeWidth = 1f)
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = gridTextArgb
                        textSize = 24f
                        textAlign = android.graphics.Paint.Align.RIGHT
                    }
                    drawText("%.1f".format(v), padLeft - 4f, y + 8f, paint)
                }
            }

            // BG line
            if (readings.size >= 2) {
                val path = Path()
                readings.forEachIndexed { i, r ->
                    val x = toX(r.timestamp.epochSecond.toDouble())
                    val y = toY(r.valueMmolL)
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lineColor, style = Stroke(width = 2.5f, cap = StrokeCap.Round))
            }

            // BG dots
            readings.forEach { r ->
                val x = toX(r.timestamp.epochSecond.toDouble())
                val y = toY(r.valueMmolL)
                drawCircle(lineColor, radius = 4f, center = Offset(x, y))
                drawCircle(Color.White.copy(alpha = 0.8f), radius = 2f, center = Offset(x, y))
            }

            // Meal dots
            mealsInRange.forEach { meal ->
                val t = meal.displayTime.epochSecond.toDouble()
                val nearestBg = readings.minByOrNull { abs(it.timestamp.epochSecond - meal.displayTime.epochSecond) }?.valueMmolL
                    ?: ((vMin + vMax) / 2)
                val x = toX(t)
                val y = toY(nearestBg)
                val carbs = meal.totalCarbsG
                val dotColor = when {
                    carbs < 30 -> Color(0xFF48BB78)   // green
                    carbs < 60 -> Color(0xFFECC94B)   // amber
                    else -> Color(0xFFFC8181)          // red
                }
                val isSelected = selectedMeal?.id == meal.id
                val radius = if (isSelected) 10f else 7f
                drawCircle(dotColor.copy(alpha = 0.9f), radius = radius, center = Offset(x, y))
                drawCircle(Color.White, radius = radius * 0.45f, center = Offset(x, y))
            }

            // X-axis time labels
            val nLabels = 4
            repeat(nLabels + 1) { i ->
                val t = tMin + tRange * i / nLabels
                val x = toX(t)
                val label = java.time.Instant.ofEpochSecond(t.toLong())
                    .atZone(ZoneId.systemDefault()).format(timeFmt)
                drawContext.canvas.nativeCanvas.apply {
                    val paint = android.graphics.Paint().apply {
                        color = gridTextArgb
                        textSize = 22f
                        textAlign = android.graphics.Paint.Align.CENTER
                    }
                    drawText(label, x, h - 4f, paint)
                }
            }
        }

        // Tooltip for selected meal
        selectedMeal?.let { meal ->
            val cardSurface = CardSurface
            val onSurface = OnSurface
            val tertiary = Tertiary
            val onSurfaceVariant = OnSurfaceVariant
            Box(modifier = Modifier.fillMaxSize()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = cardSurface,
                    tonalElevation = 4.dp,
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Text(
                            meal.description.take(30),
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurface,
                        )
                        Text(
                            "${meal.totalCarbsG}g carbs · ${meal.totalKcal} kcal",
                            style = MaterialTheme.typography.labelSmall,
                            color = tertiary,
                        )
                        Text(
                            meal.displayTime.atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("HH:mm, d MMM")),
                            style = MaterialTheme.typography.labelSmall,
                            color = onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Legend for meal dot colors */
@Composable
fun BloodGlucoseMealLegend() {
    val onSurfaceVariant = OnSurfaceVariant
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(
            Triple(Color(0xFF48BB78), "< 30g carbs", "Low"),
            Triple(Color(0xFFECC94B), "30–60g", "Med"),
            Triple(Color(0xFFFC8181), "> 60g", "High"),
        ).forEach { (color, _, label) ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Canvas(Modifier.size(8.dp)) { drawCircle(color) }
                Text(label, fontSize = 10.sp, color = onSurfaceVariant)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Canvas(Modifier.size(8.dp)) { drawCircle(Color(0xFFE53E3E)) }
            Text("BG reading", fontSize = 10.sp, color = onSurfaceVariant)
        }
    }
}
