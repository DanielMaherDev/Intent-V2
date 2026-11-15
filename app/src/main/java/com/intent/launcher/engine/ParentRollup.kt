package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.TrackerEntryStatus

/**
 * §17 case 29 — parent report / rollup.
 * Aggregates ACTIVE child tracker entries with child + source labels.
 * Never double-counts: each entry id appears once; CORRECTED/DELETED excluded;
 * parent-owned copies of the same entry id are not invented.
 */
object ParentRollup {

    data class Contribution(
        val entryId: String,
        val childIntentionId: String,
        val childTitle: String,
        val moduleId: String,
        val valueText: String,
        val provenanceLabel: String,
        val sourceLabel: String,
        val eventAtEpochMs: Long
    )

    data class View(
        val parentIntentionId: String,
        val parentTitle: String,
        val childCount: Int,
        val contributions: List<Contribution>,
        val saidCount: Int,
        val observedCount: Int,
        val inferredCount: Int,
        val honestyNote: String
    )

    fun build(snapshot: IntentionSnapshot, parentIntentionId: String): View? {
        val parent = snapshot.intentions.firstOrNull { it.id == parentIntentionId } ?: return null
        val children = IntentionHierarchy.childrenOf(snapshot, parentIntentionId)
        val childIds = children.map { it.id }.toSet()
        val seenEntryIds = mutableSetOf<String>()
        val contributions = mutableListOf<Contribution>()
        for (child in children) {
            snapshot.trackerEntries
                .filter {
                    it.intentionId == child.id &&
                        it.status == TrackerEntryStatus.ACTIVE &&
                        seenEntryIds.add(it.id)
                }
                .forEach { e ->
                    contributions += Contribution(
                        entryId = e.id,
                        childIntentionId = child.id,
                        childTitle = child.title,
                        moduleId = e.moduleId,
                        valueText = e.valueText,
                        provenanceLabel = ProgressProof.provenanceLabel(e.provenance.kind),
                        sourceLabel = e.provenance.sourceLabel,
                        eventAtEpochMs = e.eventAtEpochMs
                    )
                }
        }
        // Explicitly ignore parent-scoped rows that merely copy a child entry id.
        val parentDupes = snapshot.trackerEntries.count { e ->
            e.intentionId == parentIntentionId &&
                e.status == TrackerEntryStatus.ACTIVE &&
                e.id in contributions.map { it.entryId }.toSet()
        }
        contributions.sortByDescending { it.eventAtEpochMs }
        return View(
            parentIntentionId = parent.id,
            parentTitle = parent.title,
            childCount = children.size,
            contributions = contributions,
            saidCount = contributions.count { it.provenanceLabel == "SAID" },
            observedCount = contributions.count { it.provenanceLabel == "OBSERVED" },
            inferredCount = contributions.count { it.provenanceLabel == "INFERRED" },
            honestyNote = buildString {
                append("Rollup lists each ACTIVE child entry once with child + source labels. ")
                append("CORRECTED/DELETED rows excluded. ")
                if (parentDupes > 0) {
                    append("Ignored $parentDupes parent-scoped duplicate id(s) to prevent double counting. ")
                }
                if (childIds.isEmpty()) {
                    append("No local children — empty rollup, not invented.")
                }
            }
        )
    }
}
