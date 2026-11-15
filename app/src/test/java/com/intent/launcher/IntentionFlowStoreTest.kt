package com.intent.launcher

import com.intent.launcher.domain.BusinessScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.JobOfferScenario
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.MoveScenario
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.QuitSmokingScenario
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.ShapeProjection
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrackerModules
import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.store.IntentionFlowStore
import java.io.File
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        // Marathon long-run is Sunday 09:00 — pin Sun 19 Nov 2023 09:00 UTC (morning check-in window).
        val sundayDue = 1_700_384_400_000L
        val morning = NextActionEngine.recommend(snap, sundayDue, tz)
        assertEquals(NextActionKind.CHECK_IN, morning.kind)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = MarathonScenario.INTENTION_ID,
                localDate = com.intent.launcher.engine.DailyCycle.localDateString(sundayDue, tz),
                focusSummary = morning.detail,
                nowEpochMs = sundayDue
            )
        )
        snap = store.snapshot()
        val next = NextActionEngine.recommend(snap, sundayDue + 1, tz)
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
    fun fourShapes_shareCoreRecordsIncludingMove() {
        SpanishScenario.seed(store, now)
        MarathonScenario.seed(store, now + 1)
        BusinessScenario.seed(store, now + 2)
        MoveScenario.seed(store, now + 3)
        val snap = store.snapshot()
        assertEquals(4, snap.intentions.size)
        assertTrue(snap.intentions.any { it.shapeTag == "move" })
        assertEquals(MoveScenario.INTENTION_ID, snap.activeIntentionId)
    }

    @Test
    fun moveFlow_noPlace_landlordStepsStayGated() {
        MoveScenario.seed(store, now)
        MoveScenario.onChip(store, "Six weeks — Barcelona to Valencia, renting", now + 10)
        MoveScenario.onChip(store, "No place found yet", now + 20)
        var snap = store.snapshot()

        val intention = snap.intentions.first { it.id == MoveScenario.INTENTION_ID }
        assertEquals("move", intention.shapeTag)
        assertEquals(IntentionPhase.CLARIFYING, intention.phase)
        assertEquals(ProvenanceKind.SAID, snap.facts.first { it.id == MoveScenario.FACT_DEADLINE_ID }.provenance.kind)
        assertEquals(ProvenanceKind.SAID, snap.facts.first { it.id == MoveScenario.FACT_NO_PLACE_ID }.provenance.kind)
        assertTrue(snap.hypotheses.any { it.id == MoveScenario.HYP_LEASE_ID && it.text.contains("Gated") })
        assertTrue(snap.hypotheses.any { it.id == MoveScenario.HYP_SEARCH_ID && it.text.contains("Ready") })
        assertFalse(MoveScenario.placeIsConfirmed(snap.facts))
        assertTrue(snap.proposals.none { it.intentionId == MoveScenario.INTENTION_ID })

        val projection = ShapeProjection.project(
            intention = intention,
            facts = snap.facts.filter { it.intentionId == intention.id },
            hypotheses = snap.hypotheses.filter { it.intentionId == intention.id }
        )
        assertEquals("Your move plan", projection.approachSectionTitle)
        assertTrue(projection.approachSteps.any { it.title.contains("Search") && !it.muted })
        assertTrue(projection.approachSteps.any { it.title.contains("Sign lease") && it.muted })
        assertTrue(projection.approachSteps.any { it.title.contains("movers") && it.muted })
        assertTrue(projection.approachSteps.any { it.detail.contains("Gated", ignoreCase = true) })
    }

    @Test
    fun moveFlow_searchProposal_approveDeferPersist_doesNotInventLease() {
        MoveScenario.seed(store, now)
        MoveScenario.onChip(store, "Six weeks — Barcelona to Valencia, renting", now + 10)
        MoveScenario.onChip(store, "No place found yet", now + 15)
        MoveScenario.onChip(store, "Budget is tight until the lease starts", now + 18)
        MoveScenario.onChip(store, "Schedule weekly apartment search", now + 20)
        var snap = store.snapshot()

        val fact = snap.facts.first { it.id == MoveScenario.FACT_DEADLINE_ID }
        val proposal = snap.proposals.first { it.id == MoveScenario.PROPOSAL_ID }
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertNotEquals(fact.text, proposal.summary)
        assertTrue(proposal.summary.contains("landlord", ignoreCase = true))
        assertTrue(proposal.summary.contains("not a claim that a place exists", ignoreCase = true))
        assertTrue(proposal.basedOnFactIds.contains(fact.id))
        assertEquals(
            IntentionPhase.PROPOSAL_PENDING,
            snap.intentions.first { it.id == MoveScenario.INTENTION_ID }.phase
        )

        val review = NextActionEngine.recommend(snap, now + 30, tz)
        assertEquals(NextActionKind.REVIEW_PROPOSAL, review.kind)

        MoveScenario.approve(store, now + 40)
        snap = store.snapshot()
        assertEquals(ProposalStatus.APPROVED, snap.proposals.first { it.id == MoveScenario.PROPOSAL_ID }.status)
        assertEquals(1, snap.sessions.count { it.id == MoveScenario.SESSION_ID })
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first { it.id == MoveScenario.INTENTION_ID }.phase)
        // Still no place invented after approve
        assertFalse(MoveScenario.placeIsConfirmed(snap.facts))
        assertTrue(snap.facts.any { it.id == MoveScenario.FACT_NO_PLACE_ID })

        // Saturday apartment search is Sat 10:00 — pin Sat 18 Nov 2023 10:00 UTC (morning check-in).
        val saturdayDue = 1_700_301_600_000L
        val morning = NextActionEngine.recommend(snap, saturdayDue, tz)
        assertEquals(NextActionKind.CHECK_IN, morning.kind)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = MoveScenario.INTENTION_ID,
                localDate = com.intent.launcher.engine.DailyCycle.localDateString(saturdayDue, tz),
                focusSummary = morning.detail,
                nowEpochMs = saturdayDue
            )
        )
        snap = store.snapshot()
        val next = NextActionEngine.recommend(snap, saturdayDue + 1, tz)
        assertEquals(NextActionKind.BEGIN, next.kind)

        val deferUntil = saturdayDue + 3_600_000L
        MoveScenario.deferSession(store, deferUntil, saturdayDue + 10)
        snap = store.snapshot()
        assertEquals(deferUntil, snap.sessions.first { it.id == MoveScenario.SESSION_ID }.deferredUntilEpochMs)

        val restarted = IntentionFlowStore(File(tmp.root, "flow.json"))
        val again = restarted.snapshot()
        assertEquals(IntentionPhase.ACTIVE, again.intentions.first { it.id == MoveScenario.INTENTION_ID }.phase)
        assertEquals(deferUntil, again.sessions.first { it.id == MoveScenario.SESSION_ID }.deferredUntilEpochMs)
        assertEquals("move", again.intentions.first { it.id == MoveScenario.INTENTION_ID }.shapeTag)
        assertFalse(MoveScenario.placeIsConfirmed(again.facts))
    }

    @Test
    fun jobOffer_keepComparing_doesNotChooseForUser() {
        JobOfferScenario.seed(store, now)
        JobOfferScenario.onChip(store, "I have an offer and ten days to decide", now + 10)
        JobOfferScenario.onChip(
            store,
            "Compensation higher; growth unclear vs current role",
            now + 20
        )
        JobOfferScenario.onChip(store, "Keep comparing — do not choose for me", now + 30)

        val snap = store.snapshot()
        val intention = snap.intentions.first { it.id == JobOfferScenario.INTENTION_ID }
        assertEquals("decision", intention.shapeTag)
        assertEquals(IntentionPhase.EXPLORING, intention.phase)
        assertTrue(snap.proposals.none { it.intentionId == JobOfferScenario.INTENTION_ID })
        assertTrue(snap.sessions.none { it.intentionId == JobOfferScenario.INTENTION_ID })
        assertEquals(ProvenanceKind.SAID, snap.facts.first { it.id == JobOfferScenario.FACT_DEADLINE_ID }.provenance.kind)
        assertEquals(
            ProvenanceKind.SAID,
            snap.facts.first { it.id == JobOfferScenario.FACT_CRITERION_COMP_ID }.provenance.kind
        )
        assertEquals(
            ProvenanceKind.SAID,
            snap.facts.first { it.id == JobOfferScenario.FACT_CRITERION_GROWTH_ID }.provenance.kind
        )
        val agentTurns = snap.turns.filter { it.intentionId == JobOfferScenario.INTENTION_ID && it.role == "agent" }
        assertTrue(agentTurns.any { it.text.contains("No choice made on your behalf", ignoreCase = true) })
        // Negation language ("will not accept or decline") is allowed; recommendations are not.
        assertFalse(
            agentTurns.any {
                val t = it.text.lowercase()
                t.contains("you should accept") ||
                    t.contains("you should decline") ||
                    t.contains("i recommend accepting") ||
                    t.contains("i recommend declining") ||
                    t.contains("accept on your behalf") ||
                    t.contains("decline on your behalf")
            }
        )
        assertTrue(
            agentTurns.any {
                it.text.contains("will not", ignoreCase = true) &&
                    it.text.contains("accept", ignoreCase = true) &&
                    it.text.contains("decline", ignoreCase = true)
            } || agentTurns.any { it.text.contains("No choice made on your behalf", ignoreCase = true) }
        )
    }

    @Test
    fun jobOfferFlow_criteriaMatrix_reviewProposal_doesNotAutoDecide() {
        JobOfferScenario.seed(store, now)
        assertEquals(IntentionPhase.EXPLORING, store.snapshot().intentions.first { it.id == JobOfferScenario.INTENTION_ID }.phase)
        assertEquals("decision", store.snapshot().intentions.first { it.id == JobOfferScenario.INTENTION_ID }.shapeTag)

        JobOfferScenario.onChip(store, "I have an offer and ten days to decide", now + 10)
        JobOfferScenario.onChip(
            store,
            "Compensation higher; growth unclear vs current role",
            now + 20
        )
        JobOfferScenario.onChip(store, "Commute would be longer than today", now + 30)
        JobOfferScenario.onChip(store, "Schedule a decision review before the deadline", now + 40)

        var snap = store.snapshot()
        val proposal = snap.proposals.first { it.id == JobOfferScenario.PROPOSAL_ID }
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertTrue(proposal.title.contains("Decision review", ignoreCase = true))
        assertTrue(proposal.summary.contains("does not accept or decline", ignoreCase = true))
        assertFalse(proposal.title.contains("accept", ignoreCase = true))
        assertFalse(proposal.title.contains("decline", ignoreCase = true))
        assertTrue(proposal.basedOnFactIds.contains(JobOfferScenario.FACT_DEADLINE_ID))
        assertEquals(
            IntentionPhase.PROPOSAL_PENDING,
            snap.intentions.first { it.id == JobOfferScenario.INTENTION_ID }.phase
        )
        assertEquals(3, snap.facts.count { it.id.startsWith("fact-criterion-") })
        assertTrue(snap.facts.all { it.provenance.kind == ProvenanceKind.SAID })

        JobOfferScenario.approve(store, now + 50)
        snap = store.snapshot()
        assertEquals(ProposalStatus.APPROVED, snap.proposals.first { it.id == JobOfferScenario.PROPOSAL_ID }.status)
        assertEquals(1, snap.sessions.count { it.id == JobOfferScenario.SESSION_ID })
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first { it.id == JobOfferScenario.INTENTION_ID }.phase)
        assertEquals("Decision review", snap.sessions.first { it.id == JobOfferScenario.SESSION_ID }.title)

        val deferUntil = now + 86_400_000L
        JobOfferScenario.deferSession(store, deferUntil, now + 60)
        assertEquals(deferUntil, store.snapshot().sessions.first { it.id == JobOfferScenario.SESSION_ID }.deferredUntilEpochMs)

        val restarted = IntentionFlowStore(File(tmp.root, "flow.json"))
        val again = restarted.snapshot()
        assertEquals(IntentionPhase.ACTIVE, again.intentions.first { it.id == JobOfferScenario.INTENTION_ID }.phase)
        assertEquals(deferUntil, again.sessions.first { it.id == JobOfferScenario.SESSION_ID }.deferredUntilEpochMs)
        assertEquals(
            ProvenanceKind.SAID,
            again.facts.first { it.id == JobOfferScenario.FACT_CRITERION_COMMUTE_ID }.provenance.kind
        )
    }

    @Test
    fun fourShapes_shareCoreRecordsIncludingDecision() {
        SpanishScenario.seed(store, now)
        MarathonScenario.seed(store, now + 1)
        BusinessScenario.seed(store, now + 2)
        JobOfferScenario.seed(store, now + 3)
        val snap = store.snapshot()
        assertEquals(4, snap.intentions.size)
        assertTrue(snap.intentions.any { it.shapeTag == "language" })
        assertTrue(snap.intentions.any { it.shapeTag == "marathon" })
        assertTrue(snap.intentions.any { it.shapeTag == "business" })
        assertTrue(snap.intentions.any { it.shapeTag == "decision" })
        assertEquals(JobOfferScenario.INTENTION_ID, snap.activeIntentionId)
    }

    @Test
    fun quitSmokingFlow_baselineSelfReport_approveDeferPersist() {
        QuitSmokingScenario.seed(store, now)
        var snap = store.snapshot()
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first { it.id == QuitSmokingScenario.INTENTION_ID }.phase)
        assertEquals(QuitSmokingScenario.SHAPE_TAG, snap.intentions.first { it.id == QuitSmokingScenario.INTENTION_ID }.shapeTag)

        QuitSmokingScenario.onChip(store, "I smoke about eight a day", now + 10)
        QuitSmokingScenario.onChip(store, "No quit date yet", now + 20)
        QuitSmokingScenario.onChip(store, "Evenings are hardest", now + 30)
        snap = store.snapshot()

        val baseline = snap.facts.first { it.id == QuitSmokingScenario.FACT_BASELINE_ID }
        assertEquals(ProvenanceKind.SAID, baseline.provenance.kind)
        assertTrue(baseline.text.contains("8"))
        assertTrue(snap.hypotheses.any { it.text.contains("No quit date", ignoreCase = true) })

        val proposal = snap.proposals.first { it.id == QuitSmokingScenario.PROPOSAL_ID }
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertNotEquals(baseline.text, proposal.summary)
        assertTrue(proposal.summary.contains("evening", ignoreCase = true))
        assertEquals(IntentionPhase.PROPOSAL_PENDING, snap.intentions.first { it.id == QuitSmokingScenario.INTENTION_ID }.phase)

        QuitSmokingScenario.approve(store, now + 40)
        snap = store.snapshot()
        assertEquals(ProposalStatus.APPROVED, snap.proposals.first { it.id == QuitSmokingScenario.PROPOSAL_ID }.status)
        assertEquals(1, snap.sessions.count { it.id == QuitSmokingScenario.SESSION_ID })
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first { it.id == QuitSmokingScenario.INTENTION_ID }.phase)

        QuitSmokingScenario.onChip(store, "I smoked fewer than usual today", now + 50)
        QuitSmokingScenario.onChip(store, "I rode out a craving", now + 60)
        snap = store.snapshot()
        val activeEntries = snap.trackerEntries.filter { it.status == TrackerEntryStatus.ACTIVE }
        assertEquals(2, activeEntries.size)
        assertTrue(activeEntries.all { it.provenance.kind == ProvenanceKind.SAID })
        assertTrue(activeEntries.any { it.moduleId == "cigarettes_today" })
        assertTrue(activeEntries.any { it.moduleId == "craving_log" })
        assertFalse(activeEntries.any { it.valueText.contains("100%", ignoreCase = true) })
        assertFalse(activeEntries.any { it.valueText.contains("streak", ignoreCase = true) })

        val modules = TrackerModules.forShape(QuitSmokingScenario.SHAPE_TAG)
        assertTrue(modules.any { it.id == "cigarettes_today" })
        assertTrue(modules.any { it.id == "craving_log" })
        assertTrue(modules.any { it.id == "support_check" })
        assertFalse(modules.any { it.id == "general_log" })

        val mondayEvening = 1_700_165_280_000L // approx Mon 20:00-ish for schedule
        val deferUntil = mondayEvening + 3 * 60 * 60 * 1000L
        QuitSmokingScenario.deferSession(store, deferUntil, mondayEvening + 10)
        snap = store.snapshot()
        assertEquals(deferUntil, snap.sessions.first { it.id == QuitSmokingScenario.SESSION_ID }.deferredUntilEpochMs)

        val encoded = store.encode(snap)
        val againFile = tmp.newFile("cessation-again.json")
        againFile.writeText(encoded)
        val again = IntentionFlowStore(againFile).snapshot()
        assertEquals(6, again.schemaVersion)
        assertEquals(IntentionPhase.ACTIVE, again.intentions.first { it.id == QuitSmokingScenario.INTENTION_ID }.phase)
        assertEquals(deferUntil, again.sessions.first { it.id == QuitSmokingScenario.SESSION_ID }.deferredUntilEpochMs)
        assertEquals(ProvenanceKind.SAID, again.facts.first { it.id == QuitSmokingScenario.FACT_BASELINE_ID }.provenance.kind)
        assertEquals(2, again.trackerEntries.count { it.status == TrackerEntryStatus.ACTIVE })
    }

    @Test
    fun fourShapes_shareCoreRecordsIncludingCessation() {
        SpanishScenario.seed(store, now)
        MarathonScenario.seed(store, now + 1)
        BusinessScenario.seed(store, now + 2)
        QuitSmokingScenario.seed(store, now + 3)
        val snap = store.snapshot()
        assertEquals(4, snap.intentions.size)
        assertTrue(snap.intentions.any { it.shapeTag == QuitSmokingScenario.SHAPE_TAG })
        assertEquals(QuitSmokingScenario.INTENTION_ID, snap.activeIntentionId)
    }

    @Test
    fun migration_stampsCurrentSchema() {
        val raw = """{"schemaVersion":0,"intentions":[],"facts":[],"hypotheses":[],"proposals":[],"sessions":[],"turns":[],"activeIntentionId":null}"""
        val f = tmp.newFile("old.json")
        f.writeText(raw)
        val s = IntentionFlowStore(f)
        assertEquals(com.intent.launcher.domain.SCHEMA_VERSION, s.snapshot().schemaVersion)
        assertTrue(s.snapshot().sessionRuns.isEmpty())
        assertTrue(s.snapshot().trackerEntries.isEmpty())
    }

    @Test
    fun beginSession_createsInProgressRun_completeAndAbandon() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 1)
        SpanishScenario.approve(store, now + 2)

        SpanishScenario.beginSession(store, now + 3, runId = "run-spanish-1")
        var snap = store.snapshot()
        assertEquals(1, snap.sessionRuns.size)
        assertEquals(SessionRunStatus.IN_PROGRESS, snap.sessionRuns.first().status)
        assertEquals(SpanishScenario.SESSION_ID, snap.sessionRuns.first().sessionId)

        val dueNow = 1_700_159_400_000L
        val active = NextActionEngine.recommend(snap, dueNow, tz)
        assertEquals(NextActionKind.ACTIVE_SESSION, active.kind)
        assertEquals("Complete", active.completeLabel)
        assertEquals("Abandon", active.abandonLabel)

        store.apply(
            FlowCommand.CompleteSessionRun(runId = "run-spanish-1", nowEpochMs = now + 4)
        )
        snap = store.snapshot()
        assertEquals(SessionRunStatus.COMPLETED, snap.sessionRuns.first().status)
        assertNotNull(snap.sessionRuns.first().endedAtEpochMs)

        // After complete, engine returns to Begin (or Discuss) for the schedule — not ACTIVE_SESSION
        val afterComplete = NextActionEngine.recommend(snap, dueNow, tz)
        assertTrue(afterComplete.kind != NextActionKind.ACTIVE_SESSION)

        SpanishScenario.beginSession(store, now + 5, runId = "run-spanish-2")
        store.apply(
            FlowCommand.AbandonSessionRun(runId = "run-spanish-2", nowEpochMs = now + 6)
        )
        snap = store.snapshot()
        assertEquals(SessionRunStatus.ABANDONED, snap.sessionRuns.first { it.id == "run-spanish-2" }.status)

        val restarted = IntentionFlowStore(File(tmp.root, "flow.json"))
        val again = restarted.snapshot()
        assertEquals(2, again.sessionRuns.size)
        assertEquals(SessionRunStatus.COMPLETED, again.sessionRuns.first { it.id == "run-spanish-1" }.status)
        assertEquals(SessionRunStatus.ABANDONED, again.sessionRuns.first { it.id == "run-spanish-2" }.status)
    }

    @Test
    fun beginSession_secondBeginWhileActive_isNoOp() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 1)
        SpanishScenario.approve(store, now + 2)
        SpanishScenario.beginSession(store, now + 3, runId = "run-a")
        SpanishScenario.beginSession(store, now + 4, runId = "run-b")
        val snap = store.snapshot()
        assertEquals(1, snap.sessionRuns.size)
        assertEquals("run-a", snap.sessionRuns.first().id)
    }

    @Test
    fun marathonAndBusiness_beginCreatesActiveSession() {
        MarathonScenario.seed(store, now)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", now + 1)
        MarathonScenario.approve(store, now + 2)
        MarathonScenario.beginSession(store, now + 3, runId = "run-marathon")
        val sundayDue = 1_700_384_400_000L
        val marathonNext = NextActionEngine.recommend(store.snapshot(), sundayDue, tz)
        assertEquals(NextActionKind.ACTIVE_SESSION, marathonNext.kind)

        BusinessScenario.seed(store, now + 10)
        BusinessScenario.onChip(store, "Customer chats work best on Wednesdays", now + 11)
        BusinessScenario.approve(store, now + 12)
        BusinessScenario.beginSession(store, now + 13, runId = "run-business")
        // Business becomes active intention after seed; Wed due
        val wednesdayDue = 1_700_049_600_000L
        val bizNext = NextActionEngine.recommend(store.snapshot(), wednesdayDue, tz)
        assertEquals(NextActionKind.ACTIVE_SESSION, bizNext.kind)
        assertEquals(BusinessScenario.SESSION_ID, bizNext.sessionId)
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
