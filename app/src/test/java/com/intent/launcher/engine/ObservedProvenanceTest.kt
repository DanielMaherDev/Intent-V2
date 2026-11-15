package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AppUseReductionScenario
import com.intent.launcher.domain.EvidenceAccuracy
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
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

/**
 * OBSERVED provenance from UsageStats — distinct from SAID/INFERRED; never lesson completion.
 */
class ObservedProvenanceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_159_400_000L
    private val seedAt = 1_700_049_600_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveAndBeginSpanish(runId: String = "run-obs-1") {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
        SpanishScenario.beginSession(store, now, runId = runId)
    }

    @Test
    fun summary_matchesSpecCopy() {
        assertEquals(
            "Observed 2 min in Duolingo",
            ObservedProvenance.summaryText("Duolingo", 125_000L)
        )
    }

    @Test
    fun record_withPermission_storesObserved_notSaidOrInferred() {
        approveAndBeginSpanish()
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(SessionDestinations.SPANISH_PACKAGE to 125_000L)
        )
        val registry = UsageStatsProbe.liveRegistry(access)
        assertTrue(registry.canObserveAppUsage())
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))

        val dest = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID)!!
        val obs = UsageStatsProbe.observeSessionForeground(
            access = access,
            destination = dest,
            sessionStartedAtEpochMs = now,
            nowEpochMs = now + 300_000L,
            registry = registry
        )
        val recorded = ObservedProvenance.recordIfObservable(
            store = store,
            observation = obs,
            intentionId = SpanishScenario.INTENTION_ID,
            sessionRunId = "run-obs-1",
            nowEpochMs = now + 300_000L,
            registry = registry
        )
        assertNotNull(recorded)
        assertEquals(ProvenanceKind.OBSERVED, recorded!!.provenance.kind)
        assertEquals("usage_stats", recorded.provenance.sourceLabel)
        assertEquals(EvidenceAccuracy.ACTIVE, recorded.accuracy)
        assertTrue(recorded.summaryText.contains("Observed"))
        assertTrue(recorded.summaryText.contains("Duolingo"))
        assertEquals(
            SessionRunStatus.IN_PROGRESS,
            store.snapshot().sessionRuns.single().status
        )
        assertFalse(
            store.snapshot().facts.any { it.provenance.kind == ProvenanceKind.OBSERVED }
        )
    }

    @Test
    fun record_withoutPermission_doesNotStore_andDoesNotComplete() {
        approveAndBeginSpanish()
        val access = FakeUsageStatsAccess(granted = false)
        val registry = UsageStatsProbe.liveRegistry(access)
        assertFalse(registry.canObserveAppUsage())
        val dest = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID)!!
        val obs = UsageStatsProbe.observeSessionForeground(
            access = access,
            destination = dest,
            sessionStartedAtEpochMs = now,
            nowEpochMs = now + 60_000L,
            registry = registry
        )
        val recorded = ObservedProvenance.recordIfObservable(
            store = store,
            observation = obs,
            intentionId = SpanishScenario.INTENTION_ID,
            sessionRunId = "run-obs-1",
            nowEpochMs = now + 60_000L,
            registry = registry
        )
        assertNull(recorded)
        assertTrue(store.snapshot().evidence.isEmpty())
        assertEquals(
            SessionRunStatus.IN_PROGRESS,
            store.snapshot().sessionRuns.single().status
        )
    }

    @Test
    fun completePath_recordsObservedThenUserCompletes_neverAutoCompleteFromUsageAlone() {
        approveAndBeginSpanish(runId = "run-obs-complete")
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(SessionDestinations.SPANISH_PACKAGE to 90_000L)
        )
        val registry = UsageStatsProbe.liveRegistry(access)
        val dest = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID)!!
        val obs = UsageStatsProbe.observeSessionForeground(
            access = access,
            destination = dest,
            sessionStartedAtEpochMs = now,
            nowEpochMs = now + 120_000L,
            registry = registry
        )
        ObservedProvenance.recordIfObservable(
            store = store,
            observation = obs,
            intentionId = SpanishScenario.INTENTION_ID,
            sessionRunId = "run-obs-complete",
            nowEpochMs = now + 120_000L,
            registry = registry
        )
        // Observation alone leaves run in progress
        assertEquals(
            SessionRunStatus.IN_PROGRESS,
            store.snapshot().sessionRuns.single().status
        )
        store.apply(
            FlowCommand.CompleteSessionRun(
                runId = "run-obs-complete",
                nowEpochMs = now + 130_000L
            )
        )
        val snap = store.snapshot()
        assertEquals(SessionRunStatus.COMPLETED, snap.sessionRuns.single().status)
        val evidence = snap.evidence.single()
        assertEquals(ProvenanceKind.OBSERVED, evidence.provenance.kind)
        assertTrue(ObservedProvenance.checkoutNote(evidence).contains("not lesson completion"))
    }

    @Test
    fun dismiss_marksInaccurate_withoutEditingChat() {
        approveAndBeginSpanish()
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(SessionDestinations.SPANISH_PACKAGE to 60_000L)
        )
        val registry = UsageStatsProbe.liveRegistry(access)
        val dest = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID)!!
        val obs = UsageStatsProbe.observeSessionForeground(
            access = access,
            destination = dest,
            sessionStartedAtEpochMs = now,
            nowEpochMs = now + 90_000L,
            registry = registry
        )
        val recorded = ObservedProvenance.recordIfObservable(
            store = store,
            observation = obs,
            intentionId = SpanishScenario.INTENTION_ID,
            sessionRunId = "run-obs-1",
            nowEpochMs = now + 90_000L,
            registry = registry
        )!!
        val turnsBefore = store.snapshot().turns.size
        store.apply(
            FlowCommand.DismissEvidence(
                evidenceId = recorded.id,
                nowEpochMs = now + 100_000L
            )
        )
        val snap = store.snapshot()
        assertEquals(EvidenceAccuracy.DISMISSED, snap.evidence.single().accuracy)
        assertEquals(turnsBefore, snap.turns.size)
        assertTrue(
            ObservedProvenance.activeForIntention(snap, SpanishScenario.INTENTION_ID).isEmpty()
        )
    }

    @Test
    fun liveRegistry_observeAppUsageTracksPermission_lessonStillUnsupported() {
        val denied = UsageStatsProbe.liveRegistry(FakeUsageStatsAccess(granted = false))
        assertTrue(denied.needsPermission(CapabilityRegistry.ID_OBSERVE_APP_USAGE))
        assertFalse(denied.canObserveAppUsage())
        assertTrue(denied.isUnsupported(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))

        val granted = UsageStatsProbe.liveRegistry(FakeUsageStatsAccess(granted = true))
        assertTrue(granted.canObserveAppUsage())
        assertTrue(granted.canReadUsageStats())
        assertTrue(granted.isUnsupported(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
    }

    @Test
    fun persist_roundTrip_preservesObservedEvidence() {
        approveAndBeginSpanish()
        store.apply(
            FlowCommand.RecordObservedEvidence(
                evidenceId = "obs-persist",
                intentionId = SpanishScenario.INTENTION_ID,
                sessionRunId = "run-obs-1",
                packageName = SessionDestinations.SPANISH_PACKAGE,
                appLabel = SessionDestinations.SPANISH_LABEL,
                foregroundMs = 180_000L,
                summaryText = ObservedProvenance.summaryText("Duolingo", 180_000L),
                nowEpochMs = now + 1
            )
        )
        val encoded = store.encode(store.snapshot())
        val wireFile = tmp.newFile("wire.json")
        wireFile.writeText(encoded)
        val reopened = IntentionFlowStore(wireFile)
        val evidence = reopened.snapshot().evidence.single()
        assertEquals(ProvenanceKind.OBSERVED, evidence.provenance.kind)
        assertEquals(SCHEMA_VERSION, reopened.snapshot().schemaVersion)
        assertEquals("Observed 3 min in Duolingo", evidence.summaryText)
    }

    @Test
    fun appUse_instagramObservation_includesShortsLimitation_neverAutoCompletes() {
        AppUseReductionScenario.seed(store, seedAt)
        AppUseReductionScenario.onChip(store, "I want under 45 minutes a day", seedAt + 1)
        AppUseReductionScenario.approve(store, seedAt + 2)
        AppUseReductionScenario.beginSession(store, now, runId = "run-app-use-obs")

        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(
                SessionDestinations.INSTAGRAM_PACKAGE to 50 * 60_000L
            )
        )
        val registry = UsageStatsProbe.liveRegistry(access)
        val dest = SessionDestinations.forShape("app_use", AppUseReductionScenario.INTENTION_ID)!!
        assertEquals(SessionDestinations.INSTAGRAM_PACKAGE, dest.packageName)

        val obs = UsageStatsProbe.observeSessionForeground(
            access = access,
            destination = dest,
            sessionStartedAtEpochMs = now,
            nowEpochMs = now + 3_600_000L,
            registry = registry
        )
        assertTrue(obs.note.contains("Shorts", ignoreCase = true) ||
            obs.note.contains("Reels", ignoreCase = true))
        assertTrue(ObservedProvenance.meetsOrExceedsTargetMinutes(
            obs.foregroundMs!!,
            AppUseReductionScenario.DAILY_TARGET_MINUTES
        ))

        val recorded = ObservedProvenance.recordIfObservable(
            store = store,
            observation = obs,
            intentionId = AppUseReductionScenario.INTENTION_ID,
            sessionRunId = "run-app-use-obs",
            nowEpochMs = now + 3_600_000L,
            registry = registry
        )
        assertNotNull(recorded)
        assertEquals(ProvenanceKind.OBSERVED, recorded!!.provenance.kind)
        assertTrue(ObservedProvenance.sessionCardNote(recorded).contains("Shorts") ||
            ObservedProvenance.sessionCardNote(recorded).contains("Reels"))
        assertTrue(ObservedProvenance.checkoutNote(recorded).contains("Shorts") ||
            ObservedProvenance.checkoutNote(recorded).contains("Reels"))
        assertEquals(
            SessionRunStatus.IN_PROGRESS,
            store.snapshot().sessionRuns.single().status
        )
    }
}
