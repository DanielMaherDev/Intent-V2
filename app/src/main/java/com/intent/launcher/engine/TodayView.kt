package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.domain.SessionRunStatus
import java.util.Calendar
import java.util.TimeZone

/**
 * Dedicated Today projection — planned · completed · deferred from day cycle,
 * occurrences, and sessions (not chat prose).
 */
object TodayView {

    const val HONESTY_NOTE =
        "From day cycle, occurrences, and sessions — not chat prose."

    enum class Bucket {
        PLANNED,
        COMPLETED,
        DEFERRED
    }

    data class Item(
        val id: String,
        val title: String,
        val detail: String,
        val statusLabel: String,
        val intentionId: String?,
        val bucket: Bucket
    )

    data class Model(
        val localDate: String,
        val focusSummary: String?,
        val planned: List<Item>,
        val completed: List<Item>,
        val deferred: List<Item>,
        val honestyNote: String = HONESTY_NOTE
    )

    fun project(
        snapshot: IntentionSnapshot,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): Model {
        val localDate = DailyCycle.localDateString(nowEpochMs, timeZone)
        val day = snapshot.dayCycle.takeIf { it.localDate == localDate }
        val planned = mutableListOf<Item>()
        val completed = mutableListOf<Item>()
        val deferred = mutableListOf<Item>()
        val claimedSessionIds = mutableSetOf<String>()

        // Durable day-loop occurrences for this local date.
        snapshot.occurrences
            .filter { it.localDate == localDate }
            .sortedBy { it.recordedAtEpochMs }
            .forEach { occ ->
                val title = when (occ.kind) {
                    "check_in" -> "Morning check-in"
                    "midday_check" -> "Midday check"
                    "check_out" -> "Evening check-out"
                    else -> occ.kind.replace('_', ' ').replaceFirstChar { it.uppercase() }
                }
                completed += Item(
                    id = "occ-${occ.id}",
                    title = title,
                    detail = occ.summary.ifBlank { "${occ.phase} · ${occ.kind}" },
                    statusLabel = "Completed · occurrence",
                    intentionId = occ.intentionId,
                    bucket = Bucket.COMPLETED
                )
            }

        // Session runs started today.
        snapshot.sessionRuns
            .filter { isSameLocalDay(it.startedAtEpochMs, nowEpochMs, timeZone) }
            .sortedBy { it.startedAtEpochMs }
            .forEach { run ->
                claimedSessionIds += run.sessionId
                when (run.status) {
                    SessionRunStatus.COMPLETED -> {
                        completed += Item(
                            id = "run-${run.id}",
                            title = run.title,
                            detail = "Session run · user-reported complete",
                            statusLabel = "Completed",
                            intentionId = run.intentionId,
                            bucket = Bucket.COMPLETED
                        )
                    }
                    SessionRunStatus.IN_PROGRESS -> {
                        planned += Item(
                            id = "run-${run.id}",
                            title = run.title,
                            detail = "Session run · in progress",
                            statusLabel = "In progress",
                            intentionId = run.intentionId,
                            bucket = Bucket.PLANNED
                        )
                    }
                    SessionRunStatus.ABANDONED -> {
                        completed += Item(
                            id = "run-${run.id}",
                            title = run.title,
                            detail = "Session run · abandoned",
                            statusLabel = "Abandoned",
                            intentionId = run.intentionId,
                            bucket = Bucket.COMPLETED
                        )
                    }
                }
            }

        // Scheduled sessions: deferred vs still planned for today.
        snapshot.sessions.forEach { session ->
            if (session.id in claimedSessionIds) return@forEach
            val timing = NextActionEngine.classifySessionTiming(session, nowEpochMs, timeZone)
            when (timing) {
                NextActionEngine.SessionTiming.DEFERRED_FUTURE -> {
                    deferred += Item(
                        id = "session-${session.id}",
                        title = session.title,
                        detail = deferredDetail(session, nowEpochMs, timeZone),
                        statusLabel = "Deferred",
                        intentionId = session.intentionId,
                        bucket = Bucket.DEFERRED
                    )
                }
                NextActionEngine.SessionTiming.DUE_NOW,
                NextActionEngine.SessionTiming.APPROACHING,
                NextActionEngine.SessionTiming.LATER_TODAY -> {
                    planned += Item(
                        id = "session-${session.id}",
                        title = session.title,
                        detail = NextActionEngine.formatSessionWhen(
                            session.deferredUntilEpochMs,
                            session.dayOfWeek,
                            session.hour,
                            session.minute,
                            nowEpochMs,
                            timeZone
                        ),
                        statusLabel = when (timing) {
                            NextActionEngine.SessionTiming.DUE_NOW -> "Due now"
                            NextActionEngine.SessionTiming.APPROACHING -> "Approaching"
                            else -> "Later today"
                        },
                        intentionId = session.intentionId,
                        bucket = Bucket.PLANNED
                    )
                }
                NextActionEngine.SessionTiming.FUTURE_DAY -> Unit
            }
        }

        // Day-cycle focus when morning confirmed and no check-in occurrence yet
        // (legacy / partial day markers).
        val hasCheckInOcc = snapshot.occurrences.any {
            it.localDate == localDate && it.kind == "check_in"
        }
        val focusSummary = day?.focusSummary
        if (focusSummary != null && !hasCheckInOcc) {
            planned.add(
                0,
                Item(
                    id = "focus-$localDate",
                    title = focusSummary,
                    detail = "Day cycle focus",
                    statusLabel = "Focus",
                    intentionId = day.focusIntentionId,
                    bucket = Bucket.PLANNED
                )
            )
        }

        return Model(
            localDate = localDate,
            focusSummary = day?.focusSummary,
            planned = planned,
            completed = completed,
            deferred = deferred
        )
    }

    private fun deferredDetail(
        session: ScheduledSession,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): String {
        val until = session.deferredUntilEpochMs ?: return "Deferred"
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = until
        val clock = "%02d:%02d".format(
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE)
        )
        val untilDate = DailyCycle.localDateString(until, timeZone)
        val today = DailyCycle.localDateString(nowEpochMs, timeZone)
        return if (untilDate == today) {
            "Deferred until $clock today"
        } else {
            "Deferred until $untilDate $clock"
        }
    }

    private fun isSameLocalDay(a: Long, b: Long, timeZone: TimeZone): Boolean =
        DailyCycle.localDateString(a, timeZone) == DailyCycle.localDateString(b, timeZone)
}
