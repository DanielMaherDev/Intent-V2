package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.engine.DailyCycle
import com.intent.launcher.engine.ProgressProof
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * FE-C3 report date-range — Day / Week / 30-day prev/next filters store rows only.
 */
class ReportDateRangeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    // 2023-11-16 12:00:00 UTC
    private val now = 1_700_136_000_000L
    private val registry = CapabilityRegistry.default

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("report-range.json"))
    }

    @Test
    fun weekSelection_defaultsToLast7EndingToday() {
        val sel = ReportDateRange.selectionFor(ReportDateRange.Span.WEEK, now, tz)
        assertEquals(ReportDateRange.Span.WEEK, sel.span)
        assertEquals("Last 7 days", sel.label)
        assertFalse(sel.canGoNext)
        assertTrue(sel.canGoPrevious)
        val today = DailyCycle.startOfLocalDayEpochMs(now, tz)
        assertEquals(today, sel.endDayStartEpochMs)
        assertEquals(today - TimeUnit.DAYS.toMillis(6), sel.rangeStartEpochMs)
    }

    @Test
    fun dayPrevious_thenNext_roundTrips() {
        val today = ReportDateRange.selectionFor(ReportDateRange.Span.DAY, now, tz)
        assertEquals("Today", today.label)
        val yesterday = ReportDateRange.stepPrevious(today, tz)
        assertEquals("Yesterday", yesterday.label)
        val back = ReportDateRange.stepNext(yesterday, tz)
        assertEquals(today.endDayStartEpochMs, back.endDayStartEpochMs)
    }

    @Test
    fun withSpan_keepsEndDay() {
        val day = ReportDateRange.selectionFor(ReportDateRange.Span.DAY, now, tz)
        val week = ReportDateRange.withSpan(day, ReportDateRange.Span.WEEK, tz)
        assertEquals(ReportDateRange.Span.WEEK, week.span)
        assertEquals(day.endDayStartEpochMs, week.endDayStartEpochMs)
        val thirty = ReportDateRange.withSpan(week, ReportDateRange.Span.DAYS_30, tz)
        assertEquals(ReportDateRange.Span.DAYS_30, thirty.span)
        assertEquals("Last 30 days", thirty.label)
    }

    @Test
    fun stepPrevious_clampsToMaxBack() {
        var sel = ReportDateRange.selectionFor(ReportDateRange.Span.DAY, now, tz)
        repeat(ReportDateRange.MAX_BACK_DAYS + 10) {
            sel = ReportDateRange.stepPrevious(sel, tz)
        }
        assertFalse(sel.canGoPrevious)
        val earliest =
            sel.todayStartEpochMs - TimeUnit.DAYS.toMillis(ReportDateRange.MAX_BACK_DAYS.toLong())
        assertEquals(earliest, sel.endDayStartEpochMs)
    }

    @Test
    fun progressProof_withRange_filtersWithoutInventing() {
        SpanishScenario.seed(store, now - TimeUnit.DAYS.toMillis(10))
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-old",
                intentionId = SpanishScenario.INTENTION_ID,
                moduleId = "practice_log",
                valueText = "10 min",
                nowEpochMs = now - TimeUnit.DAYS.toMillis(10),
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user",
                eventAtEpochMs = now - TimeUnit.DAYS.toMillis(10)
            )
        )
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-today",
                intentionId = SpanishScenario.INTENTION_ID,
                moduleId = "practice_log",
                valueText = "20 min",
                nowEpochMs = now,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user"
            )
        )
        val proof = ProgressProof.build(
            snapshot = store.snapshot(),
            intentionId = SpanishScenario.INTENTION_ID,
            nowEpochMs = now,
            shapeTag = "language",
            registry = registry,
            timeZone = tz
        )!!
        assertTrue(proof.items.size >= 2)

        val day = ReportDateRange.selectionFor(ReportDateRange.Span.DAY, now, tz)
        val todayOnly = ProgressProof.withRange(proof, day)
        assertTrue(todayOnly.rangeItems.any { it.id == "trk-today" })
        assertFalse(todayOnly.rangeItems.any { it.id == "trk-old" })
        assertNotNull(todayOnly.range)

        val oldDay = ReportDateRange.selectionFor(
            span = ReportDateRange.Span.DAY,
            nowEpochMs = now,
            timeZone = tz,
            endDayAnchorEpochMs = now - TimeUnit.DAYS.toMillis(10)
        )
        val oldOnly = ProgressProof.withRange(proof, oldDay)
        assertTrue(oldOnly.rangeItems.any { it.id == "trk-old" })
        assertFalse(oldOnly.rangeItems.any { it.id == "trk-today" })

        val emptyFutureish = ReportDateRange.stepPrevious(
            ReportDateRange.selectionFor(ReportDateRange.Span.DAY, now, tz),
            tz
        ).let { y ->
            // Walk to a day with no entries (yesterday if old is 10d back)
            var s = y
            repeat(3) { s = ReportDateRange.stepPrevious(s, tz) }
            s
        }
        val empty = ProgressProof.withRange(proof, emptyFutureish)
        assertTrue(empty.rangeItems.isEmpty())
        assertNotNull(empty.rangeEmptyState)
        assertTrue(empty.rangeEmptyState!!.contains("Empty on purpose"))
        assertFalse(empty.rangeEmptyState!!.contains("0%", ignoreCase = true))
    }
}
