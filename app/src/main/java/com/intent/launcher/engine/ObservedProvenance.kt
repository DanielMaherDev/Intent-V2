package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.Evidence
import com.intent.launcher.domain.EvidenceAccuracy
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SessionElapsed
import com.intent.launcher.store.IntentionFlowStore

/**
 * OBSERVED provenance from UsageStats package foreground.
 * Distinct from SAID / INFERRED. Never treats observation as lesson completion
 * or auto-completes a session run.
 */
object ObservedProvenance {

    fun formatMinutes(foregroundMs: Long): Long =
        (foregroundMs.coerceAtLeast(0L) / 60_000L)

    /** Spec copy: “Observed N min in Duolingo”. */
    fun summaryText(appLabel: String, foregroundMs: Long): String {
        val minutes = formatMinutes(foregroundMs)
        return "Observed $minutes min in $appLabel"
    }

    fun checkoutNote(evidence: Evidence): String =
        "${evidence.summaryText}. Package foreground is not lesson completion — " +
            "you marked Complete yourself."

    fun sessionCardNote(evidence: Evidence): String =
        "${evidence.summaryText} (OBSERVED · package-level). Not lesson completion."

    /**
     * Build a record command when UsageStats is granted and foreground was measured.
     * Returns null when observation cannot honestly be stored as OBSERVED.
     */
    fun recordCommand(
        observation: UsageStatsProbe.Observation,
        intentionId: String,
        sessionRunId: String?,
        nowEpochMs: Long,
        evidenceId: String = "obs-$sessionRunId-$nowEpochMs",
        registry: CapabilityRegistry
    ): FlowCommand.RecordObservedEvidence? {
        if (!registry.canObserveAppUsage()) return null
        if (!observation.permissionGranted) return null
        val ms = observation.foregroundMs ?: return null
        val label = observation.appLabel ?: return null
        return FlowCommand.RecordObservedEvidence(
            evidenceId = evidenceId,
            intentionId = intentionId,
            sessionRunId = sessionRunId,
            packageName = observation.packageName,
            appLabel = label,
            foregroundMs = ms,
            summaryText = summaryText(label, ms),
            nowEpochMs = nowEpochMs
        )
    }

    /**
     * Upsert OBSERVED evidence for the active session window when UsageStats is granted.
     * Does not mutate session-run status.
     */
    fun recordIfObservable(
        store: IntentionFlowStore,
        observation: UsageStatsProbe.Observation,
        intentionId: String,
        sessionRunId: String?,
        nowEpochMs: Long,
        registry: CapabilityRegistry
    ): Evidence? {
        val stableId = sessionRunId?.let { "obs-$it" } ?: "obs-$intentionId-$nowEpochMs"
        val command = recordCommand(
            observation = observation,
            intentionId = intentionId,
            sessionRunId = sessionRunId,
            nowEpochMs = nowEpochMs,
            evidenceId = stableId,
            registry = registry
        ) ?: return null
        // RecordObservedEvidence must not mutate session-run status (covered by unit tests).
        store.apply(command)
        return store.snapshot().evidence.firstOrNull { it.id == command.evidenceId }
    }

    fun activeForIntention(snapshot: IntentionSnapshot, intentionId: String?): List<Evidence> {
        if (intentionId == null) return emptyList()
        return snapshot.evidence.filter {
            it.intentionId == intentionId &&
                it.accuracy == EvidenceAccuracy.ACTIVE &&
                it.provenance.kind == ProvenanceKind.OBSERVED
        }
    }

    fun latestActiveForRun(
        snapshot: IntentionSnapshot,
        sessionRunId: String?
    ): Evidence? {
        if (sessionRunId == null) return null
        return snapshot.evidence
            .filter {
                it.sessionRunId == sessionRunId &&
                    it.accuracy == EvidenceAccuracy.ACTIVE &&
                    it.provenance.kind == ProvenanceKind.OBSERVED
            }
            .maxByOrNull { it.recordedAtEpochMs }
    }

    /** Human-readable duration fragment for UI (reuses SessionElapsed clock style). */
    fun formatForeground(foregroundMs: Long): String =
        SessionElapsed.format(0L, foregroundMs.coerceAtLeast(0L))
}
