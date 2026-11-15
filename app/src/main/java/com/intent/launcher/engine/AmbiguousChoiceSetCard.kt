package com.intent.launcher.engine

import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore

/**
 * Conversation card projection for [GatewayStatus.AMBIGUOUS] + [ResponseBlockType.CHOICE_SET].
 * Pure helpers so JVM tests can assert the card path without Compose.
 *
 * Approval ambiguity ([SOURCE_APPROVAL]): [linkedObjectIds] are proposal ids.
 * Tracker scope ambiguity ([SOURCE_SCOPE]): [linkedObjectIds] are
 * `intentionId|moduleId|valueText` (pipe-separated; valueText may contain pipes only
 * after the second separator — split limited to 3 parts).
 */
object AmbiguousChoiceSetCard {

    const val SOURCE_APPROVAL = "approval_clarification"
    const val SOURCE_SCOPE = "scope_clarification"
    const val STATUS_LABEL = "AMBIGUOUS"
    private const val LINK_SEP = '|'

    data class Choice(
        val label: String,
        val linkedObjectId: String,
        val index: Int
    )

    data class Model(
        val statusLabel: String = STATUS_LABEL,
        val title: String,
        val body: String,
        val sourceLabel: String,
        val choices: List<Choice>,
        val blockId: String
    ) {
        val eyebrow: String
            get() = "$statusLabel · ${title.ifBlank { "Choose one" }}"
    }

    fun fromBlock(block: ResponseBlock): Model? {
        if (block.type != ResponseBlockType.CHOICE_SET || !block.active) return null
        if (block.actions.isEmpty()) return null
        val choices = block.actions.mapIndexed { index, label ->
            Choice(
                label = label,
                linkedObjectId = block.linkedObjectIds.getOrNull(index).orEmpty(),
                index = index
            )
        }
        return Model(
            title = block.title,
            body = block.body,
            sourceLabel = block.sourceLabel,
            choices = choices,
            blockId = block.blockId
        )
    }

    fun fromTurnBlocks(blocks: List<ResponseBlock>): Model? =
        blocks.asSequence().mapNotNull { fromBlock(it) }.firstOrNull()

    fun encodeTrackerLink(
        intentionId: String,
        moduleId: String,
        valueText: String
    ): String = listOf(intentionId, moduleId, valueText).joinToString(LINK_SEP.toString())

    fun decodeTrackerLink(linked: String): Triple<String, String, String>? {
        val parts = linked.split(LINK_SEP, limit = 3)
        if (parts.size < 3) return null
        val intentionId = parts[0].trim()
        val moduleId = parts[1].trim()
        val valueText = parts[2]
        if (intentionId.isEmpty() || moduleId.isEmpty()) return null
        return Triple(intentionId, moduleId, valueText)
    }

    /**
     * Apply a CHOICE_SET chip selection produced by [CommandGateway] AMBIGUOUS outcomes.
     * Returns null when the block is not a selectable ambiguous choice set.
     */
    fun select(
        store: IntentionFlowStore,
        block: ResponseBlock,
        choiceIndex: Int,
        nowEpochMs: Long,
        sessionIdForProposal: (proposalId: String) -> String = { "session-$it" }
    ): GatewayResult? {
        val model = fromBlock(block) ?: return null
        val choice = model.choices.getOrNull(choiceIndex) ?: return null
        return when {
            block.sourceLabel == SOURCE_APPROVAL ||
                block.sourceLabel.startsWith(SOURCE_APPROVAL) -> {
                val proposalId = choice.linkedObjectId.ifBlank { return null }
                CommandGateway.approveProposal(
                    store = store,
                    turnId = "turn-choice-approve-$nowEpochMs",
                    commandId = "cmd-choice-approve-$nowEpochMs",
                    sessionId = sessionIdForProposal(proposalId),
                    nowEpochMs = nowEpochMs,
                    utterance = choice.label,
                    explicitProposalId = proposalId
                )
            }
            block.sourceLabel == SOURCE_SCOPE ||
                block.sourceLabel.startsWith(SOURCE_SCOPE) -> {
                val decoded = decodeTrackerLink(choice.linkedObjectId) ?: return null
                val (intentionId, moduleId, valueText) = decoded
                CommandGateway.recordTrackerEntry(
                    store = store,
                    turnId = "turn-choice-tracker-$nowEpochMs",
                    commandId = "cmd-choice-tracker-$nowEpochMs",
                    entryId = "trk-choice-$nowEpochMs",
                    intentionId = intentionId,
                    moduleId = moduleId,
                    valueText = valueText,
                    nowEpochMs = nowEpochMs
                )
            }
            else -> null
        }
    }

    fun pendingAmbiguousChoiceSets(store: IntentionFlowStore): List<ResponseBlock> =
        store.snapshot().turns.flatMap { turn ->
            turn.blocks.filter {
                it.type == ResponseBlockType.CHOICE_SET &&
                    it.active &&
                    (it.sourceLabel == SOURCE_APPROVAL ||
                        it.sourceLabel == SOURCE_SCOPE ||
                        it.sourceLabel.startsWith(SOURCE_APPROVAL) ||
                        it.sourceLabel.startsWith(SOURCE_SCOPE))
            }
        }
}
