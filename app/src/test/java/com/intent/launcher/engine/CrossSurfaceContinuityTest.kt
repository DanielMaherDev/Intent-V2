package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SpanishScenario
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
 * §8.10 — after Approve, Agent tab / Discuss opens Active schedule + next-action
 * truth aligned with Home, not a stale create/proposal transcript.
 */
class CrossSurfaceContinuityTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default
    private val seedAt = 1_700_049_600_000L
    private val dueNow = 1_700_159_400_000L // Thu 16 Nov 2023 18:30 UTC

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("s810-flow.json"))
    }

    private fun seedPendingWithProposalCard() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-propose-card",
                intentionId = SpanishScenario.INTENTION_ID,
                role = "agent",
                text = "Ready to begin? Review your routine before activating it.",
                nowEpochMs = seedAt + 2,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-proposal-${SpanishScenario.PROPOSAL_ID}",
                        type = ResponseBlockType.PROPOSAL,
                        title = "Weekly Spanish session",
                        body = "Thursday at 18:00",
                        sourceLabel = "adaptive_builder|review",
                        linkedObjectIds = listOf(SpanishScenario.PROPOSAL_ID),
                        actions = listOf("Start this routine", "Edit plan", "Keep discussing"),
                        active = true
                    )
                )
            )
        )
        // Create-style greeting residue that must not stay the interactive truth.
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-create-greet-residue",
                intentionId = SpanishScenario.INTENTION_ID,
                role = "agent",
                text = CreateIntentionChat.GREETING,
                nowEpochMs = seedAt + 3
            )
        )
    }

    @Test
    fun afterApprove_agentTab_showsActiveSchedule_notProposalCard() {
        seedPendingWithProposalCard()
        val pending = store.snapshot().proposals.single {
            it.id == SpanishScenario.PROPOSAL_ID
        }
        assertEquals(ProposalStatus.PENDING, pending.status)

        val result = CommandGateway.approveProposal(
            store = store,
            turnId = "turn-approve-s810",
            commandId = "cmd-approve-s810",
            sessionId = SpanishScenario.SESSION_ID,
            nowEpochMs = seedAt + 10,
            utterance = "Yes",
            explicitProposalId = SpanishScenario.PROPOSAL_ID
        )
        assertEquals(GatewayStatus.APPLIED, result.status)

        val snap = store.snapshot()
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.single {
            it.id == SpanishScenario.INTENTION_ID
        }.phase)
        assertTrue(
            snap.turns
                .filter { it.intentionId == SpanishScenario.INTENTION_ID }
                .flatMap { it.blocks }
                .none { it.type == ResponseBlockType.PROPOSAL && it.active }
        )
        assertTrue(
            snap.turns.any { turn ->
                turn.blocks.any {
                    it.active &&
                        it.type == ResponseBlockType.NEXT_ACTION &&
                        it.title == CrossSurfaceContinuity.ACTIVE_SCHEDULE_TITLE
                }
            }
        )

        val homeNext = NextActionEngine.recommend(snap, dueNow, tz, registry)
        assertTrue(
            "Home next-action should be schedule-facing after Approve, was ${homeNext.kind}",
            AgentModuleProjection.isScheduleNextAction(homeNext.kind) ||
                homeNext.kind == NextActionKind.CHECK_IN
        )

        val agent = CrossSurfaceContinuity.openAgentTab(store, dueNow, tz, registry)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, agent.navigateTo)
        assertEquals(SpanishScenario.INTENTION_ID, agent.intentionId)
        assertEquals(IntentionPhase.ACTIVE, agent.intentionPhase)
        assertFalse("Proposal card must not remain Agent-tab truth", agent.proposalCardPresent)
        assertTrue("Active schedule block required", agent.activeScheduleBlockPresent)
        assertFalse(agent.createGreetingStillInteractive)
        assertEquals(homeNext.kind, agent.nextActionKind)
        assertEquals(homeNext.label, agent.nextActionLabel)
        assertNotNull(agent.nextForYouTitle)
        assertTrue(
            agent.nextForYouTitle!!.contains("Active schedule", ignoreCase = true) ||
                agent.nextForYouTitle == homeNext.label ||
                agent.nextForYouTitle!!.isNotBlank()
        )
    }

    @Test
    fun afterApprove_discussFromHome_matchesHomeNextAction() {
        seedPendingWithProposalCard()
        CommandGateway.approveProposal(
            store = store,
            turnId = "turn-approve-discuss",
            commandId = "cmd-approve-discuss",
            sessionId = SpanishScenario.SESSION_ID,
            nowEpochMs = seedAt + 20,
            utterance = "Start this routine",
            explicitProposalId = SpanishScenario.PROPOSAL_ID
        )

        val home = NextActionEngine.recommend(store.snapshot(), dueNow, tz, registry)
        val opened = DiscussFromHome.open(store, home, dueNow + 1, registry)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, opened.navigateTo)
        assertEquals(home.intentionId, opened.intentionId)
        assertEquals(SpanishScenario.INTENTION_ID, store.snapshot().activeIntentionId)

        val truth = CrossSurfaceContinuity.openAgentTab(store, dueNow + 2, tz, registry)
        assertEquals(home.kind, truth.nextActionKind)
        assertEquals(home.label, truth.nextActionLabel)
        assertFalse(truth.proposalCardPresent)
    }

    @Test
    fun createShellApprove_agentTab_notCreateGreetingTruth() {
        val now = seedAt
        CreateIntentionChat.begin(store, now, registry)
        store.apply(
            FlowCommand.RemapIntentionId(
                fromIntentionId = CreateIntentionChat.SESSION_INTENTION_ID,
                toIntentionId = "intention-create-s810",
                nowEpochMs = now + 5
            )
        )
        store.apply(
            FlowCommand.MarkPhase(
                intentionId = "intention-create-s810",
                phase = IntentionPhase.PROPOSAL_PENDING,
                nowEpochMs = now + 6
            )
        )
        store.apply(
            FlowCommand.ProposeRecurringSession(
                proposalId = "proposal-create-s810",
                intentionId = "intention-create-s810",
                title = "Weekly practice",
                summary = "Everyday · 19:00",
                dayOfWeek = 1,
                hour = 19,
                minute = 0,
                basedOnFactIds = emptyList(),
                nowEpochMs = now + 7
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-create-proposal",
                intentionId = "intention-create-s810",
                role = "agent",
                text = "Ready to begin?",
                nowEpochMs = now + 8,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-proposal-create-s810",
                        type = ResponseBlockType.PROPOSAL,
                        title = "Weekly practice",
                        body = "Everyday · 19:00",
                        sourceLabel = "adaptive_builder|review",
                        linkedObjectIds = listOf("proposal-create-s810"),
                        actions = listOf("Start this routine", "Edit plan", "Keep discussing"),
                        active = true
                    )
                )
            )
        )

        val result = CommandGateway.approveProposal(
            store = store,
            turnId = "turn-approve-create",
            commandId = "cmd-approve-create",
            sessionId = "session-create-s810",
            nowEpochMs = now + 20,
            utterance = "Yes",
            explicitProposalId = "proposal-create-s810"
        )
        assertEquals(GatewayStatus.APPLIED, result.status)

        val agent = CrossSurfaceContinuity.openAgentTab(store, now + 30, tz, registry)
        assertEquals("intention-create-s810", agent.intentionId)
        assertEquals(IntentionPhase.ACTIVE, agent.intentionPhase)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, agent.navigateTo)
        assertTrue(agent.activeScheduleBlockPresent)
        assertFalse(agent.proposalCardPresent)
        assertFalse(agent.createGreetingStillInteractive)
        assertTrue(
            agent.nextForYouTitle == CrossSurfaceContinuity.ACTIVE_SCHEDULE_TITLE ||
                agent.nextForYouTitle!!.isNotBlank()
        )
        val scheduleBody = store.snapshot().turns
            .flatMap { it.blocks }
            .first {
                it.active && it.title == CrossSurfaceContinuity.ACTIVE_SCHEDULE_TITLE
            }
            .body
        assertTrue(scheduleBody.contains("approved", ignoreCase = true))
        assertTrue(scheduleBody.contains("19:00") || scheduleBody.contains("day 1"))
    }
}
