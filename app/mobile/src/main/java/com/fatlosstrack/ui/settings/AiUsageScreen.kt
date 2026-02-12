package com.fatlosstrack.ui.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.R
import com.fatlosstrack.data.local.db.AiUsageDao
import com.fatlosstrack.data.local.db.DailyModelUsage
import com.fatlosstrack.data.local.db.DailyUsageSummary
import com.fatlosstrack.data.local.db.FeatureUsageSummary
import com.fatlosstrack.data.local.db.ModelUsageSummary
import com.fatlosstrack.ui.theme.CardSurface
import com.fatlosstrack.ui.theme.Primary
import com.fatlosstrack.ui.theme.Secondary
import com.fatlosstrack.ui.theme.Tertiary
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Estimated cost per 1M tokens by model family.
 * Prices as of early 2026 — update when pricing changes.
 */
private data class ModelPricing(val inputPer1M: Double, val outputPer1M: Double)

private val MODEL_PRICING = mapOf(
    "gpt-5.2" to ModelPricing(1.75, 14.0),
    "gpt-5.2-codex" to ModelPricing(1.75, 14.0),
    "gpt-5.2-pro" to ModelPricing(21.0, 168.0),
    "gpt-5.1" to ModelPricing(1.25, 10.0),
    "gpt-5" to ModelPricing(1.25, 10.0),
    "gpt-5-mini" to ModelPricing(0.25, 2.0),
    "gpt-5-nano" to ModelPricing(0.05, 0.40),
    "gpt-4.1" to ModelPricing(2.0, 8.0),
    "gpt-4.1-mini" to ModelPricing(0.40, 1.60),
    "gpt-4.1-nano" to ModelPricing(0.10, 0.40),
    "gpt-4o" to ModelPricing(2.50, 10.0),
    "gpt-4o-mini" to ModelPricing(0.15, 0.60),
    "o3" to ModelPricing(2.0, 8.0),
    "o4-mini" to ModelPricing(1.10, 4.40),
)

private val FALLBACK_PRICING = ModelPricing(2.0, 8.0)

private fun estimateCost(model: String, promptTokens: Long, completionTokens: Long): Double {
    val pricing = MODEL_PRICING.entries
        .firstOrNull { model.startsWith(it.key) }?.value
        ?: FALLBACK_PRICING
    return (promptTokens * pricing.inputPer1M + completionTokens * pricing.outputPer1M) / 1_000_000.0
}

private fun featureLabel(feature: String): String = when (feature) {
    "chat" -> "Chat"
    "meal_text" -> "Text meal log"
    "meal_photo" -> "Photo meal log"
    "meal_suggest" -> "Photo suggestions"
    "meal_edit" -> "Meal corrections"
    "day_summary" -> "Day summaries"
    "period_summary" -> "Period summaries"
    else -> feature
}

private fun formatTokens(tokens: Long): String = when {
    tokens >= 1_000_000 -> "%.1fM".format(tokens / 1_000_000.0)
    tokens >= 1_000 -> "%.1fk".format(tokens / 1_000.0)
    else -> tokens.toString()
}

