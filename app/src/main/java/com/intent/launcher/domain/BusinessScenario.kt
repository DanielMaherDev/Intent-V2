package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.store.IntentionFlowStore

/**
 * Scripted business/work exploring → fact → proposal path.
 * Shares the same core records as Spanish and marathon (facts, hypotheses, proposals, sessions).
 * Discovery-friendly: can stay exploring with no tasks; a recurring research hour is only proposed
 * after a user fact — never invented as completion metrics or forced plan.
 * Chips and proposals consult [CapabilityRegistry] (Rule 5).
 */
object BusinessScenario {
    const val INTENTION_ID = "intention-business"
    const val FACT_CUSTOMER_CHAT_ID = "fact-customer-chat-wednesday"
    const val PROPOSAL_ID = "proposal-wednesday-research"
    const val SESSION_ID = "session-wednesday-research"

    const val TITLE = "Explore a business idea"
    const val STATEMENT =
        "Figure out whether a neighborhood repair service makes sense before locking a full plan."

    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip("Demand is still unclear", CapabilityRegistry.ID_ADD_HYPOTHESIS),
        SuggestedChip("Customer chats work best on Wednesdays", CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION),
        SuggestedChip("Keep this in discovery for now", CapabilityRegistry.ID_CONTINUE_EXPLORING)
    )

    fun conversationChips(registry: CapabilityRegistry = CapabilityRegistry.default): List<String> =
        registry.filterChips(suggestedChips)

    fun seed(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = INTENTION_ID,
                title = TITLE,
                statement = STATEMENT,
                shapeTag = "business",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-business-seed-agent",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "What would help you learn whether this idea is worth pursuing?",
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
                turnId = "turn-business-user-${now}-${chip.hashCode()}",
                intentionId = INTENTION_ID,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chip.contains("Demand is still unclear", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) return
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = "hyp-demand-unclear",
                        intentionId = INTENTION_ID,
                        text = "Demand is unclear; stay in discovery",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-business-agent-demand-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Understood. Any habit that already helps you learn from people?",
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
            chip.contains("Wednesdays", ignoreCase = true) ||
                chip.contains("Customer chats", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_CUSTOMER_CHAT_ID,
                        intentionId = INTENTION_ID,
                        text = "Customer chats work best on Wednesdays",
                        nowEpochMs = now
                    )
                )
                if (!registry.canProposeRecurringSession()) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-business-agent-no-schedule-$now",
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
                        title = "Wednesday research hour",
                        summary = "Proposed recurring research hour on Wednesdays at 12:00, based on your customer-chat preference — not the preference itself.",
                        dayOfWeek = 3,
                        hour = 12,
                        minute = 0,
                        basedOnFactIds = listOf(FACT_CUSTOMER_CHAT_ID),
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-business-agent-proposal-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "I can schedule a Wednesday research hour from that habit. Review the proposal?",
                        nowEpochMs = now + 1
                    )
                )
            }
            chip.contains("discovery", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_CONTINUE_EXPLORING) &&
                    !registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)
                ) {
                    return
                }
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = "hyp-stay-discovery",
                        intentionId = INTENTION_ID,
                        text = "Intentionally staying in discovery with no weekly commitment yet",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-business-agent-discovery-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Fine. No tasks invented — this intention stays open while demand is unclear.",
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
