package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ThoughtCapture
import com.intent.launcher.domain.ThoughtCaptureStatus
import com.intent.launcher.domain.ThoughtDisposition
import com.intent.launcher.store.IntentionFlowStore
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/**
 * Evening thought triage (OS-154) — walk RAW SAID thoughts one at a time.
 * Disposition is user-picked only; never LLM-invented.
 */
object EveningThoughtTriage {

    const val HONESTY = "You choose — no auto-classify, no live LLM."

    data class Result(
        val ok: Boolean,
        val thought: ThoughtCapture?,
        val disposition: ThoughtDisposition?,
        val confirmation: String
    )

    /** Unclassified RAW inbox for evening check-out (oldest first). */
    fun listRawForEvening(store: IntentionFlowStore): List<ThoughtCapture> =
        store.snapshot().thoughtCaptures
            .filter { it.status == ThoughtCaptureStatus.RAW && it.disposition == null }
            .sortedBy { it.capturedAtEpochMs }

    fun nextRaw(store: IntentionFlowStore): ThoughtCapture? =
        listRawForEvening(store).firstOrNull()

    /**
     * Apply a user-confirmed disposition. Does not invent content — uses thought text as-is.
     */
    fun classify(
        store: IntentionFlowStore,
        thoughtId: String,
        disposition: ThoughtDisposition,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Result {
        val thought = store.snapshot().thoughtCaptures.firstOrNull { it.id == thoughtId }
            ?: return Result(
                ok = false,
                thought = null,
                disposition = null,
                confirmation = "Thought not found."
            )
        if (thought.status != ThoughtCaptureStatus.RAW || thought.disposition != null) {
            return Result(
                ok = false,
                thought = thought,
                disposition = thought.disposition,
                confirmation = "Already classified — pick another thought."
            )
        }
        val ids = idsFor(disposition, thoughtId, nowEpochMs)
        val slot = if (disposition == ThoughtDisposition.TASK) {
            taskSessionSlot(nowEpochMs, timeZone)
        } else {
            null
        }
        store.apply(
            FlowCommand.ClassifyThought(
                thoughtId = thoughtId,
                disposition = disposition,
                nowEpochMs = nowEpochMs,
                newIntentionId = ids.newIntentionId,
                factId = ids.factId,
                sessionId = ids.sessionId,
                taskDayOfWeek = slot?.first,
                taskHour = slot?.second?.first,
                taskMinute = slot?.second?.second
            )
        )
        val saved = store.snapshot().thoughtCaptures.firstOrNull { it.id == thoughtId }
        val ok = saved?.disposition == disposition
        return Result(
            ok = ok,
            thought = saved,
            disposition = saved?.disposition,
            confirmation = if (ok) confirmationFor(disposition, thought.text) else {
                "Could not classify thought."
            }
        )
    }

    fun confirmationFor(disposition: ThoughtDisposition, text: String): String {
        val short = text.trim().replace('\n', ' ').let {
            if (it.length <= 40) it else it.take(37).trimEnd() + "…"
        }
        return when (disposition) {
            ThoughtDisposition.TASK ->
                "Task saved — “$short” (lightweight session stub; SAID)."
            ThoughtDisposition.INTENTION ->
                "Intention seeded — “$short” (exploring; SAID)."
            ThoughtDisposition.REMINDER ->
                "Reminder note saved — “$short” (SAID; no alarm invented)."
            ThoughtDisposition.KEEP ->
                "Kept as evidence — “$short”."
            ThoughtDisposition.DISCARD ->
                "Discarded — “$short”."
        }
    }

    /**
     * Day-of-week / hour for a TASK session stub: tomorrow local morning (09:00).
     * Local schedule only — not an external calendar write.
     */
    fun taskSessionSlot(
        nowEpochMs: Long,
        timeZone: TimeZone
    ): Pair<Int, Pair<Int, Int>> {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowEpochMs
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val dayOfWeek = ((cal.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 // ISO 1=Mon…7=Sun
        return dayOfWeek to (9 to 0)
    }

    private data class Ids(
        val newIntentionId: String?,
        val factId: String?,
        val sessionId: String?
    )

    private fun idsFor(
        disposition: ThoughtDisposition,
        thoughtId: String,
        nowEpochMs: Long
    ): Ids =
        when (disposition) {
            ThoughtDisposition.TASK -> Ids(
                newIntentionId = "intention-task-$thoughtId",
                factId = "fact-task-$thoughtId-$nowEpochMs",
                sessionId = "session-task-$thoughtId"
            )
            ThoughtDisposition.INTENTION -> Ids(
                newIntentionId = "intention-evening-$thoughtId",
                factId = "fact-evening-$thoughtId-seed",
                sessionId = null
            )
            ThoughtDisposition.REMINDER -> Ids(
                newIntentionId = null,
                factId = "fact-reminder-$thoughtId-$nowEpochMs",
                sessionId = null
            )
            ThoughtDisposition.KEEP,
            ThoughtDisposition.DISCARD -> Ids(null, null, null)
        }

    /** Stable id helper for tests that want a fixed thought id path. */
    fun newThoughtId(): String = "thought-${UUID.randomUUID()}"
}
