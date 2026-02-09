package com.fatlosstrack.ui.chat

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.fatlosstrack.R
import com.fatlosstrack.data.local.PendingChatStore
import com.fatlosstrack.data.local.PreferencesManager
import com.fatlosstrack.data.local.db.*
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun ChatScreen(
    openAiService: OpenAiService,
    chatMessageDao: ChatMessageDao,
    dailyLogDao: DailyLogDao,
    mealDao: MealDao,
    weightDao: WeightDao,
    preferencesManager: PreferencesManager,
) {
    val messages by chatMessageDao.getAllMessages().collectAsState(initial = emptyList())
    var inputText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showClearDialog by remember { mutableStateOf(false) }

    // Pick up pending message from AiBar
    val initialHandled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val pending = PendingChatStore.consume()
        if (!pending.isNullOrBlank() && !initialHandled.value) {
            initialHandled.value = true
            isLoading = true
            chatMessageDao.insert(ChatMessage(role = "user", content = pending))
            val context = buildContextBlock(dailyLogDao, mealDao, weightDao, preferencesManager)
            val recent = chatMessageDao.getRecentMessages(20).reversed()
            val history = recent.map { it.role to it.content }
            val result = openAiService.chatWithHistory(history, context)
            result.fold(
                onSuccess = { reply ->
                    chatMessageDao.insert(ChatMessage(role = "assistant", content = reply))
                },
                onFailure = { err ->
                    chatMessageDao.insert(
                        ChatMessage(role = "assistant", content = "⚠️ ${err.message ?: "Something went wrong"}")
                    )
                },
            )
            isLoading = false
        }
    }

    // Auto-scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Clear history confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.chat_clear_title)) },
            text = { Text(stringResource(R.string.chat_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { chatMessageDao.clearAll() }
                    showClearDialog = false
                }) {
                    Text(stringResource(R.string.chat_clear_yes), color = Tertiary)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.chat_clear_no))
                }
            },
            containerColor = CardSurface,
        )
    }

    // Helper to send a message
    fun sendMessage(text: String) {
        if (text.isBlank() || isLoading) return
        isLoading = true
        scope.launch {
            chatMessageDao.insert(ChatMessage(role = "user", content = text))
            val context = buildContextBlock(dailyLogDao, mealDao, weightDao, preferencesManager)
            val recent = chatMessageDao.getRecentMessages(20).reversed()
            val history = recent.map { it.role to it.content }
            val result = openAiService.chatWithHistory(history, context)
            result.fold(
                onSuccess = { reply ->
                    chatMessageDao.insert(ChatMessage(role = "assistant", content = reply))
                },
                onFailure = { err ->
                    chatMessageDao.insert(
                        ChatMessage(role = "assistant", content = "⚠️ ${err.message ?: "Something went wrong"}")
                    )
                },
            )
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface)
            .statusBarsPadding(),
    ) {
        // Message list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty() && !isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 80.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = Accent.copy(alpha = 0.4f),
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                stringResource(R.string.chat_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = OnSurface.copy(alpha = 0.7f),
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                stringResource(R.string.chat_empty_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceVariant,
                            )
                        }
                    }
                }
            }

            items(messages, key = { it.id }) { msg ->
                ChatBubble(msg)
            }

            // Loading indicator
            if (isLoading) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Accent,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.ai_thinking),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Suggestion pills (when chat is empty or after responses)
        if (!isLoading) {
            SuggestionPills(
                onSuggestionClick = { sendMessage(it) },
                showClearChat = messages.isNotEmpty(),
                onClearChat = { showClearDialog = true },
            )
        }

        // Input bar
        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            isLoading = isLoading,
            onSend = {
                val query = inputText.trim()
                inputText = ""
                sendMessage(query)
            },
        )
    }
}

// ── Chat message ──

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"

    if (isUser) {
        // User message — right-aligned bubble
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp, topEnd = 16.dp,
                            bottomStart = 16.dp, bottomEnd = 4.dp,
                        )
                    )
                    .background(PrimaryContainer)
                    .padding(12.dp),
            ) {
                Text(
                    message.content,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurface,
                )
            }
        }
    } else {
        // AI response — full width, no bubble
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.ai_coach),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Accent,
            )
            Spacer(Modifier.height(2.dp))
            Markdown(
                content = message.content,
                colors = markdownColor(
                    text = OnSurface,
                    codeBackground = CardSurface,
                    inlineCodeBackground = CardSurface,
                    dividerColor = OnSurfaceVariant.copy(alpha = 0.3f),
                    tableBackground = CardSurface,
                ),
                typography = markdownTypography(
                    h1 = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = OnSurface),
                    h2 = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = OnSurface),
                    h3 = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, color = OnSurface),
                    text = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                    paragraph = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                    bullet = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                    list = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                    ordered = MaterialTheme.typography.bodyMedium.copy(color = OnSurface),
                ),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

// ── Suggestion pills ──

