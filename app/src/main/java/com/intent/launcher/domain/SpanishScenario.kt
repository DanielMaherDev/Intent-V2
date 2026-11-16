package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.store.IntentionFlowStore

/**
 * Scripted Spanish exploring → fact → proposal path for v0.0.01.
 * No invented lesson counts or completion percentages.
 * Chips and proposals consult [CapabilityRegistry] (Rule 5).
 */
object SpanishScenario {
    const val INTENTION_ID = "intention-spanish"
    /** Local child of [INTENTION_ID] for OS-141 breadcrumb proof — not a dependency graph. */
    const val CHILD_INTENTION_ID = "intention-spanish-conversation"
    const val FACT_TUTOR_ID = "fact-tutor-thu-1800"
    const val PROPOSAL_ID = "proposal-weekly-session"
    const val SESSION_ID = "session-weekly-spanish"

    const val TITLE = "Learn Spanish"
    const val STATEMENT = "I want to speak Spanish more confidently in everyday life."
    const val CHILD_TITLE = "Everyday conversation practice"
    const val CHILD_STATEMENT =
        "Practice speaking Spanish in everyday conversations under Learn Spanish."

    /** Honest chips only — fake caps (e.g. lesson completion) stay out of this list. */
    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip("Everyday conversations", CapabilityRegistry.ID_ADD_HYPOTHESIS),
        SuggestedChip("I meet a tutor on Thursdays at six", CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION),
        SuggestedChip("I meet a Spanish tutor", CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION)
    )

    /** Dishonest chip kept for registry filter tests — must never surface via [conversationChips]. */
    val dishonestLessonCompletionChip = SuggestedChip(
        label = "Track my Duolingo lesson completion automatically",
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

    /**
     * Honest local child under Learn Spanish for breadcrumb navigation (OS-141).
     * Kept out of [seed] so unit fixtures that count a single Spanish intention stay stable.
     */
    fun seedChild(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        if (store.snapshot().intentions.none { it.id == INTENTION_ID }) return
        if (store.snapshot().intentions.any { it.id == CHILD_INTENTION_ID }) return
        val previousActive = store.snapshot().activeIntentionId
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = CHILD_INTENTION_ID,
                title = CHILD_TITLE,
                statement = CHILD_STATEMENT,
                shapeTag = "language",
                nowEpochMs = now + 1,
                parentIntentionId = INTENTION_ID
            )
        )
        // SeedExploring activates the new row — restore prior focus for Spanish flow honesty.
        store.apply(
            FlowCommand.SetActiveIntention(
                intentionId = previousActive ?: INTENTION_ID,
                nowEpochMs = now + 2
            )
        )
    }

    fun ensureSeeded(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        if (store.snapshot().intentions.none { it.id == INTENTION_ID }) {
            seed(store, now)
        }
    }

    /**
     * App-path helper: parent + honest local child for OS-141 breadcrumb demos.
     * Tests that count a single Spanish intention should call [seed] / [ensureSeeded] only.
     */
    fun ensureParentChildSeeded(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        ensureSeeded(store, now)
        seedChild(store, now)
    }

    fun onChip(
        store: IntentionFlowStore,
        chip: String,
        now: Long = System.currentTimeMillis(),
        registry: CapabilityRegistry = CapabilityRegistry.default,
        modality: String? = null,
        /** Active / caller intention — never invent fixture id for user-created rows. */
        intentionId: String? = null
    ) {
        val targetId = intentionId
            ?: store.snapshot().activeIntentionId
            ?: INTENTION_ID
        // Fixture path only — user-built language intentions must not auto-seed Spanish.
        if (targetId == INTENTION_ID) {
            ensureSeeded(store, now)
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-user-${now}-${chip.hashCode()}",
                intentionId = targetId,
                role = "user",
                text = chip,
                nowEpochMs = now,
                modality = modality
            )
        )
        when {
            chip.contains("Everyday conversations", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)) {
                    refuseUnavailable(store, now, CapabilityRegistry.ID_ADD_HYPOTHESIS, registry, modality, targetId)
                    return
                }
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = if (targetId == INTENTION_ID) {
                            "hyp-everyday"
                        } else {
                            "hyp-everyday-$targetId"
                        },
                        intentionId = targetId,
                        text = "Focus on everyday conversational Spanish",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-agent-everyday-$now",
                        intentionId = targetId,
                        role = "agent",
                        text = "Good. Any fixed practice you already keep?",
                        nowEpochMs = now + 1,
                        modality = modality
                    )
                )
                store.apply(
                    FlowCommand.MarkPhase(
                        intentionId = targetId,
                        phase = IntentionPhase.CLARIFYING,
                        nowEpochMs = now
                    )
                )
            }
            chip.contains("tutor", ignoreCase = true) || chip.contains("Thursdays", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) {
                    refuseUnavailable(store, now, CapabilityRegistry.ID_RECORD_FACT, registry, modality, targetId)
                    return
                }
                val factId = if (targetId == INTENTION_ID) FACT_TUTOR_ID else "fact-tutor-$targetId"
                store.apply(
                    FlowCommand.AddUserFact(
                        factId = factId,
                        intentionId = targetId,
                        text = "I meet a Spanish tutor on Thursdays at 18:00",
                        nowEpochMs = now
                    )
                )
                if (!registry.canProposeRecurringSession()) {
                    store.apply(
                        FlowCommand.AddConversationTurn(
                            turnId = "turn-agent-no-schedule-$now",
                            intentionId = targetId,
                            role = "agent",
                            text = honestUnavailableMessage(
                                CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION,
                                registry
                            ),
                            nowEpochMs = now + 1,
                            modality = modality
                        )
                    )
                    return
                }
                val proposalId = if (targetId == INTENTION_ID) PROPOSAL_ID else "proposal-weekly-$targetId"
                store.apply(
                    FlowCommand.ProposeRecurringSession(
                        proposalId = proposalId,
                        intentionId = targetId,
                        title = "Weekly Spanish session",
                        summary = "Proposed recurring session on Thursdays at 18:00, based on your tutor fact — not the fact itself.",
                        dayOfWeek = 4,
                        hour = 18,
                        minute = 0,
                        basedOnFactIds = listOf(factId),
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-agent-proposal-$now",
                        intentionId = targetId,
                        role = "agent",
                        text = "I can schedule a weekly session from that tutor fact. Review the proposal?",
                        nowEpochMs = now + 1,
                        modality = modality
                    )
                )
            }
            chip.contains("Duolingo", ignoreCase = true) || chip.contains("lesson completion", ignoreCase = true) -> {
                refuseUnavailable(
                    store,
                    now,
                    CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION,
                    registry,
                    modality,
                    targetId
                )
            }
        }
    }

    private fun refuseUnavailable(
        store: IntentionFlowStore,
        now: Long,
        capabilityId: String,
        registry: CapabilityRegistry,
        modality: String? = null,
        intentionId: String = INTENTION_ID
    ) {
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-agent-unavailable-$now",
                intentionId = intentionId,
                role = "agent",
                text = honestUnavailableMessage(capabilityId, registry),
                nowEpochMs = now + 1,
                modality = modality
            )
        )
    }

    private fun honestUnavailableMessage(capabilityId: String, registry: CapabilityRegistry): String {
        val entry = registry.get(capabilityId)
        return entry?.limitNote
            ?: "That capability is not available in INTENT yet."
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
