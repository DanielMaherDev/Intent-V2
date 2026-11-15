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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ShapeProjection
import com.intent.launcher.floating.FloatingAgentService
import com.intent.launcher.platform.AndroidSpeechToTextAccess
import com.intent.launcher.ui.LauncherViewModel
import com.intent.launcher.ui.screens.ConversationScreen
import com.intent.launcher.ui.screens.HomeScreen
import com.intent.launcher.ui.screens.IntentionSpaceScreen
import com.intent.launcher.ui.screens.ProposalReviewScreen
import com.intent.launcher.ui.theme.IntentTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    private var openConversationRequest by mutableIntStateOf(0)

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        vm.refresh()
        if (granted && vm.canStartVoiceCapture()) {
            launchSpeechRecognition()
        } else {
            vm.onMicDenied()
        }
    }

    private val speechRecognition = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            vm.onVoiceUnavailable(
                "Speech capture cancelled — use chips for the same Conversation path."
            )
            return@registerForActivityResult
        }
        val matches = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            .orEmpty()
        val best = matches.firstOrNull().orEmpty()
        if (best.isBlank()) {
            vm.onVoiceUnavailable("No speech recognized — try again or tap a chip.")
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
                    LaunchedEffect(pendingOpenProposalAfterVoice) {
                        if (pendingOpenProposalAfterVoice > 0) {
                            nav.navigate("proposal")
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
                                onReviewProposal = { nav.navigate("proposal") },
                                onBegin = { vm.beginSession() },
                                onDefer = { vm.deferNext() },
                                onDiscuss = {
                                    val route = vm.openDiscussFromHome()
                                    if (route != null) {
                                        nav.navigate(route)
                                    }
                                },
                                onCompleteSession = { vm.completeActiveSession() },
                                onAbandonSession = { vm.abandonActiveSession() },
                                onOpenDestination = { vm.launchDestinationApp() },
                                destinationLaunchNote = vm.destinationLaunchNote,
                                usageNote = vm.usageObservation?.note,
                                showGrantUsageAccess = vm.usageObservation?.canOpenSettings == true,
                                onGrantUsageAccess = {
                                    startActivity(
                                        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                overlayNote = vm.overlayObservation?.note,
                                showGrantOverlay = vm.overlayObservation?.canOpenSettings == true,
                                onGrantOverlay = {
                                    startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:$packageName")
                                        ).apply {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    )
                                },
                                floatingCompanionLabel = vm.floatingPresence.state?.let {
                                    vm.floatingPresence.label
                                },
                                floatingManualRouteNote = vm.floatingPresence.manualRouteNote,
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
                                onWorkThisOut = {
                                    when (active?.phase) {
                                        IntentionPhase.PROPOSAL_PENDING -> nav.navigate("proposal")
                                        else -> nav.navigate("conversation")
                                    }
                                },
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("conversation") {
                            val active = vm.activeIntention()
                            val convProjection = ShapeProjection.project(intention = active)
                            ConversationScreen(
                                turns = vm.snapshot.turns.filter { it.intentionId == active?.id },
                                chips = vm.conversationChips(),
                                intentionTitle = active?.title.orEmpty(),
                                onChip = { chip ->
                                    vm.onConversationChip(chip)
                                    val after = vm.snapshot
                                    if (after.proposals.any {
                                            it.status == ProposalStatus.PENDING &&
                                                it.intentionId == active?.id
                                        }
                                    ) {
                                        nav.navigate("proposal")
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
        if (intent?.action == FloatingAgentService.ACTION_OPEN_CONVERSATION) {
            openConversationRequest++
        }
    }

    private fun onConversationMicClick() {
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

    private fun launchSpeechRecognition() {
        try {
            speechRecognition.launch(
                AndroidSpeechToTextAccess.listenIntent(
                    prompt = "Speak a conversation choice"
                )
            )
        } catch (_: Exception) {
            vm.onVoiceUnavailable(
                "Could not start speech recognition — use chips instead."
            )
        }
    }
}
