package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.domain.SessionRun
import com.intent.launcher.domain.SessionRunStatus
import java.util.Calendar
import java.util.TimeZone

/**
 * Stage 4 — daily action: deterministic candidates + time-aware ranking with an
 * explainable [NextAction.reason] tied to schedule/state and capability honesty.
 */
object NextActionEngine {

    /** Surface Begin when the next occurrence is within this lead window. */
    const val APPROACHING_LEAD_MS: Long = 3 * 60 * 60 * 1000L

    private val DAY_NAMES =
        listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun recommend(
        snapshot: IntentionSnapshot,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault(),
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): NextAction {
        val intentionId = snapshot.activeIntentionId
            ?: snapshot.intentions.firstOrNull()?.id
            ?: return NextAction(
                kind = NextActionKind.NONE,
                label = "Where would you like to begin?",
                detail = "Start an intention when you are ready.",
                reason = "No active intention yet — nothing is scheduled.",
                intentionId = null
            )

        val intention = snapshot.intentions.firstOrNull { it.id == intentionId }
            ?: return NextAction(
                kind = NextActionKind.NONE,
                label = "Where would you like to begin?",
                detail = "Start an intention when you are ready.",
                reason = "No active intention yet — nothing is scheduled.",
                intentionId = null
            )

        val pending = snapshot.proposals.firstOrNull {
            it.intentionId == intentionId && it.status == ProposalStatus.PENDING
        }
        if (pending != null && registry.canRecommendReviewProposal()) {
            val whenLabel = formatClock(pending.proposedDayOfWeek, pending.proposedHour, pending.proposedMinute)
            return NextAction(
                kind = NextActionKind.REVIEW_PROPOSAL,
                label = "Review weekly session",
                detail = pending.summary,
                reason = "A recurring session proposal ($whenLabel) is waiting for your approval before it can be scheduled.",
                intentionId = intentionId,
                proposalId = pending.id
            )
        }

        // An in-progress run outranks scheduling recommendations for this intention.
        val activeRun = snapshot.sessionRuns.firstOrNull {
            it.intentionId == intentionId && it.status == SessionRunStatus.IN_PROGRESS
        }
        if (activeRun != null) {
            return recommendForActiveRun(activeRun, registry)
        }

        val session = snapshot.sessions
            .filter { it.intentionId == intentionId }
            .minByOrNull {
                urgency(it.deferredUntilEpochMs, it.dayOfWeek, it.hour, it.minute, nowEpochMs, timeZone)
            }

        if (session != null && intention.phase == IntentionPhase.ACTIVE) {
            return recommendForSession(session, intentionId, nowEpochMs, timeZone, registry)
        }

        return when (intention.phase) {
            IntentionPhase.EXPLORING, IntentionPhase.CLARIFYING -> {
                if (registry.canContinueExploring()) {
                    NextAction(
                        kind = NextActionKind.CONTINUE_EXPLORING,
                        label = "Continue exploring",
                        detail = intention.title,
                        reason = "This intention is still in discovery — there is no eligible scheduled action yet.",
                        intentionId = intentionId
                    )
                } else {
                    NextAction(
                        kind = NextActionKind.NONE,
                        label = "Where would you like to begin?",
                        detail = registry.get(CapabilityRegistry.ID_CONTINUE_EXPLORING)?.limitNote
                            ?: "Exploration is unavailable.",
                        reason = registry.get(CapabilityRegistry.ID_CONTINUE_EXPLORING)?.limitNote
                            ?: "Continue exploring is not available in this build.",
                        intentionId = intentionId
                    )
                }
            }
            IntentionPhase.PROPOSAL_PENDING -> {
                if (registry.canRecommendReviewProposal()) {
                    NextAction(
                        kind = NextActionKind.REVIEW_PROPOSAL,
                        label = "Review proposal",
                        detail = intention.title,
                        reason = "A proposal is pending approval — review it before a session can be scheduled.",
                        intentionId = intentionId
                    )
                } else {
                    NextAction(
                        kind = NextActionKind.CONTINUE_EXPLORING,
                        label = "Continue exploring",
                        detail = intention.title,
                        reason = registry.get(CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION)?.limitNote
                            ?: "Proposal review is unavailable; continue exploring instead.",
                        intentionId = intentionId
                    )
                }
            }
            IntentionPhase.PAUSED -> NextAction(
                kind = NextActionKind.DISCUSS,
                label = "Discuss",
                detail = intention.title,
                reason = "This intention is paused — discuss when you want to resume.",
                intentionId = intentionId,
                discussLabel = "Discuss"
            )
            IntentionPhase.ACTIVE -> NextAction(
                kind = NextActionKind.CONTINUE_EXPLORING,
                label = "Open your intention",
                detail = intention.title,
                reason = "No eligible scheduled action for today — open your intention to continue.",
                intentionId = intentionId
            )
        }
    }

