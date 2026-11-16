package com.intent.launcher.engine

/**
 * App-level reduced-motion preference — SharedPreferences / chrome only.
 * Independent of IntentionFlowStore / SCHEMA.
 *
 * Effective reduced motion always honors the platform Animator duration scale
 * (Accessibility → Remove animations → scale 0). The in-app preference can
 * **force** reduced motion on; it can never override the system off.
 */
enum class ReducedMotionPreference {
    /** Follow platform Animator duration scale only. */
    FOLLOW_SYSTEM,

    /** Prefer reduced motion even when the platform scale is non-zero. */
    PREFER_REDUCED;

    companion object {
        fun fromStorage(raw: String?): ReducedMotionPreference =
            entries.find { it.name == raw } ?: FOLLOW_SYSTEM
    }
}

/**
 * Pure reduced-motion resolution + animation gating helpers.
 * JVM-testable; no Android framework types.
 */
object ReducedMotion {
    /**
     * Platform reports reduced motion when Animator duration scale is zero
     * (typical "Remove animations" accessibility setting).
     */
    fun systemReduced(animatorDurationScale: Float): Boolean =
        animatorDurationScale <= 0f

    /**
     * Effective reduced-motion flag for UI branches.
     * System reduced always wins; [PREFER_REDUCED] forces on.
     */
    fun resolvesToReduced(
        preference: ReducedMotionPreference,
        animatorDurationScale: Float
    ): Boolean =
        systemReduced(animatorDurationScale) ||
            preference == ReducedMotionPreference.PREFER_REDUCED

    /**
     * Compose [MotionDurationScale] factor: 0 collapses tweens; otherwise mirror
     * the platform scale (capped at a sane floor so negative/NaN never animates).
     */
    fun motionDurationScaleFactor(
        preference: ReducedMotionPreference,
        animatorDurationScale: Float
    ): Float {
        if (resolvesToReduced(preference, animatorDurationScale)) return 0f
        return animatorDurationScale.coerceAtLeast(0f)
    }

    /** Enter / exit duration for Agent-module Conversation card transitions. */
    fun cardEnterMs(reduced: Boolean, fullMs: Int = CARD_ENTER_MS): Int =
        if (reduced) 0 else fullMs

    fun cardExitMs(reduced: Boolean, fullMs: Int = CARD_EXIT_MS): Int =
        if (reduced) 0 else fullMs

    /** Home ↔ Intention pager: jump instead of animate when reduced. */
    fun scrollPageInstantly(reduced: Boolean): Boolean = reduced

    const val CARD_ENTER_MS: Int = 180
    const val CARD_EXIT_MS: Int = 120
}
