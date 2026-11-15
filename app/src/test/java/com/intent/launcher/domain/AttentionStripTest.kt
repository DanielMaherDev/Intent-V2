package com.intent.launcher.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Home attention strip honesty: secondary line is elapsed XOR OBSERVED minutes.
 */
class AttentionStripTest {

    private val active = NextAction(
        kind = NextActionKind.ACTIVE_SESSION,
        label = "Tutor session",
        detail = "Session in progress",
        reason = "You started this session.",
        intentionId = "i1",
        sessionId = "s1",
        sessionRunId = "r1",
        startedAtEpochMs = 1_000L,
        completeLabel = "Complete",
        abandonLabel = "Abandon",
        discussLabel = "Discuss"
    )

    private val begin = NextAction(
        kind = NextActionKind.BEGIN,
        label = "Prepare",
        detail = "Today at 18:00",
        reason = "It is time for your scheduled session.",
        intentionId = "i1",
        sessionId = "s1",
        beginLabel = "Begin",
        deferLabel = "Later",
        discussLabel = "Discuss"
    )

    @Test
    fun secondary_prefersObservedMinutesWhenGranted() {
        val line = AttentionStrip.secondaryLine(
            nextAction = active,
            nowEpochMs = 1_000L + 90_000L,
            usageGranted = true,
            usageForegroundMs = 125_000L,
            usageAppLabel = "Duolingo"
        )
        assertTrue(line is AttentionStrip.SecondaryLine.ObservedPackageMinutes)
        val observed = line as AttentionStrip.SecondaryLine.ObservedPackageMinutes
        assertTrue(observed.text.contains("Duolingo"))
        assertTrue(observed.text.contains("2:05") || observed.text.contains("~"))
        assertTrue(observed.contentDescription.contains("not lesson", ignoreCase = true))
    }

    @Test
    fun secondary_fallsBackToElapsedWhenUsageMissing() {
        val line = AttentionStrip.secondaryLine(
            nextAction = active,
            nowEpochMs = 1_000L + 65_000L,
            usageGranted = false,
            usageForegroundMs = null,
            usageAppLabel = null
        )
        assertTrue(line is AttentionStrip.SecondaryLine.SessionElapsed)
        val elapsed = line as AttentionStrip.SecondaryLine.SessionElapsed
        assertEquals("Elapsed 1:05", elapsed.text)
    }

    @Test
    fun secondary_omitsWhenUsageGrantedButMinutesUnknown() {
        // Honesty: never invent minutes — omit OBSERVED line; fall back to elapsed.
        val line = AttentionStrip.secondaryLine(
            nextAction = active,
            nowEpochMs = 1_000L + 30_000L,
            usageGranted = true,
            usageForegroundMs = null,
            usageAppLabel = "Duolingo"
        )
        assertTrue(line is AttentionStrip.SecondaryLine.SessionElapsed)
    }

    @Test
    fun secondary_omitsForNonActiveWithoutUsage() {
        val line = AttentionStrip.secondaryLine(
            nextAction = begin,
            nowEpochMs = 2_000L,
            usageGranted = false,
            usageForegroundMs = null,
            usageAppLabel = null
        )
        assertNull(line)
    }


    @Test
    fun secondary_prefersPausedElapsedWhenSoftPaused() {
        val line = AttentionStrip.secondaryLine(
            nextAction = active,
            nowEpochMs = 1_000L + 90_000L,
            usageGranted = true,
            usageForegroundMs = 125_000L,
            usageAppLabel = "Duolingo",
            pausedAtEpochMs = 1_000L + 60_000L,
            accumulatedPausedMs = 0L,
            softPaused = true
        )
        assertTrue(line is AttentionStrip.SecondaryLine.SessionElapsed)
        val elapsed = line as AttentionStrip.SecondaryLine.SessionElapsed
        assertTrue(elapsed.text.startsWith("Paused "))
        assertTrue(elapsed.contentDescription.contains("not lesson", ignoreCase = true))
    }

    @Test
    fun supportingSentence_prefersReasonOverDetail() {
        assertEquals(
            "It is time for your scheduled session.",
            AttentionStrip.supportingSentence(begin)
        )
    }

    @Test
    fun supportingSentence_usesDetailWhenReasonBlank() {
        val action = begin.copy(reason = "")
        assertEquals("Today at 18:00", AttentionStrip.supportingSentence(action))
    }
}
