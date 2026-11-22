package com.intent.launcher.domain

/**
 * Adaptive intention builder domain types (worksheet §3 / §5).
 * Projections over canonical IntentionFlowStore records — not a second source of truth.
 */

enum class FactStatus {
    ACTIVE,
    CORRECTED,
    RETRACTED
}

enum class FactConfirmation {
    CONFIRMED,
    APPROXIMATE,
    UNCONFIRMED
}

enum class OpenQuestionState {
    OPEN,
    ASKED,
    ANSWERED,
    DECLINED,
    DEFERRED
}

enum class ApproachStatus {
    CANDIDATE,
    SELECTED,
    DECLINED
}

/** Who performs the granular work for an approach. */
enum class ApproachActor {
    INTENT,
    APP,
    PERSON,
    EXTERNAL
}

enum class AnswerSchema {
    FREE_TEXT,
    SINGLE_CHOICE,
    MULTI_CHOICE,
    NUMBER,
    DURATION,
    WEEKDAYS,
    DATE_TIME,
    CONFIRM
}

/**
 * Typed open question the builder may ask. Distinct from chat prose.
 */
data class OpenQuestion(
    val id: String,
    val intentionId: String,
    val purpose: String,
    val whyItMatters: String,
    val blocksWhat: String,
    val answerSchema: AnswerSchema = AnswerSchema.FREE_TEXT,
    val optionIds: List<String> = emptyList(),
    val state: OpenQuestionState = OpenQuestionState.OPEN,
    val priority: Int = 0,
    val askedAtEpochMs: Long? = null,
    val answeredAtEpochMs: Long? = null,
    val sourceTurnId: String? = null,
    val lastAskedEpochMs: Long? = null
)

/**
 * Candidate / selected / declined method for pursuing an intention.
 * Selecting an approach is not an approved recurring commitment.
 */
data class Approach(
    val id: String,
    val intentionId: String,
    val title: String,
    val summary: String,
    val status: ApproachStatus = ApproachStatus.CANDIDATE,
    val actor: ApproachActor = ApproachActor.INTENT,
    val packageName: String? = null,
    /** Honest capability note (launch vs time vs progress-read). */
    val capabilityNote: String = "",
    val sourceTurnId: String? = null,
    val selectedAtEpochMs: Long? = null
)

/**
 * Per-intention builder continuity cursor (worksheet §3.2).
 * Lives beside [BuilderDraft] questionnaire state — chat-first create uses this.
 */
data class AdaptiveBuilderState(
    val intentionId: String,
    val revision: Long = 0L,
    val typeFacets: List<String> = emptyList(),
    val currentQuestionId: String? = null,
    val pendingProposalId: String? = null,
    val conversationCursorTurnId: String? = null,
    val lastAnsweredTurnId: String? = null,
    /** discovery | proposing | paused */
    val lifecycleHint: String = "discovery",
    val policyVersion: String = POLICY_VERSION,
    val updatedAtEpochMs: Long = 0L
) {
    companion object {
        const val POLICY_VERSION: String = "adaptive-builder-v1"
    }
}

/**
 * Bounded, sourced context pack for a builder decision (worksheet §5).
 */
data class BuilderContext(
    val intentionId: String,
    val revision: Long,
    val lifecycleState: String,
    val typeFacets: List<String>,
    val confirmedFacts: List<Fact>,
    val hypotheses: List<Hypothesis>,
    val openQuestions: List<OpenQuestion>,
    val approaches: List<Approach>,
    val approvedCommitments: List<ScheduledSession>,
    val pendingProposals: List<Proposal>,
    val recentTurns: List<ConversationTurn>,
    val currentQuestionId: String?,
    val userTimeZoneId: String,
    val nowEpochMs: Long,
    val policyVersion: String = AdaptiveBuilderState.POLICY_VERSION
)

/**
 * Schema-constrained next move from the builder (worksheet §5).
 * Exactly one primary decision after each user turn.
 */
sealed class BuilderDecision {
    abstract val intentionId: String
    abstract val expectedRevision: Long
    abstract val sourceTurnId: String?

    data class SaveFact(
        override val intentionId: String,
        override val expectedRevision: Long,
        val factDraft: FactDraft,
        override val sourceTurnId: String?
    ) : BuilderDecision()

    data class CorrectFact(
        override val intentionId: String,
        override val expectedRevision: Long,
        val existingFactId: String,
        val replacement: FactDraft,
        override val sourceTurnId: String?
    ) : BuilderDecision()

