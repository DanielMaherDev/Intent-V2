package com.intent.launcher.engine

import com.intent.launcher.domain.DayCycleState
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.SessionRunStatus
import java.util.Calendar
import java.util.TimeZone

/**
 * Worksheet §10 day-phase helpers — same intention snapshot as Home (no daily-task store).
 * Morning check-in and evening check-out are clock windows + durable [DayCycleState].
 */
object DailyCycle {

    /** Local hour before which Home may surface morning check-in. */
    const val CHECK_IN_END_HOUR: Int = 12

    /** Local hour at/after which Home may surface evening check-out. */
    const val CHECK_OUT_START_HOUR: Int = 18

    fun localDateString(nowEpochMs: Long, timeZone: TimeZone): String {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowEpochMs
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH) + 1
        val d = cal.get(Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(y, m, d)
    }

    fun localHour(nowEpochMs: Long, timeZone: TimeZone): Int {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowEpochMs
        return cal.get(Calendar.HOUR_OF_DAY)
    }

    fun isMorningWindow(nowEpochMs: Long, timeZone: TimeZone): Boolean =
        localHour(nowEpochMs, timeZone) < CHECK_IN_END_HOUR

    fun isEveningWindow(nowEpochMs: Long, timeZone: TimeZone): Boolean =
        localHour(nowEpochMs, timeZone) >= CHECK_OUT_START_HOUR

    fun hasActiveScheduledFocus(snapshot: IntentionSnapshot, intentionId: String): Boolean {
        val intention = snapshot.intentions.firstOrNull { it.id == intentionId } ?: return false
        if (intention.phase != IntentionPhase.ACTIVE) return false
        return snapshot.sessions.any { it.intentionId == intentionId }
    }

    fun shouldOfferCheckIn(
        snapshot: IntentionSnapshot,
        intentionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): Boolean {
        if (!isMorningWindow(nowEpochMs, timeZone)) return false
        if (!hasActiveScheduledFocus(snapshot, intentionId)) return false
        val date = localDateString(nowEpochMs, timeZone)
        val day = snapshot.dayCycle
        if (day.checkedOutFor(date)) return false
        return !day.checkedInFor(date)
    }

    fun shouldOfferCheckOut(
        snapshot: IntentionSnapshot,
        intentionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): Boolean {
        if (!isEveningWindow(nowEpochMs, timeZone)) return false
        if (!hasActiveScheduledFocus(snapshot, intentionId)) return false
        val date = localDateString(nowEpochMs, timeZone)
        val day = snapshot.dayCycle
        if (day.checkedOutFor(date)) return false
        // Prefer check-out after a morning confirm; also allow if user ran a session today.
        val checkedIn = day.checkedInFor(date)
        val ranToday = snapshot.sessionRuns.any {
            it.intentionId == intentionId &&
                isSameLocalDay(it.startedAtEpochMs, nowEpochMs, timeZone)
        }
        return checkedIn || ranToday
    }

    fun summarizeTodayRuns(
        snapshot: IntentionSnapshot,
        intentionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): String {
        val todays = snapshot.sessionRuns.filter {
            it.intentionId == intentionId &&
                isSameLocalDay(it.startedAtEpochMs, nowEpochMs, timeZone)
        }
        if (todays.isEmpty()) {
            return "No session runs recorded today — report what happened yourself."
        }
        val parts = todays.map { run ->
            when (run.status) {
                SessionRunStatus.IN_PROGRESS -> "${run.title}: still in progress"
                SessionRunStatus.COMPLETED -> "${run.title}: completed (user-reported)"
                SessionRunStatus.ABANDONED -> "${run.title}: abandoned"
            }
        }
        return parts.joinToString(" · ")
    }

    /**
     * Start of tomorrow local morning (09:00) — used for “defer tomorrow” check-out.
     * Local schedule only; not an external calendar write.
     */
    fun tomorrowMorningEpochMs(nowEpochMs: Long, timeZone: TimeZone, hour: Int = 9): Long {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowEpochMs
        cal.add(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun focusSummary(
        intentionTitle: String,
        previewNextLabel: String,
        previewDetail: String
    ): String {
        val focus = intentionTitle.ifBlank { "Your intention" }
        val next = previewNextLabel.ifBlank { "next action" }
        val detail = previewDetail.trim()
        return if (detail.isNotEmpty() && detail != next) {
            "Focus: $focus · Next: $next · $detail"
        } else {
            "Focus: $focus · Next: $next"
        }
    }

    private fun isSameLocalDay(a: Long, b: Long, timeZone: TimeZone): Boolean =
        localDateString(a, timeZone) == localDateString(b, timeZone)
}
