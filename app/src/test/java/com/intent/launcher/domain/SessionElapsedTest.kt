package com.intent.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionElapsedTest {

    @Test
    fun format_zeroAndNegativeClampToZero() {
        assertEquals("0:00", SessionElapsed.format(1_000L, 1_000L))
        assertEquals("0:00", SessionElapsed.format(2_000L, 1_000L))
    }

    @Test
    fun format_minutesAndSeconds() {
        assertEquals("0:05", SessionElapsed.format(0L, 5_000L))
        assertEquals("1:05", SessionElapsed.format(0L, 65_000L))
        assertEquals("59:59", SessionElapsed.format(0L, 3_599_000L))
    }

    @Test
    fun format_hours() {
        assertEquals("1:00:00", SessionElapsed.format(0L, 3_600_000L))
        assertEquals("1:02:03", SessionElapsed.format(0L, 3_723_000L))
    }

    @Test
    fun label_prefixesElapsed() {
        assertEquals("Elapsed 5:03", SessionElapsed.label(0L, 303_000L))
    }

    @Test
    fun softPause_excludesPausedGap() {
        val started = 1_000L
        val pausedAt = 61_000L // 60s active
        val now = 121_000L
        assertEquals(
            60_000L,
            SessionElapsed.activeElapsedMs(started, now, pausedAt, 0L)
        )
        assertEquals("1:00", SessionElapsed.formatMs(60_000L))
    }

    @Test
    fun accumulatedPause_subtractsPriorGaps() {
        // 3 minutes wall from start, 30s previously paused, currently running
        val started = 0L
        val now = 180_000L
        assertEquals(
            150_000L,
            SessionElapsed.activeElapsedMs(started, now, null, 30_000L)
        )
        assertEquals("2:30", SessionElapsed.formatMs(150_000L))
    }

    @Test
    fun formatForRun_usesPauseFields() {
        val run = SessionRun(
            id = "r1",
            sessionId = "s1",
            intentionId = "i1",
            title = "Practice",
            status = SessionRunStatus.IN_PROGRESS,
            startedAtEpochMs = 0L,
            pausedAtEpochMs = 125_000L,
            accumulatedPausedMs = 0L
        )
        assertEquals("2:05", SessionElapsed.formatForRun(run, 999_000L))
        assertEquals("Paused 2:05", SessionElapsed.labelForRun(run, 999_000L))
    }
}
