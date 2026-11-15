package com.intent.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ShapeProjection
import com.intent.launcher.engine.CaptureThought
import com.intent.launcher.engine.FloaterDrawerAction
import com.intent.launcher.engine.OverlayPermissionProbe
import com.intent.launcher.engine.QuietModeProbe
import com.intent.launcher.floating.FloatingAgentService
import com.intent.launcher.platform.AndroidSpeechToTextAccess
import com.intent.launcher.ui.LauncherViewModel
import com.intent.launcher.ui.screens.ConversationScreen
import com.intent.launcher.ui.screens.DeferConfirmDialog
import com.intent.launcher.ui.screens.FloaterActionDrawerDialog
import com.intent.launcher.ui.screens.HomeScreen
import com.intent.launcher.ui.screens.IntentionBuilderScreen
import com.intent.launcher.ui.screens.IntentionDrawerScreen
import com.intent.launcher.ui.screens.IntentionSpaceScreen
import com.intent.launcher.ui.screens.ProposalReviewScreen
import com.intent.launcher.ui.screens.ReportScreen
import com.intent.launcher.ui.screens.ThoughtCaptureDialog
import com.intent.launcher.ui.screens.ThoughtTriageScreen
import com.intent.launcher.ui.screens.TrackerScreen
import com.intent.launcher.ui.theme.IntentTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private var openConversationRequest by mutableIntStateOf(0)
    private var openAskFromDrawerRequest by mutableIntStateOf(0)
    private var openTodayFromDrawerRequest by mutableIntStateOf(0)
    private var showFloaterDrawer by mutableStateOf(false)
    private var showThoughtCapture by mutableStateOf(false)
    private var thoughtCaptureSource: String = CaptureThought.SOURCE_HOME
    /** When true, STT result goes to CaptureThought instead of Conversation. */
    private var speechForThoughtCapture: Boolean = false

    private val screenCaptureConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onScreenCaptureConsentGranted()
        } else {
            vm.onScreenCaptureConsentDenied()
        }
    }

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        vm.refresh()
        if (granted && vm.canStartVoiceCapture()) {
            launchSpeechRecognition()
        } else if (speechForThoughtCapture) {
            speechForThoughtCapture = false
            showThoughtCapture = true
            vm.onVoiceUnavailable(
                "Microphone needed for voice capture — type the thought instead."
            )
        } else {
            vm.onMicDenied()
        }
    }

    private val speechRecognition = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val forThought = speechForThoughtCapture
        speechForThoughtCapture = false
        if (result.resultCode != Activity.RESULT_OK) {
            if (forThought) {
                showThoughtCapture = true
                vm.onVoiceUnavailable("Speech cancelled — type the thought instead.")
            } else {
                vm.onVoiceUnavailable(
                    "Speech capture cancelled — use chips for the same Conversation path."
                )
            }
            return@registerForActivityResult
        }
        val matches = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            .orEmpty()
        val best = matches.firstOrNull().orEmpty()
        if (best.isBlank()) {
            if (forThought) {
                showThoughtCapture = true
                vm.onVoiceUnavailable("No speech recognized — type the thought instead.")
            } else {
                vm.onVoiceUnavailable("No speech recognized — try again or tap a chip.")
            }
        } else if (forThought) {
            vm.captureThought(best, CaptureThought.SOURCE_VOICE)
            showThoughtCapture = false
        } else {
            vm.onVoiceTranscript(best)
            val after = vm.snapshot
            val activeId = vm.activeIntention()?.id
            if (after.proposals.any {
                    it.status == ProposalStatus.PENDING && it.intentionId == activeId
                }
            ) {
                pendingOpenProposalAfterVoice++
            }
        }
    }

    private var pendingOpenProposalAfterVoice by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeCompanionIntent(intent)
        setContent {
            IntentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    val snap = vm.snapshot
                    val intention = vm.activeIntention()
                    val pending = snap.proposals.firstOrNull {
                        it.status == ProposalStatus.PENDING && it.intentionId == intention?.id
                    }
                    val intentionProjection = ShapeProjection.project(
                        intention = intention,
                        facts = snap.facts.filter { it.intentionId == intention?.id },
                        hypotheses = snap.hypotheses.filter { it.intentionId == intention?.id },
                        sessions = snap.sessions.filter { it.intentionId == intention?.id }
                    )
                    val intentionHint = intentionProjection.homeSpaceSubtitle
                    val showMarathonEntry = intention?.shapeTag != "marathon"
                    val showBusinessEntry = intention?.shapeTag != "business"
                    val showMoveEntry = intention?.shapeTag != "move"

                    LaunchedEffect(openConversationRequest) {
                        if (openConversationRequest > 0) {
                            val route = vm.openFromFloatingCompanion()
                            if (route != null) {
                                nav.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                    LaunchedEffect(openAskFromDrawerRequest) {
                        if (openAskFromDrawerRequest > 0) {
                            val route = vm.openAskFromDrawer()
                            if (route != null) {
                                nav.navigate(route) {
                                    launchSingleTop = true
                                }
                            }
                        }
                    }
                    LaunchedEffect(openTodayFromDrawerRequest) {
                        if (openTodayFromDrawerRequest > 0) {
                            val route = vm.openTodayFromDrawer()
                            nav.navigate(route) {
                                popUpTo("home") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    LaunchedEffect(pendingOpenProposalAfterVoice) {
                        if (pendingOpenProposalAfterVoice > 0) {
                            nav.navigate("proposal")
                        }
                    }

                    if (showFloaterDrawer) {
                        FloaterActionDrawerDialog(
                            onDismiss = { showFloaterDrawer = false },
                            onAction = { action ->
                                showFloaterDrawer = false
                                when (action) {
                                    FloaterDrawerAction.ASK -> openAskFromDrawerRequest++
                                    FloaterDrawerAction.TODAY -> openTodayFromDrawerRequest++
                                    FloaterDrawerAction.CAPTURE -> {
                                        thoughtCaptureSource = CaptureThought.SOURCE_FLOATER
                                        showThoughtCapture = true
                                    }
                                }
                            }
                        )
                    }

                    if (showThoughtCapture) {
                        ThoughtCaptureDialog(
                            voiceAvailable = vm.canStartVoiceCapture() || vm.voiceNeedsPermission(),
                            onDismiss = {
                                showThoughtCapture = false
                            },
                            onSaveText = { text ->
                                vm.captureThought(text, thoughtCaptureSource)
                                showThoughtCapture = false
                            },
                            onCaptureVoice = {
                                showThoughtCapture = false
                                startThoughtVoiceCapture()
                            }
                        )
                    }

                    if (vm.deferConfirmPending) {
                        val preview = vm.deferPreview
                        if (preview != null) {
                            DeferConfirmDialog(
                                preview = preview,
                                onConfirm = { vm.confirmDefer() },
                                onDismiss = { vm.cancelDefer() }
                            )
                        }
                    }

                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                nextAction = vm.nextAction,
                                intentionHint = intentionHint,
                                intentionTitle = intention?.title.orEmpty(),
                                onContinueExploring = {
                                    nav.navigate("intention")
                                },
                                onOpenIntention = { nav.navigate("intention") },
                                onOpenIntentionDrawer = { nav.navigate("drawer") },
                                onReviewProposal = { nav.navigate("proposal") },
                                onBegin = { vm.beginSession() },
                                onDefer = { vm.requestDefer() },
                                deferPreviewLine = vm.deferPreview?.oneLine,
                                onDiscuss = {
                                    val route = vm.openDiscussFromHome()
                                    if (route != null) {
                                        nav.navigate(route)
                                    }
                                },
                                onCompleteSession = { vm.completeActiveSession() },
                                onAbandonSession = { vm.abandonActiveSession() },
                                onOpenDestination = { vm.launchDestinationApp() },
                                onConfirmCheckIn = { vm.confirmDailyCheckIn() },
                                onCheckOutKeepSchedule = {
                                    vm.completeDailyCheckOut(
                                        whatHappened = "",
                                        deferTomorrow = false
                                    )
                                },
                                onCheckOutDeferTomorrow = {
                                    vm.completeDailyCheckOut(
                                        whatHappened = "",
                                        deferTomorrow = true
                                    )
                                },
                                destinationLaunchNote = vm.destinationLaunchNote,
                                usageNote = vm.usageObservation?.note,
                                usageGranted = vm.usageObservation?.permissionGranted == true,
                                usageForegroundMs = vm.usageObservation?.foregroundMs,
                                usageAppLabel = vm.usageObservation?.appLabel,
                                showGrantUsageAccess = vm.usageObservation?.canOpenSettings == true,
                                onGrantUsageAccess = {
                                    startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                observedEvidenceNote = vm.observedEvidenceNote(),
                                showDismissObserved = vm.observedEvidence != null,
                                onDismissObserved = { vm.dismissObservedEvidence() },
                                checkoutObservationNote = vm.checkoutObservationNote,
                                onClearCheckoutNote = { vm.clearCheckoutObservationNote() },
                                notificationNote = vm.notificationObservation?.note,
                                showGrantNotificationAccess =
                                    vm.notificationObservation?.canOpenSettings == true,
                                onGrantNotificationAccess = {
                                    startActivity(
                                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                quietNote = vm.quietObservation?.note?.takeIf { it.isNotBlank() },
                                showRequestQuiet = vm.nextAction.kind == NextActionKind.ACTIVE_SESSION &&
                                    vm.quietObservation?.quietRequested != true,
                                onRequestQuiet = { vm.requestSessionQuiet() },
                                showOpenDndSettings = vm.quietObservation?.canOpenDndSettings == true,
                                onOpenDndSettings = {
                                    val action = vm.quietObservation?.dndSettingsAction
                                        ?: QuietModeProbe.DND_SETTINGS_ACTION
                                    startActivity(
                                        Intent(action).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                screenCaptureNote = vm.screenCaptureObservation?.note,
                                showCaptureScreen =
                                    vm.screenCaptureObservation?.canRequestConsent == true,
                                onCaptureScreen = {
                                    val intent = vm.screenCaptureConsentIntent()
                                    if (intent != null) {
                                        screenCaptureConsent.launch(intent)
                                    } else {
                                        vm.onScreenCaptureConsentDenied()
                                    }
                                },
                                sessionNotificationRows = vm.sessionNotificationRows().map { row ->
                                    val label = "${row.packageName} · ${row.coarseCategory} · ${row.count}"
                                    val key = "${row.packageName}|${row.coarseCategory}"
                                    label to key
                                },
                                onDismissNotificationRow = { pkg, category ->
                                    vm.dismissSessionNotificationRow(pkg, category)
                                },
                                overlayNote = vm.overlayObservation?.note,
                                showGrantOverlay = vm.overlayObservation?.canOpenSettings == true,
                                onGrantOverlay = {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse(
                                                OverlayPermissionProbe.manageOverlayDataUri(
                                                    packageName
                                                )
                                            )
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                floatingCompanionLabel = vm.floatingPresence.state?.let {
                                    vm.floatingPresence.label
                                },
                                floatingManualRouteNote = vm.floatingPresence.manualRouteNote,
                                floaterTodayNote = vm.floaterTodayNote,
                                onCaptureThought = {
                                    thoughtCaptureSource = CaptureThought.SOURCE_HOME
                                    showThoughtCapture = true
                                },
                                onOpenFloaterDrawer = { showFloaterDrawer = true },
                                thoughtCaptureNote = vm.thoughtCaptureNote,
                                onExploreMarathon = if (showMarathonEntry) {
                                    {
                                        vm.startMarathonExploration()
                                        nav.navigate("intention")
                                    }
                                } else {
                                    null
                                },
                                onExploreBusiness = if (showBusinessEntry) {
                                    {
                                        vm.startBusinessExploration()
                                        nav.navigate("intention")
                                    }
                                } else {
                                    null
                                },
                                onExploreMove = if (showMoveEntry) {
                                    {
                                        vm.startMoveExploration()
                                        nav.navigate("intention")
                                    }
                                } else {
                                    null
                                },
                                undoLabel = vm.lastUndoableTransaction()?.let {
                                    "Undo: ${it.summary}"
                                },
                                onUndo = vm.lastUndoableTransaction()?.let { { vm.undoLast() } },
                                undoTrashNote = vm.undoTrashNote,
                                onStartBuilder = { nav.navigate("builder") },
                                onOpenTriage = { nav.navigate("triage") },
                                rawThoughtCount = vm.rawThoughtCount(),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("builder") {
                            IntentionBuilderScreen(
                                draft = vm.snapshot.builderDraft,
                                honestyNote = vm.builderTriageNote,
                                onStart = { text -> vm.startBuilder(text) },
                                onPickShape = { shape -> vm.pickBuilderShape(shape) },
                                onAnswerPrompt = { id, answer -> vm.answerBuilderPrompt(id, answer) },
                                onCommit = {
                                    if (vm.commitBuilder()) {
                                        nav.navigate("intention") {
                                            popUpTo("home")
                                        }
                                    }
                                },
                                onCancel = {
                                    vm.cancelBuilder()
                                    nav.popBackStack()
                                },
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("triage") {
                            ThoughtTriageScreen(
                                triage = vm.snapshot.triage,
                                rawThoughtCount = vm.rawThoughtCount(),
                                honestyNote = vm.builderTriageNote,
                                onStartFromDump = { dump -> vm.startTriageFromDump(dump) },
                                onStartFromRaw = { vm.startTriageFromRaw() },
                                onConfirm = { id ->
                                    if (vm.confirmTriageItem(id)) {
                                        // Stay on triage for remaining items
                                    }
                                },
                                onReject = { id -> vm.rejectTriageItem(id) },
                                onClear = { vm.clearTriage() },
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("drawer") {
                            IntentionDrawerScreen(
                                intentions = vm.orderedIntentions(),
                                activeIntentionId = vm.snapshot.activeIntentionId,
                                onOpenIntention = { id ->
                                    vm.openIntentionFromDrawer(id)
                                    nav.navigate("intention") {
                                        launchSingleTop = true
                                    }
                                },
                                onMoveIntention = { id, direction ->
                                    vm.moveIntentionInDrawer(id, direction)
                                },
                                onBackHome = {
                                    nav.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                        launchSingleTop = true
                                    }
                                },
                                onAgent = {
                                    val route = vm.openDiscussFromHome()
                                    if (route != null) {
                                        nav.navigate(route)
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("intention") {
                            val active = vm.activeIntention()
                            IntentionSpaceScreen(
                                intention = active,
                                facts = vm.snapshot.facts.filter { it.intentionId == active?.id },
                                hypotheses = vm.snapshot.hypotheses.filter { it.intentionId == active?.id },
                                sessions = vm.snapshot.sessions.filter { it.intentionId == active?.id },
                                evidence = vm.snapshot.evidence.filter {
                                    it.intentionId == active?.id
                                },
                                heldApps = vm.heldAppsForActive(),
                                holdSuggestions = vm.holdSuggestionsForActive(),
                                onConfirmHold = { pkg, label -> vm.confirmHoldApp(pkg, label) },
                                onMoveHeldApp = { id, direction -> vm.moveHeldApp(id, direction) },
                                onReleaseHeldApp = { id -> vm.releaseHeldApp(id) },
                                onWorkThisOut = {
                                    when (active?.phase) {
                                        IntentionPhase.PROPOSAL_PENDING -> nav.navigate("proposal")
                                        else -> nav.navigate("conversation")
                                    }
                                },
                                onBack = { nav.popBackStack() },
                                onMoveIntentionToTrash = { vm.softDeleteActiveIntention() },
                                onMoveSessionToTrash = { sessionId -> vm.softDeleteSession(sessionId) },
                                trashItems = vm.recoverableTrash(),
                                onRestoreTrash = { trashId -> vm.restoreTrashItem(trashId) },
                                onPurgeTrash = { trashId -> vm.purgeTrashItem(trashId) },
                                undoTrashNote = vm.undoTrashNote,
                                onOpenTracker = if (vm.canOpenTracker()) {
                                    { nav.navigate("tracker") }
                                } else {
                                    null
                                },
                                onOpenReports = if (vm.canOpenReports()) {
                                    { nav.navigate("reports") }
                                } else {
                                    null
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("tracker") {
                            TrackerScreen(
                                intentionTitle = vm.activeIntention()?.title.orEmpty(),
                                modules = vm.trackerModules(),
                                entries = vm.trackerEntriesForActive(),
                                honestyNote = vm.trackerHonestyNote(),
                                onRecord = { moduleId, value ->
                                    vm.recordTrackerEntry(moduleId, value)
                                },
                                onDelete = { entryId -> vm.deleteTrackerEntry(entryId) },
                                onOpenReports = { nav.navigate("reports") },
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("reports") {
                            ReportScreen(
                                pathSummary = vm.pathSummary(),
                                progressProof = vm.progressProof(),
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("conversation") {
                            val active = vm.activeIntention()
                            val convProjection = ShapeProjection.project(intention = active)
                            ConversationScreen(
                                turns = vm.snapshot.turns.filter { it.intentionId == active?.id },
                                chips = listOfNotNull(vm.askContextChip) + vm.conversationChips(),
                                intentionTitle = active?.title.orEmpty(),
                                onChip = { chip ->
                                    if (chip == vm.askContextChip) {
                                        vm.clearAskContextChip()
                                    } else {
                                        vm.clearAskContextChip()
                                        vm.onConversationChip(chip)
                                        val after = vm.snapshot
                                        if (after.proposals.any {
                                                it.status == ProposalStatus.PENDING &&
                                                    it.intentionId == active?.id
                                            }
                                        ) {
                                            nav.navigate("proposal")
                                        }
                                    }
                                },
                                onBack = { nav.popBackStack() },
                                onOpenProposal = { nav.navigate("proposal") },
                                hasPendingProposal = pending != null,
                                conversationEyebrow = convProjection.conversationEyebrow,
                                conversationHeadline = convProjection.conversationHeadline,
                                voiceNote = vm.voicePathNote ?: vm.voiceObservation.note,
                                voiceEnabled = vm.canStartVoiceCapture(),
                                onMicClick = { onConversationMicClick() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("proposal") {
                            val active = vm.activeIntention()
                            val p = vm.snapshot.proposals.firstOrNull {
                                it.status == ProposalStatus.PENDING && it.intentionId == active?.id
                            } ?: vm.snapshot.proposals.lastOrNull { it.intentionId == active?.id }
                            ProposalReviewScreen(
                                proposal = p,
                                relatedFacts = vm.snapshot.facts.filter { fact ->
                                    p?.basedOnFactIds?.contains(fact.id) == true
                                },
                                onApprove = {
                                    vm.approveProposal()
                                    nav.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                },
                                onEdit = { vm.editProposalToFriday() },
                                onDecline = {
                                    vm.declineProposal()
                                    nav.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                },
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeCompanionIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.refresh()
        FloatingAgentService.sync(this, vm.floatingBubbleState())
    }

    private fun consumeCompanionIntent(intent: Intent?) {
        when (intent?.action) {
            FloatingAgentService.ACTION_OPEN_CONVERSATION -> openConversationRequest++
            FloatingAgentService.ACTION_OPEN_DRAWER -> showFloaterDrawer = true
            FloatingAgentService.ACTION_CAPTURE_THOUGHT -> {
                thoughtCaptureSource = CaptureThought.SOURCE_FLOATER
                showThoughtCapture = true
            }
        }
    }

    private fun onConversationMicClick() {
        speechForThoughtCapture = false
        vm.refresh()
        when {
            vm.canStartVoiceCapture() -> launchSpeechRecognition()
            vm.voiceNeedsPermission() ->
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            else -> vm.onVoiceUnavailable(
                vm.voiceObservation.note.ifBlank {
                    "Voice isn’t available — use chips for the same Conversation path."
                }
            )
        }
    }

    private fun startThoughtVoiceCapture() {
        speechForThoughtCapture = true
        vm.refresh()
        when {
            vm.canStartVoiceCapture() -> launchSpeechRecognition(prompt = "Speak your thought")
            vm.voiceNeedsPermission() ->
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            else -> {
                speechForThoughtCapture = false
                showThoughtCapture = true
                vm.onVoiceUnavailable(
                    "Voice isn’t available — type the thought instead."
                )
            }
        }
    }

    private fun launchSpeechRecognition(prompt: String = "Speak a conversation choice") {
        try {
            speechRecognition.launch(
                AndroidSpeechToTextAccess.listenIntent(prompt = prompt)
            )
        } catch (_: Exception) {
            if (speechForThoughtCapture) {
                speechForThoughtCapture = false
                showThoughtCapture = true
                vm.onVoiceUnavailable(
                    "Could not start speech recognition — type the thought instead."
                )
            } else {
                vm.onVoiceUnavailable(
                    "Could not start speech recognition — use chips instead."
                )
            }
        }
    }
}
