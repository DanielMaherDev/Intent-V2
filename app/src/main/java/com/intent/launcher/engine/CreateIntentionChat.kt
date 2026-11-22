package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AdaptiveBuilderState
import com.intent.launcher.domain.AnswerQuestion
import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.ApproachActor
import com.intent.launcher.domain.ApproachDraft
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.MoveScenario
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.RelativeDateAnchoring
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone

/**
 * Chat-first create path (F1/F2): Home **Start a new intention** opens Conversation
 * with a live create session. Practice frequency and similar prompts render as
 * in-chat cards (NumberWheel / chips+Other) — not a disconnected Builder wizard.
 *
 * Structural answers always go through [IntentionBuilder] / [AnswerBuilderPrompt].
 * Language create surfaces AdaptiveBuilderEngine motivation Ask (chips + Other)
 * after minutes + method, before Confirm → [bridgeAdaptiveBuilderAfterCommit]
 * Propose. Confirm→Propose still works once motivation is answered. When live
 * OpenAI is opted in, prose may be enriched; otherwise honest local heuristics.
 */
object CreateIntentionChat {

    const val SESSION_INTENTION_ID = "intention-creating"
    const val CONVERSATION_ROUTE = DiscussFromHome.CONVERSATION_ROUTE
    const val GREETING =
        "What would you like to work on? Tell me in a sentence — " +
            "I'll ask a few useful questions here in chat."

    data class BeginResult(
        val navigateTo: String?,
        val intentionId: String?,
        val ok: Boolean,
        val note: String
    )

    data class SubmitResult(
        val ok: Boolean,
        val honestyNote: String?,
        val sourceLabel: String,
        val liveCallAttempted: Boolean = false,
        val committedIntentionId: String? = null,
        /** Set when Confirm bridged into a pending ProposeRecurringSession. */
        val pendingProposalId: String? = null
    )

    /**
     * Create-shell routing: true only for the live [SESSION_INTENTION_ID] shell
     * before Approve graduates it. After Approve, phase is ACTIVE (and/or the id
     * is remapped) so Discuss free-text hits [ConversationCoordinator].
     */
    fun isCreateSession(intention: Intention?): Boolean =
        intention?.id == SESSION_INTENTION_ID &&
            intention.phase != IntentionPhase.ACTIVE

    /**
     * True while chat-first create is still collecting answers.
     * After Confirm, [bridgeAdaptiveBuilderAfterCommit] attaches an adaptive
     * builder state to [SESSION_INTENTION_ID] — that shell is then durable Discuss.
     * After Approve, [clearCreateSessionAfterApprove] remaps the shell so this
     * returns false even if adaptive state was somehow missing.
     */
    fun isCreateInProgress(store: IntentionFlowStore): Boolean {
        val snap = store.snapshot()
        if (snap.builderDraft.active) return true
        val create = snap.intentions.firstOrNull { it.id == SESSION_INTENTION_ID }
            ?: return false
        // Approved create shell is durable Discuss — never CreateIntentionChat.
        if (create.phase == IntentionPhase.ACTIVE) return false
        return snap.adaptiveBuilderStates.none { it.intentionId == SESSION_INTENTION_ID }
    }

    fun isCreateSessionActive(store: IntentionFlowStore): Boolean {
        val snap = store.snapshot()
        if (snap.builderDraft.active) return true
        val create = snap.intentions.firstOrNull { it.id == SESSION_INTENTION_ID }
            ?: return false
        return create.phase != IntentionPhase.ACTIVE
    }

    /**
     * IV-227 / Gate C6 AVD: after gateway Approve APPLIED, graduate the create
     * shell so post-Approve free-text never re-enters [submitUtterance] /
     * [startFromDescription] (which renamed the intention on tip 0.0.170).
     *
     * @return durable intention id after remap (unchanged when already durable)
     */
    fun clearCreateSessionAfterApprove(
        store: IntentionFlowStore,
        approvedIntentionId: String,
        nowEpochMs: Long
    ): String {
        if (store.snapshot().builderDraft.active) {
            IntentionBuilder.cancel(store, nowEpochMs)
        }
        val snap = store.snapshot()
        val targetId = when {
            approvedIntentionId == SESSION_INTENTION_ID -> SESSION_INTENTION_ID
            snap.intentions.any { it.id == SESSION_INTENTION_ID } &&
                snap.proposals.any {
                    it.intentionId == SESSION_INTENTION_ID &&
                        it.status == ProposalStatus.APPROVED
                } -> SESSION_INTENTION_ID
            else -> return approvedIntentionId
        }
        val durableId = "intention-$nowEpochMs"
        if (snap.intentions.any { it.id == durableId }) {
            return approvedIntentionId
        }
        store.apply(
            FlowCommand.RemapIntentionId(
                fromIntentionId = targetId,
                toIntentionId = durableId,
                nowEpochMs = nowEpochMs
            )
        )
        return durableId
    }

