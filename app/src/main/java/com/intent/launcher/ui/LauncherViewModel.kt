package com.intent.launcher.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import com.intent.launcher.IntentApp
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.store.IntentionFlowStore

class LauncherViewModel(
    private val store: IntentionFlowStore = IntentApp.instance.store
) : ViewModel() {

    var snapshot by mutableStateOf(store.snapshot())
        private set

    var nextAction by mutableStateOf(recommend())
        private set

    fun refresh() {
        snapshot = store.snapshot()
        nextAction = recommend()
    }

    private fun recommend(): NextAction =
        NextActionEngine.recommend(store.snapshot(), System.currentTimeMillis())

    fun onConversationChip(chip: String) {
        SpanishScenario.onChip(store, chip)
        refresh()
    }

    fun approveProposal() {
        SpanishScenario.approve(store)
        refresh()
    }

    fun declineProposal() {
        SpanishScenario.decline(store)
        refresh()
    }

    fun editProposalToFriday() {
        store.apply(
            FlowCommand.EditProposal(
                proposalId = SpanishScenario.PROPOSAL_ID,
                dayOfWeek = 5,
                hour = 18,
                minute = 0,
                nowEpochMs = System.currentTimeMillis()
            )
        )
        refresh()
    }

    fun deferNext() {
        val sessionId = nextAction.sessionId ?: SpanishScenario.SESSION_ID
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

    fun beginNoted() {
        // v0.0.01: begin is acknowledged in UI; schedule unchanged
        refresh()
    }
}
