package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.ApproachDraft
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlockType
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
import java.util.TimeZone

/**
 * Gate E3 — Approve receipt + Undo for reversible activation (JVM, no live OpenAI / AVD).
 *
 * Proves: gateway Approve writes a receipt; Undo reverses activation so Home/check-in
 * is not left active wrongly; no invented lesson completion.
 */
class GateE3ApproveUndoReceiptTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val registry = CapabilityRegistry.default
    private val tz = TimeZone.getTimeZone("UTC")
    private val seedAt = 1_700_049_600_000L
    /** Thursday 30 Nov 2023 09:00 UTC — Spanish tutor day, morning check-in window. */
    private val thu0900 = 1_700_125_200_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("gate-e3-flow.json"))
    }

    @Test
    fun gatewayApprove_writesReceipt_lastUndoableIsApprove_notStolenByReceiptTurn() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1, registry)

        val result = CommandGateway.approveProposal(
            store = store,
            turnId = "turn-e3-approve",
            commandId = "cmd-e3-approve",
            sessionId = SpanishScenario.SESSION_ID,
            nowEpochMs = seedAt + 10,
            utterance = "Yes",
            explicitProposalId = SpanishScenario.PROPOSAL_ID
        )
        assertEquals(GatewayStatus.APPLIED, result.status)

        val snap = store.snapshot()
        assertEquals(ProposalStatus.APPROVED, snap.proposals.first().status)
        assertEquals(1, snap.sessions.size)
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first().phase)

        val receipt = ApproveReceipt.findBlock(snap, SpanishScenario.PROPOSAL_ID)
        assertNotNull(receipt)
        assertEquals(ResponseBlockType.RECORDED_ENTRY, receipt!!.type)
        assertEquals(ApproveReceipt.SOURCE_LABEL, receipt.sourceLabel)
        assertTrue(receipt.actions.any { it.equals("Undo", ignoreCase = true) })
        assertTrue(
            snap.turns.any {
                it.id == ApproveReceipt.turnId(seedAt + 10) &&
                    it.text.contains("Activated", ignoreCase = true) &&
                    it.text.contains("Undo", ignoreCase = true)
            }
        )

        val undoable = CommandReversibility.lastUndoable(snap.transactions)
        assertNotNull(undoable)
        assertEquals("ApproveProposal", undoable!!.commandKind)
        assertTrue(undoable.summary.contains("Approved", ignoreCase = true))
        // Receipt must not be a separate reversible turn that steals Undo.
        assertFalse(undoable.commandKind == "AddConversationTurn")
        assertFalse(
            snap.transactions.any {
                it.commandKind == "AddConversationTurn" &&
                    it.summary.contains(ApproveReceipt.turnId(seedAt + 10))
            }
        )
    }

    @Test
    fun undo_reversesActivation_homeCheckInNotLeftActive_noInventedLesson() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1, registry)

        CommandGateway.approveProposal(
            store = store,
            turnId = "turn-e3-approve-2",
            commandId = "cmd-e3-approve-2",
            sessionId = SpanishScenario.SESSION_ID,
            nowEpochMs = seedAt + 20,
            utterance = "Yes",
            explicitProposalId = SpanishScenario.PROPOSAL_ID
        )

        var snap = store.snapshot()
        assertTrue(ApproveReceipt.hasReceipt(snap, SpanishScenario.PROPOSAL_ID))
        assertTrue(DailyCycle.shouldOfferCheckIn(snap, SpanishScenario.INTENTION_ID, thu0900, tz))
        val nextAfterApprove = NextActionEngine.recommend(snap, thu0900, tz, registry)
        assertEquals(NextActionKind.CHECK_IN, nextAfterApprove.kind)
        assertTrue(nextAfterApprove.label.contains("Morning check-in", ignoreCase = true))

        // Honesty: Approve must not invent Duolingo / lesson completion.
        assertTrue(
            snap.trackerEntries.none {
                it.valueText.contains("lesson", ignoreCase = true) ||
                    it.valueText.contains("completed", ignoreCase = true)
            }
        )
        assertTrue(
            snap.turns.none {
                it.text.contains("lesson was completed", ignoreCase = true) ||
                    it.text.contains("lesson completion", ignoreCase = true) &&
                    !it.text.contains("requires", ignoreCase = true)
            }
        )
        assertNull(
            snap.facts.firstOrNull {
                it.text.contains("completed a lesson", ignoreCase = true)
            }
        )

        store.apply(FlowCommand.UndoLastReversible("undo-e3-1", seedAt + 30))
        snap = store.snapshot()

        assertEquals(ProposalStatus.PENDING, snap.proposals.first().status)
        assertTrue(snap.sessions.isEmpty())
        assertEquals(IntentionPhase.PROPOSAL_PENDING, snap.intentions.first().phase)
        assertFalse(ApproveReceipt.hasReceipt(snap, SpanishScenario.PROPOSAL_ID))
        assertFalse(DailyCycle.shouldOfferCheckIn(snap, SpanishScenario.INTENTION_ID, thu0900, tz))
        assertFalse(DailyCycle.hasActiveScheduledFocus(snap, SpanishScenario.INTENTION_ID))

        val nextAfterUndo = NextActionEngine.recommend(snap, thu0900, tz, registry)
        assertTrue(
            "After Undo, Home must not keep Morning check-in active; got ${nextAfterUndo.kind}",
            nextAfterUndo.kind != NextActionKind.CHECK_IN &&
                nextAfterUndo.kind != NextActionKind.BEGIN
        )
        assertTrue(snap.transactions.any { it.undoOfTransactionId != null })
    }

    @Test
    fun adaptivePlanCompiler_approveWithMorningCheckIn_receiptThenUndoClearsHome() {
        val now = seedAt
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        val abTz = AdaptiveBuilderFixtures.utc()
        fun rev(): Long =
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, abTz)!!.revision

        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.OfferApproaches(
                intentionId = id,
                expectedRevision = rev(),
                approachDrafts = listOf(
                    ApproachDraft(
                        approachId = "approach-learning-app",
                        title = "Duolingo",
                        summary = "App path",
                        capabilityNote =
                            "Can open Duolingo; lesson completion requires your check-in."
                    )
                )
            ),
            now,
            timeZone = abTz
        )
        AdaptiveBuilderEngine.selectApproaches(
            store, id, listOf("approach-learning-app"), now + 1, "turn-sel", rev(), "TEXT_CHIP"
        )
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "f-freq-e3",
                    text = "Everyday",
                    key = AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY
                ),
                sourceTurnId = "t-freq-e3"
            ),
            now + 2,
            timeZone = abTz
        )
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "f-dur-e3",
                    text = "20 minutes",
                    key = AdaptiveBuilderEngine.KEY_PRACTICE_DURATION,
                    normalizedValue = "20"
                ),
                sourceTurnId = "t-dur-e3"
            ),
            now + 3,
            timeZone = abTz
        )

        val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 4, abTz)!!
        val draft = PlanCompiler.compile(ctx, morningCheckIn = true)
        assertNotNull(draft)
        assertTrue(draft!!.includeMorningCheckIn)
        assertTrue(draft.capabilityHonesty.contains("lesson completion", ignoreCase = true))
        assertTrue(draft.capabilityHonesty.contains("check-in", ignoreCase = true))
        assertFalse(draft.capabilityHonesty.contains("lesson was completed", ignoreCase = true))

        val propose = PlanCompiler.toProposeDecision(draft, id, ctx.revision)
        val applied = AdaptiveBuilderEngine.applyDecision(store, propose, now + 5, timeZone = abTz)
        assertTrue(applied.reason, applied.accepted)
        assertTrue(store.snapshot().sessions.isEmpty())

        val proposalId = draft.proposalId
        val sessionId = "session-$proposalId"
        val approveAt = now + 10
        val gateway = CommandGateway.approveProposal(
            store = store,
            turnId = "turn-e3-ab-approve",
            commandId = "cmd-e3-ab-approve",
            sessionId = sessionId,
            nowEpochMs = approveAt,
            utterance = "Yes",
            explicitProposalId = proposalId
        )
        assertEquals(GatewayStatus.APPLIED, gateway.status)

        var snap = store.snapshot()
        assertEquals(1, snap.sessions.size)
        assertTrue(ApproveReceipt.hasReceipt(snap, proposalId))
        assertEquals(
            ProposalStatus.APPROVED,
            snap.proposals.first { it.id == proposalId }.status
        )
        assertTrue(snap.proposals.first { it.id == proposalId }.includeMorningCheckIn)
        assertTrue(
            snap.approaches.any {
                it.id == "approach-learning-app" && it.status == ApproachStatus.SELECTED
            }
        )
        // Still no fabricated lesson rows after Approve.
        assertTrue(snap.trackerEntries.isEmpty())

        // Morning window + ACTIVE session → check-in eligible; Undo must clear it.
        assertTrue(DailyCycle.shouldOfferCheckIn(snap, id, thu0900, tz))
        store.apply(FlowCommand.UndoLastReversible("undo-e3-ab", now + 20))
        snap = store.snapshot()
        assertTrue(snap.sessions.isEmpty())
        assertEquals(ProposalStatus.PENDING, snap.proposals.first { it.id == proposalId }.status)
        assertFalse(ApproveReceipt.hasReceipt(snap, proposalId))
        assertFalse(DailyCycle.shouldOfferCheckIn(snap, id, thu0900, tz))
        assertFalse(DailyCycle.hasActiveScheduledFocus(snap, id))
    }
}
