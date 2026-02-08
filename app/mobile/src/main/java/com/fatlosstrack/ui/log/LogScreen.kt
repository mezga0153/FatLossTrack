package com.fatlosstrack.ui.log

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * Log tab — shows logged meals from Room database, grouped by date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(mealDao: MealDao) {
    val meals by mealDao.getAllMeals().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedMeal by remember { mutableStateOf<MealEntry?>(null) }

    // Group by date
    val grouped = meals.groupBy { it.date }.toSortedMap(compareByDescending { it })

    Box(Modifier.fillMaxSize()) {
        if (meals.isEmpty()) {
            // Empty state
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Restaurant,
                        contentDescription = null,
                        tint = OnSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No meals logged yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Take a photo of your meal to get started",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceVariant.copy(alpha = 0.6f),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Meal Log",
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnSurface,
                )

                // Stats summary
                val todayMeals = meals.filter { it.date == LocalDate.now() }
                val todayKcal = todayMeals.sumOf { it.totalKcal }
                if (todayMeals.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column {
                                Text(
                                    "Today",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = OnSurface,
                                )
                                Text(
                                    "${todayMeals.size} meal${if (todayMeals.size > 1) "s" else ""}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceVariant,
                                )
                            }
                            Text(
                                "$todayKcal kcal",
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                color = Primary,
                            )
                        }
                    }
                }

                grouped.forEach { (date, dayMeals) ->
                    val dateLabel = when (date) {
                        LocalDate.now() -> "Today"
                        LocalDate.now().minusDays(1) -> "Yesterday"
                        else -> date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()) +
                                ", " + date.format(DateTimeFormatter.ofPattern("d MMM"))
                    }

                    Text(
                        text = dateLabel,
                        style = MaterialTheme.typography.titleMedium,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )

                    dayMeals.forEach { meal ->
                        MealCard(
                            meal = meal,
                            onClick = { selectedMeal = meal },
                        )
                    }
                }

                Spacer(Modifier.height(80.dp))
            }
        }

        // Meal detail bottom sheet
        if (selectedMeal != null) {
            ModalBottomSheet(
                onDismissRequest = { selectedMeal = null },
                sheetState = sheetState,
                containerColor = CardSurface,
            ) {
                MealDetailSheet(
                    meal = selectedMeal!!,
                    onDelete = {
                        scope.launch {
                            mealDao.delete(selectedMeal!!)
                            selectedMeal = null
                        }
                    },
                    onDismiss = { selectedMeal = null },
                )
            }
        }
    }
}

@Composable
private fun MealCard(meal: MealEntry, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = meal.description.take(80) + if (meal.description.length > 80) "…" else "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = OnSurface,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "${meal.totalKcal} kcal",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Primary,
                )
            }

            // Show item names as chips
            val items = parseItemNames(meal.itemsJson)
            if (items.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = items.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSurfaceVariant,
                )
            }

            // Time
            Spacer(Modifier.height(4.dp))
            Text(
                text = meal.createdAt.atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("h:mm a")),
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceVariant.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun MealDetailSheet(
    meal: MealEntry,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = meal.createdAt.atZone(java.time.ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("EEEE, d MMM · h:mm a")),
                style = MaterialTheme.typography.titleMedium,
                color = OnSurface,
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = OnSurfaceVariant)
            }
        }

        // Description
        Text(
            text = meal.description,
            style = MaterialTheme.typography.bodyLarge,
            color = OnSurface,
        )

        // Nutrition items
        val items = parseItems(meal.itemsJson)
        items.forEach { item ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = OnSurface,
                        )
                        Text(
                            text = item.portion,
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }
                    Text(
                        text = "${item.calories} kcal",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = Primary,
                    )
                }
            }
        }

        // Total
        Card(
            colors = CardDefaults.cardColors(containerColor = PrimaryContainer),
            shape = RoundedCornerShape(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Total",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = OnSurface,
                )
                Text(
                    "${meal.totalKcal} kcal",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Primary,
                )
            }
        }

        // Coach note
        if (!meal.coachNote.isNullOrBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "Coach said",
                        style = MaterialTheme.typography.labelLarge,
                        color = Accent,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        meal.coachNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface,
                    )
                }
            }
        }

        // Delete
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Tertiary),
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Delete meal")
        }

        Spacer(Modifier.height(32.dp))
    }
}

// ── JSON helpers ──

private data class ParsedItem(
    val name: String,
    val portion: String,
    val calories: Int,
)

private fun parseItemNames(json: String?): List<String> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        Json.parseToJsonElement(json).jsonArray.map { el ->
            el.jsonObject["name"]?.jsonPrimitive?.content ?: "Unknown"
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun parseItems(json: String?): List<ParsedItem> {
    if (json.isNullOrBlank()) return emptyList()
    return try {
        Json.parseToJsonElement(json).jsonArray.map { el ->
            val obj = el.jsonObject
            ParsedItem(
                name = obj["name"]?.jsonPrimitive?.content ?: "Unknown",
                portion = obj["portion"]?.jsonPrimitive?.content ?: "",
                calories = obj["calories"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
