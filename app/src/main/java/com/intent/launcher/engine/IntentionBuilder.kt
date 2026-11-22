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
        val quantitySummaryTemplate: String = "{value} {unit}",
        /**
         * Predefined chip labels (without Other). Conversation / builder UI
         * appends [ChoiceOptions.OTHER] for the free-text path.
         */
        val choiceOptions: List<String>? = null
    )

    data class StartResult(
        val ok: Boolean,
        val draft: BuilderDraft?,
        val note: String
    )

    fun suggestShape(text: String): String {
        val t = text.lowercase()
        return when {
            // OC2 / §8.8 — before language so “Spain” legal work ≠ Learn Spanish.
            looksLikeLegalResearch(t) -> "research"
            listOf("spanish", "language", "duolingo", "tutor", "french", "german")
                .any { t.contains(it) } -> "language"
            listOf("marathon", "5k", "10k", "running", "long run", "race")
                .any { t.contains(it) } -> "marathon"
            listOf("business", "startup", "customer", "company", "product")
                .any { t.contains(it) } -> "business"
            else -> "other"
        }
    }

    /**
     * Conditional legal/research process (e.g. work legally in Spain).
     * Prefer this over language when legal/work-eligibility wording is present.
     */
    internal fun looksLikeLegalResearch(textLower: String): Boolean {
        val t = textLower.lowercase()
        if (t.contains("work legally") || t.contains("working legally")) return true
        if (t.contains("work permit") || t.contains("residence permit")) return true
        val spainOrEu = t.contains("spain") || t.contains("spanish") || t.contains("eu citizen")
        val legalCue = listOf(
            "legally", "legal", "visa", "nie", "nif", "paperwork", "registration",
            "social security", "employment registration", "work here"
        ).any { t.contains(it) }
        return spainOrEu && legalCue &&
            !t.contains("learn spanish") &&
            !t.contains("practise spanish") &&
            !t.contains("practice spanish")
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
            "research" -> "Work legally in Spain"
            else -> "New intention"
        }

    fun missingFactPrompts(shapeTag: String): List<Prompt> =
        when (shapeTag) {
            "research" -> listOf(
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_CITIZENSHIP,
                    question =
                        "Which country are you a citizen of? Requirements depend on jurisdiction.",
                    choiceOptions = listOf(
                        "Irish / EU citizen",
                        "Non-EU citizen",
                        "Not sure"
                    )
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_EMPLOYMENT_ARRANGEMENT,
                    question =
                        "Are you employed, self-employed, or still looking?",
                    choiceOptions = listOf(
                        "Employed",
                        "Self-employed",
                        "Still looking",
                        "Not sure"
                    )
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_RESIDENCE_DURATION,
                    question =
                        "How long have you lived here, and which documents do you already have? " +
                            "You can say not sure.",
                    choiceOptions = listOf(
                        "About a month — rental only",
                        "Less than three months",
                        "Longer — some paperwork done",
                        "Not sure"
                    )
                )
            )
            "language" -> listOf(
                Prompt(
                    id = "practice_minutes",
                    question = "How many minutes can you practise on a typical day?",
                    quantityChoices = PracticeMinuteChoices.VALUES,
                    quantityUnitLabel = PracticeMinuteChoices.UNIT,
                    quantitySummaryTemplate = "{value} {unit}"
                ),
                Prompt(
                    id = "existing_method",
                    question = "Do you already have a tutor, class, or app?",
                    choiceOptions = listOf(
                        "Tutor or class",
                        "Learning app",
                        "Self-study",
                        "Not yet"
                    )
                )
            )
            "marathon" -> listOf(
                Prompt(
                    id = "focus_area",
                    question = "What part of prep matters most right now?",
                    choiceOptions = listOf(
                        "Building base mileage",
                        "Long-run stamina",
                        "Speed work",
                        "Recovery / injury care"
                    )
                ),
                Prompt(
                    id = "available_time",
                    question = "How many minutes can you train in a typical week?",
                    quantityChoices = (1..180).toList(),
                    quantityUnitLabel = "minutes per week",
                    quantitySummaryTemplate = "{value} {unit}"
                )
            )
            "business" -> listOf(
                Prompt(
                    id = "learn_next",
                    question = "What would help you learn what to do next?",
                    choiceOptions = listOf(
                        "Talk to a customer",
                        "Write a one-pager",
                        "Research competitors",
                        "Not sure yet"
                    )
                ),
                Prompt(
                    id = "existing_method",
                    question = "Do you already talk with customers or mentors?",
                    choiceOptions = listOf(
                        "Yes — regularly",
                        "A little",
                        "Not yet"
                    )
                )
            )
            else -> listOf(
                Prompt(
                    id = "current_state",
                    question = "Where are you starting from today?",
                    choiceOptions = listOf(
                        "Just exploring",
                        "Already underway",
                        "Stuck / restarting"
                    )
                ),
                Prompt(
                    id = "constraint",
                    question = "Any deadline or time constraint that matters?",
                    choiceOptions = listOf(
                        "This week",
                        "This month",
                        "Flexible / none"
                    )
                )
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
        "research" to "Legal research",
        "other" to "Something else"
    )
}

/** Shared minute ladder — every whole integer in range (F3). */
internal object PracticeMinuteChoices {
    const val MIN = 1
    const val MAX = 120
    const val DEFAULT = 20
    const val UNIT = "minutes per day"
    val VALUES: List<Int> = (MIN..MAX).toList()
}
