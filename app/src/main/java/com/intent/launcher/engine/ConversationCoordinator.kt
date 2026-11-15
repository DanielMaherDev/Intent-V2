package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.BusinessScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.MoveScenario
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore

/**
 * Shared conversation command path for text chips and voice transcripts (tech-arch §8).
 * Voice does not invent LLM interpretation — chip match or honest text fallback only.
 */
object ConversationCoordinator {

    enum class Channel {
        TEXT_CHIP,
        VOICE_TRANSCRIPT
    }

    data class SubmitResult(
        val channel: Channel,
        /** Chip label applied through the shape script, if any. */
        val appliedChip: String?,
        /** Raw transcript stored when no chip matched. */
        val storedTranscript: String?,
        val honestyNote: String?
    )

    fun submitChip(
        store: IntentionFlowStore,
        intention: Intention?,
        chip: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): SubmitResult {
        dispatchChip(store, intention, chip, nowEpochMs, registry)
        return SubmitResult(
            channel = Channel.TEXT_CHIP,
            appliedChip = chip,
            storedTranscript = null,
            honestyNote = null
        )
    }

    /**
     * Voice → same command path as chips when the transcript matches a visible chip;
     * otherwise stores the transcript as a user turn with an honest non-LLM reply.
     */
    fun submitVoiceTranscript(
        store: IntentionFlowStore,
        intention: Intention?,
        availableChips: List<String>,
        transcript: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): SubmitResult {
        if (!registry.canUseVoiceInput()) {
            return SubmitResult(
                channel = Channel.VOICE_TRANSCRIPT,
                appliedChip = null,
                storedTranscript = null,
                honestyNote = registry.get(CapabilityRegistry.ID_VOICE_INPUT)?.limitNote
                    ?: "Voice input is not available — use the chips above."
            )
        }
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) {
            return SubmitResult(
                channel = Channel.VOICE_TRANSCRIPT,
                appliedChip = null,
                storedTranscript = null,
                honestyNote = "No speech recognized — try again or tap a chip."
            )
        }
        val matched = matchChip(trimmed, availableChips)
        if (matched != null) {
            dispatchChip(store, intention, matched, nowEpochMs, registry)
            return SubmitResult(
                channel = Channel.VOICE_TRANSCRIPT,
                appliedChip = matched,
                storedTranscript = trimmed,
                honestyNote = "Matched your spoken choice to a chip — same path as tapping it. " +
                    "No live LLM interpretation in this build."
            )
        }
        val intentionId = intention?.id
            ?: store.snapshot().activeIntentionId
            ?: return SubmitResult(
                channel = Channel.VOICE_TRANSCRIPT,
                appliedChip = null,
                storedTranscript = trimmed,
                honestyNote = "No active intention — open Conversation from Home first."
            )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-voice-user-$nowEpochMs",
                intentionId = intentionId,
                role = "user",
                text = trimmed,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-voice-agent-$nowEpochMs",
                intentionId = intentionId,
                role = "agent",
                text = "I stored that as text from platform speech recognition. " +
                    "This build has no live LLM — choose a chip above to change your intention.",
                nowEpochMs = nowEpochMs + 1
            )
        )
        return SubmitResult(
            channel = Channel.VOICE_TRANSCRIPT,
            appliedChip = null,
            storedTranscript = trimmed,
            honestyNote = "Transcript stored as text only — no LLM. Use chips for scripted actions."
        )
    }

    fun matchChip(transcript: String, chips: List<String>): String? {
        val normalized = transcript.trim().lowercase()
        chips.firstOrNull { it.equals(transcript.trim(), ignoreCase = true) }?.let { return it }
        return chips.firstOrNull { chip ->
            val c = chip.lowercase()
            normalized.contains(c) || c.contains(normalized)
        }
    }

    private fun dispatchChip(
        store: IntentionFlowStore,
        intention: Intention?,
        chip: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry
    ) {
        when (intention?.shapeTag) {
            "marathon" -> MarathonScenario.onChip(store, chip, nowEpochMs, registry)
            "business" -> BusinessScenario.onChip(store, chip, nowEpochMs, registry)
            "move" -> MoveScenario.onChip(store, chip, nowEpochMs, registry)
            else -> SpanishScenario.onChip(store, chip, nowEpochMs, registry)
        }
    }
}
