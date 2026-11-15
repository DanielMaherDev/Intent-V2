package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.DestinationApp
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
 * Stage 6 — UsageStats live registry + package-level observation honesty.
 */
class UsageStatsProbeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_159_400_000L
    private val seedAt = 1_700_049_600_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveAndBeginSpanish() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
        SpanishScenario.beginSession(store, now, runId = "run-usage-1")
    }

    @Test
    fun liveRegistry_permissionNeededWhenDenied() {
        val access = FakeUsageStatsAccess(granted = false)
        val registry = UsageStatsProbe.liveRegistry(access)
        assertTrue(registry.needsPermission(CapabilityRegistry.ID_USAGE_STATS))
        assertFalse(registry.canReadUsageStats())
        assertEquals(
            CapabilityStatus.PERMISSION_NEEDED,
            registry.get(CapabilityRegistry.ID_USAGE_STATS)!!.status
        )
    }

    @Test
    fun liveRegistry_availableWhenGranted_withHonestyNote() {
        val access = FakeUsageStatsAccess(granted = true)
        val registry = UsageStatsProbe.liveRegistry(access)
        assertTrue(registry.canReadUsageStats())
        assertTrue(
            registry.get(CapabilityRegistry.ID_USAGE_STATS)!!.limitNote
                .contains("not lesson", ignoreCase = true)
        )
        assertTrue(registry.canObserveAppUsage())
        // Lesson completion stays unsupported
        assertTrue(registry.isUnsupported(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION))
    }

    @Test
    fun observe_withoutPermission_offersSettings_andDoesNotComplete() {
        approveAndBeginSpanish()
        val access = FakeUsageStatsAccess(granted = false)
        val dest = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID)
        val obs = UsageStatsProbe.observeSessionForeground(
            access = access,
            destination = dest,
            sessionStartedAtEpochMs = now,
            nowEpochMs = now + 60_000L
        )
        assertFalse(obs.permissionGranted)
        assertTrue(obs.canOpenSettings)
        assertNull(obs.foregroundMs)
        assertTrue(obs.note.contains("not lesson completion", ignoreCase = true))
        assertEquals(
            SessionRunStatus.IN_PROGRESS,
            store.snapshot().sessionRuns.single().status
        )
    }

    @Test
    fun observe_withPermission_reportsPackageForeground_notLessonCompletion() {
        val dest = DestinationApp(
            packageName = SessionDestinations.SPANISH_PACKAGE,
            label = SessionDestinations.SPANISH_LABEL
        )
        val access = FakeUsageStatsAccess(
            granted = true,
            foregroundByPackage = mapOf(dest.packageName to 125_000L)
        )
        val obs = UsageStatsProbe.observeSessionForeground(
            access = access,
            destination = dest,
            sessionStartedAtEpochMs = now,
            nowEpochMs = now + 300_000L
        )
        assertTrue(obs.permissionGranted)
        assertFalse(obs.canOpenSettings)
        assertEquals(125_000L, obs.foregroundMs)
        assertTrue(obs.note.contains("package-level", ignoreCase = true))
        assertTrue(obs.note.contains("not lesson completion", ignoreCase = true))
        assertTrue(obs.note.contains("2:05") || obs.note.contains("Duolingo"))
    }

    @Test
    fun observe_noDestination_stillHonest() {
        val access = FakeUsageStatsAccess(granted = true)
        val obs = UsageStatsProbe.observeSessionForeground(
            access = access,
            destination = null,
            sessionStartedAtEpochMs = now,
            nowEpochMs = now + 1_000L
        )
        assertNotNull(obs.note)
        assertNull(obs.foregroundMs)
    }
}
