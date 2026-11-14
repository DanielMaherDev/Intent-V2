package com.intent.launcher.domain

/**
 * Honest local-clock elapsed time for an in-progress [SessionRun].
 * Uses only [startedAtEpochMs] and a caller-supplied [nowEpochMs] —
 * no server timer, fake progress, or estimated remaining duration.
 */
object SessionElapsed {

    /**
     * Formats elapsed wall time as `M:SS` under one hour, or `H:MM:SS` thereafter.
     * Negative or zero deltas clamp to `0:00`.
     */
    fun format(startedAtEpochMs: Long, nowEpochMs: Long): String {
        val elapsedMs = (nowEpochMs - startedAtEpochMs).coerceAtLeast(0L)
        val totalSeconds = elapsedMs / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%d:%02d".format(minutes, seconds)
        }
    }

    /** Short accessibility / pill label, e.g. `Elapsed 5:03`. */
    fun label(startedAtEpochMs: Long, nowEpochMs: Long): String =
        "Elapsed ${format(startedAtEpochMs, nowEpochMs)}"
}
