package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.store.IntentionFlowStore

/**
 * CASE-07 chess rating shape: manual rapid baseline 900 → goal 1200,
 * practice/review habit, honest SAID rating + practice log entries.
 * Shares the same core records as language / marathon / business — no special-case fields.
 * Never invents Chess.com scrapes, fabricated games, or automatic rating sync.
 */
object ChessScenario {
    const val INTENTION_ID = "intention-chess-rating"
    const val FACT_BASELINE_ID = "fact-baseline-rapid-900"
    const val FACT_EVENING_PRACTICE_ID = "fact-evenings-practice"
    const val PROPOSAL_ID = "proposal-evening-chess-practice"
    const val SESSION_ID = "session-evening-chess-practice"
    const val SHAPE_TAG = "chess"

    const val TITLE = "Raise Chess.com rapid"
    const val STATEMENT =
        "Chess.com rapid about 900 — want 1200. Manual baseline until a partner connection exists."

    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip("My rapid rating is about 900", CapabilityRegistry.ID_RECORD_FACT),
        SuggestedChip("I want to reach 1200", CapabilityRegistry.ID_ADD_HYPOTHESIS),
        SuggestedChip("Evenings work for practice", CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION),
        SuggestedChip("I practised tactics today", CapabilityRegistry.ID_TRACKER_MODULES),
        SuggestedChip("I reviewed a recent game", CapabilityRegistry.ID_TRACKER_MODULES),
        SuggestedChip("I logged my current rapid rating", CapabilityRegistry.ID_TRACKER_MODULES)
    )

    /** Dishonest chip for registry filter tests — must never surface via [conversationChips]. */
    val dishonestAutoRatingChip = SuggestedChip(
        label = "Sync my Chess.com rating automatically",
        requiredCapabilityId = CapabilityRegistry.ID_OBSERVE_CHESS_RATING
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
                turnId = "turn-chess-seed-agent",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "What is your current rapid rating, and when can you practise? Chess.com sync is not available — use a manual baseline.",
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
                turnId = "turn-chess-user-${now}-${chip.hashCode()}",
                intentionId = INTENTION_ID,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chip.contains("about 900", ignoreCase = true) ||
                chip.contains("rapid rating is", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_BASELINE_ID,
                        intentionId = INTENTION_ID,
                        text = "Baseline · Chess.com rapid about 900 (self-reported)",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-chess-agent-baseline-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Baseline noted as SAID — not scraped from Chess.com. What rating are you aiming for?",
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
            chip.contains("reach 1200", ignoreCase = true) ||
                chip.contains("1200", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) return
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = "hyp-goal-1200",
                        intentionId = INTENTION_ID,
                        text = "Goal · raise rapid toward 1200 with practice and review — not an auto-synced partner chart",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-chess-agent-goal-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "1200 noted as a goal hypothesis. When do you want a practice / review rhythm?",
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
            chip.contains("Evenings work for practice", ignoreCase = true) ||
                chip.contains("Evenings", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_EVENING_PRACTICE_ID,
                        intentionId = INTENTION_ID,
                        text = "Evenings work for chess practice and review",
                        nowEpochMs = now
                    )
                )
                if (!registry.canProposeRecurringSession()) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-chess-agent-no-schedule-$now",
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
                        title = "Evening chess practice",
                        summary = "Proposed daily evening practice/review at 19:00 — based on your evening availability fact, not a scraped Chess.com game list.",
                        dayOfWeek = 1,
                        hour = 19,
                        minute = 0,
                        basedOnFactIds = listOf(FACT_EVENING_PRACTICE_ID),
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-chess-agent-proposal-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "I can schedule an evening practice block from that habit. Review the proposal? Rating and games stay self-reported.",
                        nowEpochMs = now + 1
                    )
                )
            }
            chip.contains("practised tactics", ignoreCase = true) ||
                chip.contains("tactics today", ignoreCase = true) -> {
                recordSelfReport(
                    store = store,
                    registry = registry,
                    now = now,
                    moduleId = "practice_log",
                    valueText = "Practised tactics today (self-reported)",
                    agentReply = "Practice logged as SAID — not a Chess.com activity scrape."
                )
            }
            chip.contains("reviewed a recent game", ignoreCase = true) ||
                chip.contains("reviewed", ignoreCase = true) -> {
                recordSelfReport(
                    store = store,
                    registry = registry,
                    now = now,
                    moduleId = "game_review",
                    valueText = "Reviewed a recent game (self-reported)",
                    agentReply = "Review note stored as SAID. INTENT does not invent games you did not play."
                )
            }
            chip.contains("logged my current rapid", ignoreCase = true) ||
                chip.contains("current rapid rating", ignoreCase = true) -> {
                recordSelfReport(
                    store = store,
                    registry = registry,
                    now = now,
                    moduleId = "rating_self_report",
                    valueText = "Current rapid rating logged (self-reported)",
                    agentReply = "Rating entry stored as SAID — Chess.com auto-sync remains unsupported."
                )
            }
            chip.contains("Chess.com rating automatically", ignoreCase = true) ||
                chip.contains("Sync my Chess.com", ignoreCase = true) ||
                chip.contains("automatically", ignoreCase = true) -> {
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-chess-agent-refuse-sync-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = registry.get(CapabilityRegistry.ID_OBSERVE_CHESS_RATING)?.limitNote
                            ?: "Automatic Chess.com rating sync is not supported.",
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
                    turnId = "turn-chess-agent-no-tracker-$now",
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
                entryId = "trk-chess-$moduleId-$now",
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
                turnId = "turn-chess-agent-tracker-$now",
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
