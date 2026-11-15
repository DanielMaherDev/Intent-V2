package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.AppUseReductionScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.QueuedIntervention
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone

/**
 * Gate D — permission-aware usage observation → at most one queued intervention
 * after quiet-hours / cooldown / interruption-budget checks (worksheet scenario C).
 *
 * Never claims Shorts/Reels identity. Prompt provenance is OBSERVED package time.
 */
object UsageInterventionTrigger {

    data class Result(
        val queued: QueuedIntervention?,
        val suppressedReason: String? = null
    )

    /**
     * Evaluate day-window Instagram+YouTube package foreground against the
     * app-use daily target and queue one intervention when policy allows.
     */
    fun maybeQueueFromDayUsage(
        store: IntentionFlowStore,
        access: UsageStatsAccess,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default,
        timeZone: TimeZone = TimeZone.getDefault(),
        intentionId: String = AppUseReductionScenario.INTENTION_ID,
        targetMinutes: Int = AppUseReductionScenario.DAILY_TARGET_MINUTES
    ): Result {
        if (!registry.canObserveAppUsage()) {
            return Result(queued = null, suppressedReason = "observe_unavailable")
        }
        if (!access.hasPermission()) {
            return Result(queued = null, suppressedReason = "permission_denied")
        }
        val snap = store.snapshot()
        if (snap.intentions.none { it.id == intentionId }) {
            return Result(queued = null, suppressedReason = "no_intention")
        }
        val dayStart = DailyCycle.startOfLocalDayEpochMs(nowEpochMs, timeZone)
        val igMs = access.foregroundMs(
            packageName = SessionDestinations.INSTAGRAM_PACKAGE,
            startEpochMs = dayStart,
            endEpochMs = nowEpochMs
        ) ?: 0L
        val ytMs = access.foregroundMs(
            packageName = SessionDestinations.YOUTUBE_PACKAGE,
            startEpochMs = dayStart,
            endEpochMs = nowEpochMs
        ) ?: 0L
        val totalMs = igMs + ytMs
        if (!ObservedProvenance.meetsOrExceedsTargetMinutes(totalMs, targetMinutes)) {
            return Result(queued = null, suppressedReason = "under_target")
        }
        val minutes = ObservedProvenance.formatMinutes(totalMs)
        val triggerKey = TriggerCooldownPolicy.usageThresholdKey(intentionId)
        val decision = TriggerCooldownPolicy.evaluate(
            snapshot = snap,
            triggerKey = triggerKey,
            nowEpochMs = nowEpochMs,
            timeZone = timeZone
        )
        if (decision is TriggerCooldownPolicy.Decision.Suppress) {
            return Result(queued = null, suppressedReason = decision.reason)
        }
        val jobId = "job-usage-$intentionId-$nowEpochMs"
        val turnId = "turn-intervention-usage-$intentionId-$nowEpochMs"
        val prompt =
            "Instagram and YouTube packages have been in the foreground for about $minutes min today " +
                "(combined package time — not Shorts/Reels identity). Want to review, or leave you alone?"
        val block = ResponseBlock(
            blockId = "block-usage-q-$nowEpochMs",
            type = ResponseBlockType.QUESTION,
            title = "Usage check-in",
            body = prompt,
            sourceLabel = "OBSERVED · usage_stats",
            linkedObjectIds = listOf(intentionId),
            actions = listOf("review", "leave_me_alone", "dismiss")
        )
        store.apply(
            FlowCommand.QueueIntervention(
                jobId = jobId,
                turnId = turnId,
                intentionId = intentionId,
                triggerKey = triggerKey,
                kind = TriggerCooldownPolicy.KIND_USAGE_THRESHOLD,
                promptText = prompt,
                nowEpochMs = nowEpochMs,
                evidenceId = null,
                provenanceKind = ProvenanceKind.OBSERVED,
                agentTurnText = prompt,
                agentTurnBlocks = listOf(block)
            )
        )
        val queued = store.snapshot().queuedInterventions.firstOrNull { it.jobId == jobId }
        return Result(queued = queued)
    }
}
