package com.fatlosstrack.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalBar
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.mock.MockData
import com.fatlosstrack.ui.theme.TrendUp
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Day summary card — weight, meals, activity, sleep, alcohol.
 * Neutral colors. No praise. No warnings.
 */
@Composable
fun DayCard(
    day: MockData.DaySnapshot,
    onClick: () -> Unit = {},
) {
    val isToday = day.date == java.time.LocalDate.now()
    val isYesterday = day.date == java.time.LocalDate.now().minusDays(1)
    val dateLabel = when {
        isToday -> "Today"
        isYesterday -> "Yesterday"
        else -> day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()) +
                " " + day.date.format(DateTimeFormatter.ofPattern("d MMM"))
    }

    InfoCard(label = dateLabel, onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Weight
            Column {
                Text(
                    text = if (day.weight != null) "${day.weight} kg" else "—",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // Stats row
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Meals
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Restaurant,
                        contentDescription = "Meals",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${day.mealsLogged}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Steps
                if (day.steps != null) {
                    Text(
                        text = "${day.steps / 1000}k steps",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Sleep
                if (day.sleepHours != null) {
                    Text(
                        text = "${day.sleepHours}h",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // Alcohol indicator
                if (day.hasAlcohol) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(TrendUp.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.LocalBar,
                            contentDescription = "Alcohol",
                            modifier = Modifier.size(12.dp),
                            tint = TrendUp,
                        )
                    }
                }
            }
        }

        // Meal categories
        if (day.mealCategories.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = day.mealCategories.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
