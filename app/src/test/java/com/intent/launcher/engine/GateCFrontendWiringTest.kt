package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrackerModules
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
 * Gate C frontend wiring contracts — Home MIDDAY CTA labels and tracker
 * CorrectTrackerEntry honesty (UI binds these; no Compose runner here).
 */
class GateCFrontendWiringTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default
    private val seedAt = 1_700_049_600_000L
    private val thu0900 = 1_700_125_200_000L
    private val midAfternoon = thu0900 + 5 * 3_600_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveSpanishWithMorningCheckIn() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1, registry)
        SpanishScenario.approve(store, seedAt + 2)
        val localDate = DailyCycle.localDateString(thu0900, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Focus: Speak Spanish",
                nowEpochMs = thu0900,
                occurrenceId = "occ-morning-ui",
                dailyContextId = "dctx-morning-ui"
            )
        )
    }

    @Test
    fun middayNextAction_exposesKeepPlanAndDeferLabels_forHomeCta() {
        approveSpanishWithMorningCheckIn()
        val next = NextActionEngine.recommend(store.snapshot(), midAfternoon, tz, registry)
        assertEquals(NextActionKind.MIDDAY, next.kind)
        assertEquals("Keep plan", next.confirmLabel)
        assertEquals("Defer later", next.adjustLabel)
        assertEquals("Defer later", next.deferLabel)
        assertEquals("Midday check", next.label)
        assertNotNull(next.discussLabel)

        val spoken = TalkBackLabels.attentionStripContainer(
            nextAction = next,
            smallLabel = "Midday check",
            title = next.label,
            support = next.reason
        )
        assertTrue(spoken.contains("Midday check", ignoreCase = true))
        assertTrue(spoken.startsWith("Next action."))
    }

    @Test
    fun recordMiddayKeepPlan_clearsMiddayRecommendation() {
        approveSpanishWithMorningCheckIn()
        val localDate = DailyCycle.localDateString(midAfternoon, tz)
        val before = NextActionEngine.recommend(store.snapshot(), midAfternoon, tz, registry)
        assertEquals(NextActionKind.MIDDAY, before.kind)

        store.apply(
            FlowCommand.RecordMiddayCheck(
                occurrenceId = "occ-midday-ui",
                contextId = "dctx-midday-ui",
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                summary = before.detail.ifBlank { "Midday check — plan held" },
                nowEpochMs = midAfternoon
            )
        )
        val after = NextActionEngine.recommend(store.snapshot(), midAfternoon + 60_000L, tz, registry)
        assertFalse(after.kind == NextActionKind.MIDDAY)
        assertTrue(
            store.snapshot().occurrences.any {
                it.id == "occ-midday-ui" && it.kind == "midday_check"
            }
        )
    }

    @Test
    fun correctTrackerEntry_uiSourceLabel_isUserCorrection_andKeepsSaidKind() {
        approveSpanishWithMorningCheckIn()
        val modules = TrackerModules.forShape("language")
        assertTrue(modules.isNotEmpty())
        val module = modules.first()
        val original = module.suggestedValues.first()
        val corrected = module.suggestedValues.first { it != original }

        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-ui-1",
                intentionId = SpanishScenario.INTENTION_ID,
                moduleId = module.id,
                valueText = original,
                nowEpochMs = midAfternoon,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user"
            )
        )
        store.apply(
            FlowCommand.CorrectTrackerEntry(
                priorEntryId = "trk-ui-1",
                newEntryId = "trk-ui-1-corr",
                valueText = corrected,
                nowEpochMs = midAfternoon + 1,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user_correction"
            )
        )

        val snap = store.snapshot()
        val prior = snap.trackerEntries.first { it.id == "trk-ui-1" }
        val neu = snap.trackerEntries.first { it.id == "trk-ui-1-corr" }
        assertEquals(TrackerEntryStatus.CORRECTED, prior.status)
        assertEquals(TrackerEntryStatus.ACTIVE, neu.status)
        assertEquals(ProvenanceKind.SAID, neu.provenance.kind)
        assertEquals("user_correction", neu.provenance.sourceLabel)
        assertEquals(corrected, neu.valueText)
        // ACTIVE-only listing for TrackerScreen
        val active = snap.trackerEntries.filter {
            it.intentionId == SpanishScenario.INTENTION_ID &&
                it.status == TrackerEntryStatus.ACTIVE
        }
        assertEquals(1, active.size)
        assertEquals("trk-ui-1-corr", active.single().id)
    }
}
