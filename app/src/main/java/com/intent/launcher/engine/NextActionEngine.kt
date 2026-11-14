package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import java.util.Calendar
import java.util.TimeZone

object NextActionEngine {

    fun recommend(
        snapshot: IntentionSnapshot,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): NextAction {
        val intentionId = snapshot.activeIntentionId
            ?: snapshot.intentions.firstOrNull()?.id
            ?: return NextAction(
                kind = NextActionKind.NONE,
                label = "Where would you like to begin?",
                detail = "Start an intention when you are ready.",
                intentionId = null
            )

        val intention = snapshot.intentions.firstOrNull { it.id == intentionId }
            ?: return NextAction(
                kind = NextActionKind.NONE,
                label = "Where would you like to begin?",
                detail = "Start an intention when you are ready.",
                intentionId = null
            )

        val pending = snapshot.proposals.firstOrNull {
            it.intentionId == intentionId && it.status == ProposalStatus.PENDING
        }
        if (pending != null) {
            return NextAction(
                kind = NextActionKind.REVIEW_PROPOSAL,
                label = "Review weekly session",
                detail = pending.summary,
                intentionId = intentionId,
                proposalId = pending.id
            )
        }

        val session = snapshot.sessions
            .filter { it.intentionId == intentionId }
            .minByOrNull { urgency(it.deferredUntilEpochMs, it.dayOfWeek, it.hour, it.minute, nowEpochMs, timeZone) }

        if (session != null && intention.phase == IntentionPhase.ACTIVE) {
            val whenLabel = formatSessionWhen(session.deferredUntilEpochMs, session.dayOfWeek, session.hour, session.minute, nowEpochMs, timeZone)
            return NextAction(
                kind = NextActionKind.BEGIN,
                label = session.title,
                detail = whenLabel,
                intentionId = intentionId,
                sessionId = session.id,
                beginLabel = "Begin",
                deferLabel = "Later",
                discussLabel = "Discuss"
            )
        }

        return when (intention.phase) {
            IntentionPhase.EXPLORING, IntentionPhase.CLARIFYING -> NextAction(
                kind = NextActionKind.CONTINUE_EXPLORING,
                label = "Continue exploring",
                detail = intention.title,
                intentionId = intentionId
            )
            IntentionPhase.PROPOSAL_PENDING -> NextAction(
                kind = NextActionKind.REVIEW_PROPOSAL,
                label = "Review proposal",
                detail = intention.title,
                intentionId = intentionId
            )
            IntentionPhase.PAUSED -> NextAction(
                kind = NextActionKind.DISCUSS,
                label = "Discuss",
                detail = intention.title,
                intentionId = intentionId,
                discussLabel = "Discuss"
            )
            IntentionPhase.ACTIVE -> NextAction(
                kind = NextActionKind.CONTINUE_EXPLORING,
                label = "Open your intention",
                detail = intention.title,
                intentionId = intentionId
            )
        }
    }

    private fun urgency(
        deferredUntil: Long?,
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
        now: Long,
        tz: TimeZone
    ): Long {
        if (deferredUntil != null && deferredUntil > now) return deferredUntil
        return nextOccurrence(dayOfWeek, hour, minute, now, tz)
    }

    fun nextOccurrence(dayOfWeek: Int, hour: Int, minute: Int, now: Long, tz: TimeZone): Long {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = now
        // Convert ISO DOW to Calendar DOW: ISO 7=Sun→1, ISO 1=Mon→2, …
        val calendarDow = if (dayOfWeek == 7) Calendar.SUNDAY else dayOfWeek + 1
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        while (cal.get(Calendar.DAY_OF_WEEK) != calendarDow || cal.timeInMillis <= now) {
            cal.add(Calendar.DAY_OF_MONTH, 1)
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
        }
        return cal.timeInMillis
    }

    fun formatSessionWhen(
        deferredUntil: Long?,
        dayOfWeek: Int,
        hour: Int,
        minute: Int,
        now: Long,
        tz: TimeZone
    ): String {
        if (deferredUntil != null && deferredUntil > now) {
            val cal = Calendar.getInstance(tz)
            cal.timeInMillis = deferredUntil
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            return "Deferred until today %02d:%02d".format(h, m)
        }
        val names = listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        return "%s at %02d:%02d".format(names.getOrElse(dayOfWeek) { "Day $dayOfWeek" }, hour, minute)
    }
}
