package com.intent.launcher.domain

import com.intent.launcher.engine.AdaptiveBuilderEngine
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone

/**
 * Deterministic seeds for adaptive-builder JVM acceptance (§8 + other cases).
 * Not product scripts — fixtures Conversation / tests can call.
 */
object AdaptiveBuilderFixtures {

    const val SPANISH_ID = "ab-intention-spanish"
    const val BUSINESS_ID = "ab-intention-business"
    const val ERRAND_TEXT = "Buy milk tonight"

    fun seedSpanishDiscovery(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        intentionId: String = SPANISH_ID
    ) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = intentionId,
                title = "Learn Spanish",
                statement = "I want to learn a language.",
                shapeTag = "language",
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.SetAdaptiveBuilderState(
                state = AdaptiveBuilderState(
                    intentionId = intentionId,
                    revision = 0L,
                    typeFacets = AdaptiveBuilderEngine.facetsForShape("language"),
                    lifecycleHint = "discovery",
                    updatedAtEpochMs = nowEpochMs
                ),
                nowEpochMs = nowEpochMs
            )
        )
    }

    fun seedBusinessDiscovery(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        intentionId: String = BUSINESS_ID
    ) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = intentionId,
                title = "Build a business",
                statement = "I want to start a side business making €1,000 a month.",
                shapeTag = "business",
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.SetAdaptiveBuilderState(
                state = AdaptiveBuilderState(
                    intentionId = intentionId,
                    revision = 0L,
                    typeFacets = listOf("exploration", "ambition"),
                    lifecycleHint = "discovery",
                    updatedAtEpochMs = nowEpochMs
                ),
                nowEpochMs = nowEpochMs
            )
        )
    }

    fun seedErrandFacet(
        store: IntentionFlowStore,
        nowEpochMs: Long,
        intentionId: String = "ab-intention-errand-shell"
    ) {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = intentionId,
                title = "Quick capture shell",
                statement = ERRAND_TEXT,
                shapeTag = "errand",
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.SetAdaptiveBuilderState(
                state = AdaptiveBuilderState(
                    intentionId = intentionId,
                    revision = 0L,
                    typeFacets = listOf("one_off_action"),
                    lifecycleHint = "discovery",
                    updatedAtEpochMs = nowEpochMs
                ),
                nowEpochMs = nowEpochMs
            )
        )
    }

    /** Other-cases companion fixtures — seed only, no hardcoded plans. */
    fun seedOtherCase(
        store: IntentionFlowStore,
        caseId: String,
        nowEpochMs: Long
    ): String {
        val (id, title, statement, shape, facets) = when (caseId) {
            "eu_work" -> FixtureSpec(
                "ab-eu-work",
                "Work legally in Spain",
                "Help me complete what I need to work legally here.",
                "research",
                AdaptiveBuilderEngine.facetsForShape("research")
            )
            "marathon" -> FixtureSpec(
                "ab-marathon",
                "Run a marathon",
                "I want to run a marathon in eight months.",
                "marathon",
                AdaptiveBuilderEngine.facetsForShape("marathon")
            )
            "smoking" -> FixtureSpec(
                "ab-smoking",
                "Reduce smoking",
                "I smoke about eight cigarettes a day and want to quit.",
                "cessation",
                AdaptiveBuilderEngine.facetsForShape("cessation")
            )
            "app_use" -> FixtureSpec(
                "ab-app-use",
                "Limit short-form video",
                "Keep Instagram and YouTube Shorts under 45 minutes a day.",
                "app_use",
                AdaptiveBuilderEngine.facetsForShape("app_use")
            )
            "chess" -> FixtureSpec(
                "ab-chess",
                "Raise Chess.com rapid rating",
                "Raise my Chess.com rapid rating from 900 to 1200.",
                "chess",
                AdaptiveBuilderEngine.facetsForShape("chess")
            )
            "move" -> FixtureSpec(
                "ab-move",
                "Move to Valencia",
                "Move from Barcelona to Valencia within six weeks.",
                "move",
                AdaptiveBuilderEngine.facetsForShape("move")
            )
            "job_offer" -> FixtureSpec(
                "ab-job-offer",
                "Decide on job offer",
                "Help me decide whether to accept a job offer. I have ten days.",
                "decision",
                AdaptiveBuilderEngine.facetsForShape("decision")
            )
            else -> FixtureSpec(
                "ab-other-$caseId",
                caseId,
                caseId,
                "other",
                listOf("exploration")
            )
        }
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = id,
                title = title,
                statement = statement,
                shapeTag = shape,
                nowEpochMs = nowEpochMs
            )
        )
        store.apply(
            FlowCommand.SetAdaptiveBuilderState(
                state = AdaptiveBuilderState(
                    intentionId = id,
                    revision = 0L,
                    typeFacets = facets,
                    lifecycleHint = "discovery",
                    updatedAtEpochMs = nowEpochMs
                ),
                nowEpochMs = nowEpochMs
            )
        )
        return id
    }

    fun utc(): TimeZone = TimeZone.getTimeZone("UTC")

    private data class FixtureSpec(
        val id: String,
        val title: String,
        val statement: String,
        val shape: String,
        val facets: List<String>
    )
}
