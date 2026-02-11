package com.fatlosstrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.domain.TdeeCalculator

/**
 * Collect the 6 profile preferences required for TDEE and derive the daily
 * calorie target.  Returns `null` when any required field is missing.
 *
 * Replaces the duplicated collect-and-remember block that was copied across
 * LogScreen, HomeScreen, and TrendsScreen.
 */
@Composable
fun rememberDailyTargetKcal(preferencesManager: PreferencesManager): Int? {
    val sex by preferencesManager.sex.collectAsState(initial = null)
    val age by preferencesManager.age.collectAsState(initial = null)
    val height by preferencesManager.heightCm.collectAsState(initial = null)
    val startWeight by preferencesManager.startWeight.collectAsState(initial = null)
    val weeklyRate by preferencesManager.weeklyRate.collectAsState(initial = 0.5f)
    val activityLevel by preferencesManager.activityLevel.collectAsState(initial = "light")

    return remember(sex, age, height, startWeight, weeklyRate, activityLevel) {
        val s = sex ?: return@remember null
        val a = age ?: return@remember null
        val h = height ?: return@remember null
        val w = startWeight ?: return@remember null
        TdeeCalculator.dailyTarget(w, h, a, s, activityLevel, weeklyRate)
    }
}
