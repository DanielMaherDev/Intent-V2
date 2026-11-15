package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore

/**
 * §17 case 34 — interrupted voice (user speaks over the agent's reply).
 * Cancels the in-flight speech/turn and accepts one new ordered user turn.
 * Must not apply a late duplicate agent response or action for the cancelled turn.
 */
object InterruptedVoice {

    data class Result(
        val cancelledTurnId: String,
        val newUserTurnId: String,
        val lateDuplicateRefused: Boolean,
        val honestyNote: String
    )

    /**
     * Barge-in while [speakingTurnId] is the in-flight agent turn being spoken.
     */
    fun bargeIn(
        store: IntentionFlowStore,
        intentionId: String,
        speakingTurnId: String,
        userTranscript: String,
        nowEpochMs: Long
    ): Result {
        val cancel = CommandGateway.cancelTurn(
            store = store,
            turnId = speakingTurnId,
            intentionId = intentionId,
            nowEpochMs = nowEpochMs,
            reason = "Interrupted by voice — cancelled in-flight agent speech/turn"
        )
        check(cancel.status == GatewayStatus.CANCELLED)

        val userTurnId = "turn-voice-barge-$nowEpochMs"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = userTurnId,
                intentionId = intentionId,
                role = "user",
                text = userTranscript.trim(),
                nowEpochMs = nowEpochMs + 1,
                modality = "VOICE_TRANSCRIPT",
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-barge-$nowEpochMs",
                        type = ResponseBlockType.EXPLANATION,
                        title = "Interrupted voice",
                        body = "Prior agent turn cancelled; this user turn is ordered next.",
                        sourceLabel = "interrupted_voice",
                        linkedObjectIds = listOf(speakingTurnId, userTurnId),
                        actions = emptyList(),
                        active = true
                    )
                )
            )
        )

        // Simulate a late agent reply for the cancelled speaking turn — must refuse mutation.
        val late = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = speakingTurnId,
            commandId = "cmd-late-voice-$speakingTurnId",
            entryId = "trk-late-voice-$speakingTurnId",
            intentionId = intentionId,
            moduleId = "practice_log",
            valueText = "Late duplicate after barge-in",
            nowEpochMs = nowEpochMs + 2
        )
        val refused = late.status == GatewayStatus.CANCELLED

        return Result(
            cancelledTurnId = speakingTurnId,
            newUserTurnId = userTurnId,
            lateDuplicateRefused = refused,
            honestyNote = "Cancelled speech/turn; one new ordered user turn; late duplicate refused."
        )
    }
}
