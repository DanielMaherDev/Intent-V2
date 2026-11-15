package com.intent.launcher.engine

import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore

/**
 * Conversation card projection for GatewayStatus outcomes beyond AMBIGUOUS:
 * [GatewayStatus.CANCELLED], [GatewayStatus.STALE_REVISION], idempotent
 * [GatewayStatus.APPLIED] retry, [GatewayStatus.FAILED],
 * [GatewayStatus.PERMISSION_DENIED], [GatewayStatus.UNSUPPORTED],
 * and [GatewayStatus.NEEDS_APPROVAL].
 *
 * Pure helpers so JVM tests can assert the card path without Compose.
 * AMBIGUOUS CHOICE_SET remains [AmbiguousChoiceSetCard].
 */
object GatewayStatusCard {

    const val SOURCE_CANCEL = "turn_cancellation"
    const val SOURCE_STALE = "stale_revision"
    const val SOURCE_IDEMPOTENT = "idempotent_retry"
    const val SOURCE_FAILED = "gateway_failed"
    const val SOURCE_PERMISSION = "permission_denied"
    const val SOURCE_UNSUPPORTED = "unsupported_capability"
    const val SOURCE_NEEDS_APPROVAL = "needs_approval"

    const val ACTION_RETRY = "Retry"

    private const val LINK_SEP = '|'

    data class Action(
        val label: String,
        val index: Int
    )

    data class Model(
        val statusLabel: String,
        val title: String,
        val body: String,
        val sourceLabel: String,
        val blockId: String,
        val actions: List<Action> = emptyList()
    ) {
        val eyebrow: String
            get() = "$statusLabel · ${title.ifBlank { statusLabel }}"
    }

    fun fromBlock(block: ResponseBlock): Model? {
        if (!block.active) return null
        val status = statusLabelFor(block) ?: return null
        val actions = block.actions.mapIndexed { index, label ->
            Action(label = label, index = index)
        }
        return Model(
            statusLabel = status,
            title = block.title,
            body = block.body,
            sourceLabel = block.sourceLabel,
            blockId = block.blockId,
            actions = actions
        )
    }

    fun fromTurnBlocks(blocks: List<ResponseBlock>): Model? =
        blocks.asSequence().mapNotNull { fromBlock(it) }.firstOrNull()

    fun statusLabelFor(block: ResponseBlock): String? = when {
        block.sourceLabel == SOURCE_CANCEL ||
            block.sourceLabel.startsWith(SOURCE_CANCEL) ||
            (block.type == ResponseBlockType.ERROR &&
                block.title.equals("Cancelled", ignoreCase = true)) ->
            GatewayStatus.CANCELLED.name
        block.sourceLabel == SOURCE_STALE ||
            block.sourceLabel.startsWith(SOURCE_STALE) ->
            GatewayStatus.STALE_REVISION.name
        block.sourceLabel == SOURCE_IDEMPOTENT ||
            block.sourceLabel.startsWith(SOURCE_IDEMPOTENT) ->
            "RETRY"
        block.sourceLabel == SOURCE_FAILED ||
            block.sourceLabel.startsWith(SOURCE_FAILED) ->
            GatewayStatus.FAILED.name
        block.sourceLabel == SOURCE_PERMISSION ||
            block.sourceLabel.startsWith(SOURCE_PERMISSION) ->
            GatewayStatus.PERMISSION_DENIED.name
        block.sourceLabel == SOURCE_UNSUPPORTED ||
            block.sourceLabel.startsWith(SOURCE_UNSUPPORTED) ->
            GatewayStatus.UNSUPPORTED.name
        block.sourceLabel == SOURCE_NEEDS_APPROVAL ||
            block.sourceLabel.startsWith(SOURCE_NEEDS_APPROVAL) ->
            GatewayStatus.NEEDS_APPROVAL.name
        else -> null
    }

    /**
     * Encode a tracker write so [onAction] can retry after STALE_REVISION.
     * Format: turnId|commandId|entryId|intentionId|moduleId|valueText
     * (valueText may contain pipes — split limited to 6 parts).
     */
    fun encodeTrackerRetry(
        turnId: String,
        commandId: String,
        entryId: String,
        intentionId: String,
        moduleId: String,
        valueText: String
    ): String = listOf(
        turnId,
        commandId,
        entryId,
        intentionId,
        moduleId,
        valueText
    ).joinToString(LINK_SEP.toString())

    fun decodeTrackerRetry(linked: String): TrackerRetryPayload? {
        val parts = linked.split(LINK_SEP, limit = 6)
        if (parts.size < 6) return null
        val turnId = parts[0].trim()
        val commandId = parts[1].trim()
        val entryId = parts[2].trim()
        val intentionId = parts[3].trim()
        val moduleId = parts[4].trim()
        val valueText = parts[5]
        if (listOf(turnId, commandId, entryId, intentionId, moduleId).any { it.isEmpty() }) {
            return null
        }
        return TrackerRetryPayload(
            turnId = turnId,
            commandId = commandId,
            entryId = entryId,
            intentionId = intentionId,
            moduleId = moduleId,
            valueText = valueText
        )
    }

