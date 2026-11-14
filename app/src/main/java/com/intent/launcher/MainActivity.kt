package com.intent.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.SpanishScenario
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
                    val intention = snap.intentions.firstOrNull { it.id == SpanishScenario.INTENTION_ID }
                        ?: snap.intentions.firstOrNull()
                    val pending = snap.proposals.firstOrNull { it.status == ProposalStatus.PENDING }
                    var showBeginAck by remember { mutableStateOf(false) }

                    NavHost(navController = nav, startDestination = "home") {
                        composable("home") {
                            HomeScreen(
                                nextAction = vm.nextAction,
                                onContinueExploring = {
                                    nav.navigate("intention")
                                },
                                onOpenIntention = { nav.navigate("intention") },
                                onReviewProposal = { nav.navigate("proposal") },
                                onBegin = {
                                    showBeginAck = true
                                    vm.beginNoted()
                                },
                                onDefer = { vm.deferNext() },
                                onDiscuss = { nav.navigate("conversation") },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("intention") {
                            IntentionSpaceScreen(
                                intention = intention,
                                facts = snap.facts.filter { it.intentionId == intention?.id },
                                hypotheses = snap.hypotheses.filter { it.intentionId == intention?.id },
                                sessions = snap.sessions.filter { it.intentionId == intention?.id },
                                onWorkThisOut = { nav.navigate("conversation") },
                                onBack = { nav.popBackStack() },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        composable("conversation") {
                            ConversationScreen(
                                turns = snap.turns.filter { it.intentionId == intention?.id },
                                onChip = { chip ->
                                    vm.onConversationChip(chip)
                                    val after = vm.snapshot
                                    if (after.proposals.any { it.status == ProposalStatus.PENDING }) {
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
                            val p = vm.snapshot.proposals.firstOrNull { it.status == ProposalStatus.PENDING }
                                ?: vm.snapshot.proposals.lastOrNull()
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
