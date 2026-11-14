package com.intent.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.ui.LauncherViewModel
import com.intent.launcher.ui.screens.ConversationScreen
import com.intent.launcher.ui.screens.HomeScreen
import com.intent.launcher.ui.screens.IntentionSpaceScreen
import com.intent.launcher.ui.screens.ProposalReviewScreen
import com.intent.launcher.ui.theme.IntentTheme

class MainActivity : ComponentActivity() {
    private val vm: LauncherViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            IntentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val nav = rememberNavController()
                    val snap = vm.snapshot
                    val intention = vm.activeIntention()
                    val pending = snap.proposals.firstOrNull {
                        it.status == ProposalStatus.PENDING && it.intentionId == intention?.id
                    }
                    val intentionHint = when (intention?.shapeTag) {
                        "marathon" -> "Run a marathon · Base before speed"
                        "business" -> "Business idea · Stay in discovery"
                        else -> "Open your intention"
                    }
                    val showMarathonEntry = intention?.shapeTag != "marathon"
                    val showBusinessEntry = intention?.shapeTag != "business"

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
                                onDiscuss = { nav.navigate("conversation") },
                                onCompleteSession = { vm.completeActiveSession() },
                                onAbandonSession = { vm.abandonActiveSession() },
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

    override fun onResume() {
        super.onResume()
        vm.refresh()
    }
}
