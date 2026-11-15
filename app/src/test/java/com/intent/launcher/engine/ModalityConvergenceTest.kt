package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Gate B: chip / voice / free-text that match a Spanish chip converge on one
 * fact + proposal history (same FlowCommand path via ConversationCoordinator).
 */
class ModalityConvergenceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val now = 1_700_000_300_000L
    private val tutorChip = "I meet a tutor on Thursdays at six"

    private fun voiceReadyRegistry(): CapabilityRegistry {
        val entries = CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
            if (entry.id == CapabilityRegistry.ID_VOICE_INPUT) {
                entry.copy(status = CapabilityStatus.AVAILABLE, limitNote = "ok")
            } else {
                entry
            }
        }
        return CapabilityRegistry(entries)
    }

    private fun domainFingerprint(store: IntentionFlowStore): Triple<String, String, IntentionPhase> {
        val snap = store.snapshot()
        val fact = snap.facts.first { it.id == SpanishScenario.FACT_TUTOR_ID }
        val proposal = snap.proposals.first { it.id == SpanishScenario.PROPOSAL_ID }
        assertEquals(ProvenanceKind.SAID, fact.provenance.kind)
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertEquals(4, proposal.proposedDayOfWeek)
        assertEquals(18, proposal.proposedHour)
        assertEquals(listOf(SpanishScenario.FACT_TUTOR_ID), proposal.basedOnFactIds)
        return Triple(fact.text, proposal.summary, snap.intentions.first().phase)
    }

    @Test
    fun chipVoiceFreeText_sameTutorFactAndWeeklyProposal() {
        val registry = voiceReadyRegistry()
        val chips = SpanishScenario.conversationChips(registry)

        val chipStore = IntentionFlowStore(tmp.newFile("chip.json"))
        SpanishScenario.seed(chipStore, now)
        val chipIntention = chipStore.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val chipResult = ConversationCoordinator.submitChip(
            chipStore, chipIntention, tutorChip, now + 10, registry
        )
        assertEquals(ConversationCoordinator.Channel.TEXT_CHIP, chipResult.channel)
        assertEquals(tutorChip, chipResult.appliedChip)
        val chipFp = domainFingerprint(chipStore)
        assertTrue(
            chipStore.snapshot().turns.any {
                it.role == "user" && it.modality == ConversationCoordinator.Channel.TEXT_CHIP.name
            }
        )

        val voiceStore = IntentionFlowStore(tmp.newFile("voice.json"))
        SpanishScenario.seed(voiceStore, now)
        val voiceIntention = voiceStore.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val voiceResult = ConversationCoordinator.submitVoiceTranscript(
            store = voiceStore,
            intention = voiceIntention,
            availableChips = chips,
            transcript = tutorChip,
            nowEpochMs = now + 20,
            registry = registry
        )
        assertEquals(ConversationCoordinator.Channel.VOICE_TRANSCRIPT, voiceResult.channel)
        assertEquals(tutorChip, voiceResult.appliedChip)
        val voiceFp = domainFingerprint(voiceStore)
        assertTrue(
            voiceStore.snapshot().turns.any {
                it.role == "user" && it.modality == ConversationCoordinator.Channel.VOICE_TRANSCRIPT.name
            }
        )

        val textStore = IntentionFlowStore(tmp.newFile("text.json"))
        SpanishScenario.seed(textStore, now)
        val textIntention = textStore.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val textResult = ConversationCoordinator.submitFreeText(
            store = textStore,
            intention = textIntention,
            text = tutorChip,
            nowEpochMs = now + 30,
            registry = registry,
            availableChips = chips
        )
        assertEquals(ConversationCoordinator.Channel.FREE_TEXT, textResult.channel)
        assertEquals(tutorChip, textResult.appliedChip)
        assertNotNull(textResult.honestyNote)
        val textFp = domainFingerprint(textStore)
        assertTrue(
            textStore.snapshot().turns.any {
                it.role == "user" && it.modality == ConversationCoordinator.Channel.FREE_TEXT.name
            }
        )

        assertEquals(chipFp, voiceFp)
        assertEquals(chipFp, textFp)
        assertEquals(IntentionPhase.PROPOSAL_PENDING, chipFp.third)
    }
}
