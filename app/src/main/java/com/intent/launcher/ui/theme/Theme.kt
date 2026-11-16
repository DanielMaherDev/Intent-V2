package com.intent.launcher.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.intent.launcher.engine.ReducedMotion
import com.intent.launcher.engine.ReducedMotionPreference
import com.intent.launcher.engine.ThemePreference

/**
 * App-level reduced-motion token. True when platform Animator duration scale is
 * 0 (Remove animations) and/or Settings prefers reduced motion.
 * Screens branch on this for instant pager/card transitions. Platform Animator
 * scale still collapses framework motion when Remove animations is on.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

/**
 * Intent palette tokens (warm paper + restrained indigo).
 * Light matches `docs/design/intent-four-core-screens.html`; dark inverts the same language
 * (warm charcoal paper, lifted indigo accent) — not a generic purple AI dark theme.
 */
@Immutable
data class IntentColorTokens(
    val Paper: Color,
    val Card: Color,
    val Ink: Color,
    val Muted: Color,
    val Line: Color,
    val Accent: Color,
    val AccentDark: Color,
    val AccentSoft: Color,
    val Success: Color,
    val SuccessSoft: Color,
    val Warm: Color,
    val WarmSoft: Color,
    val NavBar: Color,
    val Pill: Color,
    val PillText: Color,
    val EmptyBorder: Color,
    val ComposerHint: Color,
    val Lede: Color,
    val Section: Color,
    val FactText: Color,
    val NavIdle: Color,
    val Arrow: Color,
    val GridCell: Color,
    val CalloutText: Color,
    val ComposerBorder: Color,
    val EmptyFill: Color,
    val UserBubble: Color
)

val LightIntentColors = IntentColorTokens(
    Paper = Color(0xFFF4F2EC),
    Card = Color(0xFFFFFEFA),
    Ink = Color(0xFF202522),
    Muted = Color(0xFF68716C),
    Line = Color(0xFFDEDFD6),
    Accent = Color(0xFF5459C9),
    AccentDark = Color(0xFF4146AE),
    AccentSoft = Color(0xFFE9E9FA),
    Success = Color(0xFF2D786A),
    SuccessSoft = Color(0xFFE5F1EC),
    Warm = Color(0xFFA76531),
    WarmSoft = Color(0xFFF8EEE2),
    NavBar = Color(0xFFFAF9F5),
    Pill = Color(0xFFEFF0EB),
    PillText = Color(0xFF536058),
    EmptyBorder = Color(0xFFC9CFC5),
    ComposerHint = Color(0xFF7A847C),
    Lede = Color(0xFF4E5952),
    Section = Color(0xFF59645C),
    FactText = Color(0xFF4C5851),
    NavIdle = Color(0xFF6D766E),
    Arrow = Color(0xFF8E9891),
    GridCell = Color(0xFFF2F2EC),
    CalloutText = Color(0xFF444A84),
    ComposerBorder = Color(0xFFCDD1C9),
    EmptyFill = Color(0xFFFBFAF6),
    UserBubble = Color(0xFFF3EDE3)
)

/** Warm charcoal + lifted indigo — same roles as [LightIntentColors], not pure black. */
val DarkIntentColors = IntentColorTokens(
    Paper = Color(0xFF161815),
    Card = Color(0xFF1E211E),
    Ink = Color(0xFFE6E4DC),
    Muted = Color(0xFF9AA39B),
    Line = Color(0xFF2F342F),
    Accent = Color(0xFF8B90E0),
    AccentDark = Color(0xFFB4B8F0),
    AccentSoft = Color(0xFF2A2D4A),
    Success = Color(0xFF5AA894),
    SuccessSoft = Color(0xFF1C2E28),
    Warm = Color(0xFFC4874A),
    WarmSoft = Color(0xFF2E2418),
    NavBar = Color(0xFF1A1D1A),
    Pill = Color(0xFF2A2E2A),
    PillText = Color(0xFFB0B8B2),
    EmptyBorder = Color(0xFF3F463F),
    ComposerHint = Color(0xFF8A938B),
    Lede = Color(0xFFB5BDB6),
    Section = Color(0xFFA8B1AA),
    FactText = Color(0xFFB0B8B2),
    NavIdle = Color(0xFF8E9891),
    Arrow = Color(0xFF7A847C),
    GridCell = Color(0xFF222622),
    CalloutText = Color(0xFFC5C8EE),
    ComposerBorder = Color(0xFF3A403A),
    EmptyFill = Color(0xFF1A1D1A),
    UserBubble = Color(0xFF2A261F)
)

