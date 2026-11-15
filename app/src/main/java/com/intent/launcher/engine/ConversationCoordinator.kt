package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AppUseReductionScenario
import com.intent.launcher.domain.BusinessScenario
import com.intent.launcher.domain.EuWorkSpainScenario
import com.intent.launcher.domain.ChessScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.JobOfferScenario
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.MoveScenario
import com.intent.launcher.domain.QuitSmokingScenario
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore

/**
 * Shared conversation command path for text chips, voice transcripts, and free-text
 * Discuss (tech-arch §8). Without live_llm CONNECTED, free text stays labeled local
 * heuristics — never invents model replies. When LIVE + key + opt-in, free text may
 * call [OpenAiDiscussClient] and store a clearly provenance-labeled agent turn.
 */
object ConversationCoordinator {

    enum class Channel {
        TEXT_CHIP,
        VOICE_TRANSCRIPT,
        FREE_TEXT
    }

    data class SubmitResult(
        val channel: Channel,
        /** Chip label applied through the shape script, if any. */
        val appliedChip: String?,
        /** Raw transcript stored when no chip matched. */
        val storedTranscript: String?,
        val honestyNote: String?,
        /** Discuss source label: local heuristics vs model suggestion. */
        val sourceLabel: String = LiveLlmProbe.LOCAL_HEURISTIC_LABEL,
        /** True when a network OpenAI call was attempted. */
        val liveCallAttempted: Boolean = false
    )

