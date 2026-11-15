package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.ApproachStep
import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.HeldApp
import com.intent.launcher.domain.Hypothesis
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.ShapeProjection
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrackerModules

/**
 * OS-142 — coherent intention-detail read model.
 *
 * Surfaces next action, active paths, plan, track, reports, and resources from the
 * existing registry / path / projection stack. Never invents partner completion data
 * or child intentions that are not in the store.
 */
object IntentionDetailCoherence {

    data class PathLine(
        val title: String,
        val detail: String
    )

    data class TrackLine(
        val moduleId: String,
        val title: String,
        val activeEntryCount: Int
    )

    data class ResourceLine(
        val title: String,
        val detail: String
    )

    data class View(
        /** Recommended now — same [NextAction] Home uses. */
        val nextActionLabel: String,
        val nextActionReason: String,
        val nextActionDetail: String,
        /** Phase / schedule path status — no invented children. */
        val activePathsTitle: String,
        val activePathLines: List<PathLine>,
        val activePathsEmptyNote: String?,
        /** Approach / plan steps from [ShapeProjection], or honest empty. */
        val planTitle: String,
        val planSteps: List<ApproachStep>,
        val planEmptyNote: String?,
        /** Shape tracker modules + entry counts. */
        val trackTitle: String,
        val trackLines: List<TrackLine>,
        val trackEmptyNote: String?,
        val trackHonestyNote: String,
        val canOpenTracker: Boolean,
        /** Path summary + labeled proof counts. */
        val reportsTitle: String,
        val reportsSummaryLine: String,
        val reportsProofLine: String,
        val reportsHonestyNote: String,
        val canOpenReports: Boolean,
        /** Destination + held apps + connection candidates. */
        val resourcesTitle: String,
        val resourceLines: List<ResourceLine>,
        val resourcesEmptyNote: String?,
        val resourcesHonestyNote: String
    )

    fun build(
        snapshot: IntentionSnapshot,
        intention: Intention?,
        nextAction: NextAction,
        facts: List<Fact>,
        hypotheses: List<Hypothesis>,
        sessions: List<ScheduledSession>,
        heldApps: List<HeldApp>,
        connectionUsages: List<AppConnectionUsage.ConnectionUsage>,
        nowEpochMs: Long = System.currentTimeMillis(),
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): View? {
        if (intention == null) return null
        val id = intention.id
        val projection = ShapeProjection.project(
            intention = intention,
            facts = facts,
            hypotheses = hypotheses,
            sessions = sessions,
            destination = SessionDestinations.forIntention(intention)
        )
        val path = PathSummary.build(
            snapshot = snapshot,
            intention = intention,
            nextAction = nextAction,
            nowEpochMs = nowEpochMs,
            registry = registry
        )
        val proof = ProgressProof.build(
            snapshot = snapshot,
            intentionId = id,
            nowEpochMs = nowEpochMs,
            shapeTag = intention.shapeTag,
            registry = registry
        )
        val destination = SessionDestinations.forIntention(intention)
        val modules = TrackerModules.forShape(intention.shapeTag)
        val trackLines = modules.map { def ->
            TrackLine(
                moduleId = def.id,
                title = def.title,
                activeEntryCount = snapshot.trackerEntries.count {
                    it.intentionId == id &&
                        it.moduleId == def.id &&
                        it.status == TrackerEntryStatus.ACTIVE
                }
            )
        }
        val pathLines = buildPathLines(path, sessions, intention)
        val planSteps = projection.approachSteps
        val resourceLines = buildResourceLines(
            projectionLine = projection.resourceLine,
            destination = destination,
            heldApps = heldApps,
            connectionUsages = connectionUsages
        )
        val canTrack = registry.isActionable(CapabilityRegistry.ID_TRACKER_MODULES)
        val canReport = registry.isActionable(CapabilityRegistry.ID_PROGRESS_REPORTS)
        return View(
            nextActionLabel = nextAction.label.ifBlank { nextAction.kind.name },
            nextActionReason = nextAction.reason,
            nextActionDetail = nextAction.detail,
            activePathsTitle = "Active paths",
            activePathLines = pathLines,
            activePathsEmptyNote = if (pathLines.isEmpty()) {
                "No child paths yet. This intention is the path — parent/child navigation is separate."
            } else {
                null
            },
            planTitle = projection.approachSectionTitle ?: "Plan",
            planSteps = planSteps,
            planEmptyNote = when {
                planSteps.isNotEmpty() -> null
                projection.emptyNote != null -> projection.emptyNote
                else -> "No plan committed yet. Exploring stays useful without invented tasks."
            },
            trackTitle = "Track",
            trackLines = trackLines,
            trackEmptyNote = if (trackLines.isEmpty()) {
                "No tracker modules for this shape yet."
            } else {
                null
            },
            trackHonestyNote = registry.get(CapabilityRegistry.ID_TRACKER_MODULES)?.limitNote
                ?: "Manual SAID entries only — not partner lesson completion.",
            canOpenTracker = canTrack,
            reportsTitle = "Reports",
            reportsSummaryLine = path?.let {
                "${it.phaseLabel} · ${it.statusLine} · " +
                    "${it.scheduledSessionCount} scheduled · " +
                    "${it.completedRunCount} completed · " +
                    "${it.abandonedRunCount} abandoned"
            } ?: "No path summary for this intention.",
            reportsProofLine = proof?.let {
                "Proof · ${it.saidCount} SAID · ${it.observedCount} OBSERVED · ${it.inferredCount} INFERRED"
            } ?: "No labeled proof rows yet.",
            reportsHonestyNote = path?.honestyNote
                ?: registry.get(CapabilityRegistry.ID_PROGRESS_REPORTS)?.limitNote
                ?: "Progress uses SAID / OBSERVED / INFERRED labels — no fabricated charts.",
            canOpenReports = canReport,
            resourcesTitle = "Resources",
            resourceLines = resourceLines,
            resourcesEmptyNote = if (resourceLines.isEmpty()) {
                "No destination, held apps, or connection candidates yet. Partner feeds stay empty."
            } else {
                null
            },
            resourcesHonestyNote =
                "Opening an app is not lesson completion. Partner OAuth feeds are not supported."
        )
    }

