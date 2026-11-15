package com.intent.launcher.domain

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * OS-135 — HomeAttention honesty: omit empty slots; no invented deadlines / partner data.
 */
class HomeAttentionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default

    private val seedAt = 1_700_049_600_000L // Wed 15 Nov 2023 12:00 UTC
    private val wedNoon = seedAt
    private val thu1200 = 1_700_136_000_000L
    private val thu1830 = 1_700_159_400_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveSpanishSession() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1, registry)
        SpanishScenario.approve(store, seedAt + 2)
    }

    @Test
    fun exploring_omitsDueDeadlineAgent_keepsRecommendationOnly() {
        SpanishScenario.seed(store, seedAt)
        val snap = store.snapshot()
        val next = NextActionEngine.recommend(snap, wedNoon, tz, registry)
        assertEquals(NextActionKind.CONTINUE_EXPLORING, next.kind)
        val model = HomeAttention.build(snap, next, wedNoon, tz)
        assertNull(model.nextDeadlineLine)
        assertNull(model.dueWorkSummary)
        assertNull(model.agentAttention)
    }

    @Test
    fun futureDay_showsNextDeadline_andAgentCue() {
        approveSpanishSession()
        val snap = store.snapshot()
        val next = NextActionEngine.recommend(snap, wedNoon, tz, registry)
        assertEquals(NextActionKind.DISCUSS, next.kind)
        val model = HomeAttention.build(snap, next, wedNoon, tz)
        assertNotNull(model.nextDeadlineLine)
        assertTrue(model.nextDeadlineLine!!.contains("tomorrow", ignoreCase = true) ||
            model.nextDeadlineLine!!.contains("Thursday", ignoreCase = true))
        assertNotNull(model.agentAttention)
        assertEquals("Want to adjust the plan?", model.agentAttention!!.title)
        assertNull(model.dueWorkSummary)
    }

    @Test
    fun dueNow_omitsDuplicateDeadline_keepsAgentCue() {
        approveSpanishSession()
        val snap = store.snapshot()
        val next = NextActionEngine.recommend(snap, thu1830, tz, registry)
        assertEquals(NextActionKind.BEGIN, next.kind)
        val model = HomeAttention.build(snap, next, thu1830, tz)
        assertNull(model.nextDeadlineLine)
        assertNotNull(model.agentAttention)
        assertEquals("Want to adjust the plan?", model.agentAttention!!.title)
    }

    @Test
    fun laterToday_omitsDeadlineOwnedByStrip() {
        approveSpanishSession()
        val snap = store.snapshot()
        val next = NextActionEngine.recommend(snap, thu1200, tz, registry)
        assertEquals(NextActionKind.BEGIN, next.kind)
        val model = HomeAttention.build(snap, next, thu1200, tz)
        assertNull(model.nextDeadlineLine)
        assertNotNull(model.agentAttention)
    }

    @Test
    fun dueWork_countsOtherExploringIntention_notFocus() {
        approveSpanishSession()
        MarathonScenario.seed(store, seedAt + 50)
        store.apply(
            FlowCommand.SetActiveIntention(
                intentionId = SpanishScenario.INTENTION_ID,
                nowEpochMs = seedAt + 60
            )
        )
        val snap = store.snapshot()
        assertTrue(snap.intentions.size >= 2)
        val next = NextActionEngine.recommend(snap, thu1830, tz, registry)
        assertEquals(SpanishScenario.INTENTION_ID, next.intentionId)
        val model = HomeAttention.build(snap, next, thu1830, tz)
        assertNotNull(model.dueWorkSummary)
        assertTrue(model.dueWorkSummary!!.contains("1 other intention"))
    }

    @Test
    fun agentAttention_nullWhenNoDiscussLabel() {
        val next = NextAction(
            kind = NextActionKind.NONE,
            label = "Where would you like to begin?",
            detail = "Start an intention when you are ready.",
            reason = "No active intention yet — nothing is scheduled.",
            intentionId = null
        )
        assertNull(HomeAttention.agentAttention(next))
    }

    @Test
    fun agentAttention_reviewProposalCue() {
        val next = NextAction(
            kind = NextActionKind.REVIEW_PROPOSAL,
            label = "Review weekly session",
            detail = "Thursdays",
            reason = "Waiting for approval",
            intentionId = "i1",
            proposalId = "p1",
            discussLabel = "Discuss"
        )
        val cue = HomeAttention.agentAttention(next)
        assertNotNull(cue)
        assertEquals("Talk through this proposal?", cue!!.title)
    }
}
