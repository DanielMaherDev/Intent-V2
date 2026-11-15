package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ProvenanceKind
import java.time.Instant
import java.time.ZoneId

/**
 * Bounded context pack for live Discuss (contracts v1 §6).
 * Never includes secrets or API keys.
 */
object ContextAssembler {
    const val MAX_FACTS = 8
    const val MAX_TURNS = 12
    const val MAX_PROPOSALS = 5

    /**
     * Returns a bounded honesty pack for the system prompt.
     * Tip snapshot field is [IntentionSnapshot.turns] (not conversationTurns).
     */
    fun assemble(
        snapshot: IntentionSnapshot,
        intention: Intention?,
        registry: CapabilityRegistry = CapabilityRegistry.default,
        zoneId: ZoneId = ZoneId.systemDefault(),
        nowEpochMs: Long = System.currentTimeMillis()
    ): String {
        val intentionId = intention?.id ?: snapshot.activeIntentionId
        val focus = intention ?: snapshot.intentions.firstOrNull { it.id == intentionId }
        val sb = StringBuilder()
        sb.appendLine("## Intention context (local; not model memory)")
        if (focus == null) {
            sb.appendLine("No focused intention.")
        } else {
            sb.appendLine("id=${focus.id}")
            sb.appendLine("title=${focus.title}")
            sb.appendLine("phase=${focus.phase}")
            sb.appendLine("shapeTag=${focus.shapeTag}")
            sb.appendLine("statement=${focus.statement}")
        }
        sb.appendLine()
        sb.appendLine("## SAID facts (capped $MAX_FACTS)")
        val facts = snapshot.facts
            .filter { it.intentionId == intentionId && it.provenance.kind == ProvenanceKind.SAID }
            .sortedByDescending { it.provenance.atEpochMs }
            .take(MAX_FACTS)
        if (facts.isEmpty()) sb.appendLine("(none)")
        else facts.forEach { sb.appendLine("- [${it.id}] ${it.text}") }
        sb.appendLine()
        sb.appendLine("## Pending proposals (capped $MAX_PROPOSALS)")
        val proposals = snapshot.proposals
            .filter { it.intentionId == intentionId && it.status == ProposalStatus.PENDING }
            .take(MAX_PROPOSALS)
        if (proposals.isEmpty()) sb.appendLine("(none)")
        else proposals.forEach {
            sb.appendLine(
                "- [${it.id}] ${it.title}: ${it.summary} " +
                    "(dow=${it.proposedDayOfWeek} %02d:%02d)".format(it.proposedHour, it.proposedMinute)
            )
        }
        sb.appendLine()
        sb.appendLine("## Recent turns (capped $MAX_TURNS)")
        val turns = snapshot.turns
            .filter { it.intentionId == intentionId }
            .sortedBy { it.atEpochMs }
            .takeLast(MAX_TURNS)
        if (turns.isEmpty()) sb.appendLine("(none)")
        else turns.forEach { turn ->
            val mod = turn.modality?.let { " [$it]" }.orEmpty()
            sb.appendLine("- ${turn.role}$mod: ${turn.text.take(280)}")
        }
        sb.appendLine()
        sb.appendLine("## Capabilities (honesty)")
        listOf(
            CapabilityRegistry.ID_LIVE_LLM,
            CapabilityRegistry.ID_VOICE_INPUT,
            CapabilityRegistry.ID_PROPOSE_RECURRING_SESSION
        ).forEach { id ->
            val cap = registry.get(id)
            if (cap != null) {
                sb.appendLine("- $id: ${cap.status} — ${cap.limitNote}")
            }
        }
        sb.appendLine()
        val local = Instant.ofEpochMilli(nowEpochMs).atZone(zoneId)
        sb.appendLine("## Local now")
        sb.appendLine(local.toString())
        sb.appendLine()
        sb.appendLine(
            "Rules: Suggest only; do not claim OBSERVED evidence; " +
                "do not approve proposals; do not invent lesson completion."
        )
        return sb.toString().trim()
    }
}
