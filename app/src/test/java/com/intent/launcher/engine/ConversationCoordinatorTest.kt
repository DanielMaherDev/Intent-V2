package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Voice and text share ConversationCoordinator — chip match or honest text fallback.
 */
class ConversationCoordinatorTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
        SpanishScenario.seed(store, now)
    }

    private fun voiceReadyRegistry(): CapabilityRegistry {
        val entries = CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
            if (entry.id == CapabilityRegistry.ID_VOICE_INPUT) {
                entry.copy(
                    status = CapabilityStatus.AVAILABLE,
                    limitNote = "ok"
                )
            } else {
                entry
            }
        }
        return CapabilityRegistry(entries)
    }

    @Test
    fun submitChip_runsSpanishScript_sameAsDirectOnChip() {
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val registry = CapabilityRegistry.default
        val result = ConversationCoordinator.submitChip(
            store,
            intention,
            "I meet a tutor on Thursdays at six",
            now + 10,
            registry
        )
        val snap = store.snapshot()
        assertEquals(1, snap.proposals.size)
        assertEquals(ProposalStatus.PENDING, snap.proposals.first().status)
        assertEquals(LiveLlmProbe.LOCAL_HEURISTIC_LABEL, result.sourceLabel)
        assertNotNull(result.honestyNote)
        assertTrue(result.honestyNote!!.contains("local heuristics", ignoreCase = true))
    }

    @Test
    fun submitVoice_matchesChip_sameCommandPath() {
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val registry = voiceReadyRegistry()
        val chips = SpanishScenario.conversationChips(registry)
        val result = ConversationCoordinator.submitVoiceTranscript(
            store = store,
            intention = intention,
            availableChips = chips,
            transcript = "I meet a tutor on Thursdays at six",
            nowEpochMs = now + 20,
            registry = registry
        )
        assertEquals(ConversationCoordinator.Channel.VOICE_TRANSCRIPT, result.channel)
        assertEquals("I meet a tutor on Thursdays at six", result.appliedChip)
        assertNotNull(result.honestyNote)
        assertTrue(result.honestyNote!!.contains("no live LLM", ignoreCase = true))
        assertEquals(LiveLlmProbe.LOCAL_HEURISTIC_LABEL, result.sourceLabel)
        assertEquals(1, store.snapshot().proposals.size)
    }

    @Test
    fun submitVoice_unmatched_storesTextWithoutInventingLlm() {
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val registry = voiceReadyRegistry()
        val chips = SpanishScenario.conversationChips(registry)
        val beforeTurns = store.snapshot().turns.size
        val result = ConversationCoordinator.submitVoiceTranscript(
            store = store,
            intention = intention,
            availableChips = chips,
            transcript = "random free speech about weather",
            nowEpochMs = now + 30,
            registry = registry
        )
        assertNull(result.appliedChip)
        assertEquals("random free speech about weather", result.storedTranscript)
        assertTrue(result.honestyNote!!.contains("no LLM", ignoreCase = true))
        assertEquals(LiveLlmProbe.LOCAL_HEURISTIC_LABEL, result.sourceLabel)
        assertEquals(0, store.snapshot().proposals.size)
        assertTrue(store.snapshot().turns.size >= beforeTurns + 2)
        assertTrue(
            store.snapshot().turns.any {
                it.role == "agent" && it.text.contains("local heuristics", ignoreCase = true)
            }
        )
    }

    @Test
    fun submitVoice_deniedRegistry_doesNotMutate_offersTextFallback() {
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val locked = CapabilityRegistry.default // voice_input is PERMISSION_NEEDED
        assertFalse(locked.canUseVoiceInput())
        val before = store.snapshot().turns.size
        val result = ConversationCoordinator.submitVoiceTranscript(
            store = store,
            intention = intention,
            availableChips = SpanishScenario.conversationChips(locked),
            transcript = "I meet a tutor on Thursdays at six",
            nowEpochMs = now + 40,
            registry = locked
        )
        assertNull(result.appliedChip)
        assertNull(result.storedTranscript)
        assertNotNull(result.honestyNote)
        assertTrue(result.honestyNote!!.contains("chip", ignoreCase = true))
        assertEquals(before, store.snapshot().turns.size)
        assertEquals(0, store.snapshot().proposals.size)
    }

    @Test
    fun matchChip_fuzzyContains() {
        val chips = listOf("Everyday conversations", "I meet a Spanish tutor")
        assertEquals(
            "Everyday conversations",
            ConversationCoordinator.matchChip("I'd like everyday conversations please", chips)
        )
        assertNull(ConversationCoordinator.matchChip("hello world", chips))
    }
}
