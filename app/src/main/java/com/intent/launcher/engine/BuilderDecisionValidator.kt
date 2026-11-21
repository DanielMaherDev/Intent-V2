package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderState
import com.intent.launcher.domain.AnswerQuestion
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.BuilderContext
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.domain.ProposalStatus

/**
 * Validates schema-constrained [BuilderDecision] / [AnswerQuestion] against
 * sourced builder state. Stale revision, cancelled turns, and duplicate answers
 * cannot write (worksheet §5 / §8.9).
 */
object BuilderDecisionValidator {

    data class Result(
        val accepted: Boolean,
        val reason: String,
        val decisionKind: String
    )

    fun validate(
        snapshot: IntentionSnapshot,
        decision: BuilderDecision,
        cancelledTurnId: String? = null
    ): Result {
        val kind = decision::class.simpleName ?: "BuilderDecision"
        if (decision.intentionId.isBlank()) {
            return Result(false, "Missing intentionId.", kind)
        }
        val intention = snapshot.intentions.firstOrNull { it.id == decision.intentionId }
            ?: return Result(false, "Unknown intention ${decision.intentionId}.", kind)
        val state = snapshot.adaptiveBuilderStates.firstOrNull { it.intentionId == decision.intentionId }
        val currentRevision = state?.revision ?: 0L
        if (decision.expectedRevision != currentRevision) {
            return Result(
                false,
                "Stale revision: expected ${decision.expectedRevision}, current $currentRevision.",
                kind
            )
        }
        val sourceTurn = decision.sourceTurnId
        if (sourceTurn != null && AgentTurnLifecycle.isCancelled(snapshot, sourceTurn)) {
            return Result(false, "Source turn $sourceTurn was cancelled — no write.", kind)
        }
        if (cancelledTurnId != null && sourceTurn == cancelledTurnId) {
            return Result(false, "Decision tied to cancelled turn $cancelledTurnId.", kind)
        }
        return when (decision) {
            is BuilderDecision.SaveFact -> {
                if (decision.factDraft.factId.isBlank() || decision.factDraft.text.isBlank()) {
                    Result(false, "SaveFact requires factId and text.", kind)
                } else if (snapshot.facts.any {
                        it.id == decision.factDraft.factId && it.status == FactStatus.ACTIVE
                    }
                ) {
                    Result(false, "Fact ${decision.factDraft.factId} already ACTIVE — use CorrectFact.", kind)
                } else {
                    Result(true, "ok", kind)
                }
            }
            is BuilderDecision.CorrectFact -> {
                val prior = snapshot.facts.firstOrNull { it.id == decision.existingFactId }
                    ?: return Result(false, "Unknown fact ${decision.existingFactId}.", kind)
                if (prior.intentionId != intention.id) {
                    Result(false, "Fact belongs to another intention.", kind)
                } else if (prior.status != FactStatus.ACTIVE) {
                    Result(false, "Fact ${prior.id} is ${prior.status}, not ACTIVE.", kind)
                } else if (decision.replacement.factId.isBlank() ||
                    decision.replacement.factId == prior.id
                ) {
                    Result(false, "CorrectFact requires a new factId.", kind)
                } else {
                    Result(true, "ok", kind)
                }
            }
            is BuilderDecision.Ask -> {
                if (decision.questionId.isBlank()) {
                    Result(false, "Ask requires questionId.", kind)
                } else if (decision.whyItMatters.isBlank()) {
                    Result(false, "Ask requires whyItMatters.", kind)
                } else {
                    val existing = snapshot.openQuestions.firstOrNull { it.id == decision.questionId }
                    if (existing != null && existing.state == OpenQuestionState.ANSWERED) {
                        Result(false, "Question ${decision.questionId} already answered.", kind)
                    } else {
                        Result(true, "ok", kind)
                    }
                }
            }
            is BuilderDecision.OfferApproaches -> {
                if (decision.approachDrafts.isEmpty()) {
                    Result(false, "OfferApproaches requires at least one draft.", kind)
                } else if (decision.approachDrafts.any { it.approachId.isBlank() || it.title.isBlank() }) {
                    Result(false, "Approach drafts need id and title.", kind)
                } else {
                    Result(true, "ok", kind)
                }
            }
            is BuilderDecision.OfferAction -> {
                if (decision.actionDraft.actionId.isBlank() || decision.actionDraft.title.isBlank()) {
                    Result(false, "OfferAction requires action id and title.", kind)
                } else {
                    Result(true, "ok", kind)
                }
            }
            is BuilderDecision.Propose -> {
                if (decision.proposalId.isBlank()) {
                    Result(false, "Propose requires proposalId.", kind)
                } else if (snapshot.proposals.any {
                        it.id == decision.proposalId && it.status == ProposalStatus.PENDING
                    }
                ) {
                    Result(false, "Proposal ${decision.proposalId} already PENDING.", kind)
                } else if (decision.dayOfWeek !in 1..7 || decision.hour !in 0..23 ||
                    decision.minute !in 0..59
                ) {
                    Result(false, "Propose has invalid schedule fields.", kind)
                } else {
                    Result(true, "ok", kind)
                }
            }
            is BuilderDecision.Pause -> {
                if (decision.reason.isBlank()) {
                    Result(false, "Pause requires reason.", kind)
                } else {
                    Result(true, "ok", kind)
                }
            }
        }
    }

