package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.BuilderDraft
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.store.IntentionFlowStore

/**
 * Guided intention builder — free text → agent shape classification →
 * missing-fact prompts → confirm.
 * Shape is chosen by the agent from the description; the user is not forced
 * to pick Language / Marathon / Business / Other. Optional correction stays
 * available via [setShape]. All durable writes go through [FlowCommand];
 * facts are stored only on commit.
 */
object IntentionBuilder {

    /** Internal honesty for logs / drafts — not shown as a user banner. */
    const val HONESTY = "Shape chosen from your description."

    data class Prompt(
        val id: String,
        val question: String,
        /** When set, the UI shows a NumberWheel instead of free text. */
        val quantityChoices: List<Int>? = null,
        val quantityUnitLabel: String? = null,
        val quantitySummaryTemplate: String = "{value} {unit}"
    )

    data class StartResult(
        val ok: Boolean,
        val draft: BuilderDraft?,
        val note: String
    )

    fun suggestShape(text: String): String {
        val t = text.lowercase()
        return when {
            listOf("spanish", "language", "duolingo", "tutor", "french", "german")
                .any { t.contains(it) } -> "language"
            listOf("marathon", "5k", "10k", "running", "long run", "race")
                .any { t.contains(it) } -> "marathon"
            listOf("business", "startup", "customer", "company", "product")
                .any { t.contains(it) } -> "business"
            else -> "other"
        }
    }

    fun shapeDisplayLabel(shapeTag: String): String =
        shapeChoices().firstOrNull { it.first == shapeTag }?.second ?: "Something else"

    fun titleFrom(text: String, shapeTag: String): String {
        val trimmed = text.trim().replace('\n', ' ')
        if (trimmed.length <= 48) return trimmed.ifBlank { defaultTitle(shapeTag) }
        return trimmed.take(45).trimEnd() + "…"
    }

    fun defaultTitle(shapeTag: String): String =
        when (shapeTag) {
            "language" -> "Learn a language"
            "marathon" -> "Train for a race"
            "business" -> "Explore a business idea"
            else -> "New intention"
        }

    fun missingFactPrompts(shapeTag: String): List<Prompt> =
        when (shapeTag) {
            "language" -> listOf(
                Prompt(
                    id = "practice_minutes",
                    question = "How often can you practise?",
                    quantityChoices = PracticeMinuteChoices.VALUES,
                    quantityUnitLabel = PracticeMinuteChoices.UNIT,
                    quantitySummaryTemplate = "{value} {unit}"
                ),
                Prompt(
                    id = "existing_method",
                    question = "Do you already have a tutor, class, or app?"
                )
            )
            "marathon" -> listOf(
                Prompt("focus_area", "What part of prep matters most right now?"),
                Prompt(
                    id = "available_time",
                    question = "How much time can you train in a typical week?",
                    quantityChoices = listOf(30, 45, 60, 90, 120, 180),
                    quantityUnitLabel = "minutes per week",
                    quantitySummaryTemplate = "{value} {unit}"
                )
            )
            "business" -> listOf(
                Prompt("learn_next", "What would help you learn what to do next?"),
                Prompt("existing_method", "Do you already talk with customers or mentors?")
            )
            else -> listOf(
                Prompt("current_state", "Where are you starting from today?"),
                Prompt("constraint", "Any deadline or time constraint that matters?")
            )
        }

    fun nextPrompt(draft: BuilderDraft): Prompt? {
        val shape = draft.shapeTag ?: draft.suggestedShapeTag
        return missingFactPrompts(shape).firstOrNull { it.id !in draft.factAnswers }
    }

