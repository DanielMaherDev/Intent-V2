package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.ThoughtCaptureStatus
import com.intent.launcher.domain.TriageItem
import com.intent.launcher.domain.TriageItemStatus
import com.intent.launcher.store.IntentionFlowStore

/**
 * Multi-thought triage — heuristic split into ordered intention proposals.
 * Confirm → SeedExploring + PROMOTED thoughts; reject → leave RAW evidence only.
 */
object ThoughtTriage {

    const val HONESTY = "Heuristic split — no live LLM."

    data class Result(
        val ok: Boolean,
        val batchId: String?,
        val items: List<TriageItem>,
        val note: String
    )

    /**
     * Split a dump on newlines / semicolons / " and " when it looks like multiple goals.
     */
    fun splitDump(text: String): List<String> {
        val normalized = text.trim()
        if (normalized.isEmpty()) return emptyList()
        val byLine = normalized.split('\n', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (byLine.size >= 2) return byLine.take(6)
        val byAnd = normalized.split(Regex("\\s+and\\s+", RegexOption.IGNORE_CASE))
            .map { it.trim().trimEnd(',') }
            .filter { it.isNotEmpty() }
        if (byAnd.size >= 2) return byAnd.take(6)
        return listOf(normalized)
    }

    fun suggestedNextAction(shapeTag: String): String =
        when (shapeTag) {
            "language" -> "Continue exploring — clarify practice that matters most"
            "marathon" -> "Continue exploring — clarify training focus"
            "business" -> "Continue exploring — clarify what to learn next"
            else -> "Continue exploring — answer one clarifying question"
        }

    /**
     * Capture each fragment as RAW ThoughtCapture, then open a triage batch.
     */
    fun startFromDump(
        store: IntentionFlowStore,
        dumpText: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Result {
        if (!registry.canTriageThoughts()) {
            return Result(
                ok = false,
                batchId = null,
                items = emptyList(),
                note = registry.get(CapabilityRegistry.ID_TRIAGE_THOUGHTS)?.limitNote
                    ?: "Thought triage is not available."
            )
        }
        val fragments = splitDump(dumpText)
        if (fragments.isEmpty()) {
            return Result(false, null, emptyList(), "Add two or more thoughts to triage.")
        }
        if (fragments.size == 1) {
            return Result(
                false,
                null,
                emptyList(),
                "Only one thought detected — use New intention builder instead, or add more goals."
            )
        }
        val items = mutableListOf<TriageItem>()
        fragments.forEachIndexed { index, fragment ->
            val thoughtId = "thought-triage-$nowEpochMs-$index"
            CaptureThought.capture(
                store = store,
                text = fragment,
                source = CaptureThought.SOURCE_TRIAGE,
                nowEpochMs = nowEpochMs + index,
                thoughtId = thoughtId,
                intentionId = CaptureThought.resolveIntentionId(store)
            )
            val shape = IntentionBuilder.suggestShape(fragment)
            items += TriageItem(
                id = "triage-item-$nowEpochMs-$index",
                title = IntentionBuilder.titleFrom(fragment, shape),
                statement = fragment,
                suggestedShapeTag = shape,
                suggestedNextAction = suggestedNextAction(shape),
                sourceThoughtIds = listOf(thoughtId),
                status = TriageItemStatus.PENDING,
                honestyNote = HONESTY
            )
        }
        val batchId = "triage-batch-$nowEpochMs"
        store.apply(
            FlowCommand.StartTriageBatch(
                batchId = batchId,
                items = items,
                nowEpochMs = nowEpochMs
            )
        )
        return Result(
            ok = true,
            batchId = batchId,
            items = store.snapshot().triage.items,
            note = "$HONESTY Proposed ${items.size} intentions — confirm or reject each."
        )
    }

    /**
     * Open triage over existing RAW thought captures (Home entry when inbox has raw items).
     */
    fun startFromRawThoughts(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Result {
        if (!registry.canTriageThoughts()) {
            return Result(
                ok = false,
                batchId = null,
                items = emptyList(),
                note = registry.get(CapabilityRegistry.ID_TRIAGE_THOUGHTS)?.limitNote
                    ?: "Thought triage is not available."
            )
        }
        val raw = store.snapshot().thoughtCaptures
            .filter { it.status == ThoughtCaptureStatus.RAW }
            .sortedBy { it.capturedAtEpochMs }
            .take(6)
        if (raw.isEmpty()) {
            return Result(false, null, emptyList(), "No raw thoughts to triage — capture some first.")
        }
        val items = raw.mapIndexed { index, thought ->
            val shape = IntentionBuilder.suggestShape(thought.text)
            TriageItem(
                id = "triage-item-$nowEpochMs-$index",
                title = IntentionBuilder.titleFrom(thought.text, shape),
                statement = thought.text,
                suggestedShapeTag = shape,
                suggestedNextAction = suggestedNextAction(shape),
                sourceThoughtIds = listOf(thought.id),
                status = TriageItemStatus.PENDING,
                honestyNote = HONESTY
            )
        }
        val batchId = "triage-batch-$nowEpochMs"
        store.apply(
            FlowCommand.StartTriageBatch(
                batchId = batchId,
                items = items,
                nowEpochMs = nowEpochMs
            )
        )
        return Result(true, batchId, store.snapshot().triage.items, "$HONESTY ${items.size} proposals ready.")
    }

    fun confirm(
        store: IntentionFlowStore,
        itemId: String,
        nowEpochMs: Long,
        intentionId: String = "intention-triage-$itemId",
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Boolean {
        if (!registry.canTriageThoughts()) return false
        store.apply(
            FlowCommand.ConfirmTriageItem(
                itemId = itemId,
                intentionId = intentionId,
                nowEpochMs = nowEpochMs
            )
        )
        return store.snapshot().triage.items.any {
            it.id == itemId && it.status == TriageItemStatus.CONFIRMED
        }
    }

    fun reject(
        store: IntentionFlowStore,
        itemId: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Boolean {
        if (!registry.canTriageThoughts()) return false
        store.apply(FlowCommand.RejectTriageItem(itemId = itemId, nowEpochMs = nowEpochMs))
        val item = store.snapshot().triage.items.firstOrNull { it.id == itemId } ?: return false
        // Evidence remains RAW on the thought captures.
        val stillRaw = store.snapshot().thoughtCaptures
            .filter { it.id in item.sourceThoughtIds }
            .all { it.status == ThoughtCaptureStatus.RAW }
        return item.status == TriageItemStatus.REJECTED && stillRaw
    }
}