    data class Ask(
        override val intentionId: String,
        override val expectedRevision: Long,
        val questionId: String,
        val whyItMatters: String,
        val answerSchema: AnswerSchema,
        val purpose: String = "",
        val blocksWhat: String = "",
        val options: List<String> = emptyList(),
        val priority: Int = 0,
        override val sourceTurnId: String? = null
    ) : BuilderDecision()

    data class OfferApproaches(
        override val intentionId: String,
        override val expectedRevision: Long,
        val approachDrafts: List<ApproachDraft>,
        override val sourceTurnId: String? = null
    ) : BuilderDecision()

    data class OfferAction(
        override val intentionId: String,
        override val expectedRevision: Long,
        val actionDraft: ActionDraft,
        override val sourceTurnId: String? = null
    ) : BuilderDecision()

    data class Propose(
        override val intentionId: String,
        override val expectedRevision: Long,
        val proposalId: String,
        val title: String,
        val summary: String,
        val dayOfWeek: Int,
        val hour: Int,
        val minute: Int,
        val basedOnFactIds: List<String> = emptyList(),
        val rationale: String = "",
        val effects: List<String> = emptyList(),
        /** ISO weekdays 1=Mon..7=Sun — Gate E multi-day drafts. */
        val daysOfWeek: List<Int> = emptyList(),
        val durationMinutes: Int = 20,
        val timeZoneId: String = "",
        val methodsSummary: String = "",
        val capabilityHonesty: String = "",
        val homeImpactNote: String = "",
        val includeMorningCheckIn: Boolean = false,
        val includeEveningReview: Boolean = false,
        override val sourceTurnId: String? = null
    ) : BuilderDecision()

    data class Pause(
        override val intentionId: String,
        override val expectedRevision: Long,
        val reason: String,
        val resumeCue: String? = null,
        override val sourceTurnId: String? = null
    ) : BuilderDecision()
}

data class FactDraft(
    val factId: String,
    val text: String,
    val key: String? = null,
    val rawUtterance: String? = null,
    val normalizedValue: String? = null,
    val timeZoneId: String? = null,
    val validFromEpochMs: Long? = null,
    val validUntilEpochMs: Long? = null,
    val estimatedEpochMs: Long? = null,
    val estimatedRangeStartEpochMs: Long? = null,
    val estimatedRangeEndEpochMs: Long? = null,
    val approximate: Boolean = false,
    val confirmation: FactConfirmation = FactConfirmation.CONFIRMED
)

data class ApproachDraft(
    val approachId: String,
    val title: String,
    val summary: String,
    val actor: ApproachActor = ApproachActor.INTENT,
    val packageName: String? = null,
    val capabilityNote: String = "",
    val selected: Boolean = false
)

data class ActionDraft(
    val actionId: String,
    val title: String,
    val summary: String,
    /** errand | first_step | reminder */
    val kind: String = "first_step",
    val reminderNote: String? = null
)

/**
 * Idempotent answer event for the active question (worksheet §3.4).
 * Voice and taps must resolve to the same command for the same question.
 */
data class AnswerQuestion(
    val questionId: String,
    val intentionId: String,
    val normalizedValue: String,
    val rawValue: String,
    val modality: String,
    val sourceTurnId: String,
    val answeredAtEpochMs: Long,
    val idempotencyKey: String,
    val expectedRevision: Long
)

/**
 * Gate F redacted builder decision event — why an Ask/Propose/Pause/selection
 * was applied (or rejected). IDs and version stamps only; never secrets,
 * private audio, or full chat text.
 */
data class BuilderDecisionEvent(
    val id: String,
    val atEpochMs: Long,
    val intentionId: String,
    /** Ask | Propose | Pause | SelectApproaches | AnswerQuestion | … */
    val decisionType: String,
    val accepted: Boolean,
    val reason: String = "",
    val activeQuestionId: String? = null,
    /** Fact IDs considered (source IDs) — not utterance text. */
    val factSourceIds: List<String> = emptyList(),
    val selectedApproachIds: List<String> = emptyList(),
    val rejectedApproachIds: List<String> = emptyList(),
    val capabilitySnapshotVersion: String = "",
    val policyVersion: String = AdaptiveBuilderState.POLICY_VERSION,
    val proposalId: String? = null,
    /** Short status codes only (e.g. offline_policy) — never tool payloads. */
    val toolResults: List<String> = emptyList(),
    val expectedRevision: Long = 0L,
    val resultingRevision: Long = 0L,
    val applyDurationMs: Long = 0L,
    val sourceTurnId: String? = null
) {
    companion object {
        /** Cap durable trail length (oldest dropped). */
        const val MAX_TRAIL_EVENTS: Int = 200
    }
}
