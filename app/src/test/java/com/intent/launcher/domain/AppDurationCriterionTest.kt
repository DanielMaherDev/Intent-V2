package com.intent.launcher.domain

import com.intent.launcher.engine.AppDurationTask
import com.intent.launcher.store.IntentionFlowStore
import java.io.File
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
 * App-duration criterion projection — completed/target + soft-pause honesty.
 * Never invents lesson completion or auto-Completes.
 */
class AppDurationCriterionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var storeFile: File
    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        storeFile = tmp.newFile("app-duration-criterion.json")
        store = IntentionFlowStore(storeFile)
    }

    @Test
    fun parseFact_readsExplicitDurationCriterion() {
        assertEquals(
            15,
            AppDurationCriterion.parseMinutesFromFact(
                "Explicit duration criterion: 15 minutes in Duolingo " +
                    "(foreground timing / soft-pause) — not lesson completion."
            )
        )
    }

    @Test
    fun parseFact_ignoresIncidentalMinutes() {
        assertNull(
            AppDurationCriterion.parseMinutesFromFact(
                "I usually wait 15 minutes after coffee."
            )
        )
    }

    @Test
    fun parseTitle_readsParentheticalMinutes() {
        assertEquals(15, AppDurationCriterion.parseMinutesFromTitle("Duolingo practice (15 min)"))
        assertNull(AppDurationCriterion.parseMinutesFromTitle("Tutor session"))
    }

    @Test
    fun progress_softPauseFreezesCompletedTarget_noLessonClaim() {
        val run = SessionRun(
            id = "r1",
            sessionId = AppDurationTask.DURATION_SESSION_ID,
            intentionId = SpanishScenario.INTENTION_ID,
            title = "Duolingo practice (15 min)",
            status = SessionRunStatus.IN_PROGRESS,
            startedAtEpochMs = now,
            affinityPackageName = SessionDestinations.SPANISH_PACKAGE,
            affinityAppLabel = SessionDestinations.SPANISH_LABEL,
            pausedAtEpochMs = now + 5 * 60_000L,
            accumulatedPausedMs = 0L
        )
        val progress = AppDurationCriterion.progress(
            run = run,
            targetMinutes = 15,
            nowEpochMs = now + 20 * 60_000L
        )
        assertEquals(5, progress.completedMinutes)
        assertEquals("5 / 15 min", progress.ratioLabel)
        assertTrue(progress.softPaused)
        assertFalse(progress.localTimerMet)
        assertTrue(progress.fraction in 0.33f..0.34f)
        assertTrue(progress.honestyNote.contains("Soft-paused", ignoreCase = true))
        assertTrue(progress.honestyNote.contains("not lesson", ignoreCase = true))
        assertFalse(progress.honestyNote.contains("lesson was completed", ignoreCase = true))
    }

    @Test
    fun progress_localTimerMet_stillNotLessonCompletion() {
        val progress = AppDurationCriterion.progressFromElapsed(
            targetMinutes = 15,
            elapsedMs = 15 * 60_000L,
            softPaused = false
        )
        assertEquals(15, progress.completedMinutes)
        assertEquals("15 / 15 min", progress.ratioLabel)
        assertEquals(1f, progress.fraction, 0.001f)
        assertTrue(progress.localTimerMet)
        assertTrue(progress.honestyNote.contains("not lesson completion", ignoreCase = true))
        assertTrue(progress.honestyNote.contains("Complete when", ignoreCase = true))
    }

    @Test
    fun forActiveRun_resolvesFromAppDurationTaskFixture() {
        val result = AppDurationTask.startFifteenMinutePractice(
            store = store,
            nowEpochMs = now,
            utterance = "Practise in Duolingo for 15 minutes."
        )
        assertNotNull(result.runId)
        val snap = store.snapshot()
        val run = snap.sessionRuns.first { it.id == result.runId }
        // After startFifteenMinutePractice the run is resumed (not paused).
        val resumed = run.copy(pausedAtEpochMs = null, accumulatedPausedMs = 1_000L)
        val session = snap.sessions.firstOrNull { it.id == resumed.sessionId }
        val progress = AppDurationCriterion.forActiveRun(
            run = resumed,
            session = session,
            facts = snap.facts,
            nowEpochMs = now + 5 + 7 * 60_000L,
            proposals = snap.proposals
        )
        assertNotNull(progress)
        assertEquals(15, progress!!.targetMinutes)
        assertTrue(progress.completedMinutes in 6..7)
        assertFalse(progress.softPaused)
        assertFalse(progress.localTimerMet)
    }

    @Test
    fun forActiveRun_nullWhenNoCriterion() {
        val run = SessionRun(
            id = "r2",
            sessionId = "s-plain",
            intentionId = "i-plain",
            title = "Tutor session",
            status = SessionRunStatus.IN_PROGRESS,
            startedAtEpochMs = now
        )
        assertNull(
            AppDurationCriterion.forActiveRun(
                run = run,
                session = null,
                facts = emptyList(),
                nowEpochMs = now + 60_000L
            )
        )
    }

    @Test
    fun attentionStrip_prefersDurationProgressOverObserved() {
        val progress = AppDurationCriterion.progressFromElapsed(
            targetMinutes = 15,
            elapsedMs = 3 * 60_000L,
            softPaused = false
        )
        val next = NextAction(
            kind = NextActionKind.ACTIVE_SESSION,
            label = "Duolingo practice",
            detail = "Session in progress",
            reason = "You started this session.",
            intentionId = SpanishScenario.INTENTION_ID,
            sessionId = AppDurationTask.DURATION_SESSION_ID,
            sessionRunId = "r1",
            startedAtEpochMs = now,
            completeLabel = "Complete",
            abandonLabel = "Abandon",
            discussLabel = "Discuss"
        )
        val line = AttentionStrip.secondaryLine(
            nextAction = next,
            nowEpochMs = now + 3 * 60_000L,
            usageGranted = true,
            usageForegroundMs = 180_000L,
            usageAppLabel = "Duolingo",
            durationProgress = progress
        )
        assertTrue(line is AttentionStrip.SecondaryLine.DurationProgress)
        val duration = line as AttentionStrip.SecondaryLine.DurationProgress
        assertEquals("3 / 15 min", duration.ratioLabel)
        assertTrue(duration.text.contains("3 / 15 min"))
        assertTrue(duration.contentDescription.contains("not lesson", ignoreCase = true))
    }

    @Test
    fun attentionStrip_durationSoftPauseFreezesRatio() {
        val progress = AppDurationCriterion.progressFromElapsed(
            targetMinutes = 15,
            elapsedMs = 4 * 60_000L,
            softPaused = true
        )
        val next = NextAction(
            kind = NextActionKind.ACTIVE_SESSION,
            label = "Duolingo practice",
            detail = "Session in progress",
            reason = "You started this session.",
            intentionId = SpanishScenario.INTENTION_ID,
            sessionId = AppDurationTask.DURATION_SESSION_ID,
            sessionRunId = "r1",
            startedAtEpochMs = now,
            completeLabel = "Complete",
            abandonLabel = "Abandon",
            discussLabel = "Discuss"
        )
        val line = AttentionStrip.secondaryLine(
            nextAction = next,
            nowEpochMs = now + 30 * 60_000L,
            usageGranted = true,
            usageForegroundMs = 999_000L,
            usageAppLabel = "Duolingo",
            softPaused = true,
            pausedAtEpochMs = now + 4 * 60_000L,
            durationProgress = progress
        )
        assertTrue(line is AttentionStrip.SecondaryLine.DurationProgress)
        val duration = line as AttentionStrip.SecondaryLine.DurationProgress
        assertTrue(duration.softPaused)
        assertEquals("4 / 15 min", duration.ratioLabel)
        assertTrue(duration.text.startsWith("Paused"))
    }
}
