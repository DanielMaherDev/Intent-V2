package com.intent.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.intent.launcher.IntentApp
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.BusinessScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.engine.DiscussFromHome
import com.intent.launcher.engine.FloatingAgentState
import com.intent.launcher.engine.FloatingCompanion
import com.intent.launcher.engine.InstalledAppLauncher
import com.intent.launcher.engine.LaunchDestinationApp
import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.engine.OverlayPermissionAccess
import com.intent.launcher.engine.OverlayPermissionProbe
import com.intent.launcher.engine.PlatformCapabilityLive
import com.intent.launcher.engine.UsageStatsAccess
import com.intent.launcher.engine.UsageStatsProbe
import com.intent.launcher.store.IntentionFlowStore

class LauncherViewModel(
    private val store: IntentionFlowStore = IntentApp.instance.store,
    private val appLauncher: InstalledAppLauncher = IntentApp.instance.appLauncher,
    private val usageStatsAccess: UsageStatsAccess = IntentApp.instance.usageStatsAccess,
    private val overlayPermissionAccess: OverlayPermissionAccess =
        IntentApp.instance.overlayPermissionAccess,
    initialRegistry: CapabilityRegistry? = null
) : ViewModel() {

    private var capabilityRegistry: CapabilityRegistry =
        initialRegistry ?: PlatformCapabilityLive.registry(usageStatsAccess, overlayPermissionAccess)

    var snapshot by mutableStateOf(store.snapshot())
        private set

    var nextAction by mutableStateOf(recommend())
        private set

    /**
     * Honest note from the last destination-app launch attempt
     * (missing app / blocked / failed / opened). Cleared on refresh when unused.
     */
    var destinationLaunchNote by mutableStateOf<String?>(null)
        private set

    /** Package-level UsageStats observation for the active SessionCard (never completion). */
    var usageObservation by mutableStateOf<UsageStatsProbe.Observation?>(null)
        private set

    /** Overlay permission honesty — floating companion when granted. */
    var overlayObservation by mutableStateOf<OverlayPermissionProbe.Observation?>(
        OverlayPermissionProbe.observe(overlayPermissionAccess)
    )
        private set

    /** Same-agent floating companion presence (null state = no bubble). */
    var floatingPresence by mutableStateOf(
        FloatingCompanion.presence(
            nextAction = nextAction,
            overlayGranted = overlayPermissionAccess.canDrawOverlays(),
            registry = capabilityRegistry
        )
    )
        private set

    init {
        usageObservation = observeUsageIfActive()
        floatingPresence = resolveFloatingPresence()
    }

    fun refresh() {
        capabilityRegistry =
            PlatformCapabilityLive.registry(usageStatsAccess, overlayPermissionAccess)
        snapshot = store.snapshot()
        nextAction = recommend()
        usageObservation = observeUsageIfActive()
        overlayObservation = OverlayPermissionProbe.observe(overlayPermissionAccess)
        floatingPresence = resolveFloatingPresence()
    }

    private fun resolveFloatingPresence(): FloatingCompanion.Presence =
        FloatingCompanion.presence(
            nextAction = nextAction,
            overlayGranted = overlayPermissionAccess.canDrawOverlays(),
            registry = capabilityRegistry
        )

    /** Overlay bubble state when allowed; null means do not show fake floating UI. */
    fun floatingBubbleState(): FloatingAgentState? = floatingPresence.state

    /**
     * Companion press / notification — same Discuss → Conversation path as Home.
     */
    fun openFromFloatingCompanion(): String? {
        val result = FloatingCompanion.onPress(
            store = store,
            nextAction = nextAction,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        refresh()
        return result.navigateTo
    }

    private fun recommend(): NextAction =
        NextActionEngine.recommend(
            store.snapshot(),
            System.currentTimeMillis(),
            registry = capabilityRegistry
        )

    private fun observeUsageIfActive(): UsageStatsProbe.Observation? {
        if (nextAction.kind != NextActionKind.ACTIVE_SESSION) return null
        val destination = LaunchDestinationApp.resolve(activeIntention(), nextAction)
            ?: SessionDestinations.forIntention(activeIntention())
        return UsageStatsProbe.observeSessionForeground(
            access = usageStatsAccess,
            destination = destination,
            sessionStartedAtEpochMs = nextAction.startedAtEpochMs,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
    }

    fun activeIntention(): Intention? {
        val snap = snapshot
        val id = snap.activeIntentionId
        return snap.intentions.firstOrNull { it.id == id } ?: snap.intentions.firstOrNull()
    }

    fun conversationChips(): List<String> =
        when (activeIntention()?.shapeTag) {
            "marathon" -> MarathonScenario.conversationChips(capabilityRegistry)
            "business" -> BusinessScenario.conversationChips(capabilityRegistry)
            else -> SpanishScenario.conversationChips(capabilityRegistry)
        }

    fun onConversationChip(chip: String) {
        when (activeIntention()?.shapeTag) {
            "marathon" -> MarathonScenario.onChip(store, chip, registry = capabilityRegistry)
            "business" -> BusinessScenario.onChip(store, chip, registry = capabilityRegistry)
            else -> SpanishScenario.onChip(store, chip, registry = capabilityRegistry)
        }
        refresh()
    }

    fun startMarathonExploration() {
        MarathonScenario.ensureSeeded(store)
        refresh()
    }

    fun startBusinessExploration() {
        BusinessScenario.ensureSeeded(store)
        refresh()
    }

    fun activateSpanish() {
        SpanishScenario.ensureSeeded(store)
        store.apply(
            FlowCommand.SetActiveIntention(
                intentionId = SpanishScenario.INTENTION_ID,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun approveProposal() {
        val pending = snapshot.proposals.firstOrNull { it.status == ProposalStatus.PENDING } ?: return
        val sessionId = when (pending.id) {
            MarathonScenario.PROPOSAL_ID -> MarathonScenario.SESSION_ID
            BusinessScenario.PROPOSAL_ID -> BusinessScenario.SESSION_ID
            SpanishScenario.PROPOSAL_ID -> SpanishScenario.SESSION_ID
            else -> "session-${pending.id}"
        }
        store.apply(
            FlowCommand.ApproveProposal(
                proposalId = pending.id,
                sessionId = sessionId,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun declineProposal() {
        val pending = snapshot.proposals.firstOrNull { it.status == ProposalStatus.PENDING } ?: return
        store.apply(
            FlowCommand.DeclineProposal(
                proposalId = pending.id,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun editProposalToFriday() {
        val pending = snapshot.proposals.firstOrNull { it.status == ProposalStatus.PENDING } ?: return
        val hour = when (pending.id) {
            MarathonScenario.PROPOSAL_ID -> 9
            BusinessScenario.PROPOSAL_ID -> 12
            else -> 18
        }
        store.apply(
            FlowCommand.EditProposal(
                proposalId = pending.id,
                dayOfWeek = 5,
                hour = hour,
                minute = 0,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun deferNext() {
        val sessionId = nextAction.sessionId
            ?: snapshot.sessions.firstOrNull { it.intentionId == activeIntention()?.id }?.id
            ?: return
        val until = System.currentTimeMillis() + 2 * 60 * 60 * 1000L
        store.apply(
            FlowCommand.DeferNextSession(
                sessionId = sessionId,
                deferUntilEpochMs = until,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    /**
     * Home Discuss → Conversation for the next-action intention (scheduled or
     * active session). Returns the nav route, or null when Discuss is locked.
     * Does not complete or abandon an in-progress run.
     */
    fun openDiscussFromHome(): String? {
        val result = DiscussFromHome.open(
            store = store,
            nextAction = nextAction,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        refresh()
        return result.navigateTo
    }

    /**
     * Begin the recommended (or given) scheduled session via the command path,
     * then attempt destination-app launch when the shape declares one.
     * Launch outcome never marks the session complete.
     */
    fun beginSession(sessionId: String? = nextAction.sessionId): Boolean {
        if (!capabilityRegistry.canRecommendBegin()) return false
        val id = sessionId
            ?: snapshot.sessions.firstOrNull { it.intentionId == activeIntention()?.id }?.id
            ?: return false
        val intentionId = snapshot.sessions.firstOrNull { it.id == id }?.intentionId
        val alreadyActive = snapshot.sessionRuns.any {
            it.intentionId == intentionId && it.status == SessionRunStatus.IN_PROGRESS
        }
        if (alreadyActive) return false
        val now = System.currentTimeMillis()
        store.apply(
            FlowCommand.BeginSession(
                runId = "run-$id-$now",
                sessionId = id,
                nowEpochMs = now
            )
        )
        refresh()
        val started = snapshot.sessionRuns.any {
            it.sessionId == id && it.status == SessionRunStatus.IN_PROGRESS
        }
        if (started) {
            launchDestinationApp()
        }
        return started
    }

    /**
     * Open / re-open the destination package for the current next-action intention.
     * Registry-gated; missing/blocked/failed surfaces [destinationLaunchNote].
     * Never completes or abandons the active run.
     */
    fun launchDestinationApp(): LaunchDestinationApp.Result {
        val destination = LaunchDestinationApp.resolve(
            intention = activeIntention(),
            nextAction = nextAction
        )
        val result = LaunchDestinationApp.attempt(
            destination = destination,
            launcher = appLauncher,
            registry = capabilityRegistry
        )
        destinationLaunchNote = result.note
        return result
    }

    fun clearDestinationLaunchNote() {
        destinationLaunchNote = null
    }

    fun completeActiveSession(): Boolean {
        if (!capabilityRegistry.canCompleteSession()) return false
        val runId = nextAction.sessionRunId
            ?: snapshot.sessionRuns.firstOrNull {
                it.intentionId == activeIntention()?.id && it.status == SessionRunStatus.IN_PROGRESS
            }?.id
            ?: return false
        store.apply(
            FlowCommand.CompleteSessionRun(
                runId = runId,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        destinationLaunchNote = null
        refresh()
        return true
    }

    fun abandonActiveSession(): Boolean {
        if (!capabilityRegistry.canAbandonSession()) return false
        val runId = nextAction.sessionRunId
            ?: snapshot.sessionRuns.firstOrNull {
                it.intentionId == activeIntention()?.id && it.status == SessionRunStatus.IN_PROGRESS
            }?.id
            ?: return false
        store.apply(
            FlowCommand.AbandonSessionRun(
                runId = runId,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        destinationLaunchNote = null
        refresh()
        return true
    }
}
