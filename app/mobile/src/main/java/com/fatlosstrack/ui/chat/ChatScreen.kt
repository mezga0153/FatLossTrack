package com.fatlosstrack.ui.chat

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import com.fatlosstrack.R
import com.fatlosstrack.data.local.CapturedPhotoStore
import com.fatlosstrack.data.local.db.ChatMessage
import com.fatlosstrack.data.local.db.MealCategory
import com.fatlosstrack.data.local.db.MealType
import com.fatlosstrack.ui.camera.AnalysisResult
import com.fatlosstrack.ui.camera.CaptureMode
import com.fatlosstrack.ui.camera.MealItem
import com.fatlosstrack.ui.camera.NutritionRow
import com.fatlosstrack.ui.camera.ResultContent
import com.fatlosstrack.ui.camera.parseAnalysisJson
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(state: ChatStateHolder, onNavigateToCamera: () -> Unit = {}) {
    val messages by state.messages.collectAsState(initial = emptyList())
    val streamingContent = state.streamingContent
    val isLoading = state.isLoading

    var inputText by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var showClearDialog by remember { mutableStateOf(false) }
    val androidContext = LocalContext.current

    // Image attachment state
    val attachedImages = remember { mutableStateListOf<Uri>() }

    // Consume photos from CapturedPhotoStore only when this screen is resumed
    // (prevents stealing photos destined for AnalysisResultScreen)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val photoStoreVersion = CapturedPhotoStore.version.intValue
    LaunchedEffect(photoStoreVersion) {
        val isResumed = lifecycleOwner.lifecycle.currentState.isAtLeast(
            androidx.lifecycle.Lifecycle.State.RESUMED,
        )
        if (isResumed) {
            val photos = CapturedPhotoStore.peek()
            if (photos.isNotEmpty()) {
                attachedImages.clear()
                attachedImages.addAll(photos)
                CapturedPhotoStore.clear()
            }
        }
    }

    // Pick up pending message from AiBar (once)
    val initialHandled = remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!initialHandled.value) {
            initialHandled.value = true
            state.consumePending()
        }
    }

    // Auto-scroll behavior
    val isStreaming = streamingContent != null

    // Scroll to bottom when loading starts (spinner appears)
    LaunchedEffect(isLoading) {
        if (isLoading) {
            listState.animateScrollToItem(0)
        }
    }

    // Meal review sheet state — when non-null, the review bottom sheet is shown
    var reviewMeal by remember { mutableStateOf<ChatSegment.Meal?>(null) }
    // Track which meal block indices have been logged (per message id + segment index)
    val loggedMealKeys = remember { mutableStateMapOf<String, Boolean>() }

    // Clear history confirmation dialog
    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.chat_clear_title)) },
            text = { Text(stringResource(R.string.chat_clear_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    state.clearHistory()
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

    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Surface),
    ) {
        // Message list (reverseLayout = true — index 0 at the bottom, auto-scrolled)
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            reverseLayout = true,
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = statusBarTop + 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Thinking indicator (index 0 = bottom in reverseLayout)
            if (isLoading) {
                item(key = "__loading__") {
                    Row(
                        modifier = Modifier.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Accent,
                            strokeWidth = 2.dp,
                        )
                        Text(
                            stringResource(R.string.chat_thinking),
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceVariant,
                        )
                    }
                }
            }

            // Messages in reverse chronological order (newest first = index 0)
            items(messages.reversed(), key = { it.id }) { msg ->
                ChatBubble(
                    message = msg,
                    loggedMealKeys = loggedMealKeys,
                    onCopy = { content ->
                        val clipboard = androidContext.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("AI response", content))
                    },
                    onRetry = {
                        state.retryMessage(msg)
                    },
                    onLogMeal = { meal ->
                        reviewMeal = meal
                    },
                )
            }

            // Empty state placeholder (at top = last in reversed layout)
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
        }

        // Suggestion pills (when chat is empty or after responses)
        if (!isLoading) {
            SuggestionPills(
                onSuggestionClick = { state.sendMessage(it) },
                showClearChat = messages.isNotEmpty(),
                onClearChat = { showClearDialog = true },
            )
        }

        // Input bar
        ChatInputBar(
            text = inputText,
            onTextChange = { inputText = it },
            isLoading = isLoading,
            attachedImages = attachedImages,
            onAttachImage = onNavigateToCamera,
            onRemoveImage = { attachedImages.removeAt(it) },
            onSend = {
                val query = inputText.trim()
                val images = attachedImages.toList()
                inputText = ""
                attachedImages.clear()
                CapturedPhotoStore.clear()
                state.sendMessage(query, images)
            },
            onFocused = {
                scope.launch { listState.animateScrollToItem(0) }
            },
        )
    }

    // ── Meal review bottom sheet ──
    val mealToReview = reviewMeal
    if (mealToReview != null) {
        var reviewDate by remember(mealToReview) {
            mutableStateOf(java.time.LocalDate.now().plusDays(mealToReview.dayOffset.toLong()))
        }
        // Build AnalysisResult from the chat meal JSON / segment
        var analysisResult by remember(mealToReview) {
            mutableStateOf(chatMealToAnalysisResult(mealToReview))
        }
        var correcting by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = { reviewMeal = null },
            containerColor = Surface,
        ) {
            if (correcting) {
                // Show a simple loading indicator during correction
                Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Primary)
                }
            } else {
                ResultContent(
                    result = analysisResult,
                    mode = CaptureMode.LogMeal,
                    showCorrection = true,
                    showDateSelector = true,
                    effectiveDate = reviewDate,
                    onDateChanged = { reviewDate = it },
                    onDone = { reviewMeal = null },
                    onLog = { result, category, mealType ->
                        state.saveMealFromAnalysis(result, reviewDate, category, mealType)
                        loggedMealKeys[mealToReview.description] = true
                        reviewMeal = null
                    },
                    onCorrection = { correction ->
                        correcting = true
                        scope.launch {
                            val corrected = state.correctMealJson(mealToReview.json, correction)
                            if (corrected != null) {
                                analysisResult = corrected
                            }
                            correcting = false
                        }
                    },
                )
            }
        }
    }
}

