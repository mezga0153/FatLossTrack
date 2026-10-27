package com.fatlosstrack.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.measuredLeanMassKg
import com.fatlosstrack.domain.dailyTargetKcalFlow

/**
 * Collect the 6 profile preferences required for TDEE and derive the daily
 * calorie target.  Returns `null` when any required field is missing.
 *
 * Replaces the duplicated collect-and-remember block that was copied across
 * LogScreen, HomeScreen, and TrendsScreen.  The derivation itself lives in
 * [dailyTargetKcalFlow] so non-Compose callers (the home screen widget) share it.
 */
@Composable
fun rememberDailyTargetKcal(preferencesManager: PreferencesManager): Int? {
    val targetFlow = remember(preferencesManager) { preferencesManager.dailyTargetKcalFlow() }
    val target by targetFlow.collectAsState(initial = null)
    return target
}

/**
 * Returns the most recent measured lean mass (kg) from DailyLog data.
 * Prefers recorded [leanBodyMassKg] from HC, then derives from weight + body fat %.
 * Falls back to null when no body comp data has been synced.
 */
@Composable
fun rememberLatestLeanMassKg(dailyLogDao: DailyLogDao): Float? {
    val logs by dailyLogDao.getAllLogs().collectAsState(initial = emptyList())
    return remember(logs) {
        logs.firstOrNull { it.measuredLeanMassKg != null }?.measuredLeanMassKg?.toFloat()
    }
}
