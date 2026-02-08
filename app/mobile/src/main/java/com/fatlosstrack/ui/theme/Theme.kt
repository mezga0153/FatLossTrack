package com.fatlosstrack.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ── Palette ───────────────────────────────────────────────
// Deep dark base with warm accent pops — feels alive, not flat.
val Surface = Color(0xFF0D0D12)           // near-black with blue undertone
val SurfaceVariant = Color(0xFF161622)    // slightly lifted, cool indigo cast
val CardSurface = Color(0xFF1E1E2E)       // cards — distinct from background
val OnSurface = Color(0xFFECECF4)         // high-contrast text
val OnSurfaceVariant = Color(0xFF8B8BA3)  // secondary text — lavender grey

val Primary = Color(0xFF6C9CFF)           // vivid periwinkle blue — primary CTA, trend lines
val PrimaryContainer = Color(0xFF1D2D50)  // deep blue for containers
val Secondary = Color(0xFF59D8A0)         // mint green — positive trends
val Tertiary = Color(0xFFFF8F6B)          // warm coral — attention / warnings
val Accent = Color(0xFFCDA0FF)            // soft violet — highlights, AI bar glow

val TrendDown = Color(0xFF4ADE80)         // green — weight going down (good)
val TrendUp = Color(0xFFFF7A5C)           // coral red — weight going up
val TrendFlat = Color(0xFF6B6B80)         // muted slate
val ConfidenceBand = Color(0x336C9CFF)    // translucent primary
val AiBarBg = Color(0xFF252540)           // AI pill background — distinct from cards

private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.Black,
    primaryContainer = PrimaryContainer,
    secondary = Secondary,
    tertiary = Tertiary,
    background = Surface,
    surface = SurfaceVariant,
    surfaceVariant = CardSurface,
    onBackground = OnSurface,
    onSurface = OnSurface,
    onSurfaceVariant = OnSurfaceVariant,
    error = Color(0xFFFF6B6B),
    outline = Color(0xFF2A2A40),
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
