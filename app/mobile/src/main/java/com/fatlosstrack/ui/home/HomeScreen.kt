package com.fatlosstrack.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.components.DayCard
import com.fatlosstrack.ui.components.InfoCard
import com.fatlosstrack.ui.components.TrendChart
import com.fatlosstrack.ui.mock.MockData
import com.fatlosstrack.ui.theme.Primary

/**
 * Home screen — "Am I on track this week?"
 *
 * 1. Primary trend card (chart + one-sentence summary)
 * 2. Status cards (2-3 factual sentences)
 * 3. Today & Yesterday cards
 */
@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // -- 1. Primary Trend Card --
        InfoCard(label = "7-Day Trend") {
            TrendChart(
                dataPoints = MockData.weightEntries.mapIndexed { i, (_, w) -> i to w },
                avg7d = MockData.avg7d,
                targetKg = MockData.targetKg,
                confidenceLow = MockData.confidenceLow,
                confidenceHigh = MockData.confidenceHigh,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                text = "7-day avg: ${MockData.avg7d} kg",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "You are ${MockData.deviationKg} kg above your projected trend.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = "At this rate, goal is reached on ${MockData.projectedGoalDate}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // -- 2. Status Cards --
        MockData.statusCards.forEach { card ->
            InfoCard(label = card.label) {
                Text(
                    text = card.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // -- 3. Today & Yesterday --
        DayCard(day = MockData.today)
        DayCard(day = MockData.yesterday)

        // Bottom spacer for AI bar clearance
        Spacer(Modifier.height(80.dp)) // clearance for floating AI bar
    }
}
