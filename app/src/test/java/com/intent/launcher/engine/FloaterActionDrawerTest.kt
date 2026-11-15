package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Floater Ask / Today / Capture drawer — same agent; no second bot.
 */
class FloaterActionDrawerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val seedAt = 1_700_049_600_000L
    private val dueNow = 1_700_159_400_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun overlayReg(): CapabilityRegistry {
        val entries = CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
            if (entry.id == CapabilityRegistry.ID_FLOATING_OVERLAY) {
                entry.copy(status = CapabilityStatus.AVAILABLE, limitNote = "ok")
            } else {
                entry
            }
        }
        return CapabilityRegistry(entries)
    }

    private fun approveSpanish() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
    }

    @Test
    fun items_areAskTodayCaptureHistory() {
        val actions = FloaterActionDrawer.items.map { it.action }
        assertEquals(
            listOf(
                FloaterDrawerAction.ASK,
                FloaterDrawerAction.TODAY,
                FloaterDrawerAction.CAPTURE,
                FloaterDrawerAction.HISTORY
            ),
            actions
        )
        assertTrue(FloaterActionDrawer.items.all { it.label.isNotBlank() })
        assertEquals(FloaterActionDrawer.HISTORY_ROUTE, "history")
    }

    @Test
    fun openAsk_usesSameConversationPath_withContextChip() {
        approveSpanish()
        val reg = overlayReg()
        val next = NextActionEngine.recommend(store.snapshot(), dueNow, tz, reg)
        val ask = FloaterActionDrawer.openAsk(store, next, dueNow + 1, reg)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, ask.navigateTo)
        assertEquals(SpanishScenario.INTENTION_ID, ask.intentionId)
        assertNotNull(ask.contextChip)
        assertTrue(ask.contextChip!!.startsWith("About:"))
        assertFalse(ask.contextChip!!.contains("LLM", ignoreCase = true))
    }

    @Test
    fun openToday_opensDedicatedTodayRoute() {
        val next = NextAction(
            kind = NextActionKind.BEGIN,
            label = "Begin Spanish session",
            detail = "due",
            intentionId = "i1"
        )
        val today = FloaterActionDrawer.openToday(next)
        assertEquals(FloaterActionDrawer.TODAY_ROUTE, today.navigateTo)
        assertTrue(today.focusNote.contains("Begin Spanish session"))
        assertTrue(today.focusNote.contains("Planned", ignoreCase = true))
        assertTrue(today.focusNote.contains("not chat prose", ignoreCase = true))
    }
}
