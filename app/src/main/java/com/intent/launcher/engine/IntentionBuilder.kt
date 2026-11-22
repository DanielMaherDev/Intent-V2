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
            // Usage / Shorts caps — before language so “Instagram” ≠ Learn Spanish.
            CreatePathSeedHonesty.looksLikeUsageAppTimeSeed(t) -> "app_use"
            // OC4 habit / check-in measurement — before generic “other”.
            CreatePathSeedHonesty.looksLikeHabitCheckInSeed(t) -> "cessation"
            // OC6 — connected partner metric before language (chess ≠ “tutor” language).
            CreatePathSeedHonesty.looksLikeConnectedMetricSeed(t) -> "chess"
            // Time-limited decision (decide-by date) — before business so “offer”
            // does not land as a startup exploration.
            looksLikeTimeLimitedDecision(t) -> "decision"
            // OC7 — finite relocation / lease-bound move before language (“move to Spain”).
            looksLikeFiniteMoveProject(t) -> "move"
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
     * Generic decide-by-date support: help decide under a deadline without assuming
     * a particular outcome (accept/decline/etc.).
     */
    internal fun looksLikeTimeLimitedDecision(textLower: String): Boolean {
        val t = textLower.lowercase()
        val decide = t.contains("decide") || t.contains("whether to") ||
            t.contains("should i") || t.contains("help me choose") ||
            t.contains("make a decision")
        val deadline = t.contains("deadline") || t.contains("due") ||
            Regex("""\b\d+\s*days?\b""").containsMatchIn(t) ||
            t.contains("ten days") || t.contains("two weeks") ||
            t.contains("by friday") || t.contains("by monday")
        val offerOrChoice = t.contains("offer") || t.contains("accept") ||
            t.contains("decline") || t.contains("between") || t.contains("option")
        return decide && (deadline || offerOrChoice)
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

    /**
     * Finite relocation / lease-bound housing project with external waits
     * (landlord, viewing, movers). Generic cues only — no city-pair hardcoding.
     */
    internal fun looksLikeFiniteMoveProject(textLower: String): Boolean {
        val t = textLower.lowercase()
        val relocate = listOf(
            "move from", "move to", "moving from", "moving to",
            "relocate", "relocation", "move house", "moving house"
        ).any { t.contains(it) }
        val housing = listOf(
            "lease", "rent", "renting", "landlord", "apartment", "flat",
            "one-bedroom", "1-bedroom", "studio", "haven't found a",
            "have not found a", "new place", "place to live"
        ).any { t.contains(it) }
        val deadline = listOf(
            "week", "weeks", "month", "months", "deadline", "by ",
            "within", "lease ends", "lease end"
        ).any { t.contains(it) }
        if (relocate && (housing || deadline)) return true
        if (t.contains("lease ends") && housing) return true
        return false
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
            "app_use" -> "Usage target"
            "decision" -> "Make a decision by a date"
            "move" -> "Plan a move"
            "chess" -> "Raise a connected rating"
            "cessation" -> "Reduce a habit"
            else -> "New intention"
        }

    fun missingFactPrompts(shapeTag: String): List<Prompt> =
        when (shapeTag) {
            "app_use" -> listOf(
                Prompt(
                    id = CreatePathSeedHonesty.PROMPT_USAGE_ACCESS,
                    question = CreatePathSeedHonesty.USAGE_ACCESS_QUESTION,
                    choiceOptions = CreatePathSeedHonesty.USAGE_ACCESS_OPTIONS
                ),
                Prompt(
                    id = CreatePathSeedHonesty.PROMPT_USAGE_INTERVENTION,
                    question = CreatePathSeedHonesty.USAGE_INTERVENTION_QUESTION,
                    choiceOptions = CreatePathSeedHonesty.USAGE_INTERVENTION_OPTIONS
                )
            )
            "move" -> listOf(
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_LEASE_DEADLINE,
                    question =
                        "What's the latest date you need to leave your current place?",
                    choiceOptions = listOf(
                        "Lease ends in about six weeks",
                        "Within a month",
                        "Flexible — still deciding"
                    )
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_HOUSING_REQUIREMENTS,
                    question =
                        "What budget and location requirements should shape the search?",
                    choiceOptions = listOf(
                        "One-bedroom under my rent budget",
                        "Studio or shared — keep costs low",
                        "Still deciding budget / area"
                    )
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_NOTICE_STATUS,
                    question =
                        "Have you given notice, and will you move yourself or use a mover?",
                    choiceOptions = listOf(
                        "Notice not given yet",
                        "Notice given",
                        "Not sure about movers yet"
                    )
                )
            )
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
                    id = AdaptiveBuilderEngine.KEY_RACE_STATUS,
                    question =
                        "Do you already have a race and date, or is the event still to be chosen?",
                    choiceOptions = listOf(
                        "Race chosen with date",
                        "Event still to choose",
                        "Not sure"
                    )
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_ACTIVITY_LOG_METHOD,
                    question =
                        "For the first phase, how should we record runs? " +
                            "Connect an app only if one is installed — otherwise log manually.",
                    choiceOptions = listOf(
                        "Choose race",
                        "Log runs manually",
                        "Connect an installed running app"
                    )
                )
            )
            "business" -> listOf(
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS,
                    question =
                        "What skills, interests, or problems do you already know well? " +
                            "Without an idea chosen, a launch checklist would be premature."
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET,
                    question = "How much time can you put in each week?",
                    choiceOptions = AdaptiveBuilderEngine.WEEKLY_TIME_OPTIONS
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_EXPLORATION_MODE,
                    question =
                        "Would you rather brainstorm opportunities, examine problems " +
                            "people already pay to solve, or test one idea quickly?",
                    choiceOptions = AdaptiveBuilderEngine.EXPLORATION_MODE_OPTIONS
                )
            )
            "decision" -> listOf(
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_DECIDE_BY_DATE,
                    question =
                        "When exactly is the response due? INTENT helps you decide by " +
                            "that date — it will not assume the answer is yes.",
                    choiceOptions = listOf(
                        "In about ten days",
                        "This week",
                        "This month"
                    )
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_DECISION_CRITERIA,
                    question =
                        "What matters most for this decision: pay, work, flexibility, " +
                            "growth, location, or something else?",
                    choiceOptions = listOf(
                        "Pay / compensation",
                        "Remote flexibility",
                        "Work / role fit",
                        "Growth",
                        "Location"
                    )
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_DECISION_ALTERNATIVES,
                    question =
                        "Do you have another offer or a current role to compare against?",
                    choiceOptions = listOf(
                        "Current role / job",
                        "Another offer",
                        "No alternative yet"
                    )
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_DECISION_UNKNOWNS,
                    question =
                        "What information is still missing? (Unknown information stays listed — " +
                            "we can propose asking the source.)",
                    choiceOptions = listOf(
                        "Remote / hybrid policy",
                        "Total compensation details",
                        "Team / manager fit",
                        "Nothing unknown right now"
                    )
                )
            )
            "chess" -> listOf(
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_RATING_BASELINE,
                    question =
                        "How should we set the rating baseline? Prefer a real import " +
                            "when connected; otherwise a manual SAID baseline.",
                    choiceOptions = CreatePathSeedHonesty.RATING_BASELINE_OPTIONS
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_PRACTICE_PREFERENCE,
                    question =
                        "What practice mix do you want? Practice stays distinct from " +
                            "rating — a played game is not proof the rating improved.",
                    choiceOptions = CreatePathSeedHonesty.PRACTICE_PREFERENCE_OPTIONS
                )
            )
            "cessation" -> listOf(
                Prompt(
                    id = CreatePathSeedHonesty.PROMPT_HABIT_START,
                    question =
                        "Would you rather track a baseline, discuss approaches, or do both?",
                    choiceOptions = CreatePathSeedHonesty.HABIT_START_OPTIONS
                ),
                Prompt(
                    id = AdaptiveBuilderEngine.KEY_HABIT_BASELINE,
                    question = "About how many per day is your current baseline count?",
                    quantityChoices = (0..40).toList(),
                    quantityUnitLabel = "per day",
                    quantitySummaryTemplate = "{value} {unit}"
                ),
                Prompt(
                    id = "craving_with_checkin",
                    question =
                        "Include an optional craving intensity rating (0–10) with each check-in?",
                    choiceOptions = listOf(
                        "Yes — count and craving",
                        "Count only"
                    )
                ),
                Prompt(
                    id = "check_in_cadence",
                    question =
                        "For the first week, when should a short daily check-in happen?",
                    choiceOptions = listOf(
                        "Evening",
                        "Morning",
                        "Skip for now"
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
        "app_use" to "App usage",
        "decision" to "Time-limited decision",
        "move" to "Move / relocation",
        "chess" to "Connected rating",
        "cessation" to "Habit change",
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
