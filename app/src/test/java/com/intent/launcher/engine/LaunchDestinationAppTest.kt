package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.BusinessScenario
import com.intent.launcher.domain.JobOfferScenario
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
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
 * Destination-app launch command path + registry honesty.
 * Opening an app must never imply lesson completion.
 */
class LaunchDestinationAppTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default
    private val seedAt = 1_700_049_600_000L
    private val dueNow = 1_700_159_400_000L // Thu 16 Nov 2023 18:30 UTC

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveSpanish() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
    }

    private fun lockedLaunchRegistry(note: String = "Launch locked for tests."): CapabilityRegistry =
        CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
                if (entry.id == CapabilityRegistry.ID_LAUNCH_INSTALLED_APP) {
                    entry.copy(status = CapabilityStatus.NOT_SUPPORTED, limitNote = note)
                } else {
                    entry
                }
            }
        )

    @Test
    fun spanishShape_resolvesDuolingoDestination() {
        approveSpanish()
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val dest = SessionDestinations.forIntention(intention)
        assertNotNull(dest)
        assertEquals(SessionDestinations.SPANISH_PACKAGE, dest!!.packageName)
        assertEquals(SessionDestinations.SPANISH_LABEL, dest.label)
    }

    @Test
    fun marathonAndBusiness_haveNoDestinationPackage() {
        MarathonScenario.seed(store, seedAt)
        val marathon = store.snapshot().intentions.first { it.id == MarathonScenario.INTENTION_ID }
        assertNull(SessionDestinations.forIntention(marathon))

        BusinessScenario.seed(store, seedAt + 1)
        val business = store.snapshot().intentions.first { it.id == BusinessScenario.INTENTION_ID }
        assertNull(SessionDestinations.forIntention(business))

        JobOfferScenario.seed(store, seedAt + 2)
        val decision = store.snapshot().intentions.first { it.id == JobOfferScenario.INTENTION_ID }
        assertNull(SessionDestinations.forIntention(decision))
    }

    @Test
    fun beginAndActive_surfaceOpenDestinationWhenLaunchAvailable() {
        approveSpanish()
        val begin = NextActionEngine.recommend(store.snapshot(), dueNow, tz, registry)
        assertEquals(NextActionKind.BEGIN, begin.kind)
        assertEquals(SessionDestinations.SPANISH_PACKAGE, begin.destinationPackageName)
        assertEquals("Open Duolingo", begin.openDestinationLabel)

        SpanishScenario.beginSession(store, dueNow, runId = "run-launch-active")
        val active = NextActionEngine.recommend(store.snapshot(), dueNow + 1_000L, tz, registry)
        assertEquals(NextActionKind.ACTIVE_SESSION, active.kind)
        assertEquals("Open Duolingo", active.openDestinationLabel)
        assertEquals(SessionDestinations.SPANISH_LABEL, active.destinationAppLabel)
        assertTrue(active.reason.contains("opening an app", ignoreCase = true))
    }

    @Test
    fun launch_succeedsWhenInstalled_andDoesNotCompleteSession() {
        approveSpanish()
        SpanishScenario.beginSession(store, dueNow, runId = "run-launch-ok")
        val fake = FakeInstalledAppLauncher(installed = setOf(SessionDestinations.SPANISH_PACKAGE))
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val next = NextActionEngine.recommend(store.snapshot(), dueNow + 500L, tz, registry)

        val result = LaunchDestinationApp.attempt(
            destination = LaunchDestinationApp.resolve(intention, next),
            launcher = fake,
            registry = registry
        )
        assertEquals(LaunchDestinationApp.Outcome.LAUNCHED, result.outcome)
        assertTrue(result.opened)
        assertEquals(listOf(SessionDestinations.SPANISH_PACKAGE), fake.launchedPackages)
        assertTrue(result.note.contains("not lesson completion", ignoreCase = true))

        val run = store.snapshot().sessionRuns.single { it.id == "run-launch-ok" }
        assertEquals(SessionRunStatus.IN_PROGRESS, run.status)
        assertNull(run.endedAtEpochMs)
    }

    @Test
    fun launch_missingApp_returnsHonestNote_withoutClaimingCompletion() {
        approveSpanish()
        SpanishScenario.beginSession(store, dueNow, runId = "run-missing")
        val fake = FakeInstalledAppLauncher(installed = emptySet())
        val intention = store.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }

        val result = LaunchDestinationApp.attempt(
            destination = LaunchDestinationApp.resolve(intention),
            launcher = fake,
            registry = registry
        )
        assertEquals(LaunchDestinationApp.Outcome.MISSING_APP, result.outcome)
        assertFalse(result.opened)
        assertTrue(result.note.contains("isn't installed", ignoreCase = true))
        assertTrue(result.note.contains("not lesson completion", ignoreCase = true))
        assertTrue(fake.launchedPackages.isEmpty())
        assertEquals(
            SessionRunStatus.IN_PROGRESS,
            store.snapshot().sessionRuns.single().status
        )
    }

    @Test
    fun launch_blockedByRegistry_returnsLimitNote() {
        approveSpanish()
        val lockedNote = "Installed-app launch disabled in this test registry."
        val locked = lockedLaunchRegistry(lockedNote)
        val begin = NextActionEngine.recommend(store.snapshot(), dueNow, tz, locked)
        assertNull(begin.openDestinationLabel)
        assertEquals(SessionDestinations.SPANISH_PACKAGE, begin.destinationPackageName)

        val fake = FakeInstalledAppLauncher(installed = setOf(SessionDestinations.SPANISH_PACKAGE))
        val result = LaunchDestinationApp.attempt(
            destination = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID),
            launcher = fake,
            registry = locked
        )
        assertEquals(LaunchDestinationApp.Outcome.BLOCKED_BY_REGISTRY, result.outcome)
        assertTrue(result.note.contains(lockedNote))
        assertTrue(result.note.contains("not lesson completion", ignoreCase = true))
        assertTrue(fake.launchedPackages.isEmpty())
    }

    @Test
    fun launch_failure_returnsHonestNote() {
        val fake = FakeInstalledAppLauncher(
            installed = setOf(SessionDestinations.SPANISH_PACKAGE),
            failLaunchFor = setOf(SessionDestinations.SPANISH_PACKAGE)
        )
        val result = LaunchDestinationApp.attempt(
            destination = SessionDestinations.forShape("language", SpanishScenario.INTENTION_ID),
            launcher = fake,
            registry = registry
        )
        assertEquals(LaunchDestinationApp.Outcome.LAUNCH_FAILED, result.outcome)
        assertTrue(result.note.contains("Couldn't open", ignoreCase = true))
        assertTrue(result.note.contains("not lesson completion", ignoreCase = true))
    }

    @Test
    fun noDestination_returnsNoDestinationOutcome() {
        MarathonScenario.seed(store, seedAt)
        val intention = store.snapshot().intentions.first { it.id == MarathonScenario.INTENTION_ID }
        val result = LaunchDestinationApp.attempt(
            destination = LaunchDestinationApp.resolve(intention),
            launcher = FakeInstalledAppLauncher(),
            registry = registry
        )
        assertEquals(LaunchDestinationApp.Outcome.NO_DESTINATION, result.outcome)
        assertFalse(result.opened)
    }

    @Test
    fun registry_canLaunchInstalledApp_matchesActionable() {
        assertTrue(registry.canLaunchInstalledApp())
        assertFalse(lockedLaunchRegistry().canLaunchInstalledApp())
    }
}
