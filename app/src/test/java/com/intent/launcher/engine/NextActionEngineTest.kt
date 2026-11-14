package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.engine.NextActionEngine.SessionTiming
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Stage 4 — reason selection across times and intention states.
 * Session fixture: Thursday 18:00 (ISO DOW 4), matching SpanishScenario.
 */
class NextActionEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default

    // Fixed UTC anchors around Thursday 16 Nov 2023 18:00
    private val seedAt = 1_700_049_600_000L // Wed 15 Nov 12:00 UTC
    private val wedNoon = 1_700_049_600_000L
    private val thu1200 = 1_700_136_000_000L
    private val thu1500 = 1_700_146_800_000L
    private val thu1700 = 1_700_154_000_000L
    private val thu1800 = 1_700_157_600_000L
    private val thu1830 = 1_700_159_400_000L
    private val friNoon = 1_700_222_400_000L

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
    fun exploring_reasonMentionsDiscovery_noScheduledAction() {
        SpanishScenario.seed(store, seedAt)
        val next = NextActionEngine.recommend(store.snapshot(), wedNoon, tz, registry)
        assertEquals(NextActionKind.CONTINUE_EXPLORING, next.kind)
        assertTrue(next.reason.contains("discovery", ignoreCase = true))
        assertTrue(next.reason.contains("no eligible scheduled action", ignoreCase = true))
    }

    @Test
    fun pendingProposal_reasonMentionsApproval() {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1, registry)
        val next = NextActionEngine.recommend(store.snapshot(), wedNoon, tz, registry)
        assertEquals(NextActionKind.REVIEW_PROPOSAL, next.kind)
        assertTrue(next.reason.contains("approval", ignoreCase = true))
        assertTrue(next.reason.contains("Thursday", ignoreCase = true))
    }

    @Test
    fun futureDay_recommendsDiscuss_withNotDueTodayReason() {
        approveSpanishSession()
        val next = NextActionEngine.recommend(store.snapshot(), wedNoon, tz, registry)
        assertEquals(NextActionKind.DISCUSS, next.kind)
        assertEquals(SessionTiming.FUTURE_DAY, NextActionEngine.classifySessionTiming(
            store.snapshot().sessions.first(), wedNoon, tz
        ))
        assertTrue(next.reason.contains("not due today", ignoreCase = true))
        assertTrue(next.reason.contains("Thursday", ignoreCase = true))
    }

    @Test
    fun laterToday_recommendsBegin_withLaterTodayReason() {
        approveSpanishSession()
        val session = store.snapshot().sessions.first()
        assertEquals(SessionTiming.LATER_TODAY, NextActionEngine.classifySessionTiming(session, thu1200, tz))
        val next = NextActionEngine.recommend(store.snapshot(), thu1200, tz, registry)
        assertEquals(NextActionKind.BEGIN, next.kind)
        assertTrue(next.reason.contains("later today", ignoreCase = true))
        assertEquals("Begin", next.beginLabel)
        assertEquals("Later", next.deferLabel)
        assertEquals("Discuss", next.discussLabel)
    }

    @Test
    fun approaching_recommendsBegin_withStartsSoonReason() {
        approveSpanishSession()
        val session = store.snapshot().sessions.first()
        assertEquals(SessionTiming.APPROACHING, NextActionEngine.classifySessionTiming(session, thu1700, tz))
        val next = NextActionEngine.recommend(store.snapshot(), thu1700, tz, registry)
        assertEquals(NextActionKind.BEGIN, next.kind)
        assertTrue(next.reason.contains("starts soon", ignoreCase = true))
    }

    @Test
    fun dueNow_recommendsBegin_withItIsTimeReason() {
        approveSpanishSession()
        val session = store.snapshot().sessions.first()
        assertEquals(SessionTiming.DUE_NOW, NextActionEngine.classifySessionTiming(session, thu1800, tz))
        assertEquals(SessionTiming.DUE_NOW, NextActionEngine.classifySessionTiming(session, thu1830, tz))
        val next = NextActionEngine.recommend(store.snapshot(), thu1830, tz, registry)
        assertEquals(NextActionKind.BEGIN, next.kind)
        assertTrue(next.reason.contains("It is time", ignoreCase = true))
    }

    @Test
    fun deferredFuture_recommendsBegin_withDeferralReason() {
        approveSpanishSession()
        val deferUntil = thu1700 + 2 * 60 * 60 * 1000L // 19:00
        SpanishScenario.deferSession(store, deferUntil, thu1700)
        val session = store.snapshot().sessions.first()
        assertEquals(SessionTiming.DEFERRED_FUTURE, NextActionEngine.classifySessionTiming(session, thu1700 + 1, tz))
        val next = NextActionEngine.recommend(store.snapshot(), thu1700 + 1, tz, registry)
        assertEquals(NextActionKind.BEGIN, next.kind)
        assertTrue(next.reason.contains("deferred", ignoreCase = true))
        assertTrue(next.detail.contains("Deferred", ignoreCase = true))
    }

    @Test
    fun fridayAfterThursdaySession_isFutureDayAgain() {
        approveSpanishSession()
        val session = store.snapshot().sessions.first()
        assertEquals(SessionTiming.FUTURE_DAY, NextActionEngine.classifySessionTiming(session, friNoon, tz))
        val next = NextActionEngine.recommend(store.snapshot(), friNoon, tz, registry)
        assertEquals(NextActionKind.DISCUSS, next.kind)
        assertTrue(next.reason.contains("not due today", ignoreCase = true))
    }

    @Test
    fun approachingLeadBoundary_threeHoursIsApproaching_earlierIsLaterToday() {
        approveSpanishSession()
        val session = store.snapshot().sessions.first()
        // 15:00 is exactly 3h before 18:00 → approaching
        assertEquals(SessionTiming.APPROACHING, NextActionEngine.classifySessionTiming(session, thu1500, tz))
        // 12:00 is 6h before → later today
        assertEquals(SessionTiming.LATER_TODAY, NextActionEngine.classifySessionTiming(session, thu1200, tz))
    }

    @Test
    fun beginCapabilityBlocked_fallsBackToDiscuss_withHonestLimitNote() {
        approveSpanishSession()
        val locked = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
                if (entry.id == CapabilityRegistry.ID_BEGIN_SESSION) {
                    entry.copy(
                        status = CapabilityStatus.NOT_SUPPORTED,
                        limitNote = "Beginning a session is disabled in this test registry."
                    )
                } else {
                    entry
                }
            }
        )
        val next = NextActionEngine.recommend(store.snapshot(), thu1830, tz, locked)
        assertFalse(next.kind == NextActionKind.BEGIN)
        assertEquals(NextActionKind.DISCUSS, next.kind)
        assertTrue(next.reason.contains("disabled in this test registry"))
    }

    @Test
    fun activeWithoutSession_reasonMentionsNoEligibleAction() {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = "intention-empty-active",
                title = "Empty active",
                statement = "Active but no session",
                shapeTag = "other",
                nowEpochMs = seedAt
            )
        )
        store.apply(
            FlowCommand.MarkPhase(
                intentionId = "intention-empty-active",
                phase = IntentionPhase.ACTIVE,
                nowEpochMs = seedAt + 1
            )
        )
        val next = NextActionEngine.recommend(store.snapshot(), wedNoon, tz, registry)
        assertEquals(NextActionKind.CONTINUE_EXPLORING, next.kind)
        assertTrue(next.reason.contains("No eligible scheduled action", ignoreCase = true))
    }

    @Test
    fun classifySessionTiming_rawFixtureMatchesEngine() {
        val session = ScheduledSession(
            id = "s1",
            intentionId = "i1",
            title = "Weekly",
            dayOfWeek = 4,
            hour = 18,
            minute = 0
        )
        assertEquals(SessionTiming.FUTURE_DAY, NextActionEngine.classifySessionTiming(session, wedNoon, tz))
        assertEquals(SessionTiming.LATER_TODAY, NextActionEngine.classifySessionTiming(session, thu1200, tz))
        assertEquals(SessionTiming.APPROACHING, NextActionEngine.classifySessionTiming(session, thu1700, tz))
        assertEquals(SessionTiming.DUE_NOW, NextActionEngine.classifySessionTiming(session, thu1800, tz))
    }
}
