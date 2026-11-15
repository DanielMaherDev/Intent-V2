package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.engine.TriggerCooldownPolicy
import com.intent.launcher.store.IntentionFlowStore

/**
 * CASE-06 — App-use reduction fixture (Instagram + YouTube Shorts under 45 min/day).
 *
 * Shared Intention / Fact / Proposal / Session records — no special-case DB fields.
 * Honesty: UsageStats is package-level only; it cannot identify Shorts, Reels, or
 * other in-app video surfaces. Observation never auto-Completes a session.
 */
object AppUseReductionScenario {
    const val INTENTION_ID = "intention-app-use"
    const val FACT_DAILY_TARGET_ID = "fact-daily-cap-45"
    const val PROPOSAL_ID = "proposal-evening-usage-review"
    const val SESSION_ID = "session-evening-usage-review"

    const val TITLE = "Cut Instagram & Shorts time"
    const val STATEMENT =
        "Keep Instagram and YouTube Shorts under 45 minutes a day."

    const val DAILY_TARGET_MINUTES = 45

    /** Honest chips — never claim Shorts/Reels identity from UsageStats. */
    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip(
            "Package time is only a proxy — Shorts aren't identifiable",
            CapabilityRegistry.ID_ADD_HYPOTHESIS
        ),
        SuggestedChip(
            "I want under 45 minutes a day",
            CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION
        ),
        SuggestedChip(
            "Evenings work for a usage review",
            CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION
        )
    )

    /**
     * Dishonest chip for registry filter tests — must never surface via [conversationChips].
     * Claims video-identity observation that UsageStats cannot provide.
     */
    val dishonestShortsIdentityChip = SuggestedChip(
        label = "Auto-detect my YouTube Shorts minutes",
        requiredCapabilityId = CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION
    )

    fun conversationChips(registry: CapabilityRegistry = CapabilityRegistry.default): List<String> =
        registry.filterChips(suggestedChips)

    fun seed(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = INTENTION_ID,
                title = TITLE,
                statement = STATEMENT,
                shapeTag = "app_use",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-app-use-seed-agent",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "We can track Instagram and YouTube package time with UsageStats when you grant it — " +
                    "but that is not Shorts or Reels identity. What target fits?",
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
                turnId = "turn-app-use-user-${now}-${chip.hashCode()}",
                intentionId = INTENTION_ID,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chipContainsLeaveMeAlone(chip) -> {
                leaveMeAlone(store, now)
            }
            chipContainsShortsIdentityClaim(chip) -> {
                refuseShortsIdentity(store, now, registry)
            }
            chip.contains("proxy", ignoreCase = true) ||
                chip.contains("aren't identifiable", ignoreCase = true) ||
                chip.contains("not identifiable", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) {
                    refuseUnavailable(store, now, CapabilityRegistry.ID_ADD_HYPOTHESIS, registry)
                    return
                }
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = "hyp-package-proxy",
                        intentionId = INTENTION_ID,
                        text = "Package foreground is a proxy for app time — not Shorts or Reels identity",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-app-use-agent-proxy-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = SHORTS_LIMITATION_COPY + " Any daily target you want to hold?",
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
            chip.contains("45", ignoreCase = true) ||
                chip.contains("minutes a day", ignoreCase = true) ||
                chip.contains("Evenings", ignoreCase = true) ||
                chip.contains("usage review", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) {
                    refuseUnavailable(store, now, CapabilityRegistry.ID_RECORD_FACT, registry)
                    return
                }
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = FACT_DAILY_TARGET_ID,
                        intentionId = INTENTION_ID,
                        text = "Daily target: under $DAILY_TARGET_MINUTES minutes on Instagram and YouTube packages",
                        nowEpochMs = now
                    )
                )
                if (!registry.canProposeRecurringSession()) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-app-use-agent-no-schedule-$now",
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
                        title = "Evening usage review",
                        summary = "Proposed Monday 20:00 review of Instagram/YouTube package time " +
                            "against your $DAILY_TARGET_MINUTES-min daily target — " +
                            "package foreground only, not Shorts/Reels identity.",
                        dayOfWeek = 1,
                        hour = 20,
                        minute = 0,
                        basedOnFactIds = listOf(FACT_DAILY_TARGET_ID),
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-app-use-agent-proposal-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "I can schedule a Monday evening usage review from that target. " +
                            SHORTS_LIMITATION_COPY + " Review the proposal?",
                        nowEpochMs = now + 1
                    )
                )
            }
        }
    }

    const val SHORTS_LIMITATION_COPY =
        "UsageStats sees Instagram and YouTube package foreground only — " +
            "not Shorts, Reels, or other in-app video identity."

    fun chipContainsShortsIdentityClaim(chip: String): Boolean {
        val lower = chip.lowercase()
        return (lower.contains("shorts") &&
            (lower.contains("auto") || lower.contains("detect") || lower.contains("identity") ||
                lower.contains("completion") || lower.contains("count") ||
                lower.contains("track") || lower.contains("only"))) ||
            lower.contains("reels completion") ||
            lower.contains("auto-detect") ||
            lower.contains("track shorts only")
    }

    fun chipContainsLeaveMeAlone(chip: String): Boolean {
        val lower = chip.lowercase()
        return lower.contains("leave me alone") ||
            lower.contains("not now") ||
            (lower.contains("needed a break") && lower.contains("alone"))
    }

    fun leaveMeAlone(store: IntentionFlowStore, now: Long) {
        val until = now + TriggerCooldownPolicy.DEFAULT_LEAVE_ME_ALONE_MS
        val key = TriggerCooldownPolicy.usageThresholdKey(INTENTION_ID)
        store.apply(
            FlowCommand.LeaveMeAlone(
                untilEpochMs = until,
                triggerKey = key,
                nowEpochMs = now + 1
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-app-use-agent-leave-$now",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "Understood — I'll hold identical usage prompts until then. " +
                    "This updates cooldown preference; it does not change your schedule.",
                nowEpochMs = now + 2
            )
        )
    }

    private fun refuseShortsIdentity(
        store: IntentionFlowStore,
        now: Long,
        registry: CapabilityRegistry
    ) {
        val usageNote = registry.get(CapabilityRegistry.ID_USAGE_STATS)?.limitNote
        val body = SHORTS_LIMITATION_COPY +
            (usageNote?.let { " ($it)" } ?: "") +
            " Complete stays your report — observation never auto-completes."
        val block = ResponseBlock(
            blockId = "block-cap-gap-shorts-$now",
            type = ResponseBlockType.CAPABILITY_GAP,
            title = "Shorts identity not supported",
            body = body,
            sourceLabel = "Capability registry",
            linkedObjectIds = listOf(
                CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION,
                CapabilityRegistry.ID_USAGE_STATS
            ),
            actions = emptyList()
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-app-use-agent-shorts-refuse-$now",
                intentionId = INTENTION_ID,
                role = "agent",
                text = body,
                nowEpochMs = now + 1,
                blocks = listOf(block)
            )
        )
    }

    private fun refuseUnavailable(
        store: IntentionFlowStore,
        now: Long,
        capabilityId: String,
        registry: CapabilityRegistry
    ) {
        val body = registry.get(capabilityId)?.limitNote
            ?: "That capability is not available in INTENT yet."
        val block = ResponseBlock(
            blockId = "block-cap-gap-$capabilityId-$now",
            type = ResponseBlockType.CAPABILITY_GAP,
            title = "Capability unavailable",
            body = body,
            sourceLabel = "Capability registry",
            linkedObjectIds = listOf(capabilityId),
            actions = emptyList()
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-app-use-agent-unavailable-$now",
                intentionId = INTENTION_ID,
                role = "agent",
                text = body,
                nowEpochMs = now + 1,
                blocks = listOf(block)
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
