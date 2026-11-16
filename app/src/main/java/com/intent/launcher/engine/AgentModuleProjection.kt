package com.intent.launcher.engine

import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.Proposal
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.TrackerEntryStatus

/**
 * Pure Agent-module card projections for Conversation UI.
 * Maps existing store / [ResponseBlock] / [NextAction] / [Proposal] data into
 * mockup-shaped models — never invents OBSERVED facts or LLM completions.
 */
object AgentModuleProjection {

    data class NotedBar(
        val text: String,
        val sourceLabel: String
    )

    data class NextForYouCard(
        val title: String,
        val body: String,
        val provenanceChip: String,
        val metaPrimary: String,
        val metaSecondary: String,
        val beginLabel: String,
        val laterLabel: String,
        val discussLabel: String,
        val blockId: String?,
        val fromBlock: Boolean
    )

    data class OneQuestionCard(
        val title: String,
        val body: String,
        val provenanceChip: String,
        val options: List<String>,
        val freeTextHint: String,
        val blockId: String?,
        val sourceLabel: String
    )

    data class ProposalCard(
        val title: String,
        val body: String,
        val scheduleLine: String,
        val durationLine: String,
        val linkedIntentionLine: String,
        val honestyNote: String,
        val reviewLabel: String,
        val dismissLabel: String,
        val proposalId: String?,
        val blockId: String?,
        val sourceLabel: String
    )

    data class RecordedEntryCard(
        val title: String,
        val subtitle: String,
        val valueLine: String,
        val sourceLabel: String,
        val editLabel: String,
        val undoLabel: String,
        val blockId: String?,
        val entryId: String?
    )

    data class ReportPreviewCard(
        val title: String,
        val rangeLine: String,
        val primaryMetric: String,
        val trendLine: String?,
        val statLeft: String,
        val statRight: String,
        val footer: String,
        val openLabel: String,
        val provenanceChip: String,
        val blockId: String?
    )

    /** Honest EXPLANATION card — title/body/source/actions from the block only. */
    data class ExplanationCard(
        val title: String,
        val body: String,
        val sourceLabel: String,
        val actions: List<String>,
        val blockId: String?
    )

    /**
     * Honest PROGRESS card — maps the PROGRESS [ResponseBlock] only.
     * Does not invent OBSERVED metrics or chart series from empty stores.
     */
    data class ProgressCard(
        val title: String,
        val body: String,
        val provenanceChip: String,
        val sourceLabel: String,
        val actions: List<String>,
        val honestyNote: String,
        val blockId: String?
    )

    private val DAY_NAMES =
        listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    fun notedBarFromSaidFact(fact: Fact?): NotedBar? {
        if (fact == null) return null
        if (fact.provenance.kind != ProvenanceKind.SAID) return null
        val text = fact.text.trim()
        if (text.isEmpty()) return null
        return NotedBar(
            text = "NOTED · $text",
            sourceLabel = fact.provenance.sourceLabel
        )
    }

    fun nextForYouFromBlock(
        block: ResponseBlock,
        intentionTitle: String
    ): NextForYouCard? {
        if (block.type != ResponseBlockType.NEXT_ACTION || !block.active) return null
        return NextForYouCard(
            title = block.title.ifBlank { "Next useful step" },
            body = block.body,
            provenanceChip = block.sourceLabel.ifBlank { "From your intention" },
            metaPrimary = block.actions.getOrNull(0).orEmpty(),
            metaSecondary = intentionTitle,
            beginLabel = block.actions.getOrNull(1) ?: "Begin now",
            laterLabel = block.actions.getOrNull(2) ?: "Later",
            discussLabel = block.actions.getOrNull(3) ?: "Discuss",
            blockId = block.blockId,
            fromBlock = true
        )
    }

    /**
     * Project Home [NextAction] BEGIN into the Conversation "NEXT FOR YOU" card.
     * Uses only engine fields — no invented schedule details.
     */
    fun nextForYouFromNextAction(
        next: NextAction,
        intentionTitle: String
    ): NextForYouCard? {
        if (next.kind != NextActionKind.BEGIN) return null
        return NextForYouCard(
            title = next.label.ifBlank { "Next useful step" },
            body = next.detail,
            provenanceChip = next.reason.ifBlank { "From your approved schedule" },
            metaPrimary = next.beginLabel?.takeIf { it.isNotBlank() } ?: "Ready when you are",
            metaSecondary = intentionTitle,
            beginLabel = next.beginLabel?.takeIf { it.isNotBlank() } ?: "Begin now",
            laterLabel = next.deferLabel?.takeIf { it.isNotBlank() } ?: "Later",
            discussLabel = next.discussLabel?.takeIf { it.isNotBlank() } ?: "Discuss",
            blockId = null,
            fromBlock = false
        )
    }

