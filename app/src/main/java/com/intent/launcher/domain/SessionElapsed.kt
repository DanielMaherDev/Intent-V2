package com.intent.launcher.domain

/**
 * Honest local-clock elapsed time for an in-progress [SessionRun].
 * Uses [startedAtEpochMs], optional soft-pause fields, and a caller-supplied
 * [nowEpochMs] — no server timer, fake progress, or estimated remaining duration.
 */
object SessionElapsed {

    /**
     * Active elapsed ms excluding soft-paused gaps.
     * When [pausedAtEpochMs] is set, time after that instant does not count.
     */
    fun activeElapsedMs(
        startedAtEpochMs: Long,
        nowEpochMs: Long,
        pausedAtEpochMs: Long? = null,
        accumulatedPausedMs: Long = 0L
    ): Long {
        val end = if (pausedAtEpochMs != null) {
            pausedAtEpochMs.coerceAtMost(nowEpochMs)
        } else {
            nowEpochMs
        }
        val raw = (end - startedAtEpochMs).coerceAtLeast(0L)
        return (raw - accumulatedPausedMs.coerceAtLeast(0L)).coerceAtLeast(0L)
    }

    fun activeElapsedMs(run: SessionRun, nowEpochMs: Long): Long =
        activeElapsedMs(
            startedAtEpochMs = run.startedAtEpochMs,
            nowEpochMs = nowEpochMs,
            pausedAtEpochMs = run.pausedAtEpochMs,
            accumulatedPausedMs = run.accumulatedPausedMs
        )

    /**
     * Formats elapsed wall time as `M:SS` under one hour, or `H:MM:SS` thereafter.
     * Negative or zero deltas clamp to `0:00`.
     */
    fun format(startedAtEpochMs: Long, nowEpochMs: Long): String =
        formatMs(activeElapsedMs(startedAtEpochMs, nowEpochMs))

    fun formatForRun(run: SessionRun, nowEpochMs: Long): String =
        formatMs(activeElapsedMs(run, nowEpochMs))

    fun formatMs(elapsedMs: Long): String {
        val totalSeconds = elapsedMs.coerceAtLeast(0L) / 1000L
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

    fun labelForRun(run: SessionRun, nowEpochMs: Long): String {
        val text = formatForRun(run, nowEpochMs)
        return if (run.pausedAtEpochMs != null) {
            "Paused $text"
        } else {
            "Elapsed $text"
        }
    }
}
