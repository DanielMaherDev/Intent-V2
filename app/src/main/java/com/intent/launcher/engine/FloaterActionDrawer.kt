package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.NextAction
import com.intent.launcher.store.IntentionFlowStore

/**
 * Companion sheet — Ask agent / Today / Check in / Check out / Capture a thought.
 * Same agent as Conversation; not a second bot. No model unless live_llm LIVE.
 *
 * [HISTORY] stays for notification deep-links; it is not a companion sheet row
 * (History lives under Settings per S-SETTINGS).
 */
enum class FloaterDrawerAction {
    ASK,
    TODAY,
    CHECK_IN,
    CHECK_OUT,
    CAPTURE,
    HISTORY
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

    /** S-COMPANION sheet rows, in order. */
    val items: List<Item> = listOf(
        Item(
            action = FloaterDrawerAction.ASK,
            label = "Ask agent",
            detail = "Open Conversation — same agent as Home."
        ),
        Item(
            action = FloaterDrawerAction.TODAY,
            label = "Today",
            detail = "Planned, completed, and deferred from your day."
        ),
        Item(
            action = FloaterDrawerAction.CHECK_IN,
            label = "Check in",
            detail = "Confirm today’s focus."
        ),
        Item(
            action = FloaterDrawerAction.CHECK_OUT,
            label = "Check out",
            detail = "Say what happened, then keep or adjust the schedule."
        ),
        Item(
            action = FloaterDrawerAction.CAPTURE,
            label = "Capture a thought",
            detail = "Save the raw text or audio with the time."
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
            focusNote = "Today — focus: $label. Planned · completed · deferred from your day cycle."
        )
    }

    const val HOME_ROUTE = "home"
    const val TODAY_ROUTE = "today"
    const val HISTORY_ROUTE = "history"
}
