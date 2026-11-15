package com.intent.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.intent.launcher.IntentApp
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.BusinessScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.FlowTransaction
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.domain.TrashEntry
import com.intent.launcher.engine.CaptureThought
import com.intent.launcher.engine.CommandReversibility
import com.intent.launcher.engine.ConversationCoordinator
import com.intent.launcher.engine.DiscussFromHome
import com.intent.launcher.engine.FloatingAgentState
import com.intent.launcher.engine.FloatingCompanion
import com.intent.launcher.engine.InstalledAppLauncher
import com.intent.launcher.engine.LaunchDestinationApp
import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.engine.NotificationListenerAccess
import com.intent.launcher.engine.NotificationListenerProbe
import com.intent.launcher.engine.OverlayPermissionAccess
import com.intent.launcher.engine.OverlayPermissionProbe
import com.intent.launcher.engine.PlatformCapabilityLive
import com.intent.launcher.engine.SpeechToTextAccess
import com.intent.launcher.engine.UsageStatsAccess
import com.intent.launcher.engine.UsageStatsProbe
import com.intent.launcher.engine.VoiceInputProbe
import com.intent.launcher.engine.VoicePermissionAccess
import com.intent.launcher.store.IntentionFlowStore

class LauncherViewModel(
    private val store: IntentionFlowStore = IntentApp.instance.store,
    private val appLauncher: InstalledAppLauncher = IntentApp.instance.appLauncher,
    private val usageStatsAccess: UsageStatsAccess = IntentApp.instance.usageStatsAccess,
    private val overlayPermissionAccess: OverlayPermissionAccess =
        IntentApp.instance.overlayPermissionAccess,
    private val voicePermissionAccess: VoicePermissionAccess =
        IntentApp.instance.voicePermissionAccess,
    private val speechToTextAccess: SpeechToTextAccess =
        IntentApp.instance.speechToTextAccess,
    private val notificationListenerAccess: NotificationListenerAccess =
        IntentApp.instance.notificationListenerAccess,
    initialRegistry: CapabilityRegistry? = null
) : ViewModel() {

    private var capabilityRegistry: CapabilityRegistry =
        initialRegistry
            ?: PlatformCapabilityLive.registry(
                usageStatsAccess,
                overlayPermissionAccess,
                voicePermissionAccess,
                speechToTextAccess,
                notificationListenerAccess
            )

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

    /** Notification listener honesty for the active SessionCard (never silence / completion). */
    var notificationObservation by mutableStateOf<NotificationListenerProbe.Observation?>(null)
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

    /** Voice / mic honesty for Conversation. */
    var voiceObservation by mutableStateOf(
        VoiceInputProbe.observe(voicePermissionAccess, speechToTextAccess)
    )
        private set

    /** Last voice-path note (permission, unmatched transcript, chip match). */
    var voicePathNote by mutableStateOf<String?>(null)
        private set

    /** Confirmation after a successful (or failed empty) thought capture. */
    var thoughtCaptureNote by mutableStateOf<String?>(null)
        private set

    /** Undo / trash honesty (irreversible external, expired trash, capability lock). */
    var undoTrashNote by mutableStateOf<String?>(null)
        private set

    init {
        usageObservation = observeUsageIfActive()
        notificationObservation = observeNotificationsIfActive()
        floatingPresence = resolveFloatingPresence()
    }

    fun lastUndoableTransaction(): FlowTransaction? =
        CommandReversibility.lastUndoable(snapshot.transactions)

    fun recoverableTrash(nowEpochMs: Long = System.currentTimeMillis()): List<TrashEntry> =
        snapshot.trash.filter { entry ->
            entry.expiresAtEpochMs == null || entry.expiresAtEpochMs >= nowEpochMs
        }

    fun refresh() {
        capabilityRegistry =
            PlatformCapabilityLive.registry(
                usageStatsAccess,
                overlayPermissionAccess,
                voicePermissionAccess,
                speechToTextAccess,
                notificationListenerAccess
            )
        snapshot = store.snapshot()
        nextAction = recommend()
        usageObservation = observeUsageIfActive()
        notificationObservation = observeNotificationsIfActive()
        overlayObservation = OverlayPermissionProbe.observe(overlayPermissionAccess)
        floatingPresence = resolveFloatingPresence()
        voiceObservation = VoiceInputProbe.observe(voicePermissionAccess, speechToTextAccess)
    }

    /** True when mic may start platform speech recognition. */
    fun canStartVoiceCapture(): Boolean =
        capabilityRegistry.canUseVoiceInput() && voiceObservation.canCapture

    fun voiceNeedsPermission(): Boolean =
        voiceObservation.canRequestPermission && !voiceObservation.permissionGranted

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

    /**
     * Persist a raw thought (SAID). [source] is floater / home / voice.
     * Does not open Conversation or invent a summary.
     */
    fun captureThought(
        text: String,
        source: String = CaptureThought.SOURCE_HOME
    ): Boolean {
        val result = FloatingCompanion.onCaptureThought(
            store = store,
            text = text,
            nowEpochMs = System.currentTimeMillis(),
            source = source
        )
        thoughtCaptureNote = result.confirmation
        refresh()
        return result.ok
    }

    fun clearThoughtCaptureNote() {
        thoughtCaptureNote = null
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

    private fun observeNotificationsIfActive(): NotificationListenerProbe.Observation? {
        if (nextAction.kind != NextActionKind.ACTIVE_SESSION) return null
        return NotificationListenerProbe.observeForSession(notificationListenerAccess)
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
        ConversationCoordinator.submitChip(
            store = store,
            intention = activeIntention(),
            chip = chip,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        voicePathNote = null
        refresh()
    }

    /**
     * After platform STT returns text — shared ConversationCoordinator path with chips.
     */
    fun onVoiceTranscript(transcript: String) {
        val result = ConversationCoordinator.submitVoiceTranscript(
            store = store,
            intention = activeIntention(),
            availableChips = conversationChips(),
            transcript = transcript,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        voicePathNote = result.honestyNote
        refresh()
    }

    fun onVoiceUnavailable(message: String) {
        voicePathNote = message
    }

    fun onMicDenied() {
        voicePathNote = voiceObservation.note
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
        if (result.opened) {
            val reason = capabilityRegistry.get(CapabilityRegistry.ID_EXTERNAL_ACTION_UNDO)
                ?.limitNote
                ?: "Opening another app cannot be undone by INTENT."
            store.apply(
                FlowCommand.RecordExternalAction(
                    transactionId = "ext-launch-${System.currentTimeMillis()}",
                    summary = "Opened ${destination?.label ?: "destination app"}",
                    irreversibleReason = reason,
                    nowEpochMs = System.currentTimeMillis()
                )
            )
            undoTrashNote = reason
            refresh()
        }
        return result
    }

    /** Undo last reversible FlowCommand via transaction history. */
    fun undoLast(): Boolean {
        if (!capabilityRegistry.canUndoLast()) {
            undoTrashNote = capabilityRegistry.get(CapabilityRegistry.ID_UNDO_LAST)?.limitNote
                ?: "Undo is unavailable."
            return false
        }
        val target = lastUndoableTransaction()
        if (target == null) {
            val last = snapshot.transactions.lastOrNull()
            undoTrashNote = last?.irreversibleReason
                ?: "Nothing to undo."
            return false
        }
        store.apply(
            FlowCommand.UndoLastReversible(
                transactionId = "undo-${System.currentTimeMillis()}",
                nowEpochMs = System.currentTimeMillis()
            )
        )
        undoTrashNote = "Undid: ${target.summary}"
        refresh()
        return true
    }

    fun softDeleteActiveIntention(): Boolean {
        if (!capabilityRegistry.canSoftDelete()) {
            undoTrashNote = capabilityRegistry.get(CapabilityRegistry.ID_TRASH_SOFT_DELETE)?.limitNote
            return false
        }
        val intention = activeIntention() ?: return false
        val now = System.currentTimeMillis()
        store.apply(
            FlowCommand.SoftDeleteIntention(
                trashId = "trash-intention-${intention.id}-$now",
                intentionId = intention.id,
                nowEpochMs = now,
                expiresAtEpochMs = null
            )
        )
        undoTrashNote = "Moved “${intention.title}” to trash — restore anytime, or purge to delete permanently."
        refresh()
        return true
    }

    fun softDeleteSession(sessionId: String): Boolean {
        if (!capabilityRegistry.canSoftDelete()) {
            undoTrashNote = capabilityRegistry.get(CapabilityRegistry.ID_TRASH_SOFT_DELETE)?.limitNote
            return false
        }
        val now = System.currentTimeMillis()
        store.apply(
            FlowCommand.SoftDeleteSession(
                trashId = "trash-session-$sessionId-$now",
                sessionId = sessionId,
                nowEpochMs = now,
                expiresAtEpochMs = null
            )
        )
        undoTrashNote = "Moved session to trash — restore anytime until purge."
        refresh()
        return true
    }

    fun restoreTrashItem(trashId: String): Boolean {
        if (!capabilityRegistry.canRestoreTrash()) {
            undoTrashNote = capabilityRegistry.get(CapabilityRegistry.ID_TRASH_RESTORE)?.limitNote
            return false
        }
        val entry = snapshot.trash.firstOrNull { it.id == trashId } ?: return false
        val now = System.currentTimeMillis()
        if (entry.expiresAtEpochMs != null && entry.expiresAtEpochMs < now) {
            undoTrashNote = "Trash item expired — purge only."
            return false
        }
        store.apply(
            FlowCommand.RestoreTrashItem(
                trashId = trashId,
                nowEpochMs = now
            )
        )
        undoTrashNote = "Restored “${entry.title}” from trash."
        refresh()
        return true
    }

    fun purgeTrashItem(trashId: String): Boolean {
        val entry = snapshot.trash.firstOrNull { it.id == trashId } ?: return false
        store.apply(
            FlowCommand.PurgeTrashItem(
                trashId = trashId,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        undoTrashNote = "Permanently deleted “${entry.title}” — cannot undo."
        refresh()
        return true
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
