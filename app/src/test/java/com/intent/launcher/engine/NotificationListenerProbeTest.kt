package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 6 — notification listener live registry + session observation honesty.
 */
class NotificationListenerProbeTest {

    @Test
    fun liveRegistry_permissionNeededWhenDenied() {
        val access = FakeNotificationListenerAccess(granted = false)
        val registry = NotificationListenerProbe.liveRegistry(access)
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_NOTIFICATION_LISTENER))
        assertFalse(registry.canReadNotificationSummaries())
        assertEquals(
            CapabilityStatus.PERMISSION_NEEDED,
            registry.get(CapabilityRegistry.ID_NOTIFICATION_LISTENER)!!.status
        )
        assertTrue(
            registry.get(CapabilityRegistry.ID_NOTIFICATION_LISTENER)!!.limitNote
                .contains("Does not silence", ignoreCase = true)
        )
    }

    @Test
    fun liveRegistry_availableWhenGranted_withHonestyNote() {
        val access = FakeNotificationListenerAccess(granted = true)
        val registry = NotificationListenerProbe.liveRegistry(access)
        assertTrue(registry.canReadNotificationSummaries())
        assertEquals(
            CapabilityStatus.AVAILABLE,
            registry.get(CapabilityRegistry.ID_NOTIFICATION_LISTENER)!!.status
        )
        assertTrue(
            registry.get(CapabilityRegistry.ID_NOTIFICATION_LISTENER)!!.limitNote
                .contains("cannot silence", ignoreCase = true)
        )
        assertTrue(
            registry.get(CapabilityRegistry.ID_NOTIFICATION_LISTENER)!!.limitNote
                .contains("not built yet", ignoreCase = true)
        )
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        // Post-#31: default screen_capture is consent-at-ask (PERMISSION_NEEDED).
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_SCREEN_CAPTURE))
        assertFalse(registry.canUseScreenCapture())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
    }

    @Test
    fun observe_withoutPermission_offersSettings_andDoesNotClaimSilence() {
        val obs = NotificationListenerProbe.observeForSession(
            FakeNotificationListenerAccess(granted = false)
        )
        assertFalse(obs.permissionGranted)
        assertTrue(obs.canOpenSettings)
        assertEquals(
            "android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS",
            NotificationListenerProbe.SETTINGS_ACTION
        )
        assertTrue(obs.note.contains("not granted", ignoreCase = true))
        assertTrue(obs.note.contains("not silence", ignoreCase = true))
        assertTrue(obs.note.contains("Complete", ignoreCase = true))
    }

    @Test
    fun observe_granted_saysSummariesNotBuiltYet() {
        val obs = NotificationListenerProbe.observeForSession(
            FakeNotificationListenerAccess(granted = true)
        )
        assertTrue(obs.permissionGranted)
        assertFalse(obs.canOpenSettings)
        assertTrue(obs.note.contains("not built yet", ignoreCase = true))
        assertTrue(obs.note.contains("quiet mode", ignoreCase = true))
    }

    @Test
    fun platformCapabilityLive_chainsNotifications() {
        val registry = PlatformCapabilityLive.registry(
            usage = FakeUsageStatsAccess(granted = true),
            overlay = FakeOverlayPermissionAccess(granted = true),
            voicePermission = FakeVoicePermissionAccess(granted = true),
            speech = FakeSpeechToTextAccess(available = true),
            notifications = FakeNotificationListenerAccess(granted = true)
        )
        assertTrue(registry.canReadUsageStats())
        assertTrue(registry.canUseFloatingOverlay())
        assertTrue(registry.canUseVoiceInput())
        assertTrue(registry.canReadNotificationSummaries())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        // Default FakeScreenCaptureAccess is available + NOT_ASKED → PERMISSION_NEEDED.
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_SCREEN_CAPTURE))
        assertFalse(registry.canUseScreenCapture())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_LIVE_LLM))
    }
}
