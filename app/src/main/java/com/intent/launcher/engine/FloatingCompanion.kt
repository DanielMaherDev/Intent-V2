package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.QueuedIntervention
import com.intent.launcher.store.IntentionFlowStore

/**
 * Floating companion — same agent as Conversation, not a second bot.
 * Overlay-gated; press opens shared Conversation via [DiscussFromHome].
 * Queued interventions share turnId / jobId with Conversation (Gate D).
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
        val manualRouteNote: String?,
        /** Shared with Conversation when a proactive intervention is queued. */
        val interventionJobId: String? = null,
        val interventionTurnId: String? = null,
        val unreadIntervention: Boolean = false,
        /** OBSERVED | INFERRED — never blank when an intervention is active (Gate D2). */
        val interventionProvenanceLabel: String? = null,
        /** Short floater attention line including provenance chip. */
        val interventionAttentionLine: String? = null
    )

    /**
     * Resolve whether/how the floating surface appears.
     * Never invents a bubble when overlay is denied — callers use Home/Agent → Conversation.
     */
    fun presence(
        nextAction: NextAction,
        overlayGranted: Boolean,
        registry: CapabilityRegistry = CapabilityRegistry.default,
        snapshot: IntentionSnapshot? = null
    ): Presence {
        val intervention = snapshot?.let { TriggerCooldownPolicy.activeIntervention(it) }
        val prompt = intervention?.let { InterventionProvenance.fromQueued(it) }
        if (!overlayGranted || !registry.canUseFloatingOverlay()) {
            return Presence(
                state = null,
                label = "Use Agent on Home",
                // Honest launcher fallback — never invents a bubble or overlay grant (OS-155).
                manualRouteNote = TalkBackLabels.overlayDeniedFallbackNote(),
                interventionJobId = intervention?.jobId,
                interventionTurnId = intervention?.turnId,
                unreadIntervention = intervention?.status == "queued",
                interventionProvenanceLabel = prompt?.provenanceLabel,
                interventionAttentionLine = prompt?.let {
                    InterventionProvenance.floaterAttentionLine(it)
                }
            )
        }
        val state = resolveState(nextAction, intervention)
        return Presence(
            state = state,
            label = labelFor(state, prompt),
            manualRouteNote = null,
            interventionJobId = intervention?.jobId,
            interventionTurnId = intervention?.turnId,
            unreadIntervention = intervention?.status == "queued",
            interventionProvenanceLabel = prompt?.provenanceLabel,
            interventionAttentionLine = prompt?.let {
                InterventionProvenance.floaterAttentionLine(it)
            }
        )
    }

    fun resolveState(
        nextAction: NextAction,
        intervention: QueuedIntervention? = null
    ): FloatingAgentState {
        if (intervention != null) {
            return when (intervention.status) {
                "queued" -> FloatingAgentState.ATTENTION_NEEDED
                "shown" -> FloatingAgentState.RESPONSE_READY
                else -> FloatingAgentState.IDLE
            }.let { interventionState ->
                // Active session still wins for chrome when no unread intervention.
                if (nextAction.kind == NextActionKind.ACTIVE_SESSION &&
                    intervention.status != "queued"
                ) {
                    FloatingAgentState.ACTIVE_SESSION
                } else {
                    interventionState
                }
            }
        }
        return when (nextAction.kind) {
            NextActionKind.ACTIVE_SESSION -> FloatingAgentState.ACTIVE_SESSION
            NextActionKind.REVIEW_PROPOSAL,
            NextActionKind.BEGIN,
            NextActionKind.CHECK_IN,
            NextActionKind.MIDDAY,
            NextActionKind.CHECK_OUT -> FloatingAgentState.ATTENTION_NEEDED
            else -> FloatingAgentState.IDLE
        }
    }

    fun labelFor(
        state: FloatingAgentState,
        intervention: InterventionProvenance.PromptView? = null
    ): String =
        when (state) {
            FloatingAgentState.IDLE -> "INTENT"
            FloatingAgentState.CAPTURING -> "Listening…"
            FloatingAgentState.THINKING -> "Thinking…"
            FloatingAgentState.RESPONSE_READY ->
                intervention?.let { "${it.provenanceLabel} · Reply" } ?: "Reply ready"
            FloatingAgentState.ATTENTION_NEEDED ->
                intervention?.let { InterventionProvenance.floaterAttentionLine(it) }
                    ?: "Needs you"
            FloatingAgentState.ACTIVE_SESSION -> "In session"
        }

    /**
     * Press the companion → open shared Conversation (same path as Home Discuss).
     * When an intervention is queued, returns the same turnId / jobId Conversation uses.
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
