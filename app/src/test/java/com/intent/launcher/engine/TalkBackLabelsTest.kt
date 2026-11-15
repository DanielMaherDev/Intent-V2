package com.intent.launcher.engine

import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate D / OS-201 — TalkBack container labels without device smoke.
 */
class TalkBackLabelsTest {

    @Test
    fun sessionCardContainer_includesStatusTitleAndActions() {
        val desc = TalkBackLabels.sessionCardContainer(
            statusLabel = "In progress",
            sessionTitle = "Spanish practice",
            intentionTitle = "Learn Spanish",
            softPaused = false
        )
        assertTrue(desc.contains("Session card", ignoreCase = true))
        assertTrue(desc.contains("In progress"))
        assertTrue(desc.contains("Spanish practice"))
        assertTrue(desc.contains("Learn Spanish"))
        assertTrue(desc.contains("Complete"))
        assertTrue(desc.contains("Abandon"))
    }

    @Test
    fun sessionCardContainer_softPausedDefaultStatus() {
        val desc = TalkBackLabels.sessionCardContainer(
            statusLabel = "  ",
            sessionTitle = "",
            intentionTitle = "Learn Spanish",
            softPaused = true
        )
        assertTrue(desc.contains("Away from app"))
        assertTrue(desc.contains("Active session"))
        assertTrue(desc.contains("Learn Spanish"))
    }

    @Test
    fun attentionStrip_activeSession_usesSessionCardWording() {
        val next = NextAction(
            kind = NextActionKind.ACTIVE_SESSION,
            label = "Weekly session",
            detail = "In progress",
            intentionId = "i1"
        )
        val desc = TalkBackLabels.attentionStripContainer(
            nextAction = next,
            smallLabel = "In progress",
            title = "Weekly session",
            support = "Learn Spanish"
        )
        assertTrue(desc.startsWith("Session card"))
        assertTrue(desc.contains("Weekly session"))
    }

    @Test
    fun attentionStrip_thinLabels_expandedWithKindDefaults() {
        val next = NextAction(
            kind = NextActionKind.BEGIN,
            label = "",
            detail = "",
            intentionId = "i1"
        )
        val desc = TalkBackLabels.attentionStripContainer(
            nextAction = next,
            smallLabel = "",
            title = "·",
            support = ""
        )
        assertTrue(desc.contains("Next action"))
        assertTrue(desc.contains("Relevant now"))
        assertTrue(desc.contains("Begin", ignoreCase = true))
        assertFalse(desc.contains("Session card"))
    }

    @Test
    fun expandThin_keepsReadableCopy() {
        assertTrue(TalkBackLabels.expandThin("Begin session", "fallback") == "Begin session")
        assertTrue(TalkBackLabels.expandThin("", "fallback") == "fallback")
        assertTrue(TalkBackLabels.expandThin("·", "fallback") == "fallback")
    }

    @Test
    fun floaterBubble_namesStateAndGestures() {
        val idle = TalkBackLabels.floaterBubble(FloatingAgentState.IDLE)
        assertTrue(idle.contains("INTENT companion"))
        assertTrue(idle.contains("INTENT"))
        assertTrue(idle.contains("Tap to Ask"))
        assertTrue(idle.contains("Long-press"))

        val active = TalkBackLabels.floaterBubble(FloatingAgentState.ACTIVE_SESSION)
        assertTrue(active.contains("In session"))
        assertTrue(active.contains("Conversation"))
    }

    @Test
    fun overlayDeniedFallback_neverClaimsBubbleOrGrant() {
        val note = TalkBackLabels.overlayDeniedFallbackNote()
        assertTrue(note.contains("no floating bubble", ignoreCase = true))
        assertTrue(note.contains("Discuss", ignoreCase = true))
        assertTrue(note.contains("Companion menu", ignoreCase = true))
        assertFalse(note.contains("overlay granted", ignoreCase = true))
        assertFalse(note.contains("bubble is showing", ignoreCase = true))
    }
}