@Composable
fun AiUsageScreen(
    aiUsageDao: AiUsageDao,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    val totalPrompt by aiUsageDao.totalPromptTokens().collectAsState(initial = 0)
    val totalCompletion by aiUsageDao.totalCompletionTokens().collectAsState(initial = 0)
    val totalRequests by aiUsageDao.totalRequests().collectAsState(initial = 0)
    val byFeature by aiUsageDao.usageByFeature().collectAsState(initial = emptyList())
    val byModel by aiUsageDao.usageByModel().collectAsState(initial = emptyList())

    // Daily usage for last 30 days
    val thirtyDaysAgoMillis = remember {
        Instant.now().minus(30, ChronoUnit.DAYS).toEpochMilli()
    }
    val dailyUsageRaw by aiUsageDao.usageByDay(thirtyDaysAgoMillis).collectAsState(initial = emptyList())
    val dailyModelRaw by aiUsageDao.usageByDayAndModel(thirtyDaysAgoMillis).collectAsState(initial = emptyList())

    // Fill gaps so every day in the 30-day window has an entry
    val dailyUsage = remember(dailyUsageRaw) {
        val today = LocalDate.now()
        val start = today.minusDays(29)
        val byDay = dailyUsageRaw.associateBy { it.day }
        (0L..29L).map { offset ->
            val date = start.plusDays(offset)
            val key = date.toString()
            byDay[key] ?: DailyUsageSummary(key, 0L, 0L, 0)
        }
    }

    // Compute daily cost from day+model breakdown
    val dailyCost = remember(dailyModelRaw) {
        val today = LocalDate.now()
        val start = today.minusDays(29)
        val costByDay = dailyModelRaw
            .groupBy { it.day }
            .mapValues { (_, rows) ->
                rows.sumOf { estimateCost(it.model, it.promptTokens, it.completionTokens) }
            }
        (0L..29L).map { offset ->
            val date = start.plusDays(offset)
            val key = date.toString()
            key to (costByDay[key] ?: 0.0)
        }
    }

    val promptTokens = (totalPrompt ?: 0).toLong()
    val completionTokens = (totalCompletion ?: 0).toLong()
    val totalTokens = promptTokens + completionTokens

    // Estimate cost per model
    val totalCost = byModel.sumOf { estimateCost(it.model, it.promptTokens, it.completionTokens) }

    var showClearDialog by remember { mutableStateOf(false) }

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
            Text(
                text = stringResource(R.string.ai_usage_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        // Overview card
        UsageSection(stringResource(R.string.ai_usage_overview)) {
            UsageRow(stringResource(R.string.ai_usage_total_requests), totalRequests.toString())
            UsageRow(stringResource(R.string.ai_usage_total_tokens), formatTokens(totalTokens))
            UsageRow(stringResource(R.string.ai_usage_input_tokens), formatTokens(promptTokens))
            UsageRow(stringResource(R.string.ai_usage_output_tokens), formatTokens(completionTokens))
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    stringResource(R.string.ai_usage_estimated_cost),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "$%.4f".format(totalCost),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = Secondary,
                )
            }
        }

        // Daily usage chart (last 30 days)
        if (dailyUsage.any { it.requests > 0 }) {
            UsageSection(stringResource(R.string.ai_usage_daily)) {
                val dayFormatter = remember { DateTimeFormatter.ofPattern("d") }
                val monthFormatter = remember { DateTimeFormatter.ofPattern("d MMM") }
                DailyUsageBarChart(
                    data = dailyUsage,
                    dayFormatter = dayFormatter,
                    monthFormatter = monthFormatter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                )
            }
        }

        // Daily cost chart
        if (dailyCost.any { it.second > 0.0 }) {
            UsageSection(stringResource(R.string.ai_usage_daily_cost)) {
                val dayFormatter = remember { DateTimeFormatter.ofPattern("d") }
                val monthFormatter = remember { DateTimeFormatter.ofPattern("d MMM") }
                DailyCostBarChart(
                    data = dailyCost,
                    dayFormatter = dayFormatter,
                    monthFormatter = monthFormatter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                )
            }
        }

        // By feature
        if (byFeature.isNotEmpty()) {
            UsageSection(stringResource(R.string.ai_usage_by_feature)) {
                byFeature.forEach { row ->
                    FeatureRow(row)
                }
            }
        }

        // By model
        if (byModel.isNotEmpty()) {
            UsageSection(stringResource(R.string.ai_usage_by_model)) {
                byModel.forEach { row ->
                    ModelRow(row)
                }
            }
        }

        // Clear usage data
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { showClearDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
        ) {
            Text(stringResource(R.string.ai_usage_clear), color = Tertiary)
        }

        Text(
            text = stringResource(R.string.ai_usage_disclaimer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.ai_usage_clear_title)) },
            text = { Text(stringResource(R.string.ai_usage_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { aiUsageDao.clearAll() }
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.ai_usage_clear_yes), color = Tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.ai_usage_clear_no))
                }
            },
        )
    }
}