    private fun recommendForActiveRun(
        run: SessionRun,
        registry: CapabilityRegistry
    ): NextAction {
        val canComplete = registry.canCompleteSession()
        val canAbandon = registry.canAbandonSession()
        if (!canComplete && !canAbandon) {
            val note = registry.get(CapabilityRegistry.ID_COMPLETE_SESSION)?.limitNote
                ?: "Session completion controls are not available in this build."
            return NextAction(
                kind = NextActionKind.DISCUSS,
                label = run.title,
                detail = "Session in progress",
                reason = note,
                intentionId = run.intentionId,
                sessionId = run.sessionId,
                sessionRunId = run.id,
                startedAtEpochMs = run.startedAtEpochMs,
                discussLabel = if (registry.canDiscuss()) "Discuss" else null
            )
        }
        return NextAction(
            kind = NextActionKind.ACTIVE_SESSION,
            label = run.title,
            detail = "Session in progress",
            reason = "You started this session — mark it complete when finished, or abandon if you stop. Completion is user-reported, not verified by calendar or an LLM.",
            intentionId = run.intentionId,
            sessionId = run.sessionId,
            sessionRunId = run.id,
            startedAtEpochMs = run.startedAtEpochMs,
            completeLabel = if (canComplete) "Complete" else null,
            abandonLabel = if (canAbandon) "Abandon" else null,
            discussLabel = if (registry.canDiscuss()) "Discuss" else null
        )
    }

    private fun recommendForSession(
        session: ScheduledSession,
        intentionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone,
        registry: CapabilityRegistry
    ): NextAction {
        val timing = classifySessionTiming(session, nowEpochMs, timeZone)
        val whenLabel = formatSessionWhen(
            session.deferredUntilEpochMs,
            session.dayOfWeek,
            session.hour,
            session.minute,
            nowEpochMs,
            timeZone
        )
        val clock = formatClock(session.dayOfWeek, session.hour, session.minute)
        val dayName = DAY_NAMES.getOrElse(session.dayOfWeek) { "day ${session.dayOfWeek}" }

        val reason = when (timing) {
            SessionTiming.DEFERRED_FUTURE ->
                "You deferred this earlier; it is waiting until ${formatDeferredClock(session.deferredUntilEpochMs!!, timeZone)}."
            SessionTiming.DUE_NOW ->
                "It is time for your scheduled session ($clock)."
            SessionTiming.APPROACHING ->
                "Your $dayName session starts soon at ${"%02d:%02d".format(session.hour, session.minute)}."
            SessionTiming.LATER_TODAY ->
                "Scheduled for later today at ${"%02d:%02d".format(session.hour, session.minute)}."
            SessionTiming.FUTURE_DAY ->
                "Next session is $clock — not due today. Discuss if you want to change the plan."
        }

        // Far-future day: prefer Discuss unless Begin is the only honest path users expect mid-flow.
        if (timing == SessionTiming.FUTURE_DAY) {
            if (registry.canDiscuss()) {
                return NextAction(
                    kind = NextActionKind.DISCUSS,
                    label = session.title,
                    detail = whenLabel,
                    reason = reason,
                    intentionId = intentionId,
                    sessionId = session.id,
                    discussLabel = "Discuss",
                    beginLabel = if (registry.canRecommendBegin()) "Begin" else null,
                    deferLabel = if (registry.canRecommendDefer()) "Later" else null
                )
            }
            if (!registry.canRecommendBegin()) {
                return capabilityBlockedSession(session, intentionId, whenLabel, registry)
            }
        }

        if (!registry.canRecommendBegin()) {
            return capabilityBlockedSession(session, intentionId, whenLabel, registry)
        }

        return NextAction(
            kind = NextActionKind.BEGIN,
            label = session.title,
            detail = whenLabel,
            reason = reason,
            intentionId = intentionId,
            sessionId = session.id,
            beginLabel = "Begin",
            deferLabel = if (registry.canRecommendDefer()) "Later" else null,
            discussLabel = if (registry.canDiscuss()) "Discuss" else null
        )
    }

