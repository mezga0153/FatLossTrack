package com.fatlosstrack.ui.log

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.AppLogger
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.theme.CardSurface
import kotlinx.coroutines.launch
import java.time.LocalDate

// ── Sheet state holder ──

class LogSheetState {
    var editingDate by mutableStateOf<LocalDate?>(null)
    var selectedMeal by mutableStateOf<MealEntry?>(null)
    var addMealForDate by mutableStateOf<LocalDate?>(null)
}

@Composable
fun rememberLogSheetState(): LogSheetState = remember { LogSheetState() }

// ── Sheet host — renders the 3 ModalBottomSheets ──

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogSheetHost(
    state: LogSheetState,
    logsByDate: Map<LocalDate, DailyLog>,
    mealDao: MealDao,
    dailyLogDao: DailyLogDao,
    daySummaryGenerator: DaySummaryGenerator?,
    openAiService: OpenAiService?,
    onCameraForDate: (LocalDate) -> Unit = {},
    logTag: String = "SheetHost",
) {
    val scope = rememberCoroutineScope()
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val mealSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val addMealSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // ── Daily log edit sheet ──
    if (state.editingDate != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { editSheetState.hide() }.invokeOnCompletion {
                    state.editingDate = null
                }
            },
            sheetState = editSheetState,
            containerColor = CardSurface,
        ) {
            DailyLogEditSheet(
                date = state.editingDate!!,
                existingLog = logsByDate[state.editingDate!!],
                onSave = { log ->
                    scope.launch {
                        dailyLogDao.upsert(log)
                        val parts = mutableListOf<String>()
                        log.weightKg?.let { w -> parts += "weight=%.1f".format(w) }
                        log.steps?.let { s -> parts += "steps=$s" }
                        log.sleepHours?.let { s -> parts += "sleep=${s}h" }
                        log.restingHr?.let { h -> parts += "hr=$h" }
                        AppLogger.instance?.user("DailyLog saved ${log.date}: ${parts.joinToString(", ")}")
                        launchSummary(log.date, dailyLogDao, daySummaryGenerator, "$logTag:dailyLogEdit")
                        editSheetState.hide()
                        state.editingDate = null
                    }
                },
                onDismiss = {
                    scope.launch { editSheetState.hide() }.invokeOnCompletion {
                        state.editingDate = null
                    }
                },
            )
        }
    }

    // ── Meal detail / edit sheet ──
    if (state.selectedMeal != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { mealSheetState.hide() }.invokeOnCompletion {
                    state.selectedMeal = null
                }
            },
            sheetState = mealSheetState,
            containerColor = CardSurface,
        ) {
            MealEditSheet(
                meal = state.selectedMeal!!,
                onSave = { updated ->
                    scope.launch {
                        mealDao.update(updated)
                        AppLogger.instance?.meal("Edited: ${updated.description.take(40)} — ${updated.totalKcal} kcal, ${updated.totalProteinG}g P, ${updated.totalCarbsG}g C, ${updated.totalFatG}g F, date=${updated.date}")
                        launchSummary(updated.date, dailyLogDao, daySummaryGenerator, "$logTag:mealEdit")
                        mealSheetState.hide()
                        state.selectedMeal = null
                    }
                },
                onDelete = {
                    scope.launch {
                        val meal = state.selectedMeal!!
                        AppLogger.instance?.meal("Deleted: ${meal.description.take(40)} — ${meal.totalKcal} kcal, date=${meal.date}")
                        mealDao.delete(meal)
                        launchSummary(meal.date, dailyLogDao, daySummaryGenerator, "$logTag:mealDelete")
                        mealSheetState.hide()
                        state.selectedMeal = null
                    }
                },
                onDismiss = {
                    scope.launch { mealSheetState.hide() }.invokeOnCompletion {
                        state.selectedMeal = null
                    }
                },
                openAiService = openAiService,
            )
        }
    }

    // ── Add meal manually sheet ──
    if (state.addMealForDate != null) {
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { addMealSheetState.hide() }.invokeOnCompletion {
                    state.addMealForDate = null
                }
            },
            sheetState = addMealSheetState,
            containerColor = CardSurface,
        ) {
            AddMealSheet(
                date = state.addMealForDate!!,
                onSave = { newMeal ->
                    scope.launch {
                        mealDao.insert(newMeal)
                        AppLogger.instance?.meal("Added: ${newMeal.description.take(40)} — ${newMeal.totalKcal} kcal, ${newMeal.totalProteinG}g P, ${newMeal.totalCarbsG}g C, ${newMeal.totalFatG}g F, cat=${newMeal.category}, type=${newMeal.mealType}, date=${newMeal.date}")
                        launchSummary(newMeal.date, dailyLogDao, daySummaryGenerator, "$logTag:mealAdd")
                        addMealSheetState.hide()
                        state.addMealForDate = null
                    }
                },
                onDismiss = {
                    scope.launch { addMealSheetState.hide() }.invokeOnCompletion {
                        state.addMealForDate = null
                    }
                },
                onCamera = {
                    val date = state.addMealForDate!!
                    scope.launch { addMealSheetState.hide() }.invokeOnCompletion {
                        state.addMealForDate = null
                        onCameraForDate(date)
                    }
                },
            )
        }
    }
}
