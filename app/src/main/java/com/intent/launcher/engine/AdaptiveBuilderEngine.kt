package com.intent.launcher.engine

import com.intent.launcher.domain.ActionDraft
import com.intent.launcher.domain.AdaptiveBuilderState
import com.intent.launcher.domain.AnswerQuestion
import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.Approach
import com.intent.launcher.domain.ApproachActor
import com.intent.launcher.domain.ApproachDraft
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderContext
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.FactConfirmation
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.OpenQuestion
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.RelativeDateAnchoring
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Conversation-callable hooks for adaptive builder Gates B–C.
 * Does not own UI; chat-first create (IV-189) may call [projectContext] / [applyDecision].
 */
object AdaptiveBuilderEngine {

    const val KEY_TARGET_LANGUAGE = "target_language"
    const val KEY_MOTIVATION = "motivation"
    const val KEY_MOVE_ESTIMATE = "move_estimate"
    const val KEY_PRACTICE_FREQUENCY = "practice_frequency"
    const val KEY_PRACTICE_DURATION = "practice_duration"
    const val KEY_PRACTICE_TIME = "practice_time"
    const val KEY_PRACTICE_DAYS = "practice_days"

    data class ApplyResult(
        val accepted: Boolean,
        val reason: String,
        val decisionKind: String,
        val snapshot: IntentionSnapshot
    )

    fun projectContext(
        snapshot: IntentionSnapshot,
        intentionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): BuilderContext? {
        val intention = snapshot.intentions.firstOrNull { it.id == intentionId } ?: return null
        val state = snapshot.adaptiveBuilderStates.firstOrNull { it.intentionId == intentionId }
        return BuilderContext(
            intentionId = intention.id,
            revision = state?.revision ?: 0L,
            lifecycleState = state?.lifecycleHint ?: intention.phase.name.lowercase(),
            typeFacets = state?.typeFacets?.ifEmpty { facetsForShape(intention.shapeTag) }
                ?: facetsForShape(intention.shapeTag),
            confirmedFacts = snapshot.facts.filter {
                it.intentionId == intentionId && it.status == FactStatus.ACTIVE
            },
            hypotheses = snapshot.hypotheses.filter { it.intentionId == intentionId },
            openQuestions = snapshot.openQuestions.filter { it.intentionId == intentionId },
            approaches = snapshot.approaches.filter { it.intentionId == intentionId },
            approvedCommitments = snapshot.sessions.filter { it.intentionId == intentionId },
            pendingProposals = snapshot.proposals.filter {
                it.intentionId == intentionId && it.status == ProposalStatus.PENDING
            },
            recentTurns = snapshot.turns
                .filter { it.intentionId == intentionId }
                .sortedBy { it.atEpochMs }
                .takeLast(12),
            currentQuestionId = state?.currentQuestionId,
            userTimeZoneId = timeZone.id,
            nowEpochMs = nowEpochMs,
            policyVersion = state?.policyVersion ?: AdaptiveBuilderState.POLICY_VERSION
        )
    }

    fun facetsForShape(shapeTag: String): List<String> = when (shapeTag.lowercase()) {
        "language" -> listOf("skill_practice", "dated_outcome")
        "marathon" -> listOf("dated_outcome", "skill_practice")
        "business" -> listOf("exploration", "ambition")
        "move" -> listOf("dated_outcome", "conditional_process")
        "decision" -> listOf("decision")
        "cessation" -> listOf("habit_change")
        "app_use" -> listOf("habit_change")
        "research" -> listOf("conditional_process")
        "chess" -> listOf("skill_practice", "dated_outcome")
        "errand" -> listOf("one_off_action")
        else -> listOf("exploration")
    }

    /**
     * Project the next policy decision into the store when it is an Ask
     * (e.g. §8.1 freshness reconfirm). No-op when Propose/Offer already pending
     * or when an open Ask for the same purpose already exists.
     *
     * @return true when a new Ask was applied
     */
    fun projectPendingAsk(
        store: IntentionFlowStore,
        intentionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val ctx = projectContext(store.snapshot(), intentionId, nowEpochMs, timeZone) ?: return false
        val next = suggestNextDecision(ctx, nowEpochMs, timeZone)
        if (next !is BuilderDecision.Ask) return false
        val alreadyOpen = store.snapshot().openQuestions.any {
            it.intentionId == intentionId &&
                it.purpose == next.purpose &&
                it.state != OpenQuestionState.ANSWERED &&
                it.state != OpenQuestionState.DECLINED
        }
        if (alreadyOpen) return false
        return applyDecision(store, next, nowEpochMs, timeZone = timeZone).accepted
    }

