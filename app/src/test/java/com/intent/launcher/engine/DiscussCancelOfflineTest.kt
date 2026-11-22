package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.TimeZone

/**
 * §8.9 — Cancel while pending + late response ignored, without live OpenAI.
 *
 * Complements AdaptiveBuilderAcceptanceTest.scenario9 (decision/fact writes) with
 * Discuss free-text + [DiscussOfflineHold] engineering path used on AVD.
 */
class DiscussCancelOfflineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_200_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow-s89.json"))
        SpanishScenario.seed(store, now)
        DiscussOfflineHold.clear()
    }

    @Test
    fun offlineHold_defaultOff() {
        assertEquals(0L, DiscussOfflineHold.currentHoldMs())
        assertFalse(DiscussOfflineHold.isArmed())
        DiscussOfflineHold.applyFromIntentExtra(20_000L)
        assertTrue(DiscussOfflineHold.isArmed())
        assertEquals(20_000L, DiscussOfflineHold.currentHoldMs())
        DiscussOfflineHold.clear()
        assertFalse(DiscussOfflineHold.isArmed())
    }

    @Test
    fun cancelWhilePending_noDiscussWrite() {
        val thinkingTurnId = "turn-discuss-thinking-pending"
        val intentionId = SpanishScenario.INTENTION_ID
        val turnsBefore = store.snapshot().turns.size

        CommandGateway.cancelTurn(
            store = store,
            turnId = thinkingTurnId,
            intentionId = intentionId,
            nowEpochMs = now + 1,
            reason = "Stopped waiting for the model. Earlier answers stay as they were."
        )
        assertTrue(AgentTurnLifecycle.isCancelled(store.snapshot(), thinkingTurnId))

        // Simulate Cancel before Discuss work runs (AVD hold path): no submitFreeText.
        assertEquals(turnsBefore + 1, store.snapshot().turns.size) // cancel marker only
        assertTrue(
            store.snapshot().turns.none {
                it.role == "user" && it.text.contains("should not land", ignoreCase = true)
            }
        )
        assertTrue(
            store.snapshot().facts.none { it.id == "fact-from-cancelled-discuss" }
        )
    }

    @Test
    fun lateResponseAfterCancel_ignored_noAgentWrite() {
        val thinkingTurnId = "turn-discuss-thinking-late"
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val access = FakeLiveLlmAccess(
            keyConfigured = true,
            optedIn = true,
            clientEnabled = true,
            apiKey = "sk-test-not-real"
        )
        val registry = LiveLlmProbe.liveRegistry(access)
        var httpCalls = 0
        val lateBody = """
            {"choices":[{"message":{"content":"LATE STALE REPLY that must not land"}}]}
        """.trimIndent()
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { _, _, _ ->
                httpCalls++
                // Cancel lands while the "network" is in flight.
                CommandGateway.cancelTurn(
                    store = store,
                    turnId = thinkingTurnId,
                    intentionId = SpanishScenario.INTENTION_ID,
                    nowEpochMs = now + 2,
                    reason = "Stopped waiting for the model."
                )
                OpenAiHttpResponse(200, lateBody)
            }
        )

        val result = ConversationCoordinator.submitFreeText(
            store = store,
            intention = intention,
            text = "What should I practice next?",
            nowEpochMs = now + 10,
            registry = registry,
            access = access,
            client = client,
            thinkingTurnId = thinkingTurnId
        )

        assertEquals(1, httpCalls)
        assertTrue(AgentTurnLifecycle.isCancelled(store.snapshot(), thinkingTurnId))
        val note = result.honestyNote.orEmpty()
        assertTrue(note.contains("Cancelled", ignoreCase = true))
        assertTrue(note.contains("ignored", ignoreCase = true))
        assertFalse(result.liveCallAttempted)
        assertTrue(
            store.snapshot().turns.none {
                it.role == "agent" && it.text.contains("LATE STALE REPLY")
            }
        )
        // User utterance may already be stored; agent late reply must not.
        assertTrue(
            store.snapshot().turns.any {
                it.role == "user" && it.text.contains("practice next", ignoreCase = true)
            }
        )
    }

    @Test
    fun lateBuilderDecisionAfterCancel_cannotReplaceActiveCard() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        val thinkingTurn = "turn-thinking-card"
        CommandGateway.cancelTurn(
            store = store,
            turnId = thinkingTurn,
            intentionId = id,
            nowEpochMs = now + 1
        )

        // Establish a later active Ask card.
        val ask = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = 0L,
                questionId = "q-active-now",
                whyItMatters = "Need language",
                answerSchema = com.intent.launcher.domain.AnswerSchema.FREE_TEXT,
                purpose = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE
            ),
            now + 3,
            timeZone = TimeZone.getTimeZone("UTC")
        )
        assertTrue(ask.accepted)
        val activeQ = store.snapshot().adaptiveBuilderStates
            .first { it.intentionId == id }.currentQuestionId
        assertEquals("q-active-now", activeQ)

        val late = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = AdaptiveBuilderEngine.projectContext(
                    store.snapshot(), id, now + 5, TimeZone.getTimeZone("UTC")
                )!!.revision,
                questionId = "q-stale-from-cancelled",
                whyItMatters = "Old model reply",
                answerSchema = com.intent.launcher.domain.AnswerSchema.FREE_TEXT,
                purpose = "stale",
                sourceTurnId = thinkingTurn
            ),
            now + 5,
            cancelledTurnId = thinkingTurn,
            timeZone = TimeZone.getTimeZone("UTC")
        )
        assertFalse(late.accepted)
        assertEquals(
            "q-active-now",
            store.snapshot().adaptiveBuilderStates.first { it.intentionId == id }.currentQuestionId
        )

        val lateFact = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = AdaptiveBuilderEngine.projectContext(
                    store.snapshot(), id, now + 6, TimeZone.getTimeZone("UTC")
                )!!.revision,
                factDraft = FactDraft(factId = "fact-stale-card", text = "must not land"),
                sourceTurnId = thinkingTurn
            ),
            now + 6,
            cancelledTurnId = thinkingTurn,
            timeZone = TimeZone.getTimeZone("UTC")
        )
        assertFalse(lateFact.accepted)
        assertTrue(store.snapshot().facts.none { it.id == "fact-stale-card" })
    }
}
