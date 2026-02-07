package com.fatlosstrack.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.components.DayCard
import com.fatlosstrack.ui.mock.MockData
import com.fatlosstrack.ui.theme.CardSurface
import com.fatlosstrack.ui.theme.Primary
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * Log tab — timeline of day cards.
 * Scrollable list from today → older, collapsible by week.
 * Tap a card → bottom-sheet detail with editable sections (mock).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen() {
    val allDays = listOf(MockData.today, MockData.yesterday) + MockData.olderDays
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedDay by remember { mutableStateOf<MockData.DaySnapshot?>(null) }

    // Group by week
    val weekField = WeekFields.of(Locale.getDefault()).weekOfWeekBasedYear()
    val grouped = allDays.groupBy { it.date.get(weekField) }.toSortedMap(compareByDescending { it })

    // Track collapsed weeks
    val collapsedWeeks = remember { mutableStateMapOf<Int, Boolean>() }

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Log",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            grouped.forEach { (weekNum, days) ->
                val isCollapsed = collapsedWeeks[weekNum] == true
                val weekLabel = if (days.any { it.date == LocalDate.now() }) {
                    "This week"
                } else {
                    "Week $weekNum"
                }

                // Week header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { collapsedWeeks[weekNum] = !isCollapsed }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = weekLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (isCollapsed) "▸" else "▾",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!isCollapsed) {
                    days.forEach { snapshot ->
                        DayCard(
                            day = snapshot,
                            onClick = { selectedDay = snapshot },
                        )
                    }
                }
            }

            Spacer(Modifier.height(72.dp))
        }

        // -- Day Detail Bottom Sheet --
        if (selectedDay != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedDay = null },
                sheetState = sheetState,
                containerColor = CardSurface,
            ) {
                DayDetailSheet(
                    snapshot = selectedDay!!,
                    onDismiss = { selectedDay = null },
                )
            }
        }
    }
}

@Composable
private fun DayDetailSheet(
    snapshot: MockData.DaySnapshot,
    onDismiss: () -> Unit,
) {
    val dateFmt = DateTimeFormatter.ofPattern("EEEE, MMM d")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = snapshot.date.format(dateFmt),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
        }

        // Weight section
        Section("Weight") {
            if (snapshot.weight != null) {
                Text(
                    text = "${snapshot.weight} kg",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                Text("Not logged", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            OutlinedButton(onClick = { }) {
                Text("Edit", color = Primary)
            }
        }

        // Meals section
        Section("Meals (${snapshot.mealsLogged} logged)") {
            snapshot.mealCategories.forEachIndexed { idx, cat ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Meal ${idx + 1}", style = MaterialTheme.typography.bodyLarge)
                    Text(cat, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(4.dp))
            }
            OutlinedButton(onClick = { }) {
                Text("Add meal", color = Primary)
            }
        }

        // Activity
        Section("Activity") {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                StatItem("Steps", snapshot.steps?.let { "%,d".format(it) } ?: "—")
                StatItem("Sleep", snapshot.sleepHours?.let { "%.1fh".format(it) } ?: "—")
                StatItem("Alcohol", if (snapshot.hasAlcohol) "Yes" else "No")
            }
        }

        // Notes placeholder
        Section("Notes") {
            Text(
                "No notes for this day.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = { }) {
                Text("Add note", color = Primary)
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .padding(16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

@Composable
private fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
