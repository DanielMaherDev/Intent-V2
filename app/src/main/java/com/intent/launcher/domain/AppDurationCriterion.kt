package com.intent.launcher.domain

import com.intent.launcher.engine.AppDurationTask

/**
 * Honest projection of an explicit app-duration criterion onto an active [SessionRun].
 *
 * Progress uses [SessionElapsed] (soft-pause aware). Meeting the local timer is **not**
 * lesson completion and never Completes the run — the user still taps Complete.
 *
 * Criterion minutes come from SAID duration facts / known AppDurationTask ids, or from
 * session/proposal titles that declare `(N min)`. No invented targets.
 */
object AppDurationCriterion {

    data class Progress(
        val targetMinutes: Int,
        val elapsedMs: Long,
        val completedMinutes: Int,
        /** 0f..1f toward target from local elapsed only. */
        val fraction: Float,
        val softPaused: Boolean,
        /** True when local elapsed ≥ target — still not lesson completion. */
        val localTimerMet: Boolean,
        val ratioLabel: String,
        val honestyNote: String,
        val contentDescription: String
    )

    /**
     * Resolve an explicit duration target for the active run, or null when none is declared.
     */
    fun resolveTargetMinutes(
        run: SessionRun?,
        session: ScheduledSession?,
        facts: List<Fact>,
        proposals: List<Proposal> = emptyList()
    ): Int? {
        if (run == null) return null
        val intentionFacts = facts.filter { it.intentionId == run.intentionId }

        intentionFacts.firstOrNull { it.id == AppDurationTask.DURATION_FACT_ID }
            ?.let { parseMinutesFromFact(it.text) }
            ?.let { return it }

        intentionFacts.asSequence()
            .mapNotNull { parseMinutesFromFact(it.text) }
            .firstOrNull()
            ?.let { return it }

        parseMinutesFromTitle(session?.title)?.let { return it }
        parseMinutesFromTitle(run.title)?.let { return it }

        proposals
            .asSequence()
            .filter { it.intentionId == run.intentionId }
            .mapNotNull { parseMinutesFromTitle(it.title) }
            .firstOrNull()
            ?.let { return it }

        return null
    }

    fun progress(
        run: SessionRun,
        targetMinutes: Int,
        nowEpochMs: Long
    ): Progress {
        val softPaused = run.pausedAtEpochMs != null
        val elapsedMs = SessionElapsed.activeElapsedMs(run, nowEpochMs)
        return progressFromElapsed(targetMinutes, elapsedMs, softPaused)
    }

    /** UI tick helper — same honesty rules as [progress], from already-frozen elapsed. */
    fun progressFromElapsed(
        targetMinutes: Int,
        elapsedMs: Long,
        softPaused: Boolean
    ): Progress {
        val targetMs = targetMinutes.coerceAtLeast(1).toLong() * 60_000L
        val safeElapsed = elapsedMs.coerceAtLeast(0L)
        val completedMinutes = (safeElapsed / 60_000L).toInt().coerceAtMost(targetMinutes)
        val fraction = (safeElapsed.toFloat() / targetMs.toFloat()).coerceIn(0f, 1f)
        val localTimerMet = safeElapsed >= targetMs
        val ratioLabel = "$completedMinutes / $targetMinutes min"
        val honestyNote = when {
            softPaused ->
                "Soft-paused while away — timer frozen at $ratioLabel. " +
                    "Not lesson completion."
            localTimerMet ->
                "Local timer met the $targetMinutes-min criterion ($ratioLabel) — " +
                    "not lesson completion. Complete when you're done."
            else ->
                "Toward $targetMinutes-min criterion ($ratioLabel) — local timer + soft-pause; " +
                    "not lesson completion."
        }
        val contentDescription = buildString {
            append(ratioLabel)
            append(" toward duration criterion")
            if (softPaused) append(", soft-paused")
            if (localTimerMet) append(", local timer met")
            append(" — not lesson completion")
        }
        return Progress(
            targetMinutes = targetMinutes,
            elapsedMs = safeElapsed,
            completedMinutes = completedMinutes,
            fraction = fraction,
            softPaused = softPaused,
            localTimerMet = localTimerMet,
            ratioLabel = ratioLabel,
            honestyNote = honestyNote,
            contentDescription = contentDescription
        )
    }

    /**
     * Convenience: resolve + progress for an active run, or null when no criterion.
     */
    fun forActiveRun(
        run: SessionRun?,
        session: ScheduledSession?,
        facts: List<Fact>,
        nowEpochMs: Long,
        proposals: List<Proposal> = emptyList()
    ): Progress? {
        val target = resolveTargetMinutes(run, session, facts, proposals) ?: return null
        return progress(run!!, target, nowEpochMs)
    }

    internal fun parseMinutesFromFact(text: String): Int? {
        val lower = text.lowercase()
        // Only SAID facts that declare an explicit duration criterion — not every
        // incidental "N minutes" mention in other user text.
        if (!lower.contains("duration criterion") &&
            !(lower.contains("explicit") && (lower.contains("minute") || lower.contains("min")))
        ) {
            return null
        }
        return EXPLICIT_FACT_REGEX.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it > 0 }
    }

    internal fun parseMinutesFromTitle(title: String?): Int? {
        if (title.isNullOrBlank()) return null
        return TITLE_MIN_REGEX.find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            ?.takeIf { it > 0 }
    }

    private val EXPLICIT_FACT_REGEX =
        Regex(
            """(?:duration criterion|explicit)[^\d]{0,48}(\d+)\s*(?:minutes?|mins?\b)""",
            RegexOption.IGNORE_CASE
        )
    private val TITLE_MIN_REGEX =
        Regex("""\((\d+)\s*min(?:ute)?s?\)""", RegexOption.IGNORE_CASE)
}
