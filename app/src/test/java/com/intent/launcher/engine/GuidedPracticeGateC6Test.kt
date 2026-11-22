package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.ApproachActor
import com.intent.launcher.domain.ApproachDraft
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gate C6 — Spanish guided-practice mode via the central agent
 * ([ConversationCoordinator] / shared [IntentionFlowStore] history).
 *
 * Proves: one Conversation gateway, shared Spanish history, no second bot,
 * no fabricated lesson completion. Does not invent STT or live Discuss.
 */
class GuidedPracticeGateC6Test {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var storeFile: File
    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_200_000L
    private val tz = AdaptiveBuilderFixtures.utc()

    @Before
    fun setUp() {
        storeFile = tmp.newFile("gate-c6-guided.json")
        store = IntentionFlowStore(storeFile)
    }

    @Test
    fun coordinatorFreeText_startsGuidedSession_onSpanishIntention_sharedHistory() {
        SpanishScenario.seed(store, now)
        store.apply(
            com.intent.launcher.domain.FlowCommand.AddConversationTurn(
                turnId = "turn-prior-discovery",
                intentionId = SpanishScenario.INTENTION_ID,
                role = "user",
                text = "I want everyday conversations for a move to Spain.",
                nowEpochMs = now + 1
            )
        )
        val intention = store.snapshot().intentions.first {
            it.id == SpanishScenario.INTENTION_ID
        }
        val utterance = "Let's practise a customer call in Spanish."
        var httpCalls = 0
        val client = OpenAiDiscussClient(
            transporter = OpenAiHttpTransporter { _, _, _ ->
                httpCalls++
                OpenAiHttpResponse(200, "{}")
            }
        )

        val result = ConversationCoordinator.submitFreeText(
            store = store,
            intention = intention,
            text = utterance,
            nowEpochMs = now + 10,
            registry = CapabilityRegistry.default,
            access = FakeLiveLlmAccess(),
            client = client
        )

        assertEquals(ConversationCoordinator.Channel.FREE_TEXT, result.channel)
        assertEquals(utterance, result.appliedChip)
        assertEquals(utterance, result.storedTranscript)
        assertFalse(result.liveCallAttempted)
        assertEquals(0, httpCalls)
        assertTrue(
            result.honestyNote!!.contains("Spanish intention", ignoreCase = true) ||
                result.honestyNote!!.contains("Guided session", ignoreCase = true)
        )

        val snap = store.snapshot()
        assertEquals(SpanishScenario.INTENTION_ID, snap.activeIntentionId)

        // Prior discovery turn still on the same intention (shared history).
        assertTrue(
            snap.turns.any {
                it.id == "turn-prior-discovery" &&
                    it.intentionId == SpanishScenario.INTENTION_ID
            }
        )

        val guidedTurns = snap.turns.filter {
            it.intentionId == SpanishScenario.INTENTION_ID &&
                (it.id.startsWith("turn-guided-") || it.modality == "GUIDED_PRACTICE")
        }
        assertTrue(guidedTurns.size >= 3)
        assertTrue(guidedTurns.all { it.intentionId == SpanishScenario.INTENTION_ID })

        assertTrue(
            snap.facts.any {
                it.id == GuidedPractice.ROLE_FACT_ID &&
                    it.intentionId == SpanishScenario.INTENTION_ID &&
                    it.text.contains(GuidedPractice.DEFAULT_ROLE)
            }
        )
        assertTrue(
            snap.facts.any {
                it.id == GuidedPractice.OBJECTIVE_FACT_ID &&
                    it.intentionId == SpanishScenario.INTENTION_ID &&
                    it.text.contains(GuidedPractice.DEFAULT_OBJECTIVE)
            }
        )

        val blocks = snap.turns.flatMap { it.blocks }
        assertTrue(
            blocks.any {
                it.type == ResponseBlockType.EXPLANATION &&
                    it.sourceLabel == "guided_practice" &&
                    it.linkedObjectIds.contains(SpanishScenario.INTENTION_ID) &&
                    it.linkedObjectIds.contains(GuidedPractice.SESSION_MARKER_ID)
            }
        )
        assertTrue(
            blocks.any {
                it.type == ResponseBlockType.PROGRESS &&
                    it.sourceLabel == "guided_practice_feedback" &&
                    it.linkedObjectIds.contains(SpanishScenario.INTENTION_ID)
            }
        )

        // No second bot / specialist intention.
        assertTrue(
            snap.intentions.none {
                it.id != SpanishScenario.INTENTION_ID &&
                    (it.title.contains("customer call", ignoreCase = true) ||
                        it.title.contains("guided", ignoreCase = true) ||
                        it.title.contains("tutor bot", ignoreCase = true))
            }
        )
        assertFalse(
            snap.turns.any {
                it.text.contains("lesson was completed", ignoreCase = true) ||
                    it.text.contains("lesson completed", ignoreCase = true) ||
                    it.text.contains("fabricated lesson", ignoreCase = true)
            }
        )
        assertFalse(
            snap.facts.any {
                it.text.contains("lesson completed", ignoreCase = true)
            }
        )
    }

    @Test
    fun adaptiveApproach_aiPractice_isIntentActor_sharedHistoryNote_noLessonClaim() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        var t = now

        fun rev(): Long =
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision

