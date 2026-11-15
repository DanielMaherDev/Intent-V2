package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore

/**
 * §17 case 17 — physical constraint change (“I cannot run this week.”).
 * Records a SAID constraint and a review/reschedule proposal.
 * Must not leave an unchanged high-intensity recommendation.
 */
object PhysicalConstraintChange {

    const val FACT_ID = "fact-cannot-run-this-week"
    const val PROPOSAL_ID = "proposal-easy-week-reschedule"
    const val CONSTRAINT_TEXT = "Cannot run this week (physical constraint)"

    data class Result(
        val constraintRecorded: Boolean,
        val proposalId: String?,
        val deferredSessionId: String?,
        val highIntensityLeftUnchanged: Boolean,
        val honestyNote: String
    )

    fun looksLikeConstraint(utterance: String): Boolean {
        val t = utterance.trim().lowercase()
        return (t.contains("cannot run") || t.contains("can't run") || t.contains("cant run")) &&
            (t.contains("week") || t.contains("injured") || t.contains("hurt"))
    }

    fun apply(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        utterance: String = "I cannot run this week.",
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Result {
        MarathonScenario.ensureSeeded(store, nowEpochMs)
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-constraint-user-$nowEpochMs",
                intentionId = MarathonScenario.INTENTION_ID,
                role = "user",
                text = utterance,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddUserFact(
                factId = FACT_ID,
                intentionId = MarathonScenario.INTENTION_ID,
                text = CONSTRAINT_TEXT,
                nowEpochMs = nowEpochMs
            )
        )

        var deferredSessionId: String? = null
        val sundaySession = store.snapshot().sessions.firstOrNull {
            it.id == MarathonScenario.SESSION_ID ||
                (it.intentionId == MarathonScenario.INTENTION_ID && it.dayOfWeek == 7)
        }
        if (sundaySession != null) {
            val deferUntil = nowEpochMs + 7L * 24L * 60L * 60L * 1000L
            store.apply(
                FlowCommand.DeferNextSession(
                    sessionId = sundaySession.id,
                    deferUntilEpochMs = deferUntil,
                    nowEpochMs = nowEpochMs
                )
            )
            deferredSessionId = sundaySession.id
        }

        val summary =
            "Physical constraint recorded — propose an easy recovery / walk review this week, " +
                "not speed work or high-intensity intervals. Review before any schedule change."
        if (registry.canProposeRecurringSession()) {
            store.apply(
                FlowCommand.ProposeRecurringSession(
                    proposalId = PROPOSAL_ID,
                    intentionId = MarathonScenario.INTENTION_ID,
                    title = "Easy-week review (reschedule)",
                    summary = summary,
                    dayOfWeek = 3,
                    hour = 10,
                    minute = 0,
                    basedOnFactIds = listOf(FACT_ID),
                    nowEpochMs = nowEpochMs
                )
            )
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-constraint-agent-$nowEpochMs",
                intentionId = MarathonScenario.INTENTION_ID,
                role = "agent",
                text = summary,
                nowEpochMs = nowEpochMs + 1,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-constraint-$nowEpochMs",
                        type = ResponseBlockType.PROPOSAL,
                        title = "Reschedule / review",
                        body = summary,
                        sourceLabel = "physical_constraint",
                        linkedObjectIds = listOf(FACT_ID, PROPOSAL_ID),
                        actions = listOf("Review easy week", "Keep deferred"),
                        active = true
                    )
                )
            )
        )

        val snap = store.snapshot()
        val proposal = snap.proposals.firstOrNull { it.id == PROPOSAL_ID }
        val recommendsHighIntensity = proposal != null &&
            (proposal.title.contains("speed", ignoreCase = true) ||
                proposal.summary.contains("interval training", ignoreCase = true) ||
                proposal.summary.contains("tempo run", ignoreCase = true)) &&
            !proposal.summary.contains("not speed", ignoreCase = true)
        val unchanged = (proposal == null && deferredSessionId == null) || recommendsHighIntensity
        return Result(
            constraintRecorded = snap.facts.any { it.id == FACT_ID },
            proposalId = proposal?.id,
            deferredSessionId = deferredSessionId,
            highIntensityLeftUnchanged = unchanged,
            honestyNote = "Constraint SAID + easy-week proposal — high-intensity not left unchanged."
        )
    }

    fun hasActiveConstraint(store: IntentionFlowStore): Boolean =
        store.snapshot().facts.any {
            it.id == FACT_ID && it.intentionId == MarathonScenario.INTENTION_ID
        }

    fun proposalIsEasyWeek(store: IntentionFlowStore): Boolean {
        val p = store.snapshot().proposals.firstOrNull { it.id == PROPOSAL_ID } ?: return false
        return p.status == ProposalStatus.PENDING &&
            p.summary.contains("easy", ignoreCase = true) &&
            !p.summary.contains("speed work as planned", ignoreCase = true)
    }
}
