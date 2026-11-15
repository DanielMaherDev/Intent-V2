package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.store.IntentionFlowStore

/**
 * Floating companion — same agent as Conversation, not a second bot.
 * Overlay-gated; press opens shared Conversation via [DiscussFromHome].
 */
enum class FloatingAgentState {
    IDLE,
    /** Stub — no live capture in this build. */
    CAPTURING,
    /** Stub — no live LLM thinking in this build. */
    THINKING,
    /** Stub — no streaming response surface in this build. */
    RESPONSE_READY,
    ATTENTION_NEEDED,
    ACTIVE_SESSION
}

object FloatingCompanion {

    data class Presence(
        /** Null when the bubble must not show (no overlay / registry locked). */
        val state: FloatingAgentState?,
        val label: String,
        val manualRouteNote: String?
    )

    /**
     * Resolve whether/how the floating surface appears.
     * Never invents a bubble when overlay is denied — callers use Home/Agent → Conversation.
     */
    fun presence(
        nextAction: NextAction,
        overlayGranted: Boolean,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Presence {
        if (!overlayGranted || !registry.canUseFloatingOverlay()) {
            return Presence(
                state = null,
                label = "Use Agent on Home",
                // Honest launcher fallback — never invents a bubble or overlay grant (OS-155).
                manualRouteNote = TalkBackLabels.overlayDeniedFallbackNote()
            )
        }
        val state = resolveState(nextAction)
        return Presence(
            state = state,
            label = labelFor(state),
            manualRouteNote = null
        )
    }

    fun resolveState(nextAction: NextAction): FloatingAgentState =
        when (nextAction.kind) {
            NextActionKind.ACTIVE_SESSION -> FloatingAgentState.ACTIVE_SESSION
            NextActionKind.REVIEW_PROPOSAL,
            NextActionKind.BEGIN,
            NextActionKind.CHECK_IN,
            NextActionKind.MIDDAY,
            NextActionKind.CHECK_OUT -> FloatingAgentState.ATTENTION_NEEDED
            else -> FloatingAgentState.IDLE
        }

    fun labelFor(state: FloatingAgentState): String =
        when (state) {
            FloatingAgentState.IDLE -> "INTENT"
            FloatingAgentState.CAPTURING -> "Listening…"
            FloatingAgentState.THINKING -> "Thinking…"
            FloatingAgentState.RESPONSE_READY -> "Reply ready"
            FloatingAgentState.ATTENTION_NEEDED -> "Needs you"
            FloatingAgentState.ACTIVE_SESSION -> "In session"
        }

    /**
     * Press the companion → open shared Conversation (same path as Home Discuss).
     */
    fun onPress(
        store: IntentionFlowStore,
        nextAction: NextAction,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): DiscussFromHome.OpenResult =
        DiscussFromHome.open(
            store = store,
            nextAction = nextAction,
            nowEpochMs = nowEpochMs,
            registry = registry
        )

    /**
     * Long-press / Capture — persist raw thought (SAID). Not a second bot; no LLM summary.
     */
    fun onCaptureThought(
        store: IntentionFlowStore,
        text: String,
        nowEpochMs: Long,
        source: String = CaptureThought.SOURCE_FLOATER
    ): CaptureThought.Result =
        CaptureThought.capture(
            store = store,
            text = text,
            source = source,
            nowEpochMs = nowEpochMs
        )
}
