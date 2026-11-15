package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.AppUseReductionScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Gate D2 — OBSERVED vs INFERRED on every app-usage intervention surface.
 * Never silent mix; Home / floater / Conversation share labeled PromptView.
 */
class GateDObservedInferredLabelsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    // Monday 13 Nov 2023 15:00 UTC — outside default quiet hours
    private val now = 1_699_887_600_000L
    private val seedAt = now - 60_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("gate-d2.json"))
    }

    private fun observeRegistry(access: UsageStatsAccess): CapabilityRegistry =
        UsageStatsProbe.liveRegistry(access)

    private fun overlayRegistry(base: CapabilityRegistry): CapabilityRegistry {
        val entries = CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
            val fromBase = base.get(entry.id)
            when {
                entry.id == CapabilityRegistry.ID_FLOATING_OVERLAY ->
                    entry.copy(status = CapabilityStatus.AVAILABLE, limitNote = "ok")
                fromBase != null -> fromBase
                else -> entry
            }
        }
        return CapabilityRegistry(entries)
    }

    private fun queueObservedUsage(): InterventionProvenance.PromptView {
        AppUseReductionScenario.seed(store, seedAt)
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(
                SessionDestinations.INSTAGRAM_PACKAGE to 50L * 60_000L
            )
        )
        val queued = UsageInterventionTrigger.maybeQueueFromDayUsage(
            store = store,
            access = access,
            nowEpochMs = now,
            registry = observeRegistry(access),
            timeZone = tz
        ).queued
        assertNotNull(queued)
        return InterventionProvenance.fromQueued(queued!!)
    }

    @Test
    fun usageIntervention_promptView_isAlwaysObservedLabeled() {
        val view = queueObservedUsage()
        assertEquals(ProvenanceKind.OBSERVED, view.provenanceKind)
        assertEquals("OBSERVED", view.provenanceLabel)
        assertTrue(view.sourceDetail.startsWith("OBSERVED"))
        assertTrue(view.honestyNote.contains("OBSERVED"))
        assertFalse(view.provenanceLabel.isBlank())
        assertEquals(
            "OBSERVED · Needs you",
            InterventionProvenance.floaterAttentionLine(view)
        )
        assertTrue(InterventionProvenance.homeTitle(view).startsWith("OBSERVED"))
    }

    @Test
    fun conversationTurn_questionBlock_carriesObservedSourceLabel() {
        val view = queueObservedUsage()
        val turn = store.snapshot().turns.firstOrNull { it.id == view.turnId }
        assertNotNull(turn)
        val question = turn!!.blocks.firstOrNull { it.type == ResponseBlockType.QUESTION }
        assertNotNull(question)
        assertTrue(question!!.sourceLabel.startsWith("OBSERVED"))
        assertTrue(question.active)
        // Chip helper used by Conversation AgentBubble — never silent mix.
        val chip = question.sourceLabel.uppercase()
        assertTrue(chip.startsWith("OBSERVED") || chip.contains("OBSERVED"))
    }

    @Test
    fun floaterPresence_exposesObservedProvenanceLabel() {
        val view = queueObservedUsage()
        val access = FakeUsageStatsAccess(granted = true, foregroundByPackage = emptyMap())
        val registry = overlayRegistry(observeRegistry(access))
        val next = NextAction(
            kind = NextActionKind.DISCUSS,
            label = "Discuss",
            detail = "Queued usage check-in",
            intentionId = AppUseReductionScenario.INTENTION_ID
        )
        val presence = FloatingCompanion.presence(
            nextAction = next,
            overlayGranted = true,
            registry = registry,
            snapshot = store.snapshot()
        )
        assertEquals(FloatingAgentState.ATTENTION_NEEDED, presence.state)
        assertEquals("OBSERVED", presence.interventionProvenanceLabel)
        assertEquals(
            InterventionProvenance.floaterAttentionLine(view),
            presence.interventionAttentionLine
        )
        assertTrue(presence.label.contains("OBSERVED"))
        assertEquals(view.jobId, presence.interventionJobId)
        assertEquals(view.turnId, presence.interventionTurnId)
    }

    @Test
    fun floaterPresence_overlayDenied_stillExposesProvenanceForHomeFallback() {
        queueObservedUsage()
        val access = FakeUsageStatsAccess(granted = true, foregroundByPackage = emptyMap())
        val next = NextAction(
            kind = NextActionKind.DISCUSS,
            label = "Discuss",
            detail = "Queued",
            intentionId = AppUseReductionScenario.INTENTION_ID
        )
        val presence = FloatingCompanion.presence(
            nextAction = next,
            overlayGranted = false,
            registry = observeRegistry(access),
            snapshot = store.snapshot()
        )
        assertEquals(null, presence.state)
        assertEquals("OBSERVED", presence.interventionProvenanceLabel)
        assertNotNull(presence.interventionAttentionLine)
        assertTrue(presence.interventionAttentionLine!!.contains("OBSERVED"))
    }

    @Test
    fun inferredIntervention_neverSilentMix_onAllSurfaces() {
        AppUseReductionScenario.seed(store, seedAt)
        val jobId = "job-inferred-d2"
        val turnId = "turn-inferred-d2"
        val prompt = "Based on your recent pattern, want a shorter session window?"
        val block = InterventionProvenance.labeledQuestionBlock(
            blockId = "block-inferred-d2",
            title = "Pattern check-in",
            body = prompt,
            provenanceKind = ProvenanceKind.INFERRED,
            sourceHint = "hypothesis",
            linkedObjectIds = listOf(AppUseReductionScenario.INTENTION_ID),
            actions = listOf("review", "dismiss")
        )
        store.apply(
            FlowCommand.QueueIntervention(
                jobId = jobId,
                turnId = turnId,
                intentionId = AppUseReductionScenario.INTENTION_ID,
                triggerKey = "inferred_pattern:${AppUseReductionScenario.INTENTION_ID}",
                kind = "inferred_pattern",
                promptText = prompt,
                nowEpochMs = now,
                provenanceKind = ProvenanceKind.INFERRED,
                agentTurnText = prompt,
                agentTurnBlocks = listOf(block)
            )
        )
        val view = InterventionProvenance.activePrompt(store.snapshot())
        assertNotNull(view)
        assertEquals(ProvenanceKind.INFERRED, view!!.provenanceKind)
        assertEquals("INFERRED", view.provenanceLabel)
        assertTrue(view.sourceDetail.startsWith("INFERRED"))
        assertTrue(view.honestyNote.contains("INFERRED"))
        assertFalse(view.honestyNote.contains("OBSERVED package"))

        val access = FakeUsageStatsAccess(granted = true, foregroundByPackage = emptyMap())
        val presence = FloatingCompanion.presence(
            nextAction = NextAction(
                kind = NextActionKind.DISCUSS,
                label = "Discuss",
                detail = "Inferred",
                intentionId = AppUseReductionScenario.INTENTION_ID
            ),
            overlayGranted = true,
            registry = overlayRegistry(observeRegistry(access)),
            snapshot = store.snapshot()
        )
        assertEquals("INFERRED", presence.interventionProvenanceLabel)
        assertTrue(presence.label.contains("INFERRED"))

        val turn = store.snapshot().turns.first { it.id == turnId }
        val question = turn.blocks.first { it.type == ResponseBlockType.QUESTION }
        assertTrue(question.sourceLabel.startsWith("INFERRED"))
        assertEquals(
            ProgressProof.provenanceLabel(ProvenanceKind.INFERRED),
            view.provenanceLabel
        )
    }

    @Test
    fun saidKind_mapsToInferred_neverClaimsObserved() {
        val label = InterventionProvenance.provenanceLabel(ProvenanceKind.SAID)
        assertEquals("INFERRED", label)
        assertEquals(ProvenanceKind.INFERRED, InterventionProvenance.interventionKind(ProvenanceKind.SAID))
        val detail = InterventionProvenance.sourceDetail(ProvenanceKind.SAID, "user")
        assertTrue(detail.startsWith("INFERRED"))
        assertFalse(detail.contains("OBSERVED"))
    }

    @Test
    fun labeledQuestionBlock_prefixesProvenanceOnSourceLabel() {
        val observed = InterventionProvenance.labeledQuestionBlock(
            blockId = "b1",
            title = "Usage",
            body = "q",
            provenanceKind = ProvenanceKind.OBSERVED,
            sourceHint = "usage_stats"
        )
        assertEquals(ResponseBlockType.QUESTION, observed.type)
        assertEquals("OBSERVED · usage_stats", observed.sourceLabel)

        val inferred = InterventionProvenance.labeledQuestionBlock(
            blockId = "b2",
            title = "Pattern",
            body = "q",
            provenanceKind = ProvenanceKind.INFERRED,
            sourceHint = "hypothesis"
        )
        assertEquals("INFERRED · hypothesis", inferred.sourceLabel)
    }

    @Test
    fun talkBack_floaterBubble_speaksProvenance() {
        val spoken = TalkBackLabels.floaterBubble(
            state = FloatingAgentState.ATTENTION_NEEDED,
            spokenLabel = "OBSERVED · Needs you",
            interventionProvenanceLabel = "OBSERVED"
        )
        assertTrue(spoken.contains("OBSERVED"))
        assertTrue(spoken.contains("Needs you") || spoken.contains("OBSERVED · Needs you"))
    }
}
