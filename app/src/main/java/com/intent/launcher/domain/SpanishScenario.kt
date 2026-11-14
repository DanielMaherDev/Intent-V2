package com.intent.launcher.domain

import com.intent.launcher.store.IntentionFlowStore

/**
 * Scripted Spanish exploring → fact → proposal path for v0.0.01.
 * No invented lesson counts or completion percentages.
 */
object SpanishScenario {
    const val INTENTION_ID = "intention-spanish"
    const val FACT_TUTOR_ID = "fact-tutor-thu-1800"
    const val PROPOSAL_ID = "proposal-weekly-session"
    const val SESSION_ID = "session-weekly-spanish"

    const val TITLE = "Learn Spanish"
    const val STATEMENT = "I want to speak Spanish more confidently in everyday life."

    val conversationChips = listOf(
        "Everyday conversations",
        "I meet a tutor on Thursdays at six",
        "I meet a Spanish tutor"
    )

    fun seed(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = INTENTION_ID,
                title = TITLE,
                statement = STATEMENT,
                shapeTag = "language",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-seed-agent",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "What kind of Spanish practice matters most right now?",
                nowEpochMs = now
            )
        )
    }

    fun ensureSeeded(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        if (store.snapshot().intentions.none { it.id == INTENTION_ID }) {
            seed(store, now)
        }
    }

    fun onChip(store: IntentionFlowStore, chip: String, now: Long = System.currentTimeMillis()) {
        ensureSeeded(store, now)
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-user-${now}-${chip.hashCode()}",
                intentionId = INTENTION_ID,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chip.contains("Everyday conversations", ignoreCase = true) -> {
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = "hyp-everyday",
                        intentionId = INTENTION_ID,
                        text = "Focus on everyday conversational Spanish",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-agent-everyday-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Good. Any fixed practice you already keep?",
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
            chip.contains("tutor", ignoreCase = true) || chip.contains("Thursdays", ignoreCase = true) -> {
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_TUTOR_ID,
                        intentionId = INTENTION_ID,
                        text = "I meet a Spanish tutor on Thursdays at 18:00",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.ProposeRecurringSession(
                        proposalId = PROPOSAL_ID,
                        intentionId = INTENTION_ID,
                        title = "Weekly Spanish session",
                        summary = "Proposed recurring session on Thursdays at 18:00, based on your tutor fact — not the fact itself.",
                        dayOfWeek = 4,
                        hour = 18,
                        minute = 0,
                        basedOnFactIds = listOf(FACT_TUTOR_ID),
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-agent-proposal-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "I can schedule a weekly session from that tutor fact. Review the proposal?",
                        nowEpochMs = now + 1
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
