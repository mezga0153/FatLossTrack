package com.fatlosstrack.domain

import com.fatlosstrack.data.local.PreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Daily calorie target derived from the saved profile via [TdeeCalculator.dailyTarget].
 * Emits `null` while sex, age, height or start weight is missing.
 *
 * Non-Compose source shared by `rememberDailyTargetKcal()` and the home screen widget.
 */
fun PreferencesManager.dailyTargetKcalFlow(): Flow<Int?> = combine(
    sex,
    age,
    heightCm,
    startWeight,
    combine(weeklyRate, activityLevel, ::Pair),
) { s, a, h, w, (rate, activity) ->
    if (s == null || a == null || h == null || w == null) null
    else TdeeCalculator.dailyTarget(w, h, a, s, activity, rate)
}.distinctUntilChanged()
