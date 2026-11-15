package com.intent.launcher.engine

import com.intent.launcher.domain.IntentionSnapshot
import com.intent.launcher.domain.TrackerModules

/**
 * §17 case 21 — multiple target ambiguity.
 * “Log 20 minutes” with more than one plausible tracker → ask; never pick arbitrarily.
 */
object AmbiguousTrackerResolver {

    data class Candidate(
        val intentionId: String,
        val intentionTitle: String,
        val moduleId: String,
        val moduleTitle: String,
        val shapeTag: String
    )

    sealed class Outcome {
        data class Resolved(
            val intentionId: String,
            val moduleId: String
        ) : Outcome()

        data class Ambiguous(
            val candidates: List<Candidate>,
            val message: String
        ) : Outcome()

        data class None(
            val message: String
        ) : Outcome()
    }

    fun resolve(
        snapshot: IntentionSnapshot,
        utterance: String,
        explicitIntentionId: String? = null,
        explicitModuleId: String? = null
    ): Outcome {
        if (!explicitIntentionId.isNullOrBlank() && !explicitModuleId.isNullOrBlank()) {
            val exists = snapshot.intentions.any { it.id == explicitIntentionId }
            if (!exists) {
                return Outcome.None("Intention $explicitIntentionId is not in the local store")
            }
            return Outcome.Resolved(explicitIntentionId, explicitModuleId)
        }
        if (!explicitIntentionId.isNullOrBlank() && explicitModuleId.isNullOrBlank()) {
            val intention = snapshot.intentions.firstOrNull { it.id == explicitIntentionId }
                ?: return Outcome.None("Intention $explicitIntentionId is not in the local store")
            val modules = TrackerModules.forShape(intention.shapeTag)
            return when {
                modules.isEmpty() -> Outcome.None("No tracker modules for ${intention.title}")
                modules.size == 1 -> Outcome.Resolved(intention.id, modules.first().id)
                else -> Outcome.Ambiguous(
                    candidates = modules.map {
                        Candidate(
                            intentionId = intention.id,
                            intentionTitle = intention.title,
                            moduleId = it.id,
                            moduleTitle = it.title,
                            shapeTag = intention.shapeTag
                        )
                    },
                    message = "Which field on ${intention.title} should receive “$utterance”?"
                )
            }
        }

        val durationLike = looksLikeDurationLog(utterance)
        val candidates = mutableListOf<Candidate>()
        for (intention in snapshot.intentions) {
            val modules = TrackerModules.forShape(intention.shapeTag)
            for (module in modules) {
                val durationModule = module.id in DURATION_MODULE_IDS ||
                    module.title.contains("practice", ignoreCase = true) ||
                    module.title.contains("mileage", ignoreCase = true) ||
                    module.prompt.contains("minute", ignoreCase = true) ||
                    module.prompt.contains("Distance", ignoreCase = true)
                if (durationLike && durationModule) {
                    candidates += Candidate(
                        intentionId = intention.id,
                        intentionTitle = intention.title,
                        moduleId = module.id,
                        moduleTitle = module.title,
                        shapeTag = intention.shapeTag
                    )
                }
            }
        }
        val distinctTargets = candidates.distinctBy { "${it.intentionId}:${it.moduleId}" }
        return when {
            distinctTargets.isEmpty() ->
                Outcome.None("No matching tracker for “$utterance” — ask which intention and field.")
            distinctTargets.size == 1 ->
                Outcome.Resolved(distinctTargets.first().intentionId, distinctTargets.first().moduleId)
            else -> Outcome.Ambiguous(
                candidates = distinctTargets,
                message = "“$utterance” could apply to more than one tracker — " +
                    "which intention and field should I use? I will not pick arbitrarily."
            )
        }
    }

    private val DURATION_MODULE_IDS = setOf(
        "practice_log",
        "mileage",
        "rating_self_report"
    )

    private fun looksLikeDurationLog(utterance: String): Boolean {
        val t = utterance.lowercase()
        return t.contains("minute") ||
            t.contains("min") ||
            t.contains("log ") ||
            Regex("\\d+\\s*(m|min|minutes|km|hours?)").containsMatchIn(t)
    }
}
