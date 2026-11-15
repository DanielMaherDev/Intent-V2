package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun fromStorage_defaultsToSystem() {
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage(null))
        assertEquals(ThemePreference.SYSTEM, ThemePreference.fromStorage("nope"))
        assertEquals(ThemePreference.DARK, ThemePreference.fromStorage("DARK"))
    }

    @Test
    fun fakePrefs_roundTrip_doesNotTouchStore() {
        val prefs = FakeHomeChromePrefs()
        assertEquals(ThemePreference.SYSTEM, prefs.themePreference())
        prefs.setThemePreference(ThemePreference.DARK)
        assertEquals(ThemePreference.DARK, prefs.themePreference())
        prefs.setThemePreference(ThemePreference.LIGHT)
        assertEquals(ThemePreference.LIGHT, prefs.themePreference())
    }
}
