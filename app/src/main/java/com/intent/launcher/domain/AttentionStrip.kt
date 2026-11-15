package com.intent.launcher.domain

/**
 * Home attention strip — one calm job: next action + optional secondary line.
 *
 * Secondary is **either** session elapsed **or** OBSERVED package minutes when
 * UsageStats is granted — never both, and never invented minutes.
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
     */
    fun secondaryLine(
        nextAction: NextAction,
        nowEpochMs: Long,
        usageGranted: Boolean,
        usageForegroundMs: Long?,
        usageAppLabel: String?
    ): SecondaryLine? {
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
        val started = nextAction.startedAtEpochMs
        if (nextAction.kind == NextActionKind.ACTIVE_SESSION && started != null) {
            val formatted = SessionElapsed.format(started, nowEpochMs)
            return SecondaryLine.SessionElapsed(
                text = "Elapsed $formatted",
                contentDescription = SessionElapsed.label(started, nowEpochMs)
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
