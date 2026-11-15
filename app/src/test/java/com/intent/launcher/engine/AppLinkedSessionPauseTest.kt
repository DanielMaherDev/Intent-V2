package com.intent.launcher.engine

import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.SessionElapsed
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
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
 * OS-166–167 — app-linked soft-pause honesty.
 */
class AppLinkedSessionPauseTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val seedAt = 1_700_049_600_000L
    private val beginAt = 1_700_159_400_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun beginSpanishWithAffinity(): String {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
        store.apply(
            FlowCommand.BeginSession(
                runId = "run-pause-1",
                sessionId = SpanishScenario.SESSION_ID,
                nowEpochMs = beginAt,
                affinityPackageName = SessionDestinations.SPANISH_PACKAGE,
                affinityAppLabel = SessionDestinations.SPANISH_LABEL
            )
        )
        return "run-pause-1"
    }

    @Test
    fun noAffinity_noAutoPause() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
        store.apply(
            FlowCommand.BeginSession(
                runId = "run-no-aff",
                sessionId = SpanishScenario.SESSION_ID,
                nowEpochMs = beginAt
            )
        )
        val run = store.snapshot().sessionRuns.single()
        val access = FakeUsageStatsAccess(
            granted = true,
            currentForeground = "com.instagram.android"
        )
        val eval = AppLinkedSessionPause.evaluate(
            access = access,
            run = run,
            destination = null,
            nowEpochMs = beginAt + 60_000L
        )
        assertFalse(eval.shouldAutoPause)
        assertTrue(eval.note.contains("No package affinity", ignoreCase = true))
        assertEquals(SessionRunStatus.IN_PROGRESS, store.snapshot().sessionRuns.single().status)
    }

    @Test
    fun withoutUsageStats_noAutoPause_timerKeepsLocalClock() {
        val runId = beginSpanishWithAffinity()
        val run = store.snapshot().sessionRuns.single { it.id == runId }
        val access = FakeUsageStatsAccess(granted = false)
        val now = beginAt + 60_000L
        val eval = AppLinkedSessionPause.evaluate(
            access = access,
            run = run,
            destination = DestinationApp(
                SessionDestinations.SPANISH_PACKAGE,
                SessionDestinations.SPANISH_LABEL
            ),
            nowEpochMs = now
        )
        assertFalse(eval.shouldAutoPause)
        assertFalse(eval.usageStatsGranted)
        assertTrue(eval.note.contains("unavailable", ignoreCase = true))
        assertEquals("1:00", eval.elapsedText)
        assertEquals(SessionRunStatus.IN_PROGRESS, store.snapshot().sessionRuns.single().status)
    }

    @Test
    fun awayBeyondThreshold_requestsSoftPause_notComplete() {
        val runId = beginSpanishWithAffinity()
        val run = store.snapshot().sessionRuns.single { it.id == runId }
        val leftAt = beginAt + 20_000L
        val now = leftAt + AppLinkedSessionPause.AWAY_THRESHOLD_MS + 1_000L
        val access = FakeUsageStatsAccess(
            granted = true,
            currentForeground = "com.android.chrome",
            lastLeftForegroundByPackage = mapOf(SessionDestinations.SPANISH_PACKAGE to leftAt)
        )
        val eval = AppLinkedSessionPause.evaluate(
            access = access,
            run = run,
            destination = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID),
            nowEpochMs = now
        )
        assertTrue(eval.shouldAutoPause)
        assertTrue(eval.note.contains("Away from", ignoreCase = true))
        assertTrue(eval.note.contains("not lesson completion", ignoreCase = true))
        assertEquals(SessionRunStatus.IN_PROGRESS, run.status)

        store.apply(FlowCommand.SoftPauseSessionRun(runId, now))
        val paused = store.snapshot().sessionRuns.single()
        assertEquals(now, paused.pausedAtEpochMs)
        assertEquals(SessionRunStatus.IN_PROGRESS, paused.status)
        assertNull(paused.endedAtEpochMs)
    }

    @Test
    fun inAffinity_autoResumes_whenSoftPaused() {
        val runId = beginSpanishWithAffinity()
        store.apply(FlowCommand.SoftPauseSessionRun(runId, beginAt + 30_000L))
        val run = store.snapshot().sessionRuns.single()
        val access = FakeUsageStatsAccess(
            granted = true,
            currentForeground = SessionDestinations.SPANISH_PACKAGE
        )
        val eval = AppLinkedSessionPause.evaluate(
            access = access,
            run = run,
            destination = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID),
            nowEpochMs = beginAt + 45_000L
        )
        assertTrue(eval.shouldAutoResume)
        assertTrue(eval.canResume)

        store.apply(FlowCommand.ResumeSessionRun(runId, beginAt + 45_000L))
        val resumed = store.snapshot().sessionRuns.single()
        assertNull(resumed.pausedAtEpochMs)
        assertEquals(15_000L, resumed.accumulatedPausedMs)
        assertEquals(SessionRunStatus.IN_PROGRESS, resumed.status)
    }

    @Test
    fun softPause_freezesElapsed() {
        val runId = beginSpanishWithAffinity()
        val pauseAt = beginAt + 90_000L
        store.apply(FlowCommand.SoftPauseSessionRun(runId, pauseAt))
        val run = store.snapshot().sessionRuns.single()
        assertEquals("1:30", SessionElapsed.formatForRun(run, pauseAt + 60_000L))
        assertEquals(
            90_000L,
            SessionElapsed.activeElapsedMs(run, pauseAt + 120_000L)
        )
    }

    @Test
    fun briefAway_underThreshold_doesNotPause() {
        val runId = beginSpanishWithAffinity()
        val run = store.snapshot().sessionRuns.single { it.id == runId }
        val leftAt = beginAt + 10_000L
        val now = leftAt + 5_000L // under 15s
        val access = FakeUsageStatsAccess(
            granted = true,
            currentForeground = "com.android.chrome",
            lastLeftForegroundByPackage = mapOf(SessionDestinations.SPANISH_PACKAGE to leftAt)
        )
        val eval = AppLinkedSessionPause.evaluate(
            access = access,
            run = run,
            destination = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID),
            nowEpochMs = now
        )
        assertFalse(eval.shouldAutoPause)
        assertFalse(eval.softPaused)
        assertNotNull(eval.note)
    }
}
