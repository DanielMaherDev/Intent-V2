package com.intent.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OS-136 — top screen-time bar honesty + configurability (independent of floater).
 */
class ScreenTimeBarTest {

    @Test
    fun hidden_whenPreferenceOff() {
        val content = ScreenTimeBar.resolve(
            visiblePref = false,
            usageGranted = true,
            focusPackageLabel = "Duolingo",
            focusForegroundMs = 60_000L,
            deviceForegroundMs = 120_000L,
            canOpenSettings = false
        )
        assertEquals(ScreenTimeBar.Content.Hidden, content)
    }

    @Test
    fun needsPermission_whenUsageDenied() {
        val content = ScreenTimeBar.resolve(
            visiblePref = true,
            usageGranted = false,
            focusPackageLabel = null,
            focusForegroundMs = null,
            deviceForegroundMs = null,
            canOpenSettings = true
        )
        assertTrue(content is ScreenTimeBar.Content.NeedsPermission)
        val needs = content as ScreenTimeBar.Content.NeedsPermission
        assertTrue(needs.canOpenSettings)
        assertTrue(needs.text.contains("usage access", ignoreCase = true))
        assertTrue(needs.contentDescription.contains("not lesson", ignoreCase = true))
        assertFalse(needs.text.contains("0:00"))
    }

    @Test
    fun prefersFocusPackageWhenKnown() {
        val content = ScreenTimeBar.resolve(
            visiblePref = true,
            usageGranted = true,
            focusPackageLabel = "Duolingo",
            focusForegroundMs = 125_000L,
            deviceForegroundMs = 500_000L,
            canOpenSettings = false
        )
        assertTrue(content is ScreenTimeBar.Content.FocusPackageToday)
        val focus = content as ScreenTimeBar.Content.FocusPackageToday
        assertTrue(focus.text.contains("Duolingo"))
        assertTrue(focus.text.contains("today", ignoreCase = true))
        assertTrue(focus.contentDescription.contains("not lesson", ignoreCase = true))
        assertTrue(focus.contentDescription.contains("floating", ignoreCase = true))
        assertEquals(125_000L, focus.foregroundMs)
    }

    @Test
    fun deviceTotal_whenNoFocusPackage() {
        val content = ScreenTimeBar.resolve(
            visiblePref = true,
            usageGranted = true,
            focusPackageLabel = null,
            focusForegroundMs = null,
            deviceForegroundMs = 3_725_000L,
            canOpenSettings = false
        )
        assertTrue(content is ScreenTimeBar.Content.DeviceForegroundToday)
        val device = content as ScreenTimeBar.Content.DeviceForegroundToday
        assertTrue(device.text.contains("package foreground", ignoreCase = true))
        assertTrue(device.contentDescription.contains("not lesson", ignoreCase = true))
    }

    @Test
    fun grantedNoData_whenMinutesMissing() {
        val content = ScreenTimeBar.resolve(
            visiblePref = true,
            usageGranted = true,
            focusPackageLabel = null,
            focusForegroundMs = null,
            deviceForegroundMs = null,
            canOpenSettings = false
        )
        assertTrue(content is ScreenTimeBar.Content.GrantedNoData)
        val empty = content as ScreenTimeBar.Content.GrantedNoData
        assertTrue(empty.text.contains("no focus", ignoreCase = true))
    }

    @Test
    fun neverInventsMinutesWithoutGrant() {
        val content = ScreenTimeBar.resolve(
            visiblePref = true,
            usageGranted = false,
            focusPackageLabel = "Duolingo",
            focusForegroundMs = 999_000L,
            deviceForegroundMs = 999_000L,
            canOpenSettings = true
        )
        assertTrue(content is ScreenTimeBar.Content.NeedsPermission)
    }
}
