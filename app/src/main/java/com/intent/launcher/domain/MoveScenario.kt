package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.store.IntentionFlowStore

/**
 * CASE-08 — Six-week move (Barcelona → Valencia; rent; no place found).
 *
 * Finite dependency / landlord-gated plan on the shared intention model
 * (facts, hypotheses, proposals, sessions). No invented lease, landlord
 * confirmation, or move-in date. Landlord-dependent steps stay gated until
 * a place is SAID-confirmed — never auto-unlocked.
 * Chips and proposals consult [CapabilityRegistry] (Rule 5).
 */
object MoveScenario {
    const val INTENTION_ID = "intention-move"
    const val FACT_DEADLINE_ID = "fact-move-six-weeks-rent"
    const val FACT_NO_PLACE_ID = "fact-move-no-place"
    const val FACT_BUDGET_ID = "fact-move-budget-timing"
    const val FACT_PLACE_FOUND_ID = "fact-move-place-found"
    const val PROPOSAL_ID = "proposal-saturday-apartment-search"
    const val SESSION_ID = "session-saturday-apartment-search"

    const val HYP_SEARCH_ID = "hyp-move-search-listings"
    const val HYP_BUDGET_ID = "hyp-move-set-budget"
    const val HYP_LEASE_ID = "hyp-move-sign-lease-gated"
    const val HYP_NOTICE_ID = "hyp-move-give-notice-gated"
    const val HYP_MOVERS_ID = "hyp-move-book-movers-gated"

    const val TITLE = "Move to Valencia"
    const val STATEMENT =
        "Barcelona to Valencia in six weeks; renting; no place found yet."

    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip(
            "Six weeks — Barcelona to Valencia, renting",
            CapabilityRegistry.ID_RECORD_FACT
        ),
        SuggestedChip(
            "No place found yet",
            CapabilityRegistry.ID_ADD_HYPOTHESIS
        ),
        SuggestedChip(
            "Budget is tight until the lease starts",
            CapabilityRegistry.ID_RECORD_FACT
        ),
        SuggestedChip(
            "Schedule weekly apartment search",
            CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION
        )
    )

    fun conversationChips(registry: CapabilityRegistry = CapabilityRegistry.default): List<String> =
        registry.filterChips(suggestedChips)

    fun seed(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = INTENTION_ID,
                title = TITLE,
                statement = STATEMENT,
                shapeTag = "move",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-move-seed-agent",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "What constrains this move before a landlord confirms a place?",
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
                turnId = "turn-move-user-${now}-${chip.hashCode()}",
                intentionId = INTENTION_ID,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chip.contains("Six weeks", ignoreCase = true) ||
                chip.contains("Barcelona to Valencia", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_DEADLINE_ID,
                        intentionId = INTENTION_ID,
                        text = "Moving Barcelona → Valencia in about six weeks; renting",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-move-agent-deadline-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Noted the six-week rent window. Has a place been found yet?",
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
            chip.contains("No place found", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT) &&
                    !registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)
                ) {
                    return
                }
                if (registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) {
                    store.apply(
                        FlowCommand.AddUserFact(
                            factId = FACT_NO_PLACE_ID,
                            intentionId = INTENTION_ID,
                            text = "No place found yet — landlord steps stay gated",
                            nowEpochMs = now
                        )
                    )
                }
                if (registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) {
                    seedDependencyPlan(store, now)
                }
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-move-agent-no-place-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Finite plan sketched: search and budget are ready now; " +
                            "lease, notice, and movers stay landlord-gated until a place is confirmed. " +
                            "Nothing invents a lease.",
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
            chip.contains("Budget", ignoreCase = true) ||
                chip.contains("lease starts", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_BUDGET_ID,
                        intentionId = INTENTION_ID,
                        text = "Budget is tight until the lease starts",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-move-agent-budget-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Timing stays honest: big spend waits on landlord confirmation. " +
                            "Want a weekly apartment-search block while the place is still open?",
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
            chip.contains("apartment search", ignoreCase = true) ||
                chip.contains("Schedule weekly", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                // Ensure deadline fact exists so proposal is grounded (not invented).
                val snap = store.snapshot()
                if (snap.facts.none { it.id == FACT_DEADLINE_ID }) {
                    store.apply(
                        FlowCommand.AddUserFact(
                            factId = FACT_DEADLINE_ID,
                            intentionId = INTENTION_ID,
                            text = "Moving Barcelona → Valencia in about six weeks; renting",
                            nowEpochMs = now
                        )
                    )
                }
                if (snap.hypotheses.none { it.id == HYP_SEARCH_ID }) {
                    if (registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) {
                        seedDependencyPlan(store, now)
                    }
                }
                if (!registry.canProposeRecurringSession()) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-move-agent-no-schedule-$now",
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
                        title = "Saturday apartment search",
                        summary = "Proposed recurring apartment-search block on Saturdays at 10:00, " +
                            "based on the six-week move window — not a claim that a place exists. " +
                            "Lease, notice, and movers stay landlord-gated until a place is confirmed.",
                        dayOfWeek = 6,
                        hour = 10,
                        minute = 0,
                        basedOnFactIds = listOf(FACT_DEADLINE_ID),
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-move-agent-proposal-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "I can schedule a Saturday search block. Landlord-gated steps stay blocked " +
                            "until you confirm a place — INTENT will not invent a lease.",
                        nowEpochMs = now + 1
                    )
                )
            }
        }
    }

    /**
     * Finite dependency plan as shared hypotheses — ready vs landlord-gated.
     * Does not invent a place or landlord confirmation.
     */
    fun seedDependencyPlan(store: IntentionFlowStore, now: Long) {
        val existing = store.snapshot().hypotheses.map { it.id }.toSet()
        fun add(id: String, text: String) {
            if (id in existing) return
            store.apply(
                FlowCommand.AddHypothesis(
                    hypothesisId = id,
                    intentionId = INTENTION_ID,
                    text = text,
                    nowEpochMs = now
                )
            )
        }
        add(HYP_SEARCH_ID, "Ready · Search listings this week")
        add(HYP_BUDGET_ID, "Ready · Set rent budget and timing within six weeks")
        add(HYP_LEASE_ID, "Gated · Sign lease — waiting on landlord / place")
        add(HYP_NOTICE_ID, "Gated · Give Barcelona notice — depends on signed lease")
        add(HYP_MOVERS_ID, "Gated · Book movers / utilities — depends on landlord move-in date")
    }

    fun placeIsConfirmed(facts: List<Fact>): Boolean =
        facts.any { it.id == FACT_PLACE_FOUND_ID }

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
