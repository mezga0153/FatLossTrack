package com.fatlosstrack.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.ui.log.AddMealSheet
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Row of one-tap repeat chips that sits above [AiBar], plus the undo snackbar.
 * Tap logs a copy to today at the current time; long-press opens the add-meal sheet
 * prefilled with the meal so it can be adjusted before logging.
 *
 * @param visible hides the chips (e.g. while the AI bar shows a response); the snackbar
 *   and an open edit sheet stay put.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun QuickLogBar(
    state: QuickLogStateHolder,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
) {
    val today = LocalDate.now()
    val suggestions by remember(today) { state.suggestions(today) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<MealEntry?>(null) }
    val editSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val loggedFormat = stringResource(R.string.quick_log_logged)
    val undoLabel = stringResource(R.string.quick_log_undo)

    fun log(entry: MealEntry, reason: String) {
        scope.launch {
            val logged = state.log(entry, reason)
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = loggedFormat.format(logged.description, logged.totalKcal),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) state.undo(logged)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        SnackbarHost(snackbarHostState) { data ->
            Snackbar(
                snackbarData = data,
                modifier = Modifier.padding(horizontal = 16.dp),
                shape = RoundedCornerShape(12.dp),
                containerColor = CardSurface,
                contentColor = OnSurface,
                actionColor = Primary,
            )
        }

        // Keyboard up means the user is typing into the AI bar — keep the chips out of the way
        AnimatedVisibility(
            visible = visible && suggestions.isNotEmpty() && !WindowInsets.isImeVisible,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom),
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(suggestions, key = { it.key }) { meal ->
                    QuickMealChip(
                        meal = meal,
                        onClick = { log(state.copyForNow(meal.template), "chipTap") },
                        onLongClick = { editing = state.copyForNow(meal.template) },
                    )
                }
            }
        }
    }

    // Edit-before-logging sheet, outside the animated row so hiding the chips can't close it
    editing?.let { draft ->
        ModalBottomSheet(
            onDismissRequest = {
                scope.launch { editSheetState.hide() }.invokeOnCompletion { editing = null }
            },
            sheetState = editSheetState,
            containerColor = CardSurface,
        ) {
            AddMealSheet(
                date = draft.date,
                onSave = { newMeal ->
                    log(newMeal.copy(hasAlcohol = draft.hasAlcohol), "chipEdit")
                    scope.launch { editSheetState.hide() }.invokeOnCompletion { editing = null }
                },
                onDismiss = {
                    scope.launch { editSheetState.hide() }.invokeOnCompletion { editing = null }
                },
                prefillDescription = draft.description,
                prefillKcal = draft.totalKcal.takeIf { it > 0 },
                prefillProteinG = draft.totalProteinG.takeIf { it > 0 },
                prefillCarbsG = draft.totalCarbsG.takeIf { it > 0 },
                prefillFatG = draft.totalFatG.takeIf { it > 0 },
                prefillCategory = draft.category,
                prefillMealType = draft.mealType,
                prefillItemsJson = draft.itemsJson,
                showDateSelector = true,
            )
        }
    }
}

@Composable
private fun QuickMealChip(
    meal: QuickMeal,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Surface(
        shape = shape,
        color = AiBarBg,
        border = BorderStroke(1.dp, LocalAppColors.current.outline),
        shadowElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .combinedClickable(
                    onClickLabel = stringResource(R.string.quick_log_action_log),
                    onLongClickLabel = stringResource(R.string.quick_log_action_edit),
                    onLongClick = onLongClick,
                    onClick = onClick,
                )
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (meal.bookmarked) Icons.Default.Bookmark else Icons.Default.History,
                contentDescription = null,
                tint = if (meal.bookmarked) Secondary else OnSurfaceVariant,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                meal.label,
                style = MaterialTheme.typography.labelMedium,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            if (meal.template.totalKcal > 0) {
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.format_kcal, meal.template.totalKcal),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
            }
        }
    }
}
