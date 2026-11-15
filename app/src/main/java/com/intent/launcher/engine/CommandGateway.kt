package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.store.IntentionFlowStore

/**
 * Gate E / contracts §2 — machine-readable command outcomes.
 * The model never writes the snapshot; only this gateway (or direct UI commands)
 * may call [IntentionFlowStore.apply] after validation.
 */
enum class GatewayStatus {
    APPLIED,
    NEEDS_APPROVAL,
    AMBIGUOUS,
    STALE_REVISION,
    PERMISSION_DENIED,
    UNSUPPORTED,
    CANCELLED,
    FAILED
}

data class GatewayResult(
    val turnId: String,
    val toolCallId: String?,
    val commandId: String,
    val status: GatewayStatus,
    val objectIds: List<String> = emptyList(),
    val eventIds: List<String> = emptyList(),
    val revisionAfter: Int? = null,
    val source: ProvenanceKind? = null,
    val undoEventKind: String? = null,
    val message: String? = null,
    val blocks: List<ResponseBlock> = emptyList()
)

/**
 * Validates cancel tokens, ambiguity, idempotent retries, and stale revisions
 * before mutating [IntentionFlowStore].
 */
object CommandGateway {

    const val POLICY_VERSION = "intent.policy.v1"
    const val CONTRACTS_VERSION = "intent.contracts.v1"

    fun revisionOf(snapshot: IntentionSnapshot): Int =
        snapshot.transactions.size

