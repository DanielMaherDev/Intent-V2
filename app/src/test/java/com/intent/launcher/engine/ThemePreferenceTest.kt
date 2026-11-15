package com.intent.launcher.engine

import com.intent.launcher.ui.theme.DarkIntentColors
import com.intent.launcher.ui.theme.HighContrastDarkIntentColors
import com.intent.launcher.ui.theme.HighContrastLightIntentColors
import com.intent.launcher.ui.theme.LightIntentColors
import com.intent.launcher.ui.theme.resolveIntentColorTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemePreferenceTest {

    @Test
    fun system_followsPlatform() {
        assertTrue(ThemePreference.SYSTEM.resolvesToDark(systemDark = true))
        assertFalse(ThemePreference.SYSTEM.resolvesToDark(systemDark = false))
    }

    @Test
    fun lightAndDark_lockRegardlessOfSystem() {
        assertFalse(ThemePreference.LIGHT.resolvesToDark(systemDark = true))
        assertFalse(ThemePreference.LIGHT.resolvesToDark(systemDark = false))
        assertTrue(ThemePreference.DARK.resolvesToDark(systemDark = true))
        assertTrue(ThemePreference.DARK.resolvesToDark(systemDark = false))
    }

    @Test
    fun highContrast_followsDeviceLightDark_forPalettePick() {
        assertTrue(ThemePreference.HIGH_CONTRAST.resolvesToDark(systemDark = true))
        assertFalse(ThemePreference.HIGH_CONTRAST.resolvesToDark(systemDark = false))
        assertTrue(ThemePreference.HIGH_CONTRAST.usesHighContrastTokens())
        assertFalse(ThemePreference.SYSTEM.usesHighContrastTokens())
        assertFalse(ThemePreference.DARK.usesHighContrastTokens())
    }

    @Test
    fun fromStorage_defaultsToSystem() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage("nope"))
        assertEquals(ThemePreference.DARK, ThemePreference.fromStorage("DARK"))
        assertEquals(
            ThemePreference.HIGH_CONTRAST,
            ThemePreference.fromStorage("HIGH_CONTRAST")
        )
    }

    @Test
    fun fakePrefs_roundTrip_doesNotTouchStore() {
        val prefs = FakeHomeChromePrefs()
        assertEquals(ThemePreference.SYSTEM, prefs.themePreference())
        prefs.setThemePreference(ThemePreference.DARK)
        assertEquals(ThemePreference.DARK, prefs.themePreference())
        prefs.setThemePreference(ThemePreference.LIGHT)
        assertEquals(ThemePreference.LIGHT, prefs.themePreference())
        prefs.setThemePreference(ThemePreference.HIGH_CONTRAST)
        assertEquals(ThemePreference.HIGH_CONTRAST, prefs.themePreference())
    }

    @Test
    fun resolveTokens_standardPalettes() {
        assertEquals(
            LightIntentColors,
            resolveIntentColorTokens(ThemePreference.LIGHT, systemDark = true)
        )
        assertEquals(
            DarkIntentColors,
            resolveIntentColorTokens(ThemePreference.DARK, systemDark = false)
        )
        assertEquals(
            LightIntentColors,
            resolveIntentColorTokens(ThemePreference.SYSTEM, systemDark = false)
        )
        assertEquals(
            DarkIntentColors,
            resolveIntentColorTokens(ThemePreference.SYSTEM, systemDark = true)
        )
    }

    @Test
    fun resolveTokens_highContrast_usesElevatedPalettes() {
        val hcLight = resolveIntentColorTokens(
            ThemePreference.HIGH_CONTRAST,
            systemDark = false
        )
        val hcDark = resolveIntentColorTokens(
            ThemePreference.HIGH_CONTRAST,
            systemDark = true
        )
        assertEquals(HighContrastLightIntentColors, hcLight)
        assertEquals(HighContrastDarkIntentColors, hcDark)
        assertNotEquals(LightIntentColors, hcLight)
        assertNotEquals(DarkIntentColors, hcDark)
    }

    @Test
    fun highContrast_inkPaper_moreExtremeThanStandard() {
        // Light: HC ink darker / paper lighter (or equal white) than standard.
        assertTrue(
            relativeLuminance(HighContrastLightIntentColors.Ink) <=
                relativeLuminance(LightIntentColors.Ink)
        )
        assertTrue(
            relativeLuminance(HighContrastLightIntentColors.Paper) >=
                relativeLuminance(LightIntentColors.Paper)
        )
        assertTrue(
            contrastRatio(
                HighContrastLightIntentColors.Ink,
                HighContrastLightIntentColors.Paper
            ) >
                contrastRatio(LightIntentColors.Ink, LightIntentColors.Paper)
        )

        // Dark: HC ink lighter / paper darker than standard.
        assertTrue(
            relativeLuminance(HighContrastDarkIntentColors.Ink) >=
                relativeLuminance(DarkIntentColors.Ink)
        )
        assertTrue(
            relativeLuminance(HighContrastDarkIntentColors.Paper) <=
                relativeLuminance(DarkIntentColors.Paper)
        )
        assertTrue(
            contrastRatio(
                HighContrastDarkIntentColors.Ink,
                HighContrastDarkIntentColors.Paper
            ) >
                contrastRatio(DarkIntentColors.Ink, DarkIntentColors.Paper)
        )
    }

    @Test
    fun highContrast_meetsAaBodyTextFloor() {
        // WCAG AA for normal text is 4.5:1 — HC ink/paper should clear it honestly.
        assertTrue(
            contrastRatio(
                HighContrastLightIntentColors.Ink,
                HighContrastLightIntentColors.Paper
            ) >= 4.5
        )
        assertTrue(
            contrastRatio(
                HighContrastDarkIntentColors.Ink,
                HighContrastDarkIntentColors.Paper
            ) >= 4.5
        )
        assertTrue(
            contrastRatio(
                HighContrastLightIntentColors.Muted,
                HighContrastLightIntentColors.Paper
            ) >= 4.5
        )
        assertTrue(
            contrastRatio(
                HighContrastDarkIntentColors.Muted,
                HighContrastDarkIntentColors.Paper
            ) >= 4.5
        )
    }

    /** Relative luminance per WCAG 2.x (sRGB). */
    private fun relativeLuminance(color: androidx.compose.ui.graphics.Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
    }

    private fun contrastRatio(
        a: androidx.compose.ui.graphics.Color,
        b: androidx.compose.ui.graphics.Color
    ): Double {
        val l1 = relativeLuminance(a)
        val l2 = relativeLuminance(b)
        val lighter = maxOf(l1, l2)
        val darker = minOf(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
