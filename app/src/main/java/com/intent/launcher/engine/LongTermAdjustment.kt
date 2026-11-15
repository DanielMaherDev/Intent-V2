package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore

/**
 * §17 case 24 — long-term adjustment (“Tutor is now on Tuesdays.”).
 * Corrected fact + schedule-change proposal. Must not silently rewrite recurrence.
 */
object LongTermAdjustment {

    const val CORRECTED_FACT_ID = "fact-tutor-tue-1800"
    const val SCHEDULE_CHANGE_PROPOSAL_ID = "proposal-tutor-tuesday-change"
    const val TUESDAY = 2

    data class Result(
        val correctedFactId: String,
        val proposalId: String?,
        val sessionSilentlyRewritten: Boolean,
        val honestyNote: String
    )

    fun looksLikeTutorDayChange(utterance: String): Boolean {
        val t = utterance.trim().lowercase()
        return t.contains("tutor") &&
            (t.contains("tuesday") || t.contains("tuesdays")) &&
            (t.contains("now") || t.contains("instead") || t.contains("moved") || t.contains("change"))
    }

    fun applyTutorTuesday(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        utterance: String = "Tutor is now on Tuesdays.",
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Result {
        SpanishScenario.ensureSeeded(store, nowEpochMs)
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-adjust-user-$nowEpochMs",
                intentionId = SpanishScenario.INTENTION_ID,
                role = "user",
                text = utterance,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddUserFact(
                factId = CORRECTED_FACT_ID,
                intentionId = SpanishScenario.INTENTION_ID,
                text = "I meet a Spanish tutor on Tuesdays at 18:00",
                nowEpochMs = nowEpochMs
            )
        )

        val sessionBefore = store.snapshot().sessions.firstOrNull { it.id == SpanishScenario.SESSION_ID }
        val pending = store.snapshot().proposals.firstOrNull {
            it.id == SpanishScenario.PROPOSAL_ID && it.status == ProposalStatus.PENDING
        }

        var proposalId: String? = null
        when {
            pending != null -> {
                store.apply(
                    FlowCommand.EditProposal(
                        proposalId = SpanishScenario.PROPOSAL_ID,
                        dayOfWeek = TUESDAY,
                        hour = pending.proposedHour,
                        minute = pending.proposedMinute,
                        nowEpochMs = nowEpochMs
                    )
                )
                proposalId = SpanishScenario.PROPOSAL_ID
            }
            registry.canProposeRecurringSession() -> {
                store.apply(
                    FlowCommand.ProposeRecurringSession(
                        proposalId = SCHEDULE_CHANGE_PROPOSAL_ID,
                        intentionId = SpanishScenario.INTENTION_ID,
                        title = "Move weekly Spanish session to Tuesday",
                        summary = "Schedule-change proposal from corrected tutor fact — " +
                            "not a silent recurrence rewrite.",
                        dayOfWeek = TUESDAY,
                        hour = 18,
                        minute = 0,
                        basedOnFactIds = listOf(CORRECTED_FACT_ID),
                        nowEpochMs = nowEpochMs
                    )
                )
                proposalId = SCHEDULE_CHANGE_PROPOSAL_ID
            }
        }

        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-adjust-agent-$nowEpochMs",
                intentionId = SpanishScenario.INTENTION_ID,
                role = "agent",
                text = "Recorded the corrected tutor day. Review the schedule-change proposal " +
                    "before the weekly session moves — I will not silently rewrite recurrence.",
                nowEpochMs = nowEpochMs + 1
            )
        )

        val sessionAfter = store.snapshot().sessions.firstOrNull { it.id == SpanishScenario.SESSION_ID }
        val silentlyRewritten = sessionBefore != null &&
            sessionAfter != null &&
            sessionBefore.dayOfWeek != TUESDAY &&
            sessionAfter.dayOfWeek == TUESDAY &&
            proposalId == null

        return Result(
            correctedFactId = CORRECTED_FACT_ID,
            proposalId = proposalId,
            sessionSilentlyRewritten = silentlyRewritten,
            honestyNote = "Corrected fact + schedule-change proposal; recurrence not silently rewritten."
        )
    }
}
