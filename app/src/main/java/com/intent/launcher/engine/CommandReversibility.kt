package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.FlowTransaction

/**
 * Architecture Rule 3 / tech-arch §9: classify local commitments as undoable
 * vs honest irreversible (external / purge).
 */
data class Reversibility(
    val reversible: Boolean,
    val summary: String,
    val irreversibleReason: String? = null
)

object CommandReversibility {
    fun classify(command: FlowCommand): Reversibility =
        when (command) {
            is FlowCommand.ApproveProposal ->
                Reversibility(true, "Approved proposal ${command.proposalId}")
            is FlowCommand.DeclineProposal ->
                Reversibility(true, "Declined proposal ${command.proposalId}")
            is FlowCommand.EditProposal ->
                Reversibility(true, "Edited proposal ${command.proposalId}")
            is FlowCommand.DeferNextSession ->
                Reversibility(true, "Deferred session ${command.sessionId}")
            is FlowCommand.BeginSession ->
                Reversibility(true, "Began session ${command.sessionId}")
            is FlowCommand.CompleteSessionRun ->
                Reversibility(true, "Completed session run ${command.runId}")
            is FlowCommand.AbandonSessionRun ->
                Reversibility(true, "Abandoned session run ${command.runId}")
            is FlowCommand.SoftDeleteIntention ->
                Reversibility(true, "Moved intention ${command.intentionId} to trash")
            is FlowCommand.SoftDeleteSession ->
                Reversibility(true, "Moved session ${command.sessionId} to trash")
            is FlowCommand.RestoreTrashItem ->
                Reversibility(true, "Restored trash item ${command.trashId}")
            is FlowCommand.AddUserFact ->
                Reversibility(true, "Recorded fact ${command.factId}")
            is FlowCommand.AddHypothesis ->
                Reversibility(true, "Added hypothesis ${command.hypothesisId}")
            is FlowCommand.ProposeRecurringSession ->
                Reversibility(true, "Proposed recurring session ${command.proposalId}")
            is FlowCommand.SeedExploring ->
                Reversibility(true, "Seeded intention ${command.intentionId}")
            is FlowCommand.SetActiveIntention ->
                Reversibility(true, "Activated intention ${command.intentionId}")
            is FlowCommand.MarkPhase ->
                Reversibility(true, "Marked phase for ${command.intentionId}")
            is FlowCommand.AddConversationTurn ->
                Reversibility(true, "Added conversation turn ${command.turnId}")
            is FlowCommand.CaptureThought ->
                Reversibility(true, "Captured thought ${command.thoughtId}")
            is FlowCommand.RecordObservedEvidence ->
                Reversibility(true, "Recorded observed evidence ${command.evidenceId}")
            is FlowCommand.DismissEvidence ->
                Reversibility(true, "Dismissed evidence ${command.evidenceId}")
            is FlowCommand.PurgeTrashItem ->
                Reversibility(
                    reversible = false,
                    summary = "Purged trash item ${command.trashId}",
                    irreversibleReason = "Permanent purge cannot be undone."
                )
            is FlowCommand.RecordExternalAction ->
                Reversibility(
                    reversible = false,
                    summary = command.summary,
                    irreversibleReason = command.irreversibleReason
                )
            is FlowCommand.UndoLastReversible ->
                Reversibility(
                    reversible = false,
                    summary = "Undo last reversible commitment",
                    irreversibleReason = null
                )
        }

    fun lastUndoable(transactions: List<FlowTransaction>): FlowTransaction? =
        transactions.asReversed().firstOrNull {
            it.reversible && !it.undone && it.undoOfTransactionId == null && !it.beforeJson.isNullOrBlank()
        }
}
