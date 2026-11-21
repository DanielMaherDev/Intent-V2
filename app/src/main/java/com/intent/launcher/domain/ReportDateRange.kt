package com.intent.launcher.domain

import com.intent.launcher.engine.DailyCycle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * Honest report window navigation — day / week / 30-day spans with prev/next.
 *
 * Filters existing labeled proof rows; never invents metrics, charts, or OBSERVED
 * dates for empty windows.
 */
object ReportDateRange {

    /** How far back prev may walk (aligned with [DateControls.MAX_BACKDATE_DAYS] × weeks). */
    const val MAX_BACK_DAYS: Int = 90

    enum class Span(val dayCount: Int, val chipLabel: String) {
        DAY(1, "Day"),
        WEEK(7, "Week"),
        DAYS_30(30, "30 days")
    }

    data class Selection(
        val span: Span,
        /** Inclusive local-day start of the window. */
        val rangeStartEpochMs: Long,
        /** Exclusive end (local midnight after the last day in the window). */
        val rangeEndExclusiveEpochMs: Long,
        /** Local midnight of the window's last day (anchor for prev/next). */
        val endDayStartEpochMs: Long,
        val todayStartEpochMs: Long,
        val label: String,
        val localDateSpan: String,
        val canGoPrevious: Boolean,
        val canGoNext: Boolean,
        val honestyNote: String
    )

    fun selectionFor(
        span: Span,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        /** Anchor on this day's local midnight as the window end day (clamped ≤ today). */
        endDayAnchorEpochMs: Long = nowEpochMs
    ): Selection {
        val todayStart = DailyCycle.startOfLocalDayEpochMs(nowEpochMs, timeZone)
        val earliest = todayStart - TimeUnit.DAYS.toMillis(MAX_BACK_DAYS.toLong())
        val requestedEnd = DailyCycle.startOfLocalDayEpochMs(endDayAnchorEpochMs, timeZone)
            .coerceIn(earliest, todayStart)
        return build(span, requestedEnd, todayStart, earliest, timeZone)
    }

    fun withSpan(
        current: Selection,
        span: Span,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Selection {
        val earliest =
            current.todayStartEpochMs - TimeUnit.DAYS.toMillis(MAX_BACK_DAYS.toLong())
        return build(span, current.endDayStartEpochMs, current.todayStartEpochMs, earliest, timeZone)
    }

    fun stepPrevious(
        current: Selection,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Selection {
        if (!current.canGoPrevious) return current
        val earliest =
            current.todayStartEpochMs - TimeUnit.DAYS.toMillis(MAX_BACK_DAYS.toLong())
        val step = TimeUnit.DAYS.toMillis(current.span.dayCount.toLong())
        val newEnd = (current.endDayStartEpochMs - step).coerceAtLeast(earliest)
        return build(current.span, newEnd, current.todayStartEpochMs, earliest, timeZone)
    }

    fun stepNext(
        current: Selection,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Selection {
        if (!current.canGoNext) return current
        val earliest =
            current.todayStartEpochMs - TimeUnit.DAYS.toMillis(MAX_BACK_DAYS.toLong())
        val step = TimeUnit.DAYS.toMillis(current.span.dayCount.toLong())
        val newEnd = (current.endDayStartEpochMs + step)
            .coerceAtMost(current.todayStartEpochMs)
        return build(current.span, newEnd, current.todayStartEpochMs, earliest, timeZone)
    }

    fun contains(selection: Selection, eventAtEpochMs: Long): Boolean =
        eventAtEpochMs >= selection.rangeStartEpochMs &&
            eventAtEpochMs < selection.rangeEndExclusiveEpochMs

    const val HONESTY_NOTE: String =
        "Date range filters store rows already present (SAID / OBSERVED / INFERRED). " +
            "Empty windows stay empty — no charts or invented totals."

    private fun build(
        span: Span,
        endDayStart: Long,
        todayStart: Long,
        earliest: Long,
        timeZone: TimeZone
    ): Selection {
        val endDay = endDayStart.coerceIn(earliest, todayStart)
        val start = (endDay - TimeUnit.DAYS.toMillis((span.dayCount - 1).toLong()))
            .coerceAtLeast(earliest)
        val endExclusive = endDay + TimeUnit.DAYS.toMillis(1)
        val isCurrentEnd = endDay == todayStart
        val label = when (span) {
            Span.DAY -> when {
                isCurrentEnd -> "Today"
                endDay == todayStart - TimeUnit.DAYS.toMillis(1) -> "Yesterday"
                else -> formatWeekdayDate(endDay, timeZone)
            }
            Span.WEEK -> if (isCurrentEnd) {
                "Last 7 days"
            } else {
                "7 days ending ${formatWeekdayDate(endDay, timeZone)}"
            }
            Span.DAYS_30 -> if (isCurrentEnd) {
                "Last 30 days"
            } else {
                "30 days ending ${formatWeekdayDate(endDay, timeZone)}"
            }
        }
        val startDate = DailyCycle.localDateString(start, timeZone)
        val endDate = DailyCycle.localDateString(endDay, timeZone)
        val localSpan = if (startDate == endDate) startDate else "$startDate → $endDate"
        // Previous is allowed while the window end can still move earlier.
        val canGoPrevious = endDay > earliest
        val canGoNext = endDay < todayStart
        return Selection(
            span = span,
            rangeStartEpochMs = start,
            rangeEndExclusiveEpochMs = endExclusive,
            endDayStartEpochMs = endDay,
            todayStartEpochMs = todayStart,
            label = label,
            localDateSpan = localSpan,
            canGoPrevious = canGoPrevious,
            canGoNext = canGoNext,
            honestyNote = if (isCurrentEnd) {
                "Window ends today · $localSpan. Filters real store rows only."
            } else {
                "Backdated report window · $localSpan. Not an OBSERVED adapter range."
            }
        )
    }

    private fun formatWeekdayDate(epochMs: Long, timeZone: TimeZone): String {
        val fmt = SimpleDateFormat("EEE d MMM", Locale.US)
        fmt.timeZone = timeZone
        return fmt.format(Date(epochMs))
    }
}
