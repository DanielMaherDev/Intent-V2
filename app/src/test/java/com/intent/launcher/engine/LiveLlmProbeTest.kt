package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.ProvenanceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLlmProbeTest {

    @Test
    fun noKey_notSupported_localHeuristics() {
        val access = FakeLiveLlmAccess()
        val registry = LiveLlmProbe.liveRegistry(access)
        val entry = registry.get(CapabilityRegistry.ID_LIVE_LLM)!!
        assertEquals(CapabilityStatus.NOT_SUPPORTED, entry.status)
        assertFalse(registry.canUseLiveLlm())
        assertTrue(entry.limitNote.contains("local heuristics", ignoreCase = true))
        assertTrue(entry.limitNote.contains("OBSERVED", ignoreCase = true))
        assertTrue(entry.limitNote.contains("auto-Complete", ignoreCase = true))

        val obs = LiveLlmProbe.observe(access)
        assertEquals(LiveLlmProbe.LOCAL_HEURISTIC_LABEL, obs.sourceLabel)
        assertFalse(obs.isLive)
    }

    @Test
    fun keyWithoutOptIn_denied() {
        val access = FakeLiveLlmAccess(keyConfigured = true, optedIn = false)
        val registry = LiveLlmProbe.liveRegistry(access)
        assertEquals(
            CapabilityStatus.DENIED,
            registry.get(CapabilityRegistry.ID_LIVE_LLM)!!.status
        )
        assertTrue(registry.isDenied(CapabilityRegistry.ID_LIVE_LLM))
        assertFalse(registry.canUseLiveLlm())
        assertEquals(
            LiveLlmProbe.LOCAL_HEURISTIC_LABEL,
            LiveLlmProbe.discussSourceLabel(registry)
        )
    }

    @Test
    fun keyAndOptInWithoutClient_temporarilyUnavailable_notLive() {
        val access = FakeLiveLlmAccess(
            keyConfigured = true,
            optedIn = true,
            clientEnabled = false
        )
        val registry = LiveLlmProbe.liveRegistry(access)
        assertEquals(
            CapabilityStatus.TEMPORARILY_UNAVAILABLE,
            registry.get(CapabilityRegistry.ID_LIVE_LLM)!!.status
        )
        assertFalse(registry.canUseLiveLlm())
        assertTrue(
            registry.get(CapabilityRegistry.ID_LIVE_LLM)!!.limitNote
                .contains("not claiming LIVE", ignoreCase = true)
        )
    }

    @Test
    fun keyOptInAndClient_connectedLive() {
        val access = FakeLiveLlmAccess(
            keyConfigured = true,
            optedIn = true,
            clientEnabled = true
        )
        val registry = LiveLlmProbe.liveRegistry(access)
        assertTrue(registry.canUseLiveLlm())
        assertEquals(
            CapabilityStatus.CONNECTED,
            registry.get(CapabilityRegistry.ID_LIVE_LLM)!!.status
        )
        assertEquals(
            LiveLlmProbe.MODEL_SUGGESTION_LABEL,
            LiveLlmProbe.discussSourceLabel(registry)
        )
        val note = registry.get(CapabilityRegistry.ID_LIVE_LLM)!!.limitNote
        assertTrue(note.contains("OBSERVED", ignoreCase = true))
        assertTrue(note.contains("auto-Complete", ignoreCase = true))
    }

    @Test
    fun platformCapabilityLive_chainsLiveLlmAsNotSupportedByDefault() {
        val registry = PlatformCapabilityLive.registry(
            usage = FakeUsageStatsAccess(granted = true),
            overlay = FakeOverlayPermissionAccess(granted = true),
            voicePermission = FakeVoicePermissionAccess(granted = true),
            speech = FakeSpeechToTextAccess(available = true),
            liveLlm = FakeLiveLlmAccess()
        )
        assertTrue(registry.canUseVoiceInput())
        assertFalse(registry.canUseLiveLlm())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_LIVE_LLM))
    }

    @Test
    fun guard_neverAllowsObservedOrAutoComplete() {
        assertFalse(LiveLlmDiscussGuard.mayWriteObservedFromModel())
        assertFalse(LiveLlmDiscussGuard.mayAutoCompleteSession())
        assertEquals(ProvenanceKind.SAID, LiveLlmDiscussGuard.allowedConfirmedProvenance)
        assertFalse(
            LiveLlmDiscussGuard.assertSafeModelSideEffect(
                writesObserved = true,
                autoCompletes = false
            )
        )
        assertFalse(
            LiveLlmDiscussGuard.assertSafeModelSideEffect(
                writesObserved = false,
                autoCompletes = true
            )
        )
        assertTrue(
            LiveLlmDiscussGuard.assertSafeModelSideEffect(
                writesObserved = false,
                autoCompletes = false
            )
        )
    }
}
