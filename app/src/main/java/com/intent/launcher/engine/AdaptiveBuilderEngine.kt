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

    /** OC2 / §8.8 conditional process eligibility + resume. */
    const val KEY_CITIZENSHIP = "citizenship"
    const val KEY_EMPLOYMENT_ARRANGEMENT = "employment_arrangement"
    const val KEY_RESIDENCE_DURATION = "residence_duration"
    /** Post-Pause: who is handling registration / payroll paperwork. */
    const val KEY_EMPLOYER_HANDLING = "employer_handling"
    /** Honest research-job status — never invents verified legal requirements. */
    const val KEY_RESEARCH_STATUS = "research_status"
    /** Per-item applicability on a conditional checklist (Applies / Already done / Not sure). */
    const val KEY_CHECKLIST_APPLICABILITY = "checklist_applicability"
    const val KEY_RESEARCH_RESUME = "research_resume"

    const val CHIP_CONTINUE_AFTER_RESEARCH = "Continue after research"
    const val CHIP_BUILD_SOURCED_CHECKLIST = "Build a sourced research checklist"
    const val CHIP_KEEP_CLARIFYING = "Keep clarifying — no checklist yet"

    /**
     * Generic conditional research questions — verify on authoritative sources;
     * never treat as statutes or unconditional obligations.
     */
    val CONDITIONAL_RESEARCH_QUESTIONS: List<String> = listOf(
        "Confirm whether registration / residence steps apply — verify on official portals; " +
            "record source URL + retrieval date.",
        "Confirm social-security / affiliation steps for the employment arrangement — " +
            "verify on official portals; record source URL + retrieval date.",
        "Confirm tax identification steps if still needed — verify on official portals; " +
            "record source URL + retrieval date.",
        "Confirm any employer-side notifications that may apply — verify on official portals; " +
            "do not invent obligations."
    )

    val EMPLOYER_HANDLING_OPTIONS: List<String> = listOf(
        "Yes — payroll or registration is set up",
        "Employer handling some paperwork",
        "No — I am handling it myself",
        "Not sure"
    )

    val CHECKLIST_APPLICABILITY_OPTIONS: List<String> = listOf(
        "Applies",
        "Already done",
        "Not sure"
    )

    /** OC3 / OC4 / OC5 / OC6 / OC7 / OC9 honesty keys. */
    const val KEY_RACE_STATUS = "race_status"
    /** How runs/sessions are recorded in the first phase (manual / app / choose race). */
    const val KEY_ACTIVITY_LOG_METHOD = "activity_log_method"
    /**
     * Self-reported timed activity (run, walk, practice session). One ACTIVE entry;
     * corrections use [CorrectFact] rather than a second run.
     */
    const val KEY_ACTIVITY_REPORT = "activity_report"
    const val KEY_HABIT_BASELINE = "habit_baseline"
    const val KEY_USAGE_MEASUREMENT = "usage_measurement"
    const val KEY_RATING_BASELINE = "rating_baseline"
    /** OC6 — practice mix stays distinct from the rating goal / imported metric. */
    const val KEY_PRACTICE_PREFERENCE = "practice_preference"
    const val KEY_LEASE_DEADLINE = "lease_deadline"
    const val KEY_HOUSING_REQUIREMENTS = "housing_requirements"
    const val KEY_NOTICE_STATUS = "notice_status"
    const val KEY_EXTERNAL_WAIT = "external_wait"
    const val KEY_DECIDE_BY_DATE = "decide_by_date"
    /** Generic decision support — criteria / alternatives / unknowns / refresh. */
    const val KEY_DECISION_CRITERIA = "decision_criteria"
    const val KEY_DECISION_ALTERNATIVES = "decision_alternatives"
    const val KEY_DECISION_UNKNOWNS = "decision_unknowns"
    const val KEY_DECISION_SOURCE_FACT = "decision_source_fact"
    const val KEY_COMPARISON_REFRESH = "comparison_refresh"

    /** Unformed ambition / exploration — explore before execute (OTHER §8). */
    const val KEY_SKILLS_OR_INTERESTS = "skills_or_interests"
    const val KEY_WEEKLY_TIME_BUDGET = "weekly_time_budget"
    const val KEY_EXPLORATION_MODE = "exploration_mode"
    const val KEY_SELECTED_IDEA = "selected_idea"
    const val KEY_VALIDATION_APPROACH = "validation_approach"

    val EXPLORATION_MODE_OPTIONS: List<String> = listOf(
        "Brainstorm first",
        "Examine problems people pay to solve",
        "Test one idea quickly"
    )

    val WEEKLY_TIME_OPTIONS: List<String> = listOf(
        "About five hours a week",
        "A few hours a week",
        "More than five hours",
        "Not sure yet"
    )

    val VALIDATION_APPROACH_OPTIONS: List<String> = listOf(
        "Validate with a few potential customers",
        "Keep exploring",
        "Drop it"
    )

    private val ACTIVITY_DURATION_REGEX = Regex(
        """(?i)(?:\b(?:ran|run|jogged|jog|walked|walk|trained|cycled|swam|workout|worked\s*out)\b[^\d]{0,40})?(\d{1,3})\s*(?:minutes?|mins?|min)\b"""
    )
    private val ACTIVITY_CORRECTION_PREFIX = Regex(
        """(?i)^\s*(?:actually|i\s*meant|correction|sorry[, ]*|wait[, ]*|no[, ]*)"""
    )

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
        // Unformed ambition / bare exploration — explore before execute (never launch ops).
        if (context.typeFacets.any { it == "ambition" || it == "exploration" } &&
            context.typeFacets.none {
                it == "language_learning" ||
                    it == "physical_training" ||
                    it == "habit_change" ||
                    it == "usage_goal" ||
                    it == "decision" ||
                    it == "connected_metric" ||
                    it == "conditional_process" ||
                    it == "dated_outcome" ||
                    it == "one_off_action"
            }
        ) {
            ambitionExplorationDecision(context)?.let { return it }
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
     * OTHER §8 — unformed ambition: skills → time budget → exploration mode →
     * Explore ideas / Choose an idea to validate / small experiment.
     * Never invents launch checklists, sales forecasts, or business ops.
     */
    private fun ambitionExplorationDecision(context: BuilderContext): BuilderDecision? {
        if (context.confirmedFacts.none { it.key == KEY_SKILLS_OR_INTERESTS } &&
            !hasUnresolvedPurpose(context, KEY_SKILLS_OR_INTERESTS)
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-explore-skills-${context.intentionId}",
                whyItMatters =
                    "Without an idea chosen, a launch checklist would be premature. " +
                        "Skills and interests shape what is worth exploring.",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = KEY_SKILLS_OR_INTERESTS,
                blocksWhat = "exploration_mode",
                priority = 95
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_WEEKLY_TIME_BUDGET } &&
            !hasUnresolvedPurpose(context, KEY_WEEKLY_TIME_BUDGET)
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-weekly-time-${context.intentionId}",
                whyItMatters =
                    "A weekly time budget keeps exploration honest — " +
                        "five hours a week is a constraint, not a launch plan.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_WEEKLY_TIME_BUDGET,
                blocksWhat = "exploration_mode",
                options = WEEKLY_TIME_OPTIONS,
                priority = 90
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_EXPLORATION_MODE } &&
            !hasUnresolvedPurpose(context, KEY_EXPLORATION_MODE)
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-exploration-mode-${context.intentionId}",
                whyItMatters =
                    "Brainstorm, examine paid problems, or test quickly — " +
                        "choosing how to explore comes before any execution plan.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_EXPLORATION_MODE,
                blocksWhat = "ideas_board_or_validation",
                options = EXPLORATION_MODE_OPTIONS,
                priority = 85
            )
        }

        val modeText = context.confirmedFacts
            .firstOrNull { it.key == KEY_EXPLORATION_MODE }
            ?.let { it.normalizedValue ?: it.text }
            .orEmpty()
        val wantsValidate = looksLikeValidateMode(modeText) ||
            context.confirmedFacts.any { it.key == KEY_SELECTED_IDEA }

        if (wantsValidate) {
            if (context.confirmedFacts.none { it.key == KEY_SELECTED_IDEA } &&
                !hasUnresolvedPurpose(context, KEY_SELECTED_IDEA)
            ) {
                return BuilderDecision.Ask(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    questionId = "q-selected-idea-${context.intentionId}",
                    whyItMatters =
                        "Validation needs one idea chosen — do not assume an idea " +
                            "was picked during brainstorming.",
                    answerSchema = AnswerSchema.FREE_TEXT,
                    purpose = KEY_SELECTED_IDEA,
                    blocksWhat = "validation_experiment",
                    priority = 80
                )
            }
            if (context.confirmedFacts.none { it.key == KEY_VALIDATION_APPROACH } &&
                !hasUnresolvedPurpose(context, KEY_VALIDATION_APPROACH)
            ) {
                return BuilderDecision.Ask(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    questionId = "q-validation-approach-${context.intentionId}",
                    whyItMatters =
                        "Choose whether to validate with potential customers, " +
                            "keep exploring, or drop the idea.",
                    answerSchema = AnswerSchema.SINGLE_CHOICE,
                    purpose = KEY_VALIDATION_APPROACH,
                    blocksWhat = "validation_experiment",
                    options = VALIDATION_APPROACH_OPTIONS,
                    priority = 75
                )
            }
            val approachText = context.confirmedFacts
                .firstOrNull { it.key == KEY_VALIDATION_APPROACH }
                ?.let { it.normalizedValue ?: it.text }
                .orEmpty()
                .lowercase()
            if (approachText.contains("keep exploring") || approachText.contains("drop")) {
                return BuilderDecision.Pause(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    reason =
                        "Staying in exploration — no business operations, sales forecasts, " +
                            "or revenue tracker invented.",
                    resumeCue = "Choose an idea to validate"
                )
            }
            if (!hasOfferedAction(context, "validation_experiment") &&
                !hasOfferedAction(context, "Small validation experiment")
            ) {
                return BuilderDecision.OfferAction(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    actionDraft = ActionDraft(
                        actionId = "validation-experiment-${context.intentionId}",
                        title = "Small validation experiment",
                        summary =
                            "A small experiment for this week plus a check-in to review " +
                                "what you learn — not a launch checklist or revenue forecast.",
                        kind = "validation_experiment"
                    )
                )
            }
            return BuilderDecision.Pause(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                reason =
                    "Validation experiment is proposed. Nothing is scheduled until you " +
                        "approve a check-in — no ops plan invented.",
                resumeCue = "Review experiment"
            )
        }

        // Brainstorm / examine problems — Explore ideas session, then pause.
        if (!hasOfferedAction(context, "explore_ideas") &&
            !hasOfferedAction(context, "Explore ideas")
        ) {
            return BuilderDecision.OfferAction(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                actionDraft = ActionDraft(
                    actionId = "explore-ideas-${context.intentionId}",
                    title = "Explore ideas",
                    summary =
                        "Guided Explore ideas session with a small ideas board under your " +
                            "weekly time budget. Later you can Choose an idea to validate — " +
                            "no launch checklist.",
                    kind = "explore_ideas"
                )
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "Exploration stays open on the ideas board. Choosing one idea to validate " +
                    "comes next — no business operations or forecasts invented.",
            resumeCue = "Choose an idea to validate"
        )
    }

    private fun looksLikeValidateMode(modeText: String): Boolean {
        val t = modeText.lowercase()
        return t.contains("test") || t.contains("validate") || t.contains("quickly")
    }

    private fun hasOfferedAction(context: BuilderContext, needle: String): Boolean {
        val n = needle.lowercase()
        return context.recentTurns.any { turn ->
            turn.blocks.any {
                it.title.lowercase().contains(n) ||
                    it.body.lowercase().contains(n)
            } || turn.text.lowercase().contains(n)
        }
    }

    /**
     * OC2 / §8.8 — eligibility → research Pause → resume → employer Ask →
     * researching status → conditional checklist Propose (Review plan).
     * Never invents legal requirements or an unconditional checklist.
     * Generic for any `conditional_process` facet (not one jurisdiction title).
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

        val lifecycle = context.lifecycleState.lowercase()
        val resumed = lifecycle == "researching" ||
            lifecycle == "reviewing" ||
            context.confirmedFacts.any { it.key == KEY_RESEARCH_RESUME }

        // Conv1: eligibility known — Pause until the user resumes (do not invent a plan).
        if (!resumed) {
            if (lifecycle != "paused" && !hasUnresolvedPurpose(context, KEY_RESEARCH_RESUME)) {
                return BuilderDecision.Pause(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    reason =
                        "Eligibility facts are recorded. Requirements stay unverified until " +
                            "sourced research completes — no legal checklist invented.",
                    resumeCue = CHIP_CONTINUE_AFTER_RESEARCH
                )
            }
            // After Pause: surface an explicit resume Ask so Discuss uses AdaptiveAnswerPath
            // (not a parallel fixture scenario).
            if (!hasUnresolvedPurpose(context, KEY_RESEARCH_RESUME)) {
                return BuilderDecision.Ask(
                    intentionId = context.intentionId,
                    expectedRevision = context.revision,
                    questionId = "q-research-resume-${context.intentionId}",
                    whyItMatters =
                        "Eligibility is saved. Resume when you want a sourced checklist — " +
                            "nothing is scheduled until you review a plan.",
                    answerSchema = AnswerSchema.SINGLE_CHOICE,
                    purpose = KEY_RESEARCH_RESUME,
                    blocksWhat = "sourced_checklist_review",
                    options = listOf(
                        CHIP_CONTINUE_AFTER_RESEARCH,
                        CHIP_BUILD_SOURCED_CHECKLIST
                    ),
                    priority = 88
                )
            }
            return null
        }

        if (context.confirmedFacts.none { it.key == KEY_EMPLOYER_HANDLING }) {
            if (hasUnresolvedPurpose(context, KEY_EMPLOYER_HANDLING)) return null
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-employer-handling-${context.intentionId}",
                whyItMatters =
                    "Whether an employer already handles registration or payroll changes " +
                        "which steps still need your action.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_EMPLOYER_HANDLING,
                blocksWhat = "sourced_checklist_review",
                options = EMPLOYER_HANDLING_OPTIONS,
                priority = 86
            )
        }

        if (context.confirmedFacts.none { it.key == KEY_RESEARCH_STATUS }) {
            if (hasUnresolvedPurpose(context, KEY_RESEARCH_STATUS)) return null
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-research-status-${context.intentionId}",
                whyItMatters =
                    "Researching current requirements — status is in progress until each " +
                        "item is checked against an authoritative source. No statutes invented.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_RESEARCH_STATUS,
                blocksWhat = "sourced_checklist_review",
                options = listOf(
                    "Researching current requirements",
                    "Sources still unverified"
                ),
                priority = 84
            )
        }

        if (context.confirmedFacts.none { it.key == KEY_CHECKLIST_APPLICABILITY }) {
            if (hasUnresolvedPurpose(context, KEY_CHECKLIST_APPLICABILITY)) return null
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-checklist-applicability-${context.intentionId}",
                whyItMatters =
                    "Mark each research item Applies, Already done, or Not sure — " +
                        "confirmed steps stay separate from ones still to verify.",
                answerSchema = AnswerSchema.MULTI_CHOICE,
                purpose = KEY_CHECKLIST_APPLICABILITY,
                blocksWhat = "sourced_checklist_review",
                options = CHECKLIST_APPLICABILITY_OPTIONS,
                priority = 82
            )
        }

        if (context.pendingProposals.isEmpty()) {
            val basedOn = context.confirmedFacts
                .filter {
                    it.key in setOf(
                        KEY_CITIZENSHIP,
                        KEY_EMPLOYMENT_ARRANGEMENT,
                        KEY_RESIDENCE_DURATION,
                        KEY_EMPLOYER_HANDLING,
                        KEY_RESEARCH_STATUS,
                        KEY_CHECKLIST_APPLICABILITY
                    )
                }
                .map { it.id }
            val questions = CONDITIONAL_RESEARCH_QUESTIONS.joinToString("\n") { "• $it" }
            return BuilderDecision.Propose(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                proposalId = "proposal-conditional-checklist-${context.intentionId}",
                title = "Review plan — conditional sourced checklist",
                summary =
                    "Sourced checklist (research questions only — not legal advice). " +
                        "Items you marked Applies / Already done / Not sure stay labelled. " +
                        "Prerequisites and external appointments are waits, not self-complete tasks. " +
                        "Nothing dated is scheduled until you approve Review plan.\n$questions",
                dayOfWeek = 1,
                hour = 10,
                minute = 0,
                basedOnFactIds = basedOn,
                rationale =
                    "Conditional on your eligibility and employer-handling facts; " +
                        "official sources must answer each item.",
                effects = listOf(
                    "No dated tasks until Review plan is approved",
                    "No invented statutes or unconditional EU-citizen checklist"
                ),
                capabilityHonesty =
                    "Live web research may be unavailable — verify on official portals; " +
                        "record source URL + retrieval date per item.",
                homeImpactNote = "Home stays quiet until you approve the reviewed plan."
            )
        }

        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "Conditional checklist is ready for Review plan — " +
                    "dated tasks stay off until you approve.",
            resumeCue = "Review plan"
        )
    }

    /** Discuss chips for conditional research on the active intention (not a fixture id). */
    fun conditionalResearchChips(context: BuilderContext?): List<String> {
        if (context == null) return emptyList()
        if (!context.typeFacets.contains("conditional_process")) return emptyList()
        if (context.typeFacets.contains("dated_outcome")) return emptyList()
        val hasEligibility =
            context.confirmedFacts.any { it.key == KEY_CITIZENSHIP } &&
                context.confirmedFacts.any { it.key == KEY_EMPLOYMENT_ARRANGEMENT } &&
                context.confirmedFacts.any { it.key == KEY_RESIDENCE_DURATION }
        if (!hasEligibility) return emptyList()
        val resumed = context.lifecycleState.equals("researching", ignoreCase = true) ||
            context.lifecycleState.equals("reviewing", ignoreCase = true) ||
            context.confirmedFacts.any { it.key == KEY_RESEARCH_RESUME }
        return if (!resumed) {
            listOf(
                CHIP_CONTINUE_AFTER_RESEARCH,
                CHIP_BUILD_SOURCED_CHECKLIST,
                CHIP_KEEP_CLARIFYING
            )
        } else {
            emptyList()
        }
    }

    /**
     * Handle resume chips for conditional research on [intentionId].
     * @return true when the chip was consumed.
     */
    fun handleConditionalResearchChip(
        store: IntentionFlowStore,
        intentionId: String,
        chip: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Boolean {
        val trimmed = chip.trim()
        val isResume =
            trimmed.equals(CHIP_CONTINUE_AFTER_RESEARCH, ignoreCase = true) ||
                trimmed.contains("sourced research checklist", ignoreCase = true) ||
                trimmed.equals(CHIP_BUILD_SOURCED_CHECKLIST, ignoreCase = true)
        val isClarify =
            trimmed.contains("Keep clarifying", ignoreCase = true) ||
                trimmed.contains("no checklist", ignoreCase = true)
        if (!isResume && !isClarify) return false

        val ctx = projectContext(store.snapshot(), intentionId, nowEpochMs, timeZone) ?: return false
        if (!ctx.typeFacets.contains("conditional_process")) return false

        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-cond-user-$nowEpochMs",
                intentionId = intentionId,
                role = "user",
                text = trimmed,
                nowEpochMs = nowEpochMs,
                modality = "TEXT_CHIP"
            )
        )

        if (isClarify) {
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-cond-clarify-$nowEpochMs",
                    intentionId = intentionId,
                    role = "agent",
                    text =
                        "Fine — no checklist invented. Eligibility facts stay as recorded. " +
                            "Resume with Continue after research when you want a sourced plan.",
                    nowEpochMs = nowEpochMs + 1
                )
            )
            return true
        }

        // Mark resume + advance policy (employer Ask → research status → Review plan).
        val rev = ctx.revision
        if (ctx.confirmedFacts.none { it.key == KEY_RESEARCH_RESUME }) {
            applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = intentionId,
                    expectedRevision = rev,
                    factDraft = FactDraft(
                        factId = "fact-research-resume-$intentionId",
                        text = CHIP_CONTINUE_AFTER_RESEARCH,
                        key = KEY_RESEARCH_RESUME,
                        rawUtterance = trimmed,
                        normalizedValue = "resumed"
                    ),
                    sourceTurnId = "turn-cond-resume-$nowEpochMs"
                ),
                nowEpochMs + 1,
                timeZone = timeZone
            )
        }
        bumpState(store, intentionId, nowEpochMs + 2) { state ->
            state.copy(lifecycleHint = "researching", currentQuestionId = null)
        }
        // Project next Ask (employer handling) onto the active intention.
        projectPendingAsk(store, intentionId, nowEpochMs + 3, timeZone)
        return true
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
        if (context.confirmedFacts.none { it.key == KEY_ACTIVITY_LOG_METHOD }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-activity-log-${context.intentionId}",
                whyItMatters =
                    "First phase needs an honest way to record runs — choose a race, " +
                        "connect an installed running app only if available, or log manually. " +
                        "No fabricated race date or multi-month workout plan.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_ACTIVITY_LOG_METHOD,
                blocksWhat = "activity_tracking",
                options = listOf(
                    "Choose race",
                    "Log runs manually",
                    "Connect an installed running app"
                ),
                priority = 75
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "First phase: race choice, baseline, and consistency on available days. " +
                    "Log runs with the method you chose. No eight-month workout plan invented.",
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

    /**
     * Time-limited decision support — decide-by date, criteria, alternatives,
     * unknowns, then pause. Never auto-accepts / declines for the user.
     */
    private fun decisionCaseDecision(context: BuilderContext): BuilderDecision? {
        if (context.confirmedFacts.none { it.key == KEY_DECIDE_BY_DATE }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-decide-by-${context.intentionId}",
                whyItMatters =
                    "Confirm the exact decide-by date. INTENT helps you decide by then — " +
                        "it will not auto-accept or decline for you.",
                answerSchema = AnswerSchema.DATE_TIME,
                purpose = KEY_DECIDE_BY_DATE,
                blocksWhat = "decision_comparison",
                priority = 90
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_DECISION_CRITERIA } &&
            !hasUnresolvedPurpose(context, KEY_DECISION_CRITERIA)
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-decision-criteria-${context.intentionId}",
                whyItMatters =
                    "Rank what matters most so comparison stays honest. Pick one primary " +
                        "focus (or Other) — INTENT will not choose the outcome.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_DECISION_CRITERIA,
                blocksWhat = "decision_comparison",
                options = listOf(
                    "Pay / compensation",
                    "Remote flexibility",
                    "Work / role fit",
                    "Growth",
                    "Location"
                ),
                priority = 85
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_DECISION_ALTERNATIVES } &&
            !hasUnresolvedPurpose(context, KEY_DECISION_ALTERNATIVES)
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-decision-alts-${context.intentionId}",
                whyItMatters =
                    "Side-by-side comparison needs at least one alternative " +
                        "(current role, another offer, or none yet).",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_DECISION_ALTERNATIVES,
                blocksWhat = "decision_comparison",
                options = listOf(
                    "Current role / job",
                    "Another offer",
                    "No alternative yet"
                ),
                priority = 80
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_DECISION_UNKNOWNS } &&
            !hasUnresolvedPurpose(context, KEY_DECISION_UNKNOWNS)
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-decision-unknowns-${context.intentionId}",
                whyItMatters =
                    "List unknown information so we can propose asking the source — " +
                        "do not invent missing facts.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_DECISION_UNKNOWNS,
                blocksWhat = "decision_comparison",
                options = listOf(
                    "Remote / hybrid policy",
                    "Total compensation details",
                    "Team / manager fit",
                    "Nothing unknown right now"
                ),
                priority = 75
            )
        }
        val unknowns = context.confirmedFacts.firstOrNull { it.key == KEY_DECISION_UNKNOWNS }
        val unknownsOpen = unknowns != null &&
            !unknowns.text.contains("nothing unknown", ignoreCase = true) &&
            !unknowns.normalizedValue.orEmpty().contains("nothing", ignoreCase = true)
        if (unknownsOpen &&
            context.confirmedFacts.none { it.key == KEY_COMPARISON_REFRESH } &&
            !hasUnresolvedPurpose(context, KEY_COMPARISON_REFRESH)
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-ask-source-${context.intentionId}",
                whyItMatters =
                    "Unknown information is still open. Propose asking the source, " +
                        "or keep comparing with what you know — INTENT will not decide.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_COMPARISON_REFRESH,
                blocksWhat = "decision_review",
                options = listOf(
                    DecisionSupport.CHIP_ASK_SOURCE,
                    DecisionSupport.CHIP_UPDATE_COMPARISON,
                    DecisionSupport.CHIP_DISCUSS_FLEXIBILITY
                ),
                priority = 70
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "Decide-by date and comparison inputs are recorded. Decision review stays " +
                    "open — no auto-accept or decline.",
            resumeCue = "Continue discussion"
        )
    }

    /**
     * OC6 — connected partner metric: verify baseline provenance, then practice mix.
     * Never invents a live rating, games, or lesson completion; practice ≠ rating proof.
     */
    private fun connectedMetricDecision(context: BuilderContext): BuilderDecision? {
        if (context.confirmedFacts.none { it.key == KEY_RATING_BASELINE }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-rating-baseline-${context.intentionId}",
                whyItMatters =
                    "Without a provider import, use a manual baseline. Do not invent a live " +
                        "partner rating from a stale or missing connection.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_RATING_BASELINE,
                blocksWhat = "practice_routine",
                options = CreatePathSeedHonesty.RATING_BASELINE_OPTIONS,
                priority = 85
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_PRACTICE_PREFERENCE } &&
            !hasUnresolvedPurpose(context, KEY_PRACTICE_PREFERENCE)
        ) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-practice-preference-${context.intentionId}",
                whyItMatters =
                    "Practice stays distinct from the rating goal — a played game is not " +
                        "proof that the imported or manual rating improved.",
                answerSchema = AnswerSchema.SINGLE_CHOICE,
                purpose = KEY_PRACTICE_PREFERENCE,
                blocksWhat = "practice_routine",
                options = CreatePathSeedHonesty.PRACTICE_PREFERENCE_OPTIONS,
                priority = 80
            )
        }
        return BuilderDecision.Pause(
            intentionId = context.intentionId,
            expectedRevision = context.revision,
            reason =
                "Baseline and practice preference recorded. Rating chart uses imported or " +
                    "user-entered values only — no invented games, lesson completion, or " +
                    "live rating from a stale import.",
            resumeCue = "Continue discussion"
        )
    }

    /**
     * OC7 — finite relocation / lease-bound project: capture leave date, housing
     * requirements, and notice/mover posture, then pause with a waiting board.
     * Never invents viewings or assumes a property is secured. City names are
     * user facts — not shape hardcoding.
     */
    private fun finiteMoveProjectDecision(context: BuilderContext): BuilderDecision? {
        if (context.confirmedFacts.none { it.key == KEY_LEASE_DEADLINE }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-lease-${context.intentionId}",
                whyItMatters =
                    "Leave / lease end date shapes the critical path for search and notice — " +
                        "no move plan assumes a place is already secured.",
                answerSchema = AnswerSchema.DATE_TIME,
                purpose = KEY_LEASE_DEADLINE,
                blocksWhat = "move_project_board",
                priority = 90
            )
        }
        if (context.confirmedFacts.none { it.key == KEY_HOUSING_REQUIREMENTS }) {
            return BuilderDecision.Ask(
                intentionId = context.intentionId,
                expectedRevision = context.revision,
                questionId = "q-housing-${context.intentionId}",
                whyItMatters =
                    "Budget and location requirements shape search without inventing a secured place.",
                answerSchema = AnswerSchema.FREE_TEXT,
                purpose = KEY_HOUSING_REQUIREMENTS,
                blocksWhat = "move_project_board",
                priority = 88
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
                "Lease, housing, and notice facts saved. Project board keeps Next user action " +
                    "ready and Waiting on someone gated — no invented viewings or secured place.",
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
                val hint = when (draft.key) {
                    KEY_RESEARCH_RESUME -> "researching"
                    KEY_RESEARCH_STATUS, KEY_CHECKLIST_APPLICABILITY -> "reviewing"
                    else -> "discovery"
                }
                bumpState(store, decision.intentionId, nowEpochMs) { state ->
                    state.copy(
                        lastAnsweredTurnId = decision.sourceTurnId ?: state.lastAnsweredTurnId,
                        conversationCursorTurnId = decision.sourceTurnId
                            ?: state.conversationCursorTurnId,
                        lifecycleHint = hint
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
                    val parsed = ErrandCapture.parse(
                        decision.actionDraft.summary.ifBlank { decision.actionDraft.title }
                    )
                    val todoId = decision.actionDraft.actionId
                    store.apply(
                        FlowCommand.CaptureErrand(
                            todoId = todoId,
                            title = decision.actionDraft.title,
                            firstAction = parsed.firstAction,
                            reminderNote = decision.actionDraft.reminderNote
                                ?: parsed.reminderNote,
                            nowEpochMs = nowEpochMs,
                            checklistItems = ErrandCapture.checklistItemsFrom(
                                parsed.checklistLabels,
                                todoId
                            ),
                            reminderEnabled = false
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
                                    linkedObjectIds = listOf(decision.actionDraft.actionId),
                                    actions = when (decision.actionDraft.kind) {
                                        "explore_ideas" -> listOf(
                                            "Start Explore ideas",
                                            "Choose an idea to validate",
                                            "Keep exploring"
                                        )
                                        "validation_experiment" -> listOf(
                                            "Show experiment plan",
                                            "Keep exploring",
                                            "Not now"
                                        )
                                        else -> emptyList()
                                    }
                                )
                            )
                        )
                    )
                    // Ideas board shell — empty hypotheses, not invented ideas.
                    if (decision.actionDraft.kind == "explore_ideas") {
                        val already = store.snapshot().hypotheses.any {
                            it.intentionId == decision.intentionId &&
                                it.text.contains("ideas board", ignoreCase = true)
                        }
                        if (!already) {
                            store.apply(
                                FlowCommand.AddHypothesis(
                                    hypothesisId = "hyp-ideas-board-${decision.intentionId}",
                                    intentionId = decision.intentionId,
                                    text =
                                        "Ideas board open — capture sourced ideas here; " +
                                            "none chosen yet.",
                                    nowEpochMs = nowEpochMs
                                )
                            )
                        }
                    }
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
                val isConditionalChecklist =
                    decision.title.contains("Review plan", ignoreCase = true) ||
                        decision.title.contains("checklist", ignoreCase = true)
                val reviewText = if (isConditionalChecklist) {
                    "Here is a sourced checklist with applicability marked separately. " +
                        "Shall we Review plan together? Dated tasks stay off until you approve."
                } else {
                    "Ready to begin? Review your routine before activating it."
                }
                val reviewActions = if (isConditionalChecklist) {
                    listOf("Review plan", "Edit checklist", "Keep researching")
                } else {
                    listOf("Start this routine", "Edit plan", "Keep discussing")
                }
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-propose-${decision.proposalId}-$nowEpochMs",
                        intentionId = decision.intentionId,
                        role = "agent",
                        text = reviewText,
                        nowEpochMs = nowEpochMs,
                        blocks = listOf(
                            ResponseBlock(
                                blockId = "block-proposal-${decision.proposalId}",
                                type = ResponseBlockType.PROPOSAL,
                                title = decision.title,
                                body = summary,
                                sourceLabel = "adaptive_builder|review",
                                linkedObjectIds = listOf(decision.proposalId),
                                actions = reviewActions,
                                active = true
                            )
                        )
                    )
                )
                bumpState(store, decision.intentionId, nowEpochMs) { state ->
                    state.copy(
                        pendingProposalId = decision.proposalId,
                        lifecycleHint = if (isConditionalChecklist) "reviewing" else "proposing",
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

    /** Minutes from a self-reported timed activity or correction utterance, if any. */
    fun parseActivityDurationMinutes(utterance: String): Int? {
        val match = ACTIVITY_DURATION_REGEX.find(utterance.trim()) ?: return null
        val minutes = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
        return minutes.takeIf { it in 1..600 }
    }

    /**
     * True when free-text should SaveFact / CorrectFact a timed activity report
     * instead of falling through to live Discuss prose.
     */
    fun shouldHandleActivityReport(
        store: IntentionFlowStore,
        intentionId: String,
        utterance: String
    ): Boolean {
        val minutes = parseActivityDurationMinutes(utterance) ?: return false
        val hasActive = store.snapshot().facts.any {
            it.intentionId == intentionId &&
                it.key == KEY_ACTIVITY_REPORT &&
                it.status == FactStatus.ACTIVE
        }
        if (hasActive && ACTIVITY_CORRECTION_PREFIX.containsMatchIn(utterance)) return true
        if (hasActive && utterance.trim().matches(Regex("""(?i)^\s*(?:actually[, ]+)?\d{1,3}\s*(?:minutes?|mins?|min)?\.?\s*$"""))) {
            return true
        }
        // Fresh report: verb + duration (ran/walked/…) or explicit “for N minutes”.
        val lower = utterance.lowercase()
        val hasVerb = listOf(
            "ran", "run", "jog", "walk", "train", "cycle", "swam", "workout", "worked out"
        ).any { lower.contains(it) }
        return hasVerb && minutes > 0
    }

    /**
     * Record a self-reported timed activity, or [CorrectFact] the ACTIVE report
     * when the user corrects duration (e.g. 20 → 25 minutes). Generic across
     * physical-training / practice intentions — not marathon-title-specific.
     */
    fun recordOrCorrectActivityReport(
        store: IntentionFlowStore,
        intentionId: String,
        utterance: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        sourceTurnId: String? = null
    ): ApplyResult {
        val minutes = parseActivityDurationMinutes(utterance)
            ?: return ApplyResult(
                false,
                "No activity duration found to record.",
                "SaveFact",
                store.snapshot()
            )
        val text = "$minutes minutes"
        val rev = store.snapshot().adaptiveBuilderStates
            .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L
        val prior = store.snapshot().facts.firstOrNull {
            it.intentionId == intentionId &&
                it.key == KEY_ACTIVITY_REPORT &&
                it.status == FactStatus.ACTIVE
        }
        val turn = sourceTurnId ?: "turn-activity-$intentionId-$nowEpochMs"
        if (prior != null) {
            return applyDecision(
                store,
                BuilderDecision.CorrectFact(
                    intentionId = intentionId,
                    expectedRevision = rev,
                    existingFactId = prior.id,
                    replacement = FactDraft(
                        factId = "fact-activity-corrected-$intentionId-$nowEpochMs",
                        text = text,
                        key = KEY_ACTIVITY_REPORT,
                        rawUtterance = utterance.trim(),
                        normalizedValue = minutes.toString()
                    ),
                    sourceTurnId = turn
                ),
                nowEpochMs,
                timeZone = timeZone
            )
        }
        // Ensure adaptive state exists so Discuss can CorrectFact later.
        if (store.snapshot().adaptiveBuilderStates.none { it.intentionId == intentionId }) {
            val intention = store.snapshot().intentions.firstOrNull { it.id == intentionId }
            store.apply(
                FlowCommand.SetAdaptiveBuilderState(
                    state = AdaptiveBuilderState(
                        intentionId = intentionId,
                        revision = 0L,
                        typeFacets = facetsForShape(intention?.shapeTag ?: "other"),
                        lifecycleHint = "discovery",
                        updatedAtEpochMs = nowEpochMs
                    ),
                    nowEpochMs = nowEpochMs
                )
            )
        }
        val revAfter = store.snapshot().adaptiveBuilderStates
            .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L
        return applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = intentionId,
                expectedRevision = revAfter,
                factDraft = FactDraft(
                    factId = "fact-activity-$intentionId-$nowEpochMs",
                    text = text,
                    key = KEY_ACTIVITY_REPORT,
                    rawUtterance = utterance.trim(),
                    normalizedValue = minutes.toString()
                ),
                sourceTurnId = turn
            ),
            nowEpochMs,
            timeZone = timeZone
        )
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
        val answerHint = when (factKey) {
            KEY_RESEARCH_RESUME -> "researching"
            KEY_RESEARCH_STATUS, KEY_CHECKLIST_APPLICABILITY -> "reviewing"
            else -> null
        }
        bumpState(store, answer.intentionId, answer.answeredAtEpochMs) { state ->
            state.copy(
                currentQuestionId = null,
                lastAnsweredTurnId = answer.sourceTurnId,
                conversationCursorTurnId = answer.sourceTurnId,
                lifecycleHint = answerHint ?: state.lifecycleHint
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
