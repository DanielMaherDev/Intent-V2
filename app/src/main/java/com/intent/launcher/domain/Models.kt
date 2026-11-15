package com.intent.launcher.domain

/** Schema version for safe migrations. Bump when wire format changes. */
const val SCHEMA_VERSION: Int = 4

/** Soft-deleted entity kinds held in recoverable trash (tech-arch §9). */
enum class TrashKind {
    INTENTION,
    SESSION
}

enum class ProvenanceKind {
    SAID,
    INFERRED,
    OBSERVED
}

/** User correction of stored evidence without editing chat. */
enum class EvidenceAccuracy {
    ACTIVE,
    DISMISSED
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
 * Completion remains user-reported via the command path — not calendar/LLM/UsageStats claims.
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

/**
 * Device- or user-sourced evidence attached to an intention.
 * OBSERVED rows come from package foreground (UsageStats) — never lesson completion.
 */
data class Evidence(
    val id: String,
    val intentionId: String,
    val sessionRunId: String?,
    val packageName: String?,
    val appLabel: String?,
    val foregroundMs: Long?,
    val recordedAtEpochMs: Long,
    val summaryText: String,
    val provenance: Provenance,
    val accuracy: EvidenceAccuracy = EvidenceAccuracy.ACTIVE
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

/**
 * Instant raw thought — stored before any classification.
 * Provenance is always SAID; classification is deferred (evening / Conversation).
 */
enum class ThoughtCaptureStatus {
    RAW,
    PROMOTED,
    DISCARDED
}

data class ThoughtCapture(
    val id: String,
    /** Active or last intention when captured; null only if none exist. */
    val intentionId: String?,
    val text: String,
    val provenance: Provenance,
    val capturedAtEpochMs: Long,
    /** Entry surface: floater | home | voice */
    val source: String,
    val status: ThoughtCaptureStatus = ThoughtCaptureStatus.RAW
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

/**
 * One applied FlowCommand in the durable event/transaction log.
 * Undo restores [beforeJson] for the last reversible, non-undone entry.
 */
data class FlowTransaction(
    val id: String,
    val commandKind: String,
    val summary: String,
    val occurredAtEpochMs: Long,
    val reversible: Boolean,
    val irreversibleReason: String? = null,
    /** Encoded domain snapshot before this command (omit history/trash noise handled by store). */
    val beforeJson: String? = null,
    val undone: Boolean = false,
    val undoOfTransactionId: String? = null
)

/**
 * Soft-deleted intention or session (task) with recoverable child context.
 * [expiresAtEpochMs] null means keep until an explicit purge command.
 */
data class TrashEntry(
    val id: String,
    val kind: TrashKind,
    val entityId: String,
    val title: String,
    val deletedAtEpochMs: Long,
    val expiresAtEpochMs: Long? = null,
    val parentIntentionId: String? = null,
    /** Former parent / path label for restore UI (OS-077). */
    val pathLabel: String,
    val payloadJson: String
)

data class IntentionSnapshot(
    val schemaVersion: Int = SCHEMA_VERSION,
    val intentions: List<Intention> = emptyList(),
    val facts: List<Fact> = emptyList(),
    val hypotheses: List<Hypothesis> = emptyList(),
    val proposals: List<Proposal> = emptyList(),
    val sessions: List<ScheduledSession> = emptyList(),
    val sessionRuns: List<SessionRun> = emptyList(),
    val evidence: List<Evidence> = emptyList(),
    val turns: List<ConversationTurn> = emptyList(),
    val thoughtCaptures: List<ThoughtCapture> = emptyList(),
    val activeIntentionId: String? = null,
    val transactions: List<FlowTransaction> = emptyList(),
    val trash: List<TrashEntry> = emptyList()
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

    /**
     * Persist package-foreground observation as [ProvenanceKind.OBSERVED] evidence.
     * Does **not** complete or abandon the session run.
     */
    data class RecordObservedEvidence(
        val evidenceId: String,
        val intentionId: String,
        val sessionRunId: String?,
        val packageName: String?,
        val appLabel: String?,
        val foregroundMs: Long?,
        val summaryText: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /** Mark evidence inaccurate / dismissed without editing chat. */
    data class DismissEvidence(
        val evidenceId: String,
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

    /**
     * Persist a raw thought immediately (SAID). No LLM summary; classification later.
     */
    data class CaptureThought(
        val thoughtId: String,
        val intentionId: String?,
        val text: String,
        val source: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /** Undo the last reversible commitment via transaction history. */
    data class UndoLastReversible(
        val transactionId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Soft-delete an intention into recoverable trash (facts / proposals / sessions
     * preserved in the trash payload).
     */
    data class SoftDeleteIntention(
        val trashId: String,
        val intentionId: String,
        val nowEpochMs: Long,
        /** null = infinite retention until [PurgeTrashItem]. */
        val expiresAtEpochMs: Long? = null
    ) : FlowCommand()

    /** Soft-delete a scheduled session (task) into trash with parent path. */
    data class SoftDeleteSession(
        val trashId: String,
        val sessionId: String,
        val nowEpochMs: Long,
        val expiresAtEpochMs: Long? = null
    ) : FlowCommand()

    data class RestoreTrashItem(
        val trashId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /** Permanent delete from trash — not reversible. */
    data class PurgeTrashItem(
        val trashId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Honest log of an external side effect that INTENT cannot reverse
     * (e.g. opening another app). Does not mutate domain entities.
     */
    data class RecordExternalAction(
        val transactionId: String,
        val summary: String,
        val irreversibleReason: String,
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
