package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore

/**
 * §17 case 31 — guided practice (“Let’s practise a customer call in Spanish.”).
 * Guided session with role, objective, and transcript/feedback linked to the
 * same Spanish intention — not a separate bot with disconnected memory.
 */
object GuidedPractice {

    const val ROLE_FACT_ID = "fact-guided-role"
    const val OBJECTIVE_FACT_ID = "fact-guided-objective"
    const val SESSION_MARKER_ID = "guided-session-customer-call"
    const val DEFAULT_ROLE = "Customer calling a Spanish-speaking support desk"
    const val DEFAULT_OBJECTIVE = "Practise a polite customer call in Spanish"

    data class Result(
        val intentionId: String,
        val role: String,
        val objective: String,
        val transcriptTurnIds: List<String>,
        val feedbackTurnId: String,
        val linkedToSpanishIntention: Boolean,
        val separateBotCreated: Boolean,
        val honestyNote: String
    )

    fun looksLikeGuidedPractice(utterance: String): Boolean {
        val t = utterance.trim().lowercase()
        return (t.contains("practise") || t.contains("practice")) &&
            (t.contains("customer call") || t.contains("role-play") || t.contains("roleplay") ||
                t.contains("guided"))
    }

    fun start(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        utterance: String = "Let's practise a customer call in Spanish.",
        role: String = DEFAULT_ROLE,
        objective: String = DEFAULT_OBJECTIVE
    ): Result {
        SpanishScenario.ensureSeeded(store, nowEpochMs)
        store.apply(
            FlowCommand.SetActiveIntention(
                intentionId = SpanishScenario.INTENTION_ID,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-guided-user-$nowEpochMs",
                intentionId = SpanishScenario.INTENTION_ID,
                role = "user",
                text = utterance,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddUserFact(
                factId = ROLE_FACT_ID,
                intentionId = SpanishScenario.INTENTION_ID,
                text = "Guided practice role: $role",
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddUserFact(
                factId = OBJECTIVE_FACT_ID,
                intentionId = SpanishScenario.INTENTION_ID,
                text = "Guided practice objective: $objective",
                nowEpochMs = nowEpochMs + 1
            )
        )
        val openId = "turn-guided-open-$nowEpochMs"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = openId,
                intentionId = SpanishScenario.INTENTION_ID,
                role = "agent",
                text = "Guided session linked to Learn Spanish. Role: $role. Objective: $objective. " +
                    "I stay in this intention’s memory — not a separate bot.",
                nowEpochMs = nowEpochMs + 2,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-guided-open-$nowEpochMs",
                        type = ResponseBlockType.EXPLANATION,
                        title = "Guided practice",
                        body = "Role + objective attached to ${SpanishScenario.INTENTION_ID}",
                        sourceLabel = "guided_practice",
                        linkedObjectIds = listOf(
                            SpanishScenario.INTENTION_ID,
                            ROLE_FACT_ID,
                            OBJECTIVE_FACT_ID,
                            SESSION_MARKER_ID
                        ),
                        actions = listOf("Continue practice", "End practice"),
                        active = true
                    )
                )
            )
        )
        val userLineId = "turn-guided-line-user-$nowEpochMs"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = userLineId,
                intentionId = SpanishScenario.INTENTION_ID,
                role = "user",
                text = "Hola, llamo porque tengo una pregunta sobre mi pedido.",
                nowEpochMs = nowEpochMs + 3,
                modality = "GUIDED_PRACTICE"
            )
        )
        val agentLineId = "turn-guided-line-agent-$nowEpochMs"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = agentLineId,
                intentionId = SpanishScenario.INTENTION_ID,
                role = "agent",
                text = "Buenos días, claro — ¿me puede dar el número de pedido?",
                nowEpochMs = nowEpochMs + 4,
                modality = "GUIDED_PRACTICE"
            )
        )
        val feedbackId = "turn-guided-feedback-$nowEpochMs"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = feedbackId,
                intentionId = SpanishScenario.INTENTION_ID,
                role = "agent",
                text = "Feedback (same intention): clear opening; next, practise confirming the order number. " +
                    "Transcript stays on Learn Spanish.",
                nowEpochMs = nowEpochMs + 5,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-guided-feedback-$nowEpochMs",
                        type = ResponseBlockType.PROGRESS,
                        title = "Practice feedback",
                        body = "Linked transcript + feedback on ${SpanishScenario.TITLE}",
                        sourceLabel = "guided_practice_feedback",
                        linkedObjectIds = listOf(
                            SpanishScenario.INTENTION_ID,
                            userLineId,
                            agentLineId,
                            SESSION_MARKER_ID
                        ),
                        actions = emptyList(),
                        active = true
                    )
                )
            )
        )
        val snap = store.snapshot()
        val separateBot = snap.intentions.any {
            it.id != SpanishScenario.INTENTION_ID &&
                (it.title.contains("customer call", ignoreCase = true) ||
                    it.title.contains("guided", ignoreCase = true))
        }
        return Result(
            intentionId = SpanishScenario.INTENTION_ID,
            role = role,
            objective = objective,
            transcriptTurnIds = listOf(openId, userLineId, agentLineId),
            feedbackTurnId = feedbackId,
            linkedToSpanishIntention = snap.facts
                .filter { it.id == ROLE_FACT_ID || it.id == OBJECTIVE_FACT_ID }
                .all { it.intentionId == SpanishScenario.INTENTION_ID },
            separateBotCreated = separateBot,
            honestyNote = "Guided session memory stays on the Spanish intention."
        )
    }
}
