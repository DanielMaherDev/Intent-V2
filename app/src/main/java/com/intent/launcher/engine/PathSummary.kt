package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrackerModules

/**
 * PathSummary for an intention — phase, status, next action, tracker modules.
 * Reads the same intention store; never invents partner completion charts.
 */
object PathSummary {

    data class ModuleLine(
        val moduleId: String,
        val title: String,
        val activeEntryCount: Int
    )

    data class View(
        val intentionId: String,
        val title: String,
        val shapeTag: String,
        val phaseLabel: String,
        val statusLine: String,
        val nextActionLabel: String,
        val nextActionReason: String,
        val scheduledSessionCount: Int,
        val completedRunCount: Int,
        val abandonedRunCount: Int,
        val modules: List<ModuleLine>,
        val honestyNote: String
    )

    fun phaseLabel(phase: IntentionPhase): String =
        when (phase) {
            IntentionPhase.EXPLORING -> "Exploring"
            IntentionPhase.CLARIFYING -> "Clarifying"
            IntentionPhase.PROPOSAL_PENDING -> "Proposal pending"
            IntentionPhase.ACTIVE -> "Active"
            IntentionPhase.PAUSED -> "Paused"
        }

    fun build(
        snapshot: IntentionSnapshot,
        intention: Intention?,
        nextAction: NextAction,
        @Suppress("UNUSED_PARAMETER") nowEpochMs: Long = System.currentTimeMillis(),
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): View? {
        if (intention == null) return null
        val id = intention.id
        val modules = TrackerModules.forShape(intention.shapeTag).map { def ->
            ModuleLine(
                moduleId = def.id,
                title = def.title,
                activeEntryCount = snapshot.trackerEntries.count {
                    it.intentionId == id &&
                        it.moduleId == def.id &&
                        it.status == TrackerEntryStatus.ACTIVE
                }
            )
        }
        val runs = snapshot.sessionRuns.filter { it.intentionId == id }
        val canReport = registry.isActionable(CapabilityRegistry.ID_PROGRESS_REPORTS)
        return View(
            intentionId = id,
            title = intention.title,
            shapeTag = intention.shapeTag,
            phaseLabel = phaseLabel(intention.phase),
            statusLine = when {
                runs.any { it.status == SessionRunStatus.IN_PROGRESS } ->
                    "Session in progress"
                intention.phase == IntentionPhase.ACTIVE ->
                    "On the path — local schedule only"
                else -> "Building understanding"
            },
            nextActionLabel = nextAction.label.ifBlank { nextAction.kind.name },
            nextActionReason = nextAction.reason,
            scheduledSessionCount = snapshot.sessions.count { it.intentionId == id },
            completedRunCount = runs.count { it.status == SessionRunStatus.COMPLETED },
            abandonedRunCount = runs.count { it.status == SessionRunStatus.ABANDONED },
            modules = modules,
            honestyNote = if (canReport) {
                registry.get(CapabilityRegistry.ID_PROGRESS_REPORTS)?.limitNote
                    ?: "Progress uses SAID / OBSERVED / INFERRED labels — no fabricated charts."
            } else {
                registry.get(CapabilityRegistry.ID_PROGRESS_REPORTS)?.limitNote
                    ?: "Progress reports are unavailable in this registry."
            }
        )
    }
}
