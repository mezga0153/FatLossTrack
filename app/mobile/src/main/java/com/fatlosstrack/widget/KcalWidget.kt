package com.fatlosstrack.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
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

/** Hilt access for the widget, which isn't an injectable Android component. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface KcalWidgetEntryPoint {
    fun mealDao(): MealDao
    fun preferencesManager(): PreferencesManager
}

/** What the widget shows: today's eaten kcal against the TDEE-based target. */
private data class KcalWidgetData(
    val eatenKcal: Int,
    val targetKcal: Int?,
    val colors: AppColors,
)

/**
 * Home screen widget: kcal left today (TDEE target minus today's meals),
 * plus "Snap meal" and "Log meal" quick actions.
 *
 * Data is read once per update; [KcalWidgetUpdater] and [KcalWidgetReceiver]
 * trigger updates when meals/profile change and at midnight.
 */
class KcalWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadData(context)
        provideContent {
            KcalWidgetContent(data)
        }
    }

    private suspend fun loadData(context: Context): KcalWidgetData {
        val entryPoint = EntryPointAccessors.fromApplication(context, KcalWidgetEntryPoint::class.java)
        val prefs = entryPoint.preferencesManager()
        val eaten = entryPoint.mealDao().getMealsForDate(LocalDate.now()).first().sumOf { it.totalKcal }
        val target = prefs.dailyTargetKcalFlow().first()
        // Follow the app's theme preset so the widget matches the app
        val preset = try {
            ThemePreset.valueOf(prefs.themePreset.first())
        } catch (_: Exception) {
            ThemePreset.PURPLE_DARK
        }
        return KcalWidgetData(eaten, target, buildAppColors(preset.accentHue, preset.mode))
    }
}

@Composable
private fun KcalWidgetContent(data: KcalWidgetData) {
    val context = LocalContext.current
    val colors = data.colors
    val target = data.targetKcal
    val left = target?.let { it - data.eatenKcal }

    val label: String
    val value: String
    val valueColor: Color
    val detail: String
    when {
        target == null || left == null -> {
            label = context.getString(R.string.widget_eaten_today)
            value = context.getString(R.string.format_kcal, data.eatenKcal)
            valueColor = colors.onSurface
            detail = context.getString(R.string.widget_no_target)
        }
        left >= 0 -> {
            label = context.getString(R.string.widget_left_today)
            value = context.getString(R.string.format_kcal, left)
            valueColor = colors.primary
            detail = context.getString(R.string.widget_eaten_of_target, data.eatenKcal, target)
        }
        else -> {
            label = context.getString(R.string.widget_over_today)
            value = context.getString(R.string.format_kcal, -left)
            valueColor = colors.error
            detail = context.getString(R.string.widget_eaten_of_target, data.eatenKcal, target)
        }
    }

    // Tapping anywhere outside the buttons opens the app like the launcher icon does
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(20.dp)
            .background(ColorProvider(colors.cardSurface))
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .then(if (openApp != null) GlanceModifier.clickable(actionStartActivity(openApp)) else GlanceModifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = TextStyle(color = ColorProvider(colors.onSurfaceVariant), fontSize = 12.sp),
            maxLines = 1,
        )
        Text(
            text = value,
            style = TextStyle(
                color = ColorProvider(valueColor),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Text(
            text = detail,
            style = TextStyle(color = ColorProvider(colors.onSurfaceVariant), fontSize = 12.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(10.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            QuickActionButton(
                iconRes = R.drawable.ic_widget_camera,
                label = context.getString(R.string.shortcut_snap_meal_short),
                action = actionStartActivity(QuickAction.SnapMeal.intent(context)),
                colors = colors,
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            QuickActionButton(
                iconRes = R.drawable.ic_widget_edit,
                label = context.getString(R.string.shortcut_log_meal_short),
                action = actionStartActivity(QuickAction.LogMeal.intent(context)),
                colors = colors,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
    }
}

@Composable
private fun QuickActionButton(
    iconRes: Int,
    label: String,
    action: Action,
    colors: AppColors,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .cornerRadius(20.dp)
            .background(ColorProvider(colors.primaryContainer))
            .padding(horizontal = 10.dp)
            .clickable(action),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            provider = ImageProvider(iconRes),
            contentDescription = null,
            colorFilter = ColorFilter.tint(ColorProvider(colors.primary)),
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = label,
            style = TextStyle(
                color = ColorProvider(colors.onSurface),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
    }
}
