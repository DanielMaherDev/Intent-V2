package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Worksheet §10 — morning check-in / active session / evening check-out
 * on the same intention model (no separate daily-task store).
 */
class DailyCheckInCheckoutTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default

    private val seedAt = 1_700_049_600_000L // Wed 15 Nov 2023 12:00 UTC
    /** Thursday 09:00 UTC — morning check-in window. */
    private val thu0900 = 1_700_125_200_000L
    /** Thursday 18:30 UTC — session due + evening check-out window. */
    private val thu1830 = 1_700_159_400_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveSpanish() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1, registry)
        SpanishScenario.approve(store, seedAt + 2)
    }

    @Test
    fun morning_surfacesCheckIn_withFocusAndNextAction() {
        approveSpanish()
        val next = NextActionEngine.recommend(store.snapshot(), thu0900, tz, registry)
        assertEquals(NextActionKind.CHECK_IN, next.kind)
        assertTrue(next.detail.contains("Focus:", ignoreCase = true))
        assertTrue(next.detail.contains("Next:", ignoreCase = true))
        assertEquals("Confirm focus", next.confirmLabel)
        assertNotNull(next.adjustLabel)
        assertEquals(SpanishScenario.SESSION_ID, next.sessionId)
        assertTrue(next.reason.contains("same Home", ignoreCase = true))
    }

    @Test
    fun confirmCheckIn_persistsDayCycle_thenBeginAvailableLater() {
        approveSpanish()
        val localDate = DailyCycle.localDateString(thu0900, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Focus: Speak Spanish · Next: Thursday session",
                nowEpochMs = thu0900,
                adjustedFocusNote = "Prioritize speaking practice today",
                adjustedFactId = "fact-checkin-1",
                adjustedHypothesisId = "hyp-checkin-1"
            )
        )
        val snap = store.snapshot()
        assertTrue(snap.dayCycle.checkedInFor(localDate))
        assertEquals(SpanishScenario.INTENTION_ID, snap.dayCycle.focusIntentionId)
        val fact = snap.facts.first { it.id == "fact-checkin-1" }
        assertEquals(ProvenanceKind.SAID, fact.provenance.kind)
        assertEquals("user", fact.provenance.sourceLabel)
        val hyp = snap.hypotheses.first { it.id == "hyp-checkin-1" }
        assertEquals(ProvenanceKind.SAID, hyp.provenance.kind)

        // Mid-afternoon (not morning): Begin, not another check-in.
        val mid = NextActionEngine.recommend(snap, thu1830 - 3 * 3_600_000L, tz, registry)
        assertEquals(NextActionKind.BEGIN, mid.kind)

        // Due evening without checkout yet but without morning-only gate: Begin still works
        // until checkout eligibility (checked-in → evening offers CHECK_OUT).
        val evening = NextActionEngine.recommend(snap, thu1830, tz, registry)
        assertEquals(NextActionKind.CHECK_OUT, evening.kind)
    }

    @Test
    fun activeSession_outranksCheckInAndCheckOut() {
        approveSpanish()
        val localDate = DailyCycle.localDateString(thu0900, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Focus test",
                nowEpochMs = thu0900
            )
        )
        SpanishScenario.beginSession(store, thu1830, runId = "run-daycycle-1")
        val active = NextActionEngine.recommend(store.snapshot(), thu1830, tz, registry)
        assertEquals(NextActionKind.ACTIVE_SESSION, active.kind)
        assertEquals("Complete", active.completeLabel)
        assertEquals("Abandon", active.abandonLabel)
    }

    @Test
    fun eveningCheckOut_recordsSaid_andCanDeferTomorrow() {
        approveSpanish()
        val localDate = DailyCycle.localDateString(thu1830, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Focus test",
                nowEpochMs = thu0900
            )
        )
        SpanishScenario.beginSession(store, thu1830, runId = "run-daycycle-complete")
        store.apply(
            FlowCommand.CompleteSessionRun(
                runId = "run-daycycle-complete",
                nowEpochMs = thu1830 + 30
            )
        )
        val deferUntil = DailyCycle.tomorrowMorningEpochMs(thu1830 + 60, tz)
        store.apply(
            FlowCommand.CompleteDailyCheckOut(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                whatHappened = "Finished the tutor session; felt rusty on verbs",
                factId = "fact-checkout-1",
                nowEpochMs = thu1830 + 60,
                hypothesisId = "hyp-checkout-1",
                hypothesisText = "Tomorrow: shorter speaking drill before the hour",
                deferSessionId = SpanishScenario.SESSION_ID,
                deferUntilEpochMs = deferUntil
            )
        )
        val snap = store.snapshot()
        assertTrue(snap.dayCycle.checkedOutFor(localDate))
        val fact = snap.facts.first { it.id == "fact-checkout-1" }
        assertEquals(ProvenanceKind.SAID, fact.provenance.kind)
        assertTrue(fact.text.contains("Finished the tutor", ignoreCase = true))
        val hyp = snap.hypotheses.first { it.id == "hyp-checkout-1" }
        assertEquals(ProvenanceKind.SAID, hyp.provenance.kind)
        assertEquals(deferUntil, snap.sessions.first().deferredUntilEpochMs)

        val after = NextActionEngine.recommend(snap, thu1830 + 120, tz, registry)
        assertFalse(after.kind == NextActionKind.CHECK_OUT)
        assertFalse(after.kind == NextActionKind.CHECK_IN)
    }

    @Test
    fun eveningCheckOut_keepSchedule_leavesDeferralNull() {
        approveSpanish()
        val localDate = DailyCycle.localDateString(thu1830, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Focus test",
                nowEpochMs = thu0900
            )
        )
        store.apply(
            FlowCommand.CompleteDailyCheckOut(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                whatHappened = "Kept the usual plan",
                factId = "fact-checkout-keep",
                nowEpochMs = thu1830
            )
        )
        assertNull(store.snapshot().sessions.first().deferredUntilEpochMs)
        assertTrue(store.snapshot().dayCycle.checkedOutFor(localDate))
    }

    @Test
    fun registryLock_skipsCheckInAndCheckOut() {
        approveSpanish()
        val locked = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map {
                when (it.id) {
                    CapabilityRegistry.ID_DAILY_CHECK_IN,
                    CapabilityRegistry.ID_DAILY_CHECK_OUT ->
                        it.copy(status = CapabilityStatus.NOT_SUPPORTED)
                    else -> it
                }
            }
        )
        val morning = NextActionEngine.recommend(store.snapshot(), thu0900, tz, locked)
        assertEquals(NextActionKind.BEGIN, morning.kind)

        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = DailyCycle.localDateString(thu0900, tz),
                focusSummary = "manual",
                nowEpochMs = thu0900
            )
        )
        val evening = NextActionEngine.recommend(store.snapshot(), thu1830, tz, locked)
        assertEquals(NextActionKind.BEGIN, evening.kind)
    }

    @Test
    fun restart_persistsDayCycle_schemaV3() {
        approveSpanish()
        val localDate = DailyCycle.localDateString(thu0900, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Persisted focus",
                nowEpochMs = thu0900
            )
        )
        val file = tmp.newFile("restart-day.json")
        file.writeText(store.encode(store.snapshot()))
        val reloaded = IntentionFlowStore(file)
        assertEquals(
            com.intent.launcher.domain.SCHEMA_VERSION,
            reloaded.snapshot().schemaVersion
        )
        assertTrue(reloaded.snapshot().dayCycle.checkedInFor(localDate))
        assertEquals("Persisted focus", reloaded.snapshot().dayCycle.focusSummary)
    }

    @Test
    fun fullMorningToEvening_offlineCycle_withInterruption() {
        approveSpanish()
        val localDate = DailyCycle.localDateString(thu0900, tz)

        val morning = NextActionEngine.recommend(store.snapshot(), thu0900, tz, registry)
        assertEquals(NextActionKind.CHECK_IN, morning.kind)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = morning.detail,
                nowEpochMs = thu0900
            )
        )

        // Begin → complete (interruption-safe via durable SessionRun).
        SpanishScenario.beginSession(store, thu1830, runId = "run-cycle")
        assertEquals(
            SessionRunStatus.IN_PROGRESS,
            store.snapshot().sessionRuns.single().status
        )
        val mid = NextActionEngine.recommend(store.snapshot(), thu1830 + 10, tz, registry)
        assertEquals(NextActionKind.ACTIVE_SESSION, mid.kind)
        store.apply(
            FlowCommand.CompleteSessionRun(runId = "run-cycle", nowEpochMs = thu1830 + 20)
        )

        val checkout = NextActionEngine.recommend(store.snapshot(), thu1830 + 30, tz, registry)
        assertEquals(NextActionKind.CHECK_OUT, checkout.kind)
        store.apply(
            FlowCommand.CompleteDailyCheckOut(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                whatHappened = "Completed after a short interruption",
                factId = "fact-cycle",
                nowEpochMs = thu1830 + 40
            )
        )
        assertTrue(store.snapshot().dayCycle.checkedOutFor(localDate))
        assertEquals(
            ProvenanceKind.SAID,
            store.snapshot().facts.first { it.id == "fact-cycle" }.provenance.kind
        )
    }
}
