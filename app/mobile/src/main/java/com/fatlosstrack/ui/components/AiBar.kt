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
import com.fatlosstrack.R
import com.fatlosstrack.ui.theme.*
import androidx.compose.ui.res.stringResource
import java.time.LocalDate

/**
 * Floating AI bar — persistent pill above bottom nav.
 * Text input + send/camera. Shows AI response in a card above.
 *
 * Pure UI — all logic lives in [AiBarStateHolder].
 */
@Composable
fun AiBar(
    modifier: Modifier = Modifier,
    state: AiBarStateHolder,
    onCameraClick: () -> Unit = {},
    onTextMealAnalyzed: ((LocalDate) -> Unit)? = null,
    onChatOpen: ((String) -> Unit)? = null,
) {
    var text by remember { mutableStateOf("") }
    val pillShape = RoundedCornerShape(28.dp)
    val errorFallback = stringResource(R.string.error_something_went_wrong)

    Column(modifier = modifier) {
        // Response card (above the bar)
        AnimatedVisibility(
            visible = state.aiResponse != null || state.aiError != null || state.isLoading,
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
                    if (state.isLoading) {
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
                            if (state.aiError != null) {
                                Text(
                                    state.aiError!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Tertiary,
                                )
                            } else if (state.aiResponse != null) {
                                if (state.mealLogged) {
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
                                    state.aiResponse!!,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = OnSurface,
                                )
                            }
                        }
                    }
                    // Close button
                    if (!state.isLoading) {
                        IconButton(
                            onClick = { state.dismiss() },
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
                .border(width = 1.5.dp, color = Accent.copy(alpha = 0.3f), shape = pillShape)
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
                        state.submit(query, errorFallback, onTextMealAnalyzed, onChatOpen)
                    },
                    enabled = !state.isLoading,
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
