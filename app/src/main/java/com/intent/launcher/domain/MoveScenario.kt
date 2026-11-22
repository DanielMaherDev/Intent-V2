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
    const val HYP_EXTERNAL_WAIT_ID = "hyp-move-external-wait"
    const val FACT_EXTERNAL_WAIT_ID = "fact-move-external-wait"

    const val TITLE = "Move to Valencia"
    const val STATEMENT =
        "Barcelona to Valencia in six weeks; renting; no place found yet."

    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip(
            "Leave date set — renting, place not found",
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
        ),
        SuggestedChip(
            "Landlord hasn't replied about a viewing",
            CapabilityRegistry.ID_RECORD_FACT
        ),
        SuggestedChip(
            "Follow up tomorrow",
            CapabilityRegistry.ID_RECORD_FACT
        ),
        SuggestedChip(
            "Look at another listing now",
            CapabilityRegistry.ID_RECORD_FACT
        ),
        SuggestedChip(
            "Adjust the search plan",
            CapabilityRegistry.ID_RECORD_FACT
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
        registry: CapabilityRegistry = CapabilityRegistry.default,
        intentionId: String = INTENTION_ID
    ) {
        if (intentionId == INTENTION_ID) {
            ensureSeeded(store, now)
        }
        val id = intentionId
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-move-user-${now}-${chip.hashCode()}",
                intentionId = id,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chip.contains("Landlord hasn't replied", ignoreCase = true) ||
                chip.contains("hasn't replied about a viewing", ignoreCase = true) -> {
                markExternalWait(store, id, now, registry)
            }
            chip.contains("Follow up tomorrow", ignoreCase = true) ||
                chip.contains("Look at another listing", ignoreCase = true) ||
                chip.contains("Adjust the search plan", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = "fact-move-followup-$now",
                        intentionId = id,
                        text = "Next user action · $chip",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-move-agent-followup-$now",
                        intentionId = id,
                        role = "agent",
                        text = "Noted as Next user action. Waiting on someone stays marked until " +
                            "a reply or place is confirmed — no viewing invented.",
                        nowEpochMs = now + 1
                    )
                )
            }
            chip.contains("Six weeks", ignoreCase = true) ||
                chip.contains("Leave date set", ignoreCase = true) ||
                chip.contains("Barcelona to Valencia", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_DEADLINE_ID,
                        intentionId = id,
                        text = "Moving within a fixed leave window; renting",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-move-agent-deadline-$now",
                        intentionId = id,
                        role = "agent",
                        text = "Noted the leave window while renting. Has a place been found yet?",
                        nowEpochMs = now + 1
                    )
                )
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = id,
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
                            intentionId = id,
                            text = "No place found yet — landlord steps stay gated",
                            nowEpochMs = now
                        )
                    )
                }
                if (registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) {
                    seedDependencyPlan(store, now, id)
                }
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-move-agent-no-place-$now",
                        intentionId = id,
                        role = "agent",
                        text = "Finite plan sketched: search and budget are ready now; " +
                            "lease, notice, and movers stay landlord-gated until a place is confirmed. " +
                            "Nothing invents a lease.",
                        nowEpochMs = now + 1
                    )
                )
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = id,
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
                        intentionId = id,
                        text = "Budget is tight until the lease starts",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-move-agent-budget-$now",
                        intentionId = id,
                        role = "agent",
                        text = "Timing stays honest: big spend waits on landlord confirmation. " +
                            "Want a weekly apartment-search block while the place is still open?",
                        nowEpochMs = now + 1
                    )
                )
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = id,
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
                            intentionId = id,
                            text = "Moving within a fixed leave window; renting",
                            nowEpochMs = now
                        )
                    )
                }
                if (snap.hypotheses.none { it.id == HYP_SEARCH_ID }) {
                    if (registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) {
                        seedDependencyPlan(store, now, id)
                    }
                }
                if (!registry.canProposeRecurringSession()) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-move-agent-no-schedule-$now",
                            intentionId = id,
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
                        intentionId = id,
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
                        intentionId = id,
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
    fun seedDependencyPlan(
        store: IntentionFlowStore,
        now: Long,
        intentionId: String = INTENTION_ID
    ) {
        val existing = store.snapshot().hypotheses
            .filter { it.intentionId == intentionId }
            .map { it.id }
            .toSet()
        fun add(hypId: String, text: String) {
            if (hypId in existing) return
            store.apply(
                FlowCommand.AddHypothesis(
                    hypothesisId = hypId,
                    intentionId = intentionId,
                    text = text,
                    nowEpochMs = now
                )
            )
        }
        add(HYP_SEARCH_ID, "Next user action · Ready · Search listings")
        add(HYP_BUDGET_ID, "Next user action · Ready · Set rent budget and timing")
        add(HYP_LEASE_ID, "Waiting on someone · Gated · Sign lease — landlord / place")
        add(HYP_NOTICE_ID, "Waiting on someone · Gated · Give current-place notice — depends on signed lease")
        add(HYP_MOVERS_ID, "Waiting on someone · Gated · Book movers / utilities — landlord move-in")
    }

    /**
     * Conversation 2 — external delay: mark viewing / landlord reply as waiting.
     * Offers follow-up / other listing / adjust search. Never invents a viewing.
     */
    fun markExternalWait(
        store: IntentionFlowStore,
        intentionId: String,
        now: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ) {
        if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT) &&
            !registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)
        ) {
            return
        }
        if (registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) {
            store.apply(
                FlowCommand.AddUserFact(
                    factId = FACT_EXTERNAL_WAIT_ID,
                    intentionId = intentionId,
                    text = "Waiting on someone · Viewing — landlord hasn't replied",
                    nowEpochMs = now
                )
            )
        }
        if (registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) {
            val existing = store.snapshot().hypotheses.any {
                it.id == HYP_EXTERNAL_WAIT_ID && it.intentionId == intentionId
            }
            if (!existing) {
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = HYP_EXTERNAL_WAIT_ID,
                        intentionId = intentionId,
                        text = "Waiting on someone · Landlord reply on viewing — not booked",
                        nowEpochMs = now
                    )
                )
            }
            seedDependencyPlan(store, now, intentionId)
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-move-agent-wait-$now",
                intentionId = intentionId,
                role = "agent",
                text = "I've marked that viewing as Waiting on someone for a reply. " +
                    "Would you like to follow up tomorrow, look at another listing now, " +
                    "or adjust the search plan? No viewing is invented as booked.",
                nowEpochMs = now + 1
            )
        )
    }

    fun hasExternalWait(facts: List<Fact>, hypotheses: List<Hypothesis> = emptyList()): Boolean =
        facts.any {
            it.id == FACT_EXTERNAL_WAIT_ID ||
                it.text.contains("landlord hasn't replied", ignoreCase = true)
        } || hypotheses.any { it.id == HYP_EXTERNAL_WAIT_ID }

    fun placeIsConfirmed(facts: List<Fact>): Boolean =
        facts.any {
            it.id == FACT_PLACE_FOUND_ID ||
                (
                    it.text.contains("place confirmed", ignoreCase = true) ||
                        it.text.contains("found a place", ignoreCase = true) ||
                        it.text.contains("signed a lease", ignoreCase = true)
                    ) &&
                !it.text.contains("no place", ignoreCase = true) &&
                !it.text.contains("not found", ignoreCase = true)
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
