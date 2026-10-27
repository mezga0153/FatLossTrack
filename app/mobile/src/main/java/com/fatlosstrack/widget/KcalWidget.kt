package com.fatlosstrack.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.fatlosstrack.R
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.domain.TdeeCalculator
import com.fatlosstrack.domain.dailyTargetKcalFlow
import com.fatlosstrack.ui.QuickAction
import com.fatlosstrack.ui.theme.AppColors
import com.fatlosstrack.ui.theme.ThemePreset
import com.fatlosstrack.ui.theme.buildAppColors
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import kotlin.math.roundToInt

/** Hilt access for the widget, which isn't an injectable Android component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface KcalWidgetEntryPoint {
    fun mealDao(): MealDao
    fun preferencesManager(): PreferencesManager
}

/** What the widget shows: today's eaten kcal and protein against the TDEE-based targets. */
private data class KcalWidgetData(
    val eatenKcal: Int,
    val targetKcal: Int?,
    val eatenProteinG: Int,
    val proteinTargetG: Int?,
    val colors: AppColors,
)

/**
 * Home screen widget: a progress ring with kcal left today (TDEE target minus today's meals),
 * protein vs target when wide enough, plus "Snap meal" and "Log meal" icon buttons.
 *
 * Data is read once per update; [KcalWidgetUpdater] and [KcalWidgetReceiver]
 * trigger updates when meals/profile change and at midnight.
 */
class KcalWidget : GlanceAppWidget() {

    // Compact layout for 2×1, detail column once there's room for it
    override val sizeMode = SizeMode.Responsive(setOf(COMPACT, WIDE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent {
            KcalWidgetContent(data)
        }
    }

    private suspend fun loadData(context: Context): KcalWidgetData {
        val entryPoint = EntryPointAccessors.fromApplication(context, KcalWidgetEntryPoint::class.java)
        val prefs = entryPoint.preferencesManager()
        val meals = entryPoint.mealDao().getMealsForDate(LocalDate.now()).first()
        val target = prefs.dailyTargetKcalFlow().first()
        val proteinTarget = target?.let {
            TdeeCalculator.macroTargets(it, goalBodyWeightKg = prefs.goalWeight.first()?.takeIf { w -> w > 0f }).first
        }
        // Follow the app's theme preset so the widget matches the app
        val preset = try {
            ThemePreset.valueOf(prefs.themePreset.first())
        } catch (_: Exception) {
            ThemePreset.PURPLE_DARK
        }
        return KcalWidgetData(
            eatenKcal = meals.sumOf { it.totalKcal },
            targetKcal = target,
            eatenProteinG = meals.sumOf { it.totalProteinG },
            proteinTargetG = proteinTarget,
            colors = buildAppColors(preset.accentHue, preset.mode),
        )
    }
}

@Composable
private fun KcalWidgetContent(data: KcalWidgetData) {
    val context = LocalContext.current
    val colors = data.colors
    val target = data.targetKcal
    val left = target?.let { it - data.eatenKcal }
    val wide = LocalSize.current.width >= WIDE.width

    // Number in the ring: kcal left, kcal over, or kcal eaten when there's no target
    val ringValue: Int
    val ringLabel: String
    val ringValueColor: Color
    when {
        left == null -> {
            ringValue = data.eatenKcal
            ringLabel = context.getString(R.string.widget_ring_eaten)
            ringValueColor = colors.onSurface
        }
        left >= 0 -> {
            ringValue = left
            ringLabel = context.getString(R.string.widget_ring_left)
            ringValueColor = colors.onSurface
        }
        else -> {
            ringValue = -left
            ringLabel = context.getString(R.string.widget_ring_over)
            ringValueColor = colors.error
        }
    }

    // Tapping anywhere outside the buttons opens the app like the launcher icon does
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(24.dp)
            .background(ColorProvider(colors.cardSurface))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .then(if (openApp != null) GlanceModifier.clickable(actionStartActivity(openApp)) else GlanceModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier.size(RING_SIZE),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(
                    ringBitmap(context, data.eatenKcal, target, colors),
                ),
                contentDescription = null,
                modifier = GlanceModifier.size(RING_SIZE),
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = ringValue.toString(),
                    style = TextStyle(
                        color = ColorProvider(ringValueColor),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = ringLabel,
                    style = TextStyle(color = ColorProvider(colors.onSurfaceVariant), fontSize = 9.sp),
                    maxLines = 1,
                )
            }
        }

