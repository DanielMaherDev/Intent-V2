package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * IV-263 — Message composer timed activity must SaveFact / CorrectFact even when
 * a ONE QUESTION adaptive Ask is pending (live Discuss must not win either).
 */
class DiscussFreeTextRouterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L
    private val tz = AdaptiveBuilderFixtures.utc()
    private lateinit var intentionId: String

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("discuss-freetext-router.json"))
        intentionId = AdaptiveBuilderFixtures.seedOtherCase(store, "marathon", now)
        projectPracticeAsk()
    }

    private fun projectPracticeAsk() {
        val rev = store.snapshot().adaptiveBuilderStates
            .first { it.intentionId == intentionId }.revision
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.Ask(
                    intentionId = intentionId,
                    expectedRevision = rev,
                    questionId = "q-practice-pending",
                    whyItMatters = "Optional practice habit detail",
                    answerSchema = AnswerSchema.SINGLE_CHOICE,
                    purpose = "practice_habit",
                    options = listOf("Long run done (self-reported)", "Skip for now")
                ),
                now + 1,
                timeZone = tz
            ).accepted
        )
        assertEquals(
            "q-practice-pending",
            store.snapshot().adaptiveBuilderStates
                .first { it.intentionId == intentionId }.currentQuestionId
        )
        assertEquals(
            OpenQuestionState.ASKED,
            store.snapshot().openQuestions.first { it.id == "q-practice-pending" }.state
        )
    }

    @Test
    fun pendingAsk_messageActivityReport_routesToSaveFact_notAsk() {
        val utterance = "I ran for 20 minutes today"
        val route = DiscussFreeTextRouter.route(
            createActive = false,
            intentionId = intentionId,
            text = utterance,
            store = store,
            hasActiveAdaptiveQuestion = true
        )
        assertEquals(DiscussFreeTextRouter.Route.ACTIVITY_REPORT, route)

        val save = AdaptiveBuilderEngine.recordOrCorrectActivityReport(
            store = store,
            intentionId = intentionId,
            utterance = utterance,
            nowEpochMs = now + 10,
            timeZone = tz
        )
        assertTrue(save.reason, save.accepted)
        val active = store.snapshot().facts.single {
            it.intentionId == intentionId &&
                it.key == AdaptiveBuilderEngine.KEY_ACTIVITY_REPORT &&
                it.status == FactStatus.ACTIVE
        }
        assertEquals("20 minutes", active.text)
        // Pending Ask stays — activity path must not answer the practice chip.
        assertEquals(
            "q-practice-pending",
            store.snapshot().adaptiveBuilderStates
                .first { it.intentionId == intentionId }.currentQuestionId
        )
        assertEquals(
            OpenQuestionState.ASKED,
            store.snapshot().openQuestions.first { it.id == "q-practice-pending" }.state
        )
    }

    @Test
    fun pendingAsk_messageCorrectFact_updatesActivity_notAsk() {
        assertTrue(
            AdaptiveBuilderEngine.recordOrCorrectActivityReport(
                store = store,
                intentionId = intentionId,
                utterance = "I ran for 20 minutes today",
                nowEpochMs = now + 10,
                timeZone = tz
            ).accepted
        )
        val prior = store.snapshot().facts.single {
            it.intentionId == intentionId &&
                it.key == AdaptiveBuilderEngine.KEY_ACTIVITY_REPORT &&
                it.status == FactStatus.ACTIVE
        }

        val correction = "Actually 25 minutes"
        val route = DiscussFreeTextRouter.route(
            createActive = false,
            intentionId = intentionId,
            text = correction,
            store = store,
            hasActiveAdaptiveQuestion = true
        )
        assertEquals(DiscussFreeTextRouter.Route.ACTIVITY_REPORT, route)

        val correct = AdaptiveBuilderEngine.recordOrCorrectActivityReport(
            store = store,
            intentionId = intentionId,
            utterance = correction,
            nowEpochMs = now + 20,
            timeZone = tz
        )
        assertTrue(correct.reason, correct.accepted)
        val reports = store.snapshot().facts.filter {
            it.intentionId == intentionId && it.key == AdaptiveBuilderEngine.KEY_ACTIVITY_REPORT
        }
        assertEquals(2, reports.size)
        assertEquals(FactStatus.CORRECTED, reports.first { it.id == prior.id }.status)
        assertEquals(
            "25 minutes",
            reports.first { it.status == FactStatus.ACTIVE }.text
        )
        assertEquals(
            OpenQuestionState.ASKED,
            store.snapshot().openQuestions.first { it.id == "q-practice-pending" }.state
        )
    }

    @Test
    fun pendingAsk_nonActivityFreeText_stillRoutesToAsk() {
        val route = DiscussFreeTextRouter.route(
            createActive = false,
            intentionId = intentionId,
            text = "Long run done (self-reported)",
            store = store,
            hasActiveAdaptiveQuestion = true
        )
        assertEquals(DiscussFreeTextRouter.Route.ADAPTIVE_ASK, route)
    }

    @Test
    fun noAsk_activityReport_stillRoutesToActivity() {
        // Clear pending Ask so Discuss would otherwise win without the activity path.
        val state = store.snapshot().adaptiveBuilderStates.first { it.intentionId == intentionId }
        store.apply(
            com.intent.launcher.domain.FlowCommand.SetAdaptiveBuilderState(
                state = state.copy(currentQuestionId = null),
                nowEpochMs = now + 2
            )
        )
        assertEquals(
            DiscussFreeTextRouter.Route.ACTIVITY_REPORT,
            DiscussFreeTextRouter.route(
                createActive = false,
                intentionId = intentionId,
                text = "I walked for 15 minutes today",
                store = store,
                hasActiveAdaptiveQuestion = false
            )
        )
        assertNotNull(
            AdaptiveBuilderEngine.parseActivityDurationMinutes("I walked for 15 minutes today")
        )
    }
}
