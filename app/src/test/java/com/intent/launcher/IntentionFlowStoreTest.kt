package com.intent.launcher

import com.intent.launcher.domain.BusinessScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.MarathonScenario
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

        // Thursday 16 Nov 2023 18:30 UTC — session day, due now
        val dueNow = 1_700_159_400_000L
        val next = NextActionEngine.recommend(snap, dueNow, tz)
        assertEquals(NextActionKind.BEGIN, next.kind)
        assertEquals("Begin", next.beginLabel)
        assertEquals("Later", next.deferLabel)
        assertEquals("Discuss", next.discussLabel)
        assertTrue(next.reason.isNotBlank())

        val deferUntil = dueNow + 3_600_000L
        SpanishScenario.deferSession(store, deferUntil, dueNow + 10)
        snap = store.snapshot()
        assertEquals(deferUntil, snap.sessions.first().deferredUntilEpochMs)
        val afterDefer = NextActionEngine.recommend(snap, dueNow + 20, tz)
        assertTrue(afterDefer.detail.contains("Deferred"))
        assertTrue(afterDefer.reason.contains("deferred", ignoreCase = true))

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
    fun marathonFlow_factDistinctFromProposal_approveDeferPersist() {
        MarathonScenario.seed(store, now)
        var snap = store.snapshot()
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first { it.id == MarathonScenario.INTENTION_ID }.phase)
        assertEquals("marathon", snap.intentions.first { it.id == MarathonScenario.INTENTION_ID }.shapeTag)

        MarathonScenario.onChip(store, "Build a base before speed work", now + 10)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", now + 20)
        snap = store.snapshot()

        val fact = snap.facts.first { it.id == MarathonScenario.FACT_LONG_RUN_ID }
        assertEquals(ProvenanceKind.SAID, fact.provenance.kind)
        val proposal = snap.proposals.first { it.id == MarathonScenario.PROPOSAL_ID }
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertNotEquals(fact.text, proposal.summary)
        assertTrue(proposal.basedOnFactIds.contains(fact.id))
        assertEquals(IntentionPhase.PROPOSAL_PENDING, snap.intentions.first { it.id == MarathonScenario.INTENTION_ID }.phase)

        MarathonScenario.approve(store, now + 40)
        snap = store.snapshot()
        assertEquals(ProposalStatus.APPROVED, snap.proposals.first { it.id == MarathonScenario.PROPOSAL_ID }.status)
        assertEquals(1, snap.sessions.count { it.id == MarathonScenario.SESSION_ID })
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first { it.id == MarathonScenario.INTENTION_ID }.phase)

        // Marathon long-run is Sunday 09:00 — pin Sun 19 Nov 2023 09:00 UTC
        val sundayDue = 1_700_384_400_000L
        val next = NextActionEngine.recommend(snap, sundayDue, tz)
        assertEquals(NextActionKind.BEGIN, next.kind)
        assertTrue(next.reason.isNotBlank())

        val deferUntil = sundayDue + 3_600_000L
        MarathonScenario.deferSession(store, deferUntil, sundayDue + 10)
        snap = store.snapshot()
        assertEquals(deferUntil, snap.sessions.first { it.id == MarathonScenario.SESSION_ID }.deferredUntilEpochMs)
        val afterDefer = NextActionEngine.recommend(snap, sundayDue + 20, tz)
        assertTrue(afterDefer.detail.contains("Deferred"))
        assertTrue(afterDefer.reason.contains("deferred", ignoreCase = true))

        val restarted = IntentionFlowStore(File(tmp.root, "flow.json"))
        val again = restarted.snapshot()
        assertEquals(IntentionPhase.ACTIVE, again.intentions.first { it.id == MarathonScenario.INTENTION_ID }.phase)
        assertEquals(deferUntil, again.sessions.first { it.id == MarathonScenario.SESSION_ID }.deferredUntilEpochMs)
        assertEquals(ProvenanceKind.SAID, again.facts.first { it.id == MarathonScenario.FACT_LONG_RUN_ID }.provenance.kind)
    }

    @Test
    fun businessDiscovery_noForcedTasks() {
        BusinessScenario.seed(store, now)
        BusinessScenario.onChip(store, "Keep this in discovery for now", now + 10)
        val snap = store.snapshot()
        val intention = snap.intentions.first { it.id == BusinessScenario.INTENTION_ID }
        assertEquals("business", intention.shapeTag)
        assertTrue(snap.sessions.none { it.intentionId == BusinessScenario.INTENTION_ID })
        assertTrue(snap.proposals.none { it.intentionId == BusinessScenario.INTENTION_ID })
        assertEquals(IntentionPhase.EXPLORING, intention.phase)
        val bizNext = NextActionEngine.recommend(snap, now + 20, tz)
        assertEquals(NextActionKind.CONTINUE_EXPLORING, bizNext.kind)
        assertTrue(bizNext.reason.contains("discovery", ignoreCase = true))
        assertTrue(snap.hypotheses.any { it.id == "hyp-stay-discovery" })
    }

    @Test
    fun businessFlow_factDistinctFromProposal_approveDeferPersist() {
        BusinessScenario.seed(store, now)
        var snap = store.snapshot()
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first { it.id == BusinessScenario.INTENTION_ID }.phase)
        assertEquals("business", snap.intentions.first { it.id == BusinessScenario.INTENTION_ID }.shapeTag)

        BusinessScenario.onChip(store, "Demand is still unclear", now + 10)
        BusinessScenario.onChip(store, "Customer chats work best on Wednesdays", now + 20)
        snap = store.snapshot()

        val fact = snap.facts.first { it.id == BusinessScenario.FACT_CUSTOMER_CHAT_ID }
        assertEquals(ProvenanceKind.SAID, fact.provenance.kind)
        val proposal = snap.proposals.first { it.id == BusinessScenario.PROPOSAL_ID }
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertNotEquals(fact.text, proposal.summary)
        assertTrue(proposal.basedOnFactIds.contains(fact.id))
        assertEquals(
            IntentionPhase.PROPOSAL_PENDING,
            snap.intentions.first { it.id == BusinessScenario.INTENTION_ID }.phase
        )

        val review = NextActionEngine.recommend(snap, now + 30, tz)
        assertEquals(NextActionKind.REVIEW_PROPOSAL, review.kind)
        assertTrue(review.reason.isNotBlank())

        BusinessScenario.approve(store, now + 40)
        snap = store.snapshot()
        assertEquals(ProposalStatus.APPROVED, snap.proposals.first { it.id == BusinessScenario.PROPOSAL_ID }.status)
        assertEquals(1, snap.sessions.count { it.id == BusinessScenario.SESSION_ID })
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first { it.id == BusinessScenario.INTENTION_ID }.phase)

        // Wednesday research hour is Wed 12:00 — pin Wed 15 Nov 2023 12:00 UTC
        val wednesdayDue = 1_700_049_600_000L
        val next = NextActionEngine.recommend(snap, wednesdayDue, tz)
        assertEquals(NextActionKind.BEGIN, next.kind)
        assertTrue(next.reason.isNotBlank())

        val deferUntil = wednesdayDue + 3_600_000L
        BusinessScenario.deferSession(store, deferUntil, wednesdayDue + 10)
        snap = store.snapshot()
        assertEquals(deferUntil, snap.sessions.first { it.id == BusinessScenario.SESSION_ID }.deferredUntilEpochMs)
        val afterDefer = NextActionEngine.recommend(snap, wednesdayDue + 20, tz)
        assertTrue(afterDefer.detail.contains("Deferred"))
        assertTrue(afterDefer.reason.contains("deferred", ignoreCase = true))

        val restarted = IntentionFlowStore(File(tmp.root, "flow.json"))
        val again = restarted.snapshot()
        assertEquals(IntentionPhase.ACTIVE, again.intentions.first { it.id == BusinessScenario.INTENTION_ID }.phase)
        assertEquals(deferUntil, again.sessions.first { it.id == BusinessScenario.SESSION_ID }.deferredUntilEpochMs)
        assertEquals(
            ProvenanceKind.SAID,
            again.facts.first { it.id == BusinessScenario.FACT_CUSTOMER_CHAT_ID }.provenance.kind
        )
    }

    @Test
    fun threeShapes_shareCoreRecordsWithoutSpecialCaseFields() {
        SpanishScenario.seed(store, now)
        MarathonScenario.seed(store, now + 1)
        BusinessScenario.seed(store, now + 2)
        val snap = store.snapshot()
        assertEquals(3, snap.intentions.size)
        assertTrue(snap.intentions.any { it.shapeTag == "language" })
        assertTrue(snap.intentions.any { it.shapeTag == "marathon" })
        assertTrue(snap.intentions.any { it.shapeTag == "business" })
        // Same Intention / Fact / Hypothesis / Proposal / Session types — no shape-specific columns
        assertEquals(BusinessScenario.INTENTION_ID, snap.activeIntentionId)
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
