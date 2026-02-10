package com.fatlosstrack.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.data.DaySummaryGenerator
import com.fatlosstrack.data.local.PendingTextMealStore
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealDao
import com.fatlosstrack.data.local.db.MealEntry
import com.fatlosstrack.data.remote.OpenAiService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import com.fatlosstrack.R
import com.fatlosstrack.ui.theme.*
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.time.LocalDate

/**
 * Floating AI bar — persistent pill above bottom nav.
 * Text input + send/camera. Shows AI response in a card above.
 */
@Composable
fun AiBar(
    modifier: Modifier = Modifier,
    openAiService: OpenAiService? = null,
    mealDao: MealDao? = null,
    daySummaryGenerator: DaySummaryGenerator? = null,
    onSend: (String) -> Unit = {},
    onCameraClick: () -> Unit = {},
    onTextMealAnalyzed: ((LocalDate) -> Unit)? = null,
    onChatOpen: ((String) -> Unit)? = null,
) {
    var text by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var mealLogged by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val pillShape = RoundedCornerShape(28.dp)
    val errorFallback = stringResource(R.string.error_something_went_wrong)

    Column(modifier = modifier) {
        // Response card (above the bar)
        AnimatedVisibility(
            visible = aiResponse != null || aiError != null || isLoading,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 4.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    if (isLoading) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Primary,
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(12.dp))
                            Text(
                                stringResource(R.string.ai_thinking),
                                style = MaterialTheme.typography.bodyMedium,
                                color = OnSurfaceVariant,
                            )
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .heightIn(max = 300.dp)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp)
                                .padding(end = 32.dp),
                        ) {
                            if (aiError != null) {
                                Text(
                                    aiError!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Tertiary,
                                )
                            } else if (aiResponse != null) {
                                if (mealLogged) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.CheckCircle,
                                            contentDescription = null,
                                            tint = Secondary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.ai_meal_logged),
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Secondary,
                                        )
                                    }
                                } else {
                                    Text(
                                        stringResource(R.string.ai_coach),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = Accent,
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    aiResponse!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface,
                                )
                            }
                        }
                    }
                    // Close button
                    if (!isLoading) {
                        IconButton(
                            onClick = {
                                aiResponse = null
                                aiError = null
                                mealLogged = false
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_dismiss),
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }

        // Input bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(pillShape)
                .border(width = 1.dp, color = Accent.copy(alpha = 0.3f), shape = pillShape)
                .background(AiBarBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(18.dp).padding(start = 6.dp),
            )
            Spacer(Modifier.width(4.dp))
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.ai_bar_placeholder), style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AiBarBg,
                    unfocusedContainerColor = AiBarBg,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                singleLine = false,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyLarge,
            )
            // Send button (shows when text is entered)
            if (text.isNotBlank()) {
                IconButton(
                    onClick = {
                        val query = text.trim()
                        text = ""
                        if (openAiService != null) {
                            isLoading = true
                            aiResponse = null
                            aiError = null
                            mealLogged = false
                            scope.launch {
                                // Try to parse as a meal first
                                val mealResult = openAiService.parseTextMeal(query)
                                mealResult.fold(
                                    onSuccess = { raw ->
                                        val parsed = tryParseMealJson(raw)
                                        if (parsed != null && onTextMealAnalyzed != null) {
                                            // It's a meal — store for analysis screen
                                            val targetDate = LocalDate.now().plusDays(parsed.dayOffset.toLong())
                                            PendingTextMealStore.store(raw, targetDate)
                                            isLoading = false
                                            onTextMealAnalyzed(targetDate)
                                        } else if (parsed != null && mealDao != null) {
                                            // Fallback: direct insert if no nav callback
                                            val targetDate = LocalDate.now().plusDays(parsed.dayOffset.toLong())
                                            mealDao.insert(
                                                MealEntry(
                                                    date = targetDate,
                                                    description = parsed.description,
                                                    itemsJson = parsed.itemsJson,
                                                    totalKcal = parsed.totalCalories,
                                                    totalProteinG = parsed.totalProteinG,
                                                    totalCarbsG = parsed.totalCarbsG,
                                                    totalFatG = parsed.totalFatG,
                                                    coachNote = parsed.coachNote,
                                                    category = parsed.source,
                                                )
                                            )
                                            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                                                daySummaryGenerator?.generateForDate(targetDate, "AiBar:textMealLogged")
                                            }
                                            isLoading = false
                                            mealLogged = true
                                            aiResponse = "${parsed.description} — ${parsed.totalCalories} kcal" +
                                                if (parsed.coachNote.isNotBlank()) "\n\n${parsed.coachNote}" else ""
                                        } else if (parsed == null) {
                                            // Not a meal — open chat screen
                                            isLoading = false
                                            if (onChatOpen != null) {
                                                onChatOpen(query)
                                            } else {
                                                val chatResult = openAiService.chat(query)
                                                chatResult.fold(
                                                    onSuccess = { aiResponse = it },
                                                    onFailure = { aiError = it.message ?: errorFallback },
                                                )
                                            }
                                        } else {
                                            // Parsed a meal but no mealDao — show it as text
                                            isLoading = false
                                            aiResponse = raw
                                        }
                                    },
                                    onFailure = {
                                        // parseTextMeal failed — open chat screen
                                        isLoading = false
                                        if (onChatOpen != null) {
                                            onChatOpen(query)
                                        } else {
                                            val chatResult = openAiService.chat(query)
                                            chatResult.fold(
                                                onSuccess = { aiResponse = it },
                                                onFailure = { e -> aiError = e.message ?: errorFallback },
                                            )
                                        }
                                    },
                                )
                            }
                        } else {
                            onSend(query)
                        }
                    },
                    enabled = !isLoading,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send),
                        tint = Primary,
                    )
                }
            } else {
                IconButton(onClick = onCameraClick) {
                    Box {
                        Icon(
                            Icons.Default.CameraAlt,
                            contentDescription = stringResource(R.string.cd_camera),
                            tint = Accent.copy(alpha = 0.7f),
                        )
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Primary,
                            modifier = Modifier.size(12.dp).align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Parsed meal helper ──

private data class ParsedMeal(
    val dayOffset: Int,
    val description: String,
    val source: MealCategory,
    val itemsJson: String,
    val totalCalories: Int,
    val totalProteinG: Int,
    val totalCarbsG: Int,
    val totalFatG: Int,
    val coachNote: String,
)

private fun tryParseMealJson(raw: String): ParsedMeal? {
    return try {
        val cleaned = raw
            .replace(Regex("^```json\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^```\\s*", RegexOption.MULTILINE), "")
            .trim()
        val json = Json.parseToJsonElement(cleaned).jsonObject
        val isMeal = json["is_meal"]?.jsonPrimitive?.boolean ?: return null
        if (!isMeal) return null

        val dayOffset = json["day_offset"]?.jsonPrimitive?.int ?: 0
        val description = json["description"]?.jsonPrimitive?.content ?: ""
        val totalCalories = json["total_calories"]?.jsonPrimitive?.int ?: 0
        val coachNote = json["coach_note"]?.jsonPrimitive?.content ?: ""
        val sourceStr = json["source"]?.jsonPrimitive?.content ?: "home"
        val source = when (sourceStr.lowercase()) {
            "restaurant" -> MealCategory.RESTAURANT
            "fast_food", "fastfood", "fast food" -> MealCategory.FAST_FOOD
            else -> MealCategory.HOME
        }

        val itemsJson = json["items"]?.jsonArray?.let { items ->
            buildJsonArray {
                items.forEach { itemEl ->
                    val item = itemEl.jsonObject
                    add(buildJsonObject {
                        put("name", item["name"]?.jsonPrimitive?.content ?: "Unknown")
                        put("portion", item["portion"]?.jsonPrimitive?.content ?: "")
                        put("calories", item["calories"]?.jsonPrimitive?.int ?: 0)
                        put("protein_g", item["protein_g"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("fat_g", item["fat_g"]?.jsonPrimitive?.intOrNull ?: 0)
                        put("carbs_g", item["carbs_g"]?.jsonPrimitive?.intOrNull ?: 0)
                    })
                }
            }.toString()
        } ?: "[]"

        ParsedMeal(
            dayOffset = dayOffset,
            description = description,
            source = source,
            itemsJson = itemsJson,
            totalCalories = totalCalories,
            totalProteinG = json["total_protein_g"]?.jsonPrimitive?.intOrNull ?: 0,
            totalCarbsG = json["total_carbs_g"]?.jsonPrimitive?.intOrNull ?: 0,
            totalFatG = json["total_fat_g"]?.jsonPrimitive?.intOrNull ?: 0,
            coachNote = coachNote,
        )
    } catch (_: Exception) {
        null
    }
}
