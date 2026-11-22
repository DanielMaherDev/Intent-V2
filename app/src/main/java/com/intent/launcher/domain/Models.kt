package com.intent.launcher.domain

/** Schema version for safe migrations. Bump when wire format changes. */
const val SCHEMA_VERSION: Int = 11

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

/** Soft-delete / correction for tracker rows until undo/trash purge is available. */
enum class TrackerEntryStatus {
    ACTIVE,
    DELETED,
    /** Superseded by a CorrectTrackerEntry — excluded from ACTIVE report counts. */
    CORRECTED
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
    /** Morning focus confirm / adjust (worksheet §10). */
    CHECK_IN,
    /** Midday drift / replan via DailyContext + Occurrence (Gate C). */
    MIDDAY,
    /** Evening SAID review + keep / defer (worksheet §10). */
    CHECK_OUT,
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
    val provenance: Provenance,
    /** Stable key for ranking / freshness (e.g. move_estimate, target_language). */
    val key: String? = null,
    val rawUtterance: String? = null,
    val normalizedValue: String? = null,
    val sourceTurnId: String? = null,
    val timeZoneId: String? = null,
    val validFromEpochMs: Long? = null,
    val validUntilEpochMs: Long? = null,
    /** Anchored absolute estimate for relative phrases like "in three months". */
    val estimatedEpochMs: Long? = null,
    val estimatedRangeStartEpochMs: Long? = null,
    val estimatedRangeEndEpochMs: Long? = null,
    val approximate: Boolean = false,
    val confirmation: FactConfirmation = FactConfirmation.CONFIRMED,
    val status: FactStatus = FactStatus.ACTIVE,
    /** Prior fact id when this row corrects an earlier estimate. */
    val supersedesFactId: String? = null,
    val revision: Int = 1
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
 *
 * Soft-pause ([pausedAtEpochMs]) freezes local elapsed when the user leaves the
 * affinity package — it never Completes or invents lesson progress.
 */
data class SessionRun(
    val id: String,
    val sessionId: String,
    val intentionId: String,
    val title: String,
    val status: SessionRunStatus,
    val startedAtEpochMs: Long,
    val endedAtEpochMs: Long? = null,
    /** Destination package this run is linked to (shape default or SAID). */
    val affinityPackageName: String? = null,
    val affinityAppLabel: String? = null,
    /** Non-null while soft-paused — elapsed freezes at this wall clock. */
    val pausedAtEpochMs: Long? = null,
    /** Prior soft-pause durations already excluded from elapsed. */
    val accumulatedPausedMs: Long = 0L
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

/**
 * Manual or observed tracker row for an intention module.
 * Lives in the same intention store — not a separate progress database.
 * Provenance must stay explicit — never silent mix or invented completion charts.
 * [eventAtEpochMs] is when the value applies (may be backdated); [recordedAtEpochMs]
 * is when INTENT accepted the write. Corrections mark the prior row CORRECTED and
 * add a new ACTIVE row with [supersedesEntryId] — reports count ACTIVE only.
 */
data class TrackerEntry(
    val id: String,
    val intentionId: String,
    val moduleId: String,
    val valueText: String,
    val recordedAtEpochMs: Long,
    val provenance: Provenance,
    val status: TrackerEntryStatus = TrackerEntryStatus.ACTIVE,
    val eventAtEpochMs: Long = recordedAtEpochMs,
    val supersedesEntryId: String? = null
)

/**
 * Durable day-loop occurrence (contracts §6 OccurrenceRecordV1).
 * Not [NextActionEngine.nextOccurrence] schedule math and not chat prose.
 * [phase]: morning | midday | evening · [kind]: check_in | midday_check | check_out | progress_note
 */
data class Occurrence(
    val id: String,
    val intentionId: String,
    val localDate: String,
    val phase: String,
    val kind: String,
    val summary: String,
    val recordedAtEpochMs: Long,
    val provenance: Provenance
)

/**
 * Time-bound day context for a local date — not a permanent profile fact.
 * Morning / midday / evening phases write separate rows alongside [Occurrence].
 */
data class DailyContext(
    val id: String,
    val localDate: String,
    val intentionId: String?,
    val phase: String,
    val availability: String? = null,
    val energy: String? = null,
    val priorities: String? = null,
    val constraints: String? = null,
    val note: String? = null,
    val recordedAtEpochMs: Long,
    val provenance: Provenance
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
    val provenance: Provenance,
    /** Gate E plan-compiler fields — additive Gson defaults (SCHEMA 10). */
    val proposedDaysOfWeek: List<Int> = emptyList(),
    val durationMinutes: Int = 60,
    val timeZoneId: String = "",
    val methodsSummary: String = "",
    val capabilityHonesty: String = "",
    val homeImpactNote: String = "",
    val includeMorningCheckIn: Boolean = false,
    val includeEveningReview: Boolean = false
)

/**
 * Typed response block (contracts §4 ResponseBlockV1).
 * Unknown [type] values are rejected at the write path.
 */
enum class ResponseBlockType {
    EXPLANATION,
    QUESTION,
    CHOICE_SET,
    RECORDED_ENTRY,
    PROPOSAL,
    NEXT_ACTION,
    REPORT_PREVIEW,
    CAPABILITY_GAP,
    PROGRESS,
    ERROR
}

data class ResponseBlock(
    val blockId: String,
    val type: ResponseBlockType,
    val title: String,
    val body: String,
    val sourceLabel: String,
    val linkedObjectIds: List<String> = emptyList(),
    val actions: List<String> = emptyList(),
    val active: Boolean = true,
    val expiresAtEpochMs: Long? = null
)

data class ConversationTurn(
    val id: String,
    val intentionId: String,
    val role: String, // user | agent
    val text: String,
    val atEpochMs: Long,
    /**
     * Input modality that produced this turn when known
     * (`TEXT_CHIP` | `VOICE_TRANSCRIPT` | `FREE_TEXT` | null for legacy/seed).
     */
    val modality: String? = null,
    /** Typed blocks (e.g. CAPABILITY_GAP) — empty for prose-only legacy turns. */
    val blocks: List<ResponseBlock> = emptyList()
)

/** User-controlled proactivity preferences (Gate D / worksheet §13). */
data class ProactivityPrefs(
    /** Local minutes from midnight; null = no quiet-hours window. */
    val quietHoursStartMin: Int? = null,
    val quietHoursEndMin: Int? = null,
    /** Max proactive interventions per local day (default 1). */
    val interruptionBudgetPerDay: Int = 1,
    /** Absolute mute until this epoch — “leave me alone”. */
    val leaveMeAloneUntilEpochMs: Long? = null
)

/** Cooldown after dismiss / leave-me-alone for a trigger key. */
data class TriggerCooldown(
    val key: String,
    val armedUntilEpochMs: Long,
    /** dismiss | leave_me_alone | budget | not_now */
    val reason: String
)

/** At-most-one queued proactive intervention (scenario C). */
data class QueuedIntervention(
    val jobId: String,
    val turnId: String,
    val intentionId: String,
    val triggerKey: String,
    val kind: String,
    val promptText: String,
    val evidenceId: String? = null,
    /** OBSERVED | INFERRED — explicit for D2 labeling; never silent mix. */
    val provenanceKind: ProvenanceKind = ProvenanceKind.OBSERVED,
    val createdAtEpochMs: Long,
    /** queued | shown | dismissed | answered */
    val status: String = "queued"
)

/**
 * Local day cycle on the same intention snapshot — not a separate daily-task store.
 * Dates are device-local `yyyy-MM-dd`. Check-in/out/midday are user-confirmed via commands.
 * Richer day fields live in [DailyContext] rows; this stays the Home clock marker.
 */
data class DayCycleState(
    val localDate: String = "",
    val checkInAtEpochMs: Long? = null,
    val checkOutAtEpochMs: Long? = null,
    val focusIntentionId: String? = null,
    val focusSummary: String? = null,
    val middayAtEpochMs: Long? = null,
    val middayNote: String? = null
) {
    fun checkedInFor(date: String): Boolean =
        localDate == date && checkInAtEpochMs != null

    fun checkedOutFor(date: String): Boolean =
        localDate == date && checkOutAtEpochMs != null

    fun middayDoneFor(date: String): Boolean =
        localDate == date && middayAtEpochMs != null
}

/**
 * Instant raw thought — stored before classification.
 * Provenance is always SAID; triage may promote or leave as evidence.
 */
enum class ThoughtCaptureStatus {
    RAW,
    PROMOTED,
    DISCARDED
}

/**
 * User-picked evening disposition (OS-154). Never LLM-invented — only set via
 * [FlowCommand.ClassifyThought] after explicit confirm.
 */
enum class ThoughtDisposition {
    TASK,
    INTENTION,
    REMINDER,
    KEEP,
    DISCARD
}

data class ThoughtCapture(
    val id: String,
    /** Active or last intention when captured; null only if none exist. */
    val intentionId: String?,
    val text: String,
    val provenance: Provenance,
    val capturedAtEpochMs: Long,
    /** Entry surface: floater | home | voice | triage */
    val source: String,
    val status: ThoughtCaptureStatus = ThoughtCaptureStatus.RAW,
    /** Set only after user-picked evening classification (SAID). */
    val disposition: ThoughtDisposition? = null
)

/** Guided intention builder draft — persisted so the flow can resume. */
enum class BuilderPhase {
    ENTER_TEXT,
    PICK_SHAPE,
    MISSING_FACTS,
    CONFIRM
}

data class BuilderDraft(
    val active: Boolean = false,
    val freeText: String = "",
    val suggestedShapeTag: String = "other",
    val shapeTag: String? = null,
    val title: String = "",
    val statement: String = "",
    /** promptId → user answer (written as SAID facts only on commit). */
    val factAnswers: Map<String, String> = emptyMap(),
    val phase: BuilderPhase = BuilderPhase.ENTER_TEXT,
    val honestyNote: String = "Shape chosen from your description."
)

enum class TriageItemStatus {
    PENDING,
    CONFIRMED,
    REJECTED
}

/** One proposed intention from multi-thought triage (heuristic unless LLM lands later). */
data class TriageItem(
    val id: String,
    val title: String,
    val statement: String,
    val suggestedShapeTag: String,
    val suggestedNextAction: String,
    val sourceThoughtIds: List<String>,
    val status: TriageItemStatus = TriageItemStatus.PENDING,
    val honestyNote: String = "On-device split — not a live model."
)

data class TriageState(
    val active: Boolean = false,
    val batchId: String = "",
    val items: List<TriageItem> = emptyList(),
    val createdAtEpochMs: Long = 0L
)

data class Intention(
    val id: String,
    val title: String,
    val statement: String,
    val phase: IntentionPhase,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val shapeTag: String, // language | marathon | business | move | decision | cessation | app_use | research | chess | other
    /**
     * Optional parent intention id (OS-141). Local pointer only —
     * not a dependency / task graph. Null = root intention.
     */
    val parentIntentionId: String? = null
)


/** Lightweight errand / quick to-do — not an Intention (OS-073 / CASE-01). */
enum class ErrandStatus {
    OPEN,
    DONE
}

/**
 * Separate inbox item for small errands (e.g. “Buy milk tonight”).
 * Lives beside the intention model; never seeds EXPLORING.
 */
data class ErrandTodo(
    val id: String,
    val title: String,
    /** Concrete first step, often the verb phrase (e.g. “Buy milk”). */
    val firstAction: String,
    /** Human reminder / due hint (e.g. “Tonight”). */
    val reminderNote: String?,
    val status: ErrandStatus,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long? = null,
    val provenance: Provenance
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


/**
 * User-confirmed app affinity for an intention (SAID).
 * Not UsageStats auto-ranking; OBSERVED evidence stays separate.
 */
data class HeldApp(
    val id: String,
    val intentionId: String,
    val packageName: String,
    val label: String,
    val sortOrder: Int,
    val provenance: Provenance
)

/**
 * Drawer card chrome for one intention (OS-139).
 * [spanColumns] is 1 (half / two-column cell) or 2 (full width).
 * [priorityBorder] is user SAID chrome — not UsageStats ranking.
 * [imageKey] selects a local shape cover; never a remote/mini-app asset.
 */
data class DrawerCardLayout(
    val intentionId: String,
    val spanColumns: Int = 1,
    val priorityBorder: Boolean = false,
    val imageKey: String = "shape"
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
    val trackerEntries: List<TrackerEntry> = emptyList(),
    /** Durable dated session instances (Gate C) — independent of recurrence template. */
    val occurrences: List<Occurrence> = emptyList(),
    /** Morning / midday / evening context rows per local date (Gate C). */
    val dailyContexts: List<DailyContext> = emptyList(),
    val turns: List<ConversationTurn> = emptyList(),
    val thoughtCaptures: List<ThoughtCapture> = emptyList(),
    val builderDraft: BuilderDraft = BuilderDraft(),
    val triage: TriageState = TriageState(),
    /** Lightweight errands — OS-073 inbox, not intentions. */
    val errandTodos: List<ErrandTodo> = emptyList(),
    /** Adaptive builder open questions (worksheet Gate B). */
    val openQuestions: List<OpenQuestion> = emptyList(),
    /** Adaptive builder approaches (worksheet Gate C). */
    val approaches: List<Approach> = emptyList(),
    /** Per-intention adaptive builder cursors. */
    val adaptiveBuilderStates: List<AdaptiveBuilderState> = emptyList(),
    val activeIntentionId: String? = null,
    val dayCycle: DayCycleState = DayCycleState(),
    val transactions: List<FlowTransaction> = emptyList(),
    val trash: List<TrashEntry> = emptyList(),
    /** Drawer display order (intention ids). Missing ids append after. */
    val intentionOrder: List<String> = emptyList(),
    /** User-confirmed hold-for-apps bindings (SAID). */
    val heldApps: List<HeldApp> = emptyList(),
    /** Per-intention drawer card span / priority / cover key (OS-139). */
    val drawerCardLayouts: List<DrawerCardLayout> = emptyList(),
    /** Quiet hours / interruption budget / leave-me-alone (Gate D). */
    val proactivityPrefs: ProactivityPrefs = ProactivityPrefs(),
    /** Active trigger cooldowns keyed by trigger key. */
    val triggerCooldowns: List<TriggerCooldown> = emptyList(),
    /**
     * Queued proactive interventions — at most one active (queued/shown) at a time.
     * Floater and Conversation share [QueuedIntervention.turnId] / [QueuedIntervention.jobId].
     */
    val queuedInterventions: List<QueuedIntervention> = emptyList(),
    /**
     * Gate F redacted builder decision trail (Ask/Propose/Pause/SelectApproaches/…).
     * IDs and versions only — no chat dump, secrets, or private audio.
     */
    val decisionTrail: List<BuilderDecisionEvent> = emptyList()
)

sealed class FlowCommand {
    data class SeedExploring(
        val intentionId: String,
        val title: String,
        val statement: String,
        val shapeTag: String,
        val nowEpochMs: Long,
        /** Optional local parent pointer (OS-141). Null = root. */
        val parentIntentionId: String? = null
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
        val nowEpochMs: Long,
        /** Optional modality tag — see [ConversationTurn.modality]. */
        val modality: String? = null,
        /** Typed response blocks (contracts §4); empty for prose-only. */
        val blocks: List<ResponseBlock> = emptyList()
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
        val nowEpochMs: Long,
        val daysOfWeek: List<Int> = emptyList(),
        val durationMinutes: Int = 60,
        val timeZoneId: String = "",
        val methodsSummary: String = "",
        val capabilityHonesty: String = "",
        val homeImpactNote: String = "",
        val includeMorningCheckIn: Boolean = false,
        val includeEveningReview: Boolean = false
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
     * Optional affinity links the run to a destination package for away-pause.
     */
    data class BeginSession(
        val runId: String,
        val sessionId: String,
        val nowEpochMs: Long,
        val affinityPackageName: String? = null,
        val affinityAppLabel: String? = null
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

    /**
     * Manual tracker data entry — stores [ProvenanceKind.SAID] by default.
     * Callers may pass OBSERVED when logging package-foreground honestly.
     * [eventAtEpochMs] backdates when the value applies; omit to use [nowEpochMs].
     */
    data class RecordTrackerEntry(
        val entryId: String,
        val intentionId: String,
        val moduleId: String,
        val valueText: String,
        val nowEpochMs: Long,
        val provenanceKind: ProvenanceKind = ProvenanceKind.SAID,
        val sourceLabel: String = "user",
        val eventAtEpochMs: Long? = null
    ) : FlowCommand()

    /**
     * Correct or backdate a tracker value (contracts §6 CorrectTrackerEntryPayloadV1):
     * marks [priorEntryId] CORRECTED and inserts ACTIVE [newEntryId].
     * Reports / next actions count ACTIVE only — no duplicate.
     */
    data class CorrectTrackerEntry(
        val priorEntryId: String,
        val newEntryId: String,
        val valueText: String,
        val nowEpochMs: Long,
        val eventTimeEpochMs: Long? = null,
        val provenanceKind: ProvenanceKind = ProvenanceKind.SAID,
        val sourceLabel: String = "user_correction"
    ) : FlowCommand()

    /** Soft-delete a tracker row (command path; restore via undo when available). */
    data class DeleteTrackerEntry(
        val entryId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Soft-pause an in-progress run (away from affinity). Does not Complete.
     */
    data class SoftPauseSessionRun(
        val runId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Resume a soft-paused run — unfreezes elapsed. Does not Complete.
     */
    data class ResumeSessionRun(
        val runId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class SetActiveIntention(
        val intentionId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Set or clear the local parent pointer for an intention (OS-141).
     * Refuses cycles and unknown parent ids — never invents a graph.
     */
    data class SetParentIntention(
        val intentionId: String,
        val parentIntentionId: String?,
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

    data class ConfirmDailyCheckIn(
        val intentionId: String,
        val localDate: String,
        val focusSummary: String,
        val nowEpochMs: Long,
        val adjustedFocusNote: String? = null,
        val adjustedFactId: String? = null,
        val adjustedHypothesisId: String? = null,
        /** Durable morning Occurrence (contracts §6) — required for Gate C day loop. */
        val occurrenceId: String? = null,
        /** DailyContext row id for morning phase. */
        val dailyContextId: String? = null,
        val availability: String? = null,
        val energy: String? = null,
        val priorities: String? = null,
        val constraints: String? = null
    ) : FlowCommand()

    /**
     * Midday drift / replan: writes Occurrence (midday_check) + DailyContext.
     * Optional session deferral refuses when a SessionRun is IN_PROGRESS (conflict).
     * Not chat prose alone.
     */
    data class RecordMiddayCheck(
        val occurrenceId: String,
        val contextId: String,
        val intentionId: String,
        val localDate: String,
        val summary: String,
        val nowEpochMs: Long,
        val energy: String? = null,
        val constraints: String? = null,
        val deferSessionId: String? = null,
        val deferUntilEpochMs: Long? = null
    ) : FlowCommand()

    /**
     * Evening check-out: record user-reported [whatHappened] as SAID, optional SAID
     * hypothesis, mark day complete, and optionally defer the scheduled session.
     * Also writes evening [Occurrence] + [DailyContext].
     */
    data class CompleteDailyCheckOut(
        val intentionId: String,
        val localDate: String,
        val whatHappened: String,
        val factId: String,
        val nowEpochMs: Long,
        val hypothesisId: String? = null,
        val hypothesisText: String? = null,
        val deferSessionId: String? = null,
        val deferUntilEpochMs: Long? = null,
        val occurrenceId: String? = null,
        val dailyContextId: String? = null,
        val energy: String? = null,
        val constraints: String? = null
    ) : FlowCommand()

    data class MarkThoughtStatus(
        val thoughtId: String,
        val status: ThoughtCaptureStatus,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Evening thought triage (OS-154) — user-picked disposition only.
     * No LLM invent; [disposition] must come from an explicit confirm.
     */
    data class ClassifyThought(
        val thoughtId: String,
        val disposition: ThoughtDisposition,
        val nowEpochMs: Long,
        /** Seeded intention when disposition is INTENTION (or TASK with no parent). */
        val newIntentionId: String? = null,
        /** SAID fact for TASK / REMINDER / INTENTION seed. */
        val factId: String? = null,
        /** Lightweight session stub when disposition is TASK. */
        val sessionId: String? = null,
        /** ISO day-of-week (1=Mon…7=Sun) for TASK session stub. */
        val taskDayOfWeek: Int? = null,
        val taskHour: Int? = null,
        val taskMinute: Int? = null
    ) : FlowCommand()

    data class StartBuilderDraft(
        val freeText: String,
        val suggestedShapeTag: String,
        val title: String,
        val statement: String,
        val nowEpochMs: Long,
        val honestyNote: String = "Shape chosen from your description.",
        /** When true, agent applies [suggestedShapeTag] and skips forced PICK_SHAPE. */
        val autoApplyShape: Boolean = true
    ) : FlowCommand()

    data class SetBuilderShape(
        val shapeTag: String,
        val title: String,
        val statement: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class AnswerBuilderPrompt(
        val promptId: String,
        val answerText: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Commit builder draft → [SeedExploring] facts as SAID. Never auto-writes INFERRED.
     */
    data class CommitBuilderIntention(
        val intentionId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class CancelBuilderDraft(
        val nowEpochMs: Long
    ) : FlowCommand()

    data class StartTriageBatch(
        val batchId: String,
        val items: List<TriageItem>,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class ConfirmTriageItem(
        val itemId: String,
        val intentionId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class RejectTriageItem(
        val itemId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class ClearTriageBatch(
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


    /**
     * Persist intention drawer order. Reversible when undo is LIVE.
     */
    data class ReorderIntentions(
        val orderedIntentionIds: List<String>,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * User-confirmed SAID hold of a package for an intention (not UsageStats ranking).
     */
    data class HoldAppForIntention(
        val heldAppId: String,
        val intentionId: String,
        val packageName: String,
        val label: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class ReorderHeldApps(
        val intentionId: String,
        val orderedHeldAppIds: List<String>,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class ReleaseHeldApp(
        val heldAppId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Capture a lightweight errand / quick to-do (OS-073).
     * Does not create or activate an Intention.
     */
    data class CaptureErrand(
        val todoId: String,
        val title: String,
        val firstAction: String,
        val reminderNote: String?,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class CompleteErrand(
        val todoId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /** Reversible completion (OS-075 light) — tap again / undo Done. */
    data class ReopenErrand(
        val todoId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Persist drawer card chrome (span / priority border / cover key). Reversible when undo LIVE.
     */
    data class UpdateDrawerCardLayout(
        val intentionId: String,
        val spanColumns: Int,
        val priorityBorder: Boolean,
        val imageKey: String = "shape",
        val nowEpochMs: Long
    ) : FlowCommand()

    /** Set quiet hours / interruption budget (Gate D proactivity). */
    data class SetProactivityPrefs(
        val quietHoursStartMin: Int?,
        val quietHoursEndMin: Int?,
        val interruptionBudgetPerDay: Int = 1,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Arm a trigger cooldown so identical prompts stay suppressed until [armedUntilEpochMs].
     * Used after dismiss / leave-me-alone / budget exhaustion.
     */
    data class ArmTriggerCooldown(
        val key: String,
        val armedUntilEpochMs: Long,
        val reason: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * “Leave me alone” — durable mute until [untilEpochMs] plus optional trigger cooldown.
     */
    data class LeaveMeAlone(
        val untilEpochMs: Long,
        val triggerKey: String? = null,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Queue at most one proactive intervention. Idempotent when an active queued/shown
     * intervention already exists for [triggerKey] (or any key when budget is 1/day).
     */
    data class QueueIntervention(
        val jobId: String,
        val turnId: String,
        val intentionId: String,
        val triggerKey: String,
        val kind: String,
        val promptText: String,
        val nowEpochMs: Long,
        val evidenceId: String? = null,
        val provenanceKind: ProvenanceKind = ProvenanceKind.OBSERVED,
        /** Optional agent turn text + blocks written with the same [turnId]. */
        val agentTurnText: String? = null,
        val agentTurnBlocks: List<ResponseBlock> = emptyList()
    ) : FlowCommand()

    /** Mark a queued intervention as shown (floater / Conversation opened it). */
    data class MarkInterventionShown(
        val jobId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Dismiss a queued intervention and arm cooldown for its trigger key.
     * Does not secretly change schedules.
     */
    data class DismissIntervention(
        val jobId: String,
        val cooldownUntilEpochMs: Long,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Persist a typed, source-linked fact (adaptive builder Gate B).
     * Prefer over [AddUserFact] when key / estimate / turn linkage is known.
     */
    data class SaveTypedFact(
        val factId: String,
        val intentionId: String,
        val text: String,
        val nowEpochMs: Long,
        val key: String? = null,
        val rawUtterance: String? = null,
        val normalizedValue: String? = null,
        val sourceTurnId: String? = null,
        val timeZoneId: String? = null,
        val validFromEpochMs: Long? = null,
        val validUntilEpochMs: Long? = null,
        val estimatedEpochMs: Long? = null,
        val estimatedRangeStartEpochMs: Long? = null,
        val estimatedRangeEndEpochMs: Long? = null,
        val approximate: Boolean = false,
        val confirmation: FactConfirmation = FactConfirmation.CONFIRMED,
        val supersedesFactId: String? = null,
        val revision: Int = 1
    ) : FlowCommand()

    /**
     * Correct an existing fact: mark prior CORRECTED and write a replacement ACTIVE row.
     */
    data class CorrectTypedFact(
        val priorFactId: String,
        val newFactId: String,
        val intentionId: String,
        val text: String,
        val nowEpochMs: Long,
        val key: String? = null,
        val rawUtterance: String? = null,
        val normalizedValue: String? = null,
        val sourceTurnId: String? = null,
        val timeZoneId: String? = null,
        val validFromEpochMs: Long? = null,
        val validUntilEpochMs: Long? = null,
        val estimatedEpochMs: Long? = null,
        val estimatedRangeStartEpochMs: Long? = null,
        val estimatedRangeEndEpochMs: Long? = null,
        val approximate: Boolean = false,
        val confirmation: FactConfirmation = FactConfirmation.CONFIRMED
    ) : FlowCommand()

    data class UpsertOpenQuestion(
        val question: OpenQuestion,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class AnswerOpenQuestion(
        val questionId: String,
        val intentionId: String,
        val answerText: String,
        val nowEpochMs: Long,
        val sourceTurnId: String,
        val modality: String,
        val idempotencyKey: String,
        val factId: String? = null
    ) : FlowCommand()

    data class UpsertApproach(
        val approach: Approach,
        val nowEpochMs: Long
    ) : FlowCommand()

    data class SelectApproaches(
        val intentionId: String,
        val approachIds: List<String>,
        val nowEpochMs: Long,
        val sourceTurnId: String? = null
    ) : FlowCommand()

    data class SetAdaptiveBuilderState(
        val state: AdaptiveBuilderState,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Promote create-session shell records onto a durable intention id
     * (Gate D AVD: avoid Spanish transcript residue on the next create).
     */
    data class RemapIntentionId(
        val fromIntentionId: String,
        val toIntentionId: String,
        val nowEpochMs: Long
    ) : FlowCommand()

    /**
     * Mark prior adaptive-builder answer cards inactive so only the latest
     * card accepts input (stale NumberWheel / chips fix).
     */
    data class DeactivateBuilderAnswerCards(
        val intentionId: String,
        val nowEpochMs: Long,
        val exceptBlockId: String? = null
    ) : FlowCommand()

    /**
     * Append a Gate F [BuilderDecisionEvent] to the durable decision trail.
     * Not a user Undo target — audit hygiene only.
     */
    data class AppendDecisionEvent(
        val event: BuilderDecisionEvent,
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
    val confirmLabel: String? = null,
    val adjustLabel: String? = null,
    val keepLabel: String? = null,
    val deferTomorrowLabel: String? = null,
    /** Destination package when this shape declares one (e.g. Duolingo for Spanish). */
    val destinationPackageName: String? = null,
    val destinationAppLabel: String? = null,
    /** Open / launch control on Begin chrome or active SessionCard; null when none. */
    val openDestinationLabel: String? = null
)
