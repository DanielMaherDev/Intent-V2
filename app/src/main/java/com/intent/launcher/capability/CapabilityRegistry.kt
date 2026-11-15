package com.intent.launcher.capability

/**
 * Architecture Rule 5 / OS-049–050: single honest registry of what INTENT can do.
 * Suggestions (chips, proposals, next actions) must consult this registry — never
 * invent unavailable capabilities (e.g. lesson completion without a partner source).
 */
enum class CapabilityStatus {
    AVAILABLE,
    PERMISSION_NEEDED,
    CONNECTED,
    STALE,
    TEMPORARILY_UNAVAILABLE,
    NOT_SUPPORTED
}

data class CapabilityEntry(
    val id: String,
    val title: String,
    val status: CapabilityStatus,
    /** Honest product limit shown to agent / tests. */
    val limitNote: String,
    val requiresUserApproval: Boolean = false
)

/** A conversation chip gated by a capability id. */
data class SuggestedChip(
    val label: String,
    val requiredCapabilityId: String
)

class CapabilityRegistry(
    entries: List<CapabilityEntry> = DEFAULT_ENTRIES
) {
    private val byId: Map<String, CapabilityEntry> = entries.associateBy { it.id }

    fun all(): List<CapabilityEntry> = byId.values.toList()

    fun get(id: String): CapabilityEntry? = byId[id]

    /** True when the agent may offer this capability as a working action. */
    fun isActionable(id: String): Boolean {
        val entry = byId[id] ?: return false
        return entry.status == CapabilityStatus.AVAILABLE ||
            entry.status == CapabilityStatus.CONNECTED
    }

    /** Permission-needed caps may be suggested only as "request access", not as working actions. */
    fun needsPermission(id: String): Boolean =
        byId[id]?.status == CapabilityStatus.PERMISSION_NEEDED

    fun isUnsupported(id: String): Boolean =
        byId[id]?.status == CapabilityStatus.NOT_SUPPORTED || byId[id] == null

    fun filterChips(chips: List<SuggestedChip>): List<String> =
        chips.filter { isActionable(it.requiredCapabilityId) }.map { it.label }

    fun canProposeRecurringSession(): Boolean =
        isActionable(ID_PROPOSE_RECURRING_SESSION)

    fun canRecommendBegin(): Boolean =
        isActionable(ID_BEGIN_SESSION)

    fun canRecommendDefer(): Boolean =
        isActionable(ID_DEFER_SESSION)

    fun canCompleteSession(): Boolean =
        isActionable(ID_COMPLETE_SESSION)

    fun canAbandonSession(): Boolean =
        isActionable(ID_ABANDON_SESSION)

    fun canRecommendReviewProposal(): Boolean =
        isActionable(ID_PROPOSE_RECURRING_SESSION)

    fun canContinueExploring(): Boolean =
        isActionable(ID_CONTINUE_EXPLORING)

    fun canDiscuss(): Boolean =
        isActionable(ID_DISCUSS)

    fun canLaunchInstalledApp(): Boolean =
        isActionable(ID_LAUNCH_INSTALLED_APP)

    /** True when UsageStats permission is granted and package-level reads are allowed. */
    fun canReadUsageStats(): Boolean =
        isActionable(ID_USAGE_STATS)

    /**
     * True when package foreground may be stored as OBSERVED evidence.
     * Never implies lesson completion ([ID_OBSERVE_LESSON_COMPLETION] stays unsupported).
     */
    fun canObserveAppUsage(): Boolean =
        isActionable(ID_OBSERVE_APP_USAGE)

    /**
     * True when overlay permission is granted.
     * Does not mean a floating agent is shipped — check limit notes.
     */
    fun canUseFloatingOverlay(): Boolean =
        isActionable(ID_FLOATING_OVERLAY)

    /** Mic + platform STT available for voice turns (same ConversationCoordinator as chips). */
    fun canUseVoiceInput(): Boolean =
        isActionable(ID_VOICE_INPUT)

    /**
     * True when NotificationListenerService access is granted.
     * Does not mean quiet mode, content inbox, or lesson completion.
     */
    fun canReadNotificationSummaries(): Boolean =
        isActionable(ID_NOTIFICATION_LISTENER)

    /**
     * True when MediaProjection consent was granted this process.
     * Does not mean OCR, lesson completion, or frame retention.
     */
    fun canUseScreenCapture(): Boolean =
        isActionable(ID_SCREEN_CAPTURE)

    fun canUndoLast(): Boolean =
        isActionable(ID_UNDO_LAST)

    fun canSoftDelete(): Boolean =
        isActionable(ID_TRASH_SOFT_DELETE)

    fun canRestoreTrash(): Boolean =
        isActionable(ID_TRASH_RESTORE)

    /** External side effects (app launch, calendar write-back) are not undoable. */
    fun canUndoExternalAction(): Boolean =
        isActionable(ID_EXTERNAL_ACTION_UNDO)

    /**
     * True only when quiet_mode is actionable.
     * Default build keeps it [CapabilityStatus.NOT_SUPPORTED] — no fake silence.
     */
    fun canApplyQuietMode(): Boolean =
        isActionable(ID_QUIET_MODE)

    fun canDailyCheckIn(): Boolean =
        isActionable(ID_DAILY_CHECK_IN)

    fun canDailyCheckOut(): Boolean =
        isActionable(ID_DAILY_CHECK_OUT)

    fun canUseTrackerModules(): Boolean =
        isActionable(ID_TRACKER_MODULES)

    fun canViewProgressReports(): Boolean =
        isActionable(ID_PROGRESS_REPORTS)

    /** Package-count review for the session window (no titles/bodies). */
    fun canReviewSessionNotifications(): Boolean =
        isActionable(ID_SESSION_NOTIFICATION_REVIEW)

    fun canCaptureThought(): Boolean =
        isActionable(ID_CAPTURE_THOUGHT)

    fun canBuildIntention(): Boolean =
        isActionable(ID_BUILD_INTENTION)

    fun canTriageThoughts(): Boolean =
        isActionable(ID_TRIAGE_THOUGHTS)

    /** Spotify partner OAuth / playback — not shipped. */
    fun canUsePartnerSpotify(): Boolean =
        isActionable(ID_PARTNER_SPOTIFY)

    /** Chess.com partner ratings / games — not shipped. */
    fun canUsePartnerChess(): Boolean =
        isActionable(ID_PARTNER_CHESS)

    /** Strava activity sync — not shipped. */
    fun canUsePartnerStrava(): Boolean =
        isActionable(ID_PARTNER_STRAVA)

    companion object {
        const val ID_CONTINUE_EXPLORING = "continue_exploring"
        const val ID_RECORD_FACT = "record_user_fact"
        const val ID_ADD_HYPOTHESIS = "add_hypothesis"
        const val ID_PROPOSE_RECURRING_SESSION = "propose_recurring_session"
        const val ID_SCHEDULE_SESSION = "schedule_session"
        const val ID_BEGIN_SESSION = "begin_session"
        const val ID_COMPLETE_SESSION = "complete_session"
        const val ID_ABANDON_SESSION = "abandon_session"
        const val ID_DEFER_SESSION = "defer_session"
        const val ID_DISCUSS = "discuss"
        const val ID_LAUNCH_INSTALLED_APP = "launch_installed_app"
        const val ID_OBSERVE_LESSON_COMPLETION = "observe_lesson_completion"
        const val ID_OBSERVE_APP_USAGE = "observe_app_usage"
        const val ID_USAGE_STATS = "usage_stats"
        const val ID_EXTERNAL_CALENDAR = "external_calendar_sync"
        const val ID_FLOATING_OVERLAY = "floating_overlay"
        const val ID_VOICE_INPUT = "voice_input"
        const val ID_NOTIFICATION_LISTENER = "notification_listener"
        const val ID_SESSION_NOTIFICATION_REVIEW = "session_notification_review"
        const val ID_QUIET_MODE = "quiet_mode"
        const val ID_SCREEN_CAPTURE = "screen_capture"
        const val ID_UNDO_LAST = "undo_last"
        const val ID_TRASH_SOFT_DELETE = "trash_soft_delete"
        const val ID_TRASH_RESTORE = "trash_restore"
        const val ID_EXTERNAL_ACTION_UNDO = "external_action_undo"
        const val ID_DAILY_CHECK_IN = "daily_check_in"
        const val ID_DAILY_CHECK_OUT = "daily_check_out"
        const val ID_TRACKER_MODULES = "tracker_modules"
        const val ID_PROGRESS_REPORTS = "progress_reports"
        const val ID_CAPTURE_THOUGHT = "capture_thought"
        const val ID_BUILD_INTENTION = "build_intention"
        const val ID_TRIAGE_THOUGHTS = "triage_thoughts"
        const val ID_PARTNER_SPOTIFY = "partner_spotify"
        const val ID_PARTNER_CHESS = "partner_chess"
        const val ID_PARTNER_STRAVA = "partner_strava"

        val DEFAULT_ENTRIES: List<CapabilityEntry> = listOf(
            CapabilityEntry(
                id = ID_CONTINUE_EXPLORING,
                title = "Continue exploring",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Discovery without forcing tasks."
            ),
            CapabilityEntry(
                id = ID_RECORD_FACT,
                title = "Record user fact",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Stores what the user said with SAID provenance."
            ),
            CapabilityEntry(
                id = ID_ADD_HYPOTHESIS,
                title = "Add hypothesis",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Inferred focus; not treated as a confirmed commitment."
            ),
            CapabilityEntry(
                id = ID_PROPOSE_RECURRING_SESSION,
                title = "Propose recurring session",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Proposal only; requires explicit approve before scheduling.",
                requiresUserApproval = true
            ),
            CapabilityEntry(
                id = ID_SCHEDULE_SESSION,
                title = "Schedule session",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Creates a local scheduled session after approval — not an external calendar sync.",
                requiresUserApproval = true
            ),
            CapabilityEntry(
                id = ID_BEGIN_SESSION,
                title = "Begin session",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Starts a local in-progress session run from the schedule — not an external calendar event or LLM-verified start."
            ),
            CapabilityEntry(
                id = ID_COMPLETE_SESSION,
                title = "Complete session",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Marks the active run complete as user-reported; does not verify app progress or lesson completion."
            ),
            CapabilityEntry(
                id = ID_ABANDON_SESSION,
                title = "Abandon session",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Ends the active run without claiming completion."
            ),
            CapabilityEntry(
                id = ID_DEFER_SESSION,
                title = "Defer session",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Local deferral of the next occurrence."
            ),
            CapabilityEntry(
                id = ID_DISCUSS,
                title = "Discuss",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Opens the shared conversation surface."
            ),
            CapabilityEntry(
                id = ID_LAUNCH_INSTALLED_APP,
                title = "Open installed app",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Can open an app or deep link; cannot read arbitrary in-app progress."
            ),
            CapabilityEntry(
                id = ID_OBSERVE_LESSON_COMPLETION,
                title = "Observe lesson completion",
                status = CapabilityStatus.NOT_SUPPORTED,
                limitNote = "Opening a learning app is observable; lesson completion is not unless a reliable source provides it."
            ),
            CapabilityEntry(
                id = ID_OBSERVE_APP_USAGE,
                title = "Observe app usage",
                status = CapabilityStatus.PERMISSION_NEEDED,
                limitNote = "Needs UsageStats; stores OBSERVED package foreground only — not lesson completion."
            ),
            CapabilityEntry(
                id = ID_USAGE_STATS,
                title = "App foreground time",
                status = CapabilityStatus.PERMISSION_NEEDED,
                limitNote = "Needs UsageStats access; package-level use only — not lesson or video identity."
            ),
            CapabilityEntry(
                id = ID_EXTERNAL_CALENDAR,
                title = "External calendar sync",
                status = CapabilityStatus.NOT_SUPPORTED,
                limitNote = "Local schedule only in this build; no Google/Outlook write-back."
            ),
            CapabilityEntry(
                id = ID_FLOATING_OVERLAY,
                title = "Floating agent overlay",
                status = CapabilityStatus.PERMISSION_NEEDED,
                limitNote = "Requires overlay permission for the floating companion. " +
                    "Use Home’s Agent tab or Discuss without it."
            ),
            CapabilityEntry(
                id = ID_VOICE_INPUT,
                title = "Voice conversation input",
                status = CapabilityStatus.PERMISSION_NEEDED,
                limitNote = "Requires microphone permission and platform speech recognition. " +
                    "Use conversation chips without it — same command path, no live LLM."
            ),
            CapabilityEntry(
                id = ID_NOTIFICATION_LISTENER,
                title = "Notification summaries",
                status = CapabilityStatus.PERMISSION_NEEDED,
                limitNote = "Needs Notification access for permitted summaries. " +
                    "Does not silence notifications or prove lesson completion — " +
                    "Complete remains your report."
            ),
            CapabilityEntry(
                id = ID_SESSION_NOTIFICATION_REVIEW,
                title = "Session notification review",
                status = CapabilityStatus.PERMISSION_NEEDED,
                limitNote = "Needs Notification access for package counts during a session. " +
                    "Does not store titles/bodies, silence apps, or mark sessions complete."
            ),
            CapabilityEntry(
                id = ID_QUIET_MODE,
                title = "Quiet mode / DND",
                status = CapabilityStatus.NOT_SUPPORTED,
                limitNote = "User-initiated quiet mode is not shipped as an INTENT filter. " +
                    "INTENT cannot silence all apps — open system Do Not Disturb if you " +
                    "want device quiet. OEM variance applies; emergency alerts remain."
            ),
            CapabilityEntry(
                id = ID_SCREEN_CAPTURE,
                title = "Screen capture consent",
                status = CapabilityStatus.PERMISSION_NEEDED,
                limitNote = "Requires one-shot MediaProjection system consent when you ask. " +
                    "Does not OCR screens or prove lesson completion; frames not retained by default."
            ),
            CapabilityEntry(
                id = ID_UNDO_LAST,
                title = "Undo last reversible commitment",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Reverses the last local FlowCommand via transaction history " +
                    "(approve / decline / defer / session / trash). External app opens cannot be undone."
            ),
            CapabilityEntry(
                id = ID_TRASH_SOFT_DELETE,
                title = "Soft-delete to trash",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Moves intentions and sessions into recoverable trash; " +
                    "keeps until purge unless an expiry is set."
            ),
            CapabilityEntry(
                id = ID_TRASH_RESTORE,
                title = "Restore from trash",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Restores a soft-deleted intention or session with preserved child context."
            ),
            CapabilityEntry(
                id = ID_EXTERNAL_ACTION_UNDO,
                title = "Undo external action",
                status = CapabilityStatus.NOT_SUPPORTED,
                limitNote = "Opening another app, calendar write-back, messages, and purchases " +
                    "cannot be reversed by INTENT — only logged honestly."
            ),
            CapabilityEntry(
                id = ID_DAILY_CHECK_IN,
                title = "Daily check-in",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Morning focus confirm/adjust on the Home intention model — " +
                    "not a separate daily-task store."
            ),
            CapabilityEntry(
                id = ID_DAILY_CHECK_OUT,
                title = "Daily check-out",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Evening SAID review + keep/defer on the same intention snapshot."
            ),
            CapabilityEntry(
                id = ID_TRACKER_MODULES,
                title = "Tracker modules",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Shape-default modules in the intention store; manual entries are SAID. " +
                    "No partner autofill or fabricated streak charts."
            ),
            CapabilityEntry(
                id = ID_PROGRESS_REPORTS,
                title = "Progress reports",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "PathSummary + labeled SAID/OBSERVED/INFERRED proof from the same store. " +
                    "Missing partner data stays empty — never invents completion."
            ),
            CapabilityEntry(
                id = ID_CAPTURE_THOUGHT,
                title = "Capture thought",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Stores raw text with SAID provenance before classification."
            ),
            CapabilityEntry(
                id = ID_BUILD_INTENTION,
                title = "Guided intention builder",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Heuristic shape + missing-fact prompts — no live LLM. " +
                    "Facts are SAID only after you confirm; no auto-commit of INFERRED."
            ),
            CapabilityEntry(
                id = ID_TRIAGE_THOUGHTS,
                title = "Multi-thought triage",
                status = CapabilityStatus.AVAILABLE,
                limitNote = "Heuristic split into proposed intentions — no live LLM. " +
                    "Confirm creates an intention; reject leaves the thought as evidence only."
            ),
            CapabilityEntry(
                id = ID_PARTNER_SPOTIFY,
                title = "Spotify partner",
                status = CapabilityStatus.NOT_SUPPORTED,
                limitNote = "Spotify OAuth, playback state, and controls are not supported. " +
                    "No fake now-playing or completion claims."
            ),
            CapabilityEntry(
                id = ID_PARTNER_CHESS,
                title = "Chess.com partner",
                status = CapabilityStatus.NOT_SUPPORTED,
                limitNote = "Chess.com ratings and game sync are not supported. " +
                    "Use a manual SAID baseline — never invent ratings or games."
            ),
            CapabilityEntry(
                id = ID_PARTNER_STRAVA,
                title = "Strava partner",
                status = CapabilityStatus.NOT_SUPPORTED,
                limitNote = "Strava activity sync is not supported. " +
                    "No fabricated runs or auto-Complete from partner data."
            )
        )

        val default: CapabilityRegistry = CapabilityRegistry()
    }
}
