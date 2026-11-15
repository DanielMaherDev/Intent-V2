package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore

/**
 * §17 case 20 — cross-intention link (“Gym is part of my Health goal.”).
 * Exact relation proposal (or clarification) before [FlowCommand.SetParentIntention].
 * Must not duplicate the child or guess a parent silently.
 */
object CrossIntentionLink {

    const val RELATION_FACT_PREFIX = "fact-relation-"
    const val PENDING_MARKER_PREFIX = "turn-relation-pending-"

    data class ProposalView(
        val childIntentionId: String,
        val parentIntentionId: String,
        val relationLabel: String,
        val pending: Boolean,
        val applied: Boolean,
        val honestyNote: String
    )

    fun looksLikeRelationClaim(utterance: String): Boolean {
        val t = utterance.trim().lowercase()
        return t.contains("part of") ||
            t.contains("under my") ||
            t.contains("belongs to") ||
            (t.contains("child of") || t.contains("parent of"))
    }

    /**
     * Propose setting [childIntentionId]'s parent to [parentIntentionId] without applying yet.
     */
    fun propose(
        store: IntentionFlowStore,
        childIntentionId: String,
        parentIntentionId: String,
        relationLabel: String,
        nowEpochMs: Long,
        utterance: String = relationLabel
    ): ProposalView {
        val snap = store.snapshot()
        if (!IntentionHierarchy.canSetParent(snap, childIntentionId, parentIntentionId)) {
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-relation-clarify-$nowEpochMs",
                    intentionId = childIntentionId,
                    role = "agent",
                    text = "I need clarification before linking — that parent/child pair is invalid " +
                        "or would create a cycle. I will not guess a parent or duplicate the intention.",
                    nowEpochMs = nowEpochMs,
                    blocks = listOf(
                        ResponseBlock(
                            blockId = "block-relation-clarify-$nowEpochMs",
                            type = ResponseBlockType.QUESTION,
                            title = "Clarify relation",
                            body = "Exact parent and child ids required",
                            sourceLabel = "cross_intention_link",
                            linkedObjectIds = listOf(childIntentionId, parentIntentionId),
                            actions = emptyList(),
                            active = true
                        )
                    )
                )
            )
            return ProposalView(
                childIntentionId = childIntentionId,
                parentIntentionId = parentIntentionId,
                relationLabel = relationLabel,
                pending = false,
                applied = false,
                honestyNote = "Clarification required — no guessed parent or duplicate."
            )
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-relation-user-$nowEpochMs",
                intentionId = childIntentionId,
                role = "user",
                text = utterance,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddUserFact(
                factId = "$RELATION_FACT_PREFIX$childIntentionId",
                intentionId = childIntentionId,
                text = relationLabel,
                nowEpochMs = nowEpochMs
            )
        )
        val markerId = "$PENDING_MARKER_PREFIX$childIntentionId"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = markerId,
                intentionId = childIntentionId,
                role = "agent",
                text = "Proposed exact relation: “$relationLabel” — " +
                    "set parent of $childIntentionId to $parentIntentionId. Confirm to apply.",
                nowEpochMs = nowEpochMs + 1,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-relation-$nowEpochMs",
                        type = ResponseBlockType.PROPOSAL,
                        title = "Exact relation proposal",
                        body = relationLabel,
                        sourceLabel = "cross_intention_link",
                        linkedObjectIds = listOf(childIntentionId, parentIntentionId),
                        actions = listOf("Confirm relation", "Cancel"),
                        active = true
                    )
                )
            )
        )
        val child = store.snapshot().intentions.first { it.id == childIntentionId }
        return ProposalView(
            childIntentionId = childIntentionId,
            parentIntentionId = parentIntentionId,
            relationLabel = relationLabel,
            pending = true,
            applied = child.parentIntentionId == parentIntentionId,
            honestyNote = "Relation proposed only — parent pointer not set until confirm."
        )
    }

    fun confirm(
        store: IntentionFlowStore,
        childIntentionId: String,
        parentIntentionId: String,
        nowEpochMs: Long
    ): ProposalView {
        val beforeCount = store.snapshot().intentions.count { it.id == childIntentionId }
        store.apply(
            FlowCommand.SetParentIntention(
                intentionId = childIntentionId,
                parentIntentionId = parentIntentionId,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-relation-applied-$nowEpochMs",
                intentionId = childIntentionId,
                role = "agent",
                text = "Relation applied — local parent pointer only, not a dependency graph.",
                nowEpochMs = nowEpochMs,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-relation-applied-$nowEpochMs",
                        type = ResponseBlockType.RECORDED_ENTRY,
                        title = "Relation confirmed",
                        body = "$childIntentionId → parent $parentIntentionId",
                        sourceLabel = "cross_intention_link",
                        linkedObjectIds = listOf(childIntentionId, parentIntentionId),
                        actions = emptyList(),
                        active = true
                    )
                )
            )
        )
        val snap = store.snapshot()
        val child = snap.intentions.first { it.id == childIntentionId }
        val duplicated = snap.intentions.count { it.id == childIntentionId } > beforeCount
        return ProposalView(
            childIntentionId = childIntentionId,
            parentIntentionId = parentIntentionId,
            relationLabel = snap.facts.firstOrNull {
                it.id == "$RELATION_FACT_PREFIX$childIntentionId"
            }?.text ?: "parent link",
            pending = false,
            applied = child.parentIntentionId == parentIntentionId && !duplicated,
            honestyNote = "Exact relation confirmed via SetParent — no duplicate intention."
        )
    }

    fun isPending(store: IntentionFlowStore, childIntentionId: String): Boolean {
        val marker = "$PENDING_MARKER_PREFIX$childIntentionId"
        val child = store.snapshot().intentions.firstOrNull { it.id == childIntentionId }
            ?: return false
        return store.snapshot().turns.any { it.id == marker } &&
            child.parentIntentionId == null
    }
}
