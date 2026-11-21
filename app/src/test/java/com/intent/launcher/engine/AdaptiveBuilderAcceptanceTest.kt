package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.AnswerQuestion
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.ErrandScenario
import com.intent.launcher.domain.FactConfirmation
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.RelativeDateAnchoring
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
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

/**
 * Gate F JVM harness — worksheet §8 scenarios 1, 4, 5, 6, 7, 9 (deterministic).
 * Fixtures cover companion other-cases seeds without inventing plans.
 */
class AdaptiveBuilderAcceptanceTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var storeFile: File
    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L
    private val tz = AdaptiveBuilderFixtures.utc()

    @Before
    fun setUp() {
        storeFile = tmp.newFile("adaptive-builder.json")
        store = IntentionFlowStore(storeFile)
    }

    @Test
    fun relativeDate_anchorsThreeMonths_andFreshnessAfterSimulatedMonth() {
        val uttered = "In about three months."
        val anchored = RelativeDateAnchoring.anchor(uttered, now, tz)
        assertNotNull(anchored)
        assertTrue(anchored!!.approximate)
        assertEquals(3, anchored.amount)
        assertEquals("month", anchored.unit)
        assertTrue(anchored.displayMonthLabel.isNotBlank())

        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        var ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!
        val save = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = ctx.revision,
                factDraft = FactDraft(
                    factId = "fact-move-1",
                    text = uttered,
                    key = AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE,
                    rawUtterance = uttered,
                    approximate = true,
                    confirmation = FactConfirmation.APPROXIMATE
                ),
                sourceTurnId = "turn-move-1"
            ),
            now,
            timeZone = tz
        )
        assertTrue(save.reason, save.accepted)
        val fact = store.snapshot().facts.first { it.id == "fact-move-1" }
        assertNotNull(fact.estimatedEpochMs)
        assertEquals(FactConfirmation.APPROXIMATE, fact.confirmation)

        val later = AdaptiveBuilderEngine.plusDays(now, 30)
        val freshness = RelativeDateAnchoring.freshnessCheck(fact, later, timeZone = tz)
        assertTrue(freshness.needsReconfirm)
        assertFalse(freshness.suggestedPrompt.contains("three months", ignoreCase = true))
        assertTrue(freshness.suggestedPrompt.contains(freshness.originalEstimateLabel))
    }

    // —— §8.1 Interrupted Spanish flow ——
    @Test
    fun scenario1_interruptedSpanish_resumeReconfirmsMove_notReplayAnswered() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID

        fun answer(purposeKey: String, text: String, t: Long, qSuffix: String) {
            val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!
            val ask = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, tz)
            assertTrue(ask is BuilderDecision.Ask)
            val askResult = AdaptiveBuilderEngine.applyDecision(store, ask, t, timeZone = tz)
            assertTrue(askResult.reason, askResult.accepted)
            val afterAsk = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!
            val q = (ask as BuilderDecision.Ask)
            val ans = AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = q.questionId,
                    intentionId = id,
                    normalizedValue = text,
                    rawValue = text,
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-ans-$qSuffix",
                    answeredAtEpochMs = t + 1,
                    idempotencyKey = "idem-$qSuffix",
                    expectedRevision = afterAsk.revision
                ),
                factKey = purposeKey,
                timeZone = tz
            )
            assertTrue(ans.reason, ans.accepted)
        }

        answer(AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE, "Spanish", now + 10, "lang")
        answer(AdaptiveBuilderEngine.KEY_MOTIVATION, "I'm moving to Spain.", now + 20, "mot")
        answer(AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE, "In about three months.", now + 30, "move")

        val mid = store.snapshot()
        assertEquals(3, AdaptiveBuilderEngine.activeFacts(mid, id).size)
        assertTrue(mid.openQuestions.filter { it.intentionId == id }
            .all { it.state == OpenQuestionState.ANSWERED })

        // Persist across "close app"
        val reopened = IntentionFlowStore(storeFile)
        val later = AdaptiveBuilderEngine.plusDays(now, 30)
        val ctx = AdaptiveBuilderEngine.projectContext(reopened.snapshot(), id, later, tz)!!
        val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, later, tz)
        assertTrue("expected reconfirm Ask, got $next", next is BuilderDecision.Ask)
        val reconfirm = next as BuilderDecision.Ask
        assertEquals("reconfirm_move_estimate", reconfirm.purpose)
        assertFalse(reconfirm.whyItMatters.contains("three months", ignoreCase = true))

        // Existing answered questions stay answered — not re-asked as language/motivation.
        assertTrue(
            reopened.snapshot().openQuestions
                .filter { it.intentionId == id && it.state == OpenQuestionState.ANSWERED }
                .size >= 3
        )
    }

    // —— §8.4 Review boundary ——
    @Test
    fun scenario4_reviewBoundary_noSessionUntilApprove_declineKeepsFacts() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        var t = now
        fun rev(): Long =
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision

        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-motivation",
                    text = "Moving to Spain",
                    key = AdaptiveBuilderEngine.KEY_MOTIVATION
                ),
                sourceTurnId = "turn-mot"
            ),
            t,
            timeZone = tz
        )
        t += 5
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-move",
                    text = "In about three months.",
                    key = AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE,
                    rawUtterance = "In about three months.",
                    approximate = true
                ),
                sourceTurnId = "turn-move"
            ),
            t,
            timeZone = tz
        )
        t += 5
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.OfferApproaches(
                intentionId = id,
                expectedRevision = rev(),
                approachDrafts = listOf(
                    com.intent.launcher.domain.ApproachDraft(
                        approachId = "approach-ai-practice",
                        title = "AI conversation practice",
                        summary = "Guided practice"
                    ),
                    com.intent.launcher.domain.ApproachDraft(
                        approachId = "approach-learning-app",
                        title = "Learning app",
                        summary = "App path"
                    )
                )
            ),
            t,
            timeZone = tz
        )
        t += 5
        AdaptiveBuilderEngine.selectApproaches(
            store, id,
            listOf("approach-ai-practice", "approach-learning-app"),
            t, "turn-select", rev(), "TEXT_CHIP"
        )
        t += 5
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-freq",
                    text = "3 days per week",
                    key = AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY
                ),
                sourceTurnId = "turn-freq"
            ),
            t,
            timeZone = tz
        )
        t += 5
        val propose = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
        )
        assertTrue(propose is BuilderDecision.Propose)
        val applied = AdaptiveBuilderEngine.applyDecision(store, propose, t, timeZone = tz)
        assertTrue(applied.reason, applied.accepted)

        var snap = store.snapshot()
        assertEquals(1, snap.proposals.count { it.status == ProposalStatus.PENDING })
        assertTrue(snap.sessions.isEmpty())
        assertEquals(0, snap.sessions.size)

        AdaptiveBuilderEngine.declineProposalKeepFacts(
            store,
            (propose as BuilderDecision.Propose).proposalId,
            t + 10
        )
        snap = store.snapshot()
        assertEquals(ProposalStatus.DECLINED, snap.proposals.first().status)
        assertTrue(snap.sessions.isEmpty())
        assertTrue(
            AdaptiveBuilderEngine.activeFacts(snap, id)
                .any { it.key == AdaptiveBuilderEngine.KEY_MOTIVATION }
        )
        assertTrue(
            snap.approaches.any {
                it.id == "approach-learning-app" && it.status == ApproachStatus.SELECTED
            }
        )
    }

    // —— §8.5 Date correction ——
    @Test
    fun scenario5_dateCorrection_preservesPriorEstimate_auditable() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        val save = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = 0L,
                factDraft = FactDraft(
                    factId = "fact-move-old",
                    text = "In about three months.",
                    key = AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE,
                    rawUtterance = "In about three months.",
                    approximate = true
                ),
                sourceTurnId = "turn-old"
            ),
            now,
            timeZone = tz
        )
        assertTrue(save.accepted)
        val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!.revision
        val correct = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.CorrectFact(
                intentionId = id,
                expectedRevision = rev,
                existingFactId = "fact-move-old",
                replacement = FactDraft(
                    factId = "fact-move-new",
                    text = "In about six months.",
                    key = AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE,
                    rawUtterance = "In about six months.",
                    approximate = true
                ),
                sourceTurnId = "turn-new"
            ),
            now + 50,
            timeZone = tz
        )
        assertTrue(correct.reason, correct.accepted)
        val history = AdaptiveBuilderEngine.correctionHistory(
            store.snapshot(),
            AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE,
            id
        )
        assertEquals(2, history.size)
        assertEquals(FactStatus.CORRECTED, history.first { it.id == "fact-move-old" }.status)
        assertEquals(FactStatus.ACTIVE, history.first { it.id == "fact-move-new" }.status)
        assertEquals("fact-move-old", history.first { it.id == "fact-move-new" }.supersedesFactId)
        assertTrue(
            history.first { it.id == "fact-move-new" }.estimatedEpochMs!! >
                history.first { it.id == "fact-move-old" }.estimatedEpochMs!!
        )
    }

    // —— §8.6 Small errand ——
    @Test
    fun scenario6_smallErrand_todoWithoutLanguageDiscovery() {
        val beforeIntentions = store.snapshot().intentions.size
        val result = ErrandScenario.captureBuyMilkTonight(store, now)
        assertTrue(result.ok)
        assertNotNull(result.todo)
        assertEquals(beforeIntentions, store.snapshot().intentions.size)
        assertTrue(result.intentionCountUnchanged)
        assertTrue(store.snapshot().openQuestions.isEmpty())
        assertFalse(
            store.snapshot().facts.any {
                it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE
            }
        )

        AdaptiveBuilderFixtures.seedErrandFacet(store, now + 1)
        val ctx = AdaptiveBuilderEngine.projectContext(
            store.snapshot(),
            "ab-intention-errand-shell",
            now + 1,
            tz
        )!!
        val decision = AdaptiveBuilderEngine.suggestNextDecision(ctx, now + 1, tz)
        assertTrue(decision is BuilderDecision.OfferAction)
        assertEquals("errand", (decision as BuilderDecision.OfferAction).actionDraft.kind)
        assertFalse(decision is BuilderDecision.Ask)
    }

    // —— §8.7 Broad ambition ——
    @Test
    fun scenario7_broadAmbition_staysDiscovery_noFictionalLaunchChecklist() {
        AdaptiveBuilderFixtures.seedBusinessDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.BUSINESS_ID
        val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!
        val decision = AdaptiveBuilderEngine.suggestNextDecision(ctx, now, tz)
        assertTrue(decision is BuilderDecision.Ask)
        val ask = decision as BuilderDecision.Ask
        assertTrue(
            ask.whyItMatters.contains("premature", ignoreCase = true) ||
                ask.purpose.contains("skill", ignoreCase = true)
        )
        AdaptiveBuilderEngine.applyDecision(store, ask, now, timeZone = tz)
        val snap = store.snapshot()
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first { it.id == id }.phase)
        assertTrue(snap.proposals.none { it.intentionId == id })
        assertTrue(snap.sessions.none { it.intentionId == id })
        assertFalse(
            snap.turns.any {
                it.text.contains("launch checklist", ignoreCase = true) &&
                    !it.text.contains("premature", ignoreCase = true)
            }
        )
    }

    // —— §8.9 Cancellation / stale turn ——
    @Test
    fun scenario9_cancelledAndStaleTurn_cannotMutate() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        val thinkingTurn = "turn-thinking-1"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = AgentTurnLifecycle.cancelMarkerTurnId(thinkingTurn),
                intentionId = id,
                role = "agent",
                text = "Cancelled",
                nowEpochMs = now + 1,
                blocks = listOf(
                    ResponseBlock(
                        blockId = "block-cancel",
                        type = ResponseBlockType.ERROR,
                        title = "Cancelled",
                        body = "User cancelled while thinking",
                        sourceLabel = "adaptive-builder",
                        linkedObjectIds = listOf(thinkingTurn)
                    )
                )
            )
        )
        assertTrue(AgentTurnLifecycle.isCancelled(store.snapshot(), thinkingTurn))

        val rejected = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = 0L,
                factDraft = FactDraft(factId = "fact-stale", text = "Spanish"),
                sourceTurnId = thinkingTurn
            ),
            now + 2,
            cancelledTurnId = thinkingTurn,
            timeZone = tz
        )
        assertFalse(rejected.accepted)
        assertTrue(store.snapshot().facts.none { it.id == "fact-stale" })

        // Answer a later question successfully.
        val ask = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Ask(
                intentionId = id,
                expectedRevision = 0L,
                questionId = "q-later",
                whyItMatters = "Need language",
                answerSchema = com.intent.launcher.domain.AnswerSchema.FREE_TEXT,
                purpose = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE
            ),
            now + 3,
            timeZone = tz
        )
        assertTrue(ask.accepted)
        val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 3, tz)!!.revision
        val answered = AdaptiveBuilderEngine.answerQuestion(
            store,
            AnswerQuestion(
                questionId = "q-later",
                intentionId = id,
                normalizedValue = "Spanish",
                rawValue = "Spanish",
                modality = "FREE_TEXT",
                sourceTurnId = "turn-later",
                answeredAtEpochMs = now + 4,
                idempotencyKey = "idem-later",
                expectedRevision = rev
            ),
            factKey = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
            timeZone = tz
        )
        assertTrue(answered.accepted)

        // Old cancelled decision still cannot write even with a newer revision guess.
        val staleAgain = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = AdaptiveBuilderEngine.projectContext(
                    store.snapshot(), id, now + 5, tz
                )!!.revision,
                factDraft = FactDraft(factId = "fact-from-old-response", text = "should not land"),
                sourceTurnId = thinkingTurn
            ),
            now + 5,
            timeZone = tz
        )
        assertFalse(staleAgain.accepted)
        assertTrue(store.snapshot().facts.none { it.id == "fact-from-old-response" })

        // Stale revision rejected.
        val staleRev = AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = 0L,
                factDraft = FactDraft(factId = "fact-stale-rev", text = "nope"),
                sourceTurnId = "turn-fresh"
            ),
            now + 6,
            timeZone = tz
        )
        assertFalse(staleRev.accepted)
        assertTrue(staleRev.reason.contains("Stale revision", ignoreCase = true))
    }

    @Test
    fun otherCaseFixtures_seedWithoutInventedPlans() {
        listOf(
            "eu_work", "marathon", "smoking", "app_use", "chess", "move", "job_offer"
        ).forEach { caseId ->
            val id = AdaptiveBuilderFixtures.seedOtherCase(store, caseId, now)
            val snap = store.snapshot()
            assertTrue(snap.intentions.any { it.id == id })
            assertEquals(
                IntentionPhase.EXPLORING,
                snap.intentions.first { it.id == id }.phase
            )
            assertTrue(snap.proposals.none { it.intentionId == id })
            assertTrue(snap.sessions.none { it.intentionId == id })
            val ctx = AdaptiveBuilderEngine.projectContext(snap, id, now, tz)
            assertNotNull(ctx)
            assertTrue(ctx!!.typeFacets.isNotEmpty())
        }
    }

    @Test
    fun typedFact_persistsAcrossRestart_stampCompatible() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = AdaptiveBuilderFixtures.SPANISH_ID,
                expectedRevision = 0L,
                factDraft = FactDraft(
                    factId = "fact-persist",
                    text = "Spanish",
                    key = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE
                ),
                sourceTurnId = "turn-persist"
            ),
            now,
            timeZone = tz
        )
        val reopened = IntentionFlowStore(storeFile)
        val fact = reopened.snapshot().facts.first { it.id == "fact-persist" }
        assertEquals(AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE, fact.key)
        assertEquals("turn-persist", fact.sourceTurnId)
        assertEquals(FactStatus.ACTIVE, fact.status)
        assertNotNull(
            reopened.snapshot().adaptiveBuilderStates
                .firstOrNull { it.intentionId == AdaptiveBuilderFixtures.SPANISH_ID }
        )
    }

    @Test
    fun pause_leavesResumableDiscovery_withoutManufacturedPlan() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = 0L,
                factDraft = FactDraft(
                    factId = "fact-lang",
                    text = "Spanish",
                    key = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE
                ),
                sourceTurnId = "turn-lang"
            ),
            now,
            timeZone = tz
        )
        val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!.revision
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.Pause(
                intentionId = id,
                expectedRevision = rev,
                reason = "User left the conversation.",
                resumeCue = "Continue discussion"
            ),
            now + 10,
            timeZone = tz
        )
        val snap = store.snapshot()
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first { it.id == id }.phase)
        assertTrue(snap.sessions.isEmpty())
        assertEquals(
            "paused",
            snap.adaptiveBuilderStates.first { it.intentionId == id }.lifecycleHint
        )
        assertTrue(AdaptiveBuilderEngine.activeFacts(snap, id).any { it.text == "Spanish" })
    }
}
