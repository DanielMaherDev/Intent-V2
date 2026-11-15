package com.intent.launcher.domain

/**
 * Home attention strip — one calm job: next action + optional secondary line.
 *
 * Secondary is **either** session elapsed (pause-aware) **or** OBSERVED package
 * minutes when UsageStats is granted — never both, and never invented minutes.
 * Soft-pause always wins the secondary slot so “Away from app” elapsed stays honest.
 */
object AttentionStrip {

    sealed class SecondaryLine {
        data class SessionElapsed(
            val text: String,
            val contentDescription: String
        ) : SecondaryLine()

        data class ObservedPackageMinutes(
            val text: String,
            val contentDescription: String
        ) : SecondaryLine()
    }

    /**
     * @param usageGranted whether UsageStats permission is granted
     * @param usageForegroundMs package foreground ms for the destination, or null
     * @param usageAppLabel destination label when minutes are known
     * @param pausedAtEpochMs soft-pause freeze instant, or null when running
     * @param accumulatedPausedMs prior soft-pause gaps already folded out
     * @param softPaused whether the strip should label elapsed as paused / away
     */
    fun secondaryLine(
        nextAction: NextAction,
        nowEpochMs: Long,
        usageGranted: Boolean,
        usageForegroundMs: Long?,
        usageAppLabel: String?,
        pausedAtEpochMs: Long? = null,
        accumulatedPausedMs: Long = 0L,
        softPaused: Boolean = false
    ): SecondaryLine? {
        val started = nextAction.startedAtEpochMs
        val activeWithClock =
            nextAction.kind == NextActionKind.ACTIVE_SESSION && started != null

        // Soft-pause freezes elapsed — prefer that honesty over OBSERVED minutes.
        if (activeWithClock && (softPaused || pausedAtEpochMs != null)) {
            val ms = SessionElapsed.activeElapsedMs(
                startedAtEpochMs = started!!,
                nowEpochMs = nowEpochMs,
                pausedAtEpochMs = pausedAtEpochMs,
                accumulatedPausedMs = accumulatedPausedMs
            )
            val formatted = SessionElapsed.formatMs(ms)
            return SecondaryLine.SessionElapsed(
                text = "Paused $formatted",
                contentDescription =
                    "Paused $formatted — soft-paused while away from the linked app, " +
                        "not lesson completion"
            )
        }

        // Prefer OBSERVED package minutes when honestly available — omit if missing.
        if (usageGranted &&
            usageForegroundMs != null &&
            !usageAppLabel.isNullOrBlank()
        ) {
            val formatted = SessionElapsed.format(0L, usageForegroundMs.coerceAtLeast(0L))
            val text = "${usageAppLabel.trim()} · ~$formatted"
            return SecondaryLine.ObservedPackageMinutes(
                text = text,
                contentDescription =
                    "$usageAppLabel package foreground about $formatted — not lesson completion"
            )
        }

        // Otherwise, session elapsed for an active run (local clock only).
        if (activeWithClock) {
            val ms = SessionElapsed.activeElapsedMs(
                startedAtEpochMs = started!!,
                nowEpochMs = nowEpochMs,
                pausedAtEpochMs = pausedAtEpochMs,
                accumulatedPausedMs = accumulatedPausedMs
            )
            val formatted = SessionElapsed.formatMs(ms)
            return SecondaryLine.SessionElapsed(
                text = "Elapsed $formatted",
                contentDescription = "Elapsed $formatted"
            )
        }

        return null
    }

    /** One supporting sentence for the strip — prefer reason, else detail; no pile. */
    fun supportingSentence(nextAction: NextAction): String {
        val reason = nextAction.reason.trim()
        if (reason.isNotEmpty()) return reason
        return nextAction.detail.trim()
    }
}