    data class TrackerRetryPayload(
        val turnId: String,
        val commandId: String,
        val entryId: String,
        val intentionId: String,
        val moduleId: String,
        val valueText: String
    )

    /**
     * Apply a gateway-status chip (today: Retry on STALE_REVISION tracker writes).
     * Returns null when the block is not actionable.
     */
    fun onAction(
        store: IntentionFlowStore,
        block: ResponseBlock,
        actionIndex: Int,
        nowEpochMs: Long
    ): GatewayResult? {
        val model = fromBlock(block) ?: return null
        val action = model.actions.getOrNull(actionIndex) ?: return null
        if (action.label != ACTION_RETRY &&
            !action.label.equals("Retry", ignoreCase = true)
        ) {
            return null
        }
        if (block.sourceLabel != SOURCE_STALE &&
            !block.sourceLabel.startsWith(SOURCE_STALE)
        ) {
            return null
        }
        val linked = block.linkedObjectIds.firstOrNull().orEmpty()
        val payload = decodeTrackerRetry(linked) ?: return null
        // Fresh revision — do not re-send the stale expectedRevision.
        return CommandGateway.recordTrackerEntry(
            store = store,
            turnId = payload.turnId,
            commandId = "${payload.commandId}-retry-$nowEpochMs",
            entryId = payload.entryId,
            intentionId = payload.intentionId,
            moduleId = payload.moduleId,
            valueText = payload.valueText,
            nowEpochMs = nowEpochMs,
            expectedRevision = null
        )
    }

    fun cancelBlock(
        turnId: String,
        reason: String
    ): ResponseBlock = ResponseBlock(
        blockId = "block-cancel-$turnId",
        type = ResponseBlockType.ERROR,
        title = "Cancelled",
        body = reason,
        sourceLabel = SOURCE_CANCEL,
        linkedObjectIds = listOf(turnId),
        actions = emptyList(),
        active = true
    )

    fun staleTrackerBlock(
        commandId: String,
        message: String,
        retryPayload: String
    ): ResponseBlock = ResponseBlock(
        blockId = "block-stale-$commandId",
        type = ResponseBlockType.ERROR,
        title = "Stale revision",
        body = message,
        sourceLabel = SOURCE_STALE,
        linkedObjectIds = listOf(retryPayload),
        actions = listOf(ACTION_RETRY),
        active = true
    )

    fun idempotentRetryBlock(
        commandId: String,
        entryId: String,
        message: String
    ): ResponseBlock = ResponseBlock(
        blockId = "block-idempotent-$commandId",
        type = ResponseBlockType.RECORDED_ENTRY,
        title = "Already applied",
        body = message,
        sourceLabel = SOURCE_IDEMPOTENT,
        linkedObjectIds = listOf(entryId),
        actions = emptyList(),
        active = true
    )

    fun failedBlock(
        commandId: String,
        message: String
    ): ResponseBlock = ResponseBlock(
        blockId = "block-failed-$commandId",
        type = ResponseBlockType.ERROR,
        title = "Could not apply",
        body = message,
        sourceLabel = SOURCE_FAILED,
        linkedObjectIds = emptyList(),
        actions = emptyList(),
        active = true
    )

    fun permissionDeniedBlock(
        commandId: String,
        message: String,
        capabilityId: String
    ): ResponseBlock = ResponseBlock(
        blockId = "block-permission-$commandId",
        type = ResponseBlockType.ERROR,
        title = "Permission needed",
        body = message,
        sourceLabel = SOURCE_PERMISSION,
        linkedObjectIds = listOf(capabilityId),
        actions = emptyList(),
        active = true
    )

    fun unsupportedBlock(
        commandId: String,
        message: String,
        capabilityId: String
    ): ResponseBlock = ResponseBlock(
        blockId = "block-unsupported-$commandId",
        type = ResponseBlockType.ERROR,
        title = "Not supported",
        body = message,
        sourceLabel = SOURCE_UNSUPPORTED,
        linkedObjectIds = listOf(capabilityId),
        actions = emptyList(),
        active = true
    )

    fun needsApprovalBlock(
        commandId: String,
        message: String,
        linkedObjectId: String? = null
    ): ResponseBlock = ResponseBlock(
        blockId = "block-needs-approval-$commandId",
        type = ResponseBlockType.ERROR,
        title = "Needs your approval",
        body = message,
        sourceLabel = SOURCE_NEEDS_APPROVAL,
        linkedObjectIds = listOfNotNull(linkedObjectId),
        actions = emptyList(),
        active = true
    )
}
