package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceInputProbeTest {

    @Test
    fun liveRegistry_permissionNeededWhenMicDenied() {
        val registry = VoiceInputProbe.liveRegistry(
            permission = FakeVoicePermissionAccess(granted = false),
            speech = FakeSpeechToTextAccess(available = true)
        )
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_VOICE_INPUT))
        assertFalse(registry.canUseVoiceInput())
        assertTrue(
            registry.get(CapabilityRegistry.ID_VOICE_INPUT)!!.limitNote
                .contains("chips", ignoreCase = true)
        )
    }

    @Test
    fun liveRegistry_unavailableWhenSttMissing() {
        val registry = VoiceInputProbe.liveRegistry(
            permission = FakeVoicePermissionAccess(granted = true),
            speech = FakeSpeechToTextAccess(available = false)
        )
        assertEquals(
            CapabilityStatus.TEMPORARILY_UNAVAILABLE,
            registry.get(CapabilityRegistry.ID_VOICE_INPUT)!!.status
        )
        assertFalse(registry.canUseVoiceInput())
    }

    @Test
    fun liveRegistry_availableWhenMicAndSttOk() {
        val registry = VoiceInputProbe.liveRegistry(
            permission = FakeVoicePermissionAccess(granted = true),
            speech = FakeSpeechToTextAccess(available = true)
        )
        assertTrue(registry.canUseVoiceInput())
        assertTrue(
            registry.get(CapabilityRegistry.ID_VOICE_INPUT)!!.limitNote
                .contains("No live LLM", ignoreCase = true)
        )
    }

    @Test
    fun observe_denied_offersTextFallback() {
        val obs = VoiceInputProbe.observe(
            FakeVoicePermissionAccess(false),
            FakeSpeechToTextAccess(true)
        )
        assertFalse(obs.canCapture)
        assertTrue(obs.canRequestPermission)
        assertTrue(obs.note.contains("chips", ignoreCase = true))
    }

    @Test
    fun platformCapabilityLive_chainsVoice() {
        val registry = PlatformCapabilityLive.registry(
            usage = FakeUsageStatsAccess(granted = true),
            overlay = FakeOverlayPermissionAccess(granted = true),
            voicePermission = FakeVoicePermissionAccess(granted = true),
            speech = FakeSpeechToTextAccess(available = true)
        )
        assertTrue(registry.canReadUsageStats())
        assertTrue(registry.canUseFloatingOverlay())
        assertTrue(registry.canUseVoiceInput())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_LIVE_LLM))
        assertFalse(registry.canUseLiveLlm())
    }
}