    /**
     * Cancel an in-flight agent turn. Persists an ERROR block so process death
     * still sees the cancellation. Late mutations for [turnId] are refused.
     */
    fun cancelTurn(
        store: IntentionFlowStore,
        turnId: String,
        intentionId: String,
        nowEpochMs: Long,
        reason: String = "User cancelled while the agent was thinking"
    ): GatewayResult {
        if (AgentTurnLifecycle.isCancelled(store.snapshot(), turnId)) {
            return GatewayResult(
                turnId = turnId,
                toolCallId = null,
                commandId = "cancel-$turnId",
                status = GatewayStatus.CANCELLED,
                message = "Turn already cancelled"
            )
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = AgentTurnLifecycle.cancelMarkerTurnId(turnId),
                intentionId = intentionId,
                role = "agent",
                text = reason,
                nowEpochMs = nowEpochMs,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-cancel-$turnId",
                        type = ResponseBlockType.ERROR,
                        title = "Cancelled",
                        body = reason,
                        sourceLabel = "turn_cancellation",
                        linkedObjectIds = listOf(turnId),
                        actions = emptyList(),
                        active = true
                    )
                )
            )
        )
        return GatewayResult(
            turnId = turnId,
            toolCallId = null,
            commandId = "cancel-$turnId",
            status = GatewayStatus.CANCELLED,
            objectIds = listOf(AgentTurnLifecycle.cancelMarkerTurnId(turnId)),
            revisionAfter = revisionOf(store.snapshot()),
            message = reason,
            blocks = store.snapshot().turns
                .firstOrNull { it.id == AgentTurnLifecycle.cancelMarkerTurnId(turnId) }
                ?.blocks
                .orEmpty()
        )
    }

    /**
     * Record a tracker entry with Gate E guards:
     * - cancelled turn → CANCELLED, no write
     * - ambiguous targets → AMBIGUOUS + CHOICE_SET, no write
     * - stale expected revision → STALE_REVISION, no write
     * - same [commandId]/entryId → idempotent APPLIED (no duplicate ACTIVE row)
     */
    fun recordTrackerEntry(
        store: IntentionFlowStore,
        turnId: String,
        commandId: String,
        entryId: String,
        intentionId: String?,
        moduleId: String?,
        valueText: String,
        nowEpochMs: Long,
        expectedRevision: Int? = null,
        provenanceKind: ProvenanceKind = ProvenanceKind.SAID,
        sourceLabel: String = "user",
        toolCallId: String? = null,
        eventAtEpochMs: Long? = null
    ): GatewayResult {
        val snap = store.snapshot()
        if (AgentTurnLifecycle.isCancelled(snap, turnId)) {
            return GatewayResult(
                turnId = turnId,
                toolCallId = toolCallId,
                commandId = commandId,
                status = GatewayStatus.CANCELLED,
                message = "Refused late write after cancellation — no tracker mutation"
            )
        }
        if (expectedRevision != null && expectedRevision != revisionOf(snap)) {
            return GatewayResult(
                turnId = turnId,
                toolCallId = toolCallId,
                commandId = commandId,
                status = GatewayStatus.STALE_REVISION,
                revisionAfter = revisionOf(snap),
                message = "Snapshot revision changed — refresh before applying"
            )
        }

        val ambiguity = AmbiguousTrackerResolver.resolve(
            snapshot = snap,
            utterance = valueText,
            explicitIntentionId = intentionId,
            explicitModuleId = moduleId
        )
        if (ambiguity is AmbiguousTrackerResolver.Outcome.Ambiguous) {
            val block = ResponseBlock(
                blockId = "block-ambiguous-$commandId",
                type = ResponseBlockType.CHOICE_SET,
                title = "Which tracker?",
                body = ambiguity.message,
                sourceLabel = AmbiguousChoiceSetCard.SOURCE_SCOPE,
                linkedObjectIds = ambiguity.candidates.map {
                    AmbiguousChoiceSetCard.encodeTrackerLink(
                        intentionId = it.intentionId,
                        moduleId = it.moduleId,
                        valueText = valueText
                    )
                },
                actions = ambiguity.candidates.map { "${it.intentionTitle} · ${it.moduleTitle}" },
                active = true
            )
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-ambiguous-$commandId",
                    intentionId = ambiguity.candidates.first().intentionId,
                    role = "agent",
                    text = ambiguity.message,
                    nowEpochMs = nowEpochMs,
                    blocks = listOf(block)
                )
            )
            return GatewayResult(
                turnId = turnId,
                toolCallId = toolCallId,
                commandId = commandId,
                status = GatewayStatus.AMBIGUOUS,
                message = ambiguity.message,
                blocks = listOf(block)
            )
        }

        val target = (ambiguity as AmbiguousTrackerResolver.Outcome.Resolved)
        val beforeCount = snap.trackerEntries.count {
            it.id == entryId && it.status == TrackerEntryStatus.ACTIVE
        }
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = entryId,
                intentionId = target.intentionId,
                moduleId = target.moduleId,
                valueText = valueText,
                nowEpochMs = nowEpochMs,
                provenanceKind = provenanceKind,
                sourceLabel = sourceLabel,
                eventAtEpochMs = eventAtEpochMs
            )
        )
        val after = store.snapshot()
        val activeSameId = after.trackerEntries.filter {
            it.id == entryId && it.status == TrackerEntryStatus.ACTIVE
        }
        check(activeSameId.size == 1) {
            "Idempotent retry must leave exactly one ACTIVE entry for $entryId"
        }
        return GatewayResult(
            turnId = turnId,
            toolCallId = toolCallId,
            commandId = commandId,
            status = GatewayStatus.APPLIED,
            objectIds = listOf(entryId),
            eventIds = listOf(commandId),
            revisionAfter = revisionOf(after),
            source = provenanceKind,
            undoEventKind = "retract_tracker_entry",
            message = if (beforeCount == 1) {
                "Idempotent retry — same entryId, no duplicate"
            } else {
                "Tracker entry applied"
            }
        )
    }

    /**
     * §17 case 33 — approve a pending proposal with ambiguity guards.
     * Bare “Yes” with two+ PENDING proposals → AMBIGUOUS, no write.
     */
    fun approveProposal(
        store: IntentionFlowStore,
        turnId: String,
        commandId: String,
        sessionId: String,
        nowEpochMs: Long,
        utterance: String = "Yes",
        explicitProposalId: String? = null,
        toolCallId: String? = null
    ): GatewayResult {
        val snap = store.snapshot()
        if (AgentTurnLifecycle.isCancelled(snap, turnId)) {
            return GatewayResult(
                turnId = turnId,
                toolCallId = toolCallId,
                commandId = commandId,
                status = GatewayStatus.CANCELLED,
                message = "Refused approval after cancellation"
            )
        }
        val outcome = ApprovalAmbiguityResolver.resolve(
            snapshot = snap,
            utterance = utterance,
            explicitProposalId = explicitProposalId
        )
        when (outcome) {
            is ApprovalAmbiguityResolver.Outcome.None ->
                return GatewayResult(
                    turnId = turnId,
                    toolCallId = toolCallId,
                    commandId = commandId,
                    status = GatewayStatus.FAILED,
                    message = outcome.message
                )
            is ApprovalAmbiguityResolver.Outcome.Ambiguous -> {
                // Prefer active intention so Conversation shows the AMBIGUOUS card.
                val intentionId = snap.activeIntentionId
                    ?.takeIf { id -> outcome.candidates.any { it.intentionId == id } }
                    ?: outcome.candidates.first().intentionId
                val block = ResponseBlock(
                    blockId = "block-approve-ambiguous-$commandId",
                    type = ResponseBlockType.CHOICE_SET,
                    title = "Which proposal?",
                    body = outcome.message,
                    sourceLabel = AmbiguousChoiceSetCard.SOURCE_APPROVAL,
                    linkedObjectIds = outcome.candidates.map { it.proposalId },
                    actions = outcome.candidates.map { it.title },
                    active = true
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-approve-ambiguous-$commandId",
                        intentionId = intentionId,
                        role = "agent",
                        text = outcome.message,
                        nowEpochMs = nowEpochMs,
                        blocks = listOf(block)
                    )
                )
                val stillPending = store.snapshot().proposals.count {
                    it.status == ProposalStatus.PENDING
                }
                check(stillPending >= 2) {
                    "Ambiguous approval must not approve any pending proposal"
                }
                return GatewayResult(
                    turnId = turnId,
                    toolCallId = toolCallId,
                    commandId = commandId,
                    status = GatewayStatus.AMBIGUOUS,
                    message = outcome.message,
                    blocks = listOf(block)
                )
            }
            is ApprovalAmbiguityResolver.Outcome.Single -> {
                val proposalId = outcome.proposalId
                store.apply(
                    FlowCommand.ApproveProposal(
                        proposalId = proposalId,
                        sessionId = sessionId,
                        nowEpochMs = nowEpochMs
                    )
                )
                val after = store.snapshot()
                val approved = after.proposals.firstOrNull { it.id == proposalId }
                return GatewayResult(
                    turnId = turnId,
                    toolCallId = toolCallId,
                    commandId = commandId,
                    status = GatewayStatus.APPLIED,
                    objectIds = listOf(proposalId, sessionId),
                    revisionAfter = revisionOf(after),
                    message = if (approved?.status == ProposalStatus.APPROVED) {
                        "Proposal approved"
                    } else {
                        "Approve command applied"
                    }
                )
            }
        }
    }
}