    /**
     * Deterministic next-move policy for tests and Conversation hooks.
     * Not an LLM — ranks by consequence; zero questions for small errands.
     */
    fun suggestNextDecision(
        context: BuilderContext,
        nowEpochMs: Long = context.nowEpochMs,
        timeZone: TimeZone = TimeZone.getTimeZone(context.userTimeZoneId)
    ): BuilderDecision {
        if (context.typeFacets.contains("one_off_action")) {
            return BuilderDecision.OfferAction(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                actionDraft = ActionDraft(
                    actionId = "action-errand-${context.intentionId}",
                    title = "Add a quick to-do",
                    summary = "Capture this as a checklist item — no discovery interview.",
                    kind = "errand"
                )
            )
        }
        if (context.typeFacets.any { it == "ambition" || it == "exploration" } &&
            context.confirmedFacts.isEmpty() &&
            context.openQuestions.none { it.state == OpenQuestionState.ASKED }
        ) {
            // Broad ambition: one discovery question, never a fictional launch checklist.
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-explore-skills-${context.intentionId}",
                whyItMatters = "Without an idea chosen, a launch checklist would be premature.",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = "skills_or_interests",
                blocksWhat = "validation_experiment",
                priority = 10
            )
        }

        val hasSelectedApproaches =
            context.approaches.any { it.status == ApproachStatus.SELECTED }
        val hasFrequency =
            context.confirmedFacts.any { it.key == KEY_PRACTICE_FREQUENCY }

