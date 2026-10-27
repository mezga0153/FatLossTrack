package com.fatlosstrack.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.di.ApplicationScope
import com.fatlosstrack.domain.dailyTargetKcalFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Refreshes [KcalWidget] while the app process is alive, whenever anything it shows
 * changes: meals added/edited/deleted (from any screen, via Room's table invalidation),
 * the profile behind the TDEE target, the theme preset or the language.
 * Midnight rollover is handled by [KcalWidgetReceiver]'s alarm.
 */
@Singleton
class KcalWidgetUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mealDao: MealDao,
    private val preferencesManager: PreferencesManager,
    private val appLogger: AppLogger,
    @ApplicationScope private val appScope: CoroutineScope,
) {
    private data class Snapshot(
        val date: LocalDate,
        val eatenKcal: Int,
        val targetKcal: Int?,
        val themePreset: String,
        val language: String,
    )

    fun start() {
        appScope.launch {
            combine(
                // Window starts at launch day so today's meals stay in it past midnight
                mealDao.getMealsSince(LocalDate.now()),
                preferencesManager.dailyTargetKcalFlow(),
                preferencesManager.themePreset,
                preferencesManager.language,
            ) { meals, target, theme, language ->
                val today = LocalDate.now()
                Snapshot(today, meals.filter { it.date == today }.sumOf { it.totalKcal }, target, theme, language)
            }
                .distinctUntilChanged()
                .collectLatest {
                    // Coalesce bursts (e.g. language pref written just before the locale switch)
                    delay(UPDATE_DEBOUNCE_MS)
                    try {
                        KcalWidget().updateAll(context)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        appLogger.error("Widget", "Kcal widget update failed", e)
                    }
                }
        }
    }

    private companion object {
        const val UPDATE_DEBOUNCE_MS = 500L
    }
}
