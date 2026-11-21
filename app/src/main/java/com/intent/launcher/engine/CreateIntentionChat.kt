package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.store.IntentionFlowStore

/**
 * Chat-first create path (F1/F2): Home **Start a new intention** opens Conversation
 * with a live create session. Practice frequency and similar prompts render as
 * in-chat cards (NumberWheel / chips+Other) — not a disconnected Builder wizard.
 *
 * Structural answers always go through [IntentionBuilder] / [AnswerBuilderPrompt].
 * When live OpenAI is opted in, prose may be enriched; otherwise the path stays
 * honest local heuristics.
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
        val committedIntentionId: String? = null
    )

    fun isCreateSession(intention: Intention?): Boolean =
        intention?.id == SESSION_INTENTION_ID

    fun isCreateSessionActive(store: IntentionFlowStore): Boolean {
        val snap = store.snapshot()
        return snap.activeIntentionId == SESSION_INTENTION_ID ||
            snap.builderDraft.active ||
            snap.intentions.any { it.id == SESSION_INTENTION_ID }
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

        val existing = store.snapshot().intentions.firstOrNull { it.id == SESSION_INTENTION_ID }
        if (existing == null) {
            store.apply(
                FlowCommand.SeedExploring(
                    intentionId = SESSION_INTENTION_ID,
                    title = "New intention",
                    statement = "Starting a new intention",
                    shapeTag = "other",
                    nowEpochMs = nowEpochMs
                )
            )
        } else {
            store.apply(
                FlowCommand.SetActiveIntention(
                    intentionId = SESSION_INTENTION_ID,
                    nowEpochMs = nowEpochMs
                )
            )
        }

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
                    nowEpochMs = nowEpochMs,
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

        val prompt = IntentionBuilder.nextPrompt(draft)
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

        IntentionBuilder.answerPrompt(store, prompt.id, trimmed, nowEpochMs + 1, registry)
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
        var prose = "I’ve treated this as a ${shapeLabel.lowercase()} intention. " +
            "Next I’ll ask one useful question at a time in this chat."

        val live = registry.canUseLiveLlm()
        val apiKey = access.resolveApiKey()
        if (live && client != null && !apiKey.isNullOrBlank()) {
            liveAttempted = true
            val pack = ContextAssembler.assemble(
                snapshot = store.snapshot(),
                intention = store.snapshot().intentions.firstOrNull { it.id == SESSION_INTENTION_ID },
                registry = registry,
                nowEpochMs = nowEpochMs
            ) + "\nCreate-intention mode: ask consequential discovery questions one at a time. " +
                "Do not invent a full plan. Do not claim observed app progress."
            when (
                val outcome = client.complete(
                    apiKey = apiKey,
                    userMessage = description,
                    contextPack = pack
                )
            ) {
                is OpenAiDiscussClient.Outcome.Success -> {
                    prose = OpenAiDiscussClient.formatAgentTurn(outcome.completion)
                }
                is OpenAiDiscussClient.Outcome.Failure -> {
                    prose = "$prose\n\n(${outcome.honestMessage})"
                }
            }
        } else if (sourceLabel == LiveLlmProbe.LOCAL_HEURISTIC_LABEL) {
            prose = "$prose (Local heuristics — not a live model.)"
        }

        postAgent(store, nowEpochMs + 3, prose)
        projectNextPromptCard(store, nowEpochMs + 4)
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
                postAgent(
                    store,
                    nowEpochMs,
                    "Does this look right? Confirm to save the intention, or keep discussing."
                )
                projectConfirmCard(store, nowEpochMs + 1)
                SubmitResult(true, "Ready to confirm.", sourceLabel)
            }
            BuilderPhase.MISSING_FACTS -> {
                val next = IntentionBuilder.nextPrompt(draft)
                postAgent(
                    store,
                    nowEpochMs,
                    next?.question ?: "One more useful detail when you have it."
                )
                projectNextPromptCard(store, nowEpochMs + 1)
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
        val title = store.snapshot().intentions
            .firstOrNull { it.id == SESSION_INTENTION_ID }?.title ?: "Your intention"
        postAgent(
            store,
            nowEpochMs + 2,
            "Saved “$title” as exploring. We can keep refining it here — " +
                "nothing is scheduled until you approve a plan."
        )
        return SubmitResult(
            ok = true,
            honestyNote = "Intention created.",
            sourceLabel = sourceLabel,
            committedIntentionId = SESSION_INTENTION_ID
        )
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
                    body = "Pick one, or choose Other to type your own answer.",
                    spec = BuilderAnswerCard.ChoiceSpec(
                        promptId = prompt.id,
                        options = options
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

    private fun deactivatePriorBuilderCards(@Suppress("UNUSED_PARAMETER") store: IntentionFlowStore, @Suppress("UNUSED_PARAMETER") nowEpochMs: Long) {
        // ResponseBlock.active is immutable in-store; new cards supersede visually via
        // ConversationScreen taking the latest active builder-decoded block.
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
