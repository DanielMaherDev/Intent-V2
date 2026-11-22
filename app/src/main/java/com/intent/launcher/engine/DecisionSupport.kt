package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.SuggestedChip
import com.intent.launcher.domain.AnswerSchema
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.Intention
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone

/**
 * Generic time-limited **decision** support — compare criteria before a decide-by
 * date; never accept/decline for the user. Shared by create → adaptive Ask cards
 * and Discuss free-text / chips (not job-offer-title hardcoding).
 */
object DecisionSupport {

    const val CHIP_UPDATE_COMPARISON = "Update the comparison"
    const val CHIP_ASK_FOLLOW_UP = "Ask a follow-up question"
    const val CHIP_DISCUSS_FLEXIBILITY = "Discuss how much flexibility I need"
    const val CHIP_ASK_SOURCE = "Ask the source about the unknown"

    val followUpChips: List<SuggestedChip> = listOf(
        SuggestedChip(CHIP_UPDATE_COMPARISON, CapabilityRegistry.ID_RECORD_FACT),
        SuggestedChip(CHIP_ASK_FOLLOW_UP, CapabilityRegistry.ID_CONTINUE_EXPLORING),
        SuggestedChip(CHIP_DISCUSS_FLEXIBILITY, CapabilityRegistry.ID_CONTINUE_EXPLORING),
        SuggestedChip(CHIP_ASK_SOURCE, CapabilityRegistry.ID_CONTINUE_EXPLORING)
    )

    fun conversationChips(registry: CapabilityRegistry = CapabilityRegistry.default): List<String> =
        registry.filterChips(followUpChips)

    /** Source-reported / third-party fact that should refresh comparison, not auto-decide. */
    fun looksLikeSourceReportedFact(text: String): Boolean {
        val t = text.lowercase()
        val sourceCue = listOf(
            "employer says", "they say", "they said", "offer says",
            "recruiter", "hr says", "company says", "told me",
            "confirmed that", "just learned", "new info", "update:"
        ).any { t.contains(it) }
        val factCue = listOf(
            "office", "remote", "hybrid", "salary", "pay", "comp",
            "days", "equity", "title", "location", "hours"
        ).any { t.contains(it) }
        return sourceCue || (factCue && (t.contains("says") || t.contains("said") ||
            t.contains("will be") || t.contains("is ")))
    }

    fun looksLikeUpdateComparison(text: String): Boolean {
        val t = text.lowercase()
        return t.contains("update the comparison") ||
            t.contains("update comparison") ||
            (t.contains("update") && t.contains("compar"))
    }

