package com.fatlosstrack.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.db.DailyLog
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.ui.theme.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

// ── Day Card ──

@Composable
internal fun DayCard(
    date: LocalDate,
    log: DailyLog?,
    meals: List<MealEntry>,
    dailyTargetKcal: Int? = null,
    onEdit: () -> Unit,
    onMealClick: (MealEntry) -> Unit,
    onAddMeal: () -> Unit,
) {
    val dateLabel = when (date) {
        LocalDate.now() -> stringResource(R.string.day_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.day_yesterday)
        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                ", " + date.format(DateTimeFormatter.ofPattern("d MMM"))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(dateLabel, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold), color = OnSurface)
                IconButton(onClick = onEdit, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.cd_edit), tint = Primary, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(8.dp))

            // Stats chips
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                StatChip(Icons.Default.Scale, log?.weightKg?.let { "%.1f kg".format(it) }, stringResource(R.string.stat_weight))
                StatChip(Icons.AutoMirrored.Filled.DirectionsWalk, log?.steps?.let { "%,d".format(it) }, stringResource(R.string.stat_steps))
                StatChip(Icons.Default.Bedtime, log?.sleepHours?.let { "%.1fh".format(it) }, stringResource(R.string.stat_sleep))
                StatChip(Icons.Default.FavoriteBorder, log?.restingHr?.let { "$it bpm" }, stringResource(R.string.stat_heart_rate))
            }

            // Meals section
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.section_meals), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                IconButton(onClick = onAddMeal, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.cd_add_meal), tint = Primary, modifier = Modifier.size(18.dp))
                }
            }

            if (meals.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                // Sort: breakfast → brunch → lunch → dinner, snacks between, null-type at end
                val sortedMeals = meals.sortedWith(compareByDescending(nullsFirst()) { it.mealType?.ordinal })
                val dayTotalKcal = meals.sumOf { it.totalKcal }
                val dayTotalProtein = meals.sumOf { it.totalProteinG }
                val dayTotalCarbs = meals.sumOf { it.totalCarbsG }
                val dayTotalFat = meals.sumOf { it.totalFatG }
                // Macro targets from daily calorie target (for percentage calculations)
                val macroTargets = dailyTargetKcal?.let { com.fatlosstrack.domain.TdeeCalculator.macroTargets(it) }
                val targetProtein = macroTargets?.first ?: dayTotalProtein
                val targetCarbs = macroTargets?.second ?: dayTotalCarbs
                val targetFat = macroTargets?.third ?: dayTotalFat
                sortedMeals.forEach { meal ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                            .clickable { onMealClick(meal) }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        // Line 1: meal type, description
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (meal.mealType != null) {
                                Text(
                                    mealTypeLabel(meal.mealType),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Accent,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("·", style = MaterialTheme.typography.labelSmall, color = OnSurfaceVariant)
                                Spacer(Modifier.width(4.dp))
                            }
                            Text(
                                meal.description.take(40) + if (meal.description.length > 40) "\u2026" else "",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurface,
                                maxLines = 1,
                            )
                        }
                        // Line 2: kcal P C F with percentages (of daily target when available, else day total)
                        Row(
                            modifier = Modifier.padding(top = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (dailyTargetKcal != null && dailyTargetKcal > 0) {
                                val kcalPct = meal.totalKcal * 100 / dailyTargetKcal
                                Text(
                                    "${meal.totalKcal} kcal ($kcalPct%)",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Secondary,
                                )
                            } else {
                                Text(
                                    "${meal.totalKcal} kcal",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = Secondary,
                                )
                            }
                            if (meal.totalProteinG > 0) {
                                val pct = if (targetProtein > 0) (meal.totalProteinG * 100 / targetProtein) else 0
                                Text(
                                    "P ${meal.totalProteinG}g ($pct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Primary,
                                )
                            }
                            if (meal.totalCarbsG > 0) {
                                val pct = if (targetCarbs > 0) (meal.totalCarbsG * 100 / targetCarbs) else 0
                                Text(
                                    "C ${meal.totalCarbsG}g ($pct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Tertiary,
                                )
                            }
                            if (meal.totalFatG > 0) {
                                val pct = if (targetFat > 0) (meal.totalFatG * 100 / targetFat) else 0
                                Text(
                                    "F ${meal.totalFatG}g ($pct%)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Accent,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                val totalKcal = dayTotalKcal
                val totalProtein = dayTotalProtein
                val totalCarbs = dayTotalCarbs
                val totalFat = dayTotalFat
                val macroSuffix = buildString {
                    if (totalProtein > 0) append(" · ${totalProtein}g P")
                    if (totalCarbs > 0) append(" · ${totalCarbs}g C")
                    if (totalFat > 0) append(" · ${totalFat}g F")
                }
                // Total row: show % of daily target if available
                val targetSuffix = if (dailyTargetKcal != null && dailyTargetKcal > 0) {
                    " / $dailyTargetKcal (${totalKcal * 100 / dailyTargetKcal}%)"
                } else ""
                Text(
                    stringResource(R.string.meals_total_kcal, totalKcal) + targetSuffix + macroSuffix,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Secondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Text(
                    stringResource(R.string.no_meals_logged),
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceVariant.copy(alpha = 0.4f),
                )
            }

            // Exercises
            val exercises = parseExercises(log?.exercisesJson)
            if (exercises.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.section_exercises), style = MaterialTheme.typography.labelMedium, color = OnSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                exercises.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Surface)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(ex.name, style = MaterialTheme.typography.bodySmall, color = OnSurface)
                        Text(
                            buildString {
                                if (ex.durationMin > 0) append("${ex.durationMin}min")
                                if (ex.kcal > 0) { if (isNotEmpty()) append(" \u00b7 "); append("${ex.kcal} kcal") }
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = Secondary,
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                }
            }

            // Notes
            if (!log?.notes.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(log!!.notes!!, style = MaterialTheme.typography.bodySmall, color = OnSurfaceVariant)
            }

            // AI day summary
            if (!log?.daySummary.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                val isLoading = log!!.daySummary == SUMMARY_PLACEHOLDER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Primary.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.AutoAwesome,
                        contentDescription = stringResource(R.string.cd_ai_summary),
                        tint = Primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    if (isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = Primary.copy(alpha = 0.5f),
                            trackColor = Primary.copy(alpha = 0.1f),
                        )
                    } else {
                        Text(
                            log.daySummary!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurface,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun RowScope.StatChip(icon: ImageVector, value: String?, label: String) {
    Column(
        modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).background(Surface).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = label, tint = if (value != null) Primary else OnSurfaceVariant.copy(alpha = 0.3f), modifier = Modifier.size(14.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            value ?: "\u2014",
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = if (value != null) OnSurface else OnSurfaceVariant.copy(alpha = 0.3f),
        )
    }
}
