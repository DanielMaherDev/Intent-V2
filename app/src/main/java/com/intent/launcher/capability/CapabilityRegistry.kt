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
     * True when overlay permission is granted.
     * Does not mean a floating agent is shipped — check limit notes.
     */
    fun canUseFloatingOverlay(): Boolean =
        isActionable(ID_FLOATING_OVERLAY)

    /** Mic + platform STT available for voice turns (same ConversationCoordinator as chips). */
    fun canUseVoiceInput(): Boolean =
        isActionable(ID_VOICE_INPUT)

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
        const val ID_USAGE_STATS = "usage_stats"
        const val ID_EXTERNAL_CALENDAR = "external_calendar_sync"
        const val ID_FLOATING_OVERLAY = "floating_overlay"
        const val ID_VOICE_INPUT = "voice_input"

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
            )
        )

        val default: CapabilityRegistry = CapabilityRegistry()
    }
}
