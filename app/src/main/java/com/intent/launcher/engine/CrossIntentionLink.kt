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
    const val CANCELLED_MARKER_PREFIX = "turn-relation-cancelled-"
    const val SOURCE_LABEL = "cross_intention_link"

    data class ProposalView(
        val childIntentionId: String,
        val parentIntentionId: String,
        val relationLabel: String,
        val pending: Boolean,
        val applied: Boolean,
        val honestyNote: String
    )

    data class ResolveResult(
        val childIntentionId: String?,
        val parentIntentionId: String?,
        val relationLabel: String,
        val matched: Boolean,
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
     * Match [utterance] to existing local intention titles only.
     * Never invents a parent or child id when titles are missing or ambiguous.
     */
    fun resolveFromUtterance(
        store: IntentionFlowStore,
        utterance: String,
        activeIntentionId: String? = null
    ): ResolveResult {
        val snap = store.snapshot()
        val label = utterance.trim().ifBlank { "Cross-intention relation" }
        val matches = matchTitlesInUtterance(utterance, snap.intentions)
        if (matches.size < 2) {
            return ResolveResult(
                childIntentionId = null,
                parentIntentionId = null,
                relationLabel = label,
                matched = false,
                honestyNote = "Clarification required — I only link intentions that " +
                    "already exist locally. I will not invent a parent or duplicate a goal."
            )
        }
        val lower = utterance.trim().lowercase()
        val ordered = orderChildParent(lower, matches, activeIntentionId)
            ?: return ResolveResult(
                childIntentionId = null,
                parentIntentionId = null,
                relationLabel = label,
                matched = false,
                honestyNote = "Clarification required — which intention is the child " +
                    "and which is the parent? I will not guess."
            )
        val (child, parent) = ordered
        if (!IntentionHierarchy.canSetParent(snap, child.id, parent.id)) {
            return ResolveResult(
                childIntentionId = child.id,
                parentIntentionId = parent.id,
                relationLabel = label,
                matched = false,
                honestyNote = "Clarification required — that parent/child pair is invalid " +
                    "or would create a cycle. I will not guess a parent or duplicate the intention."
            )
        }
        return ResolveResult(
            childIntentionId = child.id,
            parentIntentionId = parent.id,
            relationLabel = label,
            matched = true,
            honestyNote = "Matched existing intentions by title — relation proposed only until confirm."
        )
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
                            sourceLabel = SOURCE_LABEL,
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
                        sourceLabel = SOURCE_LABEL,
                        linkedObjectIds = listOf(childIntentionId, parentIntentionId),
                        actions = listOf(
                            CrossIntentionRelationCard.ACTION_CONFIRM,
                            CrossIntentionRelationCard.ACTION_CANCEL
                        ),
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
                        sourceLabel = SOURCE_LABEL,
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

    /**
     * Dismiss a pending relation proposal without setting a parent pointer.
     */
    fun cancel(
        store: IntentionFlowStore,
        childIntentionId: String,
        nowEpochMs: Long
    ): ProposalView {
        val child = store.snapshot().intentions.firstOrNull { it.id == childIntentionId }
        val parentId = child?.parentIntentionId.orEmpty()
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "$CANCELLED_MARKER_PREFIX$childIntentionId",
                intentionId = childIntentionId,
                role = "agent",
                text = "Relation proposal cancelled — no parent pointer was set.",
                nowEpochMs = nowEpochMs,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-relation-cancelled-$nowEpochMs",
                        type = ResponseBlockType.RECORDED_ENTRY,
                        title = "Relation cancelled",
                        body = "No parent link applied for $childIntentionId",
                        sourceLabel = SOURCE_LABEL,
                        linkedObjectIds = listOf(childIntentionId),
                        actions = emptyList(),
                        active = true
                    )
                )
            )
        )
        return ProposalView(
            childIntentionId = childIntentionId,
            parentIntentionId = parentId,
            relationLabel = store.snapshot().facts.firstOrNull {
                it.id == "$RELATION_FACT_PREFIX$childIntentionId"
            }?.text ?: "parent link",
            pending = false,
            applied = false,
            honestyNote = "Relation proposal cancelled — no parent pointer set, no duplicate."
        )
    }

    fun isPending(store: IntentionFlowStore, childIntentionId: String): Boolean {
        val marker = "$PENDING_MARKER_PREFIX$childIntentionId"
        val cancelled = "$CANCELLED_MARKER_PREFIX$childIntentionId"
        val snap = store.snapshot()
        val child = snap.intentions.firstOrNull { it.id == childIntentionId } ?: return false
        if (child.parentIntentionId != null) return false
        if (snap.turns.none { it.id == marker }) return false
        if (snap.turns.any { it.id == cancelled }) return false
        return true
    }

    private fun matchTitlesInUtterance(
        utterance: String,
        intentions: List<com.intent.launcher.domain.Intention>
    ): List<com.intent.launcher.domain.Intention> {
        val lower = utterance.trim().lowercase()
        return intentions
            .filter { it.title.isNotBlank() && lower.contains(it.title.trim().lowercase()) }
            .sortedByDescending { it.title.trim().length }
            .distinctBy { it.id }
    }

    private fun orderChildParent(
        lowerUtterance: String,
        matches: List<com.intent.launcher.domain.Intention>,
        activeIntentionId: String?
    ): Pair<com.intent.launcher.domain.Intention, com.intent.launcher.domain.Intention>? {
        if (matches.size < 2) return null
        val separators = listOf(
            " is part of my ",
            " is part of ",
            " part of my ",
            " part of ",
            " under my ",
            " belongs to ",
            " child of ",
            " parent of "
        )
        for (sep in separators) {
            val idx = lowerUtterance.indexOf(sep)
            if (idx < 0) continue
            val before = lowerUtterance.substring(0, idx)
            val after = lowerUtterance.substring(idx + sep.length)
            val beforeHit = matches.firstOrNull { before.contains(it.title.trim().lowercase()) }
            val afterHit = matches.firstOrNull {
                it.id != beforeHit?.id && after.contains(it.title.trim().lowercase())
            }
            if (beforeHit != null && afterHit != null) {
                return if (sep.trim() == "parent of") {
                    // "Health is parent of Gym" → child=Gym, parent=Health
                    afterHit to beforeHit
                } else {
                    beforeHit to afterHit
                }
            }
        }
        // Fallback: active intention as child when exactly two title hits.
        if (matches.size == 2 && activeIntentionId != null) {
            val child = matches.firstOrNull { it.id == activeIntentionId } ?: return null
            val parent = matches.firstOrNull { it.id != child.id } ?: return null
            return child to parent
        }
        return null
    }
}
