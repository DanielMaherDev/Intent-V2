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
 * heuristics — never invents model replies. When LIVE + key + opt-in, unmatched voice
 * STT and typed free-text both call [OpenAiDiscussClient] on the same path.
 *
 * Chip-matching free-text / voice route through the same [dispatchChip] path so domain
 * mutations (facts, proposals) converge across modalities when a chip matches.
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
        dispatchChip(
            store,
            intention,
            chip,
            nowEpochMs,
            registry,
            modality = Channel.TEXT_CHIP.name
        )
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
     * otherwise the same Discuss free-text path as typed send ([submitFreeText] /
     * [OpenAiDiscussClient] when CONNECTED). Never invents a model reply when key or
     * opt-in is missing.
     */
    fun submitVoiceTranscript(
        store: IntentionFlowStore,
        intention: Intention?,
        availableChips: List<String>,
        transcript: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default,
        access: LiveLlmAccess = FakeLiveLlmAccess(),
        client: OpenAiDiscussClient? = null
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
        val chips = availableChips.ifEmpty { chipsForIntention(intention, registry) }
        val matched = matchChip(trimmed, chips)
        if (matched != null) {
            dispatchChip(
                store,
                intention,
                matched,
                nowEpochMs,
                registry,
                modality = Channel.VOICE_TRANSCRIPT.name
            )
            return SubmitResult(
                channel = Channel.VOICE_TRANSCRIPT,
                appliedChip = matched,
                storedTranscript = trimmed,
                honestyNote = "Matched your spoken choice to a chip — same path as tapping it. " +
                    "Local heuristics — no live LLM interpretation.",
                sourceLabel = sourceLabel
            )
        }
        dispatchGateEUtterance(
            store = store,
            trimmed = trimmed,
            nowEpochMs = nowEpochMs,
            registry = registry,
            channel = Channel.VOICE_TRANSCRIPT,
            sourceLabel = sourceLabel
        )?.let { return it.copy(channel = Channel.VOICE_TRANSCRIPT) }
        // Unmatched STT free-text → identical OpenAI / local-heuristics path as typed send.
        return submitFreeText(
            store = store,
            intention = intention,
            text = trimmed,
            nowEpochMs = nowEpochMs,
            registry = registry,
            access = access,
            client = client,
            availableChips = emptyList() // already failed chip match
        ).copy(channel = Channel.VOICE_TRANSCRIPT)
    }

    /**
     * Free-text Discuss path.
     *
     * - When text matches a suggested chip for the intention (or [availableChips]),
     *   routes through the same [dispatchChip] path as voice/chip (Gate B modality).
     * - Not LIVE: store user text + honest local reply; never calls the network.
     * - LIVE: calls [client] with [ContextAssembler] pack appended to the system prompt.
     *   Never writes OBSERVED; never auto-Completes.
     */
    fun submitFreeText(
        store: IntentionFlowStore,
        intention: Intention?,
        text: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default,
        access: LiveLlmAccess = FakeLiveLlmAccess(),
        client: OpenAiDiscussClient? = null,
        availableChips: List<String> = emptyList()
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

        // Gate E scripted paths may seed their own intention (e.g. vague health).
        dispatchGateEUtterance(
            store = store,
            trimmed = trimmed,
            nowEpochMs = nowEpochMs,
            registry = registry,
            channel = Channel.FREE_TEXT,
            sourceLabel = sourceLabel
        )?.let { return it }

        val intentionId = intention?.id
            ?: store.snapshot().activeIntentionId
            ?: return SubmitResult(
                channel = Channel.FREE_TEXT,
                appliedChip = null,
                storedTranscript = trimmed,
                honestyNote = "No active intention — open Conversation from Home first.",
                sourceLabel = sourceLabel
            )

        val chips = availableChips.ifEmpty { chipsForIntention(intention, registry) }
        if (chips.isNotEmpty()) {
            val matched = matchChip(trimmed, chips)
            if (matched != null) {
                dispatchChip(
                    store,
                    intention,
                    matched,
                    nowEpochMs,
                    registry,
                    modality = Channel.FREE_TEXT.name
                )
                return SubmitResult(
                    channel = Channel.FREE_TEXT,
                    appliedChip = matched,
                    storedTranscript = trimmed,
                    honestyNote = "Matched your typed text to a chip — same path as tapping it.",
                    sourceLabel = sourceLabel
                )
            }
        }

        // App-use free-text: typed CAPABILITY_GAP / leave-me-alone (case 12 / 22) —
        // same scripted path as chips, not prose-only LLM fallback.
        if (intention?.shapeTag == "app_use" || intentionId == AppUseReductionScenario.INTENTION_ID) {
            if (AppUseReductionScenario.chipContainsShortsIdentityClaim(trimmed) ||
                AppUseReductionScenario.chipContainsLeaveMeAlone(trimmed)
            ) {
                AppUseReductionScenario.onChip(store, trimmed, nowEpochMs, registry)
                return SubmitResult(
                    channel = Channel.FREE_TEXT,
                    appliedChip = trimmed,
                    storedTranscript = trimmed,
                    honestyNote = if (AppUseReductionScenario.chipContainsShortsIdentityClaim(trimmed)) {
                        "Capability gap — Shorts identity is not supported."
                    } else {
                        "Leave-me-alone preference recorded — cooldown armed."
                    },
                    sourceLabel = sourceLabel
                )
            }
        }

        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-free-user-$nowEpochMs",
                intentionId = intentionId,
                role = "user",
                text = trimmed,
                nowEpochMs = nowEpochMs,
                modality = Channel.FREE_TEXT.name
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
                    nowEpochMs = nowEpochMs + 1,
                    modality = Channel.FREE_TEXT.name
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

        val contextPack = ContextAssembler.assemble(
            snapshot = store.snapshot(),
            intention = intention ?: store.snapshot().intentions.firstOrNull { it.id == intentionId },
            registry = registry,
            nowEpochMs = nowEpochMs
        )

        return when (
            val outcome = client.complete(
                apiKey = apiKey,
                userMessage = trimmed,
                contextPack = contextPack
            )
        ) {
            is OpenAiDiscussClient.Outcome.Success -> {
                val agentText = OpenAiDiscussClient.formatAgentTurn(outcome.completion)
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-free-agent-$nowEpochMs",
                        intentionId = intentionId,
                        role = "agent",
                        text = agentText,
                        nowEpochMs = nowEpochMs + 1,
                        modality = Channel.FREE_TEXT.name
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
                        nowEpochMs = nowEpochMs + 1,
                        modality = Channel.FREE_TEXT.name
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

    /**
     * Shape-aware suggested chips.
     * Language: fixture Spanish only — user-built language stays empty (no invented
     * practice catalog). Quit/`other` / unknown / null stay empty.
     */
    fun chipsForIntention(
        intention: Intention?,
        registry: CapabilityRegistry
    ): List<String> = when (intention?.shapeTag) {
        "language" -> SpanishScenario.conversationChipsFor(intention, registry)
        "marathon" -> MarathonScenario.conversationChips(registry)
        "business" -> BusinessScenario.conversationChips(registry)
        "move" -> MoveScenario.conversationChips(registry)
        "decision" -> JobOfferScenario.conversationChips(registry)
        QuitSmokingScenario.SHAPE_TAG -> QuitSmokingScenario.conversationChips(registry)
        "app_use" -> AppUseReductionScenario.conversationChips(registry)
        "research" -> EuWorkSpainScenario.conversationChips(registry)
        ChessScenario.SHAPE_TAG -> ChessScenario.conversationChips(registry)
        else -> emptyList()
    }

    private fun dispatchChip(
        store: IntentionFlowStore,
        intention: Intention?,
        chip: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry,
        modality: String? = null
    ) {
        val activeId = intention?.id
        when (intention?.shapeTag) {
            "marathon" -> MarathonScenario.onChip(store, chip, nowEpochMs, registry)
            "business" -> BusinessScenario.onChip(store, chip, nowEpochMs, registry)
            "move" -> MoveScenario.onChip(store, chip, nowEpochMs, registry)
            "decision" -> JobOfferScenario.onChip(store, chip, nowEpochMs, registry)
            QuitSmokingScenario.SHAPE_TAG -> QuitSmokingScenario.onChip(store, chip, nowEpochMs, registry)
            "app_use" -> AppUseReductionScenario.onChip(store, chip, nowEpochMs, registry)
            "research" -> EuWorkSpainScenario.onChip(store, chip, nowEpochMs, registry)
            ChessScenario.SHAPE_TAG -> ChessScenario.onChip(store, chip, nowEpochMs, registry)
            "language" -> SpanishScenario.onChip(
                store,
                chip,
                nowEpochMs,
                registry,
                modality,
                intentionId = activeId
            )
            else -> {
                // No scaffold Spanish fallback for other / unknown shapes.
            }
        }
    }

    /**
     * Gate E §17 scripted utterances (health, constraint, guided practice, Duolingo,
     * tutor day change, bare spoken approval). Returns null when no match.
     */
    private fun dispatchGateEUtterance(
        store: IntentionFlowStore,
        trimmed: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry,
        channel: Channel,
        sourceLabel: String
    ): SubmitResult? {
        if (VagueHealthGoal.looksLikeVagueHealth(trimmed)) {
            val result = VagueHealthGoal.open(store, trimmed, nowEpochMs)
            return SubmitResult(
                channel = channel,
                appliedChip = trimmed,
                storedTranscript = trimmed,
                honestyNote = result.honestyNote,
                sourceLabel = sourceLabel
            )
        }
        if (PhysicalConstraintChange.looksLikeConstraint(trimmed)) {
            val result = PhysicalConstraintChange.apply(store, nowEpochMs, trimmed, registry)
            return SubmitResult(
                channel = channel,
                appliedChip = trimmed,
                storedTranscript = trimmed,
                honestyNote = result.honestyNote,
                sourceLabel = sourceLabel
            )
        }
        if (GuidedPractice.looksLikeGuidedPractice(trimmed)) {
            val result = GuidedPractice.start(store, nowEpochMs, trimmed)
            return SubmitResult(
                channel = channel,
                appliedChip = trimmed,
                storedTranscript = trimmed,
                honestyNote = result.honestyNote,
                sourceLabel = sourceLabel
            )
        }
        if (LongTermAdjustment.looksLikeTutorDayChange(trimmed)) {
            val result = LongTermAdjustment.applyTutorTuesday(store, nowEpochMs, trimmed, registry)
            return SubmitResult(
                channel = channel,
                appliedChip = trimmed,
                storedTranscript = trimmed,
                honestyNote = result.honestyNote,
                sourceLabel = sourceLabel
            )
        }
        if (AppDurationTask.looksLikeDurationTask(trimmed)) {
            val result = AppDurationTask.startFifteenMinutePractice(store, nowEpochMs, trimmed, registry)
            return SubmitResult(
                channel = channel,
                appliedChip = trimmed,
                storedTranscript = trimmed,
                honestyNote = result.honestyNote,
                sourceLabel = sourceLabel
            )
        }
        if (AppDurationTask.looksLikeDuolingoApproach(trimmed)) {
            val result = AppDurationTask.confirmDuolingoApproach(store, nowEpochMs, trimmed, registry)
            return SubmitResult(
                channel = channel,
                appliedChip = trimmed,
                storedTranscript = trimmed,
                honestyNote = result.honestyNote,
                sourceLabel = sourceLabel
            )
        }
        if (ApprovalAmbiguityResolver.looksLikeBareApproval(trimmed) &&
            ApprovalAmbiguityResolver.pendingProposals(store.snapshot()).size >= 2
        ) {
            val gateway = CommandGateway.approveProposal(
                store = store,
                turnId = "turn-voice-yes-$nowEpochMs",
                commandId = "cmd-voice-yes-$nowEpochMs",
                sessionId = "session-unresolved",
                nowEpochMs = nowEpochMs,
                utterance = trimmed
            )
            return SubmitResult(
                channel = channel,
                appliedChip = null,
                storedTranscript = trimmed,
                honestyNote = gateway.message
                    ?: "Ambiguous spoken approval — clarification required.",
                sourceLabel = sourceLabel
            )
        }
        return null
    }
}
