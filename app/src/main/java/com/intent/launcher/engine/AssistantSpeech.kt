package com.intent.launcher.engine

import com.intent.launcher.domain.ConversationTurn
import com.intent.launcher.domain.IntentionSnapshot

/**
 * §17 case 34 — Conversation UI helpers for an honest interrupt / stop-speaking surface.
 *
 * Wires [InterruptedVoice] into the live composer. Does **not** invent OEM silent
 * barge-in detection or claim a live TTS engine when none is driving playback.
 */
object AssistantSpeech {

    const val HONESTY_NOTE =
        "Interrupt cancels the in-flight agent reply turn and orders your next turn. " +
            "Not OEM silent barge-in / acoustic TTS detection."

    const val SPEAKING_BANNER =
        "INTENT is speaking — tap Stop or speak/type to interrupt"

    const val STOP_TRANSCRIPT = "Stop — interrupt speaking"

    data class Speakable(
        val turnId: String,
        val text: String,
        val modality: String
    )

    /**
     * Latest non-cancelled agent turn for the intention — the reply the user may
     * interrupt. Prefers [modality] `VOICE_TTS` when present.
     */
    fun latestSpeakableAgentTurn(
        snapshot: IntentionSnapshot,
        intentionId: String
    ): Speakable? {
        val turns = snapshot.turns
            .asSequence()
            .filter { it.intentionId == intentionId && it.role.equals("agent", ignoreCase = true) }
            .filter { !AgentTurnLifecycle.isCancelled(snapshot, it.id) }
            .filter { it.text.isNotBlank() }
            .sortedBy { it.atEpochMs }
            .toList()
        if (turns.isEmpty()) return null
        val preferred = turns.lastOrNull {
            it.modality?.equals("VOICE_TTS", ignoreCase = true) == true
        } ?: turns.last()
        return Speakable(
            turnId = preferred.id,
            text = preferred.text,
            modality = preferred.modality.orEmpty()
        )
    }

    fun isSpeaking(speakingTurnId: String?): Boolean = !speakingTurnId.isNullOrBlank()
}
