package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone

/**
 * Worksheet §8.10 — after Approve, Home / Intention Space / Agent tab (Discuss) /
 * full chat share the same approved routine and current next action.
 *
 * Create/proposal transcript history may remain as read-only context, but it is
 * not the interactive builder truth. Floater overlay may stay thin when overlay
 * permission is denied; open-Ask still resolves the same [NextAction].
 */
object CrossSurfaceContinuity {

    const val ACTIVE_SCHEDULE_SOURCE = "adaptive_builder|active_schedule"
    const val ACTIVE_SCHEDULE_TITLE = "Active schedule"

    data class AgentTabTruth(
        val navigateTo: String?,
        val intentionId: String?,
        val intentionPhase: IntentionPhase?,
        val nextActionKind: NextActionKind,
        val nextActionLabel: String,
        val nextActionDetail: String,
        val nextForYouTitle: String?,
        val proposalCardPresent: Boolean,
        val activeScheduleBlockPresent: Boolean,
        val createGreetingStillInteractive: Boolean
    )

    /**
     * After a proposal is approved: deactivate stale proposal/builder cards and
     * project an Active-schedule [ResponseBlockType.NEXT_ACTION] aligned with
     * [NextActionEngine] (same truth Home / Intention Space read).
     */
    fun afterApprove(
        store: IntentionFlowStore,
        intentionId: String,
        sessionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        registry: CapabilityRegistry = CapabilityRegistry.default
    ) {
        // Gate E3: ApproveProposal already sets activeIntentionId, deactivates
        // proposal/builder cards, and writes ApproveReceipt atomically. Do not
        // re-apply SetActiveIntention / Deactivate here — those reversible
        // companions would steal last-undoable away from Approve.
        // Still clear create-shell cards if approve remapped onto a durable id
        // while the create greeting still lives under intention-creating.
        if (intentionId != CreateIntentionChat.SESSION_INTENTION_ID) {
            store.apply(
                FlowCommand.DeactivateBuilderAnswerCards(
                    intentionId = CreateIntentionChat.SESSION_INTENTION_ID,
                    nowEpochMs = nowEpochMs + 1
                )
            )
        }

        val snap = store.snapshot()
        val session = snap.sessions.firstOrNull { it.id == sessionId }
            ?: snap.sessions.firstOrNull { it.intentionId == intentionId }
        val next = NextActionEngine.recommend(snap, nowEpochMs + 2, timeZone, registry)
        val scheduleLine = session?.let {
            "day ${it.dayOfWeek} · %02d:%02d · approved".format(it.hour, it.minute)
        } ?: next.detail
        val body = buildString {
            append(scheduleLine)
            if (next.label.isNotBlank() && next.kind != NextActionKind.NONE) {
                append("\nNext: ").append(next.label)
                if (next.detail.isNotBlank() && next.detail != scheduleLine) {
                    append(" — ").append(next.detail)
                }
            }
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-active-schedule-$nowEpochMs",
                intentionId = intentionId,
                role = "agent",
                text = "Routine is active. $scheduleLine — same next action as Home.",
                nowEpochMs = nowEpochMs + 3,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-active-schedule-$nowEpochMs",
                        type = ResponseBlockType.NEXT_ACTION,
                        title = ACTIVE_SCHEDULE_TITLE,
                        body = body,
                        sourceLabel = ACTIVE_SCHEDULE_SOURCE,
                        linkedObjectIds = listOfNotNull(sessionId, next.sessionId),
                        actions = listOfNotNull(
                            next.beginLabel ?: next.label.takeIf {
                                next.kind == NextActionKind.BEGIN ||
                                    next.kind == NextActionKind.DISCUSS ||
                                    next.kind == NextActionKind.ACTIVE_SESSION ||
                                    next.kind == NextActionKind.CHECK_IN
                            },
                            next.deferLabel,
                            next.discussLabel ?: "Discuss"
                        ),
                        active = true
                    )
                )
            )
        )
    }

    /**
     * Agent tab / Discuss entry after Approve — same route as Home Discuss,
     * asserting conversation projection prefers Active schedule / next action
     * over a pending create/proposal card.
     */
    fun openAgentTab(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): AgentTabTruth {
        val next = NextActionEngine.recommend(
            store.snapshot(),
            nowEpochMs,
            timeZone,
            registry
        )
        val opened = DiscussFromHome.open(store, next, nowEpochMs + 1, registry)
        val snap = store.snapshot()
        val intentionId = opened.intentionId
        val intention = snap.intentions.firstOrNull { it.id == intentionId }
        val title = intention?.title.orEmpty()
        val turns = snap.turns.filter { it.intentionId == intentionId }
        val pending = snap.proposals.firstOrNull {
            it.intentionId == intentionId && it.status == ProposalStatus.PENDING
        }
        val proposalCard = turns.asSequence()
            .flatMap { it.blocks }
            .mapNotNull { AgentModuleProjection.proposalFromBlock(it, title, pending) }
            .firstOrNull()
            ?: pending?.let { AgentModuleProjection.proposalFromPending(it, title) }
        val nextFromBlock = turns.asSequence()
            .flatMap { it.blocks }
            .mapNotNull { AgentModuleProjection.nextForYouFromBlock(it, title) }
            .firstOrNull()
        val nextFromAction = AgentModuleProjection.nextForYouFromNextAction(next, title)
        val nextCard = nextFromBlock ?: nextFromAction
        val activeSchedule = turns.any { turn ->
            turn.blocks.any {
                it.active &&
                    it.type == ResponseBlockType.NEXT_ACTION &&
                    (
                        it.sourceLabel == ACTIVE_SCHEDULE_SOURCE ||
                            it.title.equals(ACTIVE_SCHEDULE_TITLE, ignoreCase = true)
                        )
            }
        }
        val createInteractive = turns.any { turn ->
            turn.intentionId == CreateIntentionChat.SESSION_INTENTION_ID &&
                turn.blocks.any { BuilderAnswerCard.isBuilderCard(it) && it.active }
        } || (
            intentionId == CreateIntentionChat.SESSION_INTENTION_ID &&
                snap.builderDraft.active
            )
        return AgentTabTruth(
            navigateTo = opened.navigateTo,
            intentionId = intentionId,
            intentionPhase = intention?.phase,
            nextActionKind = next.kind,
            nextActionLabel = next.label,
            nextActionDetail = next.detail,
            nextForYouTitle = nextCard?.title,
            proposalCardPresent = proposalCard != null,
            activeScheduleBlockPresent = activeSchedule,
            createGreetingStillInteractive = createInteractive
        )
    }

    /** Shared next-action kinds that Agent tab may surface as NEXT FOR YOU. */
    fun isScheduleNextAction(kind: NextActionKind): Boolean =
        AgentModuleProjection.isScheduleNextAction(kind)
}
