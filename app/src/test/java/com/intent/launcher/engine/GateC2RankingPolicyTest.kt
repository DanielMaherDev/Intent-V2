package com.intent.launcher.engine

import com.intent.launcher.domain.AdaptiveBuilderFixtures
import com.intent.launcher.domain.AnswerQuestion
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderDecision
import com.intent.launcher.domain.FactConfirmation
import com.intent.launcher.domain.FactDraft
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.domain.RelativeDateAnchoring
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gate C2 — rank questions by consequence; avoid duplicates / stale / low-value;
 * no fixed questionnaire length ([AdaptiveBuilderEngine.suggestNextDecision]).
 *
 * JVM-only: no live OpenAI / AVD.
 */
class GateC2RankingPolicyTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var storeFile: File
    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_300_000L
    private val tz = AdaptiveBuilderFixtures.utc()

    @Before
    fun setUp() {
        storeFile = tmp.newFile("gate-c2-ranking.json")
        store = IntentionFlowStore(storeFile)
    }

    @Test
    fun ranking_prefersHigherConsequenceAsks_inPriorityOrder() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        val asks = mutableListOf<BuilderDecision.Ask>()

        fun nextAsk(t: Long): BuilderDecision.Ask {
            val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!
            val decision = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, tz)
            assertTrue("expected Ask at t=$t, got $decision", decision is BuilderDecision.Ask)
            val ask = decision as BuilderDecision.Ask
            val applied = AdaptiveBuilderEngine.applyDecision(store, ask, t, timeZone = tz)
            assertTrue(applied.reason, applied.accepted)
            return ask
        }

        fun answer(ask: BuilderDecision.Ask, text: String, t: Long, suffix: String) {
            val afterAsk = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!
            val ans = AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = ask.questionId,
                    intentionId = id,
                    normalizedValue = text,
                    rawValue = text,
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-c2-$suffix",
                    answeredAtEpochMs = t + 1,
                    idempotencyKey = "idem-c2-$suffix",
                    expectedRevision = afterAsk.revision
                ),
                factKey = ask.purpose,
                timeZone = tz
            )
            assertTrue(ans.reason, ans.accepted)
        }

        val language = nextAsk(now + 10)
        asks += language
        assertEquals(AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE, language.purpose)
        assertEquals(90, language.priority)
        answer(language, "Spanish", now + 10, "lang")

        val motivation = nextAsk(now + 20)
        asks += motivation
        assertEquals(AdaptiveBuilderEngine.KEY_MOTIVATION, motivation.purpose)
        assertEquals(80, motivation.priority)
        assertTrue(motivation.priority < language.priority)
        answer(motivation, "I'm moving to Spain.", now + 20, "mot")

        val move = nextAsk(now + 30)
        asks += move
        assertEquals(AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE, move.purpose)
        assertEquals(70, move.priority)
        assertTrue(move.priority < motivation.priority)
        answer(move, "In about three months.", now + 30, "move")

        // After discovery facts: approaches (not a lower-priority explore Ask).
        val afterMove = AdaptiveBuilderEngine.projectContext(
            store.snapshot(), id, now + 40, tz
        )!!
        val approaches = AdaptiveBuilderEngine.suggestNextDecision(afterMove, now + 40, tz)
        assertTrue(
            "expected OfferApproaches after move fact, got $approaches",
            approaches is BuilderDecision.OfferApproaches
        )

        assertEquals(
            listOf(
                AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                AdaptiveBuilderEngine.KEY_MOTIVATION,
                AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE
            ),
            asks.map { it.purpose }
        )
        assertEquals(listOf(90, 80, 70), asks.map { it.priority })
    }

    @Test
    fun ranking_staleMoveReconfirm_outranksLowerConsequenceAsks() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        var t = now
        fun rev(): Long =
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision

        // Seed high-value facts including a relative move estimate.
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-c2-lang",
                    text = "Spanish",
                    key = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                    normalizedValue = "Spanish"
                ),
                sourceTurnId = "turn-c2-seed-lang"
            ),
            t,
            timeZone = tz
        )
        t += 2
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-c2-mot",
                    text = "Moving to Spain",
                    key = AdaptiveBuilderEngine.KEY_MOTIVATION,
                    normalizedValue = "Moving to Spain"
                ),
                sourceTurnId = "turn-c2-seed-mot"
            ),
            t,
            timeZone = tz
        )
        t += 2
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-c2-move",
                    text = "In about three months.",
                    key = AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE,
                    rawUtterance = "In about three months.",
                    approximate = true,
                    confirmation = FactConfirmation.APPROXIMATE
                ),
                sourceTurnId = "turn-c2-seed-move"
            ),
            t,
            timeZone = tz
        )

        val later = AdaptiveBuilderEngine.plusDays(now, 30)
        val moveFact = store.snapshot().facts.first {
            it.id == "fact-c2-move"
        }
        assertTrue(
            RelativeDateAnchoring.freshnessCheck(moveFact, later, timeZone = tz).needsReconfirm
        )

        val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, later, tz)!!
        val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, later, tz)
        assertTrue("expected reconfirm Ask, got $next", next is BuilderDecision.Ask)
        val reconfirm = next as BuilderDecision.Ask
        assertEquals("reconfirm_move_estimate", reconfirm.purpose)
        assertEquals(100, reconfirm.priority)
        // Stale absolute phrasing must not be presented as current.
        assertFalse(reconfirm.whyItMatters.contains("three months", ignoreCase = true))
        // Not a low-value language re-ask.
        assertFalse(reconfirm.purpose == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE)
    }

    @Test
    fun duplicateGuard_skipsAlreadyAskedAndAnsweredPurposes() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID

        val ctx0 = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!
        val first = AdaptiveBuilderEngine.suggestNextDecision(ctx0, now, tz)
        assertTrue(first is BuilderDecision.Ask)
        val languageAsk = first as BuilderDecision.Ask
        assertEquals(AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE, languageAsk.purpose)
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, languageAsk, now, timeZone = tz).accepted
        )

        // Same purpose still unresolved → do not mint a second language Ask.
        val ctx1 = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 1, tz)!!
        val whilePending = AdaptiveBuilderEngine.suggestNextDecision(ctx1, now + 1, tz)
        val pendingAsk = whilePending as? BuilderDecision.Ask
        assertFalse(
            "duplicate language Ask while pending: $whilePending",
            pendingAsk?.purpose == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE &&
                pendingAsk.questionId != languageAsk.questionId
        )
        assertFalse(
            "must not invent motivation before language is answered: $whilePending",
            pendingAsk?.purpose == AdaptiveBuilderEngine.KEY_MOTIVATION
        )

        val afterAsk = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 2, tz)!!
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = languageAsk.questionId,
                    intentionId = id,
                    normalizedValue = "Spanish",
                    rawValue = "Spanish",
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-c2-dup-ans",
                    answeredAtEpochMs = now + 3,
                    idempotencyKey = "idem-c2-dup",
                    expectedRevision = afterAsk.revision
                ),
                factKey = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                timeZone = tz
            ).accepted
        )

        val ctx2 = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 4, tz)!!
        val afterAnswer = AdaptiveBuilderEngine.suggestNextDecision(ctx2, now + 4, tz)
        assertTrue(afterAnswer is BuilderDecision.Ask)
        val motivation = afterAnswer as BuilderDecision.Ask
        assertEquals(AdaptiveBuilderEngine.KEY_MOTIVATION, motivation.purpose)
        assertFalse(motivation.purpose == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE)

        // Answered language open-question stays ANSWERED — never re-opened as Ask.
        assertTrue(
            store.snapshot().openQuestions.any {
                it.id == languageAsk.questionId && it.state == OpenQuestionState.ANSWERED
            }
        )
    }

    @Test
    fun noFixedQuestionnaireLength_errandZero_ambitionOne_spanishVariesThenPauseOrPropose() {
        // Errand: zero discovery Asks.
        AdaptiveBuilderFixtures.seedErrandFacet(store, now)
        val errandCtx = AdaptiveBuilderEngine.projectContext(
            store.snapshot(),
            "ab-intention-errand-shell",
            now,
            tz
        )!!
        val errand = AdaptiveBuilderEngine.suggestNextDecision(errandCtx, now, tz)
        assertTrue(errand is BuilderDecision.OfferAction)
        assertEquals(0, errandCtx.openQuestions.size)

        // Ambition: consequence-driven explore Asks (skills → time → mode), then
        // Explore ideas OfferAction / Pause — not a fixed N-question script or launch plan.
        storeFile = tmp.newFile("gate-c2-ambition.json")
        store = IntentionFlowStore(storeFile)
        AdaptiveBuilderFixtures.seedBusinessDiscovery(store, now)
        val bizId = AdaptiveBuilderFixtures.BUSINESS_ID
        val bizAsk = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), bizId, now, tz)!!,
            now,
            tz
        )
        assertTrue(bizAsk is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS,
            (bizAsk as BuilderDecision.Ask).purpose
        )
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, bizAsk, now, timeZone = tz).accepted
        )
        val afterBizAsk = AdaptiveBuilderEngine.projectContext(
            store.snapshot(), bizId, now + 1, tz
        )!!
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = bizAsk.questionId,
                    intentionId = bizId,
                    normalizedValue = "Teaching people online",
                    rawValue = "Teaching people online",
                    modality = "FREE_TEXT",
                    sourceTurnId = "turn-c2-biz-ans",
                    answeredAtEpochMs = now + 2,
                    idempotencyKey = "idem-c2-biz",
                    expectedRevision = afterBizAsk.revision
                ),
                factKey = AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS,
                timeZone = tz
            ).accepted
        )
        // Next consequential Ask is weekly time — not a fictional launch checklist.
        val afterBizFact = AdaptiveBuilderEngine.projectContext(
            store.snapshot(), bizId, now + 3, tz
        )!!
        val bizNext = AdaptiveBuilderEngine.suggestNextDecision(afterBizFact, now + 3, tz)
        assertTrue(
            "ambition should Ask weekly time after skills, got $bizNext",
            bizNext is BuilderDecision.Ask &&
                (bizNext as BuilderDecision.Ask).purpose ==
                AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET
        )
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, bizNext, now + 3, timeZone = tz).accepted
        )
        val afterTimeAsk = AdaptiveBuilderEngine.projectContext(
            store.snapshot(), bizId, now + 4, tz
        )!!
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = (bizNext as BuilderDecision.Ask).questionId,
                    intentionId = bizId,
                    normalizedValue = "About five hours a week",
                    rawValue = "About five hours a week",
                    modality = "CHIP",
                    sourceTurnId = "turn-c2-biz-time",
                    answeredAtEpochMs = now + 5,
                    idempotencyKey = "idem-c2-biz-time",
                    expectedRevision = afterTimeAsk.revision
                ),
                factKey = AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET,
                timeZone = tz
            ).accepted
        )
        val modeAsk = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), bizId, now + 6, tz)!!,
            now + 6,
            tz
        )
        assertTrue(modeAsk is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_EXPLORATION_MODE,
            (modeAsk as BuilderDecision.Ask).purpose
        )
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, modeAsk, now + 6, timeZone = tz).accepted
        )
        val afterModeAsk = AdaptiveBuilderEngine.projectContext(
            store.snapshot(), bizId, now + 7, tz
        )!!
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = modeAsk.questionId,
                    intentionId = bizId,
                    normalizedValue = "Brainstorm first",
                    rawValue = "Brainstorm first",
                    modality = "CHIP",
                    sourceTurnId = "turn-c2-biz-mode",
                    answeredAtEpochMs = now + 8,
                    idempotencyKey = "idem-c2-biz-mode",
                    expectedRevision = afterModeAsk.revision
                ),
                factKey = AdaptiveBuilderEngine.KEY_EXPLORATION_MODE,
                timeZone = tz
            ).accepted
        )
        val exploreOffer = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), bizId, now + 9, tz)!!,
            now + 9,
            tz
        )
        assertTrue(
            "ambition should Offer Explore ideas after brainstorm, got $exploreOffer",
            exploreOffer is BuilderDecision.OfferAction &&
                (exploreOffer as BuilderDecision.OfferAction).actionDraft.kind == "explore_ideas"
        )
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, exploreOffer, now + 9, timeZone = tz).accepted
        )
        val afterExplore = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), bizId, now + 10, tz)!!,
            now + 10,
            tz
        )
        assertTrue(
            "ambition must Pause after Explore ideas (not invent ops), got $afterExplore",
            afterExplore is BuilderDecision.Pause
        )
        assertTrue(
            (afterExplore as BuilderDecision.Pause).resumeCue
                ?.contains("validate", ignoreCase = true) == true
        )
        val bizAskCount = store.snapshot().openQuestions.count {
            it.intentionId == bizId && it.state != OpenQuestionState.OPEN
        }
        assertEquals(3, bizAskCount)

        // Spanish: length is consequence-driven (Ask → … → OfferApproaches), not a fixed N.
        storeFile = tmp.newFile("gate-c2-spanish-len.json")
        store = IntentionFlowStore(storeFile)
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        var t = now
        val spanishAskPurposes = mutableListOf<String>()
        repeat(8) {
            val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!
            when (val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, tz)) {
                is BuilderDecision.Ask -> {
                    spanishAskPurposes += next.purpose
                    assertTrue(
                        AdaptiveBuilderEngine.applyDecision(store, next, t, timeZone = tz).accepted
                    )
                    val revCtx = AdaptiveBuilderEngine.projectContext(
                        store.snapshot(), id, t + 1, tz
                    )!!
                    assertTrue(
                        AdaptiveBuilderEngine.answerQuestion(
                            store,
                            AnswerQuestion(
                                questionId = next.questionId,
                                intentionId = id,
                                normalizedValue = when (next.purpose) {
                                    AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE -> "Spanish"
                                    AdaptiveBuilderEngine.KEY_MOTIVATION -> "Moving soon"
                                    AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE ->
                                        "In about three months."
                                    AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY -> "Everyday"
                                    else -> "ok"
                                },
                                rawValue = "ok",
                                modality = "TEXT_CHIP",
                                sourceTurnId = "turn-c2-len-$t",
                                answeredAtEpochMs = t + 2,
                                idempotencyKey = "idem-c2-len-$t",
                                expectedRevision = revCtx.revision
                            ),
                            factKey = next.purpose,
                            timeZone = tz
                        ).accepted
                    )
                    t += 10
                }
                is BuilderDecision.OfferApproaches -> {
                    assertTrue(
                        AdaptiveBuilderEngine.applyDecision(store, next, t, timeZone = tz).accepted
                    )
                    AdaptiveBuilderEngine.selectApproaches(
                        store = store,
                        intentionId = id,
                        approachIds = listOf("approach-ai-practice"),
                        nowEpochMs = t + 1,
                        sourceTurnId = "turn-c2-select-$t",
                        expectedRevision = AdaptiveBuilderEngine.projectContext(
                            store.snapshot(), id, t + 1, tz
                        )!!.revision,
                        modality = "TEXT_CHIP"
                    )
                    t += 10
                }
                is BuilderDecision.Propose,
                is BuilderDecision.Pause,
                is BuilderDecision.OfferAction -> return@repeat
                else -> t += 10
            }
        }

        // Consequence path asked language → motivation → move (3), not a fixed 5/10 script.
        assertEquals(
            listOf(
                AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                AdaptiveBuilderEngine.KEY_MOTIVATION,
                AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE
            ),
            spanishAskPurposes.take(3)
        )
        // After approaches + frequency the policy may Propose — still not a fixed length.
        assertTrue(
            "Spanish ask count must not look like a fixed questionnaire (got $spanishAskPurposes)",
            spanishAskPurposes.size in 3..5
        )
        assertTrue(
            store.snapshot().approaches.any {
                it.intentionId == id && it.status == ApproachStatus.SELECTED
            }
        )
        // Cross-facet proof: errand 0 Asks, ambition consequence-driven (≠ fixed N), Spanish ≥3.
        assertTrue(spanishAskPurposes.size != 0)
        assertTrue(spanishAskPurposes.size != 1)
    }

    @Test
    fun freshMoveEstimate_isNotStaleReconfirm_lowValueSkip() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        var t = now
        fun rev(): Long =
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision

        listOf(
            AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE to "Spanish",
            AdaptiveBuilderEngine.KEY_MOTIVATION to "Moving to Spain",
            AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE to "In about three months."
        ).forEachIndexed { index, (key, text) ->
            AdaptiveBuilderEngine.applyDecision(
                store,
                BuilderDecision.SaveFact(
                    intentionId = id,
                    expectedRevision = rev(),
                    factDraft = FactDraft(
                        factId = "fact-fresh-$index",
                        text = text,
                        key = key,
                        rawUtterance = text,
                        approximate = key == AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE,
                        confirmation = if (key == AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE) {
                            FactConfirmation.APPROXIMATE
                        } else {
                            FactConfirmation.CONFIRMED
                        }
                    ),
                    sourceTurnId = "turn-fresh-$index"
                ),
                t,
                timeZone = tz
            )
            t += 2
        }

        val soon = AdaptiveBuilderEngine.plusDays(now, 2)
        val moveFact = store.snapshot().facts.first { it.key == AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE }
        assertFalse(
            RelativeDateAnchoring.freshnessCheck(moveFact, soon, timeZone = tz).needsReconfirm
        )
        val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, soon, tz)!!
        val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, soon, tz)
        val nextAsk = next as? BuilderDecision.Ask
        assertFalse(
            "fresh estimate must not trigger stale reconfirm: $next",
            nextAsk?.purpose == "reconfirm_move_estimate"
        )
        assertTrue(
            "expected approaches (or pause), got $next",
            next is BuilderDecision.OfferApproaches || next is BuilderDecision.Pause
        )
    }
}
