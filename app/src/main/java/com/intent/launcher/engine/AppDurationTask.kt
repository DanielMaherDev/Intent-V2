package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore

/**
 * §17 cases 5 + 32 — app-led learning / app-duration task.
 * Confirms Duolingo as approach (hold) and an explicit duration criterion with
 * foreground timing + soft-pause/resume. Never claims lesson completion.
 */
object AppDurationTask {

    const val APPROACH_FACT_ID = "fact-duolingo-approach"
    const val DURATION_FACT_ID = "fact-duolingo-duration-15"
    const val DURATION_PROPOSAL_ID = "proposal-duolingo-15min"
    const val DURATION_SESSION_ID = "session-duolingo-15min"
    const val DURATION_MINUTES = 15

    data class ApproachResult(
        val heldPackage: String?,
        val approachConfirmed: Boolean,
        val claimedLessonCompletion: Boolean,
        val honestyNote: String
    )

    data class DurationResult(
        val criterionMinutes: Int,
        val runId: String?,
        val softPaused: Boolean,
        val resumed: Boolean,
        val claimedLessonCompletion: Boolean,
        val honestyNote: String
    )

    fun looksLikeDuolingoApproach(utterance: String): Boolean {
        val t = utterance.trim().lowercase()
        return t.contains("duolingo") &&
            (t.contains("use") || t.contains("practise") || t.contains("practice") ||
                t.contains("choose") || t.contains("with"))
    }

    fun looksLikeDurationTask(utterance: String): Boolean {
        val t = utterance.trim().lowercase()
        return t.contains("duolingo") &&
            (t.contains("15") || t.contains("fifteen")) &&
            (t.contains("minute") || t.contains("min"))
    }

