package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrackerModules
import java.util.concurrent.TimeUnit

/**
 * Progress proof — labeled SAID / OBSERVED / INFERRED mix from the intention store.
 * Never silently mixes provenance or fabricates completion charts.
 */
object ProgressProof {

    data class LabeledItem(
        val id: String,
        val provenanceLabel: String,
        val kind: ProvenanceKind,
        val text: String,
        val recordedAtEpochMs: Long,
        val sourceDetail: String
    )

    data class DayBucket(
        val dayStartEpochMs: Long,
        val label: String,
        val items: List<LabeledItem>
    )

    data class View(
        val intentionId: String,
        val items: List<LabeledItem>,
        val saidCount: Int,
        val observedCount: Int,
        val inferredCount: Int,
        val dayBuckets: List<DayBucket>,
        val weekBuckets: List<DayBucket>,
        val emptyState: String?,
        val honestyNote: String
    )

    fun provenanceLabel(kind: ProvenanceKind): String =
        when (kind) {
            ProvenanceKind.SAID -> "SAID"
            ProvenanceKind.OBSERVED -> "OBSERVED"
            ProvenanceKind.INFERRED -> "INFERRED"
        }

    fun build(
        snapshot: IntentionSnapshot,
        intentionId: String?,
        nowEpochMs: Long,
        shapeTag: String? = null,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): View? {
        if (intentionId == null) return null
        val items = mutableListOf<LabeledItem>()

        snapshot.facts.filter { it.intentionId == intentionId }.forEach { f ->
            items += LabeledItem(
                id = f.id,
                provenanceLabel = provenanceLabel(f.provenance.kind),
                kind = f.provenance.kind,
                text = f.text,
                recordedAtEpochMs = f.provenance.learnedAtEpochMs,
                sourceDetail = "Fact · ${f.provenance.sourceLabel}"
            )
        }
        snapshot.hypotheses.filter { it.intentionId == intentionId }.forEach { h ->
            items += LabeledItem(
                id = h.id,
                provenanceLabel = provenanceLabel(h.provenance.kind),
                kind = h.provenance.kind,
                text = h.text,
                recordedAtEpochMs = h.provenance.learnedAtEpochMs,
                sourceDetail = "Hypothesis · ${h.provenance.sourceLabel}"
            )
        }
        snapshot.trackerEntries
            .filter {
                it.intentionId == intentionId && it.status == TrackerEntryStatus.ACTIVE
            }
            .forEach { e ->
                val module = TrackerModules.moduleTitle(e.moduleId, shapeTag)
                items += LabeledItem(
                    id = e.id,
                    provenanceLabel = provenanceLabel(e.provenance.kind),
                    kind = e.provenance.kind,
                    text = e.valueText,
                    recordedAtEpochMs = e.recordedAtEpochMs,
                    sourceDetail = "Tracker · $module · ${e.provenance.sourceLabel}"
                )
            }

        items.sortByDescending { it.recordedAtEpochMs }

        val said = items.count { it.kind == ProvenanceKind.SAID }
        val observed = items.count { it.kind == ProvenanceKind.OBSERVED }
        val inferred = items.count { it.kind == ProvenanceKind.INFERRED }

        val dayMs = TimeUnit.DAYS.toMillis(1)
        val weekMs = TimeUnit.DAYS.toMillis(7)
        val dayStart = startOfUtcDay(nowEpochMs)
        val weekStart = dayStart - 6 * dayMs

        val dayItems = items.filter { it.recordedAtEpochMs >= dayStart }
        val weekItems = items.filter { it.recordedAtEpochMs >= weekStart }

        val empty = when {
            items.isEmpty() ->
                "No progress proof yet. Add a tracker entry (SAID), or wait for OBSERVED " +
                    "package rows when UsageStats evidence is recorded. Partner lesson charts " +
                    "are not available."
            observed == 0 ->
                null // still show mixed list; honesty note covers missing OBSERVED
            else -> null
        }

        val limit = registry.get(CapabilityRegistry.ID_PROGRESS_REPORTS)?.limitNote
            ?: "Labels every row SAID / OBSERVED / INFERRED — no fabricated completion charts."

        return View(
            intentionId = intentionId,
            items = items,
            saidCount = said,
            observedCount = observed,
            inferredCount = inferred,
            dayBuckets = listOf(
                DayBucket(dayStart, "Today", dayItems)
            ),
            weekBuckets = listOf(
                DayBucket(weekStart, "Last 7 days", weekItems)
            ),
            emptyState = empty,
            honestyNote = buildString {
                append(limit)
                if (observed == 0) {
                    append(" No OBSERVED rows in store yet")
                    append(" (UsageStats / partner adapters do not invent completion).")
                }
            }
        )
    }

    private fun startOfUtcDay(epochMs: Long): Long {
        val dayMs = TimeUnit.DAYS.toMillis(1)
        return epochMs - (epochMs % dayMs)
    }
}
