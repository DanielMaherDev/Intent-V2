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
}
