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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.ShapeProjection
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.engine.AnimationBudgetGate
import com.intent.launcher.engine.CaptureThought
import com.intent.launcher.engine.DiscussOfflineHold
import com.intent.launcher.engine.FloaterDrawerAction
import com.intent.launcher.engine.OverlayPermissionProbe
import com.intent.launcher.engine.QuietModeProbe
import com.intent.launcher.engine.SpeechTranscriptInject
import com.intent.launcher.floating.FloatingAgentService
import com.intent.launcher.platform.AndroidSpeechToTextAccess
import com.intent.launcher.ui.LauncherViewModel
import com.intent.launcher.BuildConfig
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.store.SnapshotExport
import com.intent.launcher.ui.screens.ConnectionsScreen
import com.intent.launcher.ui.screens.ConversationScreen
import com.intent.launcher.ui.screens.DeferConfirmDialog
import com.intent.launcher.ui.screens.ErrandTodoDialog
import com.intent.launcher.ui.screens.FloaterActionDrawerDialog
import com.intent.launcher.ui.screens.HomeScreen
import com.intent.launcher.ui.screens.IntentionBuilderScreen
import com.intent.launcher.ui.screens.IntentionDrawerScreen
import com.intent.launcher.ui.screens.IntentionSpaceScreen
import com.intent.launcher.ui.screens.ProposalReviewScreen
import com.intent.launcher.ui.screens.HistoryScreen
import com.intent.launcher.ui.screens.ReportScreen
import com.intent.launcher.ui.screens.CheckOutWhatHappenedDialog
import com.intent.launcher.ui.screens.EveningThoughtTriageSheet
import com.intent.launcher.ui.screens.SettingsScreen
import com.intent.launcher.ui.screens.ThoughtCaptureDialog
import com.intent.launcher.ui.screens.TodayScreen
import com.intent.launcher.ui.screens.ThoughtTriageScreen
import com.intent.launcher.ui.screens.TrackerScreen
import com.intent.launcher.ui.screens.TrashScreen
import com.intent.launcher.ui.screens.WhatWeKnowScreen
import com.intent.launcher.ui.components.OverflowMenuItem
import com.intent.launcher.engine.FloaterActionDrawer
import com.intent.launcher.engine.HistoryTrashMenus
import com.intent.launcher.ui.theme.IntentTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private var openConversationRequest by mutableIntStateOf(0)
    private var openAskFromDrawerRequest by mutableIntStateOf(0)
    private var openTodayFromDrawerRequest by mutableIntStateOf(0)
    private var openHistoryFromDrawerRequest by mutableIntStateOf(0)
    private var showFloaterDrawer by mutableStateOf(false)
    private var showThoughtCapture by mutableStateOf(false)
    private var thoughtCaptureSource: String = CaptureThought.SOURCE_HOME
    /** When true, STT result goes to CaptureThought instead of Conversation. */
    private var speechForThoughtCapture: Boolean = false

    /** Evening check-out: what-happened → optional thought triage → Keep/Defer commit. */
    private var showCheckOutWhatHappened by mutableStateOf(false)
    private var showEveningTriage by mutableStateOf(false)
    private var pendingCheckOutDefer by mutableStateOf(false)
    private var pendingWhatHappened by mutableStateOf("")

    private val screenCaptureConsent = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            vm.onScreenCaptureConsentGranted()
        } else {
            vm.onScreenCaptureConsentDenied()
        }
    }

    private val importSnapshotPicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) return@registerForActivityResult
        val text = SnapshotExport.readImportText(this, uri).orEmpty()
        vm.importSnapshotJson(text)
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
        // §8.9 AVD: optional engineering hold so Cancel is tappable without live OpenAI.
        DiscussOfflineHold.applyFromIntentExtra(
            intent.getLongExtra(DiscussOfflineHold.EXTRA_HOLD_MS, -1L)
        )
        consumeCompanionIntent(intent)
        setContent {
            IntentTheme(
                themePreference = vm.themePref,
                reducedMotionPreference = vm.reducedMotionPref
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
                        sessions = snap.sessions.filter { it.intentionId == intention?.id },
                        approaches = snap.approaches.filter { it.intentionId == intention?.id },
                        openQuestions = snap.openQuestions.filter { it.intentionId == intention?.id }
                    )
                    val intentionHint = intentionProjection.homeSpaceSubtitle

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
                                launchSingleTop = true
                            }
                        }
                    }
                    LaunchedEffect(openHistoryFromDrawerRequest) {
                        if (openHistoryFromDrawerRequest > 0) {
                            nav.navigate(FloaterActionDrawer.HISTORY_ROUTE) {
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
                                    FloaterDrawerAction.CHECK_IN -> {
                                        vm.confirmDailyCheckIn()
                                    }
                                    FloaterDrawerAction.CHECK_OUT -> {
                                        pendingCheckOutDefer = false
                                        pendingWhatHappened = ""
                                        showCheckOutWhatHappened = true
                                    }
                                    FloaterDrawerAction.CAPTURE -> {
                                        thoughtCaptureSource = CaptureThought.SOURCE_FLOATER
                                        showThoughtCapture = true
                                    }
                                    FloaterDrawerAction.HISTORY -> openHistoryFromDrawerRequest++
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

                    if (showCheckOutWhatHappened) {
                        CheckOutWhatHappenedDialog(
                            initialText = pendingWhatHappened,
                            onDismiss = {
                                showCheckOutWhatHappened = false
                            },
                            onConfirm = { text ->
                                pendingWhatHappened = text
                                showCheckOutWhatHappened = false
                                if (vm.nextRawThoughtForCheckOut() != null) {
                                    showEveningTriage = true
                                } else {
                                    finishEveningCheckOut()
                                }
                            }
                        )
                    }

                    if (showEveningTriage) {
                        val current = vm.nextRawThoughtForCheckOut()
                        LaunchedEffect(current?.id, showEveningTriage) {
                            if (showEveningTriage && current == null) {
                                showEveningTriage = false
                                finishEveningCheckOut()
                            }
                        }
                        if (current != null) {
                            val remaining = vm.rawThoughtsForCheckOut().size
                            EveningThoughtTriageSheet(
                                thought = current,
                                remainingCount = remaining,
                                onClassify = { disposition ->
                                    vm.classifyThought(current.id, disposition)
                                    if (vm.nextRawThoughtForCheckOut() == null) {
                                        showEveningTriage = false
                                        finishEveningCheckOut()
                                    }
                                },
                                onSkipRest = {
                                    showEveningTriage = false
                                    finishEveningCheckOut()
                                }
                            )
                        }
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
                        composable("today") {
                            TodayScreen(
                                model = vm.todayModel(),
                                focusNote = vm.floaterTodayNote,
                                onBack = {
                                    vm.clearFloaterTodayNote()
                                    nav.popBackStack()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("home") {
                            var showErrandDialog by remember { mutableStateOf(false) }
                            if (showErrandDialog) {
                                ErrandTodoDialog(
                                    onDismiss = { showErrandDialog = false },
                                    onSave = { text ->
                                        vm.captureQuickTodo(text)
                                        showErrandDialog = false
                                    }
                                )
                            }
                            HomeScreen(
                                nextAction = vm.nextAction,
                                intentionHint = intentionHint,
                                intentionTitle = intention?.title.orEmpty(),
                                onContinueExploring = {
                                    nav.navigate("intention")
                                },
                                onOpenIntention = { nav.navigate("intention") },
                                onOpenIntentionDrawer = { nav.navigate("drawer") },
                                onOpenToday = {
                                    val route = vm.openTodayFromDrawer()
                                    nav.navigate(route) { launchSingleTop = true }
                                },
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
                                onConfirmMidday = {
                                    vm.recordMiddayCheck(
                                        summary = vm.nextAction.detail.ifBlank {
                                            "Midday check — plan held"
                                        },
                                        deferLater = false
                                    )
                                },
                                onDeferMidday = {
                                    vm.recordMiddayCheck(
                                        summary = vm.nextAction.detail.ifBlank {
                                            "Midday check — deferring"
                                        },
                                        deferLater = true
                                    )
                                },
                                onCheckOutKeepSchedule = {
                                    pendingCheckOutDefer = false
                                    pendingWhatHappened = ""
                                    showCheckOutWhatHappened = true
                                },
                                onCheckOutDeferTomorrow = {
                                    pendingCheckOutDefer = true
                                    pendingWhatHappened = ""
                                    showCheckOutWhatHappened = true
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
                                sessionSoftPaused = vm.sessionPause?.softPaused == true,
                                sessionPauseNote = vm.sessionPause?.note,
                                sessionPausedAtEpochMs = vm.activeSessionPausedAtEpochMs(),
                                sessionAccumulatedPausedMs = vm.activeSessionAccumulatedPausedMs(),
                                durationProgress = vm.activeDurationProgress(),
                                showResumeSession = vm.sessionPause?.canResume == true,
                                onResumeSession = { vm.resumeActiveSession() },
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
                                interventionPrompt = vm.activeInterventionPrompt(),
                                onOpenIntervention = {
                                    val route = vm.openDiscussFromHome()
                                    if (route != null) {
                                        nav.navigate(route)
                                    }
                                },
                                onDismissIntervention = { vm.dismissActiveIntervention() },
                                floaterTodayNote = vm.floaterTodayNote,
                                onCaptureThought = {
                                    thoughtCaptureSource = CaptureThought.SOURCE_HOME
                                    showThoughtCapture = true
                                },
                                onOpenFloaterDrawer = { showFloaterDrawer = true },
                                thoughtCaptureNote = vm.thoughtCaptureNote,
                                // CASE / explore fixture rows hidden from product Home —
                                // real create is chat-first Conversation + Triage.
                                onExploreMarathon = null,
                                onExploreBusiness = null,
                                onExploreMove = null,
                                onExploreJobOffer = null,
                                onExploreQuitSmoking = null,
                                onExploreAppUse = null,
                                onExploreEuWorkSpain = null,
                                onExploreChess = null,
                                undoLabel = vm.lastUndoableTransaction()?.let {
                                    "Undo: ${it.summary}"
                                },
                                onUndo = vm.lastUndoableTransaction()?.let { { vm.undoLast() } },
                                undoTrashNote = vm.undoTrashNote,
                                onStartBuilder = {
                                    val route = vm.beginCreateIntentionChat()
                                    if (route != null) {
                                        nav.navigate(route)
                                    }
                                },
                                onOpenTriage = { nav.navigate("triage") },
                                rawThoughtCount = vm.rawThoughtCount(),
                                openErrands = vm.openErrands(),
                                errandNote = vm.errandNote,
                                onAddQuickTodo = if (vm.canAddQuickTodo()) {
                                    { showErrandDialog = true }
                                } else {
                                    null
                                },
                                onToggleErrand = { vm.toggleErrand(it) },
                                onSettings = { nav.navigate("settings") },
                                overflowItems = listOf(
                                    OverflowMenuItem("Settings") {
                                        nav.navigate("settings")
                                    },
                                    OverflowMenuItem(HistoryTrashMenus.MENU_HISTORY) {
                                        nav.navigate("history")
                                    },
                                    OverflowMenuItem(HistoryTrashMenus.MENU_TRASH) {
                                        nav.navigate("trash")
                                    }
                                ),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("history") {
                            HistoryScreen(
                                rows = vm.historyRows(),
                                onUndoLast = vm.lastUndoableTransaction()?.let { { vm.undoLast() } },
                                undoNote = vm.undoTrashNote,
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("trash") {
                            TrashScreen(
                                rows = vm.trashRows(),
                                onRestore = { id -> vm.restoreTrashItem(id) },
                                onPurge = { id -> vm.purgeTrashItem(id) },
                                note = vm.undoTrashNote,
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("settings") {
                            SettingsScreen(
                                versionName = BuildConfig.VERSION_NAME,
                                schemaVersion = SCHEMA_VERSION,
                                onExportSnapshot = {
                                    val json = vm.exportSnapshotJson()
                                    val file = SnapshotExport.writeExportFile(cacheDir, json)
                                    startActivity(SnapshotExport.shareIntent(this@MainActivity, file))
                                },
                                onExportTroubleshooting = {
                                    val now = System.currentTimeMillis()
                                    val json = vm.exportTroubleshootingJson(
                                        appVersionName = BuildConfig.VERSION_NAME,
                                        nowEpochMs = now
                                    )
                                    val file = SnapshotExport.writeExportFile(
                                        cacheDir = cacheDir,
                                        json = json,
                                        nowEpochMs = now,
                                        exportFileName = SnapshotExport.troubleshootingFileName(now)
                                    )
                                    startActivity(
                                        SnapshotExport.shareTroubleshootingIntent(
                                            this@MainActivity,
                                            file
                                        )
                                    )
                                },
                                onImportSnapshot = {
                                    importSnapshotPicker.launch(SnapshotExport.MIME_TYPE)
                                },
                                snapshotImportNote = vm.snapshotImportNote,
                                onOpenHistory = {
                                    nav.navigate("history")
                                },
                                onOpenConnections = { nav.navigate("connections") },
                                themePreference = vm.themePref,
                                onThemePreferenceChange = { vm.setThemePreference(it) },
                                reducedMotionPreference = vm.reducedMotionPref,
                                onReducedMotionPreferenceChange = {
                                    vm.setReducedMotionPreference(it)
                                },
                                animationBudgetStatusLabel =
                                    AnimationBudgetGate.settingsCopy().statusLabel,
                                animationBudgetHonestyNote =
                                    AnimationBudgetGate.settingsCopy().body,
                                quietModeStatusLabel = vm.quietModeSettingsCopy().statusLabel,
                                quietModeHonestyNote = vm.quietModeSettingsCopy().body,
                                showQuietSilenceToggle = vm.quietModeSettingsCopy().showSilenceToggle,
                                showOpenDndSettings = vm.quietModeSettingsCopy().canOpenDndSettings,
                                onOpenDndSettings = {
                                    val action = vm.quietModeSettingsCopy().dndSettingsAction
                                    startActivity(
                                        Intent(action).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                liveLlmKeyPresent = vm.liveLlmObservation.keyConfigured,
                                liveLlmOptedIn = vm.liveLlmObservation.userOptedIn,
                                liveLlmStatusLabel = vm.discussSourceLabel(),
                                liveLlmHonestyNote = vm.discussHonestyNote(),
                                onLiveLlmOptInChange = { vm.setLiveLlmOptedIn(it) },
                                onSaveRuntimeApiKey = { vm.saveRuntimeOpenAiApiKey(it) },
                                onClearRuntimeApiKey = { vm.clearRuntimeOpenAiApiKey() },
                                onBack = {
                                    vm.clearSnapshotImportNote()
                                    nav.popBackStack()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("connections") {
                            ConnectionsScreen(
                                usages = vm.connectionUsages(),
                                unlinkNote = vm.connectionUnlinkNote,
                                onUnlink = { id -> vm.unlinkConnection(id) },
                                onOpenPlayStore = { pkg, label ->
                                    val offer = vm.playStoreOffer(pkg, label)
                                    val market = Intent(Intent.ACTION_VIEW, Uri.parse(offer.marketUri))
                                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                    try {
                                        startActivity(market)
                                    } catch (_: Exception) {
                                        startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(offer.httpsUri))
                                                .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                                        )
                                    }
                                },
                                screenTimeBarVisible = vm.screenTimeBarPrefVisible,
                                onScreenTimeBarVisibleChange = { visible ->
                                    vm.setScreenTimeBarVisible(visible)
                                },
                                usageAccessGranted =
                                    vm.screenTimeBarObservation?.permissionGranted == true ||
                                        vm.usageObservation?.permissionGranted == true,
                                onAllowUsageAccess = {
                                    startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                onBack = {
                                    vm.clearConnectionUnlinkNote()
                                    nav.popBackStack()
                                },
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
                                layouts = vm.drawerCardLayouts(),
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
                                onUpdateCardLayout = { id, span, priority ->
                                    vm.updateDrawerCardLayout(id, span, priority)
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
                                overflowItems = listOf(
                                    OverflowMenuItem(HistoryTrashMenus.MENU_HISTORY) {
                                        nav.navigate("history")
                                    },
                                    OverflowMenuItem(HistoryTrashMenus.MENU_TRASH) {
                                        nav.navigate("trash")
                                    }
                                ),
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
                                connectionUsages = vm.connectionUsagesForActive(),
                                onOpenConnections = { nav.navigate("connections") },
                                hierarchy = vm.hierarchyForActive(),
                                onOpenRelatedIntention = { id -> vm.openRelatedIntention(id) },
                                onWorkThisOut = {
                                    val reconfirmOpen = vm.snapshot.openQuestions.any {
                                        it.intentionId == active?.id &&
                                            it.purpose == "reconfirm_move_estimate" &&
                                            it.state != com.intent.launcher.domain.OpenQuestionState.ANSWERED &&
                                            it.state != com.intent.launcher.domain.OpenQuestionState.DECLINED
                                    }
                                    when {
                                        reconfirmOpen -> nav.navigate("conversation")
                                        active?.phase == IntentionPhase.PROPOSAL_PENDING ->
                                            nav.navigate("proposal")
                                        else -> nav.navigate("conversation")
                                    }
                                },
                                onOpenWhatWeKnow = { nav.navigate("know") },
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
                                approaches = vm.snapshot.approaches.filter {
                                    it.intentionId == active?.id
                                },
                                openQuestions = vm.snapshot.openQuestions.filter {
                                    it.intentionId == active?.id
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("know") {
                            val active = vm.activeIntention()
                            WhatWeKnowScreen(
                                facts = vm.snapshot.facts.filter { it.intentionId == active?.id },
                                hypotheses = vm.snapshot.hypotheses.filter {
                                    it.intentionId == active?.id
                                },
                                evidence = vm.snapshot.evidence.filter {
                                    it.intentionId == active?.id
                                },
                                defaultFacts = intentionProjection.defaultFacts,
                                onBack = { nav.popBackStack() },
                                onEditFact = { nav.navigate("conversation") },
                                onAddFact = { nav.navigate("conversation") },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("tracker") {
                            TrackerScreen(
                                intentionTitle = vm.activeIntention()?.title.orEmpty(),
                                modules = vm.trackerModules(),
                                entries = vm.trackerEntriesForActive(),
                                honestyNote = vm.trackerHonestyNote(),
                                onRecord = { moduleId, value, eventAt ->
                                    vm.recordTrackerEntry(
                                        moduleId = moduleId,
                                        valueText = value,
                                        eventAtEpochMs = eventAt
                                    )
                                },
                                onCorrect = { priorId, value, eventAt ->
                                    vm.correctTrackerEntry(
                                        priorEntryId = priorId,
                                        valueText = value,
                                        eventTimeEpochMs = eventAt
                                    )
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
                                parentRollup = vm.parentRollup(),
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("conversation") {
                            val active = vm.activeIntention()
                            val convProjection = ShapeProjection.project(intention = active)
                            val activeId = active?.id
                            val relatedSaidFact = pending?.basedOnFactIds?.firstOrNull()?.let { factId ->
                                vm.snapshot.facts.firstOrNull { it.id == factId }
                            } ?: vm.snapshot.facts
                                .filter { it.intentionId == activeId }
                                .maxByOrNull { it.provenance.learnedAtEpochMs }
                            val intentionTurns =
                                vm.snapshot.turns.filter { it.intentionId == activeId }
                            val hasRecordedBlock = intentionTurns.any { turn ->
                                turn.blocks.any {
                                    it.active && it.type == ResponseBlockType.RECORDED_ENTRY
                                }
                            }
                            val latestTracker = vm.snapshot.trackerEntries
                                .filter {
                                    it.intentionId == activeId &&
                                        it.status == TrackerEntryStatus.ACTIVE
                                }
                                .maxByOrNull { it.recordedAtEpochMs }
                                ?.takeIf { hasRecordedBlock || it.provenance.kind.name == "SAID" }
                            val hasReportBlock = intentionTurns.any { turn ->
                                turn.blocks.any {
                                    it.active && it.type == ResponseBlockType.REPORT_PREVIEW
                                }
                            }
                            val proof = vm.progressProof()
                            val hasProgressRows = proof != null &&
                                (proof.saidCount + proof.observedCount + proof.inferredCount) > 0
                            ConversationScreen(
                                turns = intentionTurns,
                                chips = listOfNotNull(vm.askContextChip) + vm.conversationChips(),
                                intentionTitle = active?.title.orEmpty(),
                                onChip = { chip ->
                                    if (chip == vm.askContextChip) {
                                        vm.clearAskContextChip()
                                    } else {
                                        vm.clearAskContextChip()
                                        vm.onConversationChip(chip)
                                        // Stay on Conversation — proposal card renders in-thread.
                                    }
                                },
                                onAmbiguousChoice = { blockId, choiceIndex ->
                                    vm.onAmbiguousChoice(blockId, choiceIndex)
                                },
                                onGatewayStatusAction = { blockId, actionIndex ->
                                    vm.onGatewayStatusAction(blockId, actionIndex)
                                },
                                onRelationAction = { blockId, actionIndex ->
                                    vm.onRelationAction(blockId, actionIndex)
                                },
                                onBack = { nav.popBackStack() },
                                onOpenProposal = { nav.navigate("proposal") },
                                hasPendingProposal = pending != null,
                                conversationEyebrow = convProjection.conversationEyebrow,
                                conversationHeadline = convProjection.conversationHeadline,
                                discussSourceLabel = vm.discussSourceLabel(),
                                discussHonestyNote = vm.discussHonestyNote(),
                                voiceNote = vm.voicePathNote ?: vm.voiceObservation.note,
                                voiceEnabled = vm.canStartVoiceCapture() || vm.isAssistantSpeaking,
                                onMicClick = { onConversationMicClick() },
                                onSendFreeText = { vm.onDiscussFreeText(it) },
                                freeTextBusy = vm.freeTextBusy,
                                onCancelThinking = { vm.cancelDiscussThinking() },
                                assistantSpeaking = vm.isAssistantSpeaking,
                                onStopSpeaking = { vm.stopAssistantSpeech() },
                                onSpeakAloud = { vm.beginSpeakingLatestAgentReply() },
                                canSpeakAloud = vm.speakableAgentReply() != null &&
                                    !vm.isAssistantSpeaking,
                                pendingProposal = pending,
                                relatedSaidFact = relatedSaidFact,
                                nextAction = vm.nextAction,
                                latestTrackerEntry = latestTracker,
                                pathSummary = vm.pathSummary(),
                                progressProof = proof,
                                showReportPreview = hasReportBlock || hasProgressRows,
                                intentions = vm.snapshot.intentions,
                                hierarchy = vm.hierarchyForActive(),
                                onBeginNext = {
                                    vm.beginSession()
                                    nav.navigate("home") {
                                        popUpTo("home") { inclusive = true }
                                    }
                                },
                                onLaterNext = { vm.requestDefer() },
                                onDiscussNext = { /* already on Conversation */ },
                                onDeclineProposal = { vm.declineProposal() },
                                onOpenReports = { nav.navigate("reports") },
                                onEditRecorded = {
                                    nav.navigate("tracker")
                                },
                                onUndoRecorded = { entryId ->
                                    if (entryId != null) {
                                        vm.deleteTrackerEntry(entryId)
                                    } else {
                                        vm.undoLast()
                                    }
                                },
                                onBuilderQuantity = { value, unit ->
                                    vm.onBuilderQuantity(value, unit)
                                },
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
                                onKeepDiscussing = {
                                    vm.keepDiscussingProposal()
                                    nav.popBackStack()
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
        FloatingAgentService.sync(
            context = this,
            state = vm.floatingBubbleState(),
            provenanceLabel = vm.floatingPresence.interventionProvenanceLabel,
            attentionLabel = vm.floatingPresence.interventionAttentionLine
                ?: vm.floatingPresence.label.takeIf {
                    vm.floatingPresence.interventionProvenanceLabel != null
                },
            interventionJobId = vm.floatingPresence.interventionJobId,
            interventionTurnId = vm.floatingPresence.interventionTurnId
        )
    }

    private fun consumeCompanionIntent(intent: Intent?) {
        when (intent?.action) {
            FloatingAgentService.ACTION_OPEN_CONVERSATION -> {
                val jobId = intent.getStringExtra(FloatingAgentService.EXTRA_INTERVENTION_JOB_ID)
                val turnId = intent.getStringExtra(FloatingAgentService.EXTRA_INTERVENTION_TURN_ID)
                if (!jobId.isNullOrBlank() || !turnId.isNullOrBlank()) {
                    vm.focusInterventionFromNotification(jobId = jobId, turnId = turnId)
                }
                openConversationRequest++
            }
            FloatingAgentService.ACTION_OPEN_DRAWER -> showFloaterDrawer = true
            FloatingAgentService.ACTION_CAPTURE_THOUGHT -> {
                thoughtCaptureSource = CaptureThought.SOURCE_FLOATER
                showThoughtCapture = true
            }
            FloatingAgentService.ACTION_OPEN_TODAY -> openTodayFromDrawerRequest++
            FloatingAgentService.ACTION_OPEN_HISTORY -> openHistoryFromDrawerRequest++
            SpeechTranscriptInject.ACTION -> {
                val transcript = SpeechTranscriptInject.transcriptFromIntent(
                    action = intent.action,
                    transcriptExtra = intent.getStringExtra(SpeechTranscriptInject.EXTRA_TRANSCRIPT)
                )
                if (transcript != null) {
                    openConversationRequest++
                    vm.onVoiceTranscript(transcript, injected = true)
                } else {
                    vm.onVoiceUnavailable(
                        "${SpeechTranscriptInject.HONESTY_PREFIX} Empty transcript — nothing injected."
                    )
                }
            }
        }
    }

    /** Commit Keep schedule / Defer after what-happened (+ optional thought triage). */
    private fun finishEveningCheckOut() {
        vm.completeDailyCheckOut(
            whatHappened = pendingWhatHappened,
            deferTomorrow = pendingCheckOutDefer
        )
        pendingWhatHappened = ""
        pendingCheckOutDefer = false
    }

    private fun onConversationMicClick() {
        speechForThoughtCapture = false
        vm.refresh()
        when {
            vm.isAssistantSpeaking && vm.canStartVoiceCapture() ->
                launchSpeechRecognition(prompt = "Speak to interrupt")
            vm.isAssistantSpeaking && vm.voiceNeedsPermission() ->
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            vm.isAssistantSpeaking ->
                // No STT — Stop still cancels; do not invent a silent OEM barge-in.
                vm.stopAssistantSpeech()
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