    fun submitChip(
        store: IntentionFlowStore,
        intention: Intention?,
        chip: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): SubmitResult {
        dispatchChip(store, intention, chip, nowEpochMs, registry)
        val live = registry.canUseLiveLlm()
        return SubmitResult(
            channel = Channel.TEXT_CHIP,
            appliedChip = chip,
            storedTranscript = null,
            honestyNote = if (live) {
                null
            } else {
                registry.get(CapabilityRegistry.ID_LIVE_LLM)?.limitNote
                    ?: "Local heuristics — not a live model."
            },
            sourceLabel = LiveLlmProbe.discussSourceLabel(registry)
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
        val sourceLabel = LiveLlmProbe.discussSourceLabel(registry)
        if (!registry.canUseVoiceInput()) {
            return SubmitResult(
                channel = Channel.VOICE_TRANSCRIPT,
                appliedChip = null,
                storedTranscript = null,
                honestyNote = registry.get(CapabilityRegistry.ID_VOICE_INPUT)?.limitNote
                    ?: "Voice input is not available — use the chips above.",
                sourceLabel = sourceLabel
            )
        }
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) {
            return SubmitResult(
                channel = Channel.VOICE_TRANSCRIPT,
                appliedChip = null,
                storedTranscript = null,
                honestyNote = "No speech recognized — try again or tap a chip.",
                sourceLabel = sourceLabel
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
                    "Local heuristics — no live LLM interpretation.",
                sourceLabel = sourceLabel
            )
        }
        val intentionId = intention?.id
            ?: store.snapshot().activeIntentionId
            ?: return SubmitResult(
                channel = Channel.VOICE_TRANSCRIPT,
                appliedChip = null,
                storedTranscript = trimmed,
                honestyNote = "No active intention — open Conversation from Home first.",
                sourceLabel = sourceLabel
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
                    "Discuss is on local heuristics — no live LLM. Choose a chip above " +
                    "to change your intention.",
                nowEpochMs = nowEpochMs + 1
            )
        )
        return SubmitResult(
            channel = Channel.VOICE_TRANSCRIPT,
            appliedChip = null,
            storedTranscript = trimmed,
            honestyNote = "Transcript stored as text only — local heuristics, no LLM. " +
                "Use chips for scripted actions.",
            sourceLabel = sourceLabel
        )
    }

    /**
     * Free-text Discuss path.
     *
     * - Not LIVE (no key / opt-in off / client off): store user text + honest local reply;
     *   never calls the network.
     * - LIVE: calls [client] with [LiveLlmAccess.resolveApiKey]; agent turn is labeled
     *   model-generated. Never writes OBSERVED; never auto-Completes.
     */
    fun submitFreeText(
        store: IntentionFlowStore,
        intention: Intention?,
        text: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default,
        access: LiveLlmAccess = FakeLiveLlmAccess(),
        client: OpenAiDiscussClient? = null
    ): SubmitResult {
        val sourceLabel = LiveLlmProbe.discussSourceLabel(registry)
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return SubmitResult(
                channel = Channel.FREE_TEXT,
                appliedChip = null,
                storedTranscript = null,
                honestyNote = "Empty message — type something or choose a chip.",
                sourceLabel = sourceLabel
            )
        }
        val intentionId = intention?.id
            ?: store.snapshot().activeIntentionId
            ?: return SubmitResult(
                channel = Channel.FREE_TEXT,
                appliedChip = null,
                storedTranscript = trimmed,
                honestyNote = "No active intention — open Conversation from Home first.",
                sourceLabel = sourceLabel
            )

        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-free-user-$nowEpochMs",
                intentionId = intentionId,
                role = "user",
                text = trimmed,
                nowEpochMs = nowEpochMs
            )
        )

        val live = registry.canUseLiveLlm()
        val apiKey = access.resolveApiKey()
        if (!live || client == null || apiKey.isNullOrBlank()) {
            val reason = when {
                !access.hasApiKeyConfigured() ->
                    "No OpenAI API key configured — local heuristics only."
                !access.userOptedIn() ->
                    "Live OpenAI is off until you opt in under Settings."
                !access.liveClientEnabled() || client == null ->
                    "Live Discuss client is not available in this session."
                else ->
                    "Live Discuss is not CONNECTED."
            }
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-free-agent-$nowEpochMs",
                    intentionId = intentionId,
                    role = "agent",
                    text = "I stored that as text. $reason " +
                        "Choose a chip above for scripted actions — " +
                        "I will not invent a model reply.",
                    nowEpochMs = nowEpochMs + 1
                )
            )
            return SubmitResult(
                channel = Channel.FREE_TEXT,
                appliedChip = null,
                storedTranscript = trimmed,
                honestyNote = "$reason Discuss source: ${LiveLlmProbe.LOCAL_HEURISTIC_LABEL}.",
                sourceLabel = LiveLlmProbe.LOCAL_HEURISTIC_LABEL,
                liveCallAttempted = false
            )
        }

        check(
            LiveLlmDiscussGuard.assertSafeModelSideEffect(
                writesObserved = false,
                autoCompletes = false
            )
        ) { "Live Discuss refused unsafe model side effects." }

        return when (val outcome = client.complete(apiKey, trimmed)) {
            is OpenAiDiscussClient.Outcome.Success -> {
                val agentText = OpenAiDiscussClient.formatAgentTurn(outcome.completion)
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-free-agent-$nowEpochMs",
                        intentionId = intentionId,
                        role = "agent",
                        text = agentText,
                        nowEpochMs = nowEpochMs + 1
                    )
                )
                SubmitResult(
                    channel = Channel.FREE_TEXT,
                    appliedChip = null,
                    storedTranscript = trimmed,
                    honestyNote = registry.get(CapabilityRegistry.ID_LIVE_LLM)?.limitNote,
                    sourceLabel = LiveLlmProbe.MODEL_SUGGESTION_LABEL,
                    liveCallAttempted = true
                )
            }
            is OpenAiDiscussClient.Outcome.Failure -> {
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-free-agent-$nowEpochMs",
                        intentionId = intentionId,
                        role = "agent",
                        text = outcome.honestMessage,
                        nowEpochMs = nowEpochMs + 1
                    )
                )
                SubmitResult(
                    channel = Channel.FREE_TEXT,
                    appliedChip = null,
                    storedTranscript = trimmed,
                    honestyNote = outcome.honestMessage,
                    sourceLabel = LiveLlmProbe.MODEL_SUGGESTION_LABEL,
                    liveCallAttempted = true
                )
            }
        }
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
            "decision" -> JobOfferScenario.onChip(store, chip, nowEpochMs, registry)
            QuitSmokingScenario.SHAPE_TAG -> QuitSmokingScenario.onChip(store, chip, nowEpochMs, registry)
            "app_use" -> AppUseReductionScenario.onChip(store, chip, nowEpochMs, registry)
            "research" -> EuWorkSpainScenario.onChip(store, chip, nowEpochMs, registry)
            ChessScenario.SHAPE_TAG -> ChessScenario.onChip(store, chip, nowEpochMs, registry)
            else -> SpanishScenario.onChip(store, chip, nowEpochMs, registry)
        }
    }
}
