package com.fatlosstrack.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fatlosstrack.R
import com.fatlosstrack.ui.theme.*
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
    val iconShape = RoundedCornerShape(14.dp)
    val errorFallback = stringResource(R.string.error_something_went_wrong)

    // Capture @Composable colors for use in non-composable modifier lambdas
    val accentColor = Accent
    val primaryColor = Primary
    val aiBarBgColor = AiBarBg

    val rainbowBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFCDA0FF),
                Color(0xFF9B8AFF),
                Color(0xFF6C9CFF),
                Color(0xFF59D8E0),
                Color(0xFF59D8A0),
                Color(0xFFB8E048),
                Color(0xFFFFD060),
                Color(0xFFFF9A3C),
                Color(0xFFFF6B6B),
                Color(0xFFFF6BC6),
                Color(0xFFCDA0FF),
            )
        )
    }
    val iconGradient = remember {
        Brush.linearGradient(listOf(Color(0xFF7B5FFF), Color(0xFFCDA0FF)))
    }
    val suggestions = listOf(
        stringResource(R.string.ai_suggestion_weight),
        stringResource(R.string.ai_suggestion_food),
    )

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
                                color = primaryColor,
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
                                        color = accentColor,
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

        // Input pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = pillShape,
                    clip = false,
                    ambientColor = accentColor.copy(alpha = 0.45f),
                    spotColor = primaryColor.copy(alpha = 0.35f),
                )
                .clip(pillShape)
                .background(aiBarBgColor)
                .border(width = 2.dp, brush = rainbowBrush, shape = pillShape)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left: AI sparkle icon in gradient rounded square
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(iconShape)
                    .background(iconGradient),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(Modifier.width(8.dp))

            // Center: text field + suggestion chips
            Column(modifier = Modifier.weight(1f)) {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.ai_bar_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    singleLine = false,
                    maxLines = 5,
                    textStyle = MaterialTheme.typography.bodyLarge,
                )
                // Quick-tap suggestion chips — fade out once the user starts typing
                AnimatedVisibility(
                    visible = text.isEmpty(),
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    Row(
                        modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(accentColor.copy(alpha = 0.15f))
                                    .clickable { text = suggestion }
                                    .padding(horizontal = 10.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    suggestion,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accentColor,
                                    fontSize = 11.sp,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Right: camera or send in gradient rounded square
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(iconShape)
                    .background(iconGradient)
                    .clickable(enabled = !state.isLoading) {
                        if (text.isNotBlank()) {
                            val query = text.trim()
                            text = ""
                            state.submit(query, errorFallback, onTextMealAnalyzed, onChatOpen)
                        } else {
                            onCameraClick()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (text.isNotBlank()) Icons.AutoMirrored.Filled.Send else Icons.Default.CameraAlt,
                    contentDescription = if (text.isNotBlank()) stringResource(R.string.cd_send) else stringResource(R.string.cd_camera),
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
