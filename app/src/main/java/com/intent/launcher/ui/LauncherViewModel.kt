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
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.store.IntentionFlowStore

class LauncherViewModel(
    private val store: IntentionFlowStore = IntentApp.instance.store,
    private val capabilityRegistry: CapabilityRegistry = CapabilityRegistry.default
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
        NextActionEngine.recommend(
            store.snapshot(),
            System.currentTimeMillis(),
            registry = capabilityRegistry
        )

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
     * Begin the recommended (or given) scheduled session via the command path.
     * No-op when begin_session is locked or the session is already in progress.
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
        return snapshot.sessionRuns.any {
            it.sessionId == id && it.status == SessionRunStatus.IN_PROGRESS
        }
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
        refresh()
        return true
    }
}
