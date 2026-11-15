package com.intent.launcher.engine

/**
 * OS-144 — decide what system / chrome back should do.
 *
 * Never finish the HOME activity from an empty stack, never silently end a live
 * Discuss/voice engagement, and never abandon an active SessionCard via back.
 */
object BackNavigationPolicy {

    const val ROUTE_HOME = "home"
    const val ROUTE_DRAWER = "drawer"
    const val ROUTE_CONVERSATION = "conversation"

    enum class Action {
        /** Stay put — Home root / SessionCard must not finish or abandon. */
        CONSUME_STAY,

        /** Close floater drawer, thought capture, defer confirm, leave-live dialog. */
        DISMISS_OVERLAY,

        /** Normal Navigation Compose pop. */
        POP_BACK_STACK,

        /** Clear to Home when pop would strand or from Intention drawer. */
        NAVIGATE_HOME,

        /**
         * Live voice / Discuss engagement is active on Conversation —
         * show confirm (Minimize vs End). Never hang up silently.
         */
        CONFIRM_LEAVE_LIVE
    }

    data class Context(
        /** Current NavHost route (destination.route). */
        val route: String?,
        /** True when [androidx.navigation.NavController.previousBackStackEntry] exists. */
        val canPopBackStack: Boolean,
        /** Floater drawer, thought capture, defer confirm, or leave-live dialog visible. */
        val overlayOpen: Boolean,
        /**
         * Voice STT awaiting result, or a future live Discuss call connected.
         * Honest: this build has no eternal call FGS — only in-flight STT today.
         */
        val liveEngagementActive: Boolean
    )

    fun resolve(ctx: Context): Action {
        if (ctx.overlayOpen) return Action.DISMISS_OVERLAY

        val route = ctx.route ?: ROUTE_HOME

        if (route == ROUTE_HOME) {
            // HOME launcher: never finish Activity; SessionCard Complete/Abandon stay explicit.
            return Action.CONSUME_STAY
        }

        if (route == ROUTE_DRAWER) {
            return Action.NAVIGATE_HOME
        }

        if (route == ROUTE_CONVERSATION && ctx.liveEngagementActive) {
            return Action.CONFIRM_LEAVE_LIVE
        }

        return if (ctx.canPopBackStack) Action.POP_BACK_STACK else Action.NAVIGATE_HOME
    }

    /**
     * Whether leaving Conversation requires an explicit Minimize / End choice.
     * Used by chrome Back and system Back alike.
     */
    fun conversationNeedsLeaveConfirm(liveEngagementActive: Boolean): Boolean =
        liveEngagementActive
}
