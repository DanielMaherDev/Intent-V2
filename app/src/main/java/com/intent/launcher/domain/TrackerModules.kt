package com.intent.launcher.domain

/**
 * Shape-default tracker modules — configuration only, not a separate progress DB.
 * Entries live in [IntentionSnapshot.trackerEntries] with explicit provenance.
 * Cessation modules are self-report only — never invented smoke-free streaks.
 *
 * [TrackerFieldType] drives honest entry controls (worksheet §11 seven types).
 * Optional [quantityChoices] prefer a NumberWheel for duration/numeric quantities.
 */
data class TrackerModuleDef(
    val id: String,
    val title: String,
    val prompt: String,
    val fieldType: TrackerFieldType = TrackerFieldType.SINGLE_CHOICE,
    /** Optional unit for numeric / duration display (e.g. "km", "min", "cigarettes"). */
    val unit: String? = null,
    /** Suggested one-tap values for manual entry (not fabricated progress). */
    val suggestedValues: List<String> = emptyList(),
    /** When set, Tracker prefers a NumberWheel for this quantity. */
    val quantityChoices: List<Int>? = null,
    val quantityUnitLabel: String? = null
)

object TrackerModules {

    fun forShape(shapeTag: String?): List<TrackerModuleDef> =
        when (shapeTag) {
            "language" -> listOf(
                TrackerModuleDef(
                    id = "practice_log",
                    title = "Practice log",
                    prompt = "How many minutes did you practise?",
                    fieldType = TrackerFieldType.DURATION,
                    unit = "min",
                    suggestedValues = listOf("10 min", "20 min", "30 min"),
                    quantityChoices = (1..120).toList(),
                    quantityUnitLabel = "minutes"
                ),
                TrackerModuleDef(
                    id = "confidence_note",
                    title = "Confidence note",
                    prompt = "How did speaking feel? (1 low – 5 clear)",
                    fieldType = TrackerFieldType.RATING,
                    suggestedValues = listOf("1", "2", "3", "4", "5")
                ),
                TrackerModuleDef(
                    id = "practice_when",
                    title = "Practice day",
                    prompt = "Which day does this practice apply to?",
                    fieldType = TrackerFieldType.DATE_TIME,
                    suggestedValues = emptyList()
                )
            )
            "marathon" -> listOf(
                TrackerModuleDef(
                    id = "mileage",
                    title = "Mileage",
                    prompt = "Distance you completed (km)?",
                    fieldType = TrackerFieldType.NUMERIC,
                    unit = "km",
                    suggestedValues = listOf("5 km", "10 km", "Rest day held"),
                    quantityChoices = listOf(3, 5, 8, 10, 15, 21),
                    quantityUnitLabel = "km"
                ),
                TrackerModuleDef(
                    id = "body_check",
                    title = "Body check",
                    prompt = "How did the run feel? (1–5)",
                    fieldType = TrackerFieldType.RATING,
                    suggestedValues = listOf("1", "2", "3", "4", "5")
                )
            )
            "business" -> listOf(
                TrackerModuleDef(
                    id = "research_notes",
                    title = "Research notes",
                    prompt = "What did you learn or decide?",
                    fieldType = TrackerFieldType.SINGLE_CHOICE,
                    suggestedValues = listOf(
                        "Talked to one customer",
                        "Updated one-pager",
                        "Parked idea — no force"
                    )
                ),
                TrackerModuleDef(
                    id = "validation_log",
                    title = "Validation log",
                    prompt = "Did you get a useful signal today?",
                    fieldType = TrackerFieldType.YES_NO,
                    suggestedValues = listOf("Yes", "No")
                )
            )
            QuitSmokingScenario.SHAPE_TAG, "quit_smoking" -> listOf(
                TrackerModuleDef(
                    id = "cigarettes_today",
                    title = "Cigarettes today",
                    prompt = "How many cigarettes today?",
                    fieldType = TrackerFieldType.NUMERIC,
                    unit = "cigarettes",
                    suggestedValues = listOf(
                        "0 cigarettes",
                        "Fewer than baseline (8)",
                        "About baseline (8)",
                        "More than usual"
                    ),
                    quantityChoices = (0..20).toList(),
                    quantityUnitLabel = "cigarettes"
                ),
                TrackerModuleDef(
                    id = "craving_log",
                    title = "Craving log",
                    prompt = "How did a craving go?",
                    fieldType = TrackerFieldType.SINGLE_CHOICE,
                    suggestedValues = listOf(
                        "Rode it out",
                        "Smoked one",
                        "Used a substitute",
                        "Hard evening — noted"
                    )
                ),
                TrackerModuleDef(
                    id = "support_check",
                    title = "Support check",
                    prompt = "What helped (or did not)? Pick all that apply.",
                    fieldType = TrackerFieldType.MULTIPLE_CHOICE,
                    suggestedValues = listOf(
                        "Told someone",
                        "Changed routine",
                        "Nothing helped today — noted",
                        "Skipped check-in — honest"
                    )
                )
            )
            ChessScenario.SHAPE_TAG, "chess_rating" -> listOf(
                TrackerModuleDef(
                    id = "rating_self_report",
                    title = "Rating (self-report)",
                    prompt = "What rapid rating will you log? (manual — not Chess.com sync)",
                    fieldType = TrackerFieldType.NUMERIC,
                    unit = "rating",
                    suggestedValues = listOf(
                        "900 rating",
                        "Improved since last log",
                        "About the same",
                        "Dropped — noted honestly"
                    )
                ),
                TrackerModuleDef(
                    id = "practice_log",
                    title = "Practice log",
                    prompt = "How many minutes did you practise?",
                    fieldType = TrackerFieldType.DURATION,
                    unit = "min",
                    suggestedValues = listOf("15 min", "30 min", "45 min", "Skipped — noted honestly"),
                    quantityChoices = (1..120).toList(),
                    quantityUnitLabel = "minutes"
                ),
                TrackerModuleDef(
                    id = "game_review",
                    title = "Game review",
                    prompt = "Did you review a real game you played?",
                    fieldType = TrackerFieldType.YES_NO,
                    suggestedValues = listOf("Yes", "No")
                )
            )
            else -> listOf(
                TrackerModuleDef(
                    id = "general_log",
                    title = "Progress log",
                    prompt = "What happened toward this intention?",
                    fieldType = TrackerFieldType.SINGLE_CHOICE,
                    suggestedValues = listOf("Made progress", "Blocked", "Paused on purpose")
                ),
                TrackerModuleDef(
                    id = "effort_minutes",
                    title = "Effort minutes",
                    prompt = "Minutes of deliberate effort?",
                    fieldType = TrackerFieldType.DURATION,
                    unit = "min",
                    suggestedValues = listOf("15 min", "30 min", "60 min"),
                    quantityChoices = (1..120).toList(),
                    quantityUnitLabel = "minutes"
                )
            )
        }

    fun moduleTitle(moduleId: String, shapeTag: String?): String =
        forShape(shapeTag).firstOrNull { it.id == moduleId }?.title
            ?: moduleId

    /** All seven worksheet field types appear at least once across shape defaults. */
    fun coversAllFieldTypes(): Boolean {
        val types = listOf("language", "marathon", "business", "quit_smoking", "chess_rating", null)
            .flatMap { forShape(it) }
            .map { it.fieldType }
            .toSet()
        return types.containsAll(TrackerFieldType.entries)
    }
}