    fun begin(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): BeginResult {
        if (!registry.canBuildIntention()) {
            return BeginResult(
                navigateTo = null,
                intentionId = null,
                ok = false,
                note = registry.get(CapabilityRegistry.ID_BUILD_INTENTION)?.limitNote
                    ?: "Intention builder is not available."
            )
        }
        if (!registry.canDiscuss()) {
            return BeginResult(
                navigateTo = null,
                intentionId = null,
                ok = false,
                note = registry.get(CapabilityRegistry.ID_DISCUSS)?.limitNote
                    ?: "Conversation is not available."
            )
        }

        // Fresh create — clear a leftover draft so Home always starts discovery.
        if (store.snapshot().builderDraft.active) {
            IntentionBuilder.cancel(store, nowEpochMs)
        }

        // AVD defect 4: promote any prior create-shell progress (Spanish transcript,
        // committed facts) onto a durable id before seeding a clean shell.
        val existing = store.snapshot().intentions.firstOrNull { it.id == SESSION_INTENTION_ID }
        if (existing != null) {
            val hasProgress = existing.title != "New intention" ||
                store.snapshot().facts.any { it.intentionId == SESSION_INTENTION_ID } ||
                store.snapshot().turns.any {
                    it.intentionId == SESSION_INTENTION_ID &&
                        !it.text.contains("What would you like to work on", ignoreCase = true)
                }
            if (hasProgress) {
                store.apply(
                    FlowCommand.RemapIntentionId(
                        fromIntentionId = SESSION_INTENTION_ID,
                        toIntentionId = "intention-$nowEpochMs",
                        nowEpochMs = nowEpochMs
                    )
                )
            }
        }

        if (store.snapshot().intentions.none { it.id == SESSION_INTENTION_ID }) {
            store.apply(
                FlowCommand.SeedExploring(
                    intentionId = SESSION_INTENTION_ID,
                    title = "New intention",
                    statement = "Starting a new intention",
                    shapeTag = "other",
                    nowEpochMs = nowEpochMs + 1
                )
            )
        } else {
            store.apply(
                FlowCommand.SetActiveIntention(
                    intentionId = SESSION_INTENTION_ID,
                    nowEpochMs = nowEpochMs + 1
                )
            )
        }

        // Always greet on a fresh shell (prior greeting moved with RemapIntentionId).
        val hasGreeting = store.snapshot().turns.any {
            it.intentionId == SESSION_INTENTION_ID &&
                it.role == "agent" &&
                it.text.contains("What would you like to work on", ignoreCase = true)
        }
        if (!hasGreeting) {
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-create-greet-$nowEpochMs",
                    intentionId = SESSION_INTENTION_ID,
                    role = "agent",
                    text = GREETING,
                    nowEpochMs = nowEpochMs + 2,
                    modality = ConversationCoordinator.Channel.FREE_TEXT.name
                )
            )
        }

        return BeginResult(
            navigateTo = CONVERSATION_ROUTE,
            intentionId = SESSION_INTENTION_ID,
            ok = true,
            note = LiveLlmProbe.discussSourceLabel(registry)
        )
    }

    /**
     * Free-text / voice path while the create session is active.
     * Always advances local builder state; optionally asks OpenAI for prose.
     */
    fun submitUtterance(
        store: IntentionFlowStore,
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
                ok = false,
                honestyNote = "Empty message — type something or choose a chip.",
                sourceLabel = sourceLabel
            )
        }
        if (!registry.canBuildIntention()) {
            return SubmitResult(
                ok = false,
                honestyNote = registry.get(CapabilityRegistry.ID_BUILD_INTENTION)?.limitNote,
                sourceLabel = sourceLabel
            )
        }

        ensureSession(store, nowEpochMs)

        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-create-user-$nowEpochMs",
                intentionId = SESSION_INTENTION_ID,
                role = "user",
                text = trimmed,
                nowEpochMs = nowEpochMs,
                modality = ConversationCoordinator.Channel.FREE_TEXT.name
            )
        )

        val draft = store.snapshot().builderDraft
        val activeCard = latestActiveBuilderCard(store)

        when {
            !draft.active || draft.phase == BuilderPhase.ENTER_TEXT -> {
                return startFromDescription(
                    store = store,
                    description = trimmed,
                    nowEpochMs = nowEpochMs,
                    registry = registry,
                    access = access,
                    client = client,
                    sourceLabel = sourceLabel
                )
            }
            draft.phase == BuilderPhase.MISSING_FACTS -> {
                val prompt = IntentionBuilder.nextPrompt(draft) ?: return confirmAndCommit(
                    store, nowEpochMs, registry, sourceLabel
                )
                return when (val decoded = activeCard?.let { BuilderAnswerCard.decode(it) }) {
                    is BuilderAnswerCard.Decoded.Quantity ->
                        answerQuantityText(
                            store, prompt.id, trimmed, decoded, nowEpochMs, registry, sourceLabel
                        )
                    is BuilderAnswerCard.Decoded.Choice ->
                        answerChoiceText(
                            store, prompt.id, trimmed, decoded, nowEpochMs, registry, sourceLabel
                        )
                    else ->
                        answerChoiceText(
                            store,
                            prompt.id,
                            trimmed,
                            BuilderAnswerCard.Decoded.Choice(
                                prompt.id,
                                prompt.choiceOptions.orEmpty()
                            ),
                            nowEpochMs,
                            registry,
                            sourceLabel
                        )
                }
            }
            draft.phase == BuilderPhase.CONFIRM -> {
                if (pendingMotivationAsk(store) != null) {
                    if (looksLikeConfirm(trimmed) || looksLikeKeepDiscussing(trimmed)) {
                        return SubmitResult(
                            false,
                            "Answer why this language matters first (chips or Other).",
                            sourceLabel
                        )
                    }
                    return answerCreateMotivation(
                        store = store,
                        raw = trimmed,
                        modality = ConversationCoordinator.Channel.FREE_TEXT.name,
                        nowEpochMs = nowEpochMs,
                        sourceLabel = sourceLabel,
                        userTurnAlreadyPosted = true
                    )
                }
                return if (looksLikeConfirm(trimmed)) {
                    confirmAndCommit(store, nowEpochMs, registry, sourceLabel)
                } else if (looksLikeKeepDiscussing(trimmed)) {
                    postAgent(
                        store,
                        nowEpochMs,
                        "Okay — keep telling me what matters. Nothing is locked in yet."
                    )
                    SubmitResult(true, "Kept discussing — draft still open.", sourceLabel)
                } else {
                    // Extra free text while confirming — store as another fact answer if possible
                    postAgent(
                        store,
                        nowEpochMs,
                        "Got it. Tap Confirm intention when this looks right, or keep discussing."
                    )
                    projectConfirmCard(store, nowEpochMs + 2)
                    SubmitResult(true, "Still waiting for confirm.", sourceLabel)
                }
            }
            else -> {
                return startFromDescription(
                    store, trimmed, nowEpochMs, registry, access, client, sourceLabel
                )
            }
        }
    }

    /** Chip / radio selection on an in-chat builder card. */
    fun submitChoice(
        store: IntentionFlowStore,
        label: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): SubmitResult {
        val sourceLabel = LiveLlmProbe.discussSourceLabel(registry)
        val trimmed = label.trim()
        if (trimmed.isEmpty()) {
            return SubmitResult(false, "Empty choice.", sourceLabel)
        }
        val draft = store.snapshot().builderDraft
        if (!draft.active) {
            return SubmitResult(false, "No create session in progress.", sourceLabel)
        }

        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-create-chip-$nowEpochMs",
                intentionId = SESSION_INTENTION_ID,
                role = "user",
                text = trimmed,
                nowEpochMs = nowEpochMs,
                modality = ConversationCoordinator.Channel.TEXT_CHIP.name
            )
        )

        if (draft.phase == BuilderPhase.CONFIRM) {
            if (pendingMotivationAsk(store) != null) {
                if (trimmed.contains("Confirm", ignoreCase = true) ||
                    trimmed.contains("Keep discussing", ignoreCase = true)
                ) {
                    return SubmitResult(
                        false,
                        "Answer why this language matters first (chips or Other).",
                        sourceLabel
                    )
                }
                return answerCreateMotivation(
                    store = store,
                    raw = trimmed,
                    modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
                    nowEpochMs = nowEpochMs,
                    sourceLabel = sourceLabel,
                    userTurnAlreadyPosted = true
                )
            }
            return when {
                trimmed.contains("Confirm", ignoreCase = true) ->
                    confirmAndCommit(store, nowEpochMs, registry, sourceLabel)
                trimmed.contains("Keep discussing", ignoreCase = true) -> {
                    postAgent(
                        store,
                        nowEpochMs + 1,
                        "Okay — keep telling me what matters. Nothing is locked in yet."
                    )
                    SubmitResult(true, "Kept discussing — draft still open.", sourceLabel)
                }
                else -> SubmitResult(false, "Choose Confirm or Keep discussing.", sourceLabel)
            }
        }

        // Prefer the active in-chat card prompt id (e.g. OC5 usage_measurement)
        // over nextPrompt, which only knows IntentionBuilder missing-fact ids.
        val activeChoice = latestActiveBuilderCard(store)?.let { BuilderAnswerCard.decode(it) }
            as? BuilderAnswerCard.Decoded.Choice
        val promptId = activeChoice?.promptId
            ?: IntentionBuilder.nextPrompt(draft)?.id
            ?: return confirmAndCommit(store, nowEpochMs, registry, sourceLabel)

        if (ChoiceOptions.isOther(trimmed)) {
            postAgent(
                store,
                nowEpochMs + 1,
                "Type your answer in your own words below."
            )
            return SubmitResult(
                ok = true,
                honestyNote = "Other selected — awaiting free text.",
                sourceLabel = sourceLabel
            )
        }

        IntentionBuilder.answerPrompt(store, promptId, trimmed, nowEpochMs + 1, registry)
        deactivatePriorBuilderCards(store, nowEpochMs + 1)
        return afterAnswer(store, nowEpochMs + 2, registry, sourceLabel)
    }

    fun submitQuantity(
        store: IntentionFlowStore,
        value: Int,
        unitLabel: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): SubmitResult {
        val sourceLabel = LiveLlmProbe.discussSourceLabel(registry)
        val draft = store.snapshot().builderDraft
        if (!draft.active) {
            return SubmitResult(false, "No create session in progress.", sourceLabel)
        }
        val prompt = IntentionBuilder.nextPrompt(draft)
            ?: return confirmAndCommit(store, nowEpochMs, registry, sourceLabel)
        val answer = "$value $unitLabel"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-create-qty-$nowEpochMs",
                intentionId = SESSION_INTENTION_ID,
                role = "user",
                text = answer,
                nowEpochMs = nowEpochMs,
                modality = ConversationCoordinator.Channel.TEXT_CHIP.name
            )
        )
        IntentionBuilder.answerPrompt(store, prompt.id, answer, nowEpochMs + 1, registry)
        deactivatePriorBuilderCards(store, nowEpochMs + 1)
        return afterAnswer(store, nowEpochMs + 2, registry, sourceLabel)
    }

    private fun startFromDescription(
        store: IntentionFlowStore,
        description: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry,
        access: LiveLlmAccess,
        client: OpenAiDiscussClient?,
        sourceLabel: String
    ): SubmitResult {
        val start = IntentionBuilder.start(store, description, nowEpochMs + 1, registry)
        if (!start.ok) {
            postAgent(store, nowEpochMs + 1, start.note)
            return SubmitResult(false, start.note, sourceLabel)
        }

        // Refresh the session intention title/shape to match the draft.
        val draft = store.snapshot().builderDraft
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = SESSION_INTENTION_ID,
                title = draft.title.ifBlank { IntentionBuilder.titleFrom(description, draft.suggestedShapeTag) },
                statement = draft.statement.ifBlank { description },
                shapeTag = draft.shapeTag ?: draft.suggestedShapeTag,
                nowEpochMs = nowEpochMs + 2
            )
        )

        val shapeLabel = IntentionBuilder.shapeDisplayLabel(
            draft.shapeTag ?: draft.suggestedShapeTag
        )
        var liveAttempted = false
        val seedHonesty = CreatePathSeedHonesty.forDescription(description)
        var prose = "I’ve treated this as a ${shapeLabel.lowercase()} intention. " +
            "Next I’ll ask one useful question at a time in this chat."
        if (seedHonesty != null) {
            // Agent must surface measurement / decide-by-date honesty — do not
            // rely on the user typing it via Other (OC5 / OC9).
            prose = "$prose\n\n${seedHonesty.agentNote}"
        }

        val live = registry.canUseLiveLlm()
        val apiKey = access.resolveApiKey()
        if (live && client != null && !apiKey.isNullOrBlank()) {
            liveAttempted = true
            val honestyHint = when (seedHonesty?.kind) {
                CreatePathSeedHonesty.Kind.USAGE_APP_TIME ->
                    " Measurement honesty required: UsageStats package foreground only " +
                        "when usage access is granted; cannot identify Shorts/Reels; " +
                        "offer track package time / manual Shorts log / both; never claim exact Shorts minutes."
                CreatePathSeedHonesty.Kind.TIME_LIMITED_DECISION ->
                    " Frame decide-by-date: help decide by a deadline; never auto-accept " +
                        "or assume the outcome; ask criteria that matter."
                CreatePathSeedHonesty.Kind.FINITE_MOVE_PROJECT ->
                    " Finite move project: propose connected paths; mark landlord/appointment " +
                        "steps as Waiting on someone; never invent viewings or a secured place."
                null -> ""
            }
            val pack = ContextAssembler.assemble(
                snapshot = store.snapshot(),
                intention = store.snapshot().intentions.firstOrNull { it.id == SESSION_INTENTION_ID },
                registry = registry,
                nowEpochMs = nowEpochMs
            ) + "\nCreate-intention mode: ask consequential discovery questions one at a time. " +
                "Do not invent a full plan. Do not claim observed app progress.$honestyHint"
            when (
                val outcome = client.complete(
                    apiKey = apiKey,
                    userMessage = description,
                    contextPack = pack
                )
            ) {
                is OpenAiDiscussClient.Outcome.Success -> {
                    // Keep seed honesty visible even if the model rewrites prose.
                    // Casual product mentions (e.g. "YouTube Shorts") must NOT suppress
                    // the package≠Shorts / usage-access note.
                    val formatted = OpenAiDiscussClient.formatAgentTurn(outcome.completion)
                    prose = if (seedHonesty != null) {
                        CreatePathSeedHonesty.mergeLiveProseWithSeedHonesty(
                            formatted,
                            seedHonesty
                        )
                    } else {
                        formatted
                    }
                }
                is OpenAiDiscussClient.Outcome.Failure -> {
                    prose = "$prose\n\n(${outcome.honestMessage})"
                }
            }
        } else if (sourceLabel == LiveLlmProbe.LOCAL_HEURISTIC_LABEL) {
            prose = "$prose (On-device reply — not a live model.)"
        }

        postAgent(store, nowEpochMs + 3, prose)
        if (seedHonesty?.firstChoicePromptId != null &&
            seedHonesty.firstChoiceOptions.isNotEmpty() &&
            seedHonesty.firstChoiceQuestion != null
        ) {
            projectSeedHonestyChoiceCard(store, nowEpochMs + 4, seedHonesty)
        } else {
            projectNextPromptCard(store, nowEpochMs + 4)
        }
        return SubmitResult(
            ok = true,
            honestyNote = start.note,
            sourceLabel = if (liveAttempted) LiveLlmProbe.MODEL_SUGGESTION_LABEL else sourceLabel,
            liveCallAttempted = liveAttempted
        )
    }

    private fun answerQuantityText(
        store: IntentionFlowStore,
        promptId: String,
        text: String,
        spec: BuilderAnswerCard.Decoded.Quantity,
        nowEpochMs: Long,
        registry: CapabilityRegistry,
        sourceLabel: String
    ): SubmitResult {
        val parsed = text.filter { it.isDigit() }.toIntOrNull()
            ?: text.trim().toIntOrNull()
        if (parsed == null || parsed !in spec.min..spec.max) {
            postAgent(
                store,
                nowEpochMs + 1,
                "Pick a whole number from ${spec.min}–${spec.max} on the wheel, or type one."
            )
            projectNextPromptCard(store, nowEpochMs + 2)
            return SubmitResult(
                false,
                "Need a whole integer ${spec.min}–${spec.max}.",
                sourceLabel
            )
        }
        val answer = "$parsed ${spec.unitLabel}"
        IntentionBuilder.answerPrompt(store, promptId, answer, nowEpochMs + 1, registry)
        deactivatePriorBuilderCards(store, nowEpochMs + 1)
        return afterAnswer(store, nowEpochMs + 2, registry, sourceLabel)
    }

    private fun answerChoiceText(
        store: IntentionFlowStore,
        promptId: String,
        text: String,
        spec: BuilderAnswerCard.Decoded.Choice,
        nowEpochMs: Long,
        registry: CapabilityRegistry,
        sourceLabel: String
    ): SubmitResult {
        when (val sel = ChoiceOptions.resolveSelection(text, spec.options)) {
            ChoiceOptions.Selection.Empty ->
                return SubmitResult(false, "Empty answer.", sourceLabel)
            ChoiceOptions.Selection.NeedsFreeText -> {
                postAgent(
                    store,
                    nowEpochMs + 1,
                    "Type your answer in your own words below."
                )
                return SubmitResult(true, "Other — awaiting free text.", sourceLabel)
            }
            ChoiceOptions.Selection.NotSure,
            ChoiceOptions.Selection.Skip -> {
                postAgent(
                    store,
                    nowEpochMs + 1,
                    "Okay — we can skip that for now. Your earlier answers stay saved."
                )
                return SubmitResult(true, "Deferred — conversation continues.", sourceLabel)
            }
            is ChoiceOptions.Selection.Chosen -> {
                IntentionBuilder.answerPrompt(
                    store, promptId, sel.value, nowEpochMs + 1, registry
                )
            }
            is ChoiceOptions.Selection.FreeText -> {
                IntentionBuilder.answerPrompt(
                    store, promptId, sel.value, nowEpochMs + 1, registry
                )
            }
        }
        deactivatePriorBuilderCards(store, nowEpochMs + 1)
        return afterAnswer(store, nowEpochMs + 2, registry, sourceLabel)
    }

    private fun afterAnswer(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        registry: CapabilityRegistry,
        sourceLabel: String
    ): SubmitResult {
        val draft = store.snapshot().builderDraft
        return when (draft.phase) {
            BuilderPhase.CONFIRM -> {
                // Gate C2: language minutes+method → engine motivation Ask before Confirm/Propose.
                if (surfaceMotivationAskBeforeConfirm(store, nowEpochMs)) {
                    return SubmitResult(true, "Motivation ask projected.", sourceLabel)
                }
                postAgent(
                    store,
                    nowEpochMs,
                    "Does this look right? Confirm to save the intention, or keep discussing."
                )
                projectConfirmCard(store, nowEpochMs + 1)
                SubmitResult(true, "Ready to confirm.", sourceLabel)
            }
            BuilderPhase.MISSING_FACTS -> {
                // Gate D / AVD: project the in-chat card once — do not also postAgent
                // the same question text (that duplicated “tutor, class, or app?”).
                projectNextPromptCard(store, nowEpochMs)
                SubmitResult(true, "Next question projected.", sourceLabel)
            }
            else -> confirmAndCommit(store, nowEpochMs, registry, sourceLabel)
        }
    }

    private fun confirmAndCommit(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        registry: CapabilityRegistry,
        sourceLabel: String
    ): SubmitResult {
        // Capture before CommitBuilderIntention clears the draft.
        val draft = store.snapshot().builderDraft
        val factAnswers = draft.factAnswers.toMap()
        val shapeTag = draft.shapeTag ?: draft.suggestedShapeTag
        val freeText = draft.freeText

        val ok = IntentionBuilder.commit(
            store = store,
            nowEpochMs = nowEpochMs,
            intentionId = SESSION_INTENTION_ID,
            registry = registry
        )
        if (!ok) {
            postAgent(store, nowEpochMs + 1, "Could not confirm that intention yet.")
            return SubmitResult(false, "Commit failed.", sourceLabel)
        }
        deactivatePriorBuilderCards(store, nowEpochMs + 1)

        val bridgedProposalId = bridgeAdaptiveBuilderAfterCommit(
            store = store,
            intentionId = SESSION_INTENTION_ID,
            shapeTag = shapeTag,
            factAnswers = factAnswers,
            freeText = freeText,
            nowEpochMs = nowEpochMs + 10
        )

        val title = store.snapshot().intentions
            .firstOrNull { it.id == SESSION_INTENTION_ID }?.title ?: "Your intention"
        val agentText = if (bridgedProposalId != null) {
            "Saved “$title”. A draft routine is ready to review — " +
                "nothing is scheduled until you approve it."
        } else {
            "Saved “$title” as exploring. We can keep refining it here — " +
                "nothing is scheduled until you approve a plan."
        }
        // After bridge timestamps so Conversation order is Confirm → Propose card → Saved.
        postAgent(store, nowEpochMs + 80, agentText)
        return SubmitResult(
            ok = true,
            honestyNote = if (bridgedProposalId != null) {
                "Intention created — review proposal pending."
            } else {
                "Intention created."
            },
            sourceLabel = sourceLabel,
            committedIntentionId = SESSION_INTENTION_ID,
            pendingProposalId = bridgedProposalId
        )
    }

    /**
     * Remap create-draft prompt ids (`practice_minutes`, `existing_method`, …)
     * onto AdaptiveBuilder engine keys + SELECTED approaches, then advance
     * [AdaptiveBuilderEngine.suggestNextDecision] until Propose when ready.
     *
     * [FlowCommand.CommitBuilderIntention] only stores Facts with `key = promptId`;
     * without this bridge, live create never reaches §8.4 Review.
     *
     * @return pending proposal id when Propose was applied, else null
     */
    internal fun bridgeAdaptiveBuilderAfterCommit(
        store: IntentionFlowStore,
        intentionId: String,
        shapeTag: String,
        factAnswers: Map<String, String>,
        freeText: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String? {
        // OC2 / §8.8 — research create: eligibility facts → AdaptiveBuilder Pause.
        if (shapeTag == "research") {
            return bridgeResearchAfterCommit(
                store = store,
                intentionId = intentionId,
                factAnswers = factAnswers,
                nowEpochMs = nowEpochMs,
                timeZone = timeZone
            )
        }
        // OC7 — finite move: lease / housing / notice → waiting Pause + project board.
        if (shapeTag == "move") {
            return bridgeMoveAfterCommit(
                store = store,
                intentionId = intentionId,
                factAnswers = factAnswers,
                nowEpochMs = nowEpochMs,
                timeZone = timeZone
            )
        }

        // OTHER §8 — business / unformed ambition: explore before execute.
        if (shapeTag == "business") {
            return bridgeBusinessAfterCommit(
                store = store,
                intentionId = intentionId,
                factAnswers = factAnswers,
                nowEpochMs = nowEpochMs,
                timeZone = timeZone
            )
        }

        // Time-limited decision create → AdaptiveBuilder decide-by / criteria Asks.
        if (shapeTag == "decision") {
            return bridgeDecisionAfterCommit(
                store = store,
                intentionId = intentionId,
                factAnswers = factAnswers,
                freeText = freeText,
                nowEpochMs = nowEpochMs,
                timeZone = timeZone
            )
        }

        // OC3 — dated physical goal: race + log method → first-phase Pause (no 8-month plan).
        if (shapeTag == "marathon") {
            return bridgePhysicalTrainingAfterCommit(
                store = store,
                intentionId = intentionId,
                factAnswers = factAnswers,
                nowEpochMs = nowEpochMs,
                timeZone = timeZone
            )
        }

        val minutes = factAnswers["practice_minutes"]?.trim()?.takeIf { it.isNotEmpty() }
        val method = factAnswers["existing_method"]?.trim()?.takeIf { it.isNotEmpty() }
        // Practice-shaped creates (language minutes wheel + method chips). Other
        // shapes keep explore-only commit without forcing a fake routine.
        if (minutes == null && method == null) return null
        if (shapeTag != "language" && minutes == null) return null

        ensureAdaptiveBuilderState(store, intentionId, shapeTag, nowEpochMs)

        var t = nowEpochMs
        fun rev(): Long =
            store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L

        fun hasKey(key: String): Boolean =
            store.snapshot().facts.any {
                it.intentionId == intentionId &&
                    it.key == key &&
                    it.status == FactStatus.ACTIVE
            }

        if (minutes != null && !hasKey(AdaptiveBuilderEngine.KEY_PRACTICE_DURATION)) {
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-duration-$intentionId",
                        text = minutes,
                        key = AdaptiveBuilderEngine.KEY_PRACTICE_DURATION,
                        rawUtterance = minutes,
                        normalizedValue = minutes
                    ),
                    sourceTurnId = "turn-create-bridge-duration-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        // Language create asks "minutes … per day" — that implies Everyday frequency
        // so PlanCompiler / suggestNextDecision can Propose without a second Ask.
        if (!hasKey(AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY)) {
            val freqText = frequencyFromCreateAnswers(minutes, method)
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-frequency-$intentionId",
                        text = freqText,
                        key = AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY,
                        rawUtterance = freqText,
                        normalizedValue = freqText
                    ),
                    sourceTurnId = "turn-create-bridge-frequency-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        if (!hasKey(AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE) && shapeTag == "language") {
            val lang = inferTargetLanguage(freeText) ?: "Spanish"
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-language-$intentionId",
                        text = lang,
                        key = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                        rawUtterance = freeText.ifBlank { lang },
                        normalizedValue = lang
                    ),
                    sourceTurnId = "turn-create-bridge-language-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        // §8.1 — relative move timing in the create sentence becomes an anchored
        // KEY_MOVE_ESTIMATE so a later freshness check can reconfirm (not re-ask language).
        if (shapeTag == "language" &&
            !hasKey(AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE) &&
            RelativeDateAnchoring.looksRelative(freeText)
        ) {
            if (!hasKey(AdaptiveBuilderEngine.KEY_MOTIVATION) &&
                freeText.contains("mov", ignoreCase = true)
            ) {
                AdaptiveBuilderEngine.applyDecision(
                    store,
                    BuilderDecision.SaveFact(
                        intentionId = intentionId,
                        expectedRevision = rev(),
                        factDraft = FactDraft(
                            factId = "fact-create-motivation-$intentionId",
                            text = "I'm moving.",
                            key = AdaptiveBuilderEngine.KEY_MOTIVATION,
                            rawUtterance = freeText,
                            normalizedValue = "moving"
                        ),
                        sourceTurnId = "turn-create-bridge-motivation-$t"
                    ),
                    t,
                    timeZone = timeZone
                )
                t += 1
            }
            val movePhrase = extractRelativePhrase(freeText) ?: freeText.trim()
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-move-$intentionId",
                        text = movePhrase,
                        key = AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE,
                        rawUtterance = freeText,
                        approximate = true
                    ),
                    sourceTurnId = "turn-create-bridge-move-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        // Safety net: if Confirm raced past pre-Confirm motivation Ask, surface it
        // here instead of OfferApproaches → Propose (Gate C2 ranking walk).
        if (shapeTag == "language" &&
            !hasKey(AdaptiveBuilderEngine.KEY_MOTIVATION) &&
            pendingMotivationAsk(store, intentionId) == null
        ) {
            val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), intentionId, t, timeZone)
                ?: return null
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, timeZone)
            if (next is BuilderDecision.Ask &&
                next.purpose == AdaptiveBuilderEngine.KEY_MOTIVATION
            ) {
                AdaptiveBuilderEngine.applyDecision(store, next, t, timeZone = timeZone)
                return null
            }
        }

        val hasSelected = store.snapshot().approaches.any {
            it.intentionId == intentionId &&
                it.status == com.intent.launcher.domain.ApproachStatus.SELECTED
        }
        if (!hasSelected) {
            val drafts = createApproachDrafts()
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.OfferApproaches(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    approachDrafts = drafts
                ),
                t,
                timeZone = timeZone
            )
            t += 1
            // Learning app / Tutor / Self / Other free-text still auto-select → Propose.
            // "Not yet" leaves a durable MULTI_CHOICE Ask so chip/typed/speech share
            // AdaptiveAnswerPath (§8.2 / Gate D2 inject) after Confirm — not create Discuss.
            val m = method?.lowercase().orEmpty()
            if (m.contains("not yet") || m.isBlank()) {
                AdaptiveBuilderEngine.applyDecision(
                    store,
                    BuilderDecision.Ask(
                        intentionId = intentionId,
                        expectedRevision = rev(),
                        questionId = "q-approaches-$intentionId",
                        whyItMatters = "Which ways of learning appeal to you?",
                        answerSchema = AnswerSchema.MULTI_CHOICE,
                        purpose = AdaptiveBuilderEngine.KEY_SELECTED_APPROACHES,
                        blocksWhat = "routine_proposal",
                        options = drafts.map { it.title },
                        priority = 60
                    ),
                    t,
                    timeZone = timeZone
                )
                return null
            }
            val selectedIds = approachIdsForMethod(method)
            AdaptiveBuilderEngine.selectApproaches(
                store = store,
                intentionId = intentionId,
                approachIds = selectedIds,
                nowEpochMs = t,
                sourceTurnId = "turn-create-bridge-select-$t",
                expectedRevision = rev(),
                modality = ConversationCoordinator.Channel.TEXT_CHIP.name
            )
            t += 1
        }

        // Advance policy until Propose (or Ask/Pause — leave next card for chat).
        repeat(6) {
            val snap = store.snapshot()
            val pending = snap.proposals.firstOrNull {
                it.intentionId == intentionId && it.status == ProposalStatus.PENDING
            }
            if (pending != null) return pending.id

            val ctx = AdaptiveBuilderEngine.projectContext(snap, intentionId, t, timeZone)
                ?: return null
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, timeZone)
            val applied = AdaptiveBuilderEngine.applyDecision(
                store, next, t, timeZone = timeZone
            )
            t += 2
            if (!applied.accepted) return null
            when (next) {
                is BuilderDecision.Propose ->
                    return store.snapshot().proposals.firstOrNull {
                        it.id == next.proposalId && it.status == ProposalStatus.PENDING
                    }?.id
                is BuilderDecision.Pause,
                is BuilderDecision.Ask -> return null
                else -> Unit
            }
        }
        return store.snapshot().proposals.firstOrNull {
            it.intentionId == intentionId && it.status == ProposalStatus.PENDING
        }?.id
    }

    /**
     * OTHER §8 — map create exploration answers onto AdaptiveBuilder keys, then
     * advance to Explore ideas OfferAction / Pause. Never invents a launch
     * checklist, revenue tracker, or business-ops Propose.
     */
    private fun bridgeBusinessAfterCommit(
        store: IntentionFlowStore,
        intentionId: String,
        factAnswers: Map<String, String>,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): String? {
        ensureAdaptiveBuilderState(store, intentionId, "business", nowEpochMs)
        var t = nowEpochMs
        fun rev(): Long =
            store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L

        fun hasKey(key: String): Boolean =
            store.snapshot().facts.any {
                it.intentionId == intentionId &&
                    it.key == key &&
                    it.status == FactStatus.ACTIVE
            }

        fun saveExplore(key: String, text: String, suffix: String) {
            if (hasKey(key) || text.isBlank()) return
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-$suffix-$intentionId",
                        text = text,
                        key = key,
                        rawUtterance = text,
                        normalizedValue = text
                    ),
                    sourceTurnId = "turn-create-bridge-$suffix-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        saveExplore(
            AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS,
            factAnswers[AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS]?.trim().orEmpty(),
            "skills"
        )
        saveExplore(
            AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET,
            factAnswers[AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET]?.trim().orEmpty(),
            "weekly-time"
        )
        saveExplore(
            AdaptiveBuilderEngine.KEY_EXPLORATION_MODE,
            factAnswers[AdaptiveBuilderEngine.KEY_EXPLORATION_MODE]?.trim().orEmpty(),
            "explore-mode"
        )

        repeat(8) {
            val ctx = AdaptiveBuilderEngine.projectContext(
                store.snapshot(), intentionId, t, timeZone
            ) ?: return null
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, timeZone)
            val applied = AdaptiveBuilderEngine.applyDecision(
                store, next, t, timeZone = timeZone
            )
            t += 2
            if (!applied.accepted) return null
            when (next) {
                is BuilderDecision.Pause -> return null
                is BuilderDecision.Propose -> return null // honesty: no launch Propose
                is BuilderDecision.Ask -> return null
                is BuilderDecision.OfferAction -> {
                    // Explore ideas / validation experiment card applied — stop so UI shows it.
                    return null
                }
                else -> Unit
            }
        }
        return null
    }

    /**
     * Map create move answers onto AdaptiveBuilder keys, seed a dependency board,
     * then advance to waiting Pause — never invents viewings or a secured place.
     */
    private fun bridgeMoveAfterCommit(
        store: IntentionFlowStore,
        intentionId: String,
        factAnswers: Map<String, String>,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): String? {
        ensureAdaptiveBuilderState(store, intentionId, "move", nowEpochMs)
        var t = nowEpochMs
        fun rev(): Long =
            store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L

        fun hasKey(key: String): Boolean =
            store.snapshot().facts.any {
                it.intentionId == intentionId &&
                    it.key == key &&
                    it.status == FactStatus.ACTIVE
            }

        fun saveMoveFact(key: String, text: String, suffix: String) {
            if (hasKey(key) || text.isBlank()) return
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-$suffix-$intentionId",
                        text = text,
                        key = key,
                        rawUtterance = text,
                        normalizedValue = text
                    ),
                    sourceTurnId = "turn-create-bridge-$suffix-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        saveMoveFact(
            AdaptiveBuilderEngine.KEY_LEASE_DEADLINE,
            factAnswers[AdaptiveBuilderEngine.KEY_LEASE_DEADLINE]?.trim().orEmpty(),
            "lease"
        )
        saveMoveFact(
            AdaptiveBuilderEngine.KEY_HOUSING_REQUIREMENTS,
            factAnswers[AdaptiveBuilderEngine.KEY_HOUSING_REQUIREMENTS]?.trim().orEmpty(),
            "housing"
        )
        saveMoveFact(
            AdaptiveBuilderEngine.KEY_NOTICE_STATUS,
            factAnswers[AdaptiveBuilderEngine.KEY_NOTICE_STATUS]?.trim().orEmpty(),
            "notice"
        )

        MoveScenario.seedDependencyPlan(store, t, intentionId)

        repeat(8) {
            val ctx = AdaptiveBuilderEngine.projectContext(
                store.snapshot(), intentionId, t, timeZone
            ) ?: return null
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, timeZone)
            val applied = AdaptiveBuilderEngine.applyDecision(
                store, next, t, timeZone = timeZone
            )
            t += 2
            if (!applied.accepted) return null
            when (next) {
                is BuilderDecision.Pause -> return null
                is BuilderDecision.Propose -> return null
                is BuilderDecision.Ask -> return null
                else -> Unit
            }
        }
        return null
    }

    /**
     * Time-limited decision create: seed decide-by / criteria facts, then Ask or Pause.
     * Never Propose accept/decline.
     */
    private fun bridgeDecisionAfterCommit(
        store: IntentionFlowStore,
        intentionId: String,
        factAnswers: Map<String, String>,
        freeText: String,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): String? {
        ensureAdaptiveBuilderState(store, intentionId, "decision", nowEpochMs)
        var t = nowEpochMs
        fun rev(): Long =
            store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L

        fun hasKey(key: String): Boolean =
            store.snapshot().facts.any {
                it.intentionId == intentionId &&
                    it.key == key &&
                    it.status == FactStatus.ACTIVE
            }

        fun saveDecisionFact(key: String, text: String, suffix: String) {
            if (hasKey(key) || text.isBlank()) return
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-$suffix-$intentionId",
                        text = text,
                        key = key,
                        rawUtterance = text,
                        normalizedValue = text
                    ),
                    sourceTurnId = "turn-create-bridge-$suffix-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        val decideBy = factAnswers[AdaptiveBuilderEngine.KEY_DECIDE_BY_DATE]
            ?.trim().orEmpty().ifBlank {
                // Seed text often already carries “ten days” / deadline language.
                when {
                    Regex("""(?i)\b\d+\s*days?\b""").containsMatchIn(freeText) ||
                        freeText.contains("ten days", ignoreCase = true) ->
                        freeText.trim().ifBlank { "In ten days" }
                    else -> ""
                }
            }
        saveDecisionFact(AdaptiveBuilderEngine.KEY_DECIDE_BY_DATE, decideBy, "decide-by")
        saveDecisionFact(
            AdaptiveBuilderEngine.KEY_DECISION_CRITERIA,
            factAnswers[AdaptiveBuilderEngine.KEY_DECISION_CRITERIA]?.trim().orEmpty(),
            "criteria"
        )
        saveDecisionFact(
            AdaptiveBuilderEngine.KEY_DECISION_ALTERNATIVES,
            factAnswers[AdaptiveBuilderEngine.KEY_DECISION_ALTERNATIVES]?.trim().orEmpty(),
            "alts"
        )
        saveDecisionFact(
            AdaptiveBuilderEngine.KEY_DECISION_UNKNOWNS,
            factAnswers[AdaptiveBuilderEngine.KEY_DECISION_UNKNOWNS]?.trim().orEmpty(),
            "unknowns"
        )

        // Advance: surface next Ask (criteria / alternatives / unknowns / ask-source)
        // or Pause — never Propose an accept/decline.
        repeat(8) {
            val ctx = AdaptiveBuilderEngine.projectContext(
                store.snapshot(), intentionId, t, timeZone
            ) ?: return null
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, timeZone)
            val applied = AdaptiveBuilderEngine.applyDecision(
                store, next, t, timeZone = timeZone
            )
            t += 2
            if (!applied.accepted) return null
            when (next) {
                is BuilderDecision.Pause -> return null
                is BuilderDecision.Propose -> return null
                is BuilderDecision.Ask -> return null
                else -> Unit
            }
        }
        return null
    }

    /**
     * OC3 — marathon / dated physical create: race status + activity log method
     * → AdaptiveBuilder first-phase Pause. Never invents an 8-month workout plan.
     */
    private fun bridgePhysicalTrainingAfterCommit(
        store: IntentionFlowStore,
        intentionId: String,
        factAnswers: Map<String, String>,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): String? {
        ensureAdaptiveBuilderState(store, intentionId, "marathon", nowEpochMs)
        var t = nowEpochMs
        fun rev(): Long =
            store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L

        fun hasKey(key: String): Boolean =
            store.snapshot().facts.any {
                it.intentionId == intentionId &&
                    it.key == key &&
                    it.status == FactStatus.ACTIVE
            }

        fun saveKeyed(key: String, text: String, suffix: String) {
            if (hasKey(key) || text.isBlank()) return
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-$suffix-$intentionId",
                        text = text,
                        key = key,
                        rawUtterance = text,
                        normalizedValue = text
                    ),
                    sourceTurnId = "turn-create-bridge-$suffix-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        saveKeyed(
            AdaptiveBuilderEngine.KEY_RACE_STATUS,
            factAnswers[AdaptiveBuilderEngine.KEY_RACE_STATUS]?.trim().orEmpty(),
            "race"
        )
        saveKeyed(
            AdaptiveBuilderEngine.KEY_ACTIVITY_LOG_METHOD,
            factAnswers[AdaptiveBuilderEngine.KEY_ACTIVITY_LOG_METHOD]?.trim().orEmpty(),
            "activity-log"
        )

        repeat(8) {
            val ctx = AdaptiveBuilderEngine.projectContext(
                store.snapshot(), intentionId, t, timeZone
            ) ?: return null
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, timeZone)
            val applied = AdaptiveBuilderEngine.applyDecision(
                store, next, t, timeZone = timeZone
            )
            t += 2
            if (!applied.accepted) return null
            when (next) {
                is BuilderDecision.Pause -> {
                    // Surface first-phase guidance without fabricating workouts.
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-first-phase-$intentionId-$t",
                            intentionId = intentionId,
                            role = "agent",
                            text =
                                "I suggest a short first phase: choose a suitable race, " +
                                    "establish your current running baseline, and build " +
                                    "consistency on your available days. We can review it " +
                                    "before prescribing later phases. No eight-month plan " +
                                    "invented — log runs with the method you chose.",
                            nowEpochMs = t
                        )
                    )
                    return null
                }
                is BuilderDecision.Propose -> return null
                is BuilderDecision.Ask -> return null
                else -> Unit
            }
        }
        return null
    }

    /**
     * Map create eligibility answers onto AdaptiveBuilder keys, then advance to
     * research Pause — never invents a legal checklist or Propose.
     *
     * @return always null (no pending proposal; Pause leaves discovery resumable)
     */
    private fun bridgeResearchAfterCommit(
        store: IntentionFlowStore,
        intentionId: String,
        factAnswers: Map<String, String>,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): String? {
        ensureAdaptiveBuilderState(store, intentionId, "research", nowEpochMs)
        var t = nowEpochMs
        fun rev(): Long =
            store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L

        fun hasKey(key: String): Boolean =
            store.snapshot().facts.any {
                it.intentionId == intentionId &&
                    it.key == key &&
                    it.status == FactStatus.ACTIVE
            }

        fun saveEligibility(key: String, text: String, suffix: String) {
            if (hasKey(key) || text.isBlank()) return
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-$suffix-$intentionId",
                        text = text,
                        key = key,
                        rawUtterance = text,
                        normalizedValue = text
                    ),
                    sourceTurnId = "turn-create-bridge-$suffix-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        saveEligibility(
            AdaptiveBuilderEngine.KEY_CITIZENSHIP,
            factAnswers[AdaptiveBuilderEngine.KEY_CITIZENSHIP]?.trim().orEmpty(),
            "citizenship"
        )
        saveEligibility(
            AdaptiveBuilderEngine.KEY_EMPLOYMENT_ARRANGEMENT,
            factAnswers[AdaptiveBuilderEngine.KEY_EMPLOYMENT_ARRANGEMENT]?.trim().orEmpty(),
            "employment"
        )
        saveEligibility(
            AdaptiveBuilderEngine.KEY_RESIDENCE_DURATION,
            factAnswers[AdaptiveBuilderEngine.KEY_RESIDENCE_DURATION]?.trim().orEmpty(),
            "residence"
        )

        // Advance: Ask remaining eligibility if needed, then Pause (no Propose).
        repeat(8) {
            val ctx = AdaptiveBuilderEngine.projectContext(
                store.snapshot(), intentionId, t, timeZone
            ) ?: return null
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, timeZone)
            val applied = AdaptiveBuilderEngine.applyDecision(
                store, next, t, timeZone = timeZone
            )
            t += 2
            if (!applied.accepted) return null
            when (next) {
                is BuilderDecision.Pause -> return null
                is BuilderDecision.Propose -> return null // honesty: never activate legal Propose
                is BuilderDecision.Ask -> return null // leave next eligibility card for chat
                else -> Unit
            }
        }
        return null
    }

    private fun ensureAdaptiveBuilderState(
        store: IntentionFlowStore,
        intentionId: String,
        shapeTag: String,
        nowEpochMs: Long
    ) {
        val existing = store.snapshot().adaptiveBuilderStates
            .firstOrNull { it.intentionId == intentionId }
        if (existing != null) return
        store.apply(
            FlowCommand.SetAdaptiveBuilderState(
                state = AdaptiveBuilderState(
                    intentionId = intentionId,
                    revision = 0L,
                    typeFacets = AdaptiveBuilderEngine.facetsForShape(shapeTag),
                    lifecycleHint = "discovery",
                    updatedAtEpochMs = nowEpochMs
                ),
                nowEpochMs = nowEpochMs
            )
        )
    }

    private fun frequencyFromCreateAnswers(minutes: String?, method: String?): String {
        val blob = listOfNotNull(minutes, method).joinToString(" ").lowercase()
        return when {
            blob.contains("3 day") || blob.contains("three day") -> "3 days per week"
            blob.contains("week") && !blob.contains("per day") -> "3 days per week"
            else -> "Everyday"
        }
    }

    private fun inferTargetLanguage(freeText: String): String? {
        val t = freeText.lowercase()
        return when {
            t.contains("spanish") -> "Spanish"
            t.contains("french") -> "French"
            t.contains("german") -> "German"
            t.contains("italian") -> "Italian"
            t.contains("japanese") -> "Japanese"
            t.contains("chinese") || t.contains("mandarin") -> "Chinese"
            else -> null
        }
    }

    /** Pull the relative-date substring used for [RelativeDateAnchoring.anchor]. */
    internal fun extractRelativePhrase(freeText: String): String? {
        val matcher = RELATIVE_PHRASE.matcher(freeText.trim())
        if (!matcher.find()) return null
        return matcher.group()?.trim()
    }

    private val RELATIVE_PHRASE = java.util.regex.Pattern.compile(
        """(?i)(?:in\s+)?(?:about\s+|around\s+|roughly\s+|approx(?:imately)?\s+)?""" +
            """(?:\d+|one|two|three|four|five|six|seven|eight|nine|ten|twelve)\s+""" +
            """(?:days?|weeks?|months?|years?)"""
    )

    private fun createApproachDrafts(): List<ApproachDraft> = listOf(
        ApproachDraft(
            approachId = "approach-ai-practice",
            title = "AI conversation practice",
            summary = "Guided Spanish practice in INTENT.",
            actor = ApproachActor.INTENT,
            capabilityNote = "Guided session objective + typed result; shared agent history."
        ),
        ApproachDraft(
            approachId = "approach-learning-app",
            title = "Learning app",
            summary = "Launch an installed language app.",
            actor = ApproachActor.APP,
            capabilityNote = "Launch and optional foreground timing — not lesson completion."
        ),
        ApproachDraft(
            approachId = "approach-offline",
            title = "Offline practice",
            summary = "Practice with a person or offline materials.",
            actor = ApproachActor.PERSON
        ),
        ApproachDraft(
            approachId = "approach-other",
            title = "Something else",
            summary = "Open response for another method.",
            actor = ApproachActor.EXTERNAL
        )
    )

    /** Map create chip / Other free text onto AdaptiveBuilder approach ids. */
    internal fun approachIdsForMethod(method: String?): List<String> {
        val m = method?.lowercase().orEmpty()
        return when {
            m.contains("tutor") || m.contains("class") -> listOf("approach-offline")
            m.contains("app") || m.contains("duolingo") -> listOf("approach-learning-app")
            m.contains("self") -> listOf("approach-ai-practice")
            m.contains("not yet") || m.isBlank() -> listOf("approach-other")
            else -> listOf("approach-other")
        }
    }

    private fun projectSeedHonestyChoiceCard(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        honesty: CreatePathSeedHonesty.Honesty
    ) {
        val promptId = honesty.firstChoicePromptId ?: return
        val question = honesty.firstChoiceQuestion ?: return
        deactivatePriorBuilderCards(store, nowEpochMs)
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-create-card-$nowEpochMs",
                intentionId = SESSION_INTENTION_ID,
                role = "agent",
                text = question,
                nowEpochMs = nowEpochMs,
                blocks = listOf(
                    BuilderAnswerCard.choiceBlock(
                        blockId = "block-create-choice-$nowEpochMs",
                        title = question,
                        body = "Pick one below, or choose Other and type your own answer.",
                        spec = BuilderAnswerCard.ChoiceSpec(
                            promptId = promptId,
                            options = honesty.firstChoiceOptions,
                            includeDeferral = false
                        )
                    )
                )
            )
        )
    }

    /**
     * After language minutes+method, mint engine motivation Ask (chips + Other)
     * before the Confirm card so AVD can capture consequence ranking.
     * @return true when an Ask was projected (caller should not show Confirm yet)
     */
    private fun surfaceMotivationAskBeforeConfirm(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val draft = store.snapshot().builderDraft
        val shape = draft.shapeTag ?: draft.suggestedShapeTag
        if (shape != "language") return false

        fun hasKey(key: String): Boolean =
            store.snapshot().facts.any {
                it.intentionId == SESSION_INTENTION_ID &&
                    it.key == key &&
                    it.status == FactStatus.ACTIVE
            }

        if (hasKey(AdaptiveBuilderEngine.KEY_MOTIVATION)) return false
        if (pendingMotivationAsk(store) != null) return false

        ensureAdaptiveBuilderState(store, SESSION_INTENTION_ID, shape, nowEpochMs)

        var t = nowEpochMs
        fun rev(): Long =
            store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == SESSION_INTENTION_ID }?.revision ?: 0L

        // Engine motivation Ask requires target_language (inferred from create text).
        if (!hasKey(AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE)) {
            val lang = inferTargetLanguage(draft.freeText) ?: "Spanish"
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = SESSION_INTENTION_ID,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-create-pre-lang-$SESSION_INTENTION_ID",
                        text = lang,
                        key = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                        rawUtterance = draft.freeText.ifBlank { lang },
                        normalizedValue = lang
                    ),
                    sourceTurnId = "turn-create-pre-language-$t"
                ),
                t,
                timeZone = timeZone
            )
            t += 1
        }

        val ctx = AdaptiveBuilderEngine.projectContext(
            store.snapshot(), SESSION_INTENTION_ID, t, timeZone
        ) ?: return false
        val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, timeZone)
        if (next !is BuilderDecision.Ask ||
            next.purpose != AdaptiveBuilderEngine.KEY_MOTIVATION
        ) {
            return false
        }
        deactivatePriorBuilderCards(store, t)
        val applied = AdaptiveBuilderEngine.applyDecision(store, next, t + 1, timeZone = timeZone)
        return applied.accepted
    }

    private fun pendingMotivationAsk(
        store: IntentionFlowStore,
        intentionId: String = SESSION_INTENTION_ID
    ): String? {
        val state = store.snapshot().adaptiveBuilderStates
            .firstOrNull { it.intentionId == intentionId } ?: return null
        val qid = state.currentQuestionId ?: return null
        val question = store.snapshot().openQuestions.firstOrNull { it.id == qid } ?: return null
        if (question.purpose != AdaptiveBuilderEngine.KEY_MOTIVATION) return null
        if (question.state == OpenQuestionState.ANSWERED) return null
        return qid
    }

    /**
     * Answer the in-create motivation Ask without advancing to move Ask /
     * OfferApproaches — Confirm card comes next; bridge still Proposes after Confirm.
     */
    private fun answerCreateMotivation(
        store: IntentionFlowStore,
        raw: String,
        modality: String,
        nowEpochMs: Long,
        sourceLabel: String,
        userTurnAlreadyPosted: Boolean,
        timeZone: TimeZone = TimeZone.getDefault()
    ): SubmitResult {
        val qid = pendingMotivationAsk(store)
            ?: return SubmitResult(false, "No motivation ask pending.", sourceLabel)
        val question = store.snapshot().openQuestions.firstOrNull { it.id == qid }
            ?: return SubmitResult(false, "Motivation question missing.", sourceLabel)
        val state = store.snapshot().adaptiveBuilderStates
            .firstOrNull { it.intentionId == SESSION_INTENTION_ID }
            ?: return SubmitResult(false, "No adaptive builder state.", sourceLabel)

        when (val sel = ChoiceOptions.resolveSelection(raw, question.optionIds)) {
            ChoiceOptions.Selection.Empty ->
                return SubmitResult(false, "Empty answer.", sourceLabel)
            ChoiceOptions.Selection.NeedsFreeText -> {
                if (!userTurnAlreadyPosted) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-create-mot-other-$nowEpochMs",
                            intentionId = SESSION_INTENTION_ID,
                            role = "user",
                            text = raw,
                            nowEpochMs = nowEpochMs,
                            modality = modality
                        )
                    )
                }
                postAgent(
                    store,
                    nowEpochMs + 1,
                    "Type your answer in your own words below."
                )
                return SubmitResult(
                    ok = true,
                    honestyNote = "Other selected — awaiting free text.",
                    sourceLabel = sourceLabel
                )
            }
            ChoiceOptions.Selection.NotSure,
            ChoiceOptions.Selection.Skip -> {
                AdaptiveAnswerPath.deferQuestion(
                    store = store,
                    intentionId = SESSION_INTENTION_ID,
                    questionId = qid,
                    label = raw,
                    modality = modality,
                    nowEpochMs = nowEpochMs + 1,
                    expectedRevision = state.revision
                )
                deactivatePriorBuilderCards(store, nowEpochMs + 2)
                postAgent(
                    store,
                    nowEpochMs + 3,
                    "Does this look right? Confirm to save the intention, or keep discussing."
                )
                projectConfirmCard(store, nowEpochMs + 4)
                return SubmitResult(true, "Motivation deferred — ready to confirm.", sourceLabel)
            }
            is ChoiceOptions.Selection.Chosen,
            is ChoiceOptions.Selection.FreeText -> {
                val value = when (sel) {
                    is ChoiceOptions.Selection.Chosen -> sel.value
                    is ChoiceOptions.Selection.FreeText -> sel.value
                    else -> raw
                }
                if (!userTurnAlreadyPosted) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-create-mot-user-$nowEpochMs",
                            intentionId = SESSION_INTENTION_ID,
                            role = "user",
                            text = value,
                            nowEpochMs = nowEpochMs,
                            modality = modality
                        )
                    )
                }
                val apply = AdaptiveBuilderEngine.answerQuestion(
                    store,
                    AnswerQuestion(
                        questionId = qid,
                        intentionId = SESSION_INTENTION_ID,
                        normalizedValue = value,
                        rawValue = value,
                        modality = modality,
                        sourceTurnId = "turn-create-mot-ans-$nowEpochMs",
                        answeredAtEpochMs = nowEpochMs + 1,
                        idempotencyKey = "idem-create-mot-$qid-${value.hashCode()}",
                        expectedRevision = state.revision
                    ),
                    factKey = AdaptiveBuilderEngine.KEY_MOTIVATION,
                    timeZone = timeZone
                )
                if (!apply.accepted) {
                    return SubmitResult(false, apply.reason, sourceLabel)
                }
                // Do not auto-advance to move Ask — Confirm → bridge → Propose.
                deactivatePriorBuilderCards(store, nowEpochMs + 2)
                postAgent(
                    store,
                    nowEpochMs + 3,
                    "Does this look right? Confirm to save the intention, or keep discussing."
                )
                projectConfirmCard(store, nowEpochMs + 4)
                return SubmitResult(true, "Motivation recorded — ready to confirm.", sourceLabel)
            }
        }
    }

    private fun projectNextPromptCard(store: IntentionFlowStore, nowEpochMs: Long) {
        val draft = store.snapshot().builderDraft
        val prompt = IntentionBuilder.nextPrompt(draft) ?: run {
            projectConfirmCard(store, nowEpochMs)
            return
        }
        deactivatePriorBuilderCards(store, nowEpochMs)
        val block: ResponseBlock = when {
            prompt.quantityChoices != null && prompt.quantityUnitLabel != null -> {
                val values = prompt.quantityChoices
                val min = values.minOrNull() ?: BuilderAnswerCard.DEFAULT_MIN
                val max = values.maxOrNull() ?: BuilderAnswerCard.DEFAULT_MAX
                val default = when {
                    BuilderAnswerCard.DEFAULT_VALUE in values -> BuilderAnswerCard.DEFAULT_VALUE
                    else -> values.getOrElse(values.size / 2) { min }
                }
                BuilderAnswerCard.quantityBlock(
                    blockId = "block-create-qty-$nowEpochMs",
                    title = prompt.question,
                    body = "Scroll every whole number, or use the steppers.",
                    spec = BuilderAnswerCard.QuantitySpec(
                        promptId = prompt.id,
                        unitLabel = prompt.quantityUnitLabel,
                        min = min,
                        max = max,
                        defaultValue = default,
                        summaryTemplate = prompt.quantitySummaryTemplate
                    )
                )
            }
            else -> {
                val options = prompt.choiceOptions.orEmpty().ifEmpty {
                    listOf("Not sure yet")
                }
                BuilderAnswerCard.choiceBlock(
                    blockId = "block-create-choice-$nowEpochMs",
                    title = prompt.question,
                    body = "Pick one below, or choose Other and type your own answer.",
                    spec = BuilderAnswerCard.ChoiceSpec(
                        promptId = prompt.id,
                        options = options,
                        includeDeferral = false
                    )
                )
            }
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-create-card-$nowEpochMs",
                intentionId = SESSION_INTENTION_ID,
                role = "agent",
                text = prompt.question,
                nowEpochMs = nowEpochMs,
                blocks = listOf(block)
            )
        )
    }

    private fun projectConfirmCard(store: IntentionFlowStore, nowEpochMs: Long) {
        deactivatePriorBuilderCards(store, nowEpochMs)
        val draft = store.snapshot().builderDraft
        val summary = buildString {
            append(draft.title.ifBlank { "Your intention" })
            if (draft.statement.isNotBlank()) append(" — ").append(draft.statement)
            draft.factAnswers.values.forEach { append("\n• ").append(it) }
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-create-confirm-$nowEpochMs",
                intentionId = SESSION_INTENTION_ID,
                role = "agent",
                text = "Does this look right?",
                nowEpochMs = nowEpochMs,
                blocks = listOf(
                    BuilderAnswerCard.confirmBlock(
                        blockId = "block-create-confirm-$nowEpochMs",
                        title = "Does this look right?",
                        body = summary
                    )
                )
            )
        )
    }

    private fun deactivatePriorBuilderCards(store: IntentionFlowStore, nowEpochMs: Long) {
        // AVD defect 2: mark prior NumberWheel / choice cards inactive so they
        // cannot stay interactive beside the next question.
        store.apply(
            FlowCommand.DeactivateBuilderAnswerCards(
                intentionId = SESSION_INTENTION_ID,
                nowEpochMs = nowEpochMs
            )
        )
    }

    private fun latestActiveBuilderCard(store: IntentionFlowStore): ResponseBlock? =
        store.snapshot().turns
            .asReversed()
            .asSequence()
            .filter { it.intentionId == SESSION_INTENTION_ID }
            .flatMap { it.blocks.asReversed() }
            .firstOrNull { BuilderAnswerCard.decode(it) != null }

    private fun ensureSession(store: IntentionFlowStore, nowEpochMs: Long) {
        if (store.snapshot().intentions.none { it.id == SESSION_INTENTION_ID }) {
            store.apply(
                FlowCommand.SeedExploring(
                    intentionId = SESSION_INTENTION_ID,
                    title = "New intention",
                    statement = "Starting a new intention",
                    shapeTag = "other",
                    nowEpochMs = nowEpochMs
                )
            )
        } else if (store.snapshot().activeIntentionId != SESSION_INTENTION_ID) {
            store.apply(
                FlowCommand.SetActiveIntention(
                    intentionId = SESSION_INTENTION_ID,
                    nowEpochMs = nowEpochMs
                )
            )
        }
    }

    private fun postAgent(store: IntentionFlowStore, nowEpochMs: Long, text: String) {
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-create-agent-$nowEpochMs",
                intentionId = SESSION_INTENTION_ID,
                role = "agent",
                text = text,
                nowEpochMs = nowEpochMs,
                modality = ConversationCoordinator.Channel.FREE_TEXT.name
            )
        )
    }

    private fun looksLikeConfirm(text: String): Boolean {
        val t = text.lowercase()
        return t == "yes" || t == "y" || t.contains("confirm") ||
            t.contains("looks right") || t.contains("looks good") || t == "ok" ||
            t == "okay"
    }

    private fun looksLikeKeepDiscussing(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("keep discussing") || t.contains("not yet") ||
            t.contains("change") || t.contains("edit")
    }
}
