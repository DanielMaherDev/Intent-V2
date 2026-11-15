package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 6 — floating overlay permission probe honesty.
 * When granted, floating companion may show; when denied, manual Conversation route.
 */
class OverlayPermissionProbeTest {

    @Test
    fun liveRegistry_permissionNeededWhenDenied() {
        val access = FakeOverlayPermissionAccess(granted = false)
        val registry = OverlayPermissionProbe.liveRegistry(access)
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_FLOATING_OVERLAY))
        assertFalse(registry.canUseFloatingOverlay())
        assertTrue(
            registry.get(CapabilityRegistry.ID_FLOATING_OVERLAY)!!.limitNote
                .contains("Discuss", ignoreCase = true)
        )
    }

    @Test
    fun liveRegistry_availableWhenGranted_companionReady() {
        val access = FakeOverlayPermissionAccess(granted = true)
        val registry = OverlayPermissionProbe.liveRegistry(access)
        assertTrue(registry.canUseFloatingOverlay())
        assertEquals(
            CapabilityStatus.AVAILABLE,
            registry.get(CapabilityRegistry.ID_FLOATING_OVERLAY)!!.status
        )
        assertTrue(
            registry.get(CapabilityRegistry.ID_FLOATING_OVERLAY)!!.limitNote
                .contains("same agent", ignoreCase = true)
        )
        assertFalse(
            registry.get(CapabilityRegistry.ID_FLOATING_OVERLAY)!!.limitNote
                .contains("not shipped", ignoreCase = true)
        )
    }

    @Test
    fun observe_denied_offersSettingsAndManualRoute() {
        val obs = OverlayPermissionProbe.observe(FakeOverlayPermissionAccess(false))
        assertFalse(obs.permissionGranted)
        assertTrue(obs.canOpenSettings)
        assertTrue(obs.note.contains("no floating", ignoreCase = true))
        assertTrue(obs.note.contains("Discuss", ignoreCase = true))
    }

    @Test
    fun observe_granted_describesCompanion() {
        val obs = OverlayPermissionProbe.observe(FakeOverlayPermissionAccess(true))
        assertTrue(obs.permissionGranted)
        assertFalse(obs.canOpenSettings)
        assertTrue(obs.note.contains("companion", ignoreCase = true))
        assertTrue(obs.note.contains("Conversation", ignoreCase = true))
    }

    @Test
    fun platformCapabilityLive_chainsUsageAndOverlay() {
        val usage = FakeUsageStatsAccess(granted = true)
        val overlay = FakeOverlayPermissionAccess(granted = false)
        val registry = PlatformCapabilityLive.registry(usage, overlay)
        assertTrue(registry.canReadUsageStats())
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_FLOATING_OVERLAY))
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
    }
}
