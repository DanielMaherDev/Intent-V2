package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.store.IntentionFlowStore

/**
 * CASE-05 quit-smoking shape: baseline cigarettes/day, no forced quit date,
 * meaningful first-phase evening check-in, honest SAID self-report tracker entries.
 * Shares the same core records as language / marathon / business — no special-case fields.
 * Never invents smoke-free streaks or biometric cessation proof.
 */
object QuitSmokingScenario {
    const val INTENTION_ID = "intention-quit-smoking"
    const val FACT_BASELINE_ID = "fact-baseline-eight-cigs"
    const val FACT_EVENING_HARD_ID = "fact-evenings-hardest"
    const val PROPOSAL_ID = "proposal-evening-check-in"
    const val SESSION_ID = "session-evening-check-in"
    const val SHAPE_TAG = "cessation"

    const val TITLE = "Quit smoking"
    const val STATEMENT =
        "I smoke about eight cigarettes a day and want to quit — no quit date yet."

    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip("I smoke about eight a day", CapabilityRegistry.ID_RECORD_FACT),
        SuggestedChip("No quit date yet", CapabilityRegistry.ID_ADD_HYPOTHESIS),
        SuggestedChip("Evenings are hardest", CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION),
        SuggestedChip("I smoked fewer than usual today", CapabilityRegistry.ID_TRACKER_MODULES),
        SuggestedChip("I smoked about my baseline today", CapabilityRegistry.ID_TRACKER_MODULES),
        SuggestedChip("I rode out a craving", CapabilityRegistry.ID_TRACKER_MODULES)
    )

    /** Dishonest chip for registry filter tests — must never surface via [conversationChips]. */
    val dishonestAutoStreakChip = SuggestedChip(
        label = "Track my smoke-free streak automatically",
        requiredCapabilityId = CapabilityRegistry.ID_OBSERVE_CESSATION
    )

    fun conversationChips(registry: CapabilityRegistry = CapabilityRegistry.default): List<String> =
        registry.filterChips(suggestedChips)

    fun seed(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = INTENTION_ID,
                title = TITLE,
                statement = STATEMENT,
                shapeTag = SHAPE_TAG,
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-cessation-seed-agent",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "What is your baseline, and what support would help before any quit date?",
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
                turnId = "turn-cessation-user-${now}-${chip.hashCode()}",
                intentionId = INTENTION_ID,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chip.contains("eight a day", ignoreCase = true) ||
                chip.contains("eight cigarettes", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_BASELINE_ID,
                        intentionId = INTENTION_ID,
                        text = "Baseline · about 8 cigarettes per day (self-reported)",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-cessation-agent-baseline-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Baseline noted as SAID. No quit date forced — any timing or trigger to shape support around?",
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
            chip.contains("No quit date", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) return
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = "hyp-no-quit-date",
                        intentionId = INTENTION_ID,
                        text = "No quit date yet — first phase is support and honest self-report, not cold turkey",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-cessation-agent-no-date-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Fine. We stay without a quit date. When do cravings hit hardest?",
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
            chip.contains("Evenings", ignoreCase = true) ||
                chip.contains("hardest", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_EVENING_HARD_ID,
                        intentionId = INTENTION_ID,
                        text = "Evenings are the hardest time for cravings",
                        nowEpochMs = now
                    )
                )
                if (!registry.canProposeRecurringSession()) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-cessation-agent-no-schedule-$now",
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
                        title = "Evening craving check-in",
                        summary = "Proposed daily evening check-in at 20:00 to log cigarettes and cravings — based on your evening difficulty fact, not a claimed quit date.",
                        dayOfWeek = 1,
                        hour = 20,
                        minute = 0,
                        basedOnFactIds = listOf(FACT_EVENING_HARD_ID),
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-cessation-agent-proposal-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "I can schedule an evening check-in from that trigger. Review the proposal? Counts stay self-reported.",
                        nowEpochMs = now + 1
                    )
                )
            }
            chip.contains("fewer than usual", ignoreCase = true) -> {
                recordSelfReport(
                    store = store,
                    registry = registry,
                    now = now,
                    moduleId = "cigarettes_today",
                    valueText = "Fewer than baseline today (self-reported)",
                    agentReply = "Logged as SAID — not verified cessation. Keep reporting honestly."
                )
            }
            chip.contains("about my baseline", ignoreCase = true) -> {
                recordSelfReport(
                    store = store,
                    registry = registry,
                    now = now,
                    moduleId = "cigarettes_today",
                    valueText = "About baseline (8) today (self-reported)",
                    agentReply = "Logged as SAID at baseline. No invented progress chart."
                )
            }
            chip.contains("rode out a craving", ignoreCase = true) -> {
                recordSelfReport(
                    store = store,
                    registry = registry,
                    now = now,
                    moduleId = "craving_log",
                    valueText = "Rode out a craving (self-reported)",
                    agentReply = "Craving note stored as SAID. INTENT cannot observe cravings for you."
                )
            }
            chip.contains("smoke-free streak", ignoreCase = true) ||
                chip.contains("automatically", ignoreCase = true) -> {
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-cessation-agent-refuse-streak-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = registry.get(CapabilityRegistry.ID_OBSERVE_CESSATION)?.limitNote
                            ?: "Automatic smoke-free streaks are not supported.",
                        nowEpochMs = now + 1
                    )
                )
            }
        }
    }

    private fun recordSelfReport(
        store: IntentionFlowStore,
        registry: CapabilityRegistry,
        now: Long,
        moduleId: String,
        valueText: String,
        agentReply: String
    ) {
        if (!registry.canUseTrackerModules()) {
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-cessation-agent-no-tracker-$now",
                    intentionId = INTENTION_ID,
                    role = "agent",
                    text = registry.get(CapabilityRegistry.ID_TRACKER_MODULES)?.limitNote
                        ?: "Self-report tracking is not available.",
                    nowEpochMs = now + 1
                )
            )
            return
        }
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-cessation-$moduleId-$now",
                intentionId = INTENTION_ID,
                moduleId = moduleId,
                valueText = valueText,
                nowEpochMs = now,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user"
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-cessation-agent-tracker-$now",
                intentionId = INTENTION_ID,
                role = "agent",
                text = agentReply,
                nowEpochMs = now + 1
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