        // Non-approximate move estimate so suggestNextDecision reaches OfferApproaches
        // (approximate facts always trip freshness reconfirm Ask first).
        listOf(
            Triple("fact-lang-c6", AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE, "Spanish"),
            Triple("fact-mot-c6", AdaptiveBuilderEngine.KEY_MOTIVATION, "Moving to Spain"),
            Triple("fact-move-c6", AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE, "June 2027")
        ).forEach { (factId, key, text) ->
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = id,
                    expectedRevision = rev(),
                    factDraft = FactDraft(factId = factId, text = text, key = key),
                    sourceTurnId = "turn-$factId"
                ),
                t,
                timeZone = tz
            )
            t += 2
        }

        val offerDecision = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!,
            t,
            tz
        )
        assertTrue(offerDecision is BuilderDecision.OfferApproaches)
        val offer = offerDecision as BuilderDecision.OfferApproaches
        val guided = offer.approachDrafts.first { it.approachId == "approach-ai-practice" }
        assertEquals(ApproachActor.INTENT, guided.actor)
        assertTrue(guided.capabilityNote.contains("shared agent history", ignoreCase = true))
        assertTrue(
            offer.approachDrafts.any {
                it.approachId == "approach-learning-app" &&
                    it.capabilityNote.contains("not lesson completion", ignoreCase = true)
            }
        )

        AdaptiveBuilderEngine.applyDecision(store, offer, t, timeZone = tz)
        t += 2
        AdaptiveBuilderEngine.selectApproaches(
            store,
            id,
            listOf("approach-ai-practice"),
            t,
            "turn-select-c6",
            rev(),
            "FREE_TEXT"
        )
        t += 2
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-freq-c6",
                    text = "3 days per week",
                    key = AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY
                ),
                sourceTurnId = "turn-freq-c6"
            ),
            t,
            timeZone = tz
        )
        t += 2

        val propose = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!,
            t,
            tz
        )
        assertTrue(propose is BuilderDecision.Propose)
        AdaptiveBuilderEngine.applyDecision(store, propose, t, timeZone = tz)

        val snap = store.snapshot()
        assertTrue(
            snap.approaches.any {
                it.id == "approach-ai-practice" &&
                    it.intentionId == id &&
                    it.status == ApproachStatus.SELECTED &&
                    it.actor == ApproachActor.INTENT
            }
        )
        val pending = snap.proposals.first { it.intentionId == id }
        assertTrue(
            pending.capabilityHonesty.contains("guided practice", ignoreCase = true) ||
                pending.capabilityHonesty.contains("shared agent history", ignoreCase = true) ||
                pending.methodsSummary.contains("AI conversation practice", ignoreCase = true)
        )
        assertFalse(pending.capabilityHonesty.contains("lesson was completed", ignoreCase = true))
        assertFalse(pending.capabilityHonesty.contains("lesson completed", ignoreCase = true))
        assertTrue(snap.sessions.none { it.intentionId == id })
        // Selecting guided approach does not spawn a second intention/bot.
        assertEquals(1, snap.intentions.count { it.id == id })
        assertTrue(
            snap.intentions.none {
                it.id != id && it.title.contains("guided", ignoreCase = true)
            }
        )
    }

    @Test
    fun approachThenCoordinatorSession_sameGateway_noSecondBotOrLessonFabrication() {
        // Adaptive discovery selects INTENT guided approach on ab-spanish,
        // then central-agent GuidedPractice attaches to fixture Spanish intention
        // via ConversationCoordinator (shared store / command path).
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val abId = AdaptiveBuilderFixtures.SPANISH_ID
        var t = now
        fun rev(): Long =
            AdaptiveBuilderEngine.projectContext(store.snapshot(), abId, t, tz)!!.revision

        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.OfferApproaches(
                intentionId = abId,
                expectedRevision = rev(),
                approachDrafts = listOf(
                    ApproachDraft(
                        approachId = "approach-ai-practice",
                        title = "AI conversation practice",
                        summary = "Guided Spanish practice in INTENT.",
                        actor = ApproachActor.INTENT,
                        capabilityNote =
                            "Guided session objective + typed result; shared agent history."
                    )
                )
            ),
            t,
            timeZone = tz
        )
        t += 2
        AdaptiveBuilderEngine.selectApproaches(
            store, abId, listOf("approach-ai-practice"), t, "turn-sel", rev(), "TEXT_CHIP"
        )
        assertTrue(
            store.snapshot().approaches.any {
                it.id == "approach-ai-practice" && it.status == ApproachStatus.SELECTED
            }
        )

        SpanishScenario.seed(store, t + 5)
        val spanish = store.snapshot().intentions.first {
            it.id == SpanishScenario.INTENTION_ID
        }
        val utterance = "Let's practise a customer call in Spanish."
        val result = ConversationCoordinator.submitFreeText(
            store = store,
            intention = spanish,
            text = utterance,
            nowEpochMs = t + 10,
            registry = CapabilityRegistry.default
        )
        assertEquals(utterance, result.appliedChip)
        assertFalse(result.liveCallAttempted)

        val snap = store.snapshot()
        // Both adaptive approach selection and guided session live in one store.
        assertTrue(
            snap.approaches.any {
                it.intentionId == abId && it.id == "approach-ai-practice"
            }
        )
        assertTrue(
            snap.facts.filter {
                it.id == GuidedPractice.ROLE_FACT_ID || it.id == GuidedPractice.OBJECTIVE_FACT_ID
            }.all { it.intentionId == SpanishScenario.INTENTION_ID }
        )
        assertTrue(
            snap.turns.filter { it.modality == "GUIDED_PRACTICE" }
                .all { it.intentionId == SpanishScenario.INTENTION_ID }
        )
        assertTrue(
            snap.intentions.none {
                it.title.contains("customer call", ignoreCase = true) ||
                    it.title.contains("tutor bot", ignoreCase = true)
            }
        )
        assertFalse(
            snap.turns.any { it.text.contains("lesson was completed", ignoreCase = true) }
        )
        assertFalse(
            snap.facts.any { it.text.contains("lesson completed", ignoreCase = true) }
        )
    }
}
