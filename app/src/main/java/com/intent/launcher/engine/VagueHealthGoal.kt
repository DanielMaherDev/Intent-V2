package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore

/**
 * §17 case 2 — vague health goal (“I want to be healthier.”).
 * Opens an exploring intention + one useful next question.
 * Must not present a generic health plan as settled.
 */
object VagueHealthGoal {

    const val INTENTION_ID = "intention-health-vague"
    const val TITLE = "Be healthier"
    const val STATEMENT = "I want to be healthier."
    const val NEXT_QUESTION =
        "What would healthier look like this month — sleep, movement, food, or something else?"

    data class Result(
        val intentionId: String,
        val phase: IntentionPhase,
        val nextQuestion: String,
        val settledPlanPresented: Boolean,
        val honestyNote: String
    )

    fun looksLikeVagueHealth(utterance: String): Boolean {
        val t = utterance.trim().lowercase()
        return (t.contains("healthier") || t.contains("be healthy") || t.contains("get healthy")) &&
            !t.contains("marathon") &&
            !t.contains("quit smoking") &&
            !t.contains("5 km")
    }

    fun open(
        store: IntentionFlowStore,
        utterance: String = STATEMENT,
        nowEpochMs: Long,
        intentionId: String = INTENTION_ID
    ): Result {
        val existing = store.snapshot().intentions.firstOrNull { it.id == intentionId }
        if (existing == null) {
            store.apply(
                FlowCommand.SeedExploring(
                    intentionId = intentionId,
                    title = TITLE,
                    statement = utterance.trim().ifBlank { STATEMENT },
                    shapeTag = "other",
                    nowEpochMs = nowEpochMs
                )
            )
        } else {
            store.apply(
                FlowCommand.SetActiveIntention(
                    intentionId = intentionId,
                    nowEpochMs = nowEpochMs
                )
            )
            if (existing.phase != IntentionPhase.EXPLORING &&
                existing.phase != IntentionPhase.CLARIFYING
            ) {
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = intentionId,
                        phase = IntentionPhase.EXPLORING,
                        nowEpochMs = nowEpochMs
                    )
                )
            }
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-health-user-$nowEpochMs",
                intentionId = intentionId,
                role = "user",
                text = utterance.trim().ifBlank { STATEMENT },
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-health-agent-$nowEpochMs",
                intentionId = intentionId,
                role = "agent",
                text = NEXT_QUESTION,
                nowEpochMs = nowEpochMs + 1,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-health-next-$nowEpochMs",
                        type = ResponseBlockType.QUESTION,
                        title = "Useful next question",
                        body = NEXT_QUESTION,
                        sourceLabel = "vague_health_exploring",
                        linkedObjectIds = listOf(intentionId),
                        actions = listOf("Sleep", "Movement", "Food", "Something else"),
                        active = true
                    )
                )
            )
        )
        val snap = store.snapshot()
        val intention = snap.intentions.first { it.id == intentionId }
        val settled = snap.proposals.any {
            it.intentionId == intentionId &&
                (it.status == ProposalStatus.PENDING || it.status == ProposalStatus.APPROVED)
        } || intention.phase == IntentionPhase.ACTIVE
        return Result(
            intentionId = intentionId,
            phase = intention.phase,
            nextQuestion = NEXT_QUESTION,
            settledPlanPresented = settled,
            honestyNote = "Exploring only — no generic health plan presented as settled."
        )
    }
}
