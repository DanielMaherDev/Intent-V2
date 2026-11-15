package com.intent.launcher.domain

import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Intention drawer card layout chrome (OS-139 / v0.0.76).
 */
class DrawerCardsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun defaultLayout_elevatesActiveAndProposalPending() {
        SpanishScenario.seed(store, now)
        MarathonScenario.seed(store, now + 1) // sets active → marathon

        val snap = store.snapshot()
        assertEquals(MarathonScenario.INTENTION_ID, snap.activeIntentionId)
        val spanish = snap.intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val marathon = snap.intentions.first { it.id == MarathonScenario.INTENTION_ID }

        val marathonLayout = DrawerCards.layoutFor(snap, marathon)
        assertEquals(2, marathonLayout.spanColumns)
        assertTrue(marathonLayout.priorityBorder)
        assertEquals("marathon", marathonLayout.imageKey)

        val spanishLayout = DrawerCards.layoutFor(snap, spanish)
        assertEquals(1, spanishLayout.spanColumns)
        assertFalse(spanishLayout.priorityBorder)
        assertEquals("language", spanishLayout.imageKey)
    }

    @Test
    fun updateDrawerCardLayout_persistsSpanAndPriority() {
        SpanishScenario.seed(store, now)
        MarathonScenario.seed(store, now + 1)

        store.apply(
            FlowCommand.UpdateDrawerCardLayout(
                intentionId = MarathonScenario.INTENTION_ID,
                spanColumns = 2,
                priorityBorder = true,
                imageKey = "marathon",
                nowEpochMs = now + 10
            )
        )
        val layout = DrawerCards.layoutFor(
            store.snapshot(),
            store.snapshot().intentions.first { it.id == MarathonScenario.INTENTION_ID }
        )
        assertEquals(2, layout.spanColumns)
        assertTrue(layout.priorityBorder)
        assertEquals("marathon", layout.imageKey)

        val restarted = IntentionFlowStore(tmp.root.resolve("flow.json"))
        assertEquals(com.intent.launcher.domain.SCHEMA_VERSION, restarted.snapshot().schemaVersion)
        val again = DrawerCards.layoutFor(
            restarted.snapshot(),
            restarted.snapshot().intentions.first { it.id == MarathonScenario.INTENTION_ID }
        )
        assertEquals(2, again.spanColumns)
        assertTrue(again.priorityBorder)
    }

    @Test
    fun normalizeSpan_clampsToOneOrTwo() {
        assertEquals(1, DrawerCards.normalizeSpan(0))
        assertEquals(1, DrawerCards.normalizeSpan(1))
        assertEquals(2, DrawerCards.normalizeSpan(2))
        assertEquals(2, DrawerCards.normalizeSpan(9))
    }

    @Test
    fun updateUnknownIntention_isNoOp() {
        SpanishScenario.seed(store, now)
        store.apply(
            FlowCommand.UpdateDrawerCardLayout(
                intentionId = "missing",
                spanColumns = 2,
                priorityBorder = true,
                nowEpochMs = now + 1
            )
        )
        assertTrue(store.snapshot().drawerCardLayouts.isEmpty())
    }
}
