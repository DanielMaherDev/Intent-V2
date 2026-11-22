package com.intent.launcher.engine

/**
 * Engineering hold so AVD / sideload can prove §8.9 Cancel **without** a live
 * OpenAI RTT (host key still HTTP 401).
 *
 * When [holdMs] > 0, [LauncherViewModel.onDiscussFreeText] delays (cancellable)
 * before calling Discuss / create heuristics. Cancel during the hold never
 * reaches the write path. This is **not** a fake model reply — it only
 * stretches the existing “Waiting for the model…” pending surface.
 *
 * Enable for a session via Intent extra [EXTRA_HOLD_MS] (e.g. AVD):
 * `adb shell am start -n com.intent.launcher/.MainActivity --el discuss_hold_ms 20000`
 *
 * Default is off (0) — production Discuss timing unchanged.
 */
object DiscussOfflineHold {
    const val EXTRA_HOLD_MS = "discuss_hold_ms"
    const val DEFAULT_AVD_HOLD_MS = 20_000L

    @Volatile
    private var holdMs: Long = 0L

    fun currentHoldMs(): Long = holdMs.coerceAtLeast(0L)

    fun setHoldMs(ms: Long) {
        holdMs = ms.coerceAtLeast(0L)
    }

    fun clear() {
        holdMs = 0L
    }

    /** True when an engineering hold is armed (AVD / debug script). */
    fun isArmed(): Boolean = currentHoldMs() > 0L

    fun applyFromIntentExtra(rawMs: Long) {
        if (rawMs < 0L) return
        setHoldMs(rawMs)
    }
}
