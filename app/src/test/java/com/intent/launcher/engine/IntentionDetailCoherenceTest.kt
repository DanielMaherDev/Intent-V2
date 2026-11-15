package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
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
 * OS-142 — intention detail coherence surfaces next action, paths, plan, track,
 * reports, and resources from the existing store (no invented partner data).
 */
class IntentionDetailCoherenceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_159_400_000L
    private val seedAt = 1_700_049_600_000L
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun exploring_surfacesSixSections_withoutInventedPlan() {
        SpanishScenario.seed(store, seedAt)
        val snap = store.snapshot()
        val intention = snap.intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val next = NextActionEngine.recommend(snap, now, tz, registry)
        val view = IntentionDetailCoherence.build(
            snapshot = snap,
            intention = intention,
            nextAction = next,
            facts = snap.facts.filter { it.intentionId == intention.id },
            hypotheses = snap.hypotheses.filter { it.intentionId == intention.id },
            sessions = snap.sessions.filter { it.intentionId == intention.id },
            heldApps = emptyList(),
            connectionUsages = emptyList(),
            nowEpochMs = now,
            registry = registry
        )!!

        assertTrue(view.nextActionLabel.isNotBlank())
        assertEquals("Active paths", view.activePathsTitle)
        assertEquals("Track", view.trackTitle)
        assertEquals("Reports", view.reportsTitle)
        assertEquals("Resources", view.resourcesTitle)
        assertTrue(view.planSteps.isEmpty())
        assertNotNull(view.planEmptyNote)
        assertTrue(view.planEmptyNote!!.contains("invent", ignoreCase = true) ||
            view.planEmptyNote!!.contains("open", ignoreCase = true) ||
            view.planEmptyNote!!.contains("plan", ignoreCase = true))
        assertTrue(view.trackLines.any { it.moduleId == "practice_log" })
        assertTrue(view.canOpenTracker)
        assertTrue(view.canOpenReports)
        assertFalse(view.reportsHonestyNote.contains("100%", ignoreCase = true))
        assertTrue(
            view.resourcesEmptyNote != null || view.resourceLines.isNotEmpty()
        )
    }

    @Test
    fun active_includesScheduledPathAndPlanSteps() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
        val snap = store.snapshot()
        val intention = snap.intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val next = NextActionEngine.recommend(snap, now, tz, registry)
        val view = IntentionDetailCoherence.build(
            snapshot = snap,
            intention = intention,
            nextAction = next,
            facts = snap.facts.filter { it.intentionId == intention.id },
            hypotheses = snap.hypotheses.filter { it.intentionId == intention.id },
            sessions = snap.sessions.filter { it.intentionId == intention.id },
            heldApps = emptyList(),
            connectionUsages = emptyList(),
            nowEpochMs = now,
            registry = registry
        )!!

        assertTrue(view.activePathLines.isNotEmpty())
        assertTrue(view.activePathLines.any { it.detail.contains("Scheduled") || it.title.isNotBlank() })
        assertTrue(view.planSteps.isNotEmpty())
        assertTrue(view.reportsSummaryLine.contains("Active") || view.reportsSummaryLine.contains("scheduled"))
        assertTrue(view.reportsProofLine.contains("SAID"))
        assertNotNull(view.resourcesHonestyNote)
        assertFalse(view.resourcesHonestyNote.contains("completed the lesson", ignoreCase = true))
    }

    @Test
    fun nullIntention_returnsNull() {
        SpanishScenario.seed(store, seedAt)
        val snap = store.snapshot()
        val next = NextActionEngine.recommend(snap, now, tz, registry)
        val view = IntentionDetailCoherence.build(
            snapshot = snap,
            intention = null,
            nextAction = next,
            facts = emptyList(),
            hypotheses = emptyList(),
            sessions = emptyList(),
            heldApps = emptyList(),
            connectionUsages = emptyList(),
            nowEpochMs = now,
            registry = registry
        )
        assertEquals(null, view)
    }
}
