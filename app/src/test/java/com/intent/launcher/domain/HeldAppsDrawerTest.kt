package com.intent.launcher.domain

import com.intent.launcher.engine.LaunchDestinationApp
import com.intent.launcher.engine.NextActionEngine
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
 * Intention drawer reorder + hold-for-apps SAID bindings (v0.0.36).
 */
class HeldAppsDrawerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L
    private val tz = TimeZone.getTimeZone("UTC")

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun reorderIntentions_persistsDrawerOrder() {
        SpanishScenario.seed(store, now)
        MarathonScenario.seed(store, now + 1)
        BusinessScenario.seed(store, now + 2)

        val ids = IntentionOrdering.ordered(store.snapshot()).map { it.id }
        assertEquals(3, ids.size)

        val moved = IntentionOrdering.move(ids, ids.first(), 1)
        store.apply(
            FlowCommand.ReorderIntentions(
                orderedIntentionIds = moved,
                nowEpochMs = now + 10
            )
        )
        val after = IntentionOrdering.ordered(store.snapshot()).map { it.id }
        assertEquals(moved, after)

        val restarted = IntentionFlowStore(tmp.root.resolve("flow.json"))
        assertEquals(moved, IntentionOrdering.ordered(restarted.snapshot()).map { it.id })
    }

    @Test
    fun holdApp_isSaid_andPreferredOverShapeDefault() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 1)
        SpanishScenario.approve(store, now + 2)

        val intentionId = SpanishScenario.INTENTION_ID
        assertFalse(
            HeldApps.isHeld(store.snapshot(), intentionId, SessionDestinations.SPANISH_PACKAGE)
        )

        store.apply(
            FlowCommand.HoldAppForIntention(
                heldAppId = "held-duo",
                intentionId = intentionId,
                packageName = SessionDestinations.SPANISH_PACKAGE,
                label = SessionDestinations.SPANISH_LABEL,
                nowEpochMs = now + 3
            )
        )
        val snap = store.snapshot()
        val held = HeldApps.forIntention(snap, intentionId)
        assertEquals(1, held.size)
        assertEquals(ProvenanceKind.SAID, held.first().provenance.kind)
        assertEquals("user-hold", held.first().provenance.sourceLabel)

        val intention = snap.intentions.first { it.id == intentionId }
        val dest = HeldApps.primaryDestination(snap, intention)
        assertEquals(SessionDestinations.SPANISH_PACKAGE, dest!!.packageName)

        val next = NextActionEngine.recommend(snap, 1_700_159_400_000L, tz)
        assertEquals(SessionDestinations.SPANISH_PACKAGE, next.destinationPackageName)

        val resolved = LaunchDestinationApp.resolve(
            intention = intention,
            nextAction = null,
            snapshot = snap
        )
        assertEquals(SessionDestinations.SPANISH_PACKAGE, resolved!!.packageName)
    }

    @Test
    fun reorderAndReleaseHeldApps() {
        SpanishScenario.seed(store, now)
        MarathonScenario.ensureSeeded(store)
        store.apply(
            FlowCommand.SetActiveIntention(
                intentionId = MarathonScenario.INTENTION_ID,
                nowEpochMs = now + 1
            )
        )
        store.apply(
            FlowCommand.HoldAppForIntention(
                heldAppId = "held-a",
                intentionId = MarathonScenario.INTENTION_ID,
                packageName = "com.duolingo",
                label = "Duolingo",
                nowEpochMs = now + 2
            )
        )
        // Second hold of same package is idempotent label refresh — add via distinct packages
        // only Duolingo in catalog; hold twice different ids same package stays one.
        store.apply(
            FlowCommand.HoldAppForIntention(
                heldAppId = "held-b",
                intentionId = MarathonScenario.INTENTION_ID,
                packageName = "com.duolingo",
                label = "Duolingo practice",
                nowEpochMs = now + 3
            )
        )
        assertEquals(1, HeldApps.forIntention(store.snapshot(), MarathonScenario.INTENTION_ID).size)

        // Release leaves empty; suggestions return.
        val id = HeldApps.forIntention(store.snapshot(), MarathonScenario.INTENTION_ID).first().id
        store.apply(FlowCommand.ReleaseHeldApp(heldAppId = id, nowEpochMs = now + 4))
        assertTrue(HeldApps.forIntention(store.snapshot(), MarathonScenario.INTENTION_ID).isEmpty())

        val suggestions = HoldableApps.suggestions(
            store.snapshot().intentions.first { it.id == MarathonScenario.INTENTION_ID },
            store.snapshot()
        )
        assertTrue(suggestions.any { it.packageName == SessionDestinations.SPANISH_PACKAGE })
    }

    @Test
    fun holdSuggestions_excludeAlreadyHeld() {
        SpanishScenario.seed(store, now)
        val intention = store.snapshot().intentions.first()
        val before = HoldableApps.suggestions(intention, store.snapshot())
        assertNotNull(before.firstOrNull { it.packageName == SessionDestinations.SPANISH_PACKAGE })

        store.apply(
            FlowCommand.HoldAppForIntention(
                heldAppId = "held-duo",
                intentionId = intention.id,
                packageName = SessionDestinations.SPANISH_PACKAGE,
                label = SessionDestinations.SPANISH_LABEL,
                nowEpochMs = now + 1
            )
        )
        val after = HoldableApps.suggestions(intention, store.snapshot())
        assertFalse(after.any { it.packageName == SessionDestinations.SPANISH_PACKAGE })
    }

    @Test
    fun noUsageStatsAutoHold() {
        SpanishScenario.seed(store, now)
        // Seeding / shape default must not create a SAID hold.
        assertTrue(store.snapshot().heldApps.isEmpty())
        assertEquals(
            ProvenanceKind.SAID,
            // After explicit hold only
            run {
                store.apply(
                    FlowCommand.HoldAppForIntention(
                        heldAppId = "h1",
                        intentionId = SpanishScenario.INTENTION_ID,
                        packageName = SessionDestinations.SPANISH_PACKAGE,
                        label = SessionDestinations.SPANISH_LABEL,
                        nowEpochMs = now + 1
                    )
                )
                store.snapshot().heldApps.first().provenance.kind
            }
        )
    }
}
