package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.QueuedIntervention
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType

/**
 * Gate D2 — OBSERVED vs INFERRED labels for every app-usage intervention surface.
 *
 * Wires [QueuedIntervention.provenanceKind] to [ProgressProof] / [ObservedProvenance]
 * label patterns. Never silently mixes provenance on Home / floater / Conversation.
 * App-usage interventions are OBSERVED or INFERRED only (never unlabeled SAID mix).
 */
object InterventionProvenance {

    const val HONESTY_OBSERVED =
        "OBSERVED package foreground — not Shorts/Reels identity or lesson completion."
    const val HONESTY_INFERRED =
        "INFERRED from prior context — not a live UsageStats measurement."

    data class PromptView(
        val jobId: String,
        val turnId: String,
        val intentionId: String,
        val promptText: String,
        val provenanceKind: ProvenanceKind,
        /** Always "OBSERVED" or "INFERRED" — never blank / silent mix. */
        val provenanceLabel: String,
        /** Chip-style detail, e.g. "OBSERVED · usage_stats". */
        val sourceDetail: String,
        val honestyNote: String,
        val status: String,
        val kind: String
    )

    /**
     * Intervention UI labels accept OBSERVED / INFERRED only.
     * Unexpected kinds fall back to INFERRED (honest uncertainty) rather than
     * silent unlabeled prose or a fake OBSERVED claim.
     */
    fun interventionKind(kind: ProvenanceKind): ProvenanceKind =
        when (kind) {
            ProvenanceKind.OBSERVED -> ProvenanceKind.OBSERVED
            ProvenanceKind.INFERRED,
            ProvenanceKind.SAID -> ProvenanceKind.INFERRED
        }

    fun provenanceLabel(kind: ProvenanceKind): String =
        when (interventionKind(kind)) {
            ProvenanceKind.OBSERVED -> ProgressProof.provenanceLabel(ProvenanceKind.OBSERVED)
            else -> ProgressProof.provenanceLabel(ProvenanceKind.INFERRED)
        }

    fun honestyNote(kind: ProvenanceKind): String =
        when (interventionKind(kind)) {
            ProvenanceKind.OBSERVED -> HONESTY_OBSERVED
            else -> HONESTY_INFERRED
        }

    fun sourceDetail(kind: ProvenanceKind, sourceHint: String? = null): String {
        val label = provenanceLabel(kind)
        val hint = sourceHint?.trim().orEmpty()
        return when {
            hint.isBlank() -> label
            hint.startsWith("$label ·") || hint.startsWith(label) -> hint
            else -> "$label · $hint"
        }
    }

    fun fromQueued(
        job: QueuedIntervention,
        sourceHint: String? = defaultSourceHint(job)
    ): PromptView {
        val kind = interventionKind(job.provenanceKind)
        return PromptView(
            jobId = job.jobId,
            turnId = job.turnId,
            intentionId = job.intentionId,
            promptText = job.promptText,
            provenanceKind = kind,
            provenanceLabel = provenanceLabel(kind),
            sourceDetail = sourceDetail(kind, sourceHint),
            honestyNote = honestyNote(kind),
            status = job.status,
            kind = job.kind
        )
    }

    fun activePrompt(snapshot: IntentionSnapshot): PromptView? {
        val job = TriggerCooldownPolicy.activeIntervention(snapshot) ?: return null
        return fromQueued(job)
    }

    /**
     * Ensure a QUESTION / intervention [ResponseBlock] carries an explicit
     * OBSERVED / INFERRED [ResponseBlock.sourceLabel] (never silent mix).
     */
    fun labeledQuestionBlock(
        blockId: String,
        title: String,
        body: String,
        provenanceKind: ProvenanceKind,
        sourceHint: String,
        linkedObjectIds: List<String> = emptyList(),
        actions: List<String> = emptyList()
    ): ResponseBlock {
        val kind = interventionKind(provenanceKind)
        return ResponseBlock(
            blockId = blockId,
            type = ResponseBlockType.QUESTION,
            title = title,
            body = body,
            sourceLabel = sourceDetail(kind, sourceHint),
            linkedObjectIds = linkedObjectIds,
            actions = actions
        )
    }

    /** Floater / TalkBack short line — always includes the provenance chip. */
    fun floaterAttentionLine(view: PromptView): String =
        "${view.provenanceLabel} · Needs you"

    fun homeTitle(view: PromptView): String =
        "${view.provenanceLabel} · Usage check-in"

    private fun defaultSourceHint(job: QueuedIntervention): String =
        when (interventionKind(job.provenanceKind)) {
            ProvenanceKind.OBSERVED ->
                if (job.kind == TriggerCooldownPolicy.KIND_USAGE_THRESHOLD) {
                    "usage_stats"
                } else {
                    "observation"
                }
            else -> "hypothesis"
        }
}
