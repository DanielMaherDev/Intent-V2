package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
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
import com.intent.launcher.domain.BuilderDecisionEvent
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
    /** Approach multiselect Ask — chip/tap, typed, and speech share this purpose. */
    const val KEY_SELECTED_APPROACHES = "selected_approaches"

    /** OC2 / §8.8 conditional process eligibility. */
    const val KEY_CITIZENSHIP = "citizenship"
    const val KEY_EMPLOYMENT_ARRANGEMENT = "employment_arrangement"
    const val KEY_RESIDENCE_DURATION = "residence_duration"

    /** OC3 / OC4 / OC5 / OC6 / OC7 / OC9 honesty keys. */
    const val KEY_RACE_STATUS = "race_status"
    const val KEY_HABIT_BASELINE = "habit_baseline"
    const val KEY_USAGE_MEASUREMENT = "usage_measurement"
    const val KEY_RATING_BASELINE = "rating_baseline"
    const val KEY_LEASE_DEADLINE = "lease_deadline"
    const val KEY_NOTICE_STATUS = "notice_status"
    const val KEY_DECIDE_BY_DATE = "decide_by_date"

    /** Chips for motivation Ask (Other appended by [BuilderAnswerCard]). */
    val MOTIVATION_OPTIONS: List<String> = listOf(
        "Moving or travel",
        "Work or school",
        "Everyday conversations"
    )

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
        "language" -> listOf("language_learning", "skill_practice", "dated_outcome")
        "marathon" -> listOf("dated_outcome", "skill_practice", "physical_training")
        "business" -> listOf("exploration", "ambition")
        "move" -> listOf("dated_outcome", "conditional_process")
        "decision" -> listOf("decision")
        "cessation" -> listOf("habit_change")
        "app_use" -> listOf("habit_change", "usage_goal")
        "research" -> listOf("conditional_process")
        "chess" -> listOf("skill_practice", "dated_outcome", "connected_metric")
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
     * Not an LLM — ranks by consequence; skips duplicates / unresolved repeats;
     * zero questions for small errands; no fixed questionnaire length.
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
            !hasUnresolvedPurpose(context, "skills_or_interests")
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

        // —— OC2 / §8.8: eligibility → research pause (research shape; not dated move projects) ——
        if (context.typeFacets.contains("conditional_process") &&
            !context.typeFacets.contains("dated_outcome")
        ) {
            conditionalProcessDecision(context)?.let { return it }
        }

        // —— OC3: dated physical goal — first phase only, no fabricated multi-month plan ——
        if (context.typeFacets.contains("physical_training")) {
            physicalTrainingDecision(context)?.let { return it }
        }

        // —— OC4 / OC5: habit / usage — baseline or measurement honesty, no invented quit/block plan ——
        if (context.typeFacets.contains("habit_change") ||
            context.typeFacets.contains("usage_goal")
        ) {
            habitOrUsageDecision(context)?.let { return it }
        }

        // —— OC9: time-limited decision — decide-by date, never auto-accept ——
        if (context.typeFacets.contains("decision")) {
            decisionCaseDecision(context)?.let { return it }
        }

        // —— OC6: connected metric — manual/provider baseline honesty, no invented ratings ——
        if (context.typeFacets.contains("connected_metric")) {
            connectedMetricDecision(context)?.let { return it }
        }

        // —— OC7: finite move project — constraints + waiting, no invented secured plan ——
        if (context.typeFacets.contains("dated_outcome") &&
            context.typeFacets.contains("conditional_process")
        ) {
            finiteMoveProjectDecision(context)?.let { return it }
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
        if (context.pendingProposals.isEmpty() &&
            hasSelectedApproaches &&
            !hasFrequency &&
            !hasUnresolvedPurpose(context, KEY_PRACTICE_FREQUENCY)
        ) {
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
                !hasUnresolvedPurpose(context, "reconfirm_move_estimate")
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
            context.typeFacets.contains("language_learning") &&
            !hasUnresolvedPurpose(context, KEY_TARGET_LANGUAGE)
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
            context.confirmedFacts.any { it.key == KEY_TARGET_LANGUAGE } &&
            !hasUnresolvedPurpose(context, KEY_MOTIVATION)
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-motivation-${context.intentionId}",
                whyItMatters = "Motivation shapes urgency and which practice to prioritize first.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_MOTIVATION,
                blocksWhat = "move_estimate",
                options = MOTIVATION_OPTIONS,
                priority = 80
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_MOVE_ESTIMATE } &&
            context.confirmedFacts.any { it.key == KEY_MOTIVATION } &&
            !hasUnresolvedPurpose(context, KEY_MOVE_ESTIMATE)
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
            val drafts = defaultApproachDrafts()
            // Seed candidates once; then Ask MULTI_CHOICE so chip/tap, typed, and
            // speech-injected transcripts resolve through AdaptiveAnswerPath.
            if (context.approaches.isEmpty()) {
                return BuilderDecision.OfferApproaches(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    approachDrafts = drafts
                )
            }
            if (context.openQuestions.none {
                    it.purpose == KEY_SELECTED_APPROACHES &&
                        it.state == OpenQuestionState.ASKED
                }
            ) {
                return BuilderDecision.Ask(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    questionId = "q-approaches-${context.intentionId}",
                    whyItMatters = "Which ways of learning appeal to you?",
                    answerSchema = AnswerSchema.MULTI_CHOICE,
                    purpose = KEY_SELECTED_APPROACHES,
                    blocksWhat = "routine_proposal",
                    options = drafts.map { it.title },
                    priority = 60
                )
            }
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason = "Enough known for now — leave discovery resumable.",
            resumeCue = "Continue discussion"
        )
    }

    /** Unanswered OPEN/ASKED/DEFERRED purpose — do not mint a duplicate Ask. */
    private fun hasUnresolvedPurpose(context: BuilderContext, purpose: String): Boolean =
        context.openQuestions.any {
            it.purpose == purpose && it.state != OpenQuestionState.ANSWERED
        }

    /**
     * OC2 / §8.8 — ask eligibility, then pause for sourced research.
     * Never invents legal requirements or an unconditional checklist.
     */
    private fun conditionalProcessDecision(context: BuilderContext): BuilderDecision? {
        if (context.confirmedFacts.none { it.key == KEY_CITIZENSHIP }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-citizenship-${context.intentionId}",
                whyItMatters =
                    "Citizenship and jurisdiction change which registrations may apply — " +
                        "requirements cannot be prescribed without this.",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = KEY_CITIZENSHIP,
                blocksWhat = "sourced_research",
                priority = 95
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_EMPLOYMENT_ARRANGEMENT }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-employment-${context.intentionId}",
                whyItMatters =
                    "Employment vs self-employed vs looking changes which steps apply.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_EMPLOYMENT_ARRANGEMENT,
                blocksWhat = "sourced_research",
                options = listOf("Employed", "Self-employed", "Still looking", "Not sure"),
                priority = 90
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_RESIDENCE_DURATION }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-residence-${context.intentionId}",
                whyItMatters =
                    "How long you have lived here affects which documents may already apply. " +
                        "You can say not sure.",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = KEY_RESIDENCE_DURATION,
                blocksWhat = "sourced_research",
                priority = 85
            )
        }
        // Eligibility known — pause for research; do not invent statutes or a plan.
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "Eligibility facts are recorded. Requirements stay unverified until " +
                    "sourced research completes — no legal checklist invented.",
            resumeCue = "Continue after research"
        )
    }

    /** OC3 — first useful phase only; never fabricate an 8-month workout plan. */
    private fun physicalTrainingDecision(context: BuilderContext): BuilderDecision? {
        if (context.confirmedFacts.none { it.key == KEY_RACE_STATUS }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-race-${context.intentionId}",
                whyItMatters =
                    "Whether a race is chosen shapes the first phase — baseline and " +
                        "consistency come before later months of training.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_RACE_STATUS,
                blocksWhat = "first_phase_proposal",
                options = listOf("Race chosen with date", "Event still to choose", "Not sure"),
                priority = 80
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "First phase stays open: race choice, baseline, and available days. " +
                    "No eight-month workout plan invented.",
            resumeCue = "Continue discussion"
        )
    }

    /** OC4 / OC5 — baseline or measurement honesty; no cessation-from-task or fabricated Shorts claims. */
    private fun habitOrUsageDecision(context: BuilderContext): BuilderDecision? {
        if (context.typeFacets.contains("usage_goal")) {
            if (context.confirmedFacts.none { it.key == KEY_USAGE_MEASUREMENT }) {
                return BuilderDecision.Ask(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    questionId = "q-usage-measure-${context.intentionId}",
                    whyItMatters =
                        "Usage access can measure app foreground time, not Shorts inside YouTube. " +
                            "Choose an honest measurement method before any intervention.",
                    answerSchema = AnswerSchema.SINGLE_CHOICE,
                    purpose = KEY_USAGE_MEASUREMENT,
                    blocksWhat = "usage_intervention",
                    options = listOf(
                        "Track Instagram automatically; log Shorts myself",
                        "Track both apps as total time",
                        "Manual log only"
                    ),
                    priority = 80
                )
            }
            return BuilderDecision.Pause(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                reason =
                    "Measurement method recorded. No overlay, block, or Shorts claim until " +
                        "permission and method match capability.",
                resumeCue = "Continue discussion"
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_HABIT_BASELINE }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-habit-baseline-${context.intentionId}",
                whyItMatters =
                    "A self-reported baseline lets check-ins track change. A checked task " +
                        "does not prove cessation.",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = KEY_HABIT_BASELINE,
                blocksWhat = "check_in_cadence",
                priority = 80
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "Baseline saved. Quit approach and date stay open — no cessation claim " +
                    "from an unchecked day.",
            resumeCue = "Continue discussion"
        )
    }

    /** OC9 — capture decide-by date; never auto-accept an offer. */
    private fun decisionCaseDecision(context: BuilderContext): BuilderDecision? {
        if (context.confirmedFacts.none { it.key == KEY_DECIDE_BY_DATE }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-decide-by-${context.intentionId}",
                whyItMatters =
                    "A decide-by date keeps comparison useful. INTENT will not auto-accept " +
                        "or decline an offer.",
                answerSchema = AnswerSchema.DATE_TIME,
                purpose = KEY_DECIDE_BY_DATE,
                blocksWhat = "offer_comparison",
                priority = 90
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "Decide-by date recorded. Comparison stays open — no auto-accept of the offer.",
            resumeCue = "Continue discussion"
        )
    }

    /**
     * OC6 — Chess.com / connected metric: ask baseline source honesty.
     * Never invents a live rating, games, or lesson completion.
     */
    private fun connectedMetricDecision(context: BuilderContext): BuilderDecision? {
        if (context.confirmedFacts.none { it.key == KEY_RATING_BASELINE }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-rating-baseline-${context.intentionId}",
                whyItMatters =
                    "Without a provider import, use a manual baseline. Do not invent a live " +
                        "Chess.com rating from a stale or missing connection.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_RATING_BASELINE,
                blocksWhat = "practice_routine",
                options = listOf(
                    "Use manual baseline (e.g. 900)",
                    "Refresh from connected provider",
                    "Not connected yet"
                ),
                priority = 85
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "Baseline method recorded. Practice stays distinct from rating — no invented " +
                    "games, lesson completion, or live rating claimed.",
            resumeCue = "Continue discussion"
        )
    }

    /**
     * OC7 — Barcelona→Valencia move: capture lease deadline + notice, then pause with waiting.
     * Never invents viewings or assumes a property is secured.
     */
    private fun finiteMoveProjectDecision(context: BuilderContext): BuilderDecision? {
        if (context.confirmedFacts.none { it.key == KEY_LEASE_DEADLINE }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-lease-${context.intentionId}",
                whyItMatters =
                    "Lease end date shapes the critical path for search and notice — " +
                        "no move plan assumes a place is already secured.",
                answerSchema = AnswerSchema.DATE_TIME,
                purpose = KEY_LEASE_DEADLINE,
                blocksWhat = "move_project_board",
                priority = 90
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_NOTICE_STATUS }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-notice-${context.intentionId}",
                whyItMatters =
                    "Notice and mover vs self-move change which steps are yours versus " +
                        "waiting on landlords or appointments.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_NOTICE_STATUS,
                blocksWhat = "move_project_board",
                options = listOf(
                    "Notice not given yet",
                    "Notice given",
                    "Not sure about movers yet"
                ),
                priority = 85
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "Lease and notice facts saved. External steps stay waiting — no invented " +
                    "viewings or secured-property plan.",
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
        val startedNs = System.nanoTime()
        val snap = store.snapshot()
        val validation = BuilderDecisionValidator.validate(snap, decision, cancelledTurnId)
        if (!validation.accepted) {
            appendDecisionEvent(
                store = store,
                preSnap = snap,
                nowEpochMs = nowEpochMs,
                startedNs = startedNs,
                decisionType = validation.decisionKind,
                intentionId = decision.intentionId,
                accepted = false,
                reason = validation.reason,
                expectedRevision = decision.expectedRevision,
                activeQuestionId = when (decision) {
                    is BuilderDecision.Ask -> decision.questionId
                    else -> snap.adaptiveBuilderStates
                        .firstOrNull { it.intentionId == decision.intentionId }
                        ?.currentQuestionId
                },
                factSourceIds = factSourceIdsFor(snap, decision),
                selectedApproachIds = emptyList(),
                rejectedApproachIds = emptyList(),
                proposalId = (decision as? BuilderDecision.Propose)?.proposalId,
                toolResults = emptyList(),
                sourceTurnId = decision.sourceTurnId
            )
            return ApplyResult(
                false,
                validation.reason,
                validation.decisionKind,
                store.snapshot()
            )
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
        val selectedIds = when (decision) {
            is BuilderDecision.OfferApproaches ->
                decision.approachDrafts.filter { it.selected }.map { it.approachId }
            else -> emptyList()
        }
        val rejectedIds = when (decision) {
            is BuilderDecision.OfferApproaches ->
                decision.approachDrafts.filterNot { it.selected }.map { it.approachId }
            else -> emptyList()
        }
        val toolResults = when (decision) {
            is BuilderDecision.Pause -> listOf("pause_recorded")
            is BuilderDecision.Propose -> listOf("proposal_drafted")
            else -> emptyList()
        }
        appendDecisionEvent(
            store = store,
            preSnap = snap,
            nowEpochMs = nowEpochMs,
            startedNs = startedNs,
            decisionType = validation.decisionKind,
            intentionId = decision.intentionId,
            accepted = true,
            reason = "ok",
            expectedRevision = decision.expectedRevision,
            activeQuestionId = when (decision) {
                is BuilderDecision.Ask -> decision.questionId
                else -> store.snapshot().adaptiveBuilderStates
                    .firstOrNull { it.intentionId == decision.intentionId }
                    ?.currentQuestionId
            },
            factSourceIds = factSourceIdsFor(snap, decision),
            selectedApproachIds = selectedIds,
            rejectedApproachIds = rejectedIds,
            proposalId = (decision as? BuilderDecision.Propose)?.proposalId,
            toolResults = toolResults,
            sourceTurnId = decision.sourceTurnId
        )
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
        val startedNs = System.nanoTime()
        val snap = store.snapshot()
        val validation = BuilderDecisionValidator.validateAnswer(snap, answer)
        if (!validation.accepted) {
            appendDecisionEvent(
                store = store,
                preSnap = snap,
                nowEpochMs = answer.answeredAtEpochMs,
                startedNs = startedNs,
                decisionType = validation.decisionKind,
                intentionId = answer.intentionId,
                accepted = false,
                reason = validation.reason,
                expectedRevision = answer.expectedRevision,
                activeQuestionId = answer.questionId,
                factSourceIds = activeFactIds(snap, answer.intentionId),
                selectedApproachIds = emptyList(),
                rejectedApproachIds = emptyList(),
                proposalId = null,
                toolResults = emptyList(),
                sourceTurnId = answer.sourceTurnId
            )
            return ApplyResult(
                false,
                validation.reason,
                validation.decisionKind,
                store.snapshot()
            )
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
        appendDecisionEvent(
            store = store,
            preSnap = snap,
            nowEpochMs = answer.answeredAtEpochMs,
            startedNs = startedNs,
            decisionType = validation.decisionKind,
            intentionId = answer.intentionId,
            accepted = true,
            reason = "ok",
            expectedRevision = answer.expectedRevision,
            activeQuestionId = answer.questionId,
            factSourceIds = activeFactIds(snap, answer.intentionId),
            selectedApproachIds = emptyList(),
            rejectedApproachIds = emptyList(),
            proposalId = null,
            toolResults = listOf("answer_recorded"),
            sourceTurnId = answer.sourceTurnId
        )
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
        val startedNs = System.nanoTime()
        val snap = store.snapshot()
        val state = snap.adaptiveBuilderStates.firstOrNull { it.intentionId == intentionId }
        val candidateIds = snap.approaches
            .filter { it.intentionId == intentionId }
            .map { it.id }
        val rejectedIds = candidateIds.filterNot { it in approachIds }
        if ((state?.revision ?: 0L) != expectedRevision) {
            appendDecisionEvent(
                store = store,
                preSnap = snap,
                nowEpochMs = nowEpochMs,
                startedNs = startedNs,
                decisionType = "SelectApproaches",
                intentionId = intentionId,
                accepted = false,
                reason = "Stale revision for SelectApproaches.",
                expectedRevision = expectedRevision,
                activeQuestionId = state?.currentQuestionId,
                factSourceIds = activeFactIds(snap, intentionId),
                selectedApproachIds = approachIds,
                rejectedApproachIds = rejectedIds,
                proposalId = null,
                toolResults = emptyList(),
                sourceTurnId = sourceTurnId
            )
            return ApplyResult(
                false,
                "Stale revision for SelectApproaches.",
                "SelectApproaches",
                store.snapshot()
            )
        }
        if (sourceTurnId != null && AgentTurnLifecycle.isCancelled(snap, sourceTurnId)) {
            appendDecisionEvent(
                store = store,
                preSnap = snap,
                nowEpochMs = nowEpochMs,
                startedNs = startedNs,
                decisionType = "SelectApproaches",
                intentionId = intentionId,
                accepted = false,
                reason = "Cancelled turn — no approach write.",
                expectedRevision = expectedRevision,
                activeQuestionId = state?.currentQuestionId,
                factSourceIds = activeFactIds(snap, intentionId),
                selectedApproachIds = approachIds,
                rejectedApproachIds = rejectedIds,
                proposalId = null,
                toolResults = emptyList(),
                sourceTurnId = sourceTurnId
            )
            return ApplyResult(
                false,
                "Cancelled turn — no approach write.",
                "SelectApproaches",
                store.snapshot()
            )
        }
        // Idempotent: re-selecting the same set is a no-op success.
        val already = snap.approaches
            .filter { it.intentionId == intentionId && it.status == ApproachStatus.SELECTED }
            .map { it.id }
            .toSet()
        if (already == approachIds.toSet() && already.isNotEmpty()) {
            appendDecisionEvent(
                store = store,
                preSnap = snap,
                nowEpochMs = nowEpochMs,
                startedNs = startedNs,
                decisionType = "SelectApproaches",
                intentionId = intentionId,
                accepted = true,
                reason = "idempotent",
                expectedRevision = expectedRevision,
                activeQuestionId = state?.currentQuestionId,
                factSourceIds = activeFactIds(snap, intentionId),
                selectedApproachIds = approachIds,
                rejectedApproachIds = rejectedIds,
                proposalId = null,
                toolResults = listOf("select_idempotent"),
                sourceTurnId = sourceTurnId
            )
            return ApplyResult(true, "idempotent", "SelectApproaches", store.snapshot())
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
        appendDecisionEvent(
            store = store,
            preSnap = snap,
            nowEpochMs = nowEpochMs,
            startedNs = startedNs,
            decisionType = "SelectApproaches",
            intentionId = intentionId,
            accepted = true,
            reason = "ok",
            expectedRevision = expectedRevision,
            activeQuestionId = state?.currentQuestionId,
            factSourceIds = activeFactIds(snap, intentionId),
            selectedApproachIds = approachIds,
            rejectedApproachIds = rejectedIds,
            proposalId = null,
            toolResults = listOf("approaches_selected"),
            sourceTurnId = sourceTurnId
        )
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

    private fun activeFactIds(snap: IntentionSnapshot, intentionId: String): List<String> =
        snap.facts
            .filter { it.intentionId == intentionId && it.status == FactStatus.ACTIVE }
            .map { it.id }

    private fun factSourceIdsFor(snap: IntentionSnapshot, decision: BuilderDecision): List<String> =
        when (decision) {
            is BuilderDecision.Propose ->
                decision.basedOnFactIds.ifEmpty { activeFactIds(snap, decision.intentionId) }
            is BuilderDecision.CorrectFact ->
                listOf(decision.existingFactId) + activeFactIds(snap, decision.intentionId)
            else -> activeFactIds(snap, decision.intentionId)
        }

    /**
     * Gate F: append a redacted decision event (IDs/versions/duration only).
     * Strips secret-like substrings from [reason]; never stores chat or audio.
     */
    private fun appendDecisionEvent(
        store: IntentionFlowStore,
        preSnap: IntentionSnapshot,
        nowEpochMs: Long,
        startedNs: Long,
        decisionType: String,
        intentionId: String,
        accepted: Boolean,
        reason: String,
        expectedRevision: Long,
        activeQuestionId: String?,
        factSourceIds: List<String>,
        selectedApproachIds: List<String>,
        rejectedApproachIds: List<String>,
        proposalId: String?,
        toolResults: List<String>,
        sourceTurnId: String?
    ) {
        val after = store.snapshot()
        val resultingRevision = after.adaptiveBuilderStates
            .firstOrNull { it.intentionId == intentionId }
            ?.revision
            ?: preSnap.adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }
                ?.revision
            ?: expectedRevision
        val durationMs = ((System.nanoTime() - startedNs) / 1_000_000L).coerceAtLeast(0L)
        val event = BuilderDecisionEvent(
            id = "dec-$intentionId-$nowEpochMs-$decisionType-${after.decisionTrail.size}",
            atEpochMs = nowEpochMs,
            intentionId = intentionId,
            decisionType = decisionType,
            accepted = accepted,
            reason = redactDecisionReason(reason),
            activeQuestionId = activeQuestionId,
            factSourceIds = factSourceIds.distinct(),
            selectedApproachIds = selectedApproachIds.distinct(),
            rejectedApproachIds = rejectedApproachIds.distinct(),
            capabilitySnapshotVersion = CapabilityRegistry.SNAPSHOT_VERSION,
            policyVersion = AdaptiveBuilderState.POLICY_VERSION,
            proposalId = proposalId,
            toolResults = toolResults,
            expectedRevision = expectedRevision,
            resultingRevision = resultingRevision,
            applyDurationMs = durationMs,
            sourceTurnId = sourceTurnId
        )
        store.apply(FlowCommand.AppendDecisionEvent(event, nowEpochMs))
    }

    /** Drop API-key-like tokens from free-text reason fields. */
    internal fun redactDecisionReason(reason: String): String {
        if (reason.isBlank()) return reason
        return SECRET_LIKE.replace(reason, "[redacted]")
    }

    private val SECRET_LIKE = Regex(
        """(?i)\b(sk-[A-Za-z0-9_\-]{8,}|Bearer\s+[A-Za-z0-9\-._~+/]+=*|api[_-]?key\s*[:=]\s*\S+)\b"""
    )

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

    fun defaultApproachDrafts(): List<ApproachDraft> = listOf(
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

    /**
     * Map a chip / typed / spoken answer onto approach ids by title (or id) containment.
     * Multi-select answers may join titles with commas / "and" / "+".
     */
    fun matchApproachIds(
        snapshot: IntentionSnapshot,
        intentionId: String,
        rawValue: String
    ): List<String> {
        val lower = rawValue.lowercase()
        if (lower.isBlank()) return emptyList()
        return snapshot.approaches
            .filter { it.intentionId == intentionId }
            .filter { approach ->
                lower.contains(approach.title.lowercase()) ||
                    lower.contains(approach.id.lowercase())
            }
            .map { it.id }
            .distinct()
    }
}
