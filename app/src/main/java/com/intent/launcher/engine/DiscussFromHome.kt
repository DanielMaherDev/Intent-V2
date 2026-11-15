package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.NextAction
import com.intent.launcher.store.IntentionFlowStore

/**
 * Home **Discuss** command path: resolve the intention behind the current
 * next action (scheduled Begin/Discuss chrome or active SessionCard) and open
 * Conversation for that context. Does not end or alter [SessionRun] state —
 * Complete / Abandon remain available after the user returns.
 */
object DiscussFromHome {

    const val CONVERSATION_ROUTE = "conversation"

    data class OpenResult(
        /** Nav route when Discuss may open; null when locked or no intention. */
        val navigateTo: String?,
        val intentionId: String?,
        val sessionId: String? = null,
        /** True when [FlowCommand.SetActiveIntention] was applied for context. */
        val activatedIntention: Boolean = false
    )

    /**
     * Prepare Conversation navigation for Home Discuss.
     * Activates [NextAction.intentionId] when it differs from the store's active id
     * so Conversation reads the scheduled / in-progress intention.
     */
    fun open(
        store: IntentionFlowStore,
        nextAction: NextAction,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): OpenResult {
        if (!registry.canDiscuss()) {
            return OpenResult(navigateTo = null, intentionId = null)
        }
        val snap = store.snapshot()
        val intentionId = nextAction.intentionId
            ?: snap.activeIntentionId
            ?: snap.intentions.firstOrNull()?.id
            ?: return OpenResult(navigateTo = null, intentionId = null)

        val activated = if (snap.activeIntentionId != intentionId) {
            store.apply(
                FlowCommand.SetActiveIntention(
                    intentionId = intentionId,
                    nowEpochMs = nowEpochMs
                )
            )
            true
        } else {
            false
        }

        return OpenResult(
            navigateTo = CONVERSATION_ROUTE,
            intentionId = intentionId,
            sessionId = nextAction.sessionId,
            activatedIntention = activated
        )
    }
}
