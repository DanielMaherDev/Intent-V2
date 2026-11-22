package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.ChessScenario
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.Provenance
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.TimeZone

/**
 * OC6 Visible controls — rating chart / badge honesty: never invent entries.
 */
class ConnectedMetricChromeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_100_000_000L
    private val tz = TimeZone.getTimeZone("UTC")

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("chrome.json"))
    }

    @Test
    fun ratingSeries_empty_whenNoEntries_doesNotInvent() {
        ChessScenario.seed(store, now)
        val points = ConnectedMetricChrome.ratingSeries(
            intentionId = ChessScenario.INTENTION_ID,
            trackerEntries = store.snapshot().trackerEntries,
            timeZone = tz
        )
        assertTrue(points.isEmpty())
        assertTrue(ConnectedMetricChrome.chartHeights(points).isEmpty())
    }

    @Test
    fun ratingSeries_usesOnlyActiveDatedSelfReports_noGoalInvention() {
        ChessScenario.seed(store, now)
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-r1",
                intentionId = ChessScenario.INTENTION_ID,
                moduleId = ConnectedMetricChrome.MODULE_RATING,
                valueText = "900",
                nowEpochMs = now + 10,
                eventAtEpochMs = now - 3L * 24 * 60 * 60 * 1000
            )
        )
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-r2",
                intentionId = ChessScenario.INTENTION_ID,
                moduleId = ConnectedMetricChrome.MODULE_RATING,
                valueText = "940",
                nowEpochMs = now + 20,
                eventAtEpochMs = now - 1L * 24 * 60 * 60 * 1000
            )
        )
        // Practice log must not appear on the rating chart.
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-practice",
                intentionId = ChessScenario.INTENTION_ID,
                moduleId = "practice_log",
                valueText = "30 min",
                nowEpochMs = now + 30
            )
        )
        val points = ConnectedMetricChrome.ratingSeries(
            intentionId = ChessScenario.INTENTION_ID,
            trackerEntries = store.snapshot().trackerEntries,
            timeZone = tz
        )
        assertEquals(2, points.size)
        assertEquals("900", points[0].valueText)
        assertEquals("940", points[1].valueText)
        assertFalse(points.any { it.valueText.contains("1200") })
        val heights = ConnectedMetricChrome.chartHeights(points)
        assertEquals(2, heights.size)
        assertTrue(heights[1] >= heights[0])
    }

    @Test
    fun spaceChrome_badgeNotSupported_refreshAndManualActions_emptyChartNote() {
        ChessScenario.seed(store, now)
        val intention = store.snapshot().intentions.first { it.id == ChessScenario.INTENTION_ID }
        val chrome = ConnectedMetricChrome.forChessIntention(
            intention = intention,
            facts = store.snapshot().facts,
            trackerEntries = store.snapshot().trackerEntries,
            nowEpochMs = now + 1,
            timeZone = tz
        )
        assertNotNull(chrome)
        assertTrue(chrome!!.badgeLabel.contains("NOT_SUPPORTED", ignoreCase = true))
        assertEquals(ConnectedMetricChrome.ACTION_REFRESH, chrome.refreshLabel)
        assertEquals(ConnectedMetricChrome.ACTION_MANUAL, chrome.manualLabel)
        assertFalse(chrome.refreshCanImport)
        assertTrue(chrome.ratingPoints.isEmpty())
        assertTrue(chrome.emptyChartNote.contains("empty", ignoreCase = true))
        assertTrue(
            ConnectedMetricChrome.refreshHonestyMessage().contains("manual", ignoreCase = true) ||
                ConnectedMetricChrome.refreshHonestyMessage().contains("invent", ignoreCase = true)
        )
    }

    @Test
    fun practiceApproaches_splitsMultiselectFact() {
        val facts = listOf(
            Fact(
                id = "f1",
                intentionId = "i1",
                text = "Playing games and Studying",
                provenance = Provenance(ProvenanceKind.SAID, now, "user"),
                key = AdaptiveBuilderEngine.KEY_PRACTICE_PREFERENCE,
                normalizedValue = "Playing games and Studying",
                status = FactStatus.ACTIVE
            )
        )
        val approaches = ConnectedMetricChrome.practiceApproaches(facts)
        assertEquals(listOf("Playing games", "Studying"), approaches)
    }

    @Test
    fun staleConnectedSource_exposesRefreshDataAndUseManualBaseline() {
        ChessScenario.seed(store, now)
        val view = StaleConnectedSource.evaluateChessRating(
            snapshot = store.snapshot(),
            intentionId = ChessScenario.INTENTION_ID,
            nowEpochMs = now + 1
        )
        assertEquals(CapabilityStatus.NOT_SUPPORTED, view.status)
        assertTrue(view.actions.contains(ConnectedMetricChrome.ACTION_REFRESH))
        assertTrue(view.actions.contains(ConnectedMetricChrome.ACTION_MANUAL))
    }

    @Test
    fun forNonChess_returnsNull() {
        assertNull(
            ConnectedMetricChrome.forChessIntention(
                intention = null,
                facts = emptyList(),
                trackerEntries = emptyList(),
                nowEpochMs = now
            )
        )
    }
}
