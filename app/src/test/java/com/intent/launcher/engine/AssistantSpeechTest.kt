package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AssistantSpeechTest {

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(File.createTempFile("assistant-speech", ".json"))
        MarathonScenario.seed(store, now)
    }

    @Test
    fun latestSpeakable_prefersVoiceTtsModality() {
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-agent-text",
                intentionId = MarathonScenario.INTENTION_ID,
                role = "agent",
                text = "Earlier text reply",
                nowEpochMs = now + 1,
                modality = "TEXT"
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-agent-tts",
                intentionId = MarathonScenario.INTENTION_ID,
                role = "agent",
                text = "I was recommending a long tempo run…",
                nowEpochMs = now + 2,
                modality = "VOICE_TTS"
            )
        )
        val speakable = AssistantSpeech.latestSpeakableAgentTurn(
            store.snapshot(),
            MarathonScenario.INTENTION_ID
        )
        assertNotNull(speakable)
        assertEquals("turn-agent-tts", speakable!!.turnId)
        assertEquals("VOICE_TTS", speakable.modality)
    }

    @Test
    fun latestSpeakable_skipsCancelledVoiceTts_prefersEarlierUncancelled() {
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-agent-speaking",
                intentionId = MarathonScenario.INTENTION_ID,
                role = "agent",
                text = "Cancel me",
                nowEpochMs = now + 9_000,
                modality = "VOICE_TTS"
            )
        )
        CommandGateway.cancelTurn(
            store = store,
            turnId = "turn-agent-speaking",
            intentionId = MarathonScenario.INTENTION_ID,
            nowEpochMs = now + 9_001
        )
        val speakable = AssistantSpeech.latestSpeakableAgentTurn(
            store.snapshot(),
            MarathonScenario.INTENTION_ID
        )
        assertNotNull(speakable)
        assertTrue(speakable!!.turnId != "turn-agent-speaking")
        assertFalse(AgentTurnLifecycle.isCancelled(store.snapshot(), speakable.turnId))
    }

    @Test
    fun bargeInFromSpeakingSurface_ordersUserTurn_andRefusesLateDuplicate() {
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-agent-live",
                intentionId = MarathonScenario.INTENTION_ID,
                role = "agent",
                text = "Speaking this aloud now…",
                nowEpochMs = now + 9_000,
                modality = "VOICE_TTS"
            )
        )
        val speakable = AssistantSpeech.latestSpeakableAgentTurn(
            store.snapshot(),
            MarathonScenario.INTENTION_ID
        )!!
        assertEquals("turn-agent-live", speakable.turnId)
        val tts = FakeTextToSpeechAccess(available = true)
        assertTrue(tts.speak(speakable.text, speakable.turnId))
        assertTrue(tts.isSpeaking())

        val result = InterruptedVoice.bargeIn(
            store = store,
            intentionId = MarathonScenario.INTENTION_ID,
            speakingTurnId = speakable.turnId,
            userTranscript = AssistantSpeech.STOP_TRANSCRIPT,
            nowEpochMs = now + 9_003
        )
        tts.stop()

        assertEquals(speakable.turnId, result.cancelledTurnId)
        assertTrue(result.lateDuplicateRefused)
        assertTrue(AgentTurnLifecycle.isCancelled(store.snapshot(), speakable.turnId))
        assertFalse(tts.isSpeaking())
        assertTrue(tts.stopCalls >= 1)
        val after = AssistantSpeech.latestSpeakableAgentTurn(
            store.snapshot(),
            MarathonScenario.INTENTION_ID
        )
        if (after != null) {
            assertTrue(after.turnId != speakable.turnId)
        }
        val userTurn = store.snapshot().turns.first { it.id == result.newUserTurnId }
        assertEquals(AssistantSpeech.STOP_TRANSCRIPT, userTurn.text)
        assertTrue(
            userTurn.blocks.any { it.sourceLabel == "interrupted_voice" }
        )
    }

    @Test
    fun fakeTtsUnavailable_stillAllowsInterruptPath() {
        val tts = FakeTextToSpeechAccess(available = false)
        assertFalse(tts.speak("hello", "u1"))
        assertFalse(tts.isSpeaking())
        assertTrue(AssistantSpeech.HONESTY_NOTE.contains("Not OEM silent barge-in"))
    }
}
