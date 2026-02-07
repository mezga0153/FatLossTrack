package com.fatlosstrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Muted, neutral, instrument-panel palette — dark only
val Surface = Color(0xFF0F0F0F)
val SurfaceVariant = Color(0xFF1A1A1A)
val CardSurface = Color(0xFF222222)
val OnSurface = Color(0xFFE0E0E0)
val OnSurfaceVariant = Color(0xFF9E9E9E)
val Primary = Color(0xFF8AB4F8)           // calm blue — trend lines, primary actions
val PrimaryContainer = Color(0xFF1A3A5C)
val TrendDown = Color(0xFF81C995)         // muted green — weight going down (good)
val TrendUp = Color(0xFFE8A87C)           // muted amber — weight going up
val TrendFlat = Color(0xFF9E9E9E)         // grey — flat
val ConfidenceBand = Color(0x338AB4F8)    // translucent blue

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.Black,
    primaryContainer = PrimaryContainer,
    secondary = TrendDown,
    tertiary = TrendUp,
    background = Surface,
    surface = SurfaceVariant,
    surfaceVariant = CardSurface,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    error = Color(0xFFCF6679),
    outline = Color(0xFF333333),
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
    ),
    titleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelLarge = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
    ),
)

@Composable
fun FatLossTrackTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
