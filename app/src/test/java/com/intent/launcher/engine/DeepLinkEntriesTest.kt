package com.intent.launcher.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shipped floater / companion deep entries — honesty inventory for Gate D1 leftover.
 * Action strings mirror FloatingAgentService companion constants (no Android import).
 */
class DeepLinkEntriesTest {

    companion object {
        const val ACTION_OPEN_CONVERSATION = "com.intent.launcher.OPEN_CONVERSATION"
        const val ACTION_OPEN_DRAWER = "com.intent.launcher.OPEN_FLOATER_DRAWER"
        const val ACTION_CAPTURE_THOUGHT = "com.intent.launcher.CAPTURE_THOUGHT"
    }

    @Test
    fun floaterDrawer_isAskTodayCaptureHistory() {
        assertEquals(
            listOf("Ask", "Today", "Capture", "History"),
            DeepLinkEntries.floaterLabels()
        )
        assertEquals(
            FloaterActionDrawer.items.map { it.label },
            DeepLinkEntries.floaterLabels()
        )
        val dests = DeepLinkEntries.FLOATER_DRAWER.map { it.destination }
        assertTrue(dests.contains("conversation"))
        assertTrue(dests.contains(FloaterActionDrawer.TODAY_ROUTE))
        assertTrue(dests.contains("thought_capture_sheet"))
        assertTrue(dests.contains(FloaterActionDrawer.HISTORY_ROUTE))
    }

    @Test
    fun companionIntents_matchFloatingAgentServiceActions() {
        val actions = DeepLinkEntries.COMPANION_INTENTS.map { it.action }.toSet()
        assertTrue(actions.contains(ACTION_OPEN_CONVERSATION))
        assertTrue(actions.contains(ACTION_OPEN_DRAWER))
        assertTrue(actions.contains(ACTION_CAPTURE_THOUGHT))
        assertEquals(3, DeepLinkEntries.COMPANION_INTENTS.size)
    }

    @Test
    fun systemHonesty_includesDndWithoutSilenceClaim() {
        val dnd = DeepLinkEntries.SYSTEM_HONESTY.first {
            it.action == QuietModeProbe.DND_SETTINGS_ACTION
        }
        assertEquals(QuietModeProbe.DND_SETTINGS_ACTION, dnd.destination)
        assertTrue(dnd.honestyNote.contains("never claims silence", ignoreCase = true))
        assertFalse(dnd.honestyNote.contains("silence applied successfully", ignoreCase = true))
    }

    @Test
    fun allShipped_entriesHaveHonestyNotes() {
        DeepLinkEntries.allShipped().forEach { entry ->
            assertTrue(entry.label.isNotBlank())
            assertTrue(entry.action.isNotBlank())
            assertTrue(entry.destination.isNotBlank())
            assertTrue(entry.honestyNote.isNotBlank())
        }
    }
}
