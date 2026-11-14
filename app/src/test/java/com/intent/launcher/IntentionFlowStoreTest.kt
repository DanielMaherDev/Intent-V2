package com.intent.launcher

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.store.IntentionFlowStore
import java.io.File
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IntentionFlowStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L
    private val tz = TimeZone.getTimeZone("UTC")

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun spanishFlow_factDistinctFromProposal_approveDeferPersist() {
        SpanishScenario.seed(store, now)
        var snap = store.snapshot()
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first().phase)

        SpanishScenario.onChip(store, "Everyday conversations", now + 10)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 20)
        snap = store.snapshot()

        val fact = snap.facts.first { it.id == SpanishScenario.FACT_TUTOR_ID }
        assertEquals(ProvenanceKind.SAID, fact.provenance.kind)
        val proposal = snap.proposals.first { it.id == SpanishScenario.PROPOSAL_ID }
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertNotEquals(fact.text, proposal.summary)
        assertTrue(proposal.basedOnFactIds.contains(fact.id))
        assertEquals(IntentionPhase.PROPOSAL_PENDING, snap.intentions.first().phase)

        val review = NextActionEngine.recommend(snap, now + 30, tz)
        assertEquals(NextActionKind.REVIEW_PROPOSAL, review.kind)

        SpanishScenario.approve(store, now + 40)
        snap = store.snapshot()
        assertEquals(ProposalStatus.APPROVED, snap.proposals.first().status)
        assertEquals(1, snap.sessions.size)
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first().phase)

        val next = NextActionEngine.recommend(snap, now + 50, tz)
        assertEquals(NextActionKind.BEGIN, next.kind)
        assertEquals("Begin", next.beginLabel)
        assertEquals("Later", next.deferLabel)
        assertEquals("Discuss", next.discussLabel)

        val deferUntil = now + 3_600_000L
        SpanishScenario.deferSession(store, deferUntil, now + 60)
        snap = store.snapshot()
        assertEquals(deferUntil, snap.sessions.first().deferredUntilEpochMs)
        val afterDefer = NextActionEngine.recommend(snap, now + 70, tz)
        assertTrue(afterDefer.detail.contains("Deferred"))

        // Restart = new store instance on same file
        val restarted = IntentionFlowStore(File(tmp.root, "flow.json"))
        val again = restarted.snapshot()
        assertEquals(IntentionPhase.ACTIVE, again.intentions.first().phase)
        assertEquals(deferUntil, again.sessions.first().deferredUntilEpochMs)
        assertEquals(ProvenanceKind.SAID, again.facts.first().provenance.kind)
    }

    @Test
    fun declineProposal_returnsToClarifying() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a Spanish tutor", now + 5)
        SpanishScenario.decline(store, now + 10)
        val snap = store.snapshot()
        assertEquals(ProposalStatus.DECLINED, snap.proposals.first().status)
        assertEquals(IntentionPhase.CLARIFYING, snap.intentions.first().phase)
        assertTrue(snap.sessions.isEmpty())
    }

    @Test
    fun editProposal_keepsPendingWithNewTiming() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 5)
        store.apply(
            FlowCommand.EditProposal(
                proposalId = SpanishScenario.PROPOSAL_ID,
                dayOfWeek = 5,
                hour = 19,
                minute = 30,
                nowEpochMs = now + 10
            )
        )
        val p = store.snapshot().proposals.first()
        assertEquals(ProposalStatus.PENDING, p.status)
        assertEquals(5, p.proposedDayOfWeek)
        assertEquals(19, p.proposedHour)
        assertEquals(30, p.proposedMinute)
    }

    @Test
    fun marathonShape_sharesCoreRecords() {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = "intention-marathon",
                title = "Run a marathon",
                statement = "Train for a spring marathon without a fixed weekly plan yet.",
                shapeTag = "marathon",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddUserFact(
                factId = "fact-long-run",
                intentionId = "intention-marathon",
                text = "Long runs feel best on Sundays",
                nowEpochMs = now + 1
            )
        )
        store.apply(
            FlowCommand.AddHypothesis(
                hypothesisId = "hyp-base",
                intentionId = "intention-marathon",
                text = "Base-building phase before speed work",
                nowEpochMs = now + 2
            )
        )
        val snap = store.snapshot()
        assertEquals("marathon", snap.intentions.first().shapeTag)
        assertEquals(ProvenanceKind.SAID, snap.facts.first().provenance.kind)
        assertEquals(ProvenanceKind.INFERRED, snap.hypotheses.first().provenance.kind)
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first().phase)
        val action = NextActionEngine.recommend(snap, now + 3, tz)
        assertEquals(NextActionKind.CONTINUE_EXPLORING, action.kind)
    }

    @Test
    fun businessDiscovery_noForcedTasks() {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = "intention-biz",
                title = "Explore a small business idea",
                statement = "Still figuring out whether a neighborhood repair service makes sense.",
                shapeTag = "business",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddHypothesis(
                hypothesisId = "hyp-market",
                intentionId = "intention-biz",
                text = "Demand is unclear; stay in discovery",
                nowEpochMs = now + 1
            )
        )
        val snap = store.snapshot()
        assertEquals("business", snap.intentions.first().shapeTag)
        assertTrue(snap.sessions.isEmpty())
        assertTrue(snap.proposals.isEmpty())
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first().phase)
        assertEquals(NextActionKind.CONTINUE_EXPLORING, NextActionEngine.recommend(snap, now + 2, tz).kind)
    }

    @Test
    fun migration_stampsCurrentSchema() {
        val raw = """{"schemaVersion":0,"intentions":[],"facts":[],"hypotheses":[],"proposals":[],"sessions":[],"turns":[],"activeIntentionId":null}"""
        val f = tmp.newFile("old.json")
        f.writeText(raw)
        val s = IntentionFlowStore(f)
        assertEquals(1, s.snapshot().schemaVersion)
    }

    @Test
    fun approveRequiresCommand_notDirectMutation() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 1)
        val before = store.snapshot()
        assertNotNull(before.proposals.firstOrNull { it.status == ProposalStatus.PENDING })
        // Only FlowCommand path changes status
        store.apply(
            FlowCommand.ApproveProposal(
                proposalId = SpanishScenario.PROPOSAL_ID,
                sessionId = SpanishScenario.SESSION_ID,
                nowEpochMs = now + 2
            )
        )
        assertEquals(ProposalStatus.APPROVED, store.snapshot().proposals.first().status)
    }
}
