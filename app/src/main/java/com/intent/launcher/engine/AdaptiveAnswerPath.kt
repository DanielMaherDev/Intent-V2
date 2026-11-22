package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderState
import com.intent.launcher.domain.AnswerQuestion
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
            projectVoiceConfirm(store, intentionId, activeId, trimmed, nowEpochMs)
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
        val ctx = AdaptiveBuilderEngine.projectContext(
            store.snapshot(), intentionId, nowEpochMs + 2, timeZone
        )
        if (ctx != null) {
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, nowEpochMs + 2, timeZone)
            AdaptiveBuilderEngine.applyDecision(store, next, nowEpochMs + 3, timeZone = timeZone)
        }
        return Result(ok = true, honestyNote = "Answer recorded.", accepted = true)
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
        candidate: String,
        nowEpochMs: Long
    ) {
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-voice-confirm-$nowEpochMs",
                intentionId = intentionId,
                role = "agent",
                text = "I heard “$candidate”. Is that right?",
                nowEpochMs = nowEpochMs,
                blocks = listOf(
                    BuilderAnswerCard.confirmBlock(
                        blockId = "block-voice-confirm-$questionId-$nowEpochMs",
                        title = "Confirm what I heard",
                        body = candidate
                    ).copy(
                        actions = listOf("Yes, that's right", "No, let me rephrase"),
                        linkedObjectIds = listOf(questionId, "voice-confirm")
                    )
                )
            )
        )
    }
}
