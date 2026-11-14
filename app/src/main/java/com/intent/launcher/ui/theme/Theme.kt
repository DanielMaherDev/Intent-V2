package com.intent.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Warm paper + restrained indigo — matches docs/design/intent-four-core-screens.html */
object IntentColors {
    val Paper = Color(0xFFF4F2EC)
    val Card = Color(0xFFFFFEFA)
    val Ink = Color(0xFF202522)
    val Muted = Color(0xFF68716C)
    val Line = Color(0xFFDEDFD6)
    val Accent = Color(0xFF5459C9)
    val AccentDark = Color(0xFF4146AE)
    val AccentSoft = Color(0xFFE9E9FA)
    val Success = Color(0xFF2D786A)
    val SuccessSoft = Color(0xFFE5F1EC)
    val Warm = Color(0xFFA76531)
    val WarmSoft = Color(0xFFF8EEE2)
    val NavBar = Color(0xFFFAF9F5)
    val Pill = Color(0xFFEFF0EB)
    val PillText = Color(0xFF536058)
    val EmptyBorder = Color(0xFFC9CFC5)
    val ComposerHint = Color(0xFF7A847C)
    val Lede = Color(0xFF4E5952)
    val Section = Color(0xFF59645C)
    val FactText = Color(0xFF4C5851)
    val NavIdle = Color(0xFF6D766E)
    val Arrow = Color(0xFF8E9891)
    val GridCell = Color(0xFFF2F2EC)
    val CalloutText = Color(0xFF444A84)
    val ComposerBorder = Color(0xFFCDD1C9)
    val EmptyFill = Color(0xFFFBFAF6)
    val UserBubble = Color(0xFFF3EDE3)
}

private val Scheme = lightColorScheme(
    primary = IntentColors.Accent,
    onPrimary = Color.White,
    primaryContainer = IntentColors.AccentSoft,
    onPrimaryContainer = IntentColors.AccentDark,
    secondary = IntentColors.Warm,
    onSecondary = Color.White,
    secondaryContainer = IntentColors.WarmSoft,
    onSecondaryContainer = IntentColors.Warm,
    tertiary = IntentColors.Success,
    onTertiary = Color.White,
    tertiaryContainer = IntentColors.SuccessSoft,
    onTertiaryContainer = IntentColors.Success,
    background = IntentColors.Paper,
    onBackground = IntentColors.Ink,
    surface = IntentColors.Card,
    onSurface = IntentColors.Ink,
    surfaceVariant = IntentColors.NavBar,
    onSurfaceVariant = IntentColors.Muted,
    outline = IntentColors.Line,
    outlineVariant = IntentColors.Line
)

/**
 * Inter-like stack (platform sans). HTML mock + JPG mocks use sans display —
 * v0.0.03 serif Home headline was the documented mismatch.
 */
private val IntentTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 35.sp,
        lineHeight = 38.sp,
        letterSpacing = (-2.0).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp,
        lineHeight = 36.sp,
        letterSpacing = (-1.6).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 30.sp,
        letterSpacing = (-1.0).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 21.sp,
        lineHeight = 24.sp,
        letterSpacing = (-0.8).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        lineHeight = 20.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 1.2.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 1.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 12.sp,
        letterSpacing = 0.8.sp
    )
)

@Composable
fun IntentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = Scheme,
        typography = IntentTypography,
        content = content
    )
}
