package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.ErrandChecklistItem
import com.intent.launcher.domain.ErrandStatus
import com.intent.launcher.domain.ErrandTodo
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.store.IntentionFlowStore
import java.util.UUID

/**
 * Lightweight errand / quick to-do capture (OS-073 / CASE-01 / OTHER §1).
 * Never seeds an Intention or forces exploring.
 *
 * Generic for all small errands: classify → optional reminder-vs-checklist →
 * compact checklist children when useful — not milk-specific.
 */
object ErrandCapture {

    const val MODE_PROMPT_ID = "errand_capture_mode"
    const val CHIP_CHECKLIST = "Just the checklist"
    const val CHIP_REMINDER = "Add a reminder"

    enum class Kind {
        /** Small errand — belongs in the to-do inbox. */
        QUICK_TODO,
        /** Longer / exploratory goal — should use Intention builder, not this path. */
        INTENTION_CANDIDATE
    }

    enum class CaptureMode {
        /** Compact checklist; due label kept when present; no reminder ping. */
        CHECKLIST,
        /** Reminder-oriented save (still may carry checklist children). */
        REMINDER
    }

    data class Parsed(
        val title: String,
        val firstAction: String,
        val reminderNote: String?,
        val kind: Kind,
        val honestyLabel: String,
        /** Child labels for a compact list (e.g. milk, eggs). */
        val checklistLabels: List<String>
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
        val short = text.split(Regex("\\s+")).size <= 12
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
        val labels = splitChecklistLabels(firstAction)
        val honesty = when (kind) {
            Kind.QUICK_TODO ->
                "Quick to-do — not an intention. Checklist + optional reminder only."
            Kind.INTENTION_CANDIDATE ->
                "This may be larger than an errand — still saved as a quick to-do " +
                    "(not exploring). Use Start a new intention if you want a full plan."
        }
        return Parsed(
            title = trimmed,
            firstAction = firstAction,
            reminderNote = reminder,
            kind = kind,
            honestyLabel = honesty,
            checklistLabels = labels
        )
    }

    /**
     * Split a first-action phrase into checklist children when the user listed
     * multiple objects (commas / "and" / "&"). Generic — not product-specific.
     */
    fun splitChecklistLabels(firstAction: String): List<String> {
        val trimmed = firstAction.trim()
        if (trimmed.isEmpty()) return emptyList()
        val lower = trimmed.lowercase()
        val verb = errandVerbs
            .sortedByDescending { it.length }
            .firstOrNull { lower.startsWith(it) }
        val rest = if (verb != null) {
            trimmed.substring(verb.length).trim().trimStart(',', ' ')
        } else {
            trimmed
        }
        if (rest.isEmpty()) return listOf(trimmed)
        val parts = rest
            .split(Regex("\\s*(?:,|/|&|\\band\\b)\\s*", RegexOption.IGNORE_CASE))
            .map { it.trim().trimEnd('.', '!') }
            .filter { it.isNotEmpty() }
        return when {
            parts.size >= 2 -> parts.map { label ->
                val cleaned = label.trim()
                if (rest == rest.lowercase()) cleaned.lowercase() else cleaned
            }
            else -> listOf(trimmed)
        }
    }

    fun checklistItemsFrom(
        labels: List<String>,
        todoId: String
    ): List<ErrandChecklistItem> =
        labels.mapIndexed { index, label ->
            ErrandChecklistItem(
                id = "$todoId-item-$index",
                label = label,
                done = false
            )
        }

    fun offerBody(parsed: Parsed): String {
        val due = parsed.reminderNote?.let { " for ${it.lowercase()}" }.orEmpty()
        return "I can put that on your to-do list$due. Want a reminder, or just the checklist?"
    }

    fun modeFromChip(label: String): CaptureMode? {
        val t = label.trim()
        if (t.equals(CHIP_CHECKLIST, ignoreCase = true) ||
            t.contains("checklist", ignoreCase = true)
        ) {
            return CaptureMode.CHECKLIST
        }
        if (t.equals(CHIP_REMINDER, ignoreCase = true) ||
            t.contains("reminder", ignoreCase = true)
        ) {
            return CaptureMode.REMINDER
        }
        return null
    }

    fun capture(
        store: IntentionFlowStore,
        text: String,
        nowEpochMs: Long,
        todoId: String = "errand-${UUID.randomUUID()}",
        registry: CapabilityRegistry = CapabilityRegistry.default,
        mode: CaptureMode = CaptureMode.CHECKLIST
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
        val items = checklistItemsFrom(parsed.checklistLabels, todoId)
        val reminderEnabled = mode == CaptureMode.REMINDER
        store.apply(
            FlowCommand.CaptureErrand(
                todoId = todoId,
                title = parsed.title,
                firstAction = parsed.firstAction,
                reminderNote = parsed.reminderNote,
                nowEpochMs = nowEpochMs,
                checklistItems = items,
                reminderEnabled = reminderEnabled
            )
        )
        val snap = store.snapshot()
        val saved = snap.errandTodos.firstOrNull { it.id == todoId }
        val itemBit = when {
            items.size >= 2 ->
                " Added: ${items.joinToString(" and ") { it.label }}."
            else -> ""
        }
        val reminderBit = when {
            reminderEnabled && saved?.reminderNote != null ->
                " Reminder: ${saved.reminderNote}."
            !reminderEnabled && saved?.reminderNote != null ->
                " Due: ${saved.reminderNote}."
            else -> ""
        }
        return Result(
            ok = saved != null && saved.status == ErrandStatus.OPEN,
            todo = saved,
            confirmation = "Saved as a quick to-do — “${saved?.firstAction ?: parsed.firstAction}”." +
                itemBit +
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

    fun toggleChecklistItem(
        store: IntentionFlowStore,
        todoId: String,
        itemId: String,
        nowEpochMs: Long
    ): Boolean {
        store.apply(
            FlowCommand.ToggleErrandChecklistItem(
                todoId = todoId,
                itemId = itemId,
                nowEpochMs = nowEpochMs
            )
        )
        return store.snapshot().errandTodos
            .firstOrNull { it.id == todoId }
            ?.checklistItems
            ?.any { it.id == itemId } == true
    }
}