// ── Chat message ──

@Composable
private fun ChatBubble(
    message: ChatMessage,
    loggedMealKeys: Map<String, Boolean> = emptyMap(),
    onCopy: (String) -> Unit = {},
    onRetry: () -> Unit = {},
    onLogMeal: (ChatSegment.Meal) -> Unit = {},
) {
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
                Column {
                    // Display attached images
                    val imageUriList = message.imageUris?.split(",")?.filter { it.isNotBlank() }
                    if (!imageUriList.isNullOrEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 8.dp),
                        ) {
                            items(imageUriList) { uriStr ->
                                AsyncImage(
                                    model = Uri.parse(uriStr),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                    Text(
                        message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurface,
                    )
                }
            }
        }
    } else {
        // AI response — full width, parse [MEAL] blocks for log buttons
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.ai_coach),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Accent,
            )
            Spacer(Modifier.height(2.dp))

            // Split content into text segments and meal blocks
            val segments = remember(message.content) { parseMealBlocks(message.content) }

            segments.forEachIndexed { index, segment ->
                when (segment) {
                    is ChatSegment.Text -> {
                        if (segment.content.isNotBlank()) {
                            Markdown(
                                content = segment.content.trim(),
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
                    is ChatSegment.Meal -> {
                        val isLogged = loggedMealKeys[segment.description] == true
                        MealLogCard(
                            meal = segment,
                            isLogged = isLogged,
                            onLog = {
                                onLogMeal(segment)
                            },
                        )
                    }
                }
            }

            // Action icons
            Row(
                modifier = Modifier.padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                IconButton(
                    onClick = { onCopy(message.content) },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.chat_action_copy),
                        tint = OnSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                }
                IconButton(
                    onClick = onRetry,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.chat_action_retry),
                        tint = OnSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
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
    attachedImages: List<Uri> = emptyList(),
    onAttachImage: () -> Unit = {},
    onRemoveImage: (Int) -> Unit = {},
    onSend: () -> Unit,
    onFocused: () -> Unit = {},
) {
    val pillShape = RoundedCornerShape(28.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        // Image preview strip
        if (attachedImages.isNotEmpty()) {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp),
            ) {
                items(attachedImages.size) { index ->
                    Box(contentAlignment = Alignment.TopEnd) {
                        AsyncImage(
                            model = attachedImages[index],
                            contentDescription = null,
                            modifier = Modifier
                                .size(64.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        IconButton(
                            onClick = { onRemoveImage(index) },
                            modifier = Modifier
                                .size(20.dp)
                                .offset(x = 4.dp, y = (-4).dp)
                                .background(Surface.copy(alpha = 0.8f), CircleShape),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = stringResource(R.string.cd_close),
                                tint = OnSurface,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(pillShape)
                .border(width = 1.dp, color = Accent.copy(alpha = 0.3f), shape = pillShape)
                .background(AiBarBg)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onAttachImage,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.Default.AddAPhoto,
                    contentDescription = stringResource(R.string.chat_attach_image),
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            TextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = {
                    Text(
                        stringResource(R.string.chat_input_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) onFocused() },
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
            if (text.isNotBlank() || attachedImages.isNotEmpty()) {
                IconButton(
                    onClick = onSend,
                    enabled = !isLoading && text.isNotBlank(),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send),
                        tint = if (isLoading || text.isBlank()) OnSurfaceVariant else Primary,
                    )
                }
            }
        }
    }
}

// ── Meal conversion helper ──

/** Convert a ChatSegment.Meal (from AI chat [MEAL] block) into an AnalysisResult for the review UI. */
private fun chatMealToAnalysisResult(meal: ChatSegment.Meal): AnalysisResult {
    // Try to parse items from the JSON if available
    val items: List<MealItem> = try {
        meal.itemsJson?.let { itemsStr ->
            val arr = Json.parseToJsonElement(itemsStr).jsonArray
            arr.map { el: JsonElement ->
                val obj = el.jsonObject
                val name = obj["name"]?.jsonPrimitive?.content ?: "Item"
                val portion = obj["portion"]?.jsonPrimitive?.content ?: ""
                val cal = obj["calories"]?.jsonPrimitive?.intOrNull ?: obj["kcal"]?.jsonPrimitive?.intOrNull ?: 0
                val prot = obj["protein_g"]?.jsonPrimitive?.intOrNull ?: 0
                val fat = obj["fat_g"]?.jsonPrimitive?.intOrNull ?: 0
                val carbs = obj["carbs_g"]?.jsonPrimitive?.intOrNull ?: 0
                MealItem(
                    name = name,
                    portion = portion,
                    nutrition = listOf(
                        NutritionRow("Calories", "$cal", "kcal"),
                        NutritionRow("Protein", "$prot", "g"),
                        NutritionRow("Fat", "$fat", "g"),
                        NutritionRow("Carbs", "$carbs", "g"),
                    ),
                )
            }
        } ?: emptyList()
    } catch (_: Exception) { emptyList<MealItem>() }

    val mealType: MealType? = try {
        meal.mealType?.uppercase()?.let { name -> MealType.valueOf(name) }
    } catch (_: Exception) { null }

    return AnalysisResult(
        description = meal.description,
        items = items,
        totalCalories = meal.kcal,
        totalProteinG = meal.proteinG,
        totalCarbsG = meal.carbsG,
        totalFatG = meal.fatG,
        aiNote = "",
        mealType = mealType,
    )
}

// ── Meal block parser ──

private sealed class ChatSegment {
    data class Text(val content: String) : ChatSegment()
    data class Meal(
        val json: String,
        val description: String,
        val kcal: Int,
        val proteinG: Int,
        val carbsG: Int,
        val fatG: Int,
        val mealType: String?,
        val dayOffset: Int,
        val itemsJson: String?,
    ) : ChatSegment()
}

private val mealBlockRegex = Regex("""\[MEAL](.*?)\[/MEAL]""", RegexOption.DOT_MATCHES_ALL)

private fun parseMealBlocks(content: String): List<ChatSegment> {
    val segments = mutableListOf<ChatSegment>()
    var lastEnd = 0
    for (match in mealBlockRegex.findAll(content)) {
        if (match.range.first > lastEnd) {
            segments.add(ChatSegment.Text(content.substring(lastEnd, match.range.first)))
        }
        val json = match.groupValues[1].trim()
        try {
            val obj = Json.parseToJsonElement(json).jsonObject
            segments.add(
                ChatSegment.Meal(
                    json = json,
                    description = obj["description"]
                        ?.jsonPrimitive?.content ?: "Meal",
                    kcal = obj["kcal"]
                        ?.jsonPrimitive?.intOrNull ?: 0,
                    proteinG = obj["protein_g"]
                        ?.jsonPrimitive?.intOrNull ?: 0,
                    carbsG = obj["carbs_g"]
                        ?.jsonPrimitive?.intOrNull ?: 0,
                    fatG = obj["fat_g"]
                        ?.jsonPrimitive?.intOrNull ?: 0,
                    mealType = obj["meal_type"]
                        ?.jsonPrimitive?.contentOrNull,
                    dayOffset = obj["day_offset"]
                        ?.jsonPrimitive?.intOrNull ?: 0,
                    itemsJson = obj["items"]?.toString(),
                ),
            )
        } catch (_: Exception) {
            // If JSON parse fails, treat as plain text
            segments.add(ChatSegment.Text(match.value))
        }
        lastEnd = match.range.last + 1
    }
    if (lastEnd < content.length) {
        segments.add(ChatSegment.Text(content.substring(lastEnd)))
    }
    // If no MEAL blocks found, return the whole content as text
    if (segments.isEmpty()) segments.add(ChatSegment.Text(content))
    return segments
}

// ── Meal log card ──

@Composable
private fun MealLogCard(
    meal: ChatSegment.Meal,
    isLogged: Boolean,
    onLog: () -> Unit,
) {
    val dateLabel = when (meal.dayOffset) {
        0 -> stringResource(R.string.day_today)
        -1 -> stringResource(R.string.day_yesterday)
        else -> {
            val d = java.time.LocalDate.now().plusDays(meal.dayOffset.toLong())
            d.format(java.time.format.DateTimeFormatter.ofPattern("MMM d"))
        }
    }
    val typeLabel = meal.mealType?.replaceFirstChar { it.uppercase() }
    val subtitle = buildString {
        if (typeLabel != null) { append(typeLabel); append(" · ") }
        append("${meal.kcal} kcal · ${meal.proteinG}g P")
        if (meal.carbsG > 0 || meal.fatG > 0) append(" · ${meal.carbsG}g C · ${meal.fatG}g F")
        if (meal.dayOffset != 0) { append(" · "); append(dateLabel) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                meal.description,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = OnSurface,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        if (isLogged) {
            Text(
                stringResource(R.string.chat_meal_logged),
                style = MaterialTheme.typography.labelMedium,
                color = Secondary,
            )
        } else {
            TextButton(onClick = onLog) {
                Text(
                    stringResource(R.string.chat_log_meal),
                    color = Primary,
                )
            }
        }
    }
}
