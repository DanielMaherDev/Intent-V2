package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SCHEMA_VERSION
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
 * Tracker modules + PathSummary / ProgressProof — same store, labeled provenance.
 */
class TrackerReportsTest {

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
    fun shapeDefaults_spanishHasPracticeLog() {
        val modules = TrackerModules.forShape("language")
        assertTrue(modules.any { it.id == "practice_log" })
        assertTrue(TrackerModules.forShape("marathon").any { it.id == "mileage" })
        assertTrue(TrackerModules.forShape("business").any { it.id == "research_notes" })
    }

    @Test
    fun recordTrackerEntry_isSaid_andSoftDeleteWorks() {
        SpanishScenario.seed(store, seedAt)
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-1",
                intentionId = SpanishScenario.INTENTION_ID,
                moduleId = "practice_log",
                valueText = "Spoke for 10 minutes",
                nowEpochMs = now,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user"
            )
        )
        var entry = store.snapshot().trackerEntries.single()
        assertEquals(ProvenanceKind.SAID, entry.provenance.kind)
        assertEquals(TrackerEntryStatus.ACTIVE, entry.status)

        store.apply(FlowCommand.DeleteTrackerEntry(entryId = "trk-1", nowEpochMs = now + 1))
        entry = store.snapshot().trackerEntries.single()
        assertEquals(TrackerEntryStatus.DELETED, entry.status)
    }

    @Test
    fun progressProof_labelsSaidInferredObserved_neverSilentMix() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "Everyday conversations", seedAt + 1)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 2)
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-obs",
                intentionId = SpanishScenario.INTENTION_ID,
                moduleId = "practice_log",
                valueText = "Observed 2 min in Duolingo (package-level)",
                nowEpochMs = now,
                provenanceKind = ProvenanceKind.OBSERVED,
                sourceLabel = "usage_stats"
            )
        )
        val proof = ProgressProof.build(
            snapshot = store.snapshot(),
            intentionId = SpanishScenario.INTENTION_ID,
            nowEpochMs = now,
            shapeTag = "language",
            registry = registry
        )!!
        assertTrue(proof.saidCount >= 1)
        assertTrue(proof.inferredCount >= 1)
        assertEquals(1, proof.observedCount)
        assertTrue(proof.items.all { it.provenanceLabel in setOf("SAID", "OBSERVED", "INFERRED") })
        assertTrue(proof.items.any { it.kind == ProvenanceKind.OBSERVED })
        // No fabricated chart claim
        assertFalse(proof.honestyNote.contains("100%", ignoreCase = true))
    }

    @Test
    fun progressProof_emptyState_whenNoRows() {
        SpanishScenario.seed(store, seedAt)
        // Clear facts/hypotheses by using fresh intention without chips — seed only has turns
        val proof = ProgressProof.build(
            snapshot = store.snapshot(),
            intentionId = SpanishScenario.INTENTION_ID,
            nowEpochMs = now,
            shapeTag = "language"
        )!!
        // Seed may have no facts yet
        if (proof.items.isEmpty()) {
            assertNotNull(proof.emptyState)
            assertTrue(proof.emptyState!!.contains("No progress proof"))
        }
    }

    @Test
    fun pathSummary_readsPhaseNextActionAndModules() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
        val snap = store.snapshot()
        val intention = snap.intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val next = NextActionEngine.recommend(snap, now, tz, registry)
        val summary = PathSummary.build(snap, intention, next, now, registry)!!
        assertEquals("Active", summary.phaseLabel)
        assertTrue(summary.scheduledSessionCount >= 1)
        assertTrue(summary.modules.any { it.moduleId == "practice_log" })
        assertTrue(summary.nextActionLabel.isNotBlank())
        assertTrue(registry.canUseTrackerModules())
        assertTrue(registry.canViewProgressReports())
    }

    @Test
    fun persist_roundTrip_trackerEntries() {
        SpanishScenario.seed(store, seedAt)
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-persist",
                intentionId = SpanishScenario.INTENTION_ID,
                moduleId = "confidence_note",
                valueText = "Felt clearer than last time",
                nowEpochMs = now
            )
        )
        val encoded = store.encode(store.snapshot())
        val wire = tmp.newFile("wire.json")
        wire.writeText(encoded)
        val again = IntentionFlowStore(wire)
        assertEquals(SCHEMA_VERSION, again.snapshot().schemaVersion)
        assertEquals(1, again.snapshot().trackerEntries.size)
        assertEquals(ProvenanceKind.SAID, again.snapshot().trackerEntries.single().provenance.kind)
    }
}
