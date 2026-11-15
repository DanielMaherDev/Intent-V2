package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.NextAction
import com.intent.launcher.store.IntentionFlowStore

/**
 * Floater long-press drawer — Ask / Today / Capture.
 * Same agent as Conversation; not a second bot. No LLM unless live_llm LIVE.
 */
enum class FloaterDrawerAction {
    ASK,
    TODAY,
    CAPTURE
}

object FloaterActionDrawer {

    data class Item(
        val action: FloaterDrawerAction,
        val label: String,
        val detail: String
    )

    data class OpenAskResult(
        val navigateTo: String?,
        val intentionId: String?,
        /** Optional context line for Conversation — intention title, not an LLM prompt. */
        val contextChip: String?,
        /** Shared with floater / Conversation when an intervention is active. */
        val interventionJobId: String? = null,
        val interventionTurnId: String? = null
    )

    data class OpenTodayResult(
        val navigateTo: String,
        /** One-liner for the dedicated Today screen (not a second agent). */
        val focusNote: String
    )

    val items: List<Item> = listOf(
        Item(
            action = FloaterDrawerAction.ASK,
            label = "Ask",
            detail = "Open Conversation — same agent, chips path (no live LLM unless available)."
        ),
        Item(
            action = FloaterDrawerAction.TODAY,
            label = "Today",
            detail = "Open Today — planned, completed, and deferred from the day cycle."
        ),
        Item(
            action = FloaterDrawerAction.CAPTURE,
            label = "Capture",
            detail = "Save a raw thought (SAID) — no summary."
        )
    )

    fun openAsk(
        store: IntentionFlowStore,
        nextAction: NextAction,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): OpenAskResult {
        val result = FloatingCompanion.onPress(
            store = store,
            nextAction = nextAction,
            nowEpochMs = nowEpochMs,
            registry = registry
        )
        val title = store.snapshot().intentions
            .firstOrNull { it.id == result.intentionId }
            ?.title
            ?.trim()
            .orEmpty()
        val chip = title.takeIf { it.isNotEmpty() }?.let { "About: $it" }
        return OpenAskResult(
            navigateTo = result.navigateTo,
            intentionId = result.intentionId,
            contextChip = chip,
            interventionJobId = result.interventionJobId,
            interventionTurnId = result.interventionTurnId
        )
    }

    fun openToday(nextAction: NextAction): OpenTodayResult {
        val label = nextAction.label.trim().ifEmpty { "next action" }
        return OpenTodayResult(
            navigateTo = TODAY_ROUTE,
            focusNote = "Today — focus: $label. Planned · completed · deferred from your day cycle — not chat prose."
        )
    }

    const val HOME_ROUTE = "home"
    const val TODAY_ROUTE = "today"
}
