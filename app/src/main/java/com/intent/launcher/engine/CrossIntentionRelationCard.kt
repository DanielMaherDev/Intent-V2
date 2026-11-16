package com.intent.launcher.engine

import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore

/**
 * Conversation relation-card projection for §17 case 20 ([CrossIntentionLink]).
 * Surfaces exact parent/child proposals and already-linked pointers only —
 * never invents dependency edges or OBSERVED facts.
 */
object CrossIntentionRelationCard {

    const val SOURCE = CrossIntentionLink.SOURCE_LABEL
    const val ACTION_CONFIRM = "Confirm relation"
    const val ACTION_CANCEL = "Cancel"

    data class Model(
        val title: String,
        val body: String,
        val childTitle: String,
        val parentTitle: String,
        val relationLine: String,
        val honestyNote: String,
        val confirmLabel: String,
        val cancelLabel: String,
        val childIntentionId: String,
        val parentIntentionId: String,
        val blockId: String,
        val pending: Boolean
    )

    data class RelatedStrip(
        val eyebrow: String,
        val lines: List<String>,
        val honestyNote: String
    )

    fun fromBlock(
        block: ResponseBlock,
        intentions: List<Intention>
    ): Model? {
        if (block.type != ResponseBlockType.PROPOSAL || !block.active) return null
        if (!block.sourceLabel.startsWith(SOURCE)) return null
        val childId = block.linkedObjectIds.getOrNull(0) ?: return null
        val parentId = block.linkedObjectIds.getOrNull(1) ?: return null
        val byId = intentions.associateBy { it.id }
        val child = byId[childId] ?: return null
        val parent = byId[parentId] ?: return null
        val pending = child.parentIntentionId != parentId
        return Model(
            title = block.title.ifBlank { "Exact relation proposal" },
            body = block.body,
            childTitle = child.title,
            parentTitle = parent.title,
            relationLine = "${child.title} → parent ${parent.title}",
            honestyNote = if (pending) {
                "PROPOSED · local parent pointer only — not a dependency graph. " +
                    "Nothing is linked until you confirm."
            } else {
                "Relation already applied — local parent pointer only."
            },
            confirmLabel = block.actions.getOrNull(0) ?: ACTION_CONFIRM,
            cancelLabel = block.actions.getOrNull(1) ?: ACTION_CANCEL,
            childIntentionId = childId,
            parentIntentionId = parentId,
            blockId = block.blockId,
            pending = pending
        )
    }

    fun fromTurnBlocks(
        blocks: List<ResponseBlock>,
        intentions: List<Intention>
    ): Model? =
        blocks.asSequence()
            .mapNotNull { fromBlock(it, intentions) }
            .firstOrNull { it.pending }
            ?: blocks.asSequence()
                .mapNotNull { fromBlock(it, intentions) }
                .firstOrNull()

    /**
     * Read-only strip for already-stored parent/child pointers on the active intention.
     * Empty when no local links exist — never invents related goals.
     */
    fun relatedStrip(hierarchy: IntentionHierarchy.View): RelatedStrip? {
        val parentCrumbs = hierarchy.crumbs.filter { !it.isCurrent }
        val children = hierarchy.children
        if (parentCrumbs.isEmpty() && children.isEmpty()) return null
        val lines = buildList {
            parentCrumbs.lastOrNull()?.let { add("Parent · ${it.title}") }
            children.forEach { add("Child · ${it.title}") }
        }
        if (lines.isEmpty()) return null
        return RelatedStrip(
            eyebrow = "RELATED INTENTIONS",
            lines = lines,
            honestyNote = hierarchy.honestyNote
        )
    }

    /**
     * Confirm or cancel a pending cross-intention relation from the Conversation card.
     * Returns null when the block is not a relation proposal or the action is unknown.
     */
    fun onAction(
        store: IntentionFlowStore,
        block: ResponseBlock,
        actionIndex: Int,
        nowEpochMs: Long
    ): CrossIntentionLink.ProposalView? {
        val snap = store.snapshot()
        val model = fromBlock(block, snap.intentions) ?: return null
        if (!model.pending) return null
        return when (actionIndex) {
            0 -> CrossIntentionLink.confirm(
                store = store,
                childIntentionId = model.childIntentionId,
                parentIntentionId = model.parentIntentionId,
                nowEpochMs = nowEpochMs
            )
            1 -> CrossIntentionLink.cancel(
                store = store,
                childIntentionId = model.childIntentionId,
                nowEpochMs = nowEpochMs
            )
            else -> null
        }
    }
}
