package com.intent.launcher.domain
import com.intent.launcher.domain.JobOfferScenario
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
 * Stage 5 — shape-aware Intention / Home / Conversation projections
 * share one model; composition differs by shapeTag.
 */
class ShapeProjectionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_049_600_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun exploring_spanishVsMarathonVsBusiness_differInHeroAndEmptyNote() {
        SpanishScenario.seed(store, now)
        val spanish = ShapeProjection.project(
            store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        )
        assertEquals("How will you practise?", spanish.hero.heroTitle)
        assertTrue(spanish.emptyNote!!.contains("No tasks yet"))
        assertTrue(spanish.hero.heroBody.contains("shape this around"))
        assertEquals("Open your intention", spanish.homeSpaceSubtitle)
        assertEquals("A conversation about your goal", spanish.conversationEyebrow)

        MarathonScenario.seed(store, now + 1)
        val marathon = ShapeProjection.project(
            store.snapshot().intentions.first { it.id == MarathonScenario.INTENTION_ID }
        )
        assertEquals("How will you train?", marathon.hero.heroTitle)
        assertTrue(marathon.emptyNote!!.contains("No training plan"))
        assertEquals("Training still taking shape", marathon.homeSpaceSubtitle)
        assertEquals("A conversation about your training", marathon.conversationEyebrow)

        BusinessScenario.seed(store, now + 2)
        val business = ShapeProjection.project(
            store.snapshot().intentions.first { it.id == BusinessScenario.INTENTION_ID }
        )
        assertEquals("How will you learn?", business.hero.heroTitle)
        assertTrue(business.emptyNote!!.contains("No launch plan"))
        assertEquals("Still in discovery", business.homeSpaceSubtitle)
        assertEquals("A conversation about your idea", business.conversationEyebrow)

        MoveScenario.seed(store, now + 3)
        val move = ShapeProjection.project(
            store.snapshot().intentions.first { it.id == MoveScenario.INTENTION_ID }
        )
        assertEquals("What depends on a landlord?", move.hero.heroTitle)
        assertTrue(move.emptyNote!!.contains("No lease") || move.emptyNote!!.contains("landlord"))
        assertEquals("Move plan · landlord steps gated", move.homeSpaceSubtitle)
        assertEquals("A conversation about your move", move.conversationEyebrow)
        assertTrue(move.approachSteps.isEmpty())