    fun progressStep(draft: BuilderDraft): Pair<Int, Int> {
        if (!draft.active || draft.phase == BuilderPhase.ENTER_TEXT) return 0 to 3
        val total = 3
        return when (draft.phase) {
            BuilderPhase.ENTER_TEXT -> 0 to total
            BuilderPhase.PICK_SHAPE -> 1 to total
            BuilderPhase.MISSING_FACTS -> {
                val shape = draft.shapeTag ?: draft.suggestedShapeTag
                val prompts = missingFactPrompts(shape)
                val answered = draft.factAnswers.size.coerceAtMost(prompts.size)
                // Step 1 = description done; steps 2..n = prompts; last = confirm
                (1 + answered).coerceAtMost(total - 1) to total
            }
            BuilderPhase.CONFIRM -> total to total
        }
    }

    fun start(
        store: IntentionFlowStore,
        freeText: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): StartResult {
        if (!registry.canBuildIntention()) {
            return StartResult(
                ok = false,
                draft = null,
                note = registry.get(CapabilityRegistry.ID_BUILD_INTENTION)?.limitNote
                    ?: "Intention builder is not available."
            )
        }
        val trimmed = freeText.trim()
        if (trimmed.isEmpty()) {
            return StartResult(false, null, "Enter a short description of what you want to work on.")
        }
        val shape = suggestShape(trimmed)
        val title = titleFrom(trimmed, shape)
        store.apply(
            FlowCommand.StartBuilderDraft(
                freeText = trimmed,
                suggestedShapeTag = shape,
                title = title,
                statement = trimmed,
                nowEpochMs = nowEpochMs,
                honestyNote = HONESTY,
                // Agent auto-classifies — skip forced shape pick.
                autoApplyShape = true
            )
        )
        val draft = store.snapshot().builderDraft
        val label = shapeDisplayLabel(shape)
        return StartResult(
            ok = draft.active,
            draft = draft,
            note = "We’ve treated this as a ${label.lowercase()} intention."
        )
    }

    fun setShape(
        store: IntentionFlowStore,
        shapeTag: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Boolean {
        if (!registry.canBuildIntention()) return false
        val draft = store.snapshot().builderDraft
        if (!draft.active) return false
        val title = when (shapeTag) {
            draft.suggestedShapeTag -> draft.title
            else -> defaultTitle(shapeTag)
        }
        val statement = draft.statement.ifBlank { draft.freeText }
        store.apply(
            FlowCommand.SetBuilderShape(
                shapeTag = shapeTag,
                title = title.ifBlank { defaultTitle(shapeTag) },
                statement = statement,
                nowEpochMs = nowEpochMs
            )
        )
        return store.snapshot().builderDraft.phase == BuilderPhase.MISSING_FACTS
    }

    fun answerPrompt(
        store: IntentionFlowStore,
        promptId: String,
        answerText: String,
        nowEpochMs: Long,
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Boolean {
        if (!registry.canBuildIntention()) return false
        store.apply(
            FlowCommand.AnswerBuilderPrompt(
                promptId = promptId,
                answerText = answerText,
                nowEpochMs = nowEpochMs
            )
        )
        return store.snapshot().builderDraft.factAnswers.containsKey(promptId)
    }

    fun commit(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        intentionId: String = "intention-builder-$nowEpochMs",
        registry: CapabilityRegistry = CapabilityRegistry.default
    ): Boolean {
        if (!registry.canBuildIntention()) return false
        if (!store.snapshot().builderDraft.active) return false
        store.apply(
            FlowCommand.CommitBuilderIntention(
                intentionId = intentionId,
                nowEpochMs = nowEpochMs
            )
        )
        return store.snapshot().intentions.any { it.id == intentionId } &&
            !store.snapshot().builderDraft.active
    }

    fun cancel(store: IntentionFlowStore, nowEpochMs: Long) {
        store.apply(FlowCommand.CancelBuilderDraft(nowEpochMs = nowEpochMs))
    }

    fun shapeChoices(): List<Pair<String, String>> = listOf(
        "language" to "Language",
        "marathon" to "Marathon / race",
        "business" to "Business",
        "other" to "Something else"
    )
}

/** Shared minute ladder for builder / tracker quantity wheels. */
private object PracticeMinuteChoices {
    val VALUES: List<Int> = listOf(5, 10, 15, 20, 30, 45, 60)
    const val UNIT = "minutes per day"
}
