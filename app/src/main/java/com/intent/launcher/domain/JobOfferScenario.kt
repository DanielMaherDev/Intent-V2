package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.store.IntentionFlowStore

/**
 * CASE-10 — Job-offer decision support on the shared intention model.
 *
 * Comparison / criteria matrix via SAID facts only. Proposals schedule a
 * **decision review** block — never accept or decline the offer for the user.
 * Chips and proposals consult [CapabilityRegistry] (Rule 5).
 */
object JobOfferScenario {
    const val INTENTION_ID = "intention-job-offer"
    const val FACT_DEADLINE_ID = "fact-offer-deadline-ten-days"
    const val FACT_CRITERION_COMP_ID = "fact-criterion-compensation"
    const val FACT_CRITERION_GROWTH_ID = "fact-criterion-growth"
    const val FACT_CRITERION_COMMUTE_ID = "fact-criterion-commute"
    const val PROPOSAL_ID = "proposal-decision-review"
    const val SESSION_ID = "session-decision-review"

    const val TITLE = "Decide on a job offer"
    const val STATEMENT =
        "Decide whether to accept a job offer in ten days — comparison support only; INTENT will not choose for you."

    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip("I have an offer and ten days to decide", CapabilityRegistry.ID_RECORD_FACT),
        SuggestedChip(
            "Compensation higher; growth unclear vs current role",
            CapabilityRegistry.ID_RECORD_FACT
        ),
        SuggestedChip("Commute would be longer than today", CapabilityRegistry.ID_RECORD_FACT),
        SuggestedChip(
            "Schedule a decision review before the deadline",
            CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION
        ),
        SuggestedChip("Keep comparing — do not choose for me", CapabilityRegistry.ID_CONTINUE_EXPLORING)
    )

    fun conversationChips(registry: CapabilityRegistry = CapabilityRegistry.default): List<String> =
        registry.filterChips(suggestedChips)

    fun seed(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = INTENTION_ID,
                title = TITLE,
                statement = STATEMENT,
                shapeTag = "decision",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-decision-seed-agent",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "I can help you compare criteria before the deadline. " +
                    "I will not accept or decline the offer for you — what matters most to weigh?",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.SetActiveIntention(
                intentionId = INTENTION_ID,
                nowEpochMs = now
            )
        )
    }

    fun ensureSeeded(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        if (store.snapshot().intentions.none { it.id == INTENTION_ID }) {
            seed(store, now)
        } else {
            store.apply(FlowCommand.SetActiveIntention(intentionId = INTENTION_ID, nowEpochMs = now))
        }
    }

    fun onChip(
        store: IntentionFlowStore,
        chip: String,
        now: Long = System.currentTimeMillis(),
        registry: CapabilityRegistry = CapabilityRegistry.default
    ) {
        ensureSeeded(store, now)
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-decision-user-${now}-${chip.hashCode()}",
                intentionId = INTENTION_ID,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chip.contains("ten days", ignoreCase = true) ||
                chip.contains("offer and ten", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_DEADLINE_ID,
                        intentionId = INTENTION_ID,
                        text = "Offer decision deadline · about ten days",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-decision-agent-deadline-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Deadline noted as SAID. Which criteria should go into your comparison matrix?",
                        nowEpochMs = now + 1
                    )
                )
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = INTENTION_ID,
                        phase = IntentionPhase.CLARIFYING,
                        nowEpochMs = now
                    )
                )
            }
            chip.contains("Compensation", ignoreCase = true) ||
                chip.contains("growth unclear", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_CRITERION_COMP_ID,
                        intentionId = INTENTION_ID,
                        text = "Criterion · compensation — offer looks higher than current",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_CRITERION_GROWTH_ID,
                        intentionId = INTENTION_ID,
                        text = "Criterion · growth — unclear vs current role",
                        nowEpochMs = now + 1
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-decision-agent-matrix-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Added compensation and growth to your SAID comparison matrix. " +
                            "Any other trade-off — commute, team, or lifestyle?",
                        nowEpochMs = now + 2
                    )
                )
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = INTENTION_ID,
                        phase = IntentionPhase.CLARIFYING,
                        nowEpochMs = now
                    )
                )
            }
            chip.contains("Commute", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_CRITERION_COMMUTE_ID,
                        intentionId = INTENTION_ID,
                        text = "Criterion · commute — longer than today",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-decision-agent-commute-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Commute is on the matrix. When you want a focused review block before the " +
                            "deadline, say so — I still will not choose accept or decline.",
                        nowEpochMs = now + 1
                    )
                )
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = INTENTION_ID,
                        phase = IntentionPhase.CLARIFYING,
                        nowEpochMs = now
                    )
                )
            }
            chip.contains("decision review", ignoreCase = true) ||
                chip.contains("Schedule a decision", ignoreCase = true) -> {
                ensureDeadlineFact(store, now, registry)
                if (!registry.canProposeRecurringSession()) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-decision-agent-no-schedule-$now",
                            intentionId = INTENTION_ID,
                            role = "agent",
                            text = registry.get(CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION)?.limitNote
                                ?: "Scheduling is not available.",
                            nowEpochMs = now + 1
                        )
                    )
                    return
                }
                store.apply(
                    FlowCommand.ProposeRecurringSession(
                        proposalId = PROPOSAL_ID,
                        intentionId = INTENTION_ID,
                        title = "Decision review",
                        summary = "Proposed a Friday decision-review block at 18:00 so you can weigh your " +
                            "SAID criteria before the deadline. This is decision support only — " +
                            "INTENT does not accept or decline the offer for you.",
                        dayOfWeek = 5,
                        hour = 18,
                        minute = 0,
                        basedOnFactIds = listOf(FACT_DEADLINE_ID),
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-decision-agent-proposal-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "I can schedule a decision-review block from your deadline. " +
                            "You still choose accept or decline — review the proposal?",
                        nowEpochMs = now + 1
                    )
                )
            }
            chip.contains("do not choose", ignoreCase = true) ||
                chip.contains("Keep comparing", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_CONTINUE_EXPLORING) &&
                    !registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)
                ) {
                    return
                }
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = "hyp-keep-comparing",
                        intentionId = INTENTION_ID,
                        text = "Stay in comparison — no accept/decline chosen by INTENT",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-decision-agent-no-choice-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Understood. No choice made on your behalf — criteria stay open for comparison.",
                        nowEpochMs = now + 1
                    )
                )
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = INTENTION_ID,
                        phase = IntentionPhase.EXPLORING,
                        nowEpochMs = now
                    )
                )
            }
        }
    }

    private fun ensureDeadlineFact(
        store: IntentionFlowStore,
        now: Long,
        registry: CapabilityRegistry
    ) {
        if (store.snapshot().facts.any { it.id == FACT_DEADLINE_ID }) return
        if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
        store.apply(
            FlowCommand.AddUserFact(
                factId = FACT_DEADLINE_ID,
                intentionId = INTENTION_ID,
                text = "Offer decision deadline · about ten days",
                nowEpochMs = now
            )
        )
    }

    fun approve(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.ApproveProposal(
                proposalId = PROPOSAL_ID,
                sessionId = SESSION_ID,
                nowEpochMs = now
            )
        )
    }

    fun decline(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(FlowCommand.DeclineProposal(proposalId = PROPOSAL_ID, nowEpochMs = now))
    }

    fun deferSession(store: IntentionFlowStore, deferUntilEpochMs: Long, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.DeferNextSession(
                sessionId = SESSION_ID,
                deferUntilEpochMs = deferUntilEpochMs,
                nowEpochMs = now
            )
        )
    }

    fun beginSession(
        store: IntentionFlowStore,
        now: Long = System.currentTimeMillis(),
        runId: String = "run-$SESSION_ID-$now"
    ) {
        store.apply(
            FlowCommand.BeginSession(
                runId = runId,
                sessionId = SESSION_ID,
                nowEpochMs = now
            )
        )
    }
}
