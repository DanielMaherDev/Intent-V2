package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Stage 6 — screen-capture consent honesty (no OCR / lesson completion).
 */
class ScreenCaptureProbeTest {

    @Test
    fun liveRegistry_permissionNeededWhenNotAsked() {
        val access = FakeScreenCaptureAccess(
            available = true,
            consent = ScreenCaptureConsent.NOT_ASKED
        )
        val registry = ScreenCaptureProbe.liveRegistry(access)
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_SCREEN_CAPTURE))
        assertFalse(registry.canUseScreenCapture())
        assertTrue(
            registry.get(CapabilityRegistry.ID_SCREEN_CAPTURE)!!.limitNote
                .contains("lesson completion", ignoreCase = true)
        )
    }

    @Test
    fun liveRegistry_availableWhenGranted_noOcrClaim() {
        val access = FakeScreenCaptureAccess(
            available = true,
            consent = ScreenCaptureConsent.GRANTED
        )
        val registry = ScreenCaptureProbe.liveRegistry(access)
        assertTrue(registry.canUseScreenCapture())
        assertEquals(
            CapabilityStatus.AVAILABLE,
            registry.get(CapabilityRegistry.ID_SCREEN_CAPTURE)!!.status
        )
        val note = registry.get(CapabilityRegistry.ID_SCREEN_CAPTURE)!!.limitNote
        assertTrue(note.contains("OCR", ignoreCase = true))
        assertTrue(note.contains("never lesson", ignoreCase = true) ||
            note.contains("never lesson completion", ignoreCase = true) ||
            note.contains("lesson completion", ignoreCase = true))
        assertTrue(note.contains("continuous", ignoreCase = true))
    }

    @Test
    fun liveRegistry_denied_noSilentRetry() {
        val access = FakeScreenCaptureAccess(
            available = true,
            consent = ScreenCaptureConsent.DENIED
        )
        val registry = ScreenCaptureProbe.liveRegistry(access)
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_SCREEN_CAPTURE))
        assertTrue(
            registry.get(CapabilityRegistry.ID_SCREEN_CAPTURE)!!.limitNote
                .contains("no silent retry", ignoreCase = true)
        )
    }

    @Test
    fun liveRegistry_notSupportedWhenApiMissing() {
        val access = FakeScreenCaptureAccess(available = false)
        val registry = ScreenCaptureProbe.liveRegistry(access)
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_SCREEN_CAPTURE))
    }

    @Test
    fun observe_notAsked_offersConsent() {
        val obs = ScreenCaptureProbe.observeForSession(
            FakeScreenCaptureAccess(consent = ScreenCaptureConsent.NOT_ASKED)
        )
        assertTrue(obs.canRequestConsent)
        assertFalse(obs.consentGranted)
        assertTrue(obs.note.contains("system dialog", ignoreCase = true) ||
            obs.note.contains("consent", ignoreCase = true))
        assertTrue(obs.note.contains("OCR", ignoreCase = true) ||
            obs.note.contains("lesson", ignoreCase = true))
    }

    @Test
    fun observe_denied_blocksRetry() {
        val obs = ScreenCaptureProbe.observeForSession(
            FakeScreenCaptureAccess(consent = ScreenCaptureConsent.DENIED)
        )
        assertFalse(obs.canRequestConsent)
        assertTrue(obs.note.contains("no silent retry", ignoreCase = true))
    }

    @Test
    fun platformCapabilityLive_chainsScreenCapture() {
        val registry = PlatformCapabilityLive.registry(
            usage = FakeUsageStatsAccess(granted = true),
            overlay = FakeOverlayPermissionAccess(granted = true),
            voicePermission = FakeVoicePermissionAccess(granted = true),
            speech = FakeSpeechToTextAccess(available = true),
            screenCapture = FakeScreenCaptureAccess(
                available = true,
                consent = ScreenCaptureConsent.GRANTED
            )
        )
        assertTrue(registry.canReadUsageStats())
        assertTrue(registry.canUseFloatingOverlay())
        assertTrue(registry.canUseVoiceInput())
        assertTrue(registry.canUseScreenCapture())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
    }
}
