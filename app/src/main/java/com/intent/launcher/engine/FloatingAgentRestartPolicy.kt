package com.intent.launcher.engine

/**
 * Gate D / OS-155 — sticky resume honesty for [com.intent.launcher.floating.FloatingAgentService].
 *
 * Pure policy (no Android deps): never invents overlay grant; sticky only while overlay is granted.
 */
object FloatingAgentRestartPolicy {

    enum class StartMode {
        /** Overlay granted — show bubble / FGS; system may restart after kill. */
        STICKY_WITH_OVERLAY,

        /** Overlay denied or revoked — stop; do not sticky-restart a fake bubble. */
        STOP_NOT_STICKY
    }

    data class Decision(
        val mode: StartMode,
        /** State to apply when sticky-restarting with a null intent (defaults to IDLE). */
        val resolvedState: FloatingAgentState?,
        /** Spoken / Home fallback note when the bubble must not appear. */
        val fallbackNote: String?
    )

    /**
     * @param overlayGranted live [Settings.canDrawOverlays] (or test double)
     * @param requestedState from start intent extra, or null on sticky restart
     */
    fun decide(
        overlayGranted: Boolean,
        requestedState: FloatingAgentState?
    ): Decision {
        if (!overlayGranted) {
            return Decision(
                mode = StartMode.STOP_NOT_STICKY,
                resolvedState = null,
                fallbackNote = TalkBackLabels.overlayDeniedFallbackNote()
            )
        }
        return Decision(
            mode = StartMode.STICKY_WITH_OVERLAY,
            resolvedState = requestedState ?: FloatingAgentState.IDLE,
            fallbackNote = null
        )
    }

    /**
     * After an unexpected kill while overlay was granted, sticky restart is honest.
     * If overlay was revoked while dead, [decide] returns STOP_NOT_STICKY — never invent grant.
     */
    fun documentsStickyRestart(): String =
        "FloatingAgentService returns START_STICKY only while overlay is granted. " +
            "On sticky restart with a null intent, state defaults to IDLE and overlay is re-checked. " +
            "If overlay is denied, the service stops with START_NOT_STICKY and Home / Companion menu " +
            "remain the Conversation path — no floating bubble is invented."
}
