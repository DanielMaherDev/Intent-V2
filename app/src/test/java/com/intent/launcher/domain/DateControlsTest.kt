package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.engine.DailyCycle
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * FE-C3 date-controls — explicit SAID entry day prev/next; session started labels.
 * Never invents OBSERVED dates.
 */
class DateControlsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    // 2023-11-15 12:00:00 UTC
    private val now = 1_700_049_600_000L
    private val registry = CapabilityRegistry.default

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("date-controls.json"))
    }

    @Test
    fun selectionFor_today_isDefault() {
        val sel = DateControls.selectionFor(now, now, tz)
        assertTrue(sel.isToday)
        assertEquals("Today", sel.label)
        assertEquals(DailyCycle.localDateString(now, tz), sel.localDate)
        assertFalse(sel.canGoNext)
        assertTrue(sel.canGoPrevious)
        assertEquals(now, DateControls.eventAtEpochMs(sel, now, tz))
    }

    @Test
    fun stepPrevious_thenNext_roundTripsToToday() {
        val today = DateControls.selectionFor(now, now, tz)
        val yesterday = DateControls.stepPrevious(today, tz)
        assertFalse(yesterday.isToday)
        assertEquals("Yesterday", yesterday.label)
        assertTrue(yesterday.canGoNext)
        val back = DateControls.stepNext(yesterday, tz)
        assertTrue(back.isToday)
        assertEquals(today.dayStartEpochMs, back.dayStartEpochMs)
    }

    @Test
    fun stepPrevious_stopsAtMaxBackdateDays() {
        var sel = DateControls.selectionFor(now, now, tz)
        repeat(DateControls.MAX_BACKDATE_DAYS + 5) {
            sel = DateControls.stepPrevious(sel, tz)
        }
        assertFalse(sel.canGoPrevious)
        val earliest =
            sel.todayStartEpochMs - TimeUnit.DAYS.toMillis(DateControls.MAX_BACKDATE_DAYS.toLong())
        assertEquals(earliest, sel.dayStartEpochMs)
    }

    @Test
    fun eventAtEpochMs_pastDay_usesLocalMidday_notObserved() {
        val today = DateControls.selectionFor(now, now, tz)
        val yesterday = DateControls.stepPrevious(today, tz)
        val eventAt = DateControls.eventAtEpochMs(yesterday, now, tz)
        assertEquals(yesterday.dayStartEpochMs + TimeUnit.HOURS.toMillis(12), eventAt)
        assertFalse(eventAt == now)
    }

    @Test
    fun recordTrackerEntry_withBackdatedDay_persistsEventAt() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 1, registry)
        SpanishScenario.approve(store, now + 2)
        val today = DateControls.selectionFor(now, now, tz)
        val yesterday = DateControls.stepPrevious(today, tz)
        val eventAt = DateControls.eventAtEpochMs(yesterday, now, tz)
        val modules = TrackerModules.forShape("language")
        val module = modules.first()
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-date-1",
                intentionId = SpanishScenario.INTENTION_ID,
                moduleId = module.id,
                valueText = module.suggestedValues.first(),
                nowEpochMs = now,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user",
                eventAtEpochMs = eventAt
            )
        )
        val entry = store.snapshot().trackerEntries.single { it.id == "trk-date-1" }
        assertEquals(TrackerEntryStatus.ACTIVE, entry.status)
        assertEquals(ProvenanceKind.SAID, entry.provenance.kind)
        assertEquals(eventAt, entry.eventAtEpochMs)
        assertEquals(now, entry.recordedAtEpochMs)
        assertTrue(entry.eventAtEpochMs < entry.recordedAtEpochMs)
    }

    @Test
    fun correctTrackerEntry_canBackdateEventDay() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 1, registry)
        SpanishScenario.approve(store, now + 2)
        val modules = TrackerModules.forShape("language")
        val module = modules.first()
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-date-2",
                intentionId = SpanishScenario.INTENTION_ID,
                moduleId = module.id,
                valueText = module.suggestedValues.first(),
                nowEpochMs = now,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user"
            )
        )
        val yesterday = DateControls.stepPrevious(DateControls.selectionFor(now, now, tz), tz)
        val eventAt = DateControls.eventAtEpochMs(yesterday, now, tz)
        val corrected = module.suggestedValues.first { it != module.suggestedValues.first() }
        store.apply(
            FlowCommand.CorrectTrackerEntry(
                priorEntryId = "trk-date-2",
                newEntryId = "trk-date-2-corr",
                valueText = corrected,
                nowEpochMs = now + 1,
                eventTimeEpochMs = eventAt,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user_correction"
            )
        )
        val neu = store.snapshot().trackerEntries.single { it.id == "trk-date-2-corr" }
        assertEquals(eventAt, neu.eventAtEpochMs)
        assertEquals("user_correction", neu.provenance.sourceLabel)
        assertEquals(ProvenanceKind.SAID, neu.provenance.kind)
    }

    @Test
    fun sessionStartedLabel_todayIncludesClock_notObserved() {
        val started = now - TimeUnit.MINUTES.toMillis(4)
        val label = DateControls.sessionStartedLabel(started, now, tz)
        assertTrue(label.startsWith("Started · Today"))
        assertTrue(label.contains(":"))
        val desc = DateControls.sessionStartedContentDescription(
            startedAtEpochMs = started,
            nowEpochMs = now,
            softPaused = true,
            timeZone = tz
        )
        assertTrue(desc.contains("soft-paused"))
        assertTrue(desc.contains("not lesson completion"))
        assertFalse(desc.contains("OBSERVED", ignoreCase = true) && desc.contains("invent"))
    }

    @Test
    fun entryDayLabel_marksAppliesDay() {
        val yesterdayStart = DailyCycle.startOfLocalDayEpochMs(now, tz) -
            TimeUnit.DAYS.toMillis(1)
        val label = DateControls.entryDayLabel(yesterdayStart + TimeUnit.HOURS.toMillis(12), now, tz)
        assertTrue(label.startsWith("Applies ·"))
        assertTrue(label.contains("Yesterday"))
    }
}
