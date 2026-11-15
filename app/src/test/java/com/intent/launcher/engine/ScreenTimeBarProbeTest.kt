package com.intent.launcher.engine

import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.ScreenTimeBar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * OS-136 probe — day-window UsageStats + chrome prefs; floater-independent.
 */
class ScreenTimeBarProbeTest {

    private val tz = TimeZone.getTimeZone("UTC")
    private val noon = 1_700_000_000_000L // arbitrary fixed epoch

    @Test
    fun denied_showsGrantPath_notMinutes() {
        val access = FakeUsageStatsAccess(granted = false)
        val prefs = FakeHomeChromePrefs(visible = true)
        val obs = ScreenTimeBarProbe.observe(
            access = access,
            prefs = prefs,
            focusDestination = DestinationApp("com.duolingo", "Duolingo"),
            nowEpochMs = noon,
            timeZone = tz
        )
        assertFalse(obs.permissionGranted)
        assertTrue(obs.canOpenSettings)
        assertTrue(obs.content is ScreenTimeBar.Content.NeedsPermission)
    }

    @Test
    fun granted_focusPackage_dayWindow() {
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf("com.duolingo" to 90_000L),
            deviceTotalForegroundMs = 400_000L
        )
        val prefs = FakeHomeChromePrefs(visible = true)
        val obs = ScreenTimeBarProbe.observe(
            access = access,
            prefs = prefs,
            focusDestination = DestinationApp("com.duolingo", "Duolingo"),
            nowEpochMs = noon,
            timeZone = tz
        )
        assertTrue(obs.permissionGranted)
        assertFalse(obs.canOpenSettings)
        assertTrue(obs.content is ScreenTimeBar.Content.FocusPackageToday)
        val focus = obs.content as ScreenTimeBar.Content.FocusPackageToday
        assertEquals("Duolingo", focus.packageLabel)
        assertEquals(90_000L, focus.foregroundMs)
    }

    @Test
    fun hidden_whenPrefOff_evenIfGranted() {
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf("com.duolingo" to 90_000L)
        )
        val prefs = FakeHomeChromePrefs(visible = false)
        val obs = ScreenTimeBarProbe.observe(
            access = access,
            prefs = prefs,
            focusDestination = DestinationApp("com.duolingo", "Duolingo"),
            nowEpochMs = noon,
            timeZone = tz
        )
        assertEquals(ScreenTimeBar.Content.Hidden, obs.content)
    }

    @Test
    fun floatingCompanion_unaffectedByBarPrefs() {
        // Independence: FloatingCompanion.presence ignores Home chrome prefs entirely.
        val next = com.intent.launcher.domain.NextAction(
            kind = com.intent.launcher.domain.NextActionKind.DISCUSS,
            label = "Talk",
            detail = "",
            reason = "Ready when you are.",
            intentionId = "i1",
            discussLabel = "Discuss"
        )
        val registry = com.intent.launcher.capability.CapabilityRegistry(
            com.intent.launcher.capability.CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
                if (entry.id == com.intent.launcher.capability.CapabilityRegistry.ID_FLOATING_OVERLAY) {
                    entry.copy(
                        status = com.intent.launcher.capability.CapabilityStatus.AVAILABLE,
                        limitNote = "ok"
                    )
                } else {
                    entry
                }
            }
        )
        val presence = FloatingCompanion.presence(
            nextAction = next,
            overlayGranted = true,
            registry = registry
        )
        assertEquals(FloatingAgentState.IDLE, presence.state)
        assertNull(presence.manualRouteNote)
        // Bar prefs are not parameters of presence — toggling them cannot change floater state.
        FakeHomeChromePrefs(visible = false).setScreenTimeBarVisible(false)
        FakeHomeChromePrefs(visible = true).setScreenTimeBarVisible(true)
        val afterToggle = FloatingCompanion.presence(
            nextAction = next,
            overlayGranted = true,
            registry = registry
        )
        assertEquals(presence.state, afterToggle.state)
        assertEquals(presence.label, afterToggle.label)
    }

    @Test
    fun startOfLocalDay_isMidnight() {
        val start = DailyCycle.startOfLocalDayEpochMs(noon, tz)
        val cal = java.util.Calendar.getInstance(tz)
        cal.timeInMillis = start
        assertEquals(0, cal.get(java.util.Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(java.util.Calendar.MINUTE))
        assertEquals(0, cal.get(java.util.Calendar.SECOND))
        assertTrue(start <= noon)
    }
}