    fun confirmDuolingoApproach(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        utterance: String = "I'll practise with Duolingo",
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): ApproachResult {
        SpanishScenario.ensureSeeded(store, nowEpochMs)
        if (utterance.contains("lesson completion", ignoreCase = true) ||
            utterance.contains("track my duolingo lesson", ignoreCase = true)
        ) {
            SpanishScenario.onChip(store, utterance, nowEpochMs, registry)
            return ApproachResult(
                heldPackage = null,
                approachConfirmed = false,
                claimedLessonCompletion = false,
                honestyNote = registry.get(CapabilityRegistry.ID_OBSERVE_LESSON_COMPLETION)?.limitNote
                    ?: "Lesson completion is not supported."
            )
        }
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-duo-approach-user-$nowEpochMs",
                intentionId = SpanishScenario.INTENTION_ID,
                role = "user",
                text = utterance,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddUserFact(
                factId = APPROACH_FACT_ID,
                intentionId = SpanishScenario.INTENTION_ID,
                text = "Confirmed approach: Duolingo app practice (SAID) — not lesson completion.",
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.HoldAppForIntention(
                heldAppId = "held-duolingo-$nowEpochMs",
                intentionId = SpanishScenario.INTENTION_ID,
                packageName = SessionDestinations.SPANISH_PACKAGE,
                label = SessionDestinations.SPANISH_LABEL,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-duo-approach-agent-$nowEpochMs",
                intentionId = SpanishScenario.INTENTION_ID,
                role = "agent",
                text = "Duolingo confirmed as your approach. I can open it and time practice locally. " +
                    "I cannot read lesson completion without a supported source.",
                nowEpochMs = nowEpochMs + 1,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-duo-approach-$nowEpochMs",
                        type = ResponseBlockType.EXPLANATION,
                        title = "App-led approach",
                        body = "Hold Duolingo + routine option — no lesson-completion claim",
                        sourceLabel = "app_led_learning",
                        linkedObjectIds = listOf(SpanishScenario.INTENTION_ID, APPROACH_FACT_ID),
                        actions = listOf("Open Duolingo", "Set a 15-minute practice"),
                        active = true
                    )
                )
            )
        )
        val held = store.snapshot().heldApps.firstOrNull {
            it.intentionId == SpanishScenario.INTENTION_ID &&
                it.packageName == SessionDestinations.SPANISH_PACKAGE
        }
        return ApproachResult(
            heldPackage = held?.packageName,
            approachConfirmed = held != null,
            claimedLessonCompletion = false,
            honestyNote = "Approach confirmed via SAID hold — lesson completion NOT_SUPPORTED."
        )
    }

    fun startFifteenMinutePractice(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        utterance: String = "Practise in Duolingo for 15 minutes.",
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): DurationResult {
        confirmDuolingoApproach(store, nowEpochMs, utterance, registry)
        store.apply(
            FlowCommand.AddUserFact(
                factId = DURATION_FACT_ID,
                intentionId = SpanishScenario.INTENTION_ID,
                text = "Explicit duration criterion: $DURATION_MINUTES minutes in Duolingo " +
                    "(foreground timing / soft-pause) — not lesson completion.",
                nowEpochMs = nowEpochMs + 2
            )
        )
        if (registry.canProposeRecurringSession()) {
            val existing = store.snapshot().proposals.firstOrNull { it.id == DURATION_PROPOSAL_ID }
            if (existing == null) {
                store.apply(
                    FlowCommand.ProposeRecurringSession(
                        proposalId = DURATION_PROPOSAL_ID,
                        intentionId = SpanishScenario.INTENTION_ID,
                        title = "Duolingo practice ($DURATION_MINUTES min)",
                        summary = "Explicit $DURATION_MINUTES-minute Duolingo practice criterion — " +
                            "local timer + soft-pause; never claims a lesson was completed.",
                        dayOfWeek = 1,
                        hour = 18,
                        minute = 0,
                        basedOnFactIds = listOf(DURATION_FACT_ID, APPROACH_FACT_ID),
                        nowEpochMs = nowEpochMs + 3
                    )
                )
            }
        }
        val pending = store.snapshot().proposals.firstOrNull {
            it.id == DURATION_PROPOSAL_ID && it.status == ProposalStatus.PENDING
        }
        if (pending != null) {
            store.apply(
                FlowCommand.ApproveProposal(
                    proposalId = DURATION_PROPOSAL_ID,
                    sessionId = DURATION_SESSION_ID,
                    nowEpochMs = nowEpochMs + 4
                )
            )
        } else if (store.snapshot().sessions.none { it.id == DURATION_SESSION_ID }) {
            // Fallback: seed session via a one-shot approve path already handled;
            // if proposal capability locked, skip begin.
            return DurationResult(
                criterionMinutes = DURATION_MINUTES,
                runId = null,
                softPaused = false,
                resumed = false,
                claimedLessonCompletion = false,
                honestyNote = "Duration criterion recorded; session propose unavailable."
            )
        }

        val runId = "run-duo-15-$nowEpochMs"
        val dest = DestinationApp(
            packageName = SessionDestinations.SPANISH_PACKAGE,
            label = SessionDestinations.SPANISH_LABEL
        )
        store.apply(
            FlowCommand.BeginSession(
                runId = runId,
                sessionId = DURATION_SESSION_ID,
                nowEpochMs = nowEpochMs + 5,
                affinityPackageName = dest.packageName,
                affinityAppLabel = dest.label
            )
        )
        store.apply(
            FlowCommand.SoftPauseSessionRun(
                runId = runId,
                nowEpochMs = nowEpochMs + 6
            )
        )
        val paused = store.snapshot().sessionRuns.first { it.id == runId }
        store.apply(
            FlowCommand.ResumeSessionRun(
                runId = runId,
                nowEpochMs = nowEpochMs + 7
            )
        )
        val resumed = store.snapshot().sessionRuns.first { it.id == runId }
        return DurationResult(
            criterionMinutes = DURATION_MINUTES,
            runId = runId,
            softPaused = paused.pausedAtEpochMs != null &&
                paused.status == SessionRunStatus.IN_PROGRESS,
            resumed = resumed.pausedAtEpochMs == null &&
                resumed.status == SessionRunStatus.IN_PROGRESS,
            claimedLessonCompletion = false,
            honestyNote = "15-min criterion + soft-pause/resume — no lesson-completion claim."
        )
    }
}