    fun oneQuestionFromBlock(block: ResponseBlock): OneQuestionCard? {
        when (block.type) {
            ResponseBlockType.CHOICE_SET -> {
                if (!block.active || block.actions.isEmpty()) return null
                return OneQuestionCard(
                    title = block.title.ifBlank { "One question" },
                    body = block.body,
                    provenanceChip = "ONE QUESTION",
                    options = block.actions,
                    freeTextHint = "Tell me in your own words",
                    blockId = block.blockId,
                    sourceLabel = block.sourceLabel
                )
            }
            ResponseBlockType.QUESTION -> {
                if (!block.active) return null
                return OneQuestionCard(
                    title = block.title.ifBlank { "One question" },
                    body = block.body,
                    provenanceChip = interventionProvenanceChip(block.sourceLabel, block.title),
                    options = block.actions,
                    freeTextHint = "Tell me in your own words",
                    blockId = block.blockId,
                    sourceLabel = block.sourceLabel
                )
            }
            else -> return null
        }
    }

    /** Exploring chips as a ONE QUESTION card — labels only, no invented answers. */
    fun oneQuestionFromChips(
        chips: List<String>,
        headline: String,
        body: String
    ): OneQuestionCard? {
        if (chips.isEmpty()) return null
        return OneQuestionCard(
            title = headline.ifBlank { "Let's shape how you practise." },
            body = body,
            provenanceChip = "ONE QUESTION",
            options = chips,
            freeTextHint = "Tell me in your own words",
            blockId = null,
            sourceLabel = "Local heuristics"
        )
    }

    fun proposalFromBlock(
        block: ResponseBlock,
        intentionTitle: String,
        proposal: Proposal?
    ): ProposalCard? {
        if (block.type != ResponseBlockType.PROPOSAL || !block.active) return null
        val linked = proposal?.takeIf { it.status == ProposalStatus.PENDING }
        val schedule = linked?.let { p ->
            val day = DAY_NAMES.getOrElse(p.proposedDayOfWeek) { "Day ${p.proposedDayOfWeek}" }
            "$day · %02d:%02d".format(p.proposedHour, p.proposedMinute)
        } ?: block.actions.getOrNull(0).orEmpty()
        return ProposalCard(
            title = block.title.ifBlank { linked?.title ?: "Proposed change" },
            body = block.body.ifBlank { linked?.summary.orEmpty() },
            scheduleLine = schedule.ifBlank { "Schedule pending your review" },
            durationLine = "Duration shown after you review details",
            linkedIntentionLine = "Linked to ${intentionTitle.ifBlank { "this intention" }}",
            honestyNote = "PROPOSED · NOT YET ACTIVE — nothing is scheduled until you approve.",
            reviewLabel = block.actions.getOrNull(0) ?: "Review & approve",
            dismissLabel = block.actions.getOrNull(1) ?: "Not now",
            proposalId = linked?.id ?: block.linkedObjectIds.firstOrNull(),
            blockId = block.blockId,
            sourceLabel = block.sourceLabel
        )
    }

    fun proposalFromPending(
        proposal: Proposal,
        intentionTitle: String
    ): ProposalCard? {
        if (proposal.status != ProposalStatus.PENDING) return null
        val day = DAY_NAMES.getOrElse(proposal.proposedDayOfWeek) { "Day ${proposal.proposedDayOfWeek}" }
        val time = "%02d:%02d".format(proposal.proposedHour, proposal.proposedMinute)
        return ProposalCard(
            title = "Make this a weekly session?",
            body = proposal.summary,
            scheduleLine = "$day · $time",
            durationLine = "60 minutes",
            linkedIntentionLine = "Linked to ${intentionTitle.ifBlank { proposal.title }}",
            honestyNote = "If approved, INTENT can bring this session into Home when relevant " +
                "and ask how it went. No tutor or calendar service will be contacted.",
            reviewLabel = "Review & approve",
            dismissLabel = "Not now",
            proposalId = proposal.id,
            blockId = null,
            sourceLabel = proposal.provenance.sourceLabel
        )
    }

