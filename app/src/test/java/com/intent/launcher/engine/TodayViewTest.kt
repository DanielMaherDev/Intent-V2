package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Dedicated Today projection — planned · completed · deferred from store records.
 */
class TodayViewTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val registry = CapabilityRegistry.default
    private val seedAt = 1_700_049_600_000L
    /** Thursday 30 Nov 2023 09:00 UTC — Spanish tutor day. */
    private val thu0900 = 1_700_125_200_000L
    private val midAfternoon = thu0900 + 5 * 3_600_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun approveSpanishWithMorning() {
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
                occurrenceId = "occ-morning-today",
                dailyContextId = "dctx-morning-today"
            )
        )
    }

    @Test
    fun project_listsMorningOccurrenceAsCompleted_andSessionAsPlanned() {
        approveSpanishWithMorning()
        val model = TodayView.project(store.snapshot(), thu0900 + 60_000L, tz)
        assertEquals(DailyCycle.localDateString(thu0900, tz), model.localDate)
        assertTrue(model.honestyNote.contains("not chat prose", ignoreCase = true))
        assertTrue(
            model.completed.any {
                it.title.contains("Morning check-in", ignoreCase = true) &&
                    it.detail.contains("Speak Spanish")
            }
        )
        assertTrue(
            model.planned.any {
                it.title.contains("Spanish", ignoreCase = true) ||
                    it.detail.contains("Thursday", ignoreCase = true) ||
                    it.statusLabel.contains("Later", ignoreCase = true) ||
                    it.statusLabel.contains("Due", ignoreCase = true) ||
                    it.statusLabel.contains("Approaching", ignoreCase = true)
            }
        )
        assertTrue(model.deferred.isEmpty())
    }

    @Test
    fun project_middayDefer_movesSessionToDeferred() {
        approveSpanishWithMorning()
        val localDate = DailyCycle.localDateString(midAfternoon, tz)
        val sessionId = store.snapshot().sessions
            .first { it.intentionId == SpanishScenario.INTENTION_ID }
            .id
        val deferUntil = midAfternoon + 3 * 3_600_000L
        store.apply(
            FlowCommand.RecordMiddayCheck(
                occurrenceId = "occ-midday-today",
                contextId = "dctx-midday-today",
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                summary = "Midday check — deferring tutor block",
                nowEpochMs = midAfternoon,
                deferSessionId = sessionId,
                deferUntilEpochMs = deferUntil
            )
        )
        val model = TodayView.project(store.snapshot(), midAfternoon + 60_000L, tz)
        assertTrue(
            model.completed.any { it.title.contains("Midday check", ignoreCase = true) }
        )
        assertTrue(
            model.deferred.any {
                it.statusLabel.equals("Deferred", ignoreCase = true) &&
                    it.detail.contains("Deferred", ignoreCase = true)
            }
        )
    }

    @Test
    fun project_completedSessionRun_listsUnderCompleted() {
        approveSpanishWithMorning()
        val session = store.snapshot().sessions
            .first { it.intentionId == SpanishScenario.INTENTION_ID }
        store.apply(
            FlowCommand.BeginSession(
                runId = "run-today-1",
                sessionId = session.id,
                nowEpochMs = thu0900 + 9 * 3_600_000L
            )
        )
        store.apply(
            FlowCommand.CompleteSessionRun(
                runId = "run-today-1",
                nowEpochMs = thu0900 + 10 * 3_600_000L
            )
        )
        val model = TodayView.project(
            store.snapshot(),
            thu0900 + 10 * 3_600_000L + 1,
            tz
        )
        assertTrue(
            model.completed.any {
                it.id == "run-run-today-1" &&
                    it.statusLabel.equals("Completed", ignoreCase = true)
            }
        )
    }
}
