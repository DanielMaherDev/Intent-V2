package com.intent.launcher.domain

/** Schema version for safe migrations. Bump when wire format changes. */
const val SCHEMA_VERSION: Int = 2

enum class ProvenanceKind {
    SAID,
    INFERRED,
    OBSERVED
}

enum class IntentionPhase {
    EXPLORING,
    CLARIFYING,
    PROPOSAL_PENDING,
    ACTIVE,
    PAUSED
}

enum class ProposalStatus {
    PENDING,
    APPROVED,
    DECLINED,
    EDITED
}

/** Local session-run lifecycle — user-reported; not calendar/LLM verified. */
enum class SessionRunStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED
}

enum class NextActionKind {
    CONTINUE_EXPLORING,
    BEGIN,
    DEFER,
    DISCUSS,
    REVIEW_PROPOSAL,
    /** A session run is in progress — Complete / Abandon, not another Begin. */
    ACTIVE_SESSION,
    NONE
}

data class Provenance(
    val kind: ProvenanceKind,
    val learnedAtEpochMs: Long,
    val sourceLabel: String
)

data class Fact(
    val id: String,
    val intentionId: String,
    val text: String,
    val provenance: Provenance
)

data class Hypothesis(
    val id: String,
    val intentionId: String,
    val text: String,
    val provenance: Provenance
)

data class ScheduledSession(
    val id: String,
    val intentionId: String,
    val title: String,
    val dayOfWeek: Int, // 1=Monday … 7=Sunday (ISO)
    val hour: Int,
    val minute: Int,
    val deferredUntilEpochMs: Long? = null
)

/**
 * One started occurrence of a [ScheduledSession].
 * Begin creates an IN_PROGRESS run; Complete / Abandon end it.
 * Evidence is user-reported via the command path — not external calendar or LLM claims.
 */
data class SessionRun(
    val id: String,
    val sessionId: String,
    val intentionId: String,
    val title: String,
    val status: SessionRunStatus,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null
)

data class Proposal(
    val id: String,
    val intentionId: String,
    val title: String,
    val summary: String,
    val proposedDayOfWeek: Int,
    val proposedHour: Int,
    val proposedMinute: Int,
    val status: ProposalStatus,
    val basedOnFactIds: List<String>,
    val provenance: Provenance
)

data class ConversationTurn(
    val id: String,
    val intentionId: String,
    val role: String, // user | agent
    val text: String,
    val atEpochMs: Long
)

data class Intention(
    val id: String,
    val title: String,
    val statement: String,
    val phase: IntentionPhase,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val shapeTag: String // language | marathon | business | other
)

data class IntentionSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val intentions: List<Intention> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val hypotheses: List<Hypothesis> = emptyList(),
    val proposals: List<Proposal> = emptyList(),
    val sessions: List<ScheduledSession> = emptyList(),
    val sessionRuns: List<SessionRun> = emptyList(),
    val turns: List<ConversationTurn> = emptyList(),
    val activeIntentionId: String? = null
)

sealed class FlowCommand {
    data class SeedExploring(
        val intentionId: String,
        val title: String,
        val statement: String,
        val shapeTag: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class AddUserFact(
        val factId: String,
        val intentionId: String,
        val text: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class AddHypothesis(
        val hypothesisId: String,
        val intentionId: String,
        val text: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class AddConversationTurn(
        val turnId: String,
        val intentionId: String,
        val role: String,
        val text: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class ProposeRecurringSession(
        val proposalId: String,
        val intentionId: String,
        val title: String,
        val summary: String,
        val dayOfWeek: Int,
        val hour: Int,
        val minute: Int,
        val basedOnFactIds: List<String>,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class ApproveProposal(
        val proposalId: String,
        val sessionId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class DeclineProposal(
        val proposalId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class EditProposal(
        val proposalId: String,
        val dayOfWeek: Int,
        val hour: Int,
        val minute: Int,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class DeferNextSession(
        val sessionId: String,
        val deferUntilEpochMs: Long,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Start a scheduled session as an in-progress [SessionRun].
     * Requires [CapabilityRegistry] begin_session to be actionable at the call site.
     */
    data class BeginSession(
        val runId: String,
        val sessionId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class CompleteSessionRun(
        val runId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class AbandonSessionRun(
        val runId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class SetActiveIntention(
        val intentionId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class MarkPhase(
        val intentionId: String,
        val phase: IntentionPhase,
        val nowEpochMs: Long
    ) : FlowCommand()
}

data class NextAction(
    val kind: NextActionKind,
    val label: String,
    val detail: String,
    /** Explainable “why this now?” tied to schedule, state, or capability honesty. */
    val reason: String = "",
    val intentionId: String?,
    val sessionId: String? = null,
    val sessionRunId: String? = null,
    /** Wall-clock start of the active [SessionRun]; UI derives elapsed locally. */
    val startedAtEpochMs: Long? = null,
    val proposalId: String? = null,
    val beginLabel: String? = null,
    val deferLabel: String? = null,
    val discussLabel: String? = null,
    val completeLabel: String? = null,
    val abandonLabel: String? = null,
    /** Destination package when this shape declares one (e.g. Duolingo for Spanish). */
    val destinationPackageName: String? = null,
    val destinationAppLabel: String? = null,
    /** Open / launch control on Begin chrome or active SessionCard; null when none. */
    val openDestinationLabel: String? = null
)