    /**
     * Persist a SAID source-labelled fact and project an Ask to update comparison
     * (or ask follow-up / discuss flexibility). Never marks accept/decline.
     */
    fun ingestSourceFact(
        store: IntentionFlowStore,
        intention: Intention,
        text: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default, // reserved for capability gates
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val intentionId = intention.id
        ensureAdaptive(store, intentionId, nowEpochMs)

        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-decision-user-$nowEpochMs",
                intentionId = intentionId,
                role = "user",
                text = text,
                nowEpochMs = nowEpochMs
            )
        )

        if (!registry.isActionable(CapabilityRegistry.ID_RECORD_FACT)) {
            return "Record-fact capability is not available — comparison unchanged."
        }

        var t = nowEpochMs + 1
        fun rev(): Long =
            store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L

        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = intentionId,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-decision-source-$intentionId-$t",
                    text = text.trim(),
                    key = AdaptiveBuilderEngine.KEY_DECISION_SOURCE_FACT,
                    rawUtterance = text.trim(),
                    normalizedValue = text.trim(),
                    approximate = false
                ),
                sourceTurnId = "turn-decision-source-$t"
            ),
            t,
            timeZone = timeZone
        )
        // Label provenance in agent prose — engine facts stay SAID (user-reported).
        t += 1

        val ask = BuilderDecision.Ask(
            intentionId = intentionId,
            expectedRevision = rev(),
            questionId = "q-comparison-refresh-$intentionId-$t",
            whyItMatters =
                "Source-reported information may change a criterion you ranked. " +
                    "INTENT will not decide for you — update the comparison, ask a " +
                    "follow-up, or discuss how much flexibility you need.",
            answerSchema = AnswerSchema.SINGLE_CHOICE,
            purpose = AdaptiveBuilderEngine.KEY_COMPARISON_REFRESH,
            blocksWhat = "decision_review",
            options = listOf(
                CHIP_UPDATE_COMPARISON,
                CHIP_ASK_FOLLOW_UP,
                CHIP_DISCUSS_FLEXIBILITY
            ),
            priority = 95
        )
        AdaptiveBuilderEngine.applyDecision(store, ask, t, timeZone = timeZone)

        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-decision-agent-$t",
                intentionId = intentionId,
                role = "agent",
                text =
                    "I'll add that as source-reported information (SAID — not observed). " +
                        "It may affect a criterion you care about. Would you like to " +
                        "update the comparison, ask a follow-up question, or discuss " +
                        "how much flexibility you need?",
                nowEpochMs = t + 1,
                blocks = listOf(BuilderAnswerCard.blockForAsk(ask, t + 1))
            )
        )
        return "Source-reported fact saved — comparison refresh offered."
    }

    fun onChip(
        store: IntentionFlowStore,
        intention: Intention,
        chip: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default, // reserved for capability gates
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val intentionId = intention.id
        ensureAdaptive(store, intentionId, nowEpochMs)
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-decision-chip-user-$nowEpochMs",
                intentionId = intentionId,
                role = "user",
                text = chip,
                nowEpochMs = nowEpochMs
            )
        )

        val agentText = when {
            looksLikeUpdateComparison(chip) || chip.equals(CHIP_UPDATE_COMPARISON, ignoreCase = true) -> {
                markComparisonUpdated(store, intentionId, nowEpochMs + 1, timeZone)
                "Comparison updated with your latest source-reported facts. " +
                    "No accept/decline chosen — review stays open until your decide-by date."
            }
            chip.equals(CHIP_ASK_FOLLOW_UP, ignoreCase = true) ||
                chip.equals(CHIP_ASK_SOURCE, ignoreCase = true) ->
                "Good next step: ask the source about the unresolved item before you decide. " +
                    "I will not mark the decision as accepted."
            chip.equals(CHIP_DISCUSS_FLEXIBILITY, ignoreCase = true) ->
                "Flexibility stays a criterion you weigh — tell me how much you need, " +
                    "and we can adjust the comparison. INTENT still will not choose for you."
            else ->
                "Noted. Comparison support only — INTENT does not accept or decline for you."
        }

        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-decision-chip-agent-${nowEpochMs + 1}",
                intentionId = intentionId,
                role = "agent",
                text = agentText,
                nowEpochMs = nowEpochMs + 1
            )
        )
        return agentText
    }

    private fun markComparisonUpdated(
        store: IntentionFlowStore,
        intentionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone
    ) {
        fun rev(): Long =
            store.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == intentionId }?.revision ?: 0L
        val already = store.snapshot().facts.any {
            it.intentionId == intentionId &&
                it.key == AdaptiveBuilderEngine.KEY_COMPARISON_REFRESH &&
                it.status == FactStatus.ACTIVE
        }
        if (already) return
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = intentionId,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-comparison-refresh-$intentionId-$nowEpochMs",
                    text = CHIP_UPDATE_COMPARISON,
                    key = AdaptiveBuilderEngine.KEY_COMPARISON_REFRESH,
                    rawUtterance = CHIP_UPDATE_COMPARISON,
                    normalizedValue = "updated"
                ),
                sourceTurnId = "turn-comparison-refresh-$nowEpochMs"
            ),
            nowEpochMs,
            timeZone = timeZone
        )
    }

    private fun ensureAdaptive(
        store: IntentionFlowStore,
        intentionId: String,
        nowEpochMs: Long
    ) {
        val existing = store.snapshot().adaptiveBuilderStates
            .firstOrNull { it.intentionId == intentionId }
        if (existing != null) return
        store.apply(
            FlowCommand.SetAdaptiveBuilderState(
                state = com.intent.launcher.domain.AdaptiveBuilderState(
                    intentionId = intentionId,
                    revision = 0L,
                    typeFacets = AdaptiveBuilderEngine.facetsForShape("decision"),
                    lifecycleHint = "discovery",
                    updatedAtEpochMs = nowEpochMs
                ),
                nowEpochMs = nowEpochMs
            )
        )
    }
}
