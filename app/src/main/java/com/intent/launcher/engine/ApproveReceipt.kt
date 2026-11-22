package com.intent.launcher.engine

import com.intent.launcher.domain.ConversationTurn
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.Proposal
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType

/**
 * Gate E3 — post-Approve receipt + Undo affordance.
 *
 * Receipt is written atomically with [com.intent.launcher.domain.FlowCommand.ApproveProposal]
 * so UndoLastReversible restores both the activation and the receipt turn.
 */
object ApproveReceipt {

    /**
     * Must NOT use the `adaptive_builder|` prefix — that matches
     * [BuilderAnswerCard.isBuilderCard] and §8.10 [CrossSurfaceContinuity.afterApprove]
     * would deactivate the receipt via [FlowCommand.DeactivateBuilderAnswerCards].
     */
    const val SOURCE_LABEL = "approve_receipt"

    fun turnId(nowEpochMs: Long): String = "turn-approve-receipt-$nowEpochMs"

    fun blockId(nowEpochMs: Long): String = "block-receipt-$nowEpochMs"

    fun buildTurn(
        proposal: Proposal,
        sessionId: String,
        nowEpochMs: Long
    ): ConversationTurn {
        val title = proposal.title.ifBlank { "Practice routine" }
        return ConversationTurn(
            id = turnId(nowEpochMs),
            intentionId = proposal.intentionId,
            role = "agent",
            text = "Activated: $title. First session is on your schedule — " +
                "it can appear on Home when relevant. Undo available.",
            atEpochMs = nowEpochMs,
            blocks = listOf(
                ResponseBlock(
                    blockId = blockId(nowEpochMs),
                    type = ResponseBlockType.RECORDED_ENTRY,
                    title = "Activated",
                    body = title,
                    sourceLabel = SOURCE_LABEL,
                    linkedObjectIds = listOf(proposal.id, sessionId),
                    // Edit at [0], Undo at [1] — matches AgentModuleProjection.recordedFromBlock.
                    actions = listOf("Edit", "Undo"),
                    active = true
                )
            )
        )
    }

    fun findBlock(
        snapshot: IntentionSnapshot,
        proposalId: String
    ): ResponseBlock? =
        snapshot.turns
            .asReversed()
            .flatMap { it.blocks }
            .firstOrNull {
                it.type == ResponseBlockType.RECORDED_ENTRY &&
                    it.sourceLabel == SOURCE_LABEL &&
                    proposalId in it.linkedObjectIds &&
                    it.active
            }

    fun hasReceipt(snapshot: IntentionSnapshot, proposalId: String): Boolean =
        findBlock(snapshot, proposalId) != null
}