    fun recordedFromBlock(block: ResponseBlock): RecordedEntryCard? {
        if (block.type != ResponseBlockType.RECORDED_ENTRY || !block.active) return null
        return RecordedEntryCard(
            title = block.title.ifBlank { "Recorded" },
            subtitle = "RECORDED · REPORTED BY YOU",
            valueLine = block.body,
            sourceLabel = block.sourceLabel.ifBlank { "Added from your conversation" },
            editLabel = block.actions.getOrNull(0) ?: "Edit entry",
            undoLabel = block.actions.getOrNull(1) ?: "Undo",
            blockId = block.blockId,
            entryId = block.linkedObjectIds.firstOrNull()
        )
    }

    fun recordedFromTrackerEntry(entry: TrackerEntry): RecordedEntryCard? {
        if (entry.status != TrackerEntryStatus.ACTIVE) return null
        if (entry.provenance.kind != ProvenanceKind.SAID) {
            // Still show OBSERVED/INFERRED with honest subtitle — never relabel as SAID.
            val label = when (entry.provenance.kind) {
                ProvenanceKind.OBSERVED -> "RECORDED · OBSERVED"
                ProvenanceKind.INFERRED -> "RECORDED · INFERRED"
                ProvenanceKind.SAID -> "RECORDED · REPORTED BY YOU"
            }
            return RecordedEntryCard(
                title = entry.valueText,
                subtitle = label,
                valueLine = entry.valueText,
                sourceLabel = entry.provenance.sourceLabel,
                editLabel = "Edit entry",
                undoLabel = "Undo",
                blockId = null,
                entryId = entry.id
            )
        }
        return RecordedEntryCard(
            title = entry.valueText,
            subtitle = "RECORDED · REPORTED BY YOU",
            valueLine = entry.valueText,
            sourceLabel = entry.provenance.sourceLabel.ifBlank { "Added from your conversation" },
            editLabel = "Edit entry",
            undoLabel = "Undo",
            blockId = null,
            entryId = entry.id
        )
    }

    fun reportPreviewFromBlock(
        block: ResponseBlock,
        pathSummary: PathSummary.View?,
        progressProof: ProgressProof.View?
    ): ReportPreviewCard? {
        if (block.type != ResponseBlockType.REPORT_PREVIEW || !block.active) return null
        val said = progressProof?.saidCount ?: 0
        val observed = progressProof?.observedCount ?: 0
        val inferred = progressProof?.inferredCount ?: 0
        val total = said + observed + inferred
        val primary = when {
            block.body.isNotBlank() -> block.body
            pathSummary != null -> pathSummary.statusLine
            total > 0 -> "$total labeled rows"
            else -> "No progress rows yet"
        }
        val provenance = when {
            said > 0 && observed == 0 && inferred == 0 -> "From manual user entries"
            observed > 0 && said == 0 && inferred == 0 -> "From OBSERVED rows only"
            total > 0 -> "SAID $said · OBSERVED $observed · INFERRED $inferred"
            else -> block.sourceLabel.ifBlank { "From your intention store" }
        }
        return ReportPreviewCard(
            title = block.title.ifBlank { pathSummary?.title ?: "Progress preview" },
            rangeLine = pathSummary?.phaseLabel.orEmpty(),
            primaryMetric = primary,
            trendLine = null,
            statLeft = if (pathSummary != null) {
                "${pathSummary.completedRunCount} completed"
            } else {
                "$said SAID"
            },
            statRight = if (pathSummary != null) {
                "${pathSummary.scheduledSessionCount} scheduled"
            } else {
                "$observed OBSERVED"
            },
            footer = progressProof?.honestyNote
                ?: pathSummary?.honestyNote
                ?: "Reads the same intention store. Provenance stays labeled.",
            openLabel = block.actions.getOrNull(0) ?: "Open full report",
            provenanceChip = provenance,
            blockId = block.blockId
        )
    }

