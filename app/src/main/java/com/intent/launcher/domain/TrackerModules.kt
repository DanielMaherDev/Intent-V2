package com.intent.launcher.domain

/**
 * Shape-default tracker modules — configuration only, not a separate progress DB.
 * Entries live in [IntentionSnapshot.trackerEntries] with explicit provenance.
 * Cessation modules are SAID self-report only — never invented smoke-free streaks.
 */
data class TrackerModuleDef(
    val id: String,
    val title: String,
    val prompt: String,
    /** Suggested one-tap values for manual SAID entry (not fabricated progress). */
    val suggestedValues: List<String> = emptyList()
)

object TrackerModules {

    fun forShape(shapeTag: String?): List<TrackerModuleDef> =
        when (shapeTag) {
            "language" -> listOf(
                TrackerModuleDef(
                    id = "practice_log",
                    title = "Practice log",
                    prompt = "What did you practise?",
                    suggestedValues = listOf(
                        "Spoke for 10 minutes",
                        "Reviewed vocabulary",
                        "Tutor session done"
                    )
                ),
                TrackerModuleDef(
                    id = "confidence_note",
                    title = "Confidence note",
                    prompt = "How did speaking feel?",
                    suggestedValues = listOf(
                        "Felt clearer than last time",
                        "Stuck on vocabulary",
                        "Ready for work small-talk"
                    )
                )
            )
            "marathon" -> listOf(
                TrackerModuleDef(
                    id = "mileage",
                    title = "Mileage",
                    prompt = "Distance or effort you completed?",
                    suggestedValues = listOf(
                        "Easy 5 km",
                        "Long run done",
                        "Rest day held"
                    )
                ),
                TrackerModuleDef(
                    id = "body_check",
                    title = "Body check",
                    prompt = "How did the run feel?",
                    suggestedValues = listOf(
                        "Legs felt strong",
                        "Needed more recovery",
                        "Skipped — noted honestly"
                    )
                )
            )
            "business" -> listOf(
                TrackerModuleDef(
                    id = "research_notes",
                    title = "Research notes",
                    prompt = "What did you learn or decide?",
                    suggestedValues = listOf(
                        "Talked to one customer",
                        "Updated one-pager",
                        "Parked idea — no force"
                    )
                ),
                TrackerModuleDef(
                    id = "validation_log",
                    title = "Validation log",
                    prompt = "Evidence of interest?",
                    suggestedValues = listOf(
                        "One useful conversation",
                        "No signal yet",
                        "Need a clearer offer"
                    )
                )
            )
            QuitSmokingScenario.SHAPE_TAG, "quit_smoking" -> listOf(
                TrackerModuleDef(
                    id = "cigarettes_today",
                    title = "Cigarettes today",
                    prompt = "How many cigarettes today? (self-report only)",
                    suggestedValues = listOf(
                        "0 today (self-reported)",
                        "Fewer than baseline (8)",
                        "About baseline (8)",
                        "More than usual"
                    )
                ),
                TrackerModuleDef(
                    id = "craving_log",
                    title = "Craving log",
                    prompt = "How did a craving go?",
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
                    prompt = "What helped (or did not)?",
                    suggestedValues = listOf(
                        "Told someone",
                        "Changed routine",
                        "Nothing helped today — noted",
                        "Skipped check-in — honest"
                    )
                )
            )
            else -> listOf(
                TrackerModuleDef(
                    id = "general_log",
                    title = "Progress log",
                    prompt = "What happened toward this intention?",
                    suggestedValues = listOf("Made progress", "Blocked", "Paused on purpose")
                )
            )
        }

    fun moduleTitle(moduleId: String, shapeTag: String?): String =
        forShape(shapeTag).firstOrNull { it.id == moduleId }?.title
            ?: moduleId
}
