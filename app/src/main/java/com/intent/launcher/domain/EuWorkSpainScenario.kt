package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.store.IntentionFlowStore

/**
 * CASE-03 — EU citizen living in Spain; work legally.
 *
 * Research/checklist shape on the shared intention model (`shapeTag = research`).
 * Asks citizenship, arrangement, duration, and document status as user-confirmed SAID facts.
 * Proposes a sourced research-checklist session only after those facts — never invents statutes,
 * visa rules, or official requirement text. Live web research stays NOT_SUPPORTED.
 */
object EuWorkSpainScenario {
    const val INTENTION_ID = "intention-eu-work-spain"
    const val FACT_CITIZENSHIP_ID = "fact-eu-citizenship"
    const val FACT_ARRANGEMENT_ID = "fact-live-in-spain"
    const val FACT_DURATION_ID = "fact-work-duration"
    const val FACT_DOCUMENTS_ID = "fact-documents-status"
    const val PROPOSAL_ID = "proposal-sourced-checklist-review"
    const val SESSION_ID = "session-sourced-checklist-review"

    const val TITLE = "Work legally in Spain"
    const val STATEMENT =
        "EU citizen living in Spain — clarify facts, then research current official requirements as a sourced checklist."

    /** Research questions for the checklist — not legal answers. */
    val CHECKLIST_RESEARCH_QUESTIONS: List<String> = listOf(
        "Confirm whether registration / residence certificate steps apply — verify on official Spanish government and EU portals; record source URL + retrieval date.",
        "Confirm social-security affiliation steps for employment — verify on official portals; record source URL + retrieval date.",
        "Confirm tax identification (NIE/NIF) process if still needed — verify on official portals; record source URL + retrieval date.",
        "Confirm any employer-side notifications that apply to your arrangement — verify on official portals; do not invent obligations."
    )

    val suggestedChips: List<SuggestedChip> = listOf(
        SuggestedChip("I am an EU citizen", CapabilityRegistry.ID_RECORD_FACT),
        SuggestedChip("I already live in Spain", CapabilityRegistry.ID_RECORD_FACT),
        SuggestedChip("I plan to work for more than three months", CapabilityRegistry.ID_RECORD_FACT),
        SuggestedChip("I have not gathered documents yet", CapabilityRegistry.ID_RECORD_FACT),
        SuggestedChip(
            "Build a sourced research checklist",
            CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION
        ),
        SuggestedChip("Keep clarifying — no checklist yet", CapabilityRegistry.ID_CONTINUE_EXPLORING)
    )

    fun conversationChips(registry: CapabilityRegistry = CapabilityRegistry.default): List<String> =
        registry.filterChips(suggestedChips)

