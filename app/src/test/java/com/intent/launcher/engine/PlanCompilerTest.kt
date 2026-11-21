package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.ApproachDraft
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlanCompilerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L
    private val tz = AdaptiveBuilderFixtures.utc()

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("plan-compiler.json"))
    }

    @Test
    fun compile_fromApproachesAndTiming_validatesRecurrenceAndTimezone() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.OfferApproaches(
                intentionId = id,
                expectedRevision = 0L,
                approachDrafts = listOf(
                    ApproachDraft(
                        approachId = "approach-ai-practice",
                        title = "AI conversation practice",
                        summary = "Guided",
                        capabilityNote = "Guided session objective + typed result."
                    ),
                    ApproachDraft(
                        approachId = "approach-learning-app",
                        title = "Duolingo",
                        summary = "App",
                        capabilityNote = "Can open Duolingo; lesson completion requires your check-in."
                    )
                )
            ),
            now,
            timeZone = tz
        )
        AdaptiveBuilderEngine.selectApproaches(
            store, id,
            listOf("approach-ai-practice", "approach-learning-app"),
            now + 1, "turn-sel", 1L, "TEXT_CHIP"
        )
        var rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 2, tz)!!.revision
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev,
                factDraft = FactDraft(
                    factId = "f-freq",
                    text = "3 days per week",
                    key = AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY
                ),
                sourceTurnId = "t-freq"
            ),
            now + 2,
            timeZone = tz
        )
        rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 3, tz)!!.revision
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev,
                factDraft = FactDraft(
                    factId = "f-dur",
                    text = "20 minutes",
                    key = AdaptiveBuilderEngine.KEY_PRACTICE_DURATION,
                    normalizedValue = "20"
                ),
                sourceTurnId = "t-dur"
            ),
            now + 3,
            timeZone = tz
        )
        rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 4, tz)!!.revision
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev,
                factDraft = FactDraft(
                    factId = "f-time",
                    text = "19:00",
                    key = AdaptiveBuilderEngine.KEY_PRACTICE_TIME,
                    normalizedValue = "19:00"
                ),
                sourceTurnId = "t-time"
            ),
            now + 4,
            timeZone = tz
        )

        val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 5, tz)!!
        val draft = PlanCompiler.compile(ctx)
        assertNotNull(draft)
        assertEquals(20, draft!!.durationMinutes)
        assertEquals(19, draft.hour)
        assertEquals(0, draft.minute)
        assertEquals(listOf(1, 3, 5), draft.daysOfWeek)
        assertTrue(draft.methodsSummary.contains("AI conversation practice"))
        assertTrue(draft.capabilityHonesty.contains("check-in", ignoreCase = true))
        assertFalse(draft.includeMorningCheckIn)
        assertFalse(draft.includeEveningReview)
        assertEquals("ok", PlanCompiler.validate(
            draft.daysOfWeek, draft.hour, draft.minute, draft.durationMinutes, draft.timeZoneId
        ).reason)

        val decide = PlanCompiler.toProposeDecision(draft, id, ctx.revision)
        assertEquals(draft.proposalId, decide.proposalId)
        assertEquals(draft.methodsSummary, decide.methodsSummary)
    }

    @Test
    fun validate_rejectsInvalidWeekdayAndBlankTimezone() {
        assertFalse(PlanCompiler.validate(emptyList(), 10, 0, 20, "UTC").ok)
        assertFalse(PlanCompiler.validate(listOf(9), 10, 0, 20, "UTC").ok)
        assertFalse(PlanCompiler.validate(listOf(1), 25, 0, 20, "UTC").ok)
        assertFalse(PlanCompiler.validate(listOf(1), 10, 0, 0, "UTC").ok)
        assertFalse(PlanCompiler.validate(listOf(1), 10, 0, 20, "").ok)
    }

    @Test
    fun parseDays_everydayAndNamed() {
        assertEquals((1..7).toList(), PlanCompiler.parseDays(null, "Everyday"))
        assertEquals(listOf(1, 3, 5), PlanCompiler.parseDays("Mon Wed Fri", "custom"))
    }
}