    fun validateAnswer(
        snapshot: IntentionSnapshot,
        answer: AnswerQuestion
    ): Result {
        val kind = "AnswerQuestion"
        if (answer.idempotencyKey.isBlank()) {
            return Result(false, "AnswerQuestion requires idempotencyKey.", kind)
        }
        // Duplicate answer: same idempotency key already recorded on a turn.
        val duplicate = snapshot.turns.any { turn ->
            turn.id == answer.sourceTurnId ||
                turn.blocks.any { block ->
                    answer.idempotencyKey in block.linkedObjectIds &&
                        block.title.equals("Answered", ignoreCase = true)
                }
        }
        if (duplicate && snapshot.openQuestions.any {
                it.id == answer.questionId && it.state == OpenQuestionState.ANSWERED
            }
        ) {
            return Result(false, "Duplicate answer for key ${answer.idempotencyKey}.", kind)
        }
        if (AgentTurnLifecycle.isCancelled(snapshot, answer.sourceTurnId)) {
            return Result(false, "Answer source turn cancelled — no write.", kind)
        }
        val state = snapshot.adaptiveBuilderStates.firstOrNull { it.intentionId == answer.intentionId }
        val currentRevision = state?.revision ?: 0L
        if (answer.expectedRevision != currentRevision) {
            return Result(
                false,
                "Stale revision: expected ${answer.expectedRevision}, current $currentRevision.",
                kind
            )
        }
        val question = snapshot.openQuestions.firstOrNull { it.id == answer.questionId }
            ?: return Result(false, "Unknown question ${answer.questionId}.", kind)
        if (question.intentionId != answer.intentionId) {
            return Result(false, "Question intention mismatch.", kind)
        }
        if (question.state == OpenQuestionState.ANSWERED) {
            return Result(false, "Question already answered.", kind)
        }
        if (state?.currentQuestionId != null && state.currentQuestionId != answer.questionId) {
            return Result(
                false,
                "Only active question ${state.currentQuestionId} accepts answers.",
                kind
            )
        }
        if (answer.normalizedValue.isBlank() && answer.rawValue.isBlank()) {
            return Result(false, "Empty answer.", kind)
        }
        return Result(true, "ok", kind)
    }

    fun contextRevision(context: BuilderContext): Long = context.revision

    fun emptyState(intentionId: String, nowEpochMs: Long): AdaptiveBuilderState =
        AdaptiveBuilderState(
            intentionId = intentionId,
            revision = 0L,
            updatedAtEpochMs = nowEpochMs
        )
}
