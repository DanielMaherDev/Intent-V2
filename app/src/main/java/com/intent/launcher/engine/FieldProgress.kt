package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrackerModules

/**
 * Exact-field progress for Gate C — ACTIVE tracker rows only, with source labels.
 * Corrections mark prior rows CORRECTED so reports never double-count.
 */
object FieldProgress {

    data class FieldLine(
        val moduleId: String,
        val title: String,
        val activeCount: Int,
        val correctedCount: Int,
        val latest: TrackerEntry?,
        val sourceLabels: List<String>
    )

    data class View(
        val intentionId: String,
        val shapeTag: String,
        val fields: List<FieldLine>,
        val honestyNote: String
    )

    fun activeEntries(
        snapshot: IntentionSnapshot,
        intentionId: String,
        moduleId: String? = null
    ): List<TrackerEntry> =
        snapshot.trackerEntries.filter {
            it.intentionId == intentionId &&
                it.status == TrackerEntryStatus.ACTIVE &&
                (moduleId == null || it.moduleId == moduleId)
        }

    fun build(
        snapshot: IntentionSnapshot,
        intentionId: String,
        shapeTag: String
    ): View {
        val modules = TrackerModules.forShape(shapeTag)
        val fields = modules.map { def ->
            val forModule = snapshot.trackerEntries.filter {
                it.intentionId == intentionId && it.moduleId == def.id
            }
            val active = forModule.filter { it.status == TrackerEntryStatus.ACTIVE }
            val corrected = forModule.count { it.status == TrackerEntryStatus.CORRECTED }
            val latest = active.maxByOrNull { it.eventAtEpochMs }
            FieldLine(
                moduleId = def.id,
                title = def.title,
                activeCount = active.size,
                correctedCount = corrected,
                latest = latest,
                sourceLabels = active.map { e ->
                    val kind = when (e.provenance.kind) {
                        ProvenanceKind.SAID -> "SAID"
                        ProvenanceKind.OBSERVED -> "OBSERVED"
                        ProvenanceKind.INFERRED -> "INFERRED"
                    }
                    "$kind · ${e.provenance.sourceLabel}"
                }.distinct()
            )
        }
        return View(
            intentionId = intentionId,
            shapeTag = shapeTag,
            fields = fields,
            honestyNote = "Counts ACTIVE rows only — corrections supersede priors; " +
                "no invented mileage or smoke-free streaks."
        )
    }
}