    /**
     * Honest report preview from store views when Conversation asks to surface reports —
     * empty progress stays empty (no fabricated bars).
     */
    fun reportPreviewFromStore(
        pathSummary: PathSummary.View?,
        progressProof: ProgressProof.View?
    ): ReportPreviewCard? {
        if (pathSummary == null && progressProof == null) return null
        val said = progressProof?.saidCount ?: 0
        val observed = progressProof?.observedCount ?: 0
        val inferred = progressProof?.inferredCount ?: 0
        val total = said + observed + inferred
        val completed = pathSummary?.completedRunCount ?: 0
        val scheduled = pathSummary?.scheduledSessionCount ?: 0
        if (total == 0 && completed == 0) {
            return ReportPreviewCard(
                title = pathSummary?.title ?: "Progress preview",
                rangeLine = pathSummary?.phaseLabel.orEmpty(),
                primaryMetric = progressProof?.emptyState ?: "No progress rows yet",
                trendLine = null,
                statLeft = "0 completed",
                statRight = "$scheduled scheduled",
                footer = progressProof?.honestyNote
                    ?: pathSummary?.honestyNote
                    ?: "Empty report — nothing invented.",
                openLabel = "Open full report",
                provenanceChip = "From your intention store",
                blockId = null
            )
        }
        val provenance = when {
            said > 0 && observed == 0 && inferred == 0 -> "From manual user entries"
            observed > 0 && said == 0 && inferred == 0 -> "From OBSERVED rows only"
            total > 0 -> "SAID $said · OBSERVED $observed · INFERRED $inferred"
            else -> "From your intention store"
        }
        return ReportPreviewCard(
            title = pathSummary?.title ?: "Your progress",
            rangeLine = pathSummary?.phaseLabel.orEmpty(),
            primaryMetric = pathSummary?.statusLine?.takeIf { it.isNotBlank() }
                ?: "$total labeled rows",
            trendLine = null,
            statLeft = "${pathSummary?.completedRunCount ?: 0} completed",
            statRight = "${pathSummary?.scheduledSessionCount ?: 0} scheduled",
            footer = progressProof?.honestyNote
                ?: pathSummary?.honestyNote
                ?: "Reads the same intention store. Provenance stays labeled.",
            openLabel = "Open full report",
            provenanceChip = provenance,
            blockId = null
        )
    }

    /**
     * Project an EXPLANATION [ResponseBlock] into a typed card.
     * Skips inactive or empty (no title and no body) blocks — never invents copy.
     */
    fun explanationFromBlock(block: ResponseBlock): ExplanationCard? {
        if (block.type != ResponseBlockType.EXPLANATION || !block.active) return null
        if (block.title.isBlank() && block.body.isBlank()) return null
        return ExplanationCard(
            title = block.title.ifBlank { "Explanation" },
            body = block.body,
            sourceLabel = block.sourceLabel,
            actions = block.actions,
            blockId = block.blockId
        )
    }

    /**
     * Project a PROGRESS [ResponseBlock] into a typed card.
     * Skips inactive or empty blocks. Provenance chip preserves OBSERVED / SAID / INFERRED
     * labels from [ResponseBlock.sourceLabel] — does not invent metric values.
     */
    fun progressFromBlock(block: ResponseBlock): ProgressCard? {
        if (block.type != ResponseBlockType.PROGRESS || !block.active) return null
        if (block.title.isBlank() && block.body.isBlank()) return null
        val provenance = progressProvenanceChip(block.sourceLabel)
        return ProgressCard(
            title = block.title.ifBlank { "Progress" },
            body = block.body,
            provenanceChip = provenance,
            sourceLabel = block.sourceLabel,
            actions = block.actions,
            honestyNote = "Shows this progress note only — no invented OBSERVED metrics.",
            blockId = block.blockId
        )
    }

    /** Prefer explicit OBSERVED / SAID / INFERRED chip; never invent a metric value. */
    fun progressProvenanceChip(sourceLabel: String): String {
        val upper = sourceLabel.uppercase()
        return when {
            upper.startsWith("OBSERVED") || upper.contains("OBSERVED") -> "OBSERVED"
            upper.startsWith("SAID") || upper.contains("SAID") ||
                upper.contains("REPORTED BY YOU") || upper.contains("MANUAL") -> "SAID"
            upper.startsWith("INFERRED") || upper.contains("INFERRED") -> "INFERRED"
            sourceLabel.isBlank() -> "PROGRESS"
            else -> sourceLabel.trim()
        }
    }

    /** Prefer explicit OBSERVED / INFERRED chip from sourceLabel; never silent mix. */
    fun interventionProvenanceChip(sourceLabel: String, title: String): String {
        val upper = sourceLabel.uppercase()
        val chip = when {
            upper.startsWith("OBSERVED") -> "OBSERVED"
            upper.startsWith("INFERRED") -> "INFERRED"
            upper.contains("OBSERVED") -> "OBSERVED"
            upper.contains("INFERRED") -> "INFERRED"
            else -> "INFERRED"
        }
        val titlePart = title.trim().ifBlank { "Check-in" }
        return "$chip · $titlePart"
    }
}
