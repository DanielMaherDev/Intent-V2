package com.intent.launcher.engine

/**
 * Gate D / OS-155 / D3 — sticky resume honesty + overlay/service interrupt takeover.
 *
 * Pure policy (no Android deps): never invents overlay grant; sticky only while overlay
 * is granted. When overlay is revoked (or sticky-restart finds it denied) after the
 * companion was active, posts a **permitted** notification that opens Conversation —
 * reusing intervention turn/job ids when an intervention is active. Work stays in
 * [com.intent.launcher.store.IntentionFlowStore]; the bubble is not reinvented.
 */
object FloatingAgentRestartPolicy {

    enum class StartMode {
        /** Overlay granted — show bubble / FGS; system may restart after kill. */
        STICKY_WITH_OVERLAY,

        /** Overlay denied or revoked — stop; do not sticky-restart a fake bubble. */
        STOP_NOT_STICKY
    }

    /**
     * Why the companion is stopping (or continuing). Used by the service to choose
     * whether to post a takeover notification.
     */
    enum class InterruptReason {
        NONE,
        /** Overlay was revoked while the companion had been running. */
        OVERLAY_REVOKED,
        /** Sticky restart after process death found overlay denied. */
        PROCESS_DEATH_OVERLAY_DENIED,
        /** Overlay never granted / sync asked to stop without prior bubble. */
        OVERLAY_DENIED
    }

    data class InterventionDeepLink(
        val jobId: String?,
        val turnId: String?
    ) {
        val hasActiveIds: Boolean get() = !jobId.isNullOrBlank() || !turnId.isNullOrBlank()
    }

    data class Decision(
        val mode: StartMode,
        /** State to apply when sticky-restarting with a null intent (defaults to IDLE). */
        val resolvedState: FloatingAgentState?,
        /** Spoken / Home fallback note when the bubble must not appear. */
        val fallbackNote: String?,
        val interruptReason: InterruptReason = InterruptReason.NONE,
        /**
         * True when a permitted notification should take over for the stopped bubble
         * (Gate D3). Caller still checks notification permission before posting.
         */
        val postTakeoverNotification: Boolean = false,
        val deepLink: InterventionDeepLink = InterventionDeepLink(null, null),
        /** Notification title / body when [postTakeoverNotification] is true. */
        val notificationTitle: String? = null,
        val notificationText: String? = null
    )

    /**
     * @param overlayGranted live [Settings.canDrawOverlays] (or test double)
     * @param requestedState from start intent extra, or null on sticky restart
     * @param wasRunningWithOverlay true when this process previously showed the bubble/FGS
     * @param stickyRestart true when [Service.onStartCommand] received a null intent
     * @param notificationsPermitted POST_NOTIFICATIONS / areNotificationsEnabled
     * @param interventionJobId / interventionTurnId active Gate D intervention ids (nullable)
     * @param savedWorkSummary short honesty line for the takeover notice (nullable)
     */
    fun decide(
        overlayGranted: Boolean,
        requestedState: FloatingAgentState?,
        wasRunningWithOverlay: Boolean = false,
        stickyRestart: Boolean = false,
        notificationsPermitted: Boolean = true,
        interventionJobId: String? = null,
        interventionTurnId: String? = null,
        savedWorkSummary: String? = null
    ): Decision {
        val deepLink = InterventionDeepLink(
            jobId = interventionJobId?.takeIf { it.isNotBlank() },
            turnId = interventionTurnId?.takeIf { it.isNotBlank() }
        )
        if (overlayGranted) {
            return Decision(
                mode = StartMode.STICKY_WITH_OVERLAY,
                resolvedState = requestedState ?: FloatingAgentState.IDLE,
                fallbackNote = null,
                interruptReason = InterruptReason.NONE,
                postTakeoverNotification = false,
                deepLink = deepLink
            )
        }

        val reason = when {
            wasRunningWithOverlay -> InterruptReason.OVERLAY_REVOKED
            stickyRestart -> InterruptReason.PROCESS_DEATH_OVERLAY_DENIED
            else -> InterruptReason.OVERLAY_DENIED
        }
        val shouldPost = notificationsPermitted &&
            (reason == InterruptReason.OVERLAY_REVOKED ||
                reason == InterruptReason.PROCESS_DEATH_OVERLAY_DENIED ||
                deepLink.hasActiveIds)
        val title = if (shouldPost) takeoverTitle(deepLink) else null
        val text = if (shouldPost) {
            takeoverText(deepLink = deepLink, savedWorkSummary = savedWorkSummary, reason = reason)
        } else {
            null
        }
        val fallback = when (reason) {
            InterruptReason.OVERLAY_REVOKED,
            InterruptReason.PROCESS_DEATH_OVERLAY_DENIED ->
                TalkBackLabels.serviceStoppedFallbackNote()
            else -> TalkBackLabels.overlayDeniedFallbackNote()
        }
        return Decision(
            mode = StartMode.STOP_NOT_STICKY,
            resolvedState = null,
            fallbackNote = fallback,
            interruptReason = reason,
            postTakeoverNotification = shouldPost,
            deepLink = deepLink,
            notificationTitle = title,
            notificationText = text
        )
    }

    fun takeoverTitle(deepLink: InterventionDeepLink): String =
        if (deepLink.hasActiveIds) {
            "Saved work — open Conversation"
        } else {
            "Companion paused — work saved"
        }

    fun takeoverText(
        deepLink: InterventionDeepLink,
        savedWorkSummary: String?,
        reason: InterruptReason
    ): String {
        val cause = when (reason) {
            InterruptReason.OVERLAY_REVOKED ->
                "Overlay permission was revoked — the floating bubble stopped."
            InterruptReason.PROCESS_DEATH_OVERLAY_DENIED ->
                "The companion process stopped and overlay is not granted."
            else ->
                "The floating bubble is not available."
        }
        val work = savedWorkSummary?.takeIf { it.isNotBlank() }
            ?: if (deepLink.hasActiveIds) {
                "Your intervention and IntentionFlowStore changes are still on this device."
            } else {
                "IntentionFlowStore changes are still on this device."
            }
        val path = if (deepLink.hasActiveIds) {
            "Tap to open Conversation for the same intervention " +
                "(job ${deepLink.jobId ?: "—"}, turn ${deepLink.turnId ?: "—"})."
        } else {
            "Tap to open Conversation, or use Home’s Companion menu."
        }
        return "$cause $work $path"
    }

    /**
     * After an unexpected kill while overlay was granted, sticky restart is honest.
     * If overlay was revoked while dead, [decide] returns STOP_NOT_STICKY — never invent grant —
     * and may post a permitted takeover notification for saved work.
     */
    fun documentsStickyRestart(): String =
        "FloatingAgentService returns START_STICKY only while overlay is granted. " +
            "On sticky restart with a null intent, state defaults to IDLE and overlay is re-checked. " +
            "If overlay is denied, the service stops with START_NOT_STICKY, posts a permitted " +
            "notification that opens Conversation (reusing intervention turn/job ids when active), " +
            "and Home / Companion menu remain the Conversation path — no floating bubble is invented. " +
            "IntentionFlowStore mutations survive process kill via atomic persist + tmp recovery."
}