/**
 * High-contrast light — near-white paper / near-black ink / hard lines.
 * Same semantic roles as [LightIntentColors]; elevated contrast only.
 */
val HighContrastLightIntentColors = IntentColorTokens(
    Paper = Color(0xFFFFFFFF),
    Card = Color(0xFFFFFFFF),
    Ink = Color(0xFF000000),
    Muted = Color(0xFF2A2A2A),
    Line = Color(0xFF000000),
    Accent = Color(0xFF1E2278),
    AccentDark = Color(0xFF12154A),
    AccentSoft = Color(0xFFD8DAF8),
    Success = Color(0xFF0F4F42),
    SuccessSoft = Color(0xFFC8E8DE),
    Warm = Color(0xFF7A3A0A),
    WarmSoft = Color(0xFFF0DCC4),
    NavBar = Color(0xFFF5F5F5),
    Pill = Color(0xFFE8E8E8),
    PillText = Color(0xFF000000),
    EmptyBorder = Color(0xFF000000),
    ComposerHint = Color(0xFF3A3A3A),
    Lede = Color(0xFF1A1A1A),
    Section = Color(0xFF1A1A1A),
    FactText = Color(0xFF1A1A1A),
    NavIdle = Color(0xFF2A2A2A),
    Arrow = Color(0xFF000000),
    GridCell = Color(0xFFF0F0F0),
    CalloutText = Color(0xFF12154A),
    ComposerBorder = Color(0xFF000000),
    EmptyFill = Color(0xFFFFFFFF),
    UserBubble = Color(0xFFEFEFEF)
)

/**
 * High-contrast dark — pure black paper / pure white ink / hard lines.
 * Same semantic roles as [DarkIntentColors]; elevated contrast only.
 */
val HighContrastDarkIntentColors = IntentColorTokens(
    Paper = Color(0xFF000000),
    Card = Color(0xFF000000),
    Ink = Color(0xFFFFFFFF),
    Muted = Color(0xFFD0D0D0),
    Line = Color(0xFFFFFFFF),
    Accent = Color(0xFFB8BCFF),
    AccentDark = Color(0xFFE0E2FF),
    AccentSoft = Color(0xFF1A1C4A),
    Success = Color(0xFF7AD4BC),
    SuccessSoft = Color(0xFF0A2A22),
    Warm = Color(0xFFFFB060),
    WarmSoft = Color(0xFF2A1808),
    NavBar = Color(0xFF000000),
    Pill = Color(0xFF1A1A1A),
    PillText = Color(0xFFFFFFFF),
    EmptyBorder = Color(0xFFFFFFFF),
    ComposerHint = Color(0xFFC8C8C8),
    Lede = Color(0xFFE8E8E8),
    Section = Color(0xFFE0E0E0),
    FactText = Color(0xFFE8E8E8),
    NavIdle = Color(0xFFD0D0D0),
    Arrow = Color(0xFFFFFFFF),
    GridCell = Color(0xFF0A0A0A),
    CalloutText = Color(0xFFE0E2FF),
    ComposerBorder = Color(0xFFFFFFFF),
    EmptyFill = Color(0xFF000000),
    UserBubble = Color(0xFF1A1A1A)
)

/**
 * Pure token selection for [IntentTheme] + JVM tests.
 * [ThemePreference.HIGH_CONTRAST] picks HC light/dark from [systemDark];
 * other prefs keep the standard light/dark palettes.
 */
fun resolveIntentColorTokens(
    preference: ThemePreference,
    systemDark: Boolean
): IntentColorTokens {
    val dark = preference.resolvesToDark(systemDark)
    return when {
        preference.usesHighContrastTokens() && dark -> HighContrastDarkIntentColors
        preference.usesHighContrastTokens() -> HighContrastLightIntentColors
        dark -> DarkIntentColors
        else -> LightIntentColors
    }
}

