package com.intent.launcher.domain

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
        assertTrue(spanish.emptyNote!!.contains("No tasks have been invented"))
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
    }
}
