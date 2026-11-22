package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AdaptiveAnswerPathTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L
    private val tz = AdaptiveBuilderFixtures.utc()
    private val id = AdaptiveBuilderFixtures.SPANISH_ID

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("answer-path.json"))
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
    }

    @Test
    fun staleTap_returnsRefreshMessage_withoutMutating() {
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = 0L,
                questionId = "q-active",
                whyItMatters = "Need language",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                options = listOf("Spanish", "French")
            ),
            now,
            timeZone = tz
        )
        val stale = AdaptiveAnswerPath.submit(
            store = store,
            intentionId = id,
            rawValue = "Spanish",
            modality = "TEXT_CHIP",
            nowEpochMs = now + 1,
            timeZone = tz,
            expectedQuestionId = "q-old-card"
        )
        assertTrue(stale.ok)
        assertFalse(stale.accepted)
        assertTrue(stale.honestyNote.contains("no longer active", ignoreCase = true))
        assertEquals(
            OpenQuestionState.ASKED,
            store.snapshot().openQuestions.first { it.id == "q-active" }.state
        )
        assertTrue(
            AdaptiveBuilderEngine.activeFacts(store.snapshot(), id)
                .none { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }
        )
    }

    @Test
    fun skipForNow_defersWithoutLosingConversation() {
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = 0L,
                questionId = "q-skip",
                whyItMatters = "Optional detail",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = "optional_detail",
                options = listOf("A", "B")
            ),
            now,
            timeZone = tz
        )
        val result = AdaptiveAnswerPath.submit(
            store = store,
            intentionId = id,
            rawValue = ChoiceOptions.SKIP_FOR_NOW,
            modality = "TEXT_CHIP",
            nowEpochMs = now + 2,
            timeZone = tz,
            expectedQuestionId = "q-skip"
        )
        assertTrue(result.accepted)
        assertEquals(
            OpenQuestionState.DEFERRED,
            store.snapshot().openQuestions.first { it.id == "q-skip" }.state
        )
        assertEquals(
            null,
            store.snapshot().adaptiveBuilderStates.first { it.intentionId == id }.currentQuestionId
        )
        assertTrue(
            store.snapshot().turns.any {
                it.role == "agent" && it.text.contains("come back", ignoreCase = true)
            }
        )
    }

    @Test
    fun activeBuilderBlock_matchesCurrentQuestionOnly() {
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = 0L,
                questionId = "q-block",
                whyItMatters = "why",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE
            ),
            now,
            timeZone = tz
        )
        val block = AdaptiveAnswerPath.activeBuilderBlock(store, id)
        assertNotNull(block)
        assertTrue(BuilderAnswerCard.isBuilderCard(block!!))
        assertEquals("q-block", BuilderAnswerCard.decode(block)?.promptId)
    }

    @Test
    fun chipTypedAndSpeech_sameApproachAsk_writeEquivalentSelectedApproaches() {
        fun seed(target: IntentionFlowStore, qid: String) {
            AdaptiveBuilderFixtures.seedSpanishDiscovery(target, now)
            AdaptiveBuilderEngine.applyDecision(
                target,
                BuilderDecision.OfferApproaches(
                    intentionId = id,
                    expectedRevision = 0L,
                    approachDrafts = AdaptiveBuilderEngine.defaultApproachDrafts()
                ),
                now,
                timeZone = tz
            )
            val rev = target.snapshot().adaptiveBuilderStates.first { it.intentionId == id }.revision
            AdaptiveBuilderEngine.applyDecision(
                target,
                BuilderDecision.Ask(
                    intentionId = id,
                    expectedRevision = rev,
                    questionId = qid,
                    whyItMatters = "Which ways of learning appeal to you?",
                    answerSchema = AnswerSchema.MULTI_CHOICE,
                    purpose = AdaptiveBuilderEngine.KEY_SELECTED_APPROACHES,
                    options = AdaptiveBuilderEngine.defaultApproachDrafts().map { it.title }
                ),
                now + 1,
                timeZone = tz
            )
        }

        fun selected(target: IntentionFlowStore): Set<String> =
            target.snapshot().approaches
                .filter {
                    it.intentionId == id &&
                        it.status == com.intent.launcher.domain.ApproachStatus.SELECTED
                }
                .map { it.id }
                .toSet()

        fun approachesFact(target: IntentionFlowStore): String =
            AdaptiveBuilderEngine.activeFacts(target.snapshot(), id)
                .first { it.key == AdaptiveBuilderEngine.KEY_SELECTED_APPROACHES }.text

        val value = "AI conversation practice and Learning app"
        seed(store, "q-chip")
        assertTrue(
            AdaptiveAnswerPath.submit(
                store = store,
                intentionId = id,
                rawValue = value,
                modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
                nowEpochMs = now + 5,
                timeZone = tz,
                expectedQuestionId = "q-chip"
            ).accepted
        )

        val typedStore = IntentionFlowStore(tmp.newFile("answer-path-typed.json"))
        seed(typedStore, "q-typed")
        assertTrue(
            AdaptiveAnswerPath.submit(
                store = typedStore,
                intentionId = id,
                rawValue = value,
                modality = ConversationCoordinator.Channel.FREE_TEXT.name,
                nowEpochMs = now + 6,
                timeZone = tz,
                expectedQuestionId = "q-typed"
            ).accepted
        )

        val speechStore = IntentionFlowStore(tmp.newFile("answer-path-speech.json"))
        seed(speechStore, "q-speech")
        assertTrue(
            AdaptiveAnswerPath.submitFromVoice(
                store = speechStore,
                intentionId = id,
                transcript = value,
                nowEpochMs = now + 7,
                timeZone = tz,
                expectedQuestionId = "q-speech"
            ).accepted
        )

        val expected = setOf("approach-ai-practice", "approach-learning-app")
        assertEquals(expected, selected(store))
        assertEquals(expected, selected(typedStore))
        assertEquals(expected, selected(speechStore))
        assertEquals(approachesFact(store), approachesFact(typedStore))
        assertEquals(approachesFact(store), approachesFact(speechStore))
    }
}