package com.intent.launcher.engine

import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.SessionElapsed
import com.intent.launcher.domain.SessionRun
import com.intent.launcher.domain.SessionRunStatus

/**
 * OS-166–167 — soft-pause an app-linked session when UsageStats shows the
 * foreground package is not the session affinity for longer than [AWAY_THRESHOLD_MS].
 *
 * Never marks Complete / Abandon. Pause freezes local elapsed only; it does not
 * invent lesson progress or OBSERVED evidence.
 */
object AppLinkedSessionPause {

    /** Seconds away from affinity before soft-pause fires. */
    const val AWAY_THRESHOLD_MS: Long = 15_000L

    data class Evaluation(
        /** Soft-paused (elapsed frozen). */
        val softPaused: Boolean,
        /** Affinity package for this run, if any. */
        val affinityPackageName: String?,
        val affinityAppLabel: String?,
        /** Current foreground package from UsageStats, when readable. */
        val foregroundPackageName: String?,
        /** True when UsageStats permission is present. */
        val usageStatsGranted: Boolean,
        /** Honest UI note — never claims completion. */
        val note: String,
        /** Soft-paused elapsed for display (accounts for pause accumulation). */
        val elapsedLabel: String,
        val elapsedText: String,
        /** Offer Resume when soft-paused. */
        val canResume: Boolean,
        /** Auto soft-pause should be applied via FlowCommand. */
        val shouldAutoPause: Boolean,
        /** Auto resume when affinity is foreground again. */
        val shouldAutoResume: Boolean
    )

    fun resolveAffinity(
        run: SessionRun?,
        destination: DestinationApp?
    ): Pair<String?, String?> {
        val pkg = run?.affinityPackageName ?: destination?.packageName
        val label = run?.affinityAppLabel ?: destination?.label
        return pkg to label
    }

    /**
     * Evaluate pause honesty for an in-progress run.
     * Does not mutate store — caller applies SoftPause / Resume commands.
     */
    fun evaluate(
        access: UsageStatsAccess,
        run: SessionRun?,
        destination: DestinationApp?,
        nowEpochMs: Long,
        awayThresholdMs: Long = AWAY_THRESHOLD_MS
    ): Evaluation {
        if (run == null || run.status != SessionRunStatus.IN_PROGRESS) {
            return idle(
                note = "No active session to pause.",
                nowEpochMs = nowEpochMs,
                run = run
            )
        }
        val (affinityPkg, affinityLabel) = resolveAffinity(run, destination)
        if (affinityPkg.isNullOrBlank()) {
            return Evaluation(
                softPaused = run.isSoftPaused,
                affinityPackageName = null,
                affinityAppLabel = null,
                foregroundPackageName = null,
                usageStatsGranted = access.hasPermission(),
                note = "No package affinity for this session — timer uses local clock only. " +
                    "Away-from-app pause is unavailable.",
                elapsedLabel = SessionElapsed.labelForRun(run, nowEpochMs),
                elapsedText = SessionElapsed.formatForRun(run, nowEpochMs),
                canResume = run.isSoftPaused,
                shouldAutoPause = false,
                shouldAutoResume = false
            )
        }
        if (!access.hasPermission()) {
            return Evaluation(
                softPaused = run.isSoftPaused,
                affinityPackageName = affinityPkg,
                affinityAppLabel = affinityLabel,
                foregroundPackageName = null,
                usageStatsGranted = false,
                note = "Usage access not granted — away-from-app pause is unavailable; " +
                    "timer keeps the local clock. Granting UsageStats only detects package " +
                    "foreground for ${affinityLabel ?: affinityPkg}, not lesson completion.",
                elapsedLabel = SessionElapsed.labelForRun(run, nowEpochMs),
                elapsedText = SessionElapsed.formatForRun(run, nowEpochMs),
                canResume = run.isSoftPaused,
                shouldAutoPause = false,
                shouldAutoResume = false
            )
        }
        val foreground = access.currentForegroundPackage()
        val inAffinity = foreground != null && foreground == affinityPkg
        val softPaused = run.isSoftPaused

        if (inAffinity) {
            return Evaluation(
                softPaused = softPaused,
                affinityPackageName = affinityPkg,
                affinityAppLabel = affinityLabel,
                foregroundPackageName = foreground,
                usageStatsGranted = true,
                note = if (softPaused) {
                    "Back in ${affinityLabel ?: affinityPkg} — Resume to unfreeze elapsed. " +
                        "Package presence is not lesson completion."
                } else {
                    "In ${affinityLabel ?: affinityPkg} (package-level). " +
                        "Foreground time is not lesson completion — Complete yourself when finished."
                },
                elapsedLabel = SessionElapsed.labelForRun(run, nowEpochMs),
                elapsedText = SessionElapsed.formatForRun(run, nowEpochMs),
                canResume = softPaused,
                shouldAutoPause = false,
                shouldAutoResume = softPaused
            )
        }

        // Away from affinity — use UsageStats last-leave when available, else wall clock.
        val awaySince = access.lastTimePackageLeftForeground(
            packageName = affinityPkg,
            windowStartEpochMs = run.startedAtEpochMs,
            endEpochMs = nowEpochMs
        ) ?: (nowEpochMs - awayThresholdMs - 1L)
        val awayMs = (nowEpochMs - awaySince).coerceAtLeast(0L)
        val longEnough = awayMs >= awayThresholdMs

        return Evaluation(
            softPaused = softPaused || longEnough,
            affinityPackageName = affinityPkg,
            affinityAppLabel = affinityLabel,
            foregroundPackageName = foreground,
            usageStatsGranted = true,
            note = if (softPaused || longEnough) {
                "Away from ${affinityLabel ?: affinityPkg} — elapsed paused. " +
                    "Resume when you return, or Complete / Abandon yourself. " +
                    "Away pause is not lesson completion."
            } else {
                "Left ${affinityLabel ?: affinityPkg} briefly — pause arms after " +
                    "${awayThresholdMs / 1000L}s away. Package presence is not lesson completion."
            },
            elapsedLabel = SessionElapsed.labelForRun(run, nowEpochMs),
            elapsedText = SessionElapsed.formatForRun(run, nowEpochMs),
            canResume = softPaused || longEnough,
            shouldAutoPause = !softPaused && longEnough,
            shouldAutoResume = false
        )
    }

    private fun idle(note: String, nowEpochMs: Long, run: SessionRun?): Evaluation =
        Evaluation(
            softPaused = run?.isSoftPaused == true,
            affinityPackageName = run?.affinityPackageName,
            affinityAppLabel = run?.affinityAppLabel,
            foregroundPackageName = null,
            usageStatsGranted = false,
            note = note,
            elapsedLabel = if (run != null) SessionElapsed.labelForRun(run, nowEpochMs) else "Elapsed —",
            elapsedText = if (run != null) SessionElapsed.formatForRun(run, nowEpochMs) else "—",
            canResume = false,
            shouldAutoPause = false,
            shouldAutoResume = false
        )
}

/** True when [SessionRun.pausedAtEpochMs] is set (soft-paused, still IN_PROGRESS). */
val SessionRun.isSoftPaused: Boolean
    get() = pausedAtEpochMs != null && status == SessionRunStatus.IN_PROGRESS