@Composable
private fun UsageSection(title: String, content: @Composable ColumnScope.() -> Unit) {
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
private fun UsageRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FeatureRow(row: FeatureUsageSummary) {
    val tokens = row.promptTokens + row.completionTokens
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                featureLabel(row.feature),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "${row.requests} calls",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            "${formatTokens(tokens)} tokens (${formatTokens(row.promptTokens)} in / ${formatTokens(row.completionTokens)} out)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModelRow(row: ModelUsageSummary) {
    val cost = estimateCost(row.model, row.promptTokens, row.completionTokens)
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                row.model,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "$%.4f".format(cost),
                style = MaterialTheme.typography.bodyMedium,
                color = Secondary,
            )
        }
        Text(
            "${row.requests} calls · ${formatTokens(row.promptTokens + row.completionTokens)} tokens",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Daily usage bar chart ----

@Composable
private fun DailyUsageBarChart(
    data: List<DailyUsageSummary>,
    dayFormatter: DateTimeFormatter,
    monthFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val inputColor = Primary
    val outputColor = Secondary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val maxTokens = data.maxOf { it.promptTokens + it.completionTokens }.coerceAtLeast(100)

    Canvas(modifier = modifier) {
        val leftPad = 48.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val topPad = 8.dp.toPx()
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad - topPad

        val barW = chartW / data.size
        val barInner = barW * 0.65f
        val barOffset = (barW - barInner) / 2f

        // Y grid + labels
        val ticks = listOf(0L, maxTokens / 2, maxTokens)
        val textPaint = android.graphics.Paint().apply {
            color = labelColor.hashCode()
            textSize = with(density) { 10.dp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        ticks.forEach { tick ->
            val y = topPad + chartH * (1f - tick.toFloat() / maxTokens)
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y))
            drawContext.canvas.nativeCanvas.drawText(
                formatTokens(tick),
                leftPad - 6.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint,
            )
        }

        // Bars
        data.forEachIndexed { i, day ->
            val total = day.promptTokens + day.completionTokens
            if (total == 0L) return@forEachIndexed

            val totalH = chartH * (total.toFloat() / maxTokens)
            val inputH = chartH * (day.promptTokens.toFloat() / maxTokens)
            val x = leftPad + i * barW + barOffset
            val barTop = topPad + chartH - totalH

            // Output (completion) on top
            drawRoundRect(
                color = outputColor,
                topLeft = Offset(x, barTop),
                size = Size(barInner, totalH - inputH),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            // Input (prompt) on bottom
            drawRoundRect(
                color = inputColor,
                topLeft = Offset(x, barTop + (totalH - inputH)),
                size = Size(barInner, inputH),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }

        // X-axis labels (every 5th day + first + last)
        val xLabelPaint = android.graphics.Paint().apply {
            color = labelColor.hashCode()
            textSize = with(density) { 9.dp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        data.forEachIndexed { i, day ->
            if (i == 0 || i == data.lastIndex || i % 5 == 0) {
                val date = LocalDate.parse(day.day)
                val label = if (i == 0 || date.dayOfMonth == 1) {
                    date.format(monthFormatter)
                } else {
                    date.format(dayFormatter)
                }
                val cx = leftPad + i * barW + barW / 2f
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    cx,
                    size.height - 4.dp.toPx(),
                    xLabelPaint,
                )
            }
        }
    }

    // Legend
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Primary)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.ai_usage_input_tokens),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Box(
            Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Secondary)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            stringResource(R.string.ai_usage_output_tokens),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ---- Daily cost bar chart ----

private fun formatCost(dollars: Double): String = when {
    dollars >= 1.0 -> "$%.2f".format(dollars)
    dollars >= 0.01 -> "$%.3f".format(dollars)
    dollars > 0.0 -> "$%.4f".format(dollars)
    else -> "$0"
}

@Composable
private fun DailyCostBarChart(
    data: List<Pair<String, Double>>,       // (day, cost USD)
    dayFormatter: DateTimeFormatter,
    monthFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val barColor = Secondary
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    val maxCost = data.maxOf { it.second }.coerceAtLeast(0.001)

    Canvas(modifier = modifier) {
        val leftPad = 52.dp.toPx()
        val bottomPad = 24.dp.toPx()
        val topPad = 8.dp.toPx()
        val chartW = size.width - leftPad
        val chartH = size.height - bottomPad - topPad

        val barW = chartW / data.size
        val barInner = barW * 0.65f
        val barOffset = (barW - barInner) / 2f

        // Y grid + labels
        val ticks = listOf(0.0, maxCost / 2, maxCost)
        val textPaint = android.graphics.Paint().apply {
            color = labelColor.hashCode()
            textSize = with(density) { 10.dp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        ticks.forEach { tick ->
            val y = topPad + chartH * (1f - (tick / maxCost).toFloat())
            drawLine(gridColor, Offset(leftPad, y), Offset(size.width, y))
            drawContext.canvas.nativeCanvas.drawText(
                formatCost(tick),
                leftPad - 6.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint,
            )
        }

        // Bars
        data.forEachIndexed { i, (_, cost) ->
            if (cost <= 0.0) return@forEachIndexed
            val barH = chartH * (cost / maxCost).toFloat()
            val x = leftPad + i * barW + barOffset
            val barTop = topPad + chartH - barH
            drawRoundRect(
                color = barColor,
                topLeft = Offset(x, barTop),
                size = Size(barInner, barH),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
        }

        // X-axis labels
        val xLabelPaint = android.graphics.Paint().apply {
            color = labelColor.hashCode()
            textSize = with(density) { 9.dp.toPx() }
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        data.forEachIndexed { i, (day, _) ->
            if (i == 0 || i == data.lastIndex || i % 5 == 0) {
                val date = LocalDate.parse(day)
                val label = if (i == 0 || date.dayOfMonth == 1) {
                    date.format(monthFormatter)
                } else {
                    date.format(dayFormatter)
                }
                val cx = leftPad + i * barW + barW / 2f
                drawContext.canvas.nativeCanvas.drawText(
                    label,
                    cx,
                    size.height - 4.dp.toPx(),
                    xLabelPaint,
                )
            }
        }
    }
}
