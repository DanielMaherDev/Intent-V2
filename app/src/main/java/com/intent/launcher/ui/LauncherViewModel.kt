package com.intent.launcher.ui
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.intent.launcher.IntentApp
import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AppUseReductionScenario
import com.intent.launcher.domain.BusinessScenario
import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.ErrandStatus
import com.intent.launcher.domain.ErrandTodo
import com.intent.launcher.domain.EuWorkSpainScenario
import com.intent.launcher.domain.Evidence
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.FlowTransaction
import com.intent.launcher.domain.HeldApp
import com.intent.launcher.domain.HeldApps
import com.intent.launcher.domain.HoldableApps
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionOrdering
import com.intent.launcher.domain.JobOfferScenario
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.MoveScenario
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.QuitSmokingScenario
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.domain.ThoughtCapture
import com.intent.launcher.domain.ThoughtCaptureStatus
import com.intent.launcher.domain.ThoughtDisposition
import com.intent.launcher.engine.EveningThoughtTriage
import com.intent.launcher.domain.TrashEntry
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrackerModuleDef
import com.intent.launcher.domain.TrackerModules
import com.intent.launcher.engine.AppConnectionInventory
import com.intent.launcher.engine.AppConnectionUsage
import com.intent.launcher.engine.AppLinkedSessionPause
import com.intent.launcher.engine.CaptureThought
import com.intent.launcher.engine.ThoughtTriage
import com.intent.launcher.engine.IntentionBuilder
import com.intent.launcher.engine.CommandReversibility
import com.intent.launcher.engine.ConversationCoordinator
import com.intent.launcher.engine.DailyCycle
import com.intent.launcher.engine.DeferralPreview
import com.intent.launcher.engine.DiscussFromHome
import com.intent.launcher.engine.ErrandCapture
import com.intent.launcher.engine.FloaterActionDrawer
import com.intent.launcher.engine.FloatingAgentState
import com.intent.launcher.engine.FloatingCompanion
import com.intent.launcher.engine.InstalledAppLauncher
import com.intent.launcher.engine.LaunchDestinationApp
import com.intent.launcher.engine.LiveLlmAccess
import com.intent.launcher.engine.LiveLlmProbe
import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.engine.NotificationListenerAccess
import com.intent.launcher.engine.NotificationListenerProbe
import com.intent.launcher.engine.ObservedProvenance
import com.intent.launcher.engine.NotificationPackageTally
import com.intent.launcher.engine.OverlayPermissionAccess
import com.intent.launcher.engine.OverlayPermissionProbe
import com.intent.launcher.engine.PathSummary
import com.intent.launcher.engine.PlatformCapabilityLive
import com.intent.launcher.engine.PlayStoreDeepLink
import com.intent.launcher.engine.ProgressProof
import com.intent.launcher.engine.QuietModeAccess
import com.intent.launcher.engine.QuietModeProbe
import com.intent.launcher.engine.ScreenCaptureAccess
import com.intent.launcher.engine.ScreenCaptureProbe
import com.intent.launcher.engine.SessionNotificationReviewProbe
import com.intent.launcher.engine.SessionNotificationReviewStore
import com.intent.launcher.engine.SpeechToTextAccess
import com.intent.launcher.engine.UsageStatsAccess
import com.intent.launcher.engine.UsageStatsProbe
import com.intent.launcher.engine.VoiceInputProbe
import com.intent.launcher.engine.VoicePermissionAccess
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone


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
    private val quietModeAccess: QuietModeAccess =
        IntentApp.instance.quietModeAccess,
    private val screenCaptureAccess: ScreenCaptureAccess =
        IntentApp.instance.screenCaptureAccess,
    private val sessionNotificationReviewStore: SessionNotificationReviewStore =
        IntentApp.instance.sessionNotificationReviewStore,
    private val liveLlmAccess: LiveLlmAccess = IntentApp.instance.liveLlmAccess,
    initialRegistry: CapabilityRegistry? = null
) : ViewModel() {

    private var capabilityRegistry: CapabilityRegistry =
        initialRegistry
            ?: PlatformCapabilityLive.registry(
                usageStatsAccess,
                overlayPermissionAccess,
                voicePermissionAccess,
                speechToTextAccess,
                notificationListenerAccess,
                quietModeAccess,
                screenCaptureAccess,
                liveLlmAccess
            )

    /** Local UI flag — user asked for quiet; never implies silence applied. */
    private var quietRequestedForActiveSession: Boolean = false

    /** Last ended session run id kept for post-session package-count review. */
    private var lastReviewSessionRunId: String? =
        sessionNotificationReviewStore.activeWindow()?.sessionRunId

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

    /** Durable OBSERVED evidence for the active session run (null when none / dismissed). */
    var observedEvidence by mutableStateOf<Evidence?>(null)
        private set

    /** Post-Complete / check-out note from OBSERVED evidence — never auto-complete. */
    var checkoutObservationNote by mutableStateOf<String?>(null)
        private set

    /** Screen-capture consent honesty for the active SessionCard (never OCR / lesson). */
    var screenCaptureObservation by mutableStateOf<ScreenCaptureProbe.Observation?>(null)
        private set

    /** App-linked soft-pause evaluation for the active SessionCard (never completion). */
    var sessionPause by mutableStateOf<AppLinkedSessionPause.Evaluation?>(null)
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

    /** Explainable deferral preview when Later is offered (null when not applicable). */
    var deferPreview by mutableStateOf<DeferralPreview.Preview?>(null)
        private set

    /** True after Later is tapped — confirm before [FlowCommand.DeferNextSession]. */
    var deferConfirmPending by mutableStateOf(false)
        private set

    /** Quiet / DND honesty after SessionCard request (never claims silence applied). */
    var quietObservation by mutableStateOf<QuietModeProbe.Observation?>(null)
        private set

    /** Privacy-safe package counts for the active / last session (no titles/bodies). */
    var sessionNotificationObservation by mutableStateOf(
        observeSessionNotifications()
    )
        private set

    /** Floater Today — points at Home next-action strip. */
    var floaterTodayNote by mutableStateOf<String?>(null)
        private set

    /** Optional Ask context line (active intention title) — not an LLM prompt. */
    var askContextChip by mutableStateOf<String?>(null)
        private set

    /** Builder / triage honesty or validation note for the current screen. */
    var builderTriageNote by mutableStateOf<String?>(null)
        private set

    /** Live LLM / local-heuristic honesty for Discuss + Conversation. */
    var liveLlmObservation by mutableStateOf(LiveLlmProbe.observe(liveLlmAccess))
        private set

    /** Confirmation / honesty note after capturing or completing an errand. */
    var errandNote by mutableStateOf<String?>(null)
        private set

    init {
        usageObservation = observeUsageIfActive()
        notificationObservation = observeNotificationsIfActive()
        maybeRecordObservedEvidence(usageObservation)
        observedEvidence = resolveObservedEvidence()
        screenCaptureObservation = observeScreenCaptureIfActive()
        applyPauseEvaluation()
        floatingPresence = resolveFloatingPresence()
        deferPreview = resolveDeferPreview()
        quietObservation = observeQuietIfActive()
        sessionNotificationObservation = observeSessionNotifications()
        liveLlmObservation = LiveLlmProbe.observe(liveLlmAccess)
    }

    fun rawThoughtCount(): Int =
        snapshot.thoughtCaptures.count { it.status == ThoughtCaptureStatus.RAW }

    /** Unclassified RAW thoughts for evening check-out (OS-154). */
    fun rawThoughtsForCheckOut(): List<ThoughtCapture> =
        EveningThoughtTriage.listRawForEvening(store)

    fun nextRawThoughtForCheckOut(): ThoughtCapture? =
        EveningThoughtTriage.nextRaw(store)

    /** Confirmation note after an evening disposition. */
    var eveningTriageNote by mutableStateOf<String?>(null)
        private set

    fun clearEveningTriageNote() {
        eveningTriageNote = null
    }

    /**
     * User-picked evening disposition — no LLM invent.
     */
    fun classifyThought(thoughtId: String, disposition: ThoughtDisposition): Boolean {
        val result = EveningThoughtTriage.classify(
            store = store,
            thoughtId = thoughtId,
            disposition = disposition,
            nowEpochMs = System.currentTimeMillis()
        )
        eveningTriageNote = result.confirmation
        refresh()
        return result.ok
    }

    fun startBuilder(freeText: String): Boolean {
        val result = IntentionBuilder.start(
            store = store,
            freeText = freeText,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        builderTriageNote = result.note
        refresh()
        return result.ok
    }

    fun pickBuilderShape(shapeTag: String): Boolean {
        val ok = IntentionBuilder.setShape(
            store = store,
            shapeTag = shapeTag,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        builderTriageNote = if (ok) IntentionBuilder.HONESTY else "Could not set shape."
        refresh()
        return ok
    }

    fun answerBuilderPrompt(promptId: String, answer: String): Boolean {
        val ok = IntentionBuilder.answerPrompt(
            store = store,
            promptId = promptId,
            answerText = answer,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        builderTriageNote = if (ok) {
            "Answer saved in draft — SAID fact written only when you confirm."
        } else {
            "Could not save answer."
        }
        refresh()
        return ok
    }

    fun commitBuilder(): Boolean {
        val ok = IntentionBuilder.commit(
            store = store,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        builderTriageNote = if (ok) {
            "Intention created — facts stored with SAID provenance."
        } else {
            "Could not confirm intention."
        }
        refresh()
        return ok
    }

    fun cancelBuilder() {
        IntentionBuilder.cancel(store, System.currentTimeMillis())
        builderTriageNote = null
        refresh()
    }

    fun startTriageFromDump(dump: String): Boolean {
        val result = ThoughtTriage.startFromDump(
            store = store,
            dumpText = dump,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        builderTriageNote = result.note
        refresh()
        return result.ok
    }

    fun startTriageFromRaw(): Boolean {
        val result = ThoughtTriage.startFromRawThoughts(
            store = store,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        builderTriageNote = result.note
        refresh()
        return result.ok
    }

    fun confirmTriageItem(itemId: String): Boolean {
        val ok = ThoughtTriage.confirm(
            store = store,
            itemId = itemId,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        builderTriageNote = if (ok) "Intention confirmed from triage." else "Confirm failed."
        refresh()
        return ok
    }

    fun rejectTriageItem(itemId: String): Boolean {
        val ok = ThoughtTriage.reject(
            store = store,
            itemId = itemId,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        builderTriageNote = if (ok) {
            "Rejected — thought kept as RAW evidence only."
        } else {
            "Reject failed."
        }
        refresh()
        return ok
    }

    fun clearTriage() {
        store.apply(FlowCommand.ClearTriageBatch(nowEpochMs = System.currentTimeMillis()))
        builderTriageNote = null
        refresh()
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
                notificationListenerAccess,
                quietModeAccess,
                screenCaptureAccess,
                liveLlmAccess
            )
        snapshot = store.snapshot()
        nextAction = recommend()
        if (nextAction.kind != NextActionKind.ACTIVE_SESSION) {
            quietRequestedForActiveSession = false
        }
        usageObservation = observeUsageIfActive()
        notificationObservation = observeNotificationsIfActive()
        maybeRecordObservedEvidence(usageObservation)
        snapshot = store.snapshot()
        observedEvidence = resolveObservedEvidence()
        screenCaptureObservation = observeScreenCaptureIfActive()
        applyPauseEvaluation()
        overlayObservation = OverlayPermissionProbe.observe(overlayPermissionAccess)
        floatingPresence = resolveFloatingPresence()
        voiceObservation = VoiceInputProbe.observe(voicePermissionAccess, speechToTextAccess)
        deferPreview = resolveDeferPreview()
        if (deferPreview == null) deferConfirmPending = false
        quietObservation = observeQuietIfActive()
        sessionNotificationObservation = observeSessionNotifications()
        liveLlmObservation = LiveLlmProbe.observe(liveLlmAccess)
    }

    /**
     * User-initiated quiet request on SessionCard.
     * Does not mute apps or change DND — surfaces honesty + optional system deep link.
     */
    fun requestSessionQuiet() {
        if (nextAction.kind != NextActionKind.ACTIVE_SESSION) return
        quietRequestedForActiveSession = true
        quietObservation = QuietModeProbe.observeAfterRequest(
            access = quietModeAccess,
            quietRequested = true,
            registry = capabilityRegistry
        )
    }

    private fun observeQuietIfActive(): QuietModeProbe.Observation? {
        if (nextAction.kind != NextActionKind.ACTIVE_SESSION) return null
        if (!quietRequestedForActiveSession) {
            return QuietModeProbe.Observation(
                quietRequested = false,
                policyAccessGranted = quietModeAccess.hasNotificationPolicyAccess(),
                silenceApplied = false,
                note = "",
                canOpenDndSettings = false
            )
        }
        return QuietModeProbe.observeAfterRequest(
            access = quietModeAccess,
            quietRequested = true,
            registry = capabilityRegistry
        )
    }

    private fun observeSessionNotifications(): SessionNotificationReviewProbe.Observation {
        val runId = when {
            nextAction.kind == NextActionKind.ACTIVE_SESSION -> nextAction.sessionRunId
            else -> lastReviewSessionRunId
                ?: sessionNotificationReviewStore.activeWindow()?.sessionRunId
        }
        return SessionNotificationReviewProbe.observe(
            access = notificationListenerAccess,
            store = sessionNotificationReviewStore,
            registry = capabilityRegistry,
            sessionRunId = runId
        )
    }

    fun sessionNotificationRows(): List<NotificationPackageTally> =
        sessionNotificationObservation.rows

    fun dismissSessionNotificationRow(packageName: String, coarseCategory: String) {
        val runId = lastReviewSessionRunId
            ?: sessionNotificationReviewStore.activeWindow()?.sessionRunId
            ?: nextAction.sessionRunId
            ?: return
        sessionNotificationReviewStore.dismiss(packageName, coarseCategory, runId)
        sessionNotificationObservation = observeSessionNotifications()
    }

    /** Banner for Conversation: "Local heuristics" unless live_llm is honestly LIVE. */
    fun discussSourceLabel(): String =
        LiveLlmProbe.discussSourceLabel(capabilityRegistry)

    fun discussHonestyNote(): String =
        liveLlmObservation.note

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
        askContextChip = null
        refresh()
        return result.navigateTo
    }

    /** Floater drawer Ask — ConversationCoordinator path + optional intention context chip. */
    fun openAskFromDrawer(): String? {
        val result = FloaterActionDrawer.openAsk(
            store = store,
            nextAction = nextAction,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        askContextChip = result.contextChip
        floaterTodayNote = null
        refresh()
        return result.navigateTo
    }

    /** Floater drawer Today — Home next-action focus note. */
    fun openTodayFromDrawer(): String {
        val result = FloaterActionDrawer.openToday(nextAction)
        floaterTodayNote = result.focusNote
        askContextChip = null
        refresh()
        return result.navigateTo
    }

    fun clearFloaterTodayNote() {
        floaterTodayNote = null
    }

    fun clearAskContextChip() {
        askContextChip = null
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
        val destination = LaunchDestinationApp.resolve(
            intention = activeIntention(),
            nextAction = nextAction,
            snapshot = snapshot
        ) ?: SessionDestinations.forIntention(activeIntention())
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

    /**
     * Persist OBSERVED evidence while/after UsageStats is granted for the active window.
     * Never changes session-run status.
     */
    private fun maybeRecordObservedEvidence(observation: UsageStatsProbe.Observation?) {
        if (observation == null) return
        if (nextAction.kind != NextActionKind.ACTIVE_SESSION) return
        val intentionId = nextAction.intentionId ?: activeIntention()?.id ?: return
        ObservedProvenance.recordIfObservable(
            store = store,
            observation = observation,
            intentionId = intentionId,
            sessionRunId = nextAction.sessionRunId,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
    }

    private fun resolveObservedEvidence(): Evidence? =
        ObservedProvenance.latestActiveForRun(store.snapshot(), nextAction.sessionRunId)
            ?: ObservedProvenance.activeForIntention(
                store.snapshot(),
                activeIntention()?.id
            ).maxByOrNull { it.recordedAtEpochMs }

    /** SessionCard / Home copy for durable OBSERVED evidence (not lesson completion). */
    fun observedEvidenceNote(): String? =
        observedEvidence?.let { ObservedProvenance.sessionCardNote(it) }

    fun dismissObservedEvidence(evidenceId: String? = observedEvidence?.id): Boolean {
        val id = evidenceId ?: return false
        store.apply(
            FlowCommand.DismissEvidence(
                evidenceId = id,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
        return true
    }

    fun clearCheckoutObservationNote() {
        checkoutObservationNote = null
    }

    private fun observeScreenCaptureIfActive(): ScreenCaptureProbe.Observation? {
        if (nextAction.kind != NextActionKind.ACTIVE_SESSION) return null
        return ScreenCaptureProbe.observeForSession(screenCaptureAccess)
    }

    /** Intent for MediaProjection consent; null when unavailable or already denied. */
    fun screenCaptureConsentIntent() = screenCaptureAccess.createConsentIntent()

    fun onScreenCaptureConsentGranted() {
        screenCaptureAccess.recordConsentGranted()
        refresh()
    }

    fun onScreenCaptureConsentDenied() {
        screenCaptureAccess.recordConsentDenied()
        refresh()
    }

    private fun activeSessionRun() =
        snapshot.sessionRuns.firstOrNull {
            it.id == nextAction.sessionRunId && it.status == SessionRunStatus.IN_PROGRESS
        } ?: snapshot.sessionRuns.firstOrNull {
            it.intentionId == activeIntention()?.id && it.status == SessionRunStatus.IN_PROGRESS
        }

    private fun applyPauseEvaluation() {
        if (nextAction.kind != NextActionKind.ACTIVE_SESSION) {
            sessionPause = null
            return
        }
        val run = activeSessionRun()
        val destination = LaunchDestinationApp.resolve(activeIntention(), nextAction)
            ?: SessionDestinations.forIntention(activeIntention())
        val now = System.currentTimeMillis()
        var evaluation = AppLinkedSessionPause.evaluate(
            access = usageStatsAccess,
            run = run,
            destination = destination,
            nowEpochMs = now
        )
        if (run != null && evaluation.shouldAutoPause) {
            store.apply(
                FlowCommand.SoftPauseSessionRun(runId = run.id, nowEpochMs = now)
            )
            snapshot = store.snapshot()
            nextAction = recommend()
            evaluation = AppLinkedSessionPause.evaluate(
                access = usageStatsAccess,
                run = activeSessionRun(),
                destination = destination,
                nowEpochMs = now
            )
        } else if (run != null && evaluation.shouldAutoResume) {
            store.apply(
                FlowCommand.ResumeSessionRun(runId = run.id, nowEpochMs = now)
            )
            snapshot = store.snapshot()
            nextAction = recommend()
            evaluation = AppLinkedSessionPause.evaluate(
                access = usageStatsAccess,
                run = activeSessionRun(),
                destination = destination,
                nowEpochMs = now
            )
        }
        sessionPause = evaluation
    }

    fun activeIntention(): Intention? {
        val snap = snapshot
        val id = snap.activeIntentionId
        return snap.intentions.firstOrNull { it.id == id } ?: snap.intentions.firstOrNull()
    }

    /** Drawer list — explicit order, then any remainder. */
    fun orderedIntentions(): List<Intention> = IntentionOrdering.ordered(snapshot)

    fun heldAppsForActive(): List<HeldApp> =
        HeldApps.forIntention(snapshot, activeIntention()?.id)

    fun holdSuggestionsForActive(): List<DestinationApp> =
        HoldableApps.suggestions(activeIntention(), snapshot)

    fun openIntentionFromDrawer(intentionId: String) {
        store.apply(
            FlowCommand.SetActiveIntention(
                intentionId = intentionId,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun moveIntentionInDrawer(intentionId: String, direction: Int) {
        val ordered = IntentionOrdering.ordered(snapshot).map { it.id }
        val next = IntentionOrdering.move(ordered, intentionId, direction)
        if (next == ordered) return
        store.apply(
            FlowCommand.ReorderIntentions(
                orderedIntentionIds = next,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun confirmHoldApp(packageName: String, label: String) {
        val intentionId = activeIntention()?.id ?: return
        store.apply(
            FlowCommand.HoldAppForIntention(
                heldAppId = "held-$intentionId-$packageName",
                intentionId = intentionId,
                packageName = packageName,
                label = label,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun moveHeldApp(heldAppId: String, direction: Int) {
        val intentionId = activeIntention()?.id ?: return
        val held = HeldApps.forIntention(snapshot, intentionId)
        val next = HeldApps.move(held, heldAppId, direction)
        if (next == held.map { it.id }) return
        store.apply(
            FlowCommand.ReorderHeldApps(
                intentionId = intentionId,
                orderedHeldAppIds = next,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun releaseHeldApp(heldAppId: String) {
        store.apply(
            FlowCommand.ReleaseHeldApp(
                heldAppId = heldAppId,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    /** Honest note from last unlink attempt (no-op when disconnected / not supported). */
    var connectionUnlinkNote by mutableStateOf<String?>(null)
        private set

    /** OS-157/158 — all catalog candidates with live install flags (never invent installs). */
    fun appConnections() =
        AppConnectionInventory.connections(appLauncher)

    /** OS-159 — Settings list: intentions + SAID/OBSERVED contribution per connection. */
    fun connectionUsages(): List<AppConnectionUsage.ConnectionUsage> =
        AppConnectionUsage.forAll(
            connections = appConnections(),
            snapshot = snapshot,
            registry = capabilityRegistry
        )

    /** OS-159 — Intention Space subset for the active intention. */
    fun connectionUsagesForActive(): List<AppConnectionUsage.ConnectionUsage> =
        AppConnectionUsage.forIntention(
            intentionId = activeIntention()?.id,
            connections = appConnections(),
            snapshot = snapshot,
            registry = capabilityRegistry
        )

    /** Unlink is an honest no-op when DISCONNECTED / NOT_SUPPORTED. */
    fun unlinkConnection(connectionId: String) {
        val connection = appConnections().firstOrNull { it.id == connectionId } ?: return
        val result = AppConnectionInventory.unlink(connection)
        connectionUnlinkNote = result.note
    }

    fun clearConnectionUnlinkNote() {
        connectionUnlinkNote = null
    }

    fun playStoreOffer(packageName: String, label: String): PlayStoreDeepLink.Offer =
        PlayStoreDeepLink.forPackage(packageName, label)

    fun conversationChips(): List<String> =
        when (activeIntention()?.shapeTag) {
            "marathon" -> MarathonScenario.conversationChips(capabilityRegistry)
            "business" -> BusinessScenario.conversationChips(capabilityRegistry)
            "move" -> MoveScenario.conversationChips(capabilityRegistry)
            "decision" -> JobOfferScenario.conversationChips(capabilityRegistry)
            QuitSmokingScenario.SHAPE_TAG -> QuitSmokingScenario.conversationChips(capabilityRegistry)
            "app_use" -> AppUseReductionScenario.conversationChips(capabilityRegistry)
            "research" -> EuWorkSpainScenario.conversationChips(capabilityRegistry)
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

    fun startMoveExploration() {
        MoveScenario.ensureSeeded(store)
        refresh()
    }

    fun startJobOfferDecision() {
        JobOfferScenario.ensureSeeded(store)
        refresh()
    }

    fun startQuitSmokingExploration() {
        QuitSmokingScenario.ensureSeeded(store)
        refresh()
    }

    fun startAppUseExploration() {
        AppUseReductionScenario.ensureSeeded(store)
        refresh()
    }

    fun startEuWorkSpainExploration() {
        EuWorkSpainScenario.ensureSeeded(store)
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
            MoveScenario.PROPOSAL_ID -> MoveScenario.SESSION_ID
            JobOfferScenario.PROPOSAL_ID -> JobOfferScenario.SESSION_ID
            QuitSmokingScenario.PROPOSAL_ID -> QuitSmokingScenario.SESSION_ID
            AppUseReductionScenario.PROPOSAL_ID -> AppUseReductionScenario.SESSION_ID
            EuWorkSpainScenario.PROPOSAL_ID -> EuWorkSpainScenario.SESSION_ID
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
            MoveScenario.PROPOSAL_ID -> 10
            JobOfferScenario.PROPOSAL_ID -> 18
            QuitSmokingScenario.PROPOSAL_ID -> 20
            AppUseReductionScenario.PROPOSAL_ID -> 20
            EuWorkSpainScenario.PROPOSAL_ID -> 10
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

    private fun resolveDeferPreview(): DeferralPreview.Preview? {
        if (nextAction.deferLabel == null) return null
        val sessionId = nextAction.sessionId
            ?: snapshot.sessions.firstOrNull { it.intentionId == activeIntention()?.id }?.id
            ?: return null
        return DeferralPreview.previewForNextAction(
            snapshot = snapshot,
            sessionId = sessionId,
            nowEpochMs = System.currentTimeMillis(),
            timeZone = TimeZone.getDefault()
        )
    }

    /** Later tapped — show preview confirm; does not mutate until [confirmDefer]. */
    fun requestDefer() {
        deferPreview = resolveDeferPreview()
        if (deferPreview == null) return
        deferConfirmPending = true
    }

    fun cancelDefer() {
        deferConfirmPending = false
    }

    /** Commit deferral via [FlowCommand.DeferNextSession] using the previewed window. */
    fun confirmDefer() {
        val preview = deferPreview ?: resolveDeferPreview() ?: return
        val now = System.currentTimeMillis()
        store.apply(
            FlowCommand.DeferNextSession(
                sessionId = preview.sessionId,
                deferUntilEpochMs = preview.deferUntilEpochMs,
                nowEpochMs = now
            )
        )
        deferConfirmPending = false
        refresh()
    }

    /**
     * Home Later entry — opens preview confirm ([requestDefer]).
     * Commit only via [confirmDefer] → [FlowCommand.DeferNextSession].
     */
    fun deferNext() {
        requestDefer()
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
        val runId = "run-$id-$now"
        val destination = SessionDestinations.forIntention(
            snapshot.intentions.firstOrNull { it.id == intentionId } ?: activeIntention()
        )
        store.apply(
            FlowCommand.BeginSession(
                runId = runId,
                sessionId = id,
                nowEpochMs = now,
                affinityPackageName = destination?.packageName,
                affinityAppLabel = destination?.label
            )
        )
        sessionNotificationReviewStore.beginWindow(runId, now)
        lastReviewSessionRunId = runId
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
     * Manual Resume after soft-pause. Unfreezes elapsed; never Completes.
     */
    fun resumeActiveSession(): Boolean {
        val run = activeSessionRun() ?: return false
        if (run.pausedAtEpochMs == null) return false
        store.apply(
            FlowCommand.ResumeSessionRun(
                runId = run.id,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
        return true
    }

    fun activeSessionPausedAtEpochMs(): Long? = activeSessionRun()?.pausedAtEpochMs

    fun activeSessionAccumulatedPausedMs(): Long =
        activeSessionRun()?.accumulatedPausedMs ?: 0L

    /**
     * Open / re-open the destination package for the current next-action intention.
     * Registry-gated; missing/blocked/failed surfaces [destinationLaunchNote].
     * Never completes or abandons the active run.
     */
    fun launchDestinationApp(): LaunchDestinationApp.Result {
        val destination = LaunchDestinationApp.resolve(
            intention = activeIntention(),
            nextAction = nextAction,
            snapshot = snapshot
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
        val intentionId = nextAction.intentionId
            ?: snapshot.sessionRuns.firstOrNull { it.id == runId }?.intentionId
            ?: activeIntention()?.id
        val now = System.currentTimeMillis()
        // Capture OBSERVED evidence at check-out when UsageStats is granted — still user Completes.
        val observation = usageObservation ?: observeUsageIfActive()
        if (observation != null && intentionId != null) {
            ObservedProvenance.recordIfObservable(
                store = store,
                observation = observation,
                intentionId = intentionId,
                sessionRunId = runId,
                nowEpochMs = now,
                registry = capabilityRegistry
            )
        }
        val recorded = ObservedProvenance.latestActiveForRun(store.snapshot(), runId)
        store.apply(
            FlowCommand.CompleteSessionRun(
                runId = runId,
                nowEpochMs = now
            )
        )
        checkoutObservationNote = recorded?.let { ObservedProvenance.checkoutNote(it) }
        // End tally window — review rows remain; never auto-Complete from counts.
        sessionNotificationReviewStore.endWindow(now)
        lastReviewSessionRunId = runId
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
        val now = System.currentTimeMillis()
        store.apply(
            FlowCommand.AbandonSessionRun(
                runId = runId,
                nowEpochMs = now
            )
        )
        sessionNotificationReviewStore.endWindow(now)
        lastReviewSessionRunId = runId
        destinationLaunchNote = null
        refresh()
        return true
    }

    /**
     * Morning check-in — confirm today's focus (+ optional SAID adjustment note).
     * Same intention snapshot; not a separate daily-task store.
     */
    fun confirmDailyCheckIn(adjustedFocusNote: String? = null): Boolean {
        if (!capabilityRegistry.canDailyCheckIn()) return false
        val intention = activeIntention() ?: return false
        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val localDate = DailyCycle.localDateString(now, tz)
        val note = adjustedFocusNote?.trim().orEmpty()
        val summary = nextAction.detail.ifBlank {
            DailyCycle.focusSummary(
                intentionTitle = intention.title,
                previewNextLabel = nextAction.label,
                previewDetail = nextAction.detail
            )
        }
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = intention.id,
                localDate = localDate,
                focusSummary = summary,
                nowEpochMs = now,
                adjustedFocusNote = note.takeIf { it.isNotEmpty() },
                adjustedFactId = if (note.isNotEmpty()) "fact-checkin-$localDate-$now" else null,
                adjustedHypothesisId = if (note.isNotEmpty()) {
                    "hyp-checkin-$localDate-$now"
                } else {
                    null
                }
            )
        )
        refresh()
        return snapshot.dayCycle.checkedInFor(localDate)
    }

    /**
     * Evening check-out — SAID what-happened (+ optional SAID hypothesis), then keep
     * schedule or defer tomorrow via the existing deferral field.
     */
    fun completeDailyCheckOut(
        whatHappened: String,
        deferTomorrow: Boolean,
        hypothesisText: String? = null
    ): Boolean {
        if (!capabilityRegistry.canDailyCheckOut()) return false
        val intention = activeIntention() ?: return false
        val now = System.currentTimeMillis()
        val tz = TimeZone.getDefault()
        val localDate = DailyCycle.localDateString(now, tz)
        val reported = whatHappened.trim().ifBlank {
            DailyCycle.summarizeTodayRuns(snapshot, intention.id, now, tz)
        }
        val sessionId = nextAction.sessionId
            ?: snapshot.sessions.firstOrNull { it.intentionId == intention.id }?.id
        val hyp = hypothesisText?.trim().orEmpty()
        store.apply(
            FlowCommand.CompleteDailyCheckOut(
                intentionId = intention.id,
                localDate = localDate,
                whatHappened = reported,
                factId = "fact-checkout-$localDate-$now",
                nowEpochMs = now,
                hypothesisId = if (hyp.isNotEmpty()) "hyp-checkout-$localDate-$now" else null,
                hypothesisText = hyp.takeIf { it.isNotEmpty() },
                deferSessionId = if (deferTomorrow) sessionId else null,
                deferUntilEpochMs = if (deferTomorrow) {
                    DailyCycle.tomorrowMorningEpochMs(now, tz)
                } else {
                    null
                }
            )
        )
        refresh()
        return snapshot.dayCycle.checkedOutFor(localDate)
    }

    fun trackerModules(): List<TrackerModuleDef> =
        TrackerModules.forShape(activeIntention()?.shapeTag)

    fun trackerEntriesForActive(): List<TrackerEntry> {
        val id = activeIntention()?.id ?: return emptyList()
        return snapshot.trackerEntries.filter { it.intentionId == id }
    }

    fun trackerHonestyNote(): String =
        capabilityRegistry.get(CapabilityRegistry.ID_TRACKER_MODULES)?.limitNote
            ?: "Manual tracker entries are SAID."

    fun canOpenTracker(): Boolean = capabilityRegistry.canUseTrackerModules()

    fun canOpenReports(): Boolean = capabilityRegistry.canViewProgressReports()

    fun recordTrackerEntry(
        moduleId: String,
        valueText: String,
        provenanceKind: ProvenanceKind = ProvenanceKind.SAID,
        sourceLabel: String = "user"
    ): Boolean {
        if (!capabilityRegistry.canUseTrackerModules()) return false
        val intentionId = activeIntention()?.id ?: return false
        val now = System.currentTimeMillis()
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-$moduleId-$now",
                intentionId = intentionId,
                moduleId = moduleId,
                valueText = valueText,
                nowEpochMs = now,
                provenanceKind = provenanceKind,
                sourceLabel = sourceLabel
            )
        )
        refresh()
        return true
    }

    /**
     * Optional OBSERVED tracker row from live UsageStats package foreground.
     * Does not complete the session.
     */
    fun recordObservedForegroundToTracker(): Boolean {
        val obs = usageObservation ?: return false
        if (!obs.permissionGranted || obs.foregroundMs == null || obs.appLabel == null) return false
        val minutes = (obs.foregroundMs / 60_000L).coerceAtLeast(0L)
        val moduleId = trackerModules().firstOrNull()?.id ?: return false
        return recordTrackerEntry(
            moduleId = moduleId,
            valueText = "Observed $minutes min in ${obs.appLabel} (package-level)",
            provenanceKind = ProvenanceKind.OBSERVED,
            sourceLabel = "usage_stats"
        )
    }

    fun deleteTrackerEntry(entryId: String): Boolean {
        if (!capabilityRegistry.canUseTrackerModules()) return false
        store.apply(
            FlowCommand.DeleteTrackerEntry(
                entryId = entryId,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
        return snapshot.trackerEntries.any {
            it.id == entryId && it.status == TrackerEntryStatus.DELETED
        }
    }

    fun pathSummary(): PathSummary.View? =
        PathSummary.build(
            snapshot = snapshot,
            intention = activeIntention(),
            nextAction = nextAction,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )

    fun progressProof(): ProgressProof.View? =
        ProgressProof.build(
            snapshot = snapshot,
            intentionId = activeIntention()?.id,
            nowEpochMs = System.currentTimeMillis(),
            shapeTag = activeIntention()?.shapeTag,
            registry = capabilityRegistry
        )

    /** Open errands for the Home inbox (newest first). */
    fun openErrands(): List<ErrandTodo> =
        snapshot.errandTodos
            .filter { it.status == ErrandStatus.OPEN }
            .sortedByDescending { it.createdAtEpochMs }

    fun canAddQuickTodo(): Boolean = capabilityRegistry.canCaptureQuickTodo()

    /**
     * Capture a lightweight errand. Never seeds an Intention.
     */
    fun captureQuickTodo(text: String): Boolean {
        val result = ErrandCapture.capture(
            store = store,
            text = text,
            nowEpochMs = System.currentTimeMillis(),
            registry = capabilityRegistry
        )
        errandNote = result.confirmation
        refresh()
        return result.ok
    }

    /** Tap open → Done; tap Done (if shown) → reopen. Open list only shows OPEN. */
    fun toggleErrand(todo: ErrandTodo) {
        val now = System.currentTimeMillis()
        val ok = when (todo.status) {
            ErrandStatus.OPEN -> ErrandCapture.complete(store, todo.id, now)
            ErrandStatus.DONE -> ErrandCapture.reopen(store, todo.id, now)
        }
        errandNote = when {
            !ok -> "Could not update that to-do."
            todo.status == ErrandStatus.OPEN -> "Done — “${todo.firstAction}”."
            else -> "Reopened — “${todo.firstAction}”."
        }
        refresh()
    }

    fun clearErrandNote() {
        errandNote = null
    }
}
