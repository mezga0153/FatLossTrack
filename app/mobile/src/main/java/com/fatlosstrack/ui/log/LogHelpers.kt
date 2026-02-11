package com.fatlosstrack.ui.log

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.DailyLogDao
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate

// ── Category / meal-type helpers ──

@Composable
internal fun categoryLabel(c: MealCategory) = when (c) {
    MealCategory.HOME -> stringResource(R.string.category_home)
    MealCategory.RESTAURANT -> stringResource(R.string.category_restaurant)
    MealCategory.FAST_FOOD -> stringResource(R.string.category_fast_food)
}

internal fun categoryIcon(c: MealCategory) = when (c) {
    MealCategory.HOME -> Icons.Default.Home
    MealCategory.RESTAURANT -> Icons.Default.Restaurant
    MealCategory.FAST_FOOD -> Icons.Default.Fastfood
}

@Composable
internal fun categoryColor(c: MealCategory) = when (c) {
    MealCategory.HOME -> Secondary
    MealCategory.RESTAURANT -> Accent
    MealCategory.FAST_FOOD -> Tertiary
}

@Composable
internal fun mealTypeLabel(t: MealType) = when (t) {
    MealType.BREAKFAST -> stringResource(R.string.meal_type_breakfast)
    MealType.BRUNCH -> stringResource(R.string.meal_type_brunch)
    MealType.LUNCH -> stringResource(R.string.meal_type_lunch)
    MealType.DINNER -> stringResource(R.string.meal_type_dinner)
    MealType.SNACK -> stringResource(R.string.meal_type_snack)
}

// ── Shared field composables ──

@Composable
internal fun EditField(icon: ImageVector, label: String, value: String, onValueChange: (String) -> Unit, keyboardType: KeyboardType = KeyboardType.Text, iconTint: Color = Primary) {
    OutlinedTextField(
        value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), label = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp)) },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Next),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurface), colors = editFieldColors(), shape = RoundedCornerShape(10.dp),
    )
}

@Composable
internal fun editFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = SurfaceVariant, unfocusedContainerColor = SurfaceVariant,
    focusedBorderColor = Primary.copy(alpha = 0.5f), unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
    cursorColor = Primary,
)

// ── JSON helpers ──

internal data class ExerciseItem(val name: String, val durationMin: Int, val kcal: Int)
internal data class ParsedMealItem(val name: String, val portion: String, val calories: Int)

internal fun parseExercises(json: String?): List<ExerciseItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        Json.parseToJsonElement(json).jsonArray.map { el ->
            val obj = el.jsonObject
            ExerciseItem(
                name = obj["name"]?.jsonPrimitive?.content ?: "Exercise",
                durationMin = obj["durationMin"]?.jsonPrimitive?.intOrNull ?: 0,
                kcal = obj["kcal"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    } catch (_: Exception) { emptyList() }
}

internal fun parseItems(json: String?): List<ParsedMealItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        Json.parseToJsonElement(json).jsonArray.map { el ->
            val obj = el.jsonObject
            ParsedMealItem(
                name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                portion = obj["portion"]?.jsonPrimitive?.content ?: "",
                calories = obj["calories"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    } catch (_: Exception) { emptyList() }
}

// ── Summary helpers ──

internal const val SUMMARY_PLACEHOLDER = "⏳"
internal val summaryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

/**
 * Writes a placeholder to the daySummary field immediately so the UI shows
 * a loading indicator, then generates the real summary in the background.
 */
internal fun launchSummary(date: LocalDate, dailyLogDao: DailyLogDao, generator: DaySummaryGenerator?, reason: String = "unknown") {
    if (generator == null) return
    summaryScope.launch {
        val existing = dailyLogDao.getForDate(date) ?: DailyLog(date = date)
        dailyLogDao.upsert(existing.copy(daySummary = SUMMARY_PLACEHOLDER))
        generator.generateForDate(date, reason)
    }
}
