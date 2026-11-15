package com.intent.launcher.domain

import com.intent.launcher.engine.NextActionEngine
import com.intent.launcher.engine.NextActionEngine.SessionTiming
import java.util.Calendar
import java.util.TimeZone

/**
 * OS-135 — Home four-slot attention hierarchy (honest, clutter-free).
 *
 * Surfaces derived from the intention snapshot only:
 * 1. Today's recommendation — caller keeps [NextAction] / AttentionStrip
 * 2. Due work — other intentions with eligible work today (omit when none)
 * 3. Next deadline — soonest scheduled / proposed commitment that is not
 *    already the due-now strip focus (omit when unknown)
 * 4. Agent attention — quiet Discuss affordance when the agent needs the user
 *
 * Never invents partner data, fake deadlines, or multi-stat piles.
 * Additive to four-core Home visual reclaim (#52 / 0.0.104) — does not own
 * greeting / lede / pills.
 */
object HomeAttention {

    data class AgentCue(
        val title: String,
        val subtitle: String
    )

    data class Model(
        /** Quiet one-liner, e.g. "Tutor session · tomorrow at 18:00". */
        val nextDeadlineLine: String?,
        /** Compact “Other things today” copy; null when nothing else is due. */
        val dueWorkSummary: String?,
        /** Discuss line-row under the strip; null when agent is not needed. */
        val agentAttention: AgentCue?
    )

    fun build(
        snapshot: IntentionSnapshot,
        nextAction: NextAction,
        nowEpochMs: Long,
        timeZone: TimeZone = TimeZone.getDefault()
    ): Model = Model(
        nextDeadlineLine = nextDeadlineLine(snapshot, nextAction, nowEpochMs, timeZone),
        dueWorkSummary = dueWorkSummary(snapshot, nextAction, nowEpochMs, timeZone),
        agentAttention = agentAttention(nextAction)
    )

    /**
     * Next upcoming scheduled session or pending proposal, when it is not already
     * the strip’s due/approaching focus. Omits when nothing is scheduled.
     */
    fun nextDeadlineLine(
        snapshot: IntentionSnapshot,
        nextAction: NextAction,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): String? {
        data class Candidate(val epochMs: Long, val title: String, val sessionId: String?)

        val sessionCandidates = snapshot.sessions.map { session ->
            val deferred = session.deferredUntilEpochMs
            val whenMs = if (deferred != null && deferred > nowEpochMs) {
                deferred
            } else {
                NextActionEngine.nextOccurrence(
                    session.dayOfWeek,
                    session.hour,
                    session.minute,
                    nowEpochMs,
                    timeZone
                )
            }
            Candidate(whenMs, session.title, session.id)
        }

        val proposalCandidates = snapshot.proposals
            .filter { it.status == ProposalStatus.PENDING }
            .map { proposal ->
                val whenMs = NextActionEngine.nextOccurrence(
                    proposal.proposedDayOfWeek,
                    proposal.proposedHour,
                    proposal.proposedMinute,
                    nowEpochMs,
                    timeZone
                )
                Candidate(whenMs, proposal.title, sessionId = null)
            }

        val best = (sessionCandidates + proposalCandidates).minByOrNull { it.epochMs }
            ?: return null

        // Strip already carries due/approaching focus for this session — don't duplicate.
        val stripOwnsDeadline =
            best.sessionId != null &&
                best.sessionId == nextAction.sessionId &&
                nextAction.kind in setOf(
                    NextActionKind.BEGIN,
                    NextActionKind.ACTIVE_SESSION,
                    NextActionKind.CHECK_IN,
                    NextActionKind.MIDDAY,
                    NextActionKind.CHECK_OUT
                ) &&
                run {
                    val session = snapshot.sessions.firstOrNull { it.id == best.sessionId }
                        ?: return@run false
                    val timing = NextActionEngine.classifySessionTiming(session, nowEpochMs, timeZone)
                    timing == SessionTiming.DUE_NOW ||
                        timing == SessionTiming.APPROACHING ||
                        timing == SessionTiming.LATER_TODAY
                }
        if (stripOwnsDeadline) return null

        val relative = relativeWhen(best.epochMs, nowEpochMs, timeZone) ?: return null
        val shortTitle = best.title.trim().ifBlank { "Next commitment" }
        return "$shortTitle · $relative"
    }

