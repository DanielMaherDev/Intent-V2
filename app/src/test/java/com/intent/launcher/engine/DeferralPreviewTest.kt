package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Stage 4 deferral deadline preview — read model before FlowCommand defer.
 */
class DeferralPreviewTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val seedAt = 1_700_049_600_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun thursdayDue(): Long {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
        val session = store.snapshot().sessions.first()
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = seedAt + 86_400_000L
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.THURSDAY) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
        }
        cal.set(Calendar.HOUR_OF_DAY, session.hour)
        cal.set(Calendar.MINUTE, session.minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun preview_laterToday_showsNewWindow_andNoDependentsWhenAlone() {
        val dueNow = thursdayDue()
        val session = store.snapshot().sessions.single()
        val preview = DeferralPreview.preview(
            snapshot = store.snapshot(),
            sessionId = session.id,
            nowEpochMs = dueNow,
            deferMs = DeferralPreview.DEFAULT_DEFER_MS,
            timeZone = tz
        )
        assertNotNull(preview)
        assertEquals(session.id, preview!!.sessionId)
        assertTrue(preview.toLabel.contains("today", ignoreCase = true))
        assertTrue(preview.toLabel.contains("20:00") || preview.deferUntilEpochMs == dueNow + 2 * 60 * 60 * 1000L)
        assertTrue(preview.affectedSessions.isEmpty())
        assertTrue(preview.notes.contains("No other sessions", ignoreCase = true))
        assertTrue(preview.oneLine.contains("Moves to", ignoreCase = true))
        assertTrue(preview.oneLine.contains("no other sessions", ignoreCase = true))
        assertTrue(preview.notes.contains("dependency graph", ignoreCase = true))
        assertTrue(preview.notes.contains("Local schedule", ignoreCase = true))
    }

    @Test
    fun preview_listsSiblingSessions_withoutReschedulingThem() {
        val dueNow = thursdayDue()
        val primary = store.snapshot().sessions.single()
        store.apply(
            FlowCommand.ProposeRecurringSession(
                proposalId = "prop-sibling",
                intentionId = SpanishScenario.INTENTION_ID,
                title = "Friday review",
                summary = "Local sibling session",
                dayOfWeek = 5,
                hour = 9,
                minute = 0,
                basedOnFactIds = emptyList(),
                nowEpochMs = dueNow
            )
        )
        store.apply(
            FlowCommand.ApproveProposal(
                proposalId = "prop-sibling",
                sessionId = "session-sibling",
                nowEpochMs = dueNow + 1
            )
        )
        store.apply(
            FlowCommand.MarkPhase(
                intentionId = SpanishScenario.INTENTION_ID,
                phase = IntentionPhase.ACTIVE,
                nowEpochMs = dueNow + 2
            )
        )

        val preview = DeferralPreview.preview(
            snapshot = store.snapshot(),
            sessionId = primary.id,
            nowEpochMs = dueNow,
            timeZone = tz
        )
        assertNotNull(preview)
        assertEquals(1, preview!!.affectedSessions.size)
        assertEquals("session-sibling", preview.affectedSessions.first().sessionId)
        assertTrue(preview.affectedSessions.first().title.contains("Friday", ignoreCase = true))
        assertTrue(preview.oneLine.contains("still scheduled", ignoreCase = true))
        assertTrue(preview.notes.contains("not auto-rescheduled", ignoreCase = true))

        store.apply(
            FlowCommand.DeferNextSession(
                sessionId = primary.id,
                deferUntilEpochMs = preview.deferUntilEpochMs,
                nowEpochMs = dueNow + 3
            )
        )
        val sibling = store.snapshot().sessions.first { it.id == "session-sibling" }
        assertNull(sibling.deferredUntilEpochMs)
        val deferred = store.snapshot().sessions.first { it.id == primary.id }
        assertEquals(preview.deferUntilEpochMs, deferred.deferredUntilEpochMs)
    }

    @Test
    fun preview_nullWhenSessionMissing() {
        assertNull(
            DeferralPreview.preview(
                snapshot = store.snapshot(),
                sessionId = "missing",
                nowEpochMs = seedAt,
                timeZone = tz
            )
        )
    }

    @Test
    fun preview_nullWhileSessionRunInProgress() {
        val dueNow = thursdayDue()
        SpanishScenario.beginSession(store, dueNow, runId = "run-1")
        val sessionId = store.snapshot().sessions.first().id
        assertNull(
            DeferralPreview.preview(
                snapshot = store.snapshot(),
                sessionId = sessionId,
                nowEpochMs = dueNow,
                timeZone = tz
            )
        )
    }

    @Test
    fun confirmPath_matchesPreviewWindow() {
        val dueNow = thursdayDue()
        val session = store.snapshot().sessions.single()
        val preview = DeferralPreview.preview(
            snapshot = store.snapshot(),
            sessionId = session.id,
            nowEpochMs = dueNow,
            timeZone = tz
        )!!
        store.apply(
            FlowCommand.DeferNextSession(
                sessionId = preview.sessionId,
                deferUntilEpochMs = preview.deferUntilEpochMs,
                nowEpochMs = dueNow
            )
        )
        assertEquals(
            preview.deferUntilEpochMs,
            store.snapshot().sessions.first().deferredUntilEpochMs
        )
        val after = NextActionEngine.recommend(store.snapshot(), dueNow + 1_000L, tz)
        assertTrue(after.reason.contains("deferred", ignoreCase = true))
    }
}
