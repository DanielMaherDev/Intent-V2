package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderState
import com.intent.launcher.domain.AnswerQuestion
import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.domain.RelativeDateAnchoring
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone

/**
 * Gate D unified answer path — spoken and tapped answers resolve to one
 * [AnswerQuestion] (or an honest deferral / stale refresh) for the active card.
 */
object AdaptiveAnswerPath {

    const val VOICE_CONFIRM_YES = "Yes, that's right"
    const val VOICE_CONFIRM_NO = "No, let me rephrase"
    const val VOICE_CONFIRM_LINK = "voice-confirm"

    data class Result(
        val ok: Boolean,
        val honestyNote: String,
        /** When true, UI should prompt for free text (Other / uncertain voice). */
        val needsFreeText: Boolean = false,
        /** When true, UI should show a Confirm card before committing. */
        val needsVoiceConfirm: Boolean = false,
        val accepted: Boolean = false
    )

    /**
     * Route a chip / spoken / free-text value for the active OpenQuestion.
     * Create-session IntentionBuilder answers stay on [CreateIntentionChat];
     * this path is for durable adaptive-builder intentions.
     */
    fun submit(
        store: IntentionFlowStore,
        intentionId: String,
        rawValue: String,
        modality: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        voiceUncertain: Boolean = false,
        expectedQuestionId: String? = null
    ): Result {
        val trimmed = rawValue.trim()
        if (trimmed.isEmpty()) {
            return Result(false, "Empty answer.")
        }
        // Gate D2 — Yes/No on a pending voice-confirm card commits or abandons the candidate.
        resolveVoiceConfirmIfPending(
            store = store,
            intentionId = intentionId,
            label = trimmed,
            modality = modality,
            nowEpochMs = nowEpochMs,
            timeZone = timeZone
        )?.let { return it }

        val snap = store.snapshot()
        val state = snap.adaptiveBuilderStates.firstOrNull { it.intentionId == intentionId }
            ?: return Result(false, "No adaptive builder state for this intention.")
        val activeId = state.currentQuestionId
            ?: return Result(false, "No active question — refresh the conversation.")

        if (expectedQuestionId != null && expectedQuestionId != activeId) {
            return Result(
                ok = true,
                honestyNote = "That card is no longer active. The conversation was refreshed — " +
                    "answer the current question.",
                accepted = false
            )
        }

        val question = snap.openQuestions.firstOrNull { it.id == activeId }
            ?: return Result(false, "Active question missing — refresh.")

        if (voiceUncertain) {
            projectVoiceConfirm(store, intentionId, activeId, listOf(trimmed), nowEpochMs)
            return Result(
                ok = true,
                honestyNote = "Voice was uncertain — confirm before saving.",
                needsVoiceConfirm = true,
                accepted = false
            )
        }

        // §8.1 / §8.5 — freshness reconfirm chips.
        if (question.purpose == "reconfirm_move_estimate") {
            val lower = trimmed.lowercase()
            when {
                lower.contains("still about") || lower == "yes" || lower.contains("still right") -> {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-reconfirm-ok-$nowEpochMs",
                            intentionId = intentionId,
                            role = "user",
                            text = trimmed,
                            nowEpochMs = nowEpochMs,
                            modality = modality
                        )
                    )
                    store.apply(
                        FlowCommand.AnswerOpenQuestion(
                            questionId = activeId,
                            intentionId = intentionId,
                            answerText = "Still about right",
                            nowEpochMs = nowEpochMs,
                            sourceTurnId = "turn-reconfirm-ok-$nowEpochMs",
                            modality = modality,
                            idempotencyKey = "idem-reconfirm-ok-$nowEpochMs",
                            factId = null
                        )
                    )
                    bumpClearQuestion(store, intentionId, nowEpochMs)
                    return Result(
                        ok = true,
                        honestyNote = "Move timing confirmed — prior estimate unchanged.",
                        accepted = true
                    )
                }
                lower.contains("update") || lower.contains("change the date") -> {
                    return Result(
                        ok = true,
                        honestyNote = "Update the date — type the new estimate below " +
                            "(for example “in about six months”).",
                        needsFreeText = true
                    )
                }
                RelativeDateAnchoring.looksRelative(trimmed) -> {
                    val corrected = AdaptiveBuilderEngine.correctMoveEstimate(
                        store = store,
                        intentionId = intentionId,
                        newUtterance = trimmed,
                        nowEpochMs = nowEpochMs,
                        timeZone = timeZone,
                        sourceTurnId = "turn-reconfirm-correct-$nowEpochMs"
                    )
                    if (!corrected.accepted) {
                        return Result(false, corrected.reason)
                    }
                    // Close the reconfirm Ask without saving a duplicate purpose-key fact.
                    store.apply(
                        FlowCommand.AnswerOpenQuestion(
                            questionId = activeId,
                            intentionId = intentionId,
                            answerText = trimmed,
                            nowEpochMs = nowEpochMs,
                            sourceTurnId = "turn-reconfirm-correct-$nowEpochMs",
                            modality = modality,
                            idempotencyKey = "idem-reconfirm-correct-$nowEpochMs",
                            factId = null
                        )
                    )
                    bumpClearQuestion(store, intentionId, nowEpochMs)
                    return Result(
                        ok = true,
                        honestyNote = "Move estimate updated — prior estimate stays on record as corrected.",
                        accepted = true
                    )
                }
            }
        }

        return when (val selection = ChoiceOptions.resolveSelection(trimmed, question.optionIds)) {
            ChoiceOptions.Selection.Empty -> Result(false, "Empty answer.")
            ChoiceOptions.Selection.NeedsFreeText -> Result(
                ok = true,
                honestyNote = "Other selected — type your answer below.",
                needsFreeText = true
            )
            ChoiceOptions.Selection.NotSure,
            ChoiceOptions.Selection.Skip -> deferQuestion(
                store = store,
                intentionId = intentionId,
                questionId = activeId,
                label = trimmed,
                modality = modality,
                nowEpochMs = nowEpochMs,
                expectedRevision = state.revision
            )
            is ChoiceOptions.Selection.Chosen -> commitAnswer(
                store, intentionId, activeId, selection.value, selection.value,
                modality, nowEpochMs, state.revision, question.purpose, timeZone
            )
            is ChoiceOptions.Selection.FreeText -> commitAnswer(
                store, intentionId, activeId, selection.value, selection.value,
                modality, nowEpochMs, state.revision, question.purpose, timeZone
            )
        }
    }

    private fun bumpClearQuestion(
        store: IntentionFlowStore,
        intentionId: String,
        nowEpochMs: Long
    ) {
        val prior = store.snapshot().adaptiveBuilderStates
            .firstOrNull { it.intentionId == intentionId } ?: return
        store.apply(
            FlowCommand.SetAdaptiveBuilderState(
                state = prior.copy(
                    currentQuestionId = null,
                    revision = prior.revision + 1,
                    updatedAtEpochMs = nowEpochMs
                ),
                nowEpochMs = nowEpochMs
            )
        )
    }

    /**
     * Gate D2 — STT → one AnswerQuestion path with certainty gating.
     * Exact chip / clear free-text commit immediately; fuzzy / multi / soft chip
     * resemblance projects a confirm card and writes nothing until Yes (or a
     * candidate tap on a multi-candidate confirm).
     */
    fun submitFromVoice(
        store: IntentionFlowStore,
        intentionId: String,
        transcript: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        expectedQuestionId: String? = null
    ): Result {
        val trimmed = transcript.trim()
        if (trimmed.isEmpty()) {
            return Result(false, "No speech recognized — try again or tap a chip.")
        }
        val snap = store.snapshot()
        val state = snap.adaptiveBuilderStates.firstOrNull { it.intentionId == intentionId }
            ?: return Result(false, "No adaptive builder state for this intention.")
        val activeId = state.currentQuestionId
            ?: return Result(false, "No active question — refresh the conversation.")
        if (expectedQuestionId != null && expectedQuestionId != activeId) {
            return Result(
                ok = true,
                honestyNote = "That card is no longer active. The conversation was refreshed — " +
                    "answer the current question.",
                accepted = false
            )
        }
        val question = snap.openQuestions.firstOrNull { it.id == activeId }
            ?: return Result(false, "Active question missing — refresh.")

        val optionChips = question.optionIds.filter {
            !ChoiceOptions.isOther(it) && !ChoiceOptions.isDeferral(it)
        }
        return when (val match = ConversationCoordinator.classifyChipMatch(trimmed, optionChips)) {
            is ConversationCoordinator.ChipMatch.Exact -> submit(
                store = store,
                intentionId = intentionId,
                rawValue = match.chip,
                modality = ConversationCoordinator.Channel.VOICE_TRANSCRIPT.name,
                nowEpochMs = nowEpochMs,
                timeZone = timeZone,
                voiceUncertain = false,
                expectedQuestionId = activeId
            )
            is ConversationCoordinator.ChipMatch.Ambiguous -> {
                // MULTI_CHOICE: several chip titles in one transcript is a selection,
                // not Gate D2 single-choice uncertainty (e.g. "AI … and Learning app").
                if (question.answerSchema == AnswerSchema.MULTI_CHOICE &&
                    match.candidates.size >= 2
                ) {
                    submit(
                        store = store,
                        intentionId = intentionId,
                        rawValue = match.candidates.joinToString(" and "),
                        modality = ConversationCoordinator.Channel.VOICE_TRANSCRIPT.name,
                        nowEpochMs = nowEpochMs,
                        timeZone = timeZone,
                        voiceUncertain = false,
                        expectedQuestionId = activeId
                    )
                } else {
                    projectVoiceConfirm(store, intentionId, activeId, match.candidates, nowEpochMs)
                    Result(
                        ok = true,
                        honestyNote = "Voice was uncertain — confirm before saving.",
                        needsVoiceConfirm = true,
                        accepted = false
                    )
                }
            }
            ConversationCoordinator.ChipMatch.None -> submit(
                store = store,
                intentionId = intentionId,
                rawValue = trimmed,
                modality = ConversationCoordinator.Channel.VOICE_TRANSCRIPT.name,
                nowEpochMs = nowEpochMs,
                timeZone = timeZone,
                voiceUncertain = false,
                expectedQuestionId = activeId
            )
        }
    }

    /** Active in-chat voice-confirm card, if any. */
    fun pendingVoiceConfirmBlock(
        store: IntentionFlowStore,
        intentionId: String
    ): ResponseBlock? =
        store.snapshot().turns
            .asSequence()
            .filter { it.intentionId == intentionId }
            .flatMap { it.blocks }
            .filter { it.active && VOICE_CONFIRM_LINK in it.linkedObjectIds }
            .lastOrNull()

    /**
     * Handle Yes / No on a pending voice-confirm card.
     * Returns null when [label] is not a voice-confirm action (caller continues normally).
     */
    fun resolveVoiceConfirmIfPending(
        store: IntentionFlowStore,
        intentionId: String,
        label: String,
        modality: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Result? {
        val confirm = pendingVoiceConfirmBlock(store, intentionId) ?: return null
        val trimmed = label.trim()
        val candidates = confirm.actions.filter {
            it != VOICE_CONFIRM_YES && it != VOICE_CONFIRM_NO &&
                !ChoiceOptions.isDeferral(it) && !ChoiceOptions.isOther(it)
        }
        val isYes = trimmed.equals(VOICE_CONFIRM_YES, ignoreCase = true)
        val isNo = trimmed.equals(VOICE_CONFIRM_NO, ignoreCase = true)
        val pickedCandidate = candidates.firstOrNull { it.equals(trimmed, ignoreCase = true) }
        if (!isYes && !isNo && pickedCandidate == null) return null

        val questionId = confirm.linkedObjectIds.firstOrNull { it != VOICE_CONFIRM_LINK }
            ?: store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }
                ?.currentQuestionId
            ?: return Result(false, "Confirm card missing question link.")

        store.apply(
            FlowCommand.DeactivateBuilderAnswerCards(
                intentionId = intentionId,
                nowEpochMs = nowEpochMs,
                exceptBlockId = null
            )
        )

        if (isNo) {
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-voice-reject-$nowEpochMs",
                    intentionId = intentionId,
                    role = "user",
                    text = trimmed,
                    nowEpochMs = nowEpochMs,
                    modality = modality
                )
            )
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-voice-reject-ack-$nowEpochMs",
                    intentionId = intentionId,
                    role = "agent",
                    text = "Okay — tap a choice or type what you meant.",
                    nowEpochMs = nowEpochMs + 1
                )
            )
            return Result(
                ok = true,
                honestyNote = "Voice guess discarded — answer with a chip or free text.",
                needsFreeText = true,
                accepted = false
            )
        }

        val candidate = when {
            pickedCandidate != null -> pickedCandidate
            isYes -> confirm.body.trim().ifBlank {
                candidates.singleOrNull().orEmpty()
            }
            else -> ""
        }
        if (candidate.isBlank()) {
            return Result(false, "Nothing to confirm — try again.")
        }

        val snap = store.snapshot()
        val state = snap.adaptiveBuilderStates.firstOrNull { it.intentionId == intentionId }
            ?: return Result(false, "No adaptive builder state for this intention.")
        val question = snap.openQuestions.firstOrNull { it.id == questionId }
            ?: return Result(false, "Active question missing — refresh.")
        // Re-activate Ask projection is unnecessary — commit against open question directly.
        return commitAnswer(
            store = store,
            intentionId = intentionId,
            questionId = questionId,
            normalizedValue = candidate,
            rawValue = candidate,
            modality = ConversationCoordinator.Channel.VOICE_TRANSCRIPT.name,
            nowEpochMs = nowEpochMs + 2,
            expectedRevision = state.revision,
            factKey = question.purpose,
            timeZone = timeZone
        )
    }

    fun commitAnswer(
        store: IntentionFlowStore,
        intentionId: String,
        questionId: String,
        normalizedValue: String,
        rawValue: String,
        modality: String,
        nowEpochMs: Long,
        expectedRevision: Long,
        factKey: String?,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Result {
        val turnId = "turn-answer-$questionId-$nowEpochMs"
        val idem = "idem-$questionId-${normalizedValue.hashCode()}-$modality"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = turnId,
                intentionId = intentionId,
                role = "user",
                text = rawValue,
                nowEpochMs = nowEpochMs,
                modality = modality
            )
        )
        val apply = AdaptiveBuilderEngine.answerQuestion(
            store,
            AnswerQuestion(
                questionId = questionId,
                intentionId = intentionId,
                normalizedValue = normalizedValue,
                rawValue = rawValue,
                modality = modality,
                sourceTurnId = turnId,
                answeredAtEpochMs = nowEpochMs + 1,
                idempotencyKey = idem,
                expectedRevision = expectedRevision
            ),
            factKey = factKey,
            timeZone = timeZone
        )
        if (!apply.accepted) {
            val stale = apply.reason.contains("Stale", ignoreCase = true) ||
                apply.reason.contains("active question", ignoreCase = true)
            return Result(
                ok = stale,
                honestyNote = if (stale) {
                    "That answer was stale — the conversation refreshed. Try again on the active card."
                } else {
                    apply.reason
                },
                accepted = false
            )
        }
        // Drop any lingering voice-confirm card once an answer lands.
        store.apply(
            FlowCommand.DeactivateBuilderAnswerCards(
                intentionId = intentionId,
                nowEpochMs = nowEpochMs + 1
            )
        )
        // §8.2 / Gate D2: approach Ask answers also SelectApproaches so chip/tap,
        // typed free text, and speech-injected transcripts write the same SELECTED
        // approach records through one AnswerQuestion path.
        if (factKey == AdaptiveBuilderEngine.KEY_SELECTED_APPROACHES) {
            val approachIds = AdaptiveBuilderEngine.matchApproachIds(
                store.snapshot(), intentionId, normalizedValue
            )
            if (approachIds.isNotEmpty()) {
                val rev = store.snapshot().adaptiveBuilderStates
                    .firstOrNull { it.intentionId == intentionId }
                    ?.revision
                    ?: expectedRevision
                AdaptiveBuilderEngine.selectApproaches(
                    store = store,
                    intentionId = intentionId,
                    approachIds = approachIds,
                    nowEpochMs = nowEpochMs + 2,
                    sourceTurnId = turnId,
                    expectedRevision = rev,
                    modality = modality
                )
            }
        }
        advanceAfterAnswer(store, intentionId, nowEpochMs + 3, timeZone)
        return Result(ok = true, honestyNote = "Answer recorded.", accepted = true)
    }

    /**
     * Apply up to two policy steps so OfferApproaches can seed candidates and the
     * follow-up MULTI_CHOICE Ask lands as the active card in the same turn.
     */
    private fun advanceAfterAnswer(
        store: IntentionFlowStore,
        intentionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone
    ) {
        var t = nowEpochMs
        repeat(2) {
            val state = store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }
            if (state?.currentQuestionId != null) return
            val ctx = AdaptiveBuilderEngine.projectContext(
                store.snapshot(), intentionId, t, timeZone
            ) ?: return
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, timeZone)
            AdaptiveBuilderEngine.applyDecision(store, next, t, timeZone = timeZone)
            t += 1
            when (next) {
                is com.intent.launcher.domain.BuilderDecision.Ask,
                is com.intent.launcher.domain.BuilderDecision.Propose,
                is com.intent.launcher.domain.BuilderDecision.Pause -> return
                else -> Unit
            }
        }
    }

    fun deferQuestion(
        store: IntentionFlowStore,
        intentionId: String,
        questionId: String,
        label: String,
        modality: String,
        nowEpochMs: Long,
        expectedRevision: Long
    ): Result {
        val snap = store.snapshot()
        val state = snap.adaptiveBuilderStates.firstOrNull { it.intentionId == intentionId }
        if ((state?.revision ?: 0L) != expectedRevision) {
            return Result(
                ok = true,
                honestyNote = "That card is no longer active. The conversation was refreshed.",
                accepted = false
            )
        }
        val question = snap.openQuestions.firstOrNull { it.id == questionId }
            ?: return Result(false, "Unknown question.")
        val deferred = question.copy(
            state = if (ChoiceOptions.isNotSure(label)) {
                OpenQuestionState.DECLINED
            } else {
                OpenQuestionState.DEFERRED
            },
            answeredAtEpochMs = nowEpochMs
        )
        store.apply(FlowCommand.UpsertOpenQuestion(deferred, nowEpochMs))
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-defer-$questionId-$nowEpochMs",
                intentionId = intentionId,
                role = "user",
                text = label,
                nowEpochMs = nowEpochMs,
                modality = modality,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-deferred-$questionId-$nowEpochMs",
                        type = ResponseBlockType.RECORDED_ENTRY,
                        title = "Deferred",
                        body = label,
                        sourceLabel = "adaptive_builder|defer",
                        linkedObjectIds = listOf(questionId),
                        active = true
                    )
                )
            )
        )
        val prior = state ?: AdaptiveBuilderState(
            intentionId = intentionId,
            updatedAtEpochMs = nowEpochMs
        )
        store.apply(
            FlowCommand.SetAdaptiveBuilderState(
                prior.copy(
                    currentQuestionId = null,
                    revision = prior.revision + 1,
                    lastAnsweredTurnId = "turn-defer-$questionId-$nowEpochMs",
                    updatedAtEpochMs = nowEpochMs
                ),
                nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-defer-ack-$nowEpochMs",
                intentionId = intentionId,
                role = "agent",
                text = "Okay — we can come back to that. Your earlier answers stay saved.",
                nowEpochMs = nowEpochMs + 1
            )
        )
        return Result(
            ok = true,
            honestyNote = "Question deferred — conversation continues.",
            accepted = true
        )
    }

    /** Latest builder answer card still tied to the active question (historical cards ignored). */
    fun activeBuilderBlock(
        store: IntentionFlowStore,
        intentionId: String
    ): ResponseBlock? {
        val state = store.snapshot().adaptiveBuilderStates
            .firstOrNull { it.intentionId == intentionId }
        val qid = state?.currentQuestionId ?: return null
        return store.snapshot().turns
            .asSequence()
            .filter { it.intentionId == intentionId }
            .flatMap { it.blocks }
            .filter { it.active && BuilderAnswerCard.isBuilderCard(it) }
            .filter { qid in it.linkedObjectIds || BuilderAnswerCard.decode(it)?.promptId == qid }
            .lastOrNull()
    }

    private fun projectVoiceConfirm(
        store: IntentionFlowStore,
        intentionId: String,
        questionId: String,
        candidates: List<String>,
        nowEpochMs: Long
    ) {
        val distinct = candidates.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (distinct.isEmpty()) return
        val single = distinct.size == 1
        val body = if (single) distinct.first() else distinct.joinToString(" · ")
        val spoken = if (single) {
            "I heard “${distinct.first()}”. Is that right?"
        } else {
            "I heard something like one of these — which did you mean?"
        }
        val actions = if (single) {
            listOf(VOICE_CONFIRM_YES, VOICE_CONFIRM_NO)
        } else {
            distinct + VOICE_CONFIRM_NO
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-voice-confirm-$nowEpochMs",
                intentionId = intentionId,
                role = "agent",
                text = spoken,
                nowEpochMs = nowEpochMs,
                blocks = listOf(
                    BuilderAnswerCard.confirmBlock(
                        blockId = "block-voice-confirm-$questionId-$nowEpochMs",
                        title = "Confirm what I heard",
                        body = body
                    ).copy(
                        actions = actions,
                        linkedObjectIds = listOf(questionId, VOICE_CONFIRM_LINK)
                    )
                )
            )
        )
    }
}
