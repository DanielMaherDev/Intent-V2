package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.store.IntentionFlowStore

/**
 * Scripted marathon exploring → fact → proposal path for v0.0.02.
 * Shares the same core records as Spanish (facts, hypotheses, proposals, sessions).
 * No invented mileage totals or race-day completion percentages.
 * Chips and proposals consult [CapabilityRegistry] (Rule 5).
 */
object MarathonScenario {
    const val INTENTION_ID = "intention-marathon"
    const val FACT_LONG_RUN_ID = "fact-long-run-sunday"
    const val PROPOSAL_ID = "proposal-sunday-long-run"
    const val SESSION_ID = "session-sunday-long-run"

    const val TITLE = "Run a marathon"
    const val STATEMENT = "Train for a spring marathon without forcing a full plan before the base is clear."

    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip("Build a base before speed work", CapabilityRegistry.ID_ADD_HYPOTHESIS),
        SuggestedChip("Long runs feel best on Sundays", CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION),
        SuggestedChip("I already jog a few times a week", CapabilityRegistry.ID_RECORD_FACT)
    )

    fun conversationChips(registry: CapabilityRegistry = CapabilityRegistry.default): List<String> =
        registry.filterChips(suggestedChips)

    fun seed(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = INTENTION_ID,
                title = TITLE,
                statement = STATEMENT,
                shapeTag = "marathon",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-marathon-seed-agent",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "What part of marathon prep matters most before you lock a weekly structure?",
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
                turnId = "turn-marathon-user-${now}-${chip.hashCode()}",
                intentionId = INTENTION_ID,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chip.contains("base before speed", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) return
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = "hyp-base-building",
                        intentionId = INTENTION_ID,
                        text = "Base-building phase before speed work",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-marathon-agent-base-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Noted. Any long-run habit you already keep?",
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
            chip.contains("Sundays", ignoreCase = true) || chip.contains("Long runs", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_LONG_RUN_ID,
                        intentionId = INTENTION_ID,
                        text = "Long runs feel best on Sundays",
                        nowEpochMs = now
                    )
                )
                if (!registry.canProposeRecurringSession()) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-marathon-agent-no-schedule-$now",
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
                        title = "Sunday long run",
                        summary = "Proposed recurring long run on Sundays at 09:00, based on your Sunday preference — not the preference itself.",
                        dayOfWeek = 7,
                        hour = 9,
                        minute = 0,
                        basedOnFactIds = listOf(FACT_LONG_RUN_ID),
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-marathon-agent-proposal-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "I can schedule a Sunday long run from that habit. Review the proposal?",
                        nowEpochMs = now + 1
                    )
                )
            }
            chip.contains("jog", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = "fact-already-jogging",
                        intentionId = INTENTION_ID,
                        text = "Already jogs a few times a week",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-marathon-agent-jog-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Good baseline. When do long runs feel best?",
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
}
