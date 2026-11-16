package com.intent.launcher.domain

import com.intent.launcher.engine.DailyCycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Honest local-day controls for tracker entry / correction and session date labels.
 *
 * SAID event dates are chosen explicitly (prev/next day). Never invents OBSERVED
 * timestamps — OBSERVED evidence keeps its adapter-recorded clock.
 * Future days beyond "today" are not offered for backdating.
 */
object DateControls {

    /** How far back prev/next may walk for manual SAID entries. */
    const val MAX_BACKDATE_DAYS: Int = 30

    data class DaySelection(
        /** Local midnight for the selected calendar day. */
        val dayStartEpochMs: Long,
        /** Local midnight for "today" relative to [nowEpochMs] used when built. */
        val todayStartEpochMs: Long,
        val localDate: String,
        /** Human label: Today / Yesterday / weekday + date. */
        val label: String,
        val isToday: Boolean,
        val canGoPrevious: Boolean,
        val canGoNext: Boolean,
        val honestyNote: String
    )

    /**
     * Anchor a selection on [anchorEpochMs] (usually now), clamped to today or earlier.
     */
    fun selectionFor(
        anchorEpochMs: Long,
        nowEpochMs: Long = anchorEpochMs,
        timeZone: TimeZone = TimeZone.getDefault()
    ): DaySelection {
        val todayStart = DailyCycle.startOfLocalDayEpochMs(nowEpochMs, timeZone)
        val earliest = todayStart - TimeUnit.DAYS.toMillis(MAX_BACKDATE_DAYS.toLong())
        val requested = DailyCycle.startOfLocalDayEpochMs(anchorEpochMs, timeZone)
        val dayStart = requested.coerceIn(earliest, todayStart)
        return buildSelection(dayStart, todayStart, earliest, timeZone)
    }

    fun stepPrevious(
        current: DaySelection,
        timeZone: TimeZone = TimeZone.getDefault()
    ): DaySelection {
        if (!current.canGoPrevious) return current
        val earliest =
            current.todayStartEpochMs - TimeUnit.DAYS.toMillis(MAX_BACKDATE_DAYS.toLong())
        val prev = (current.dayStartEpochMs - TimeUnit.DAYS.toMillis(1)).coerceAtLeast(earliest)
        return buildSelection(prev, current.todayStartEpochMs, earliest, timeZone)
    }

    fun stepNext(
        current: DaySelection,
        timeZone: TimeZone = TimeZone.getDefault()
    ): DaySelection {
        if (!current.canGoNext) return current
        val earliest =
            current.todayStartEpochMs - TimeUnit.DAYS.toMillis(MAX_BACKDATE_DAYS.toLong())
        val next = (current.dayStartEpochMs + TimeUnit.DAYS.toMillis(1))
            .coerceAtMost(current.todayStartEpochMs)
        return buildSelection(next, current.todayStartEpochMs, earliest, timeZone)
    }

    /**
     * Epoch used as [TrackerEntry.eventAtEpochMs] for a SAID write on [selection].
     * Same calendar day as [nowEpochMs] → keep [nowEpochMs] (honest "just now").
     * Past day → midday local on that day (not a fabricated OBSERVED adapter time).
     */
    fun eventAtEpochMs(
        selection: DaySelection,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Long {
        if (selection.isToday ||
            DailyCycle.localDateString(selection.dayStartEpochMs, timeZone) ==
            DailyCycle.localDateString(nowEpochMs, timeZone)
        ) {
            return nowEpochMs
        }
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = selection.dayStartEpochMs
        cal.set(Calendar.HOUR_OF_DAY, 12)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Compact entry-row label for SAID/OBSERVED event time (local). */
    fun entryDayLabel(
        eventAtEpochMs: Long,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val sel = selectionFor(eventAtEpochMs, nowEpochMs, timeZone)
        return if (sel.isToday) {
            "Applies · Today · ${formatClock(eventAtEpochMs, timeZone)}"
        } else {
            "Applies · ${sel.label}"
        }
    }

    /**
     * SessionCard / duration surface: when the local Begin clock started.
     * Uses the run's [startedAtEpochMs] only — never invents an OBSERVED start.
     */
    fun sessionStartedLabel(
        startedAtEpochMs: Long,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val day = selectionFor(startedAtEpochMs, nowEpochMs, timeZone)
        val clock = formatClock(startedAtEpochMs, timeZone)
        return if (day.isToday) {
            "Started · Today $clock"
        } else {
            "Started · ${day.label} $clock"
        }
    }

    fun sessionStartedContentDescription(
        startedAtEpochMs: Long,
        nowEpochMs: Long,
        softPaused: Boolean,
        timeZone: TimeZone = TimeZone.getDefault()
    ): String {
        val base = sessionStartedLabel(startedAtEpochMs, nowEpochMs, timeZone)
        return if (softPaused) {
            "$base — soft-paused; local Begin clock, not lesson completion"
        } else {
            "$base — local Begin clock, not lesson completion"
        }
    }

    const val TRACKER_HONESTY_NOTE: String =
        "Entry date is explicit SAID event day (prev/next). " +
            "Today keeps the live clock; past days use local midday. " +
            "Never invents OBSERVED dates."

    private fun buildSelection(
        dayStart: Long,
        todayStart: Long,
        earliest: Long,
        timeZone: TimeZone
    ): DaySelection {
        val clamped = dayStart.coerceIn(earliest, todayStart)
        val isToday = clamped == todayStart
        val localDate = DailyCycle.localDateString(clamped, timeZone)
        val label = when {
            isToday -> "Today"
            clamped == todayStart - TimeUnit.DAYS.toMillis(1) -> "Yesterday"
            else -> formatWeekdayDate(clamped, timeZone)
        }
        return DaySelection(
            dayStartEpochMs = clamped,
            todayStartEpochMs = todayStart,
            localDate = localDate,
            label = label,
            isToday = isToday,
            canGoPrevious = clamped > earliest,
            canGoNext = clamped < todayStart,
            honestyNote = if (isToday) {
                "Recording for today — event time uses the live clock (SAID)."
            } else {
                "Backdated SAID event day · $label. Not an OBSERVED adapter timestamp."
            }
        )
    }

    private fun formatWeekdayDate(epochMs: Long, timeZone: TimeZone): String {
        val fmt = SimpleDateFormat("EEE d MMM", Locale.US)
        fmt.timeZone = timeZone
        return fmt.format(Date(epochMs))
    }

    private fun formatClock(epochMs: Long, timeZone: TimeZone): String {
        val fmt = SimpleDateFormat("HH:mm", Locale.US)
        fmt.timeZone = timeZone
        return fmt.format(Date(epochMs))
    }
}
