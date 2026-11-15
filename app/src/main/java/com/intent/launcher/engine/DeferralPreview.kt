package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.SessionRunStatus
import java.util.Calendar
import java.util.TimeZone

/**
 * Stage 4 — explainable deferral preview (read model) before [FlowCommand.DeferNextSession].
 * Does not invent dependency graphs; lists other local sessions on the same intention only.
 */
object DeferralPreview {

    /** Matches Home [LauncherViewModel.deferNext] default (+2 hours). */
    const val DEFAULT_DEFER_MS: Long = 2 * 60 * 60 * 1000L

    private val DAY_NAMES =
        listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    data class AffectedSession(
        val sessionId: String,
        val title: String,
        val whenLabel: String
    )

    data class Preview(
        val sessionId: String,
        val sessionTitle: String,
        /** Current next occurrence / deferral clock label. */
        val fromLabel: String,
        /** Proposed new defer-until clock/day label. */
        val toLabel: String,
        val deferUntilEpochMs: Long,
        /** Other sessions on the same intention — unchanged by this defer. */
        val affectedSessions: List<AffectedSession>,
        /** Honesty / empty-state notes. */
        val notes: String,
        /** One-line Home preview. */
        val oneLine: String
    )

    /**
     * Build a preview for deferring [sessionId] to [nowEpochMs] + [deferMs].
     * Null when the session is missing or a run is already in progress (command would no-op).
     */
    fun preview(
        snapshot: IntentionSnapshot,
        sessionId: String,
        nowEpochMs: Long,
        deferMs: Long = DEFAULT_DEFER_MS,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Preview? {
        val session = snapshot.sessions.firstOrNull { it.id == sessionId } ?: return null
        val inProgress = snapshot.sessionRuns.any {
            it.sessionId == sessionId && it.status == SessionRunStatus.IN_PROGRESS
        }
        if (inProgress) return null

        val deferUntil = nowEpochMs + deferMs
        if (deferUntil <= nowEpochMs) return null

        val fromLabel = NextActionEngine.formatSessionWhen(
            deferredUntil = session.deferredUntilEpochMs,
            dayOfWeek = session.dayOfWeek,
            hour = session.hour,
            minute = session.minute,
            now = nowEpochMs,
            tz = timeZone
        )
        val toLabel = formatDayClock(deferUntil, nowEpochMs, timeZone)

        val siblings = snapshot.sessions
            .filter { it.intentionId == session.intentionId && it.id != session.id }
            .sortedBy {
                NextActionEngine.nextOccurrence(
                    it.dayOfWeek, it.hour, it.minute, nowEpochMs, timeZone
                )
            }
            .map { sibling ->
                AffectedSession(
                    sessionId = sibling.id,
                    title = sibling.title,
                    whenLabel = NextActionEngine.formatSessionWhen(
                        deferredUntil = sibling.deferredUntilEpochMs,
                        dayOfWeek = sibling.dayOfWeek,
                        hour = sibling.hour,
                        minute = sibling.minute,
                        now = nowEpochMs,
                        tz = timeZone
                    )
                )
            }

        val notes = buildNotes(siblings)
        val oneLine = buildOneLine(toLabel, siblings)

        return Preview(
            sessionId = session.id,
            sessionTitle = session.title,
            fromLabel = fromLabel,
            toLabel = toLabel,
            deferUntilEpochMs = deferUntil,
            affectedSessions = siblings,
            notes = notes,
            oneLine = oneLine
        )
    }

    /**
     * Preview for the Home next-action session when Defer is offered.
     */
    fun previewForNextAction(
        snapshot: IntentionSnapshot,
        sessionId: String?,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Preview? {
        val id = sessionId ?: return null
        return preview(snapshot, id, nowEpochMs, timeZone = timeZone)
    }

    private fun buildNotes(siblings: List<AffectedSession>): String {
        val base = "Local schedule only — no calendar write-back and no invented dependency graph."
        return if (siblings.isEmpty()) {
            "$base No other sessions on this intention — only this occurrence moves."
        } else {
            "$base Other sessions on this intention stay as listed; they are not auto-rescheduled."
        }
    }

    private fun buildOneLine(toLabel: String, siblings: List<AffectedSession>): String {
        val move = "Moves to $toLabel"
        if (siblings.isEmpty()) return "$move · no other sessions affected"
        val first = siblings.first()
        val shortTitle = first.title.take(28)
        return "$move · still scheduled: $shortTitle (${first.whenLabel})"
    }

    private fun formatDayClock(epochMs: Long, nowEpochMs: Long, tz: TimeZone): String {
        val cal = Calendar.getInstance(tz).apply { timeInMillis = epochMs }
        val now = Calendar.getInstance(tz).apply { timeInMillis = nowEpochMs }
        val clock = "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
        val sameDay = cal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
        if (sameDay) return "today $clock"
        val isoDow = calendarDowToIso(cal.get(Calendar.DAY_OF_WEEK))
        val day = DAY_NAMES.getOrElse(isoDow) { "day" }
        return "$day $clock"
    }

    private fun calendarDowToIso(calendarDow: Int): Int =
        if (calendarDow == Calendar.SUNDAY) 7 else calendarDow - 1
}