val LocalIntentColors = staticCompositionLocalOf { LightIntentColors }

/**
 * Theme-aware Intent palette. Properties resolve from [LocalIntentColors] —
 * call from composition only (all existing screen/chrome sites).
 */
object IntentColors {
    val Paper: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Paper
    val Card: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Card
    val Ink: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Ink
    val Muted: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Muted
    val Line: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Line
    val Accent: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Accent
    val AccentDark: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.AccentDark
    val AccentSoft: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.AccentSoft
    val Success: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Success
    val SuccessSoft: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.SuccessSoft
    val Warm: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Warm
    val WarmSoft: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.WarmSoft
    val NavBar: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.NavBar
    val Pill: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Pill
    val PillText: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.PillText
    val EmptyBorder: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.EmptyBorder
    val ComposerHint: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.ComposerHint
    val Lede: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Lede
    val Section: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Section
    val FactText: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.FactText
    val NavIdle: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.NavIdle
    val Arrow: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.Arrow
    val GridCell: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.GridCell
    val CalloutText: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.CalloutText
    val ComposerBorder: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.ComposerBorder
    val EmptyFill: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.EmptyFill
    val UserBubble: Color
        @Composable @ReadOnlyComposable get() = LocalIntentColors.current.UserBubble
}

private fun intentLightScheme(c: IntentColorTokens) = lightColorScheme(
    primary = c.Accent,
    onPrimary = Color.White,
    primaryContainer = c.AccentSoft,
    onPrimaryContainer = c.AccentDark,
    secondary = c.Warm,
    onSecondary = Color.White,
    secondaryContainer = c.WarmSoft,
    onSecondaryContainer = c.Warm,
    tertiary = c.Success,
    onTertiary = Color.White,
    tertiaryContainer = c.SuccessSoft,
    onTertiaryContainer = c.Success,
    background = c.Paper,
    onBackground = c.Ink,
    surface = c.Card,
    onSurface = c.Ink,
    surfaceVariant = c.NavBar,
    onSurfaceVariant = c.Muted,
    outline = c.Line,
    outlineVariant = c.Line
)

private fun intentDarkScheme(c: IntentColorTokens) = darkColorScheme(
    primary = c.Accent,
    onPrimary = Color(0xFF161815),
    primaryContainer = c.AccentSoft,
    onPrimaryContainer = c.AccentDark,
    secondary = c.Warm,
    onSecondary = Color(0xFF161815),
    secondaryContainer = c.WarmSoft,
    onSecondaryContainer = c.Warm,
    tertiary = c.Success,
    onTertiary = Color(0xFF161815),
    tertiaryContainer = c.SuccessSoft,
    onTertiaryContainer = c.Success,
    background = c.Paper,
    onBackground = c.Ink,
    surface = c.Card,
    onSurface = c.Ink,
    surfaceVariant = c.NavBar,
    onSurfaceVariant = c.Muted,
    outline = c.Line,
    outlineVariant = c.Line
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
fun IntentTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    reducedMotionPreference: ReducedMotionPreference = ReducedMotionPreference.FOLLOW_SYSTEM,
    systemDark: Boolean = isSystemInDarkTheme(),
    darkTheme: Boolean = themePreference.resolvesToDark(systemDark),
    /** Override for tests; null reads [Settings.Global.ANIMATOR_DURATION_SCALE]. */
    animatorDurationScaleOverride: Float? = null,
    content: @Composable () -> Unit
) {
    val tokens = resolveIntentColorTokens(themePreference, systemDark)
    val scheme = if (darkTheme) intentDarkScheme(tokens) else intentLightScheme(tokens)
    val context = LocalContext.current
    val animatorScale = animatorDurationScaleOverride ?: remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            )
        }.getOrDefault(1f)
    }
    val reduced = ReducedMotion.resolvesToReduced(reducedMotionPreference, animatorScale)
    CompositionLocalProvider(
        LocalIntentColors provides tokens,
        LocalReducedMotion provides reduced
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = IntentTypography,
            content = content
        )
    }
}