        QuitSmokingScenario.seed(store, now + 4)
        val cessation = ShapeProjection.project(
            store.snapshot().intentions.first { it.id == QuitSmokingScenario.INTENTION_ID }
        )
        assertEquals("What is your baseline?", cessation.hero.heroTitle)
        assertTrue(cessation.emptyNote!!.contains("No quit date"))
        assertEquals("Baseline and support still open", cessation.homeSpaceSubtitle)
        assertEquals("A conversation about quitting smoking", cessation.conversationEyebrow)
        AppUseReductionScenario.seed(store, now + 5)
        val appUse = ShapeProjection.project(
            store.snapshot().intentions.first { it.id == AppUseReductionScenario.INTENTION_ID }
        )
        assertEquals("How will you hold the cap?", appUse.hero.heroTitle)
        assertTrue(appUse.emptyNote!!.contains("Shorts", ignoreCase = true))
        assertEquals("Usage target still taking shape", appUse.homeSpaceSubtitle)
        assertEquals("A conversation about your usage target", appUse.conversationEyebrow)
        EuWorkSpainScenario.seed(store, now + 6)
        val research = ShapeProjection.project(
            store.snapshot().intentions.first { it.id == EuWorkSpainScenario.INTENTION_ID }
        )
        assertEquals("What must we confirm first?", research.hero.heroTitle)
        assertTrue(research.emptyNote!!.contains("No legal requirements have been invented"))
        assertEquals("Clarifying facts before research", research.homeSpaceSubtitle)
        assertEquals("A conversation about working legally in Spain", research.conversationEyebrow)
    }

    @Test
    fun clarifyingMove_showsFinitePlanWithLandlordGates() {
        MoveScenario.seed(store, now)
        MoveScenario.onChip(store, "No place found yet", now + 1)
        val intention = store.snapshot().intentions.first { it.id == MoveScenario.INTENTION_ID }
        val facts = store.snapshot().facts.filter { it.intentionId == intention.id }
        val hyps = store.snapshot().hypotheses.filter { it.intentionId == intention.id }
        val projection = ShapeProjection.project(intention, facts = facts, hypotheses = hyps)

        assertEquals("Your move plan", projection.approachSectionTitle)
        assertEquals(5, projection.approachSteps.size)
        assertFalse(projection.approachSteps[0].muted)
        assertFalse(projection.approachSteps[1].muted)
        assertTrue(projection.approachSteps[2].muted)
        assertTrue(projection.approachSteps[3].muted)
        assertTrue(projection.approachSteps[4].muted)
        assertTrue(projection.approachSteps[2].detail.contains("Gated", ignoreCase = true))
        JobOfferScenario.seed(store, now + 3)
        val decision = ShapeProjection.project(
            store.snapshot().intentions.first { it.id == JobOfferScenario.INTENTION_ID }
        )
        assertEquals("What will you weigh?", decision.hero.heroTitle)
        assertTrue(decision.emptyNote!!.contains("No accept/decline"))
        assertEquals("Comparing — no choice made yet", decision.homeSpaceSubtitle)
        assertEquals("A conversation about your offer", decision.conversationEyebrow)
        assertTrue(decision.conversationHeadline.contains("will not choose", ignoreCase = true))
    }

    @Test
    fun activeAppUse_showsUsageApproachAndShortsHonesty() {
        AppUseReductionScenario.seed(store, now)
        AppUseReductionScenario.onChip(store, "I want under 45 minutes a day", now + 1)
        AppUseReductionScenario.approve(store, now + 2)
        val intention = store.snapshot().intentions.first {
            it.id == AppUseReductionScenario.INTENTION_ID
        }
        val sessions = store.snapshot().sessions.filter { it.intentionId == intention.id }
        val projection = ShapeProjection.project(
            intention = intention,
            sessions = sessions,
            destination = SessionDestinations.forIntention(intention)
        )
        assertEquals("Your usage approach", projection.approachSectionTitle)
        assertTrue(projection.approachSteps.first().title.contains("usage", ignoreCase = true))
        assertNotNull(projection.resourceLine)
        assertTrue(projection.resourceLine!!.contains("Shorts", ignoreCase = true) ||
            projection.resourceLine!!.contains("package", ignoreCase = true))
        assertEquals("Daily usage target in motion", projection.homeSpaceSubtitle)
        assertEquals("Prepare your next usage review", projection.hero.heroTitle)
    }

    @Test
    fun activeResearch_showsChecklistHonesty_withoutInventedLaw() {
        EuWorkSpainScenario.seed(store, now)
        EuWorkSpainScenario.onChip(store, "I am an EU citizen", now + 1)
        EuWorkSpainScenario.onChip(store, "I already live in Spain", now + 2)
        EuWorkSpainScenario.onChip(store, "I plan to work for more than three months", now + 3)
        EuWorkSpainScenario.onChip(store, "I have not gathered documents yet", now + 4)
        EuWorkSpainScenario.onChip(store, "Build a sourced research checklist", now + 5)
        EuWorkSpainScenario.approve(store, now + 6)

        val intention = store.snapshot().intentions.first { it.id == EuWorkSpainScenario.INTENTION_ID }
        val sessions = store.snapshot().sessions.filter { it.intentionId == intention.id }
        val projection = ShapeProjection.project(intention, sessions = sessions)

        assertEquals("Your research checklist", projection.approachSectionTitle)
        assertTrue(projection.approachSteps.first().title.contains("checklist", ignoreCase = true))
        assertTrue(projection.resourceLine.orEmpty().contains("official", ignoreCase = true))
        assertTrue(projection.resourceLine.orEmpty().contains("not legal advice", ignoreCase = true))
        assertEquals("Prepare your next checklist review", projection.hero.heroTitle)
        assertEquals("Checklist research in motion", projection.homeSpaceSubtitle)
    }

    @Test
    fun activeSpanish_showsApproachStepsAndDuolingoResource() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 1)
        SpanishScenario.approve(store, now + 2)

        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        assertEquals(IntentionPhase.ACTIVE, intention.phase)
        val sessions = store.snapshot().sessions.filter { it.intentionId == intention.id }
        val projection = ShapeProjection.project(
            intention = intention,
            sessions = sessions,
            destination = SessionDestinations.forIntention(intention)
        )

        assertEquals("Your approach", projection.approachSectionTitle)
        assertEquals(2, projection.approachSteps.size)
        assertTrue(projection.approachSteps.first().title.contains("Spanish", ignoreCase = true))
        assertNotNull(projection.resourceLine)
        assertTrue(projection.resourceLine!!.contains("Duolingo"))
        assertTrue(projection.resourceLine!!.contains("not lesson completion"))
        assertNull(projection.emptyNote)
        assertEquals("Speaking practice in motion", projection.homeSpaceSubtitle)
        assertEquals("Your next session", projection.conversationHeadline)
    }

    @Test
    fun activeMarathon_hasTrainingApproach_withoutDuolingo() {
        MarathonScenario.seed(store, now)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", now + 1)
        MarathonScenario.approve(store, now + 2)
        val intention = store.snapshot().intentions.first { it.id == MarathonScenario.INTENTION_ID }
        val sessions = store.snapshot().sessions.filter { it.intentionId == intention.id }
        val projection = ShapeProjection.project(intention, sessions = sessions)

        assertEquals("Your training approach", projection.approachSectionTitle)
        assertTrue(projection.approachSteps.first().title.contains("long run", ignoreCase = true))
        assertFalse(projection.resourceLine.orEmpty().contains("Duolingo"))
        assertTrue(projection.resourceLine.orEmpty().contains("no partner completion"))
        assertEquals("Prepare for your next long run", projection.hero.heroTitle)
    }

    @Test
    fun proposalPending_usesDecisionCopy_andChangedSection() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 1)
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val projection = ShapeProjection.project(intention)

        assertEquals(IntentionPhase.PROPOSAL_PENDING, intention.phase)
        assertEquals("Add your session?", projection.hero.heroTitle)
        assertEquals("What has changed", projection.knowledgeSectionTitle)
        assertEquals("Your approach is taking shape", projection.homeSpaceSubtitle)
        assertTrue(projection.approachSteps.isEmpty())
    }

    @Test
    fun defaultFacts_areShapeSpecific() {
        val language = ShapeProjection.project(
            Intention(
                id = "x",
                title = "T",
                statement = "S",
                phase = IntentionPhase.EXPLORING,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                shapeTag = "language"
            )
        )
        assertTrue(language.defaultFacts.any { it.contains("Spanish", ignoreCase = true) })

        val marathon = ShapeProjection.project(
            Intention(
                id = "y",
                title = "T",
                statement = "S",
                phase = IntentionPhase.EXPLORING,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                shapeTag = "marathon"
            )
        )
        assertTrue(marathon.defaultFacts.any { it.contains("marathon", ignoreCase = true) })

        val move = ShapeProjection.project(
            Intention(
                id = "z-move",
                title = "T",
                statement = "S",
                phase = IntentionPhase.EXPLORING,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                shapeTag = "move"
            )
        )
        assertTrue(move.defaultFacts.any { it.contains("Valencia", ignoreCase = true) })
        assertTrue(move.defaultFacts.any { it.contains("six weeks", ignoreCase = true) })

        val decision = ShapeProjection.project(
            Intention(
                id = "z-decision",
                title = "T",
                statement = "S",
                phase = IntentionPhase.EXPLORING,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                shapeTag = "decision"
            )
        )
        assertTrue(decision.defaultFacts.any { it.contains("job offer", ignoreCase = true) })
        assertTrue(decision.defaultFacts.any { it.contains("does not choose", ignoreCase = true) })

        val appUseFacts = ShapeProjection.project(
            Intention(
                id = "z-app",
                title = "T",
                statement = "S",
                phase = IntentionPhase.EXPLORING,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                shapeTag = "app_use"
            )
        )
        assertTrue(appUseFacts.defaultFacts.any { it.contains("45") })
        assertTrue(appUseFacts.defaultFacts.any { it.contains("Shorts", ignoreCase = true) })
        val researchFacts = ShapeProjection.project(
            Intention(
                id = "r",
                title = "T",
                statement = "S",
                phase = IntentionPhase.EXPLORING,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
                shapeTag = "research"
            )
        )
        assertTrue(researchFacts.defaultFacts.any { it.contains("work legally", ignoreCase = true) })
        assertTrue(researchFacts.defaultFacts.any { it.contains("invented law", ignoreCase = true) })
    }

    @Test
    fun activeDecision_showsReviewApproach_withoutAcceptDecline() {
        JobOfferScenario.seed(store, now)
        JobOfferScenario.onChip(store, "Schedule a decision review before the deadline", now + 1)
        JobOfferScenario.approve(store, now + 2)
        val intention = store.snapshot().intentions.first { it.id == JobOfferScenario.INTENTION_ID }
        val sessions = store.snapshot().sessions.filter { it.intentionId == intention.id }
        val projection = ShapeProjection.project(intention, sessions = sessions)

        assertEquals("Your decision support", projection.approachSectionTitle)
        assertTrue(projection.approachSteps.first().title.contains("Decision review", ignoreCase = true))
        assertTrue(projection.approachSteps.any { it.title.contains("You choose accept or decline") })
        assertTrue(projection.resourceLine.orEmpty().contains("never accepts or declines"))
        assertEquals("Prepare your decision review", projection.hero.heroTitle)
        assertEquals("Your comparison matrix", projection.knowledgeSectionTitle)
    }
}
