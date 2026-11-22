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
            is FlowCommand.SoftPauseSessionRun ->
                Reversibility(true, "Soft-paused session run ${command.runId}")
            is FlowCommand.ResumeSessionRun ->
                Reversibility(true, "Resumed session run ${command.runId}")
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
            is FlowCommand.SetParentIntention ->
                Reversibility(
                    true,
                    if (command.parentIntentionId == null) {
                        "Cleared parent for intention ${command.intentionId}"
                    } else {
                        "Linked intention ${command.intentionId} under ${command.parentIntentionId}"
                    }
                )
            is FlowCommand.MarkPhase ->
                Reversibility(true, "Marked phase for ${command.intentionId}")
            is FlowCommand.AddConversationTurn -> {
                // §8.10 Active-schedule projection is a companion of Approve — not a
                // separate Undo target (Gate E3 keeps last-undoable on ApproveProposal).
                val isActiveSchedule = command.blocks.any {
                    it.sourceLabel == CrossSurfaceContinuity.ACTIVE_SCHEDULE_SOURCE
                }
                if (isActiveSchedule) {
                    Reversibility(
                        reversible = false,
                        summary = "Projected active schedule ${command.turnId}",
                        irreversibleReason = null
                    )
                } else {
                    Reversibility(true, "Added conversation turn ${command.turnId}")
                }
            }
            is FlowCommand.CaptureThought ->
                Reversibility(true, "Captured thought ${command.thoughtId}")
            is FlowCommand.CaptureErrand ->
                Reversibility(true, "Captured errand ${command.todoId}")
            is FlowCommand.CompleteErrand ->
                Reversibility(true, "Completed errand ${command.todoId}")
            is FlowCommand.ReopenErrand ->
                Reversibility(true, "Reopened errand ${command.todoId}")
            is FlowCommand.RecordObservedEvidence ->
                Reversibility(true, "Recorded observed evidence ${command.evidenceId}")
            is FlowCommand.DismissEvidence ->
                Reversibility(true, "Dismissed evidence ${command.evidenceId}")
            is FlowCommand.ConfirmDailyCheckIn ->
                Reversibility(true, "Confirmed daily check-in for ${command.localDate}")
            is FlowCommand.RecordMiddayCheck ->
                Reversibility(true, "Recorded midday check for ${command.localDate}")
            is FlowCommand.CompleteDailyCheckOut ->
                Reversibility(true, "Completed daily check-out for ${command.localDate}")
            is FlowCommand.RecordTrackerEntry ->
                Reversibility(true, "Recorded tracker entry ${command.entryId}")
            is FlowCommand.CorrectTrackerEntry ->
                Reversibility(true, "Corrected tracker entry ${command.priorEntryId} → ${command.newEntryId}")
            is FlowCommand.DeleteTrackerEntry ->
                Reversibility(true, "Deleted tracker entry ${command.entryId}")
            is FlowCommand.MarkThoughtStatus ->
                Reversibility(true, "Marked thought ${command.thoughtId}")
            is FlowCommand.ClassifyThought ->
                Reversibility(
                    true,
                    "Classified thought ${command.thoughtId} as ${command.disposition.name}"
                )
            is FlowCommand.StartBuilderDraft ->
                Reversibility(true, "Started intention builder")
            is FlowCommand.SetBuilderShape ->
                Reversibility(true, "Set builder shape ${command.shapeTag}")
            is FlowCommand.AnswerBuilderPrompt ->
                Reversibility(true, "Answered builder prompt ${command.promptId}")
            is FlowCommand.CommitBuilderIntention ->
                Reversibility(true, "Committed builder intention ${command.intentionId}")
            is FlowCommand.CancelBuilderDraft ->
                Reversibility(true, "Cancelled intention builder")
            is FlowCommand.StartTriageBatch ->
                Reversibility(true, "Started triage batch ${command.batchId}")
            is FlowCommand.ConfirmTriageItem ->
                Reversibility(true, "Confirmed triage item ${command.itemId}")
            is FlowCommand.RejectTriageItem ->
                Reversibility(true, "Rejected triage item ${command.itemId}")
            is FlowCommand.ClearTriageBatch ->
                Reversibility(true, "Cleared triage batch")
            is FlowCommand.ReorderIntentions ->
                Reversibility(true, "Reordered intentions")
            is FlowCommand.HoldAppForIntention ->
                Reversibility(true, "Held app ${command.packageName} for ${command.intentionId}")
            is FlowCommand.ReorderHeldApps ->
                Reversibility(true, "Reordered held apps for ${command.intentionId}")
            is FlowCommand.ReleaseHeldApp ->
                Reversibility(true, "Released held app ${command.heldAppId}")
            is FlowCommand.UpdateDrawerCardLayout ->
                Reversibility(true, "Updated drawer card layout for ${command.intentionId}")
            is FlowCommand.SetProactivityPrefs ->
                Reversibility(true, "Updated proactivity preferences")
            is FlowCommand.ArmTriggerCooldown ->
                Reversibility(true, "Armed trigger cooldown ${command.key}")
            is FlowCommand.LeaveMeAlone ->
                Reversibility(true, "Leave me alone until ${command.untilEpochMs}")
            is FlowCommand.QueueIntervention ->
                Reversibility(true, "Queued intervention ${command.jobId}")
            is FlowCommand.MarkInterventionShown ->
                Reversibility(true, "Marked intervention shown ${command.jobId}")
            is FlowCommand.DismissIntervention ->
                Reversibility(true, "Dismissed intervention ${command.jobId}")
            is FlowCommand.SaveTypedFact ->
                Reversibility(true, "Saved typed fact ${command.factId}")
            is FlowCommand.CorrectTypedFact ->
                Reversibility(true, "Corrected fact ${command.priorFactId} → ${command.newFactId}")
            is FlowCommand.UpsertOpenQuestion ->
                Reversibility(true, "Upserted open question ${command.question.id}")
            is FlowCommand.AnswerOpenQuestion ->
                Reversibility(true, "Answered open question ${command.questionId}")
            is FlowCommand.UpsertApproach ->
                Reversibility(true, "Upserted approach ${command.approach.id}")
            is FlowCommand.SelectApproaches ->
                Reversibility(true, "Selected approaches for ${command.intentionId}")
            is FlowCommand.SetAdaptiveBuilderState ->
                Reversibility(true, "Updated adaptive builder state for ${command.state.intentionId}")
            is FlowCommand.RemapIntentionId ->
                Reversibility(
                    true,
                    "Remap intention ${command.fromIntentionId} → ${command.toIntentionId}"
                )
            is FlowCommand.DeactivateBuilderAnswerCards ->
                // Card deactivation is projection hygiene — not a user Undo target.
                Reversibility(
                    reversible = false,
                    summary = "Deactivate builder cards ${command.intentionId}",
                    irreversibleReason = null
                )
            is FlowCommand.AppendDecisionEvent ->
                // Gate F audit append — not a user Undo target.
                Reversibility(
                    reversible = false,
                    summary = "Decision trail ${command.event.decisionType} ${command.event.id}",
                    irreversibleReason = null
                )
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
