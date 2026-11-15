package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.SessionElapsed
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import java.io.File
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
 * Begin → active session → complete / abandon via the command path,
 * with capability-registry honesty (no fake calendar / LLM completion).
 */
class SessionBeginExecutionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default
    private val seedAt = 1_700_049_600_000L
    private val dueNow = 1_700_159_400_000L // Thu 16 Nov 2023 18:30 UTC

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveSpanish() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
    }

    @Test
    fun begin_createsInProgressRun_andSurfacesActiveSession() {
        approveSpanish()
        val before = NextActionEngine.recommend(store.snapshot(), dueNow, tz, registry)
        assertEquals(NextActionKind.BEGIN, before.kind)
        assertEquals(SpanishScenario.SESSION_ID, before.sessionId)

        SpanishScenario.beginSession(store, dueNow, runId = "run-exec-1")
        val snap = store.snapshot()
        val run = snap.sessionRuns.single()
        assertEquals(SessionRunStatus.IN_PROGRESS, run.status)
        assertEquals(SpanishScenario.SESSION_ID, run.sessionId)
        assertNull(run.endedAtEpochMs)

        val active = NextActionEngine.recommend(snap, dueNow + 60_000L, tz, registry)
        assertEquals(NextActionKind.ACTIVE_SESSION, active.kind)
        assertEquals("run-exec-1", active.sessionRunId)
        assertEquals("Complete", active.completeLabel)
        assertEquals("Abandon", active.abandonLabel)
        assertEquals(dueNow, active.startedAtEpochMs)
        assertEquals(dueNow, run.startedAtEpochMs)
        assertEquals("1:00", SessionElapsed.format(run.startedAtEpochMs, dueNow + 60_000L))
        assertTrue(active.reason.contains("user-reported", ignoreCase = true))
    }

    @Test
    fun begin_clearsDeferral_andBlocksDeferWhileActive() {
        approveSpanish()
        val deferUntil = dueNow + 3_600_000L
        SpanishScenario.deferSession(store, deferUntil, dueNow)
        assertEquals(deferUntil, store.snapshot().sessions.first().deferredUntilEpochMs)

        SpanishScenario.beginSession(store, dueNow + 10, runId = "run-clear-defer")
        assertNull(store.snapshot().sessions.first().deferredUntilEpochMs)

        store.apply(
            FlowCommand.DeferNextSession(
                sessionId = SpanishScenario.SESSION_ID,
                deferUntilEpochMs = deferUntil + 1,
                nowEpochMs = dueNow + 20
            )
        )
        // Defer while in progress is a no-op
        assertNull(store.snapshot().sessions.first().deferredUntilEpochMs)
        assertEquals(
            SessionRunStatus.IN_PROGRESS,
            store.snapshot().sessionRuns.single().status
        )
    }

    @Test
    fun complete_endsRun_andReturnsToScheduleRecommendation() {
        approveSpanish()
        SpanishScenario.beginSession(store, dueNow, runId = "run-complete")
        store.apply(FlowCommand.CompleteSessionRun(runId = "run-complete", nowEpochMs = dueNow + 30))
        val snap = store.snapshot()
        val run = snap.sessionRuns.single()
        assertEquals(SessionRunStatus.COMPLETED, run.status)
        assertNotNull(run.endedAtEpochMs)

        // Evening + a run today → check-out outranks another Begin.
        val next = NextActionEngine.recommend(snap, dueNow + 60, tz, registry)
        assertEquals(NextActionKind.CHECK_OUT, next.kind)
        assertFalse(next.kind == NextActionKind.ACTIVE_SESSION)
    }

    @Test
    fun abandon_endsRun_withoutClaimingCompletion() {
        approveSpanish()
        SpanishScenario.beginSession(store, dueNow, runId = "run-abandon")
        store.apply(FlowCommand.AbandonSessionRun(runId = "run-abandon", nowEpochMs = dueNow + 30))
        val run = store.snapshot().sessionRuns.single()
        assertEquals(SessionRunStatus.ABANDONED, run.status)
        assertNotNull(run.endedAtEpochMs)

        val next = NextActionEngine.recommend(store.snapshot(), dueNow + 60, tz, registry)
        assertEquals(NextActionKind.CHECK_OUT, next.kind)
        assertFalse(next.kind == NextActionKind.ACTIVE_SESSION)
    }

    @Test
    fun whenBeginLocked_viewModelPathWouldNotStart_andEngineFallsBack() {
        val noBegin = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
                if (entry.id == CapabilityRegistry.ID_BEGIN_SESSION) {
                    entry.copy(
                        status = CapabilityStatus.NOT_SUPPORTED,
                        limitNote = "Beginning a session is disabled in this test registry."
                    )
                } else {
                    entry
                }
            }
        )
        approveSpanish()
        assertFalse(noBegin.canRecommendBegin())
        val next = NextActionEngine.recommend(store.snapshot(), dueNow, tz, noBegin)
        assertTrue(next.kind != NextActionKind.BEGIN)
        assertTrue(next.reason.contains("disabled", ignoreCase = true))
        // Command path still works if invoked directly (UI/ViewModel gates on registry)
        SpanishScenario.beginSession(store, dueNow, runId = "run-despite-lock")
        assertEquals(1, store.snapshot().sessionRuns.size)
    }

    @Test
    fun whenCompleteAndAbandonLocked_activeRunFallsBackToDiscuss() {
        val locked = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
                when (entry.id) {
                    CapabilityRegistry.ID_COMPLETE_SESSION,
                    CapabilityRegistry.ID_ABANDON_SESSION ->
                        entry.copy(status = CapabilityStatus.NOT_SUPPORTED)
                    else -> entry
                }
            }
        )
        approveSpanish()
        SpanishScenario.beginSession(store, dueNow, runId = "run-locked-end")
        val next = NextActionEngine.recommend(store.snapshot(), dueNow + 1, tz, locked)
        assertEquals(NextActionKind.DISCUSS, next.kind)
        assertEquals("run-locked-end", next.sessionRunId)
        assertNull(next.completeLabel)
        assertNull(next.abandonLabel)
    }

    @Test
    fun activeSession_survivesStoreRestart() {
        approveSpanish()
        SpanishScenario.beginSession(store, dueNow, runId = "run-persist")
        val restarted = IntentionFlowStore(File(tmp.root, "flow.json"))
        val snap = restarted.snapshot()
        assertEquals(6, snap.schemaVersion)
        assertEquals(SessionRunStatus.IN_PROGRESS, snap.sessionRuns.single().status)
        val next = NextActionEngine.recommend(snap, dueNow + 1, tz, registry)
        assertEquals(NextActionKind.ACTIVE_SESSION, next.kind)
    }
}
