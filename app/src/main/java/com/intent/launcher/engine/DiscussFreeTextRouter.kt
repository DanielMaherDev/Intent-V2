package com.intent.launcher.engine

import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.RelativeDateAnchoring
import com.intent.launcher.store.IntentionFlowStore

/**
 * Message-composer free-text priority for Discuss after Confirm.
 *
 * Timed activity SaveFact / CorrectFact ([AdaptiveBuilderEngine.KEY_ACTIVITY_REPORT])
 * must win over a pending adaptive Ask and over live Discuss — otherwise a ONE
 * QUESTION practice card consumes utterances like "I ran for 20 minutes today"
 * as chip/Other answers (IV-263 / live §8.5 FAIL on tip 0.0.223).
 */
object DiscussFreeTextRouter {

    enum class Route {
        CREATE,
        ACTIVITY_REPORT,
        ADAPTIVE_ASK,
        MOVE_ESTIMATE_CORRECT,
        DISCUSS
    }

    /**
     * Resolve which path handles [text] from the Message composer.
     * Does not mutate [store].
     */
    fun route(
        createActive: Boolean,
        intentionId: String?,
        text: String,
        store: IntentionFlowStore,
        hasActiveAdaptiveQuestion: Boolean
    ): Route {
        if (createActive) return Route.CREATE
        if (intentionId == null) return Route.DISCUSS
        if (AdaptiveBuilderEngine.shouldHandleActivityReport(store, intentionId, text)) {
            return Route.ACTIVITY_REPORT
        }
        if (hasActiveAdaptiveQuestion) return Route.ADAPTIVE_ASK
        if (RelativeDateAnchoring.looksRelative(text) &&
            store.snapshot().facts.any {
                it.intentionId == intentionId &&
                    it.key == AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE &&
                    it.status == FactStatus.ACTIVE
            }
        ) {
            return Route.MOVE_ESTIMATE_CORRECT
        }
        return Route.DISCUSS
    }
}
