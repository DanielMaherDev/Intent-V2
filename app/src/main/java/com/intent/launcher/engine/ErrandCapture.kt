package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.ErrandStatus
import com.intent.launcher.domain.ErrandTodo
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.store.IntentionFlowStore
import java.util.UUID

/**
 * Lightweight errand / quick to-do capture (OS-073 / CASE-01).
 * Never seeds an Intention or forces exploring.
 */
object ErrandCapture {

    enum class Kind {
        /** Small errand — belongs in the to-do inbox. */
        QUICK_TODO,
        /** Longer / exploratory goal — should use Intention builder, not this path. */
        INTENTION_CANDIDATE
    }

    data class Parsed(
        val title: String,
        val firstAction: String,
        val reminderNote: String?,
        val kind: Kind,
        val honestyLabel: String
    )

    data class Result(
        val ok: Boolean,
        val todo: ErrandTodo?,
        val confirmation: String,
        val intentionCountUnchanged: Boolean
    )

    private val timeHints = listOf(
        "tonight",
        "this evening",
        "this afternoon",
        "this morning",
        "today",
        "tomorrow",
        "later today",
        "later"
    )

    private val errandVerbs = listOf(
        "buy", "get", "grab", "pick up", "pickup", "call", "text", "email",
        "drop off", "dropoff", "return", "pay", "refill", "mail", "post",
        "wash", "fix", "book", "schedule", "remind me"
    )

    private val intentionMarkers = listOf(
        "learn", "speak", "train for", "run a", "marathon", "business",
        "quit smoking", "lose weight", "build a", "start a company",
        "become", "master", "fluent", "career", "job offer"
    )

    fun classify(raw: String): Kind {
        val text = raw.trim().lowercase()
        if (text.isEmpty()) return Kind.QUICK_TODO
        if (intentionMarkers.any { text.contains(it) }) return Kind.INTENTION_CANDIDATE
        val hasTime = timeHints.any { text.contains(it) }
        val hasVerb = errandVerbs.any { text.contains(it) }
        val short = text.split(Regex("\\s+")).size <= 10
        return if ((hasVerb || hasTime) && short) Kind.QUICK_TODO else Kind.INTENTION_CANDIDATE
    }

    fun parse(raw: String): Parsed {
        val trimmed = raw.trim().replace(Regex("\\s+"), " ")
        require(trimmed.isNotEmpty()) { "empty errand text" }
        val lower = trimmed.lowercase()
        val kind = classify(trimmed)
        val reminder = timeHints
            .sortedByDescending { it.length }
            .firstOrNull { lower.contains(it) }
            ?.replaceFirstChar { it.titlecase() }
        val firstAction = if (reminder != null) {
            val idx = lower.lastIndexOf(reminder.lowercase())
            if (idx > 0) trimmed.substring(0, idx).trim().trimEnd(',', '.', '!')
            else trimmed
        } else {
            trimmed
        }.ifBlank { trimmed }
        val honesty = when (kind) {
            Kind.QUICK_TODO ->
                "Quick to-do — not an intention. First action + optional reminder only."
            Kind.INTENTION_CANDIDATE ->
                "This may be larger than an errand — still saved as a quick to-do " +
                    "(not exploring). Use Start a new intention if you want a full plan."
        }
        return Parsed(
            title = trimmed,
            firstAction = firstAction,
            reminderNote = reminder,
            kind = kind,
            honestyLabel = honesty
        )
    }

    fun capture(
        store: IntentionFlowStore,
        text: String,
        nowEpochMs: Long,
        todoId: String = "errand-${UUID.randomUUID()}",
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Result {
        if (!registry.isActionable(CapabilityRegistry.ID_QUICK_TODO)) {
            return Result(
                ok = false,
                todo = null,
                confirmation = registry.get(CapabilityRegistry.ID_QUICK_TODO)?.limitNote
                    ?: "Quick to-dos aren’t available.",
                intentionCountUnchanged = true
            )
        }
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            return Result(
                ok = false,
                todo = null,
                confirmation = "Nothing to save — type a quick to-do first.",
                intentionCountUnchanged = true
            )
        }
        val beforeIntentions = store.snapshot().intentions.size
        val parsed = parse(trimmed)
        store.apply(
            FlowCommand.CaptureErrand(
                todoId = todoId,
                title = parsed.title,
                firstAction = parsed.firstAction,
                reminderNote = parsed.reminderNote,
                nowEpochMs = nowEpochMs
            )
        )
        val snap = store.snapshot()
        val saved = snap.errandTodos.firstOrNull { it.id == todoId }
        val reminderBit = saved?.reminderNote?.let { " Reminder: $it." } ?: ""
        return Result(
            ok = saved != null && saved.status == ErrandStatus.OPEN,
            todo = saved,
            confirmation = "Saved as a quick to-do — “${saved?.firstAction ?: parsed.firstAction}”." +
                reminderBit +
                " Not added to exploring intentions.",
            intentionCountUnchanged = snap.intentions.size == beforeIntentions
        )
    }

    fun complete(
        store: IntentionFlowStore,
        todoId: String,
        nowEpochMs: Long
    ): Boolean {
        store.apply(FlowCommand.CompleteErrand(todoId = todoId, nowEpochMs = nowEpochMs))
        return store.snapshot().errandTodos.any {
            it.id == todoId && it.status == ErrandStatus.DONE
        }
    }

    fun reopen(
        store: IntentionFlowStore,
        todoId: String,
        nowEpochMs: Long
    ): Boolean {
        store.apply(FlowCommand.ReopenErrand(todoId = todoId, nowEpochMs = nowEpochMs))
        return store.snapshot().errandTodos.any {
            it.id == todoId && it.status == ErrandStatus.OPEN
        }
    }
}
