package com.fatlosstrack.ui.recap

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.domain.recap.WeeklyRecap
import com.fatlosstrack.ui.components.InfoCard
import com.fatlosstrack.ui.theme.Primary
import com.fatlosstrack.ui.theme.Secondary
import com.fatlosstrack.ui.theme.Tertiary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/**
 * Weekly recap — the numbers behind the recap notification plus an
 * AI-written paragraph in the active coach tone (generated on open).
 */
@Composable
fun WeeklyRecapScreen(
    state: WeeklyRecapStateHolder,
    end: LocalDate,
    onBack: () -> Unit,
) {
    LaunchedEffect(end) { state.open(end) }

    // Hold off on rendering until the requested week is loaded, so a stale week never flashes
    val recap = state.recap?.takeIf { it.end == end }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = statusBarTop + 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Top bar
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.recap_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        R.string.recap_range,
                        end.minusDays(6).format(dateFormat),
                        end.format(dateFormat),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (recap == null) {
            Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Primary)
            }
            return@Column
        }

        if (!recap.hasData) {
            InfoCard {
                Text(
                    stringResource(R.string.recap_no_data),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        CoachCard(state)
        RecapStats(recap)
    }
}

@Composable
private fun CoachCard(state: WeeklyRecapStateHolder) {
    InfoCard(label = stringResource(R.string.recap_coach_label), icon = Icons.Default.AutoAwesome) {
        when {
            state.coachLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = Primary,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.recap_coach_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.coachText != null -> Text(
                state.coachText!!,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            state.coachError == RecapCoachError.NO_API_KEY -> Text(
                stringResource(R.string.recap_coach_no_key),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.coachError == RecapCoachError.FAILED -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.recap_coach_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Tertiary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { state.retry() }) {
                    Text(stringResource(R.string.recap_retry), color = Primary)
                }
            }
        }
    }
}

@Composable
private fun RecapStats(recap: WeeklyRecap) {
    val noData = stringResource(R.string.placeholder_no_data)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val change = recap.trendChangeKg
        StatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.recap_weight_trend),
            icon = Icons.Default.MonitorWeight,
            value = change?.let { stringResource(R.string.recap_kg_value, "%+.1f".format(it)) } ?: noData,
            // Losing is the goal: down is good, up is flagged
            valueColor = when {
                change == null -> MaterialTheme.colorScheme.onSurface
                change < 0 -> Secondary
                change > 0 -> Tertiary
                else -> MaterialTheme.colorScheme.onSurface
            },
            detail = if (change != null && recap.trendKg != null) {
                stringResource(R.string.recap_trend_now, "%.1f".format(recap.trendKg))
            } else {
                stringResource(R.string.recap_not_enough_weigh_ins)
            },
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.recap_avg_kcal),
            icon = Icons.Default.LocalFireDepartment,
            value = recap.avgKcal?.let { stringResource(R.string.recap_kcal_value, "%,d".format(it)) } ?: noData,
            valueColor = if (recap.avgKcal != null && recap.dailyTargetKcal != null && recap.avgKcal > recap.dailyTargetKcal) {
                Tertiary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            detail = recap.dailyTargetKcal?.let { stringResource(R.string.recap_kcal_target, "%,d".format(it)) }
                ?: stringResource(R.string.recap_no_target),
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.recap_days_on_target),
            icon = Icons.Default.TaskAlt,
            value = recap.daysOnTarget?.takeIf { recap.daysWithMeals > 0 }?.let { "$it / ${recap.daysWithMeals}" } ?: noData,
            detail = if (recap.dailyTargetKcal != null) {
                stringResource(R.string.recap_days_on_target_hint)
            } else {
                stringResource(R.string.recap_no_target)
            },
        )
        StatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.recap_avg_steps),
            icon = Icons.AutoMirrored.Filled.DirectionsWalk,
            value = recap.avgSteps?.let { "%,d".format(it) } ?: noData,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(
            modifier = Modifier.weight(1f),
            label = stringResource(R.string.recap_avg_sleep),
            icon = Icons.Default.Bedtime,
            value = recap.avgSleepHours?.let { stringResource(R.string.recap_sleep_value, "%.1f".format(it)) } ?: noData,
        )
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StatCard(
    label: String,
    icon: ImageVector,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    detail: String? = null,
) {
    InfoCard(modifier = modifier, label = label, icon = icon) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
            color = valueColor,
        )
        if (detail != null) {
            Text(
                detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