    /**
     * Count other intentions (not the recommended one) that honestly need attention
     * today. Open errands stay in the Errands section — not double-listed here.
     */
    fun dueWorkSummary(
        snapshot: IntentionSnapshot,
        nextAction: NextAction,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): String? {
        val focusId = nextAction.intentionId
        val otherIds = snapshot.intentions
            .asSequence()
            .map { it.id }
            .filter { it != focusId }
            .filter { id -> intentionHasDueWorkToday(snapshot, id, nowEpochMs, timeZone) }
            .toSet()
        if (otherIds.isEmpty()) return null
        val n = otherIds.size
        return if (n == 1) {
            "1 other intention has something worth your attention."
        } else {
            "$n other intentions have something worth your attention."
        }
    }

    fun agentAttention(nextAction: NextAction): AgentCue? {
        if (nextAction.discussLabel.isNullOrBlank()) return null
        return when (nextAction.kind) {
            NextActionKind.BEGIN,
            NextActionKind.DISCUSS -> AgentCue(
                title = "Want to adjust the plan?",
                subtitle = "Discuss it with your agent"
            )
            NextActionKind.REVIEW_PROPOSAL -> AgentCue(
                title = "Talk through this proposal?",
                subtitle = "Discuss it with your agent"
            )
            NextActionKind.ACTIVE_SESSION -> AgentCue(
                title = "Need to change course?",
                subtitle = "Discuss it with your agent"
            )
            NextActionKind.CHECK_IN,
            NextActionKind.MIDDAY,
            NextActionKind.CHECK_OUT -> AgentCue(
                title = "Want to adjust today's focus?",
                subtitle = "Discuss it with your agent"
            )
            // Continue exploring / NONE — primary strip already owns the path.
            else -> null
        }
    }

    private fun intentionHasDueWorkToday(
        snapshot: IntentionSnapshot,
        intentionId: String,
        nowEpochMs: Long,
        timeZone: TimeZone
    ): Boolean {
        if (snapshot.sessionRuns.any {
                it.intentionId == intentionId && it.status == SessionRunStatus.IN_PROGRESS
            }
        ) {
            return true
        }
        if (snapshot.proposals.any {
                it.intentionId == intentionId && it.status == ProposalStatus.PENDING
            }
        ) {
            return true
        }
        val intention = snapshot.intentions.firstOrNull { it.id == intentionId } ?: return false
        when (intention.phase) {
            IntentionPhase.EXPLORING,
            IntentionPhase.CLARIFYING,
            IntentionPhase.PROPOSAL_PENDING -> return true
            IntentionPhase.PAUSED -> return false
            IntentionPhase.ACTIVE -> Unit
        }
        return snapshot.sessions
            .filter { it.intentionId == intentionId }
            .any { session ->
                val timing = NextActionEngine.classifySessionTiming(session, nowEpochMs, timeZone)
                timing == SessionTiming.DUE_NOW ||
                    timing == SessionTiming.APPROACHING ||
                    timing == SessionTiming.LATER_TODAY
            }
    }

    private fun relativeWhen(epochMs: Long, nowEpochMs: Long, timeZone: TimeZone): String? {
        if (epochMs <= nowEpochMs) return null
        val calWhen = Calendar.getInstance(timeZone).apply { timeInMillis = epochMs }
        val calNow = Calendar.getInstance(timeZone).apply { timeInMillis = nowEpochMs }
        val clock = "%02d:%02d".format(
            calWhen.get(Calendar.HOUR_OF_DAY),
            calWhen.get(Calendar.MINUTE)
        )
        val dayDiff = calWhen.get(Calendar.DAY_OF_YEAR) - calNow.get(Calendar.DAY_OF_YEAR) +
            365 * (calWhen.get(Calendar.YEAR) - calNow.get(Calendar.YEAR))
        return when {
            dayDiff == 0 -> "today at $clock"
            dayDiff == 1 -> "tomorrow at $clock"
            else -> {
                val dayNames = listOf(
                    "", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"
                )
                val name = dayNames.getOrElse(calWhen.get(Calendar.DAY_OF_WEEK)) { "soon" }
                "$name at $clock"
            }
        }
    }
}
