package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.NextActionKind
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
 * Home Discuss → Conversation navigation/command path for scheduled and
 * active-session chrome, without breaking Complete / Abandon.
 */
class DiscussFromHomeTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default
    private val seedAt = 1_700_049_600_000L
    private val dueNow = 1_700_159_400_000L // Thu 16 Nov 2023 18:30 UTC
    private val futureDay = seedAt // Wed 15 Nov 2023 12:00 UTC — day before Thursday session

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveSpanish() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
    }

    @Test
    fun fromBeginChrome_opensConversationForScheduledIntention() {
        approveSpanish()
        val next = NextActionEngine.recommend(store.snapshot(), dueNow, tz, registry)
        assertEquals(NextActionKind.BEGIN, next.kind)
        assertEquals("Discuss", next.discussLabel)

        val opened = DiscussFromHome.open(store, next, dueNow + 1, registry)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, opened.navigateTo)
        assertEquals(SpanishScenario.INTENTION_ID, opened.intentionId)
        assertEquals(SpanishScenario.SESSION_ID, opened.sessionId)
        assertFalse(opened.activatedIntention)
        assertEquals(SpanishScenario.INTENTION_ID, store.snapshot().activeIntentionId)
    }

    @Test
    fun fromDiscussChrome_opensConversationForUpcomingSession() {
        approveSpanish()
        val next = NextActionEngine.recommend(store.snapshot(), futureDay, tz, registry)
        assertEquals(NextActionKind.DISCUSS, next.kind)
        assertEquals(SpanishScenario.INTENTION_ID, next.intentionId)

        val opened = DiscussFromHome.open(store, next, futureDay + 1, registry)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, opened.navigateTo)
        assertEquals(SpanishScenario.INTENTION_ID, opened.intentionId)
        assertEquals(SpanishScenario.SESSION_ID, opened.sessionId)
    }

    @Test
    fun fromActiveSessionChrome_opensConversation_withoutEndingRun() {
        approveSpanish()
        SpanishScenario.beginSession(store, dueNow, runId = "run-discuss-active")
        val active = NextActionEngine.recommend(store.snapshot(), dueNow + 30_000L, tz, registry)
        assertEquals(NextActionKind.ACTIVE_SESSION, active.kind)
        assertEquals("Discuss", active.discussLabel)
        assertEquals("Complete", active.completeLabel)
        assertEquals("Abandon", active.abandonLabel)

        val opened = DiscussFromHome.open(store, active, dueNow + 31_000L, registry)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, opened.navigateTo)
        assertEquals(SpanishScenario.INTENTION_ID, opened.intentionId)
        assertEquals("run-discuss-active", active.sessionRunId)

        val run = store.snapshot().sessionRuns.single { it.id == "run-discuss-active" }
        assertEquals(SessionRunStatus.IN_PROGRESS, run.status)
        assertNull(run.endedAtEpochMs)

        // Complete / Abandon still work after Discuss preparation
        store.apply(
            FlowCommand.CompleteSessionRun(
                runId = "run-discuss-active",
                nowEpochMs = dueNow + 60_000L
            )
        )
        assertEquals(
            SessionRunStatus.COMPLETED,
            store.snapshot().sessionRuns.single { it.id == "run-discuss-active" }.status
        )
    }

    @Test
    fun fromActiveSession_abandonStillWorksAfterDiscussOpen() {
        approveSpanish()
        SpanishScenario.beginSession(store, dueNow, runId = "run-discuss-abandon")
        val active = NextActionEngine.recommend(store.snapshot(), dueNow + 10_000L, tz, registry)
        assertEquals(NextActionKind.ACTIVE_SESSION, active.kind)

        val opened = DiscussFromHome.open(store, active, dueNow + 11_000L, registry)
        assertNotNull(opened.navigateTo)

        store.apply(
            FlowCommand.AbandonSessionRun(
                runId = "run-discuss-abandon",
                nowEpochMs = dueNow + 12_000L
            )
        )
        assertEquals(
            SessionRunStatus.ABANDONED,
            store.snapshot().sessionRuns.single { it.id == "run-discuss-abandon" }.status
        )
    }

    @Test
    fun activatesIntentionFromNextAction_whenActiveDiffers() {
        approveSpanish()
        MarathonScenario.ensureSeeded(store)
        assertEquals(MarathonScenario.INTENTION_ID, store.snapshot().activeIntentionId)

        val spanishNext = NextActionEngine.recommend(
            store.snapshot().copy(activeIntentionId = SpanishScenario.INTENTION_ID),
            dueNow,
            tz,
            registry
        )
        assertEquals(SpanishScenario.INTENTION_ID, spanishNext.intentionId)
        assertEquals(NextActionKind.BEGIN, spanishNext.kind)

        // Store still has marathon active; Discuss should switch for Conversation context
        assertEquals(MarathonScenario.INTENTION_ID, store.snapshot().activeIntentionId)
        val opened = DiscussFromHome.open(store, spanishNext, dueNow + 1, registry)
        assertEquals(DiscussFromHome.CONVERSATION_ROUTE, opened.navigateTo)
        assertTrue(opened.activatedIntention)
        assertEquals(SpanishScenario.INTENTION_ID, opened.intentionId)
        assertEquals(SpanishScenario.INTENTION_ID, store.snapshot().activeIntentionId)
    }

    @Test
    fun whenDiscussLocked_returnsNullRoute() {
        approveSpanish()
        val next = NextActionEngine.recommend(store.snapshot(), dueNow, tz, registry)
        assertEquals(NextActionKind.BEGIN, next.kind)

        val locked = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map {
                if (it.id == CapabilityRegistry.ID_DISCUSS) {
                    it.copy(
                        status = CapabilityStatus.NOT_SUPPORTED,
                        limitNote = "Conversation surface locked in this test build."
                    )
                } else {
                    it
                }
            }
        )
        assertFalse(locked.canDiscuss())

        val opened = DiscussFromHome.open(store, next, dueNow + 1, locked)
        assertNull(opened.navigateTo)
        assertNull(opened.intentionId)
        assertEquals(SpanishScenario.INTENTION_ID, store.snapshot().activeIntentionId)
    }

    @Test
    fun elapsedTimerFieldsSurviveDiscussOpenOnActiveCard() {
        approveSpanish()
        SpanishScenario.beginSession(store, dueNow, runId = "run-timer")
        val before = NextActionEngine.recommend(store.snapshot(), dueNow + 45_000L, tz, registry)
        assertEquals(dueNow, before.startedAtEpochMs)

        DiscussFromHome.open(store, before, dueNow + 46_000L, registry)

        val after = NextActionEngine.recommend(store.snapshot(), dueNow + 90_000L, tz, registry)
        assertEquals(NextActionKind.ACTIVE_SESSION, after.kind)
        assertEquals(dueNow, after.startedAtEpochMs)
        assertEquals("1:30", com.intent.launcher.domain.SessionElapsed.format(dueNow, dueNow + 90_000L))
    }
}
