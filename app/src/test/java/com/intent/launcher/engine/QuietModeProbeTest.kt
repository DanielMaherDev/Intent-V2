package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuietModeProbeTest {

    @Test
    fun liveRegistry_keepsQuietModeNotSupported_evenWithPolicyAccess() {
        val denied = QuietModeProbe.liveRegistry(FakeQuietModeAccess(policyAccessGranted = false))
        assertTrue(denied.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        assertFalse(denied.canApplyQuietMode())
        assertEquals(
            CapabilityStatus.NOT_SUPPORTED,
            denied.get(CapabilityRegistry.ID_QUIET_MODE)!!.status
        )
        assertTrue(
            denied.get(CapabilityRegistry.ID_QUIET_MODE)!!.limitNote
                .contains("cannot silence", ignoreCase = true)
        )

        val granted = QuietModeProbe.liveRegistry(FakeQuietModeAccess(policyAccessGranted = true))
        assertTrue(granted.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        assertFalse(granted.canApplyQuietMode())
        assertTrue(
            granted.get(CapabilityRegistry.ID_QUIET_MODE)!!.limitNote
                .contains("does not apply quiet", ignoreCase = true)
        )
        assertTrue(
            granted.get(CapabilityRegistry.ID_QUIET_MODE)!!.limitNote
                .contains("OEM", ignoreCase = true)
        )
    }

    @Test
    fun observeAfterRequest_neverClaimsSilenceApplied() {
        val obs = QuietModeProbe.observeAfterRequest(
            access = FakeQuietModeAccess(policyAccessGranted = false),
            quietRequested = true
        )
        assertTrue(obs.quietRequested)
        assertFalse(obs.silenceApplied)
        assertTrue(obs.canOpenDndSettings)
        assertEquals(QuietModeProbe.DND_SETTINGS_ACTION, obs.dndSettingsAction)
        assertTrue(obs.note.contains("cannot silence", ignoreCase = true))
        assertTrue(obs.note.contains("No notifications were suppressed", ignoreCase = true))
        assertTrue(obs.note.contains("Emergency", ignoreCase = true))
    }

    @Test
    fun observeAfterRequest_withoutRequest_isEmpty() {
        val obs = QuietModeProbe.observeAfterRequest(
            access = FakeQuietModeAccess(policyAccessGranted = true),
            quietRequested = false
        )
        assertFalse(obs.quietRequested)
        assertFalse(obs.silenceApplied)
        assertFalse(obs.canOpenDndSettings)
        assertTrue(obs.note.isEmpty())
    }

    @Test
    fun platformCapabilityLive_chainsQuietModeHonesty() {
        val registry = PlatformCapabilityLive.registry(
            usage = FakeUsageStatsAccess(granted = true),
            overlay = FakeOverlayPermissionAccess(granted = true),
            voicePermission = FakeVoicePermissionAccess(granted = true),
            speech = FakeSpeechToTextAccess(available = true),
            quietMode = FakeQuietModeAccess(policyAccessGranted = true)
        )
        assertTrue(registry.canReadUsageStats())
        assertTrue(registry.canUseFloatingOverlay())
        assertTrue(registry.canUseVoiceInput())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        assertFalse(registry.canApplyQuietMode())
    }

    @Test
    fun settingsCopy_whenNotSupported_neverOffersSilenceToggle() {
        val denied = QuietModeProbe.settingsCopy(
            access = FakeQuietModeAccess(policyAccessGranted = false)
        )
        assertEquals(QuietModeProbe.SETTINGS_SECTION_TITLE, denied.sectionTitle)
        assertEquals("Not supported · quiet_mode", denied.statusLabel)
        assertFalse(denied.showSilenceToggle)
        assertTrue(denied.canOpenDndSettings)
        assertEquals(QuietModeProbe.DND_SETTINGS_ACTION, denied.dndSettingsAction)
        assertTrue(denied.body.contains("not supported", ignoreCase = true))
        assertTrue(denied.body.contains("silence", ignoreCase = true))
        assertTrue(denied.body.contains("Do Not Disturb", ignoreCase = true))
        assertFalse(denied.body.contains("silence applied", ignoreCase = true) &&
            denied.body.contains("was applied", ignoreCase = true))

        val granted = QuietModeProbe.settingsCopy(
            access = FakeQuietModeAccess(policyAccessGranted = true)
        )
        assertFalse(granted.showSilenceToggle)
        assertTrue(granted.statusLabel.contains("Not supported", ignoreCase = true))
        assertTrue(
            granted.body.contains("policy access is granted", ignoreCase = true)
        )
        assertTrue(granted.body.contains("does not mean INTENT can silence", ignoreCase = true))
        assertTrue(granted.body.contains("no Settings silence toggle", ignoreCase = true))
    }

    @Test
    fun settingsCopy_usesLiveRegistryLimitNotes() {
        val live = QuietModeProbe.liveRegistry(
            FakeQuietModeAccess(policyAccessGranted = true)
        )
        val copy = QuietModeProbe.settingsCopy(
            access = FakeQuietModeAccess(policyAccessGranted = true),
            registry = live
        )
        assertTrue(live.isUnsupported(CapabilityRegistry.ID_QUIET_MODE))
        assertFalse(copy.showSilenceToggle)
        assertEquals("Not supported · quiet_mode", copy.statusLabel)
    }
}
