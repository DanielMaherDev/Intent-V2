package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore

/**
 * §17 cases 26–28 — cancel, idempotent retry helpers, process-death proposal recovery.
 */
object AgentTurnLifecycle {

    fun cancelMarkerTurnId(turnId: String): String = "turn-cancel-marker-$turnId"

    fun isCancelled(snapshot: IntentionSnapshot, turnId: String): Boolean {
        val markerId = cancelMarkerTurnId(turnId)
        return snapshot.turns.any { turn ->
            turn.id == markerId ||
                turn.blocks.any { block ->
                    block.type == ResponseBlockType.ERROR &&
                        block.title.equals("Cancelled", ignoreCase = true) &&
                        (turnId in block.linkedObjectIds || block.sourceLabel == "turn_cancellation")
                }
        }
    }

    /**
     * After process death, reopen the same file and confirm a pending proposal
     * is still PENDING at the original revision — never auto-approved.
     */
    data class ProposalRecovery(
        val proposalId: String,
        val status: ProposalStatus,
        val phaseLabel: String,
        val recovered: Boolean,
        val honestyNote: String
    )

    fun recoverPendingProposal(
        fileStoreFactory: (java.io.File) -> IntentionFlowStore,
        file: java.io.File,
        proposalId: String
    ): ProposalRecovery {
        val reopened = fileStoreFactory(file)
        val snap = reopened.snapshot()
        val proposal = snap.proposals.firstOrNull { it.id == proposalId }
        return if (proposal == null) {
            ProposalRecovery(
                proposalId = proposalId,
                status = ProposalStatus.DECLINED,
                phaseLabel = "missing",
                recovered = false,
                honestyNote = "Proposal not found after restart — context was lost, not auto-approved."
            )
        } else {
            val intention = snap.intentions.firstOrNull { it.id == proposal.intentionId }
            ProposalRecovery(
                proposalId = proposal.id,
                status = proposal.status,
                phaseLabel = intention?.phase?.name ?: "unknown",
                recovered = proposal.status == ProposalStatus.PENDING,
                honestyNote = if (proposal.status == ProposalStatus.PENDING) {
                    "Pending proposal recovered after process death — original revision intact; not auto-approved."
                } else {
                    "Proposal status after restart is ${proposal.status} — not silently rewritten."
                }
            )
        }
    }
}