        // Near-term plan readiness: frequency unlocks a compiled draft (defaults fill
        // missing duration/time/days). Richer timing Ask cards remain available when
        // Conversation advances via AdaptiveAnswerPath without a frequency fact yet.
        if (context.pendingProposals.isEmpty() && hasSelectedApproaches && hasFrequency) {
            val draft = PlanCompiler.compile(context)
            if (draft != null) {
                return PlanCompiler.toProposeDecision(
                    draft = draft,
                    intentionId = context.intentionId,
                    expectedRevision = context.revision
                )
            }
        }
        if (context.pendingProposals.isEmpty() && hasSelectedApproaches && !hasFrequency) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-frequency-${context.intentionId}",
                whyItMatters = "Frequency is needed before drafting a reviewable routine.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_PRACTICE_FREQUENCY,
                blocksWhat = "routine_proposal",
                options = listOf("Everyday", "3 days per week", "Other"),
                priority = 40
            )
        }

        val moveFact = context.confirmedFacts.firstOrNull { it.key == KEY_MOVE_ESTIMATE }
        if (moveFact != null) {
            val freshness = RelativeDateAnchoring.freshnessCheck(moveFact, nowEpochMs, timeZone = timeZone)
            if (freshness.needsReconfirm &&
                context.openQuestions.none {
                    it.purpose == "reconfirm_move_estimate" &&
                        it.state != OpenQuestionState.ANSWERED
                }
            ) {
                return BuilderDecision.Ask(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    questionId = "q-reconfirm-move-${context.intentionId}",
                    whyItMatters = freshness.reason,
                    answerSchema = AnswerSchema.CONFIRM,
                    purpose = "reconfirm_move_estimate",
                    blocksWhat = "practice_intensity",
                    priority = 100,
                    options = listOf("Still about right", "Update the date")
                )
            }
        }

        if (context.confirmedFacts.none { it.key == KEY_TARGET_LANGUAGE } &&
            context.typeFacets.contains("skill_practice")
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-language-${context.intentionId}",
                whyItMatters = "Target language changes which practice tools and materials apply.",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = KEY_TARGET_LANGUAGE,
                blocksWhat = "approach_selection",
                priority = 90
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_MOTIVATION } &&
            context.confirmedFacts.any { it.key == KEY_TARGET_LANGUAGE }
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-motivation-${context.intentionId}",
                whyItMatters = "Motivation shapes urgency and which practice to prioritize first.",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = KEY_MOTIVATION,
                blocksWhat = "move_estimate",
                priority = 80
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_MOVE_ESTIMATE } &&
            context.confirmedFacts.any { it.key == KEY_MOTIVATION }
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-move-${context.intentionId}",
                whyItMatters = "Move timing affects what to practise first.",
                answerSchema = AnswerSchema.DATE_TIME,
                purpose = KEY_MOVE_ESTIMATE,
                blocksWhat = "practice_intensity",
                priority = 70
            )
        }
        if (!hasSelectedApproaches &&
            context.confirmedFacts.any { it.key == KEY_MOVE_ESTIMATE }
        ) {
            return BuilderDecision.OfferApproaches(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                approachDrafts = listOf(
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
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason = "Enough known for now — leave discovery resumable.",
            resumeCue = "Continue discussion"
        )
    }

    fun applyDecision(
        store: IntentionFlowStore,
        decision: BuilderDecision,
        nowEpochMs: Long,
        cancelledTurnId: String? = null,
        timeZone: TimeZone = TimeZone.getDefault()
    ): ApplyResult {
        val snap = store.snapshot()
        val validation = BuilderDecisionValidator.validate(snap, decision, cancelledTurnId)
        if (!validation.accepted) {
            return ApplyResult(false, validation.reason, validation.decisionKind, snap)
        }
        when (decision) {
            is BuilderDecision.SaveFact -> {
                val draft = enrichRelativeDate(decision.factDraft, nowEpochMs, timeZone)
                store.apply(
                    FlowCommand.SaveTypedFact(
                        factId = draft.factId,
                        intentionId = decision.intentionId,
                        text = draft.text,
                        nowEpochMs = nowEpochMs,
                        key = draft.key,
                        rawUtterance = draft.rawUtterance,
                        normalizedValue = draft.normalizedValue,
                        sourceTurnId = decision.sourceTurnId,
                        timeZoneId = draft.timeZoneId ?: timeZone.id,
                        validFromEpochMs = draft.validFromEpochMs,
                        validUntilEpochMs = draft.validUntilEpochMs,
                        estimatedEpochMs = draft.estimatedEpochMs,
                        estimatedRangeStartEpochMs = draft.estimatedRangeStartEpochMs,
                        estimatedRangeEndEpochMs = draft.estimatedRangeEndEpochMs,
                        approximate = draft.approximate,
                        confirmation = draft.confirmation
                    )
                )
                bumpState(store, decision.intentionId, nowEpochMs) { state ->
                    state.copy(
                        lastAnsweredTurnId = decision.sourceTurnId ?: state.lastAnsweredTurnId,
                        conversationCursorTurnId = decision.sourceTurnId
                            ?: state.conversationCursorTurnId,
                        lifecycleHint = "discovery"
                    )
                }
            }
            is BuilderDecision.CorrectFact -> {
                val draft = enrichRelativeDate(decision.replacement, nowEpochMs, timeZone)
                store.apply(
                    FlowCommand.CorrectTypedFact(
                        priorFactId = decision.existingFactId,
                        newFactId = draft.factId,
                        intentionId = decision.intentionId,
                        text = draft.text,
                        nowEpochMs = nowEpochMs,
                        key = draft.key,
                        rawUtterance = draft.rawUtterance,
                        normalizedValue = draft.normalizedValue,
                        sourceTurnId = decision.sourceTurnId,
                        timeZoneId = draft.timeZoneId ?: timeZone.id,
                        validFromEpochMs = draft.validFromEpochMs,
                        validUntilEpochMs = draft.validUntilEpochMs,
                        estimatedEpochMs = draft.estimatedEpochMs,
                        estimatedRangeStartEpochMs = draft.estimatedRangeStartEpochMs,
                        estimatedRangeEndEpochMs = draft.estimatedRangeEndEpochMs,
                        approximate = draft.approximate,
                        confirmation = draft.confirmation
                    )
                )
                bumpState(store, decision.intentionId, nowEpochMs) { state ->
                    state.copy(
                        lastAnsweredTurnId = decision.sourceTurnId ?: state.lastAnsweredTurnId
                    )
                }
            }
            is BuilderDecision.Ask -> {
                val question = OpenQuestion(
                    id = decision.questionId,
                    intentionId = decision.intentionId,
                    purpose = decision.purpose.ifBlank { decision.questionId },
                    whyItMatters = decision.whyItMatters,
                    blocksWhat = decision.blocksWhat,
                    answerSchema = decision.answerSchema,
                    optionIds = decision.options,
                    state = OpenQuestionState.ASKED,
                    priority = decision.priority,
                    askedAtEpochMs = nowEpochMs,
                    lastAskedEpochMs = nowEpochMs,
                    sourceTurnId = decision.sourceTurnId
                )
                store.apply(FlowCommand.UpsertOpenQuestion(question, nowEpochMs))
                if (decision.sourceTurnId == null) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-ask-${decision.questionId}-$nowEpochMs",
                            intentionId = decision.intentionId,
                            role = "agent",
                            text = decision.whyItMatters,
                            nowEpochMs = nowEpochMs,
                            blocks = listOf(BuilderAnswerCard.blockForAsk(decision, nowEpochMs))
                        )
                    )
                }
                bumpState(store, decision.intentionId, nowEpochMs) { state ->
                    state.copy(currentQuestionId = decision.questionId, lifecycleHint = "discovery")
                }
            }
            is BuilderDecision.OfferApproaches -> {
                decision.approachDrafts.forEach { draft ->
                    store.apply(
                        FlowCommand.UpsertApproach(
                            Approach(
                                id = draft.approachId,
                                intentionId = decision.intentionId,
                                title = draft.title,
                                summary = draft.summary,
                                status = if (draft.selected) {
                                    ApproachStatus.SELECTED
                                } else {
                                    ApproachStatus.CANDIDATE
                                },
                                actor = draft.actor,
                                packageName = draft.packageName,
                                capabilityNote = draft.capabilityNote,
                                sourceTurnId = decision.sourceTurnId,
                                selectedAtEpochMs = if (draft.selected) nowEpochMs else null
                            ),
                            nowEpochMs
                        )
                    )
                }
                bumpState(store, decision.intentionId, nowEpochMs) { it }
            }
            is BuilderDecision.OfferAction -> {
                if (decision.actionDraft.kind == "errand") {
                    store.apply(
                        FlowCommand.CaptureErrand(
                            todoId = decision.actionDraft.actionId,
                            title = decision.actionDraft.title,
                            firstAction = decision.actionDraft.summary,
                            reminderNote = decision.actionDraft.reminderNote,
                            nowEpochMs = nowEpochMs
                        )
                    )
                } else {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-offer-action-${decision.actionDraft.actionId}",
                            intentionId = decision.intentionId,
                            role = "agent",
                            text = decision.actionDraft.summary,
                            nowEpochMs = nowEpochMs,
                            blocks = listOf(
                                ResponseBlock(
                                    blockId = "block-action-${decision.actionDraft.actionId}",
                                    type = ResponseBlockType.NEXT_ACTION,
                                    title = decision.actionDraft.title,
                                    body = decision.actionDraft.summary,
                                    sourceLabel = "adaptive-builder",
                                    linkedObjectIds = listOf(decision.actionDraft.actionId)
                                )
                            )
                        )
                    )
                }
                bumpState(store, decision.intentionId, nowEpochMs) { it }
            }
            is BuilderDecision.Propose -> {
                val summary = decision.summary +
                    if (decision.effects.isNotEmpty()) {
                        " Effects: ${decision.effects.joinToString("; ")}"
                    } else {
                        ""
                    }
                store.apply(
                    FlowCommand.ProposeRecurringSession(
                        proposalId = decision.proposalId,
                        intentionId = decision.intentionId,
                        title = decision.title,
                        summary = summary,
                        dayOfWeek = decision.dayOfWeek,
                        hour = decision.hour,
                        minute = decision.minute,
                        basedOnFactIds = decision.basedOnFactIds,
                        nowEpochMs = nowEpochMs,
                        daysOfWeek = decision.daysOfWeek.ifEmpty { listOf(decision.dayOfWeek) },
                        durationMinutes = decision.durationMinutes,
                        timeZoneId = decision.timeZoneId.ifBlank { timeZone.id },
                        methodsSummary = decision.methodsSummary,
                        capabilityHonesty = decision.capabilityHonesty,
                        homeImpactNote = decision.homeImpactNote,
                        includeMorningCheckIn = decision.includeMorningCheckIn,
                        includeEveningReview = decision.includeEveningReview
                    )
                )
                // In-chat review card — nothing active until Approve via gateway.
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-propose-${decision.proposalId}-$nowEpochMs",
                        intentionId = decision.intentionId,
                        role = "agent",
                        text = "Ready to begin? Review your routine before activating it.",
                        nowEpochMs = nowEpochMs,
                        blocks = listOf(
                            ResponseBlock(
                                blockId = "block-proposal-${decision.proposalId}",
                                type = ResponseBlockType.PROPOSAL,
                                title = decision.title,
                                body = summary,
                                sourceLabel = "adaptive_builder|review",
                                linkedObjectIds = listOf(decision.proposalId),
                                actions = listOf(
                                    "Start this routine",
                                    "Edit plan",
                                    "Keep discussing"
                                ),
                                active = true
                            )
                        )
                    )
                )
                bumpState(store, decision.intentionId, nowEpochMs) { state ->
                    state.copy(
                        pendingProposalId = decision.proposalId,
                        lifecycleHint = "proposing",
                        currentQuestionId = null
                    )
                }
            }
            is BuilderDecision.Pause -> {
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-pause-${decision.intentionId}-$nowEpochMs",
                        intentionId = decision.intentionId,
                        role = "agent",
                        text = buildString {
                            append(decision.reason)
                            decision.resumeCue?.let { append(" Resume cue: $it") }
                        },
                        nowEpochMs = nowEpochMs
                    )
                )
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = decision.intentionId,
                        phase = IntentionPhase.EXPLORING,
                        nowEpochMs = nowEpochMs
                    )
                )
                bumpState(store, decision.intentionId, nowEpochMs) { state ->
                    state.copy(
                        lifecycleHint = "paused",
                        currentQuestionId = null
                    )
                }
            }
        }
        return ApplyResult(true, "ok", validation.decisionKind, store.snapshot())
    }

    /**
     * §8.5 — replace an ACTIVE [KEY_MOVE_ESTIMATE] with a new relative/absolute
     * utterance via [BuilderDecision.CorrectFact]. Prior fact stays CORRECTED.
     */
    fun correctMoveEstimate(
        store: IntentionFlowStore,
        intentionId: String,
        newUtterance: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        sourceTurnId: String? = null
    ): ApplyResult {
        val prior = store.snapshot().facts.firstOrNull {
            it.intentionId == intentionId &&
                it.key == KEY_MOVE_ESTIMATE &&
                it.status == FactStatus.ACTIVE
        } ?: return ApplyResult(
            false,
            "No active move estimate to correct.",
            "CorrectFact",
            store.snapshot()
        )
        val rev = store.snapshot().adaptiveBuilderStates
            .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L
        val phrase = CreateIntentionChat.extractRelativePhrase(newUtterance)
            ?: newUtterance.trim()
        return applyDecision(
            store,
            BuilderDecision.CorrectFact(
                intentionId = intentionId,
                expectedRevision = rev,
                existingFactId = prior.id,
                replacement = FactDraft(
                    factId = "fact-move-corrected-$intentionId-$nowEpochMs",
                    text = phrase,
                    key = KEY_MOVE_ESTIMATE,
                    rawUtterance = newUtterance.trim(),
                    approximate = RelativeDateAnchoring.looksRelative(newUtterance)
                ),
                sourceTurnId = sourceTurnId ?: "turn-correct-move-$nowEpochMs"
            ),
            nowEpochMs,
            timeZone = timeZone
        )
    }

    fun answerQuestion(
        store: IntentionFlowStore,
        answer: AnswerQuestion,
        factKey: String? = null,
        timeZone: TimeZone = TimeZone.getDefault()
    ): ApplyResult {
        val snap = store.snapshot()
        val validation = BuilderDecisionValidator.validateAnswer(snap, answer)
        if (!validation.accepted) {
            return ApplyResult(false, validation.reason, validation.decisionKind, snap)
        }
        val question = snap.openQuestions.first { it.id == answer.questionId }
        val factId = "fact-${answer.questionId}-${answer.idempotencyKey.hashCode()}"
        store.apply(
            FlowCommand.AnswerOpenQuestion(
                questionId = answer.questionId,
                intentionId = answer.intentionId,
                answerText = answer.normalizedValue.ifBlank { answer.rawValue },
                nowEpochMs = answer.answeredAtEpochMs,
                sourceTurnId = answer.sourceTurnId,
                modality = answer.modality,
                idempotencyKey = answer.idempotencyKey,
                factId = factId
            )
        )
        val key = factKey ?: question.purpose.takeIf { it.isNotBlank() }
        val draft = FactDraft(
            factId = factId,
            text = answer.normalizedValue.ifBlank { answer.rawValue },
            key = key,
            rawUtterance = answer.rawValue,
            normalizedValue = answer.normalizedValue.ifBlank { answer.rawValue },
            approximate = RelativeDateAnchoring.looksRelative(answer.rawValue),
            confirmation = if (RelativeDateAnchoring.looksRelative(answer.rawValue)) {
                FactConfirmation.APPROXIMATE
            } else {
                FactConfirmation.CONFIRMED
            }
        )
        val enriched = enrichRelativeDate(draft, answer.answeredAtEpochMs, timeZone)
        store.apply(
            FlowCommand.SaveTypedFact(
                factId = enriched.factId,
                intentionId = answer.intentionId,
                text = enriched.text,
                nowEpochMs = answer.answeredAtEpochMs,
                key = enriched.key,
                rawUtterance = enriched.rawUtterance,
                normalizedValue = enriched.normalizedValue,
                sourceTurnId = answer.sourceTurnId,
                timeZoneId = enriched.timeZoneId ?: timeZone.id,
                validFromEpochMs = enriched.validFromEpochMs,
                validUntilEpochMs = enriched.validUntilEpochMs,
                estimatedEpochMs = enriched.estimatedEpochMs,
                estimatedRangeStartEpochMs = enriched.estimatedRangeStartEpochMs,
                estimatedRangeEndEpochMs = enriched.estimatedRangeEndEpochMs,
                approximate = enriched.approximate,
                confirmation = enriched.confirmation
            )
        )
        bumpState(store, answer.intentionId, answer.answeredAtEpochMs) { state ->
            state.copy(
                currentQuestionId = null,
                lastAnsweredTurnId = answer.sourceTurnId,
                conversationCursorTurnId = answer.sourceTurnId
            )
        }
        return ApplyResult(true, "ok", validation.decisionKind, store.snapshot())
    }

    fun selectApproaches(
        store: IntentionFlowStore,
        intentionId: String,
        approachIds: List<String>,
        nowEpochMs: Long,
        sourceTurnId: String?,
        expectedRevision: Long,
        modality: String
    ): ApplyResult {
        val snap = store.snapshot()
        val state = snap.adaptiveBuilderStates.firstOrNull { it.intentionId == intentionId }
        if ((state?.revision ?: 0L) != expectedRevision) {
            return ApplyResult(
                false,
                "Stale revision for SelectApproaches.",
                "SelectApproaches",
                snap
            )
        }
        if (sourceTurnId != null && AgentTurnLifecycle.isCancelled(snap, sourceTurnId)) {
            return ApplyResult(false, "Cancelled turn — no approach write.", "SelectApproaches", snap)
        }
        // Idempotent: re-selecting the same set is a no-op success.
        val already = snap.approaches
            .filter { it.intentionId == intentionId && it.status == ApproachStatus.SELECTED }
            .map { it.id }
            .toSet()
        if (already == approachIds.toSet() && already.isNotEmpty()) {
            return ApplyResult(true, "idempotent", "SelectApproaches", snap)
        }
        store.apply(
            FlowCommand.SelectApproaches(
                intentionId = intentionId,
                approachIds = approachIds,
                nowEpochMs = nowEpochMs,
                sourceTurnId = sourceTurnId
            )
        )
        val turnId = sourceTurnId ?: "turn-select-approaches-$nowEpochMs"
        if (store.snapshot().turns.none { it.id == turnId }) {
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = turnId,
                    intentionId = intentionId,
                    role = "user",
                    text = "Selected: ${approachIds.joinToString()}",
                    nowEpochMs = nowEpochMs,
                    modality = modality,
                    blocks = listOf(
                        ResponseBlock(
                            blockId = "block-answered-approaches-$nowEpochMs",
                            type = ResponseBlockType.RECORDED_ENTRY,
                            title = "Answered",
                            body = approachIds.joinToString(),
                            sourceLabel = modality,
                            linkedObjectIds = approachIds + listOf(
                                "approaches-$intentionId-${approachIds.sorted().joinToString("-")}"
                            )
                        )
                    )
                )
            )
        }
        bumpState(store, intentionId, nowEpochMs) { it }
        return ApplyResult(true, "ok", "SelectApproaches", store.snapshot())
    }

    /**
     * Decline a pending proposal without clearing saved facts / approach preferences
     * (worksheet §8.4 review boundary).
     */
    fun declineProposalKeepFacts(
        store: IntentionFlowStore,
        proposalId: String,
        nowEpochMs: Long
    ): IntentionSnapshot {
        store.apply(FlowCommand.DeclineProposal(proposalId = proposalId, nowEpochMs = nowEpochMs))
        val snap = store.snapshot()
        val proposal = snap.proposals.firstOrNull { it.id == proposalId } ?: return snap
        bumpState(store, proposal.intentionId, nowEpochMs) { state ->
            state.copy(
                pendingProposalId = null,
                lifecycleHint = "discovery"
            )
        }
        return store.snapshot()
    }

    fun activeFacts(snapshot: IntentionSnapshot, intentionId: String): List<Fact> =
        snapshot.facts.filter { it.intentionId == intentionId && it.status == FactStatus.ACTIVE }

    fun correctionHistory(snapshot: IntentionSnapshot, key: String, intentionId: String): List<Fact> =
        snapshot.facts
            .filter { it.intentionId == intentionId && it.key == key }
            .sortedBy { it.provenance.learnedAtEpochMs }

    private fun enrichRelativeDate(
        draft: FactDraft,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): FactDraft {
        val raw = draft.rawUtterance ?: draft.text
        val anchored = RelativeDateAnchoring.anchor(raw, nowEpochMs, timeZone) ?: return draft
        return draft.copy(
            rawUtterance = draft.rawUtterance ?: raw,
            normalizedValue = draft.normalizedValue ?: anchored.displayMonthLabel,
            text = draft.text.ifBlank { raw },
            timeZoneId = draft.timeZoneId ?: anchored.timeZoneId,
            estimatedEpochMs = draft.estimatedEpochMs ?: anchored.estimatedEpochMs,
            estimatedRangeStartEpochMs = draft.estimatedRangeStartEpochMs
                ?: anchored.rangeStartEpochMs,
            estimatedRangeEndEpochMs = draft.estimatedRangeEndEpochMs ?: anchored.rangeEndEpochMs,
            approximate = draft.approximate || anchored.approximate,
            confirmation = if (anchored.approximate) {
                FactConfirmation.APPROXIMATE
            } else {
                draft.confirmation
            },
            validFromEpochMs = draft.validFromEpochMs ?: nowEpochMs
        )
    }

    private fun bumpState(
        store: IntentionFlowStore,
        intentionId: String,
        nowEpochMs: Long,
        transform: (AdaptiveBuilderState) -> AdaptiveBuilderState
    ) {
        val snap = store.snapshot()
        val intention = snap.intentions.firstOrNull { it.id == intentionId }
        val prior = snap.adaptiveBuilderStates.firstOrNull { it.intentionId == intentionId }
            ?: AdaptiveBuilderState(
                intentionId = intentionId,
                typeFacets = facetsForShape(intention?.shapeTag ?: "other"),
                updatedAtEpochMs = nowEpochMs
            )
        val next = transform(prior).copy(
            revision = prior.revision + 1,
            updatedAtEpochMs = nowEpochMs,
            policyVersion = AdaptiveBuilderState.POLICY_VERSION
        )
        store.apply(FlowCommand.SetAdaptiveBuilderState(next, nowEpochMs))
    }

    /** Simulated month offset helper for interrupted-session tests. */
    fun plusDays(epochMs: Long, days: Int): Long =
        epochMs + TimeUnit.DAYS.toMillis(days.toLong())
}