    private fun capabilityBlockedSession(
        session: ScheduledSession,
        intentionId: String,
        whenLabel: String,
        registry: CapabilityRegistry
    ): NextAction {
        val note = registry.get(CapabilityRegistry.ID_BEGIN_SESSION)?.limitNote
            ?: "Beginning a session is not available in this build."
        return if (registry.canDiscuss()) {
            NextAction(
                kind = NextActionKind.DISCUSS,
                label = session.title,
                detail = whenLabel,
                reason = note,
                intentionId = intentionId,
                sessionId = session.id,
                discussLabel = "Discuss"
            )
        } else {
            NextAction(
                kind = NextActionKind.NONE,
                label = session.title,
                detail = whenLabel,
                reason = note,
                intentionId = intentionId,
                sessionId = session.id
            )
        }
    }

    /**
     * Classifies how [nowEpochMs] relates to the session's next occurrence / deferral.
     * Exposed for unit tests of reason selection.
     */
    fun classifySessionTiming(
        session: ScheduledSession,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): SessionTiming {
        val deferred = session.deferredUntilEpochMs
        if (deferred != null && deferred > nowEpochMs) {
            return SessionTiming.DEFERRED_FUTURE
        }
        val next = nextOccurrence(session.dayOfWeek, session.hour, session.minute, nowEpochMs, timeZone)
        val sameDay = isSameCalendarDay(nowEpochMs, next, timeZone)
        val msUntil = next - nowEpochMs
        return when {
            // Occurrence already passed relative to "now" on a matching day:
            // nextOccurrence always returns a future instant, so "due now" means
            // we are on the session weekday and at/after the clock time — detected
            // by checking whether the previous occurrence was today.
            isDueNowOnSessionDay(session, nowEpochMs, timeZone) -> SessionTiming.DUE_NOW
            sameDay && msUntil <= APPROACHING_LEAD_MS -> SessionTiming.APPROACHING
            sameDay -> SessionTiming.LATER_TODAY
            else -> SessionTiming.FUTURE_DAY
        }
    }

    /**
     * True when today is the session's weekday and local clock is at or past the
     * scheduled hour:minute (grace: still "due" until end of that calendar day).
     */
    fun isDueNowOnSessionDay(
        session: ScheduledSession,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): Boolean {
        val cal = Calendar.getInstance(timeZone)
        cal.timeInMillis = nowEpochMs
        val calendarDow = isoToCalendarDow(session.dayOfWeek)
        if (cal.get(Calendar.DAY_OF_WEEK) != calendarDow) return false
        val minutesNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val minutesDue = session.hour * 60 + session.minute
        return minutesNow >= minutesDue
    }

    enum class SessionTiming {
        DEFERRED_FUTURE,
        DUE_NOW,
        APPROACHING,
        LATER_TODAY,
        FUTURE_DAY
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
        val calendarDow = isoToCalendarDow(dayOfWeek)
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
            return "Deferred until today ${formatDeferredClock(deferredUntil, tz)}"
        }
        return formatClock(dayOfWeek, hour, minute)
    }

    private fun formatClock(dayOfWeek: Int, hour: Int, minute: Int): String =
        "%s at %02d:%02d".format(DAY_NAMES.getOrElse(dayOfWeek) { "Day $dayOfWeek" }, hour, minute)

    private fun formatDeferredClock(deferredUntil: Long, tz: TimeZone): String {
        val cal = Calendar.getInstance(tz)
        cal.timeInMillis = deferredUntil
        return "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }

    private fun isSameCalendarDay(a: Long, b: Long, tz: TimeZone): Boolean {
        val ca = Calendar.getInstance(tz).apply { timeInMillis = a }
        val cb = Calendar.getInstance(tz).apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    /** Convert ISO DOW (1=Mon … 7=Sun) to [Calendar] DOW. */
    private fun isoToCalendarDow(dayOfWeek: Int): Int =
        if (dayOfWeek == 7) Calendar.SUNDAY else dayOfWeek + 1
}