        if (wide) {
            Spacer(GlanceModifier.width(12.dp))
            DetailColumn(data, colors, GlanceModifier.defaultWeight())
        } else {
            Spacer(GlanceModifier.defaultWeight())
        }

        Column {
            QuickActionButton(
                iconRes = R.drawable.ic_widget_camera,
                description = context.getString(R.string.shortcut_snap_meal_short),
                action = actionStartActivity(QuickAction.SnapMeal.intent(context)),
                colors = colors,
            )
            Spacer(GlanceModifier.height(6.dp))
            QuickActionButton(
                iconRes = R.drawable.ic_widget_edit,
                description = context.getString(R.string.shortcut_log_meal_short),
                action = actionStartActivity(QuickAction.LogMeal.intent(context)),
                colors = colors,
            )
        }
    }
}

/** Kcal and protein against target, shown next to the ring when the widget is wide enough. */
@Composable
private fun DetailColumn(data: KcalWidgetData, colors: AppColors, modifier: GlanceModifier) {
    val context = LocalContext.current
    val target = data.targetKcal
    Column(modifier = modifier) {
        Text(
            text = context.getString(R.string.widget_name),
            style = TextStyle(
                color = ColorProvider(colors.onSurface),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        if (target == null) {
            Text(
                text = context.getString(R.string.widget_no_target),
                style = TextStyle(color = ColorProvider(colors.onSurfaceVariant), fontSize = 11.sp),
                maxLines = 2,
            )
            return@Column
        }
        Text(
            text = context.getString(R.string.widget_kcal_of_target, data.eatenKcal, target),
            style = TextStyle(color = ColorProvider(colors.onSurfaceVariant), fontSize = 11.sp),
            maxLines = 1,
        )
        data.proteinTargetG?.takeIf { it > 0 }?.let { proteinTarget ->
            Spacer(GlanceModifier.height(6.dp))
            Text(
                text = context.getString(R.string.widget_protein_of_target, data.eatenProteinG, proteinTarget),
                style = TextStyle(color = ColorProvider(colors.onSurfaceVariant), fontSize = 11.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(3.dp))
            LinearProgressIndicator(
                progress = (data.eatenProteinG.toFloat() / proteinTarget).coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(4.dp),
                color = ColorProvider(colors.secondary),
                backgroundColor = ColorProvider(colors.onSurface.copy(alpha = 0.10f)),
            )
        }
    }
}

/** Round icon-only button; labels didn't fit at small widget sizes. */
@Composable
private fun QuickActionButton(
    iconRes: Int,
    description: String,
    action: Action,
    colors: AppColors,
) {
    Box(
        modifier = GlanceModifier
            .size(30.dp)
            .cornerRadius(15.dp)
            .background(ColorProvider(colors.primaryContainer))
            .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = description,
            colorFilter = ColorFilter.tint(ColorProvider(colors.primary)),
            modifier = GlanceModifier.size(16.dp),
        )
    }
}

/**
 * Progress ring drawn to a bitmap, since Glance has no arc primitive. Primary→accent
 * sweep while under target, a full error-colored ring once over, just the track without one.
 */
private fun ringBitmap(context: Context, eatenKcal: Int, targetKcal: Int?, colors: AppColors): Bitmap {
    val px = (RING_SIZE.value * context.resources.displayMetrics.density).roundToInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val stroke = px * 0.11f
    val inset = stroke / 2f
    val bounds = RectF(inset, inset, px - inset, px - inset)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        strokeCap = Paint.Cap.ROUND
    }

    paint.color = colors.onSurface.copy(alpha = 0.10f).toArgb()
    canvas.drawArc(bounds, 0f, 360f, false, paint)
    if (targetKcal == null || targetKcal <= 0 || eatenKcal <= 0) return bitmap

    val fraction = eatenKcal.toFloat() / targetKcal
    if (fraction > 1f) {
        paint.color = colors.error.toArgb()
        canvas.drawArc(bounds, 0f, 360f, false, paint)
    } else {
        val center = px / 2f
        paint.shader = SweepGradient(
            center, center,
            intArrayOf(colors.primary.toArgb(), colors.accent.toArgb(), colors.primary.toArgb()),
            null,
        ).apply { setLocalMatrix(Matrix().apply { setRotate(-90f, center, center) }) }
        canvas.drawArc(bounds, -90f, 360f * fraction, false, paint)
    }
    return bitmap
}

private val RING_SIZE = 60.dp
private val COMPACT = DpSize(110.dp, 50.dp)
private val WIDE = DpSize(220.dp, 50.dp)
