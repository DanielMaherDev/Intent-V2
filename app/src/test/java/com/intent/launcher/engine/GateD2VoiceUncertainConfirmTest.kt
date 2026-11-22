package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.OpenQuestionState
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
 * Gate D2 — voice interpretation certainty before AnswerQuestion.
 * Uncertain STT → confirm card (no write); confirm Yes → same records as tap;
 * exact chip voice → write once.
 */
class GateD2VoiceUncertainConfirmTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_100_000L
    private val tz = AdaptiveBuilderFixtures.utc()
    private val id = AdaptiveBuilderFixtures.SPANISH_ID

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("gate-d2-voice.json"))
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
    }

    private fun seedLanguageAsk(target: IntentionFlowStore, questionId: String) {
        AdaptiveBuilderEngine.applyDecision(
            target,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = 0L,
                questionId = questionId,
                whyItMatters = "Target language changes tools.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                options = listOf("Spanish", "French", "Other")
            ),
            now,
            timeZone = tz
        )
    }

    private fun languageFact(target: IntentionFlowStore): String? =
        AdaptiveBuilderEngine.activeFacts(target.snapshot(), id)
            .firstOrNull { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }
            ?.text

    @Test
    fun classifyChipMatch_exactCertain_fuzzyAndMultiAmbiguous() {
        val chips = listOf("Spanish", "French", "Everyday conversations")
        assertEquals(
            ConversationCoordinator.ChipMatch.Exact("Spanish"),
            ConversationCoordinator.classifyChipMatch("spanish", chips)
        )
        val fuzzy = ConversationCoordinator.classifyChipMatch(
            "I'd like everyday conversations please",
            chips
        )
        assertTrue(fuzzy is ConversationCoordinator.ChipMatch.Ambiguous)
        assertEquals(
            listOf("Everyday conversations"),
            (fuzzy as ConversationCoordinator.ChipMatch.Ambiguous).candidates
        )
        val multi = ConversationCoordinator.classifyChipMatch(
            "practice",
            listOf("AI conversation practice", "Offline practice", "Learning app")
        )
        assertTrue(multi is ConversationCoordinator.ChipMatch.Ambiguous)
        assertTrue(
            (multi as ConversationCoordinator.ChipMatch.Ambiguous).candidates.size >= 2
        )
        assertEquals(
            ConversationCoordinator.ChipMatch.None,
            ConversationCoordinator.classifyChipMatch("hello world", chips)
        )
    }

    @Test
    fun uncertainFuzzyVoice_projectsConfirm_withoutWritingFact() {
        seedLanguageAsk(store, "q-fuzzy")
        val result = AdaptiveAnswerPath.submitFromVoice(
            store = store,
            intentionId = id,
            transcript = "I'd like spanish please",
            nowEpochMs = now + 10,
            timeZone = tz,
            expectedQuestionId = "q-fuzzy"
        )
        assertTrue(result.needsVoiceConfirm)
        assertFalse(result.accepted)
        assertNull(languageFact(store))
        assertEquals(
            OpenQuestionState.ASKED,
            store.snapshot().openQuestions.first { it.id == "q-fuzzy" }.state
        )
        val confirm = AdaptiveAnswerPath.pendingVoiceConfirmBlock(store, id)
        assertNotNull(confirm)
        assertEquals("Spanish", confirm!!.body)
        assertTrue(confirm.actions.contains(AdaptiveAnswerPath.VOICE_CONFIRM_YES))
        assertTrue(
            store.snapshot().turns.any {
                it.role == "agent" && it.text.contains("Spanish")
            }
        )
    }

    @Test
    fun uncertainMultiCandidate_projectsChoices_withoutWriting() {
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = 0L,
                questionId = "q-multi",
                whyItMatters = "Practice style",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = "practice_style",
                options = listOf(
                    "AI conversation practice",
                    "Offline practice",
                    "Learning app"
                )
            ),
            now,
            timeZone = tz
        )
        val result = AdaptiveAnswerPath.submitFromVoice(
            store = store,
            intentionId = id,
            transcript = "practice",
            nowEpochMs = now + 5,
            timeZone = tz,
            expectedQuestionId = "q-multi"
        )
        assertTrue(result.needsVoiceConfirm)
        assertFalse(result.accepted)
        val confirm = AdaptiveAnswerPath.pendingVoiceConfirmBlock(store, id)!!
        assertTrue(confirm.actions.contains("AI conversation practice"))
        assertTrue(confirm.actions.contains("Offline practice"))
        assertTrue(confirm.actions.contains(AdaptiveAnswerPath.VOICE_CONFIRM_NO))
        assertFalse(confirm.actions.contains(AdaptiveAnswerPath.VOICE_CONFIRM_YES))
        assertTrue(
            AdaptiveBuilderEngine.activeFacts(store.snapshot(), id)
                .none { it.key == "practice_style" }
        )
    }

    @Test
    fun confirmYes_writesSameFactAsTap() {
        seedLanguageAsk(store, "q-tap")
        assertTrue(
            AdaptiveAnswerPath.submit(
                store = store,
                intentionId = id,
                rawValue = "Spanish",
                modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
                nowEpochMs = now + 10,
                timeZone = tz,
                expectedQuestionId = "q-tap"
            ).accepted
        )
        val tapFact = languageFact(store)
        assertEquals("Spanish", tapFact)

        val voiceStore = IntentionFlowStore(tmp.newFile("gate-d2-confirm-yes.json"))
        AdaptiveBuilderFixtures.seedSpanishDiscovery(voiceStore, now)
        seedLanguageAsk(voiceStore, "q-voice-confirm")
        val unc = AdaptiveAnswerPath.submitFromVoice(
            store = voiceStore,
            intentionId = id,
            transcript = "span",
            nowEpochMs = now + 20,
            timeZone = tz,
            expectedQuestionId = "q-voice-confirm"
        )
        assertTrue(unc.needsVoiceConfirm)
        assertNull(languageFact(voiceStore))

        val confirmed = AdaptiveAnswerPath.submit(
            store = voiceStore,
            intentionId = id,
            rawValue = AdaptiveAnswerPath.VOICE_CONFIRM_YES,
            modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
            nowEpochMs = now + 30,
            timeZone = tz,
            expectedQuestionId = "q-voice-confirm"
        )
        assertTrue(confirmed.honestyNote, confirmed.accepted)
        assertEquals(tapFact, languageFact(voiceStore))
        assertEquals(
            OpenQuestionState.ANSWERED,
            voiceStore.snapshot().openQuestions.first { it.id == "q-voice-confirm" }.state
        )
        assertNull(AdaptiveAnswerPath.pendingVoiceConfirmBlock(voiceStore, id))
    }

    @Test
    fun certainExactVoice_writesOnce_noConfirm() {
        seedLanguageAsk(store, "q-exact")
        val result = AdaptiveAnswerPath.submitFromVoice(
            store = store,
            intentionId = id,
            transcript = "Spanish",
            nowEpochMs = now + 10,
            timeZone = tz,
            expectedQuestionId = "q-exact"
        )
        assertTrue(result.honestyNote, result.accepted)
        assertFalse(result.needsVoiceConfirm)
        assertEquals("Spanish", languageFact(store))
        assertNull(AdaptiveAnswerPath.pendingVoiceConfirmBlock(store, id))
        val answerTurns = store.snapshot().turns.count {
            it.role == "user" &&
                it.modality == ConversationCoordinator.Channel.VOICE_TRANSCRIPT.name &&
                it.text == "Spanish"
        }
        assertEquals(1, answerTurns)
    }

    @Test
    fun clearFreeTextVoice_writesWithoutConfirm() {
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = 0L,
                questionId = "q-free",
                whyItMatters = "Motivation",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = AdaptiveBuilderEngine.KEY_MOTIVATION
            ),
            now,
            timeZone = tz
        )
        val result = AdaptiveAnswerPath.submitFromVoice(
            store = store,
            intentionId = id,
            transcript = "Moving to Spain for work",
            nowEpochMs = now + 10,
            timeZone = tz,
            expectedQuestionId = "q-free"
        )
        assertTrue(result.accepted)
        assertFalse(result.needsVoiceConfirm)
        assertEquals(
            "Moving to Spain for work",
            AdaptiveBuilderEngine.activeFacts(store.snapshot(), id)
                .first { it.key == AdaptiveBuilderEngine.KEY_MOTIVATION }.text
        )
    }

    @Test
    fun confirmNo_discardsGuess_leavesQuestionOpen() {
        seedLanguageAsk(store, "q-no")
        assertTrue(
            AdaptiveAnswerPath.submitFromVoice(
                store = store,
                intentionId = id,
                transcript = "fren",
                nowEpochMs = now + 5,
                timeZone = tz,
                expectedQuestionId = "q-no"
            ).needsVoiceConfirm
        )
        val rejected = AdaptiveAnswerPath.submit(
            store = store,
            intentionId = id,
            rawValue = AdaptiveAnswerPath.VOICE_CONFIRM_NO,
            modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
            nowEpochMs = now + 15,
            timeZone = tz,
            expectedQuestionId = "q-no"
        )
        assertTrue(rejected.needsFreeText)
        assertFalse(rejected.accepted)
        assertNull(languageFact(store))
        assertEquals(
            OpenQuestionState.ASKED,
            store.snapshot().openQuestions.first { it.id == "q-no" }.state
        )
        assertNull(AdaptiveAnswerPath.pendingVoiceConfirmBlock(store, id))
    }
}