    private fun buildPathLines(
        path: PathSummary.View?,
        sessions: List<ScheduledSession>,
        intention: Intention
    ): List<PathLine> {
        val lines = mutableListOf<PathLine>()
        if (path != null) {
            lines += PathLine(
                title = path.statusLine,
                detail = "${path.phaseLabel} · local schedule only — no partner path invent"
            )
        }
        sessions.forEach { s ->
            val whenLabel = "dow ${s.dayOfWeek} · %02d:%02d".format(s.hour, s.minute)
            lines += PathLine(
                title = s.title,
                detail = "Scheduled · $whenLabel · ${intention.title}"
            )
        }
        return lines
    }

    private fun buildResourceLines(
        projectionLine: String?,
        destination: DestinationApp?,
        heldApps: List<HeldApp>,
        connectionUsages: List<AppConnectionUsage.ConnectionUsage>
    ): List<ResourceLine> {
        val lines = mutableListOf<ResourceLine>()
        when {
            projectionLine != null ->
                lines += ResourceLine(title = "Destination", detail = projectionLine)
            destination != null ->
                lines += ResourceLine(
                    title = destination.label,
                    detail = "Package ${destination.packageName} — opening ≠ completion"
                )
        }
        heldApps.forEach { held ->
            lines += ResourceLine(
                title = held.label,
                detail = "Held · SAID · ${held.packageName}"
            )
        }
        connectionUsages.forEach { usage ->
            val status = if (usage.connection.installed) {
                "Installed · ${usage.connection.accountStatus}"
            } else {
                "Not installed · ${usage.connection.accountStatus}"
            }
            val contrib = when {
                usage.intentions.isNotEmpty() ->
                    usage.intentions.joinToString(" · ") { it.detail }
                usage.emptyNote != null -> usage.emptyNote
                else -> "No SAID / OBSERVED contribution yet"
            }
            lines += ResourceLine(
                title = usage.connection.label,
                detail = "$status · $contrib"
            )
        }
        return lines
    }
}
