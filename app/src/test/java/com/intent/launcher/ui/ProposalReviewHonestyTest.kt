package com.intent.launcher.ui

import com.intent.launcher.ui.screens.HeldAppsHonesty
import com.intent.launcher.ui.screens.ProposalReviewHonesty
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gate C5 — review / held-apps copy must keep capabilities distinct. */
class ProposalReviewHonestyTest {

    @Test
    fun specialistApp_doesNotBlurLessonsWithHabitManagement() {
        val copy = ProposalReviewHonesty.SPECIALIST_APP
        assertFalse(copy.contains("Structured lessons", ignoreCase = true))
        assertFalse(copy.contains("manages the habit", ignoreCase = true))
        assertTrue(copy.contains("Install or launch", ignoreCase = true))
        assertTrue(copy.contains("cannot read", ignoreCase = true))
    }

    @Test
    fun emptyCallout_keepsFourCapabilitiesDistinct() {
        val copy = ProposalReviewHonesty.EMPTY_APPROACH_CALLOUT
        assertTrue(copy.contains("Install", ignoreCase = true))
        assertTrue(copy.contains("launch", ignoreCase = true))
        assertTrue(copy.contains("usage", ignoreCase = true))
        assertTrue(copy.contains("progress-read", ignoreCase = true))
        assertTrue(copy.contains("never claims Duolingo", ignoreCase = true))
    }

    @Test
    fun appAccessFallback_notLessonProgressRead() {
        val copy = ProposalReviewHonesty.APP_ACCESS_FALLBACK
        assertTrue(copy.contains("install or launch", ignoreCase = true))
        assertTrue(copy.contains("cannot read lesson progress", ignoreCase = true))
    }

    @Test
    fun heldApps_honestyKeepsHoldDistinctFromUsageAndProgress() {
        assertTrue(HeldAppsHonesty.MANAGE_INTRO.contains("UsageStats", ignoreCase = true))
        assertTrue(HeldAppsHonesty.MANAGE_INTRO.contains("lesson progress-read", ignoreCase = true))
        val held = HeldAppsHonesty.heldLine("com.duolingo")
        assertTrue(held.contains("SAID"))
        assertTrue(held.contains("not lesson progress"))
        assertTrue(held.contains("com.duolingo"))
    }
}
