package com.fatlosstrack.ui.components

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.*
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    var focused by remember { mutableStateOf(false) }
    val pillShape = RoundedCornerShape(24.dp)
    val errorFallback = stringResource(R.string.error_something_went_wrong)
    val hasText = text.isNotBlank()

    // Capture @Composable colors for use in non-composable modifier lambdas
    val accentColor = Accent
    val primaryColor = Primary
    val aiBarBgColor = AiBarBg
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary

    val isDark = LocalAppColors.current.isDark

    // Border, glow and send button all derive from the active theme preset.
    // The border is a full-spectrum sweep that starts and ends on the theme's primary hue,
    // slowly rotating around the pill.
    val borderColors = remember(primaryColor, isDark) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(primaryColor.toArgb(), hsv)
        val steps = 10
        IntArray(steps + 1) { i ->
            Color.hsv(
                hue = (hsv[0] + i * 360f / steps) % 360f,
                saturation = if (isDark) 0.62f else 0.78f,
                value = if (isDark) 1f else 0.88f,
            ).toArgb()
        }
    }
    // Kept as State and read only in the draw phase, so each frame redraws without recomposing
    val borderRotation = rememberInfiniteTransition(label = "aiBarBorder").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 8000, easing = LinearEasing)),
        label = "aiBarBorderRotation",
    )
    val actionGradient = remember(primaryColor, accentColor) {
        Brush.linearGradient(listOf(primaryColor, accentColor))
    }
    // The window pans for the keyboard by default, and that pan is recomputed on every
    // keystroke as the cursor moves, which makes the bar jump. While the bar is focused,
    // switch to adjustResize (no pan under edge-to-edge) and lift the bar with imePadding.
    // Other screens' text fields keep the default pan behavior.
    val window = LocalActivity.current?.window
    val originalSoftInputMode = remember(window) { window?.attributes?.softInputMode }
    fun setResizeMode(resize: Boolean) {
        val original = originalSoftInputMode ?: return
        window?.setSoftInputMode(
            if (resize) {
                (original and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST.inv()) or
                    WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            } else original
        )
    }
    DisposableEffect(window) {
        onDispose { setResizeMode(false) }
    }

    Column(modifier = modifier.then(if (focused) Modifier.imePadding() else Modifier)) {
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
                    elevation = 16.dp,
                    shape = pillShape,
                    clip = false,
                    ambientColor = accentColor.copy(alpha = 0.35f),
                    spotColor = primaryColor.copy(alpha = 0.30f),
                )
                .clip(pillShape)
                .background(aiBarBgColor)
                .drawWithCache {
                    val strokePx = 2.5.dp.toPx()
                    val center = size.center
                    val shader = android.graphics.SweepGradient(center.x, center.y, borderColors, null)
                    val brush = ShaderBrush(shader)
                    val matrix = android.graphics.Matrix()
                    val radius = pillShape.topStart.toPx(size, this).coerceAtMost(size.height / 2) - strokePx / 2
                    onDrawWithContent {
                        drawContent()
                        matrix.setRotate(borderRotation.value, center.x, center.y)
                        shader.setLocalMatrix(matrix)
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(strokePx / 2, strokePx / 2),
                            size = Size(size.width - strokePx, size.height - strokePx),
                            cornerRadius = CornerRadius(radius),
                            style = Stroke(width = strokePx),
                        )
                    }
                }
                .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // Left: AI sparkle, tinted with the theme gradient
            Box(
                modifier = Modifier.height(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithCache {
                            onDrawWithContent {
                                drawContent()
                                drawRect(actionGradient, blendMode = BlendMode.SrcAtop)
                            }
                        },
                )
            }

            Spacer(Modifier.width(10.dp))

            // Center: text field
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .onFocusChanged {
                        if (it.isFocused != focused) {
                            focused = it.isFocused
                            setResizeMode(it.isFocused)
                        }
                    },
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = OnSurface),
                cursorBrush = SolidColor(primaryColor),
                singleLine = false,
                maxLines = 5,
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.padding(vertical = 9.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                stringResource(R.string.ai_bar_placeholder),
                                style = MaterialTheme.typography.bodyLarge,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            Spacer(Modifier.width(4.dp))

            // Right: quiet camera icon when empty, filled gradient send button once there's text
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !state.isLoading) {
                        if (hasText) {
                            val query = text.trim()
                            text = ""
                            state.submit(query, errorFallback, onTextMealAnalyzed, onChatOpen)
                        } else {
                            onCameraClick()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = hasText, label = "aiBarAction") { showSend ->
                    if (showSend) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(actionGradient),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = stringResource(R.string.cd_send),
                                tint = onPrimaryColor,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    } else {
                        Icon(
                            Icons.Outlined.PhotoCamera,
                            contentDescription = stringResource(R.string.cd_camera),
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
        }
    }
}
