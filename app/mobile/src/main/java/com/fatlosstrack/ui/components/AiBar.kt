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
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fatlosstrack.data.remote.OpenAiService
import com.fatlosstrack.ui.theme.*
import kotlinx.coroutines.launch

/**
 * Floating AI bar — persistent pill above bottom nav.
 * Text input + send/camera. Shows AI response in a card above.
 */
@Composable
fun AiBar(
    modifier: Modifier = Modifier,
    openAiService: OpenAiService? = null,
    onSend: (String) -> Unit = {},
    onCameraClick: () -> Unit = {},
) {
    var text by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val pillShape = RoundedCornerShape(28.dp)

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
                                "Thinking…",
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
                                Text(
                                    "Coach",
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = Accent,
                                )
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
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Dismiss",
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
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Ask anything...", style = MaterialTheme.typography.bodyMedium) },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AiBarBg,
                    unfocusedContainerColor = AiBarBg,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                singleLine = true,
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
                            scope.launch {
                                val result = openAiService.chat(query)
                                isLoading = false
                                result.fold(
                                    onSuccess = { aiResponse = it },
                                    onFailure = { aiError = it.message ?: "Something went wrong" },
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
                        contentDescription = "Send",
                        tint = Primary,
                    )
                }
            } else {
                IconButton(onClick = onCameraClick) {
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Camera",
                        tint = Accent.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}