@Composable
private fun SuggestionPills(
    onSuggestionClick: (String) -> Unit,
    showClearChat: Boolean,
    onClearChat: () -> Unit,
) {
    val pills = listOf(
        R.string.chat_pill_am_i_on_track,
        R.string.chat_pill_suggest_meal,
        R.string.chat_pill_weekly_review,
    )
    val pillShape = RoundedCornerShape(20.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pills.forEach { resId ->
            val label = stringResource(resId)
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .border(1.dp, OnSurfaceVariant.copy(alpha = 0.25f), pillShape)
                    .clickable { onSuggestionClick(label) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = OnSurface.copy(alpha = 0.85f),
                )
            }
        }

        if (showClearChat) {
            Box(
                modifier = Modifier
                    .clip(pillShape)
                    .border(1.dp, Tertiary.copy(alpha = 0.3f), pillShape)
                    .clickable { onClearChat() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.chat_clear_title),
                    tint = Tertiary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Input bar ──

@Composable
private fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    isLoading: Boolean,
    onSend: () -> Unit,
) {
    val pillShape = RoundedCornerShape(28.dp)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
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
            modifier = Modifier
                .size(18.dp)
                .padding(start = 6.dp),
        )
        Spacer(Modifier.width(4.dp))
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = {
                Text(
                    stringResource(R.string.chat_input_placeholder),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
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
        if (text.isNotBlank()) {
            IconButton(
                onClick = onSend,
                enabled = !isLoading,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = stringResource(R.string.cd_send),
                    tint = if (isLoading) OnSurfaceVariant else Primary,
                )
            }
        }
    }
}

// ── Context builder ──

private suspend fun buildContextBlock(
    dailyLogDao: DailyLogDao,
    mealDao: MealDao,
    weightDao: WeightDao,
    preferencesManager: PreferencesManager,
): String {
    val today = LocalDate.now()
    val sevenDaysAgo = today.minusDays(7)
    val fmt = DateTimeFormatter.ISO_LOCAL_DATE

    // Goal & profile
    val startWeight = preferencesManager.startWeight.first()
    val goalKg = preferencesManager.goalWeight.first()
    val goalRate = preferencesManager.weeklyRate.first()
    val guidance = preferencesManager.aiGuidance.first()
    val coachTone = preferencesManager.coachTone.first()
    val heightCm = preferencesManager.heightCm.first()
    val startDate = preferencesManager.startDate.first()

    // Weight entries (last 7 days)
    val weights = weightDao.getEntriesSince(sevenDaysAgo).first()

    // Daily logs (last 7 days)
    val logs = dailyLogDao.getLogsSince(sevenDaysAgo).first()

    // Meals (last 7 days)
    val meals = mealDao.getMealsSince(sevenDaysAgo).first()

    val sb = StringBuilder()
    sb.appendLine("=== USER CONTEXT (last 7 days) ===")
    sb.appendLine("Today: ${fmt.format(today)}")

    // Goal & profile info
    val goalParts = mutableListOf<String>()
    if (startWeight != null && startWeight > 0f) goalParts += "start=${startWeight}kg"
    if (goalKg != null && goalKg > 0f) goalParts += "target=${goalKg}kg"
    goalParts += "rate=${goalRate}kg/week"
    if (heightCm != null) goalParts += "height=${heightCm}cm"
    if (!startDate.isNullOrBlank()) goalParts += "since=$startDate"
    sb.appendLine("Goal: ${goalParts.joinToString(", ")}")
    sb.appendLine("Coach tone: $coachTone")
    if (guidance.isNotBlank()) {
        sb.appendLine("User guidance/preferences: $guidance")
    }

    if (weights.isNotEmpty()) {
        sb.appendLine("\nWeights:")
        weights.sortedByDescending { it.date }.forEach { w ->
            sb.appendLine("  ${fmt.format(w.date)}: ${w.valueKg} kg")
        }
    }

    if (logs.isNotEmpty()) {
        sb.appendLine("\nDaily logs:")
        logs.sortedByDescending { it.date }.forEach { log ->
            val parts = mutableListOf<String>()
            log.weightKg?.let { parts += "weight=${it}kg" }
            log.steps?.let { parts += "steps=$it" }
            log.sleepHours?.let { parts += "sleep=${it}h" }
            log.restingHr?.let { parts += "restHR=$it" }
            if (log.offPlan) parts += "OFF-PLAN"
            sb.appendLine("  ${fmt.format(log.date)}: ${parts.joinToString(", ")}")
        }
    }

    if (meals.isNotEmpty()) {
        sb.appendLine("\nMeals:")
        meals.sortedByDescending { it.date }.forEach { m ->
            val prot = if (m.totalProteinG > 0) ", ${m.totalProteinG}g protein" else ""
            sb.appendLine("  ${fmt.format(m.date)}: ${m.description} (${m.totalKcal} kcal$prot)")
        }
    }

    sb.appendLine("=== END CONTEXT ===")
    return sb.toString()
}
