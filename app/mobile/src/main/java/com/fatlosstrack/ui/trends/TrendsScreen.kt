package com.fatlosstrack.ui.trends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.fatlosstrack.ui.components.InfoCard
import com.fatlosstrack.ui.components.TrendChart
import com.fatlosstrack.ui.mock.MockData
import com.fatlosstrack.ui.theme.CardSurface
import com.fatlosstrack.ui.theme.Primary

/**
 * Trends tab — analytical core.
 * Read-only. No editing. No input.
 *
 * 1. Weight trend chart with time range toggle
 * 2. Deviation & diagnosis cards
 * 3. Pattern library
 */
@Composable
fun TrendsScreen() {
    var selectedRange by remember { mutableStateOf("30d") }
    val ranges = listOf("30d", "60d", "90d")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // -- Header --
        Text(
            text = "Weight Trend",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )

        // -- Time range toggle --
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ranges.forEach { range ->
                val isSelected = range == selectedRange
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) Primary.copy(alpha = 0.2f) else CardSurface)
                        .clickable { selectedRange = range }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = range,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (isSelected) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // -- Chart --
        InfoCard {
            TrendChart(
                dataPoints = MockData.weightEntries.mapIndexed { i, (_, w) -> i to w },
                avg7d = MockData.avg7d,
                targetKg = MockData.targetKg,
                confidenceLow = MockData.confidenceLow,
                confidenceHigh = MockData.confidenceHigh,
                modifier = Modifier.height(240.dp),
            )

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("7-day avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${MockData.avg7d} kg", style = MaterialTheme.typography.titleMedium)
                }
                Column {
                    Text("14-day avg", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${MockData.avg14d} kg", style = MaterialTheme.typography.titleMedium)
                }
                Column {
                    Text("Goal", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${MockData.targetKg} kg", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // -- Deviation & Diagnosis --
        Text(
            text = "Diagnosis",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )

        MockData.insights.take(2).forEach { insight ->
            InfoCard(label = insight.title) {
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        // -- Pattern Library --
        Text(
            text = "Patterns",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
        )

        // Filter chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Food timing", "Sleep", "Alcohol", "Activity").forEach { filter ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CardSurface)
                        .clickable { }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = filter,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        MockData.insights.drop(2).forEach { insight ->
            InfoCard(label = insight.title) {
                Text(
                    text = insight.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(72.dp))
    }
}