    fun seed(store: IntentionFlowStore, now: Long = System.currentTimeMillis()) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = INTENTION_ID,
                title = TITLE,
                statement = STATEMENT,
                shapeTag = "research",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-eu-work-seed-agent",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "Before any checklist: confirm citizenship, whether you already live in Spain, " +
                    "how long you plan to work, and what documents you already have. " +
                    "I will not invent legal requirements — official sources must answer those.",
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
                turnId = "turn-eu-work-user-${now}-${chip.hashCode()}",
                intentionId = INTENTION_ID,
                role = "user",
                text = chip,
                nowEpochMs = now
            )
        )
        when {
            chip.contains("EU citizen", ignoreCase = true) -> {
                recordSaidFact(
                    store = store,
                    registry = registry,
                    factId = FACT_CITIZENSHIP_ID,
                    text = "I am an EU citizen",
                    agentReply = "Noted as SAID. Next: arrangement — do you already live in Spain?",
                    now = now
                )
            }
            chip.contains("live in Spain", ignoreCase = true) -> {
                recordSaidFact(
                    store = store,
                    registry = registry,
                    factId = FACT_ARRANGEMENT_ID,
                    text = "I already live in Spain",
                    agentReply = "Noted as SAID. How long do you plan to work?",
                    now = now
                )
            }
            chip.contains("three months", ignoreCase = true) ||
                chip.contains("more than three", ignoreCase = true) -> {
                recordSaidFact(
                    store = store,
                    registry = registry,
                    factId = FACT_DURATION_ID,
                    text = "I plan to work for more than three months",
                    agentReply = "Noted as SAID. Have you already gathered documents?",
                    now = now
                )
            }
            chip.contains("not gathered documents", ignoreCase = true) ||
                chip.contains("documents yet", ignoreCase = true) -> {
                recordSaidFact(
                    store = store,
                    registry = registry,
                    factId = FACT_DOCUMENTS_ID,
                    text = "I have not gathered documents yet",
                    agentReply = "Noted as SAID. When ready, we can propose a sourced research checklist " +
                        "session — questions to verify on official portals, not invented law text.",
                    now = now
                )
            }
            chip.contains("sourced research checklist", ignoreCase = true) ||
                chip.contains("Build a sourced", ignoreCase = true) -> {
                proposeChecklistIfReady(store, registry, now)
            }
            chip.contains("Keep clarifying", ignoreCase = true) ||
                chip.contains("no checklist", ignoreCase = true) -> {
                if (!registry.isActionable(CapabilityRegistry.ID_CONTINUE_EXPLORING) &&
                    !registry.isActionable(CapabilityRegistry.ID_ADD_HYPOTHESIS)
                ) {
                    return
                }
                store.apply(
                    FlowCommand.AddHypothesis(
                        hypothesisId = "hyp-eu-work-keep-clarifying",
                        intentionId = INTENTION_ID,
                        text = "Still clarifying citizenship/arrangement before any checklist",
                        nowEpochMs = now
                    )
                )
                store.apply(
                    FlowCommand.AddConversationTurn(
                        turnId = "turn-eu-work-agent-clarify-$now",
                        intentionId = INTENTION_ID,
                        role = "agent",
                        text = "Fine. No checklist invented — keep confirming facts. " +
                            "Live web research is not available in this build.",
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

    private fun recordSaidFact(
        store: IntentionFlowStore,
        registry: CapabilityRegistry,
        factId: String,
        text: String,
        agentReply: String,
        now: Long
    ) {
        if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) return
        store.apply(
            FlowCommand.AddUserFact(
                factId = factId,
                intentionId = INTENTION_ID,
                text = text,
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-eu-work-agent-fact-$factId-$now",
                intentionId = INTENTION_ID,
                role = "agent",
                text = agentReply,
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

    private fun proposeChecklistIfReady(
        store: IntentionFlowStore,
        registry: CapabilityRegistry,
        now: Long
    ) {
        val snap = store.snapshot()
        val factIds = snap.facts.filter { it.intentionId == INTENTION_ID }.map { it.id }.toSet()
        val missing = mutableListOf<String>()
        if (FACT_CITIZENSHIP_ID !in factIds) missing += "citizenship"
        if (FACT_ARRANGEMENT_ID !in factIds) missing += "arrangement (live in Spain)"
        if (FACT_DURATION_ID !in factIds) missing += "work duration"
        if (FACT_DOCUMENTS_ID !in factIds) missing += "documents status"

        if (missing.isNotEmpty()) {
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-eu-work-agent-need-facts-$now",
                    intentionId = INTENTION_ID,
                    role = "agent",
                    text = "Checklist stays conditional until you confirm: ${missing.joinToString(", ")}. " +
                        "I will not invent requirements to fill gaps.",
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
            return
        }

        // Live web research is NOT_SUPPORTED — still allow a local checklist review session.
        val researchNote = registry.get(CapabilityRegistry.ID_WEB_RESEARCH)?.limitNote
            ?: "Live web research is not available."
        if (!registry.canProposeRecurringSession()) {
            store.apply(
                FlowCommand.AddConversationTurn(
                    turnId = "turn-eu-work-agent-no-schedule-$now",
                    intentionId = INTENTION_ID,
                    role = "agent",
                    text = registry.get(CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION)?.limitNote
                        ?: "Scheduling is not available. $researchNote",
                    nowEpochMs = now + 1
                )
            )
            return
        }
        val basedOn = listOf(
            FACT_CITIZENSHIP_ID,
            FACT_ARRANGEMENT_ID,
            FACT_DURATION_ID,
            FACT_DOCUMENTS_ID
        )
        store.apply(
            FlowCommand.ProposeRecurringSession(
                proposalId = PROPOSAL_ID,
                intentionId = INTENTION_ID,
                title = "Sourced checklist review",
                summary = "Proposed Monday 10:00 block to work through research questions " +
                    "(registration, social security, tax ID, employer notifications) against " +
                    "official Spanish/EU portals — with source URL + retrieval date per item. " +
                    "Not legal advice; no statutes invented. $researchNote " +
                    "Based on your SAID citizenship, residence, duration, and documents facts.",
                dayOfWeek = 1,
                hour = 10,
                minute = 0,
                basedOnFactIds = basedOn,
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-eu-work-agent-proposal-$now",
                intentionId = INTENTION_ID,
                role = "agent",
                text = "I can schedule a sourced checklist review from your confirmed facts. " +
                    "The checklist is research questions only — review the proposal?",
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
