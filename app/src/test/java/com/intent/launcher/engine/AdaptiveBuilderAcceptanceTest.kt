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
 * Gate F JVM harness — worksheet §8 scenarios 1, 2, 4, 5, 6, 7, 8, 9 (deterministic).
 * Fixtures + walks for companion other-cases (OC2–OC7, OC9) without inventing plans.
 * Gate D/E paths: AnswerQuestion parity + PlanCompiler review fields.
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

    // —— §8.2 Multimodal choice (tap vs speech-injected → same AnswerQuestion records) ——
    @Test
    fun scenario2_multimodalChoice_tapAndSpeechSameRecords() {
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        val approachValue = "AI conversation practice and Learning app"
        val expectedApproachIds = setOf("approach-ai-practice", "approach-learning-app")

        fun seedLanguageAsk(target: IntentionFlowStore, questionId: String) {
            AdaptiveBuilderFixtures.seedSpanishDiscovery(target, now)
            AdaptiveBuilderEngine.applyDecision(
                target,
                BuilderDecision.Ask(
                    intentionId = id,
                    expectedRevision = 0L,
                    questionId = questionId,
                    whyItMatters = "Target language changes tools.",
                    answerSchema = com.intent.launcher.domain.AnswerSchema.SINGLE_CHOICE,
                    purpose = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                    options = listOf("Spanish", "French", "Other")
                ),
                now,
                timeZone = tz
            )
        }

        fun seedApproachAsk(target: IntentionFlowStore, questionId: String) {
            AdaptiveBuilderFixtures.seedSpanishDiscovery(target, now)
            AdaptiveBuilderEngine.applyDecision(
                target,
                BuilderDecision.OfferApproaches(
                    intentionId = id,
                    expectedRevision = 0L,
                    approachDrafts = AdaptiveBuilderEngine.defaultApproachDrafts()
                ),
                now,
                timeZone = tz
            )
            val rev = AdaptiveBuilderEngine.projectContext(target.snapshot(), id, now, tz)!!.revision
            AdaptiveBuilderEngine.applyDecision(
                target,
                BuilderDecision.Ask(
                    intentionId = id,
                    expectedRevision = rev,
                    questionId = questionId,
                    whyItMatters = "Which ways of learning appeal to you?",
                    answerSchema = com.intent.launcher.domain.AnswerSchema.MULTI_CHOICE,
                    purpose = AdaptiveBuilderEngine.KEY_SELECTED_APPROACHES,
                    options = AdaptiveBuilderEngine.defaultApproachDrafts().map { it.title }
                ),
                now + 1,
                timeZone = tz
            )
        }

        fun selectedApproachIds(target: IntentionFlowStore): Set<String> =
            target.snapshot().approaches
                .filter { it.intentionId == id && it.status == ApproachStatus.SELECTED }
                .map { it.id }
                .toSet()

        // Fact Ask: tap vs exact speech-injected transcript → same key/value.
        seedLanguageAsk(store, "q-lang-tap")
        val askBlock = store.snapshot().turns.flatMap { it.blocks }
            .first { "q-lang-tap" in it.linkedObjectIds }
        assertTrue(BuilderAnswerCard.isBuilderCard(askBlock))
        assertNotNull(BuilderAnswerCard.decode(askBlock))
        assertTrue(
            AdaptiveAnswerPath.submit(
                store = store,
                intentionId = id,
                rawValue = "Spanish",
                modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
                nowEpochMs = now + 5,
                timeZone = tz,
                expectedQuestionId = "q-lang-tap"
            ).accepted
        )
        val tapLang = AdaptiveBuilderEngine.activeFacts(store.snapshot(), id)
            .first { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }.text

        val storeSpeech = IntentionFlowStore(tmp.newFile("adaptive-builder-voice.json"))
        seedLanguageAsk(storeSpeech, "q-lang-voice")
        // Simulate SpeechTranscriptInject → same AdaptiveAnswerPath.submitFromVoice as mic STT.
        val speech = AdaptiveAnswerPath.submitFromVoice(
            store = storeSpeech,
            intentionId = id,
            transcript = "Spanish",
            nowEpochMs = now + 10,
            timeZone = tz,
            expectedQuestionId = "q-lang-voice"
        )
        assertTrue(speech.honestyNote, speech.accepted)
        assertFalse(speech.needsVoiceConfirm)
        assertEquals(
            tapLang,
            AdaptiveBuilderEngine.activeFacts(storeSpeech.snapshot(), id)
                .first { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }.text
        )
        assertEquals(
            ConversationCoordinator.Channel.VOICE_TRANSCRIPT.name,
            storeSpeech.snapshot().turns.first {
                it.role == "user" && it.text == "Spanish"
            }.modality
        )

        // Approach Ask: tap vs speech-injected multi-select → same SELECTED approach ids.
        val storeTapApp = IntentionFlowStore(tmp.newFile("adaptive-builder-approach-tap.json"))
        seedApproachAsk(storeTapApp, "q-approaches-tap")
        assertTrue(
            AdaptiveAnswerPath.submit(
                store = storeTapApp,
                intentionId = id,
                rawValue = approachValue,
                modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
                nowEpochMs = now + 20,
                timeZone = tz,
                expectedQuestionId = "q-approaches-tap"
            ).accepted
        )
        val storeSpeechApp = IntentionFlowStore(tmp.newFile("adaptive-builder-approach-speech.json"))
        seedApproachAsk(storeSpeechApp, "q-approaches-speech")
        assertTrue(
            AdaptiveAnswerPath.submitFromVoice(
                store = storeSpeechApp,
                intentionId = id,
                transcript = approachValue,
                nowEpochMs = now + 30,
                timeZone = tz,
                expectedQuestionId = "q-approaches-speech"
            ).accepted
        )
        assertEquals(expectedApproachIds, selectedApproachIds(storeTapApp))
        assertEquals(expectedApproachIds, selectedApproachIds(storeSpeechApp))
        assertEquals(
            AdaptiveBuilderEngine.activeFacts(storeTapApp.snapshot(), id)
                .first { it.key == AdaptiveBuilderEngine.KEY_SELECTED_APPROACHES }.text,
            AdaptiveBuilderEngine.activeFacts(storeSpeechApp.snapshot(), id)
                .first { it.key == AdaptiveBuilderEngine.KEY_SELECTED_APPROACHES }.text
        )

        // Uncertain voice → confirm card, no fact write; Yes → same as tap (Gate D2).
        val storeUnc = IntentionFlowStore(tmp.newFile("adaptive-builder-unc.json"))
        seedLanguageAsk(storeUnc, "q-lang-unc")
        val unc = AdaptiveAnswerPath.submitFromVoice(
            store = storeUnc,
            intentionId = id,
            transcript = "I'd like spanish please",
            nowEpochMs = now + 40,
            timeZone = tz,
            expectedQuestionId = "q-lang-unc"
        )
        assertTrue(unc.needsVoiceConfirm)
        assertFalse(unc.accepted)
        assertTrue(
            AdaptiveBuilderEngine.activeFacts(storeUnc.snapshot(), id)
                .none { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }
        )
        val afterConfirm = AdaptiveAnswerPath.submit(
            store = storeUnc,
            intentionId = id,
            rawValue = AdaptiveAnswerPath.VOICE_CONFIRM_YES,
            modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
            nowEpochMs = now + 50,
            timeZone = tz,
            expectedQuestionId = "q-lang-unc"
        )
        assertTrue(afterConfirm.honestyNote, afterConfirm.accepted)
        assertEquals(
            tapLang,
            AdaptiveBuilderEngine.activeFacts(storeUnc.snapshot(), id)
                .first { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }.text
        )
    }

    // —— §8.2 Multimodal choice (chip/tap vs typed free text → same records; retained from 0.0.169) ——
    @Test
    fun scenario2_multimodalChoice_tapAndTypedSameRecords() {
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        val approachValue = "AI conversation practice and Learning app"
        val expectedApproachIds = setOf("approach-ai-practice", "approach-learning-app")

        fun seedApproachAsk(target: IntentionFlowStore, questionId: String): String {
            AdaptiveBuilderFixtures.seedSpanishDiscovery(target, now)
            AdaptiveBuilderEngine.applyDecision(
                target,
                BuilderDecision.OfferApproaches(
                    intentionId = id,
                    expectedRevision = 0L,
                    approachDrafts = AdaptiveBuilderEngine.defaultApproachDrafts()
                ),
                now,
                timeZone = tz
            )
            val rev = AdaptiveBuilderEngine.projectContext(target.snapshot(), id, now, tz)!!.revision
            AdaptiveBuilderEngine.applyDecision(
                target,
                BuilderDecision.Ask(
                    intentionId = id,
                    expectedRevision = rev,
                    questionId = questionId,
                    whyItMatters = "Which ways of learning appeal to you?",
                    answerSchema = com.intent.launcher.domain.AnswerSchema.MULTI_CHOICE,
                    purpose = AdaptiveBuilderEngine.KEY_SELECTED_APPROACHES,
                    options = AdaptiveBuilderEngine.defaultApproachDrafts().map { it.title }
                ),
                now + 1,
                timeZone = tz
            )
            return questionId
        }

        fun selectedApproachIds(target: IntentionFlowStore): Set<String> =
            target.snapshot().approaches
                .filter { it.intentionId == id && it.status == ApproachStatus.SELECTED }
                .map { it.id }
                .toSet()

        fun approachFactText(target: IntentionFlowStore): String? =
            AdaptiveBuilderEngine.activeFacts(target.snapshot(), id)
                .firstOrNull { it.key == AdaptiveBuilderEngine.KEY_SELECTED_APPROACHES }
                ?.text

        // Chip/tap path (TEXT_CHIP) — multi-select value via one AnswerQuestion.
        val tapQ = seedApproachAsk(store, "q-approaches-tap")
        val tapBlock = store.snapshot().turns.flatMap { it.blocks }
            .first { tapQ in it.linkedObjectIds }
        assertTrue(BuilderAnswerCard.isBuilderCard(tapBlock))
        assertNotNull(BuilderAnswerCard.decode(tapBlock))

        val tap = AdaptiveAnswerPath.submit(
            store = store,
            intentionId = id,
            rawValue = approachValue,
            modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
            nowEpochMs = now + 10,
            timeZone = tz,
            expectedQuestionId = tapQ
        )
        assertTrue(tap.honestyNote, tap.accepted)
        assertEquals(expectedApproachIds, selectedApproachIds(store))
        assertEquals(approachValue, approachFactText(store))
        assertEquals(
            OpenQuestionState.ANSWERED,
            store.snapshot().openQuestions.first { it.id == tapQ }.state
        )

        // Fresh run: typed free text (FREE_TEXT) with the same value → same approach + fact.
        val storeTyped = IntentionFlowStore(tmp.newFile("adaptive-builder-typed.json"))
        val typedQ = seedApproachAsk(storeTyped, "q-approaches-typed")
        val typed = AdaptiveAnswerPath.submit(
            store = storeTyped,
            intentionId = id,
            rawValue = approachValue,
            modality = ConversationCoordinator.Channel.FREE_TEXT.name,
            nowEpochMs = now + 20,
            timeZone = tz,
            expectedQuestionId = typedQ
        )
        assertTrue(typed.honestyNote, typed.accepted)
        assertEquals(expectedApproachIds, selectedApproachIds(storeTyped))
        assertEquals(approachValue, approachFactText(storeTyped))
        assertEquals(
            approachFactText(store),
            approachFactText(storeTyped)
        )
        assertEquals(
            selectedApproachIds(store),
            selectedApproachIds(storeTyped)
        )

        // Fact Ask: chip tap vs typed free text write the same key/value (no divergent plan).
        fun seedLanguageAsk(target: IntentionFlowStore, questionId: String) {
            AdaptiveBuilderFixtures.seedSpanishDiscovery(target, now)
            AdaptiveBuilderEngine.applyDecision(
                target,
                BuilderDecision.Ask(
                    intentionId = id,
                    expectedRevision = 0L,
                    questionId = questionId,
                    whyItMatters = "Target language changes tools.",
                    answerSchema = com.intent.launcher.domain.AnswerSchema.SINGLE_CHOICE,
                    purpose = AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE,
                    options = listOf("Spanish", "French", "Other")
                ),
                now,
                timeZone = tz
            )
        }
        val storeLangTap = IntentionFlowStore(tmp.newFile("adaptive-builder-lang-tap.json"))
        seedLanguageAsk(storeLangTap, "q-lang-tap")
        assertTrue(
            AdaptiveAnswerPath.submit(
                store = storeLangTap,
                intentionId = id,
                rawValue = "Spanish",
                modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
                nowEpochMs = now + 30,
                timeZone = tz,
                expectedQuestionId = "q-lang-tap"
            ).accepted
        )
        val storeLangTyped = IntentionFlowStore(tmp.newFile("adaptive-builder-lang-typed.json"))
        seedLanguageAsk(storeLangTyped, "q-lang-typed")
        assertTrue(
            AdaptiveAnswerPath.submit(
                store = storeLangTyped,
                intentionId = id,
                rawValue = "Spanish",
                modality = ConversationCoordinator.Channel.FREE_TEXT.name,
                nowEpochMs = now + 40,
                timeZone = tz,
                expectedQuestionId = "q-lang-typed"
            ).accepted
        )
        val tapLang = AdaptiveBuilderEngine.activeFacts(storeLangTap.snapshot(), id)
            .first { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }
        val typedLang = AdaptiveBuilderEngine.activeFacts(storeLangTyped.snapshot(), id)
            .first { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }
        assertEquals(tapLang.key, typedLang.key)
        assertEquals(tapLang.text, typedLang.text)
        assertEquals("Spanish", typedLang.text)

        // Uncertain voice → confirm (no write); Yes → same fact as tap (Gate D2).
        val storeUnc = IntentionFlowStore(tmp.newFile("adaptive-builder-unc.json"))
        seedLanguageAsk(storeUnc, "q-lang-unc")
        val unc = AdaptiveAnswerPath.submitFromVoice(
            store = storeUnc,
            intentionId = id,
            transcript = "I'd like spanish please",
            nowEpochMs = now + 50,
            timeZone = tz,
            expectedQuestionId = "q-lang-unc"
        )
        assertTrue(unc.needsVoiceConfirm)
        assertFalse(unc.accepted)
        assertTrue(
            AdaptiveBuilderEngine.activeFacts(storeUnc.snapshot(), id)
                .none { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }
        )
        val afterConfirm = AdaptiveAnswerPath.submit(
            store = storeUnc,
            intentionId = id,
            rawValue = AdaptiveAnswerPath.VOICE_CONFIRM_YES,
            modality = ConversationCoordinator.Channel.TEXT_CHIP.name,
            nowEpochMs = now + 60,
            timeZone = tz,
            expectedQuestionId = "q-lang-unc"
        )
        assertTrue(afterConfirm.honestyNote, afterConfirm.accepted)
        assertEquals(
            "Spanish",
            AdaptiveBuilderEngine.activeFacts(storeUnc.snapshot(), id)
                .first { it.key == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE }.text
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
        val pending = snap.proposals.first { it.status == ProposalStatus.PENDING }
        // PlanCompiler fields present on draft — check-ins independent and off by default.
        assertTrue(pending.methodsSummary.isNotBlank())
        assertTrue(pending.capabilityHonesty.isNotBlank())
        assertEquals(false, pending.includeMorningCheckIn)
        assertEquals(false, pending.includeEveningReview)
        assertTrue(pending.proposedDaysOfWeek.isNotEmpty())
        assertTrue(pending.durationMinutes in 1..180)
        assertTrue(pending.timeZoneId.isNotBlank())
        // No daily check-in occurrences until approve.
        assertTrue(snap.occurrences.none { it.intentionId == id })

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

    @Test
    fun scenario4b_approveCreatesSession_receiptPath_noSetupBeforeApply() {
        AdaptiveBuilderFixtures.seedSpanishDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.SPANISH_ID
        var t = now
        fun rev(): Long =
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision

        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.OfferApproaches(
                intentionId = id,
                expectedRevision = rev(),
                approachDrafts = listOf(
                    com.intent.launcher.domain.ApproachDraft(
                        approachId = "approach-ai-practice",
                        title = "AI conversation practice",
                        summary = "Guided",
                        capabilityNote = "Guided session only."
                    )
                )
            ),
            t,
            timeZone = tz
        )
        t += 2
        AdaptiveBuilderEngine.selectApproaches(
            store, id, listOf("approach-ai-practice"), t, "turn-sel", rev(), "TEXT_CHIP"
        )
        t += 2
        AdaptiveBuilderEngine.applyDecision(
            store,
            BuilderDecision.SaveFact(
                intentionId = id,
                expectedRevision = rev(),
                factDraft = FactDraft(
                    factId = "fact-freq2",
                    text = "Everyday",
                    key = AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY
                ),
                sourceTurnId = "turn-freq2"
            ),
            t,
            timeZone = tz
        )
        t += 2
        val propose = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
        )
        assertTrue(propose is BuilderDecision.Propose)
        AdaptiveBuilderEngine.applyDecision(store, propose, t, timeZone = tz)
        assertTrue(store.snapshot().sessions.isEmpty())

        val proposalId = (propose as BuilderDecision.Propose).proposalId
        store.apply(
            FlowCommand.ApproveProposal(
                proposalId = proposalId,
                sessionId = "session-$proposalId",
                nowEpochMs = t + 10
            )
        )
        val snap = store.snapshot()
        assertEquals(1, snap.sessions.size)
        assertEquals(ProposalStatus.APPROVED, snap.proposals.first { it.id == proposalId }.status)
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first { it.id == id }.phase)
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

    // —— §8.7 Broad ambition / OTHER §8 ——
    @Test
    fun scenario7_broadAmbition_staysDiscovery_noFictionalLaunchChecklist() {
        AdaptiveBuilderFixtures.seedBusinessDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.BUSINESS_ID
        val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!
        val decision = AdaptiveBuilderEngine.suggestNextDecision(ctx, now, tz)
        assertTrue(decision is BuilderDecision.Ask)
        val ask = decision as BuilderDecision.Ask
        assertEquals(AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS, ask.purpose)
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

    @Test
    fun oc8_sideBusiness_exploreThenValidate_noLaunchOps() {
        AdaptiveBuilderFixtures.seedBusinessDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.BUSINESS_ID
        var t = now

        fun askAndAnswer(purpose: String, value: String, modality: String = "CHIP") {
            val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!
            val next = AdaptiveBuilderEngine.suggestNextDecision(ctx, t, tz)
            assertTrue("expected Ask for $purpose, got $next", next is BuilderDecision.Ask)
            val ask = next as BuilderDecision.Ask
            assertEquals(purpose, ask.purpose)
            assertTrue(AdaptiveBuilderEngine.applyDecision(store, ask, t, timeZone = tz).accepted)
            t += 1
            val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision
            assertTrue(
                AdaptiveBuilderEngine.answerQuestion(
                    store,
                    AnswerQuestion(
                        questionId = ask.questionId,
                        intentionId = id,
                        normalizedValue = value,
                        rawValue = value,
                        modality = modality,
                        sourceTurnId = "turn-oc8-$purpose-$t",
                        answeredAtEpochMs = t,
                        idempotencyKey = "idem-oc8-$purpose-$t",
                        expectedRevision = rev
                    ),
                    factKey = purpose,
                    timeZone = tz
                ).accepted
            )
            t += 1
        }

        askAndAnswer(
            AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS,
            "customer support and workflow automation",
            "FREE_TEXT"
        )
        askAndAnswer(
            AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET,
            "About five hours a week"
        )
        askAndAnswer(
            AdaptiveBuilderEngine.KEY_EXPLORATION_MODE,
            "Brainstorm first"
        )

        val explore = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
        )
        assertTrue(explore is BuilderDecision.OfferAction)
        val exploreAction = explore as BuilderDecision.OfferAction
        assertEquals("explore_ideas", exploreAction.actionDraft.kind)
        assertTrue(exploreAction.actionDraft.title.contains("Explore ideas", ignoreCase = true))
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, explore, t, timeZone = tz).accepted
        )
        t += 1
        assertTrue(
            store.snapshot().hypotheses.any {
                it.intentionId == id && it.text.contains("ideas board", ignoreCase = true)
            }
        )
        // After Explore ideas: Ask for selected idea (Discuss AdaptiveAnswerPath),
        // not a forever-Pause that strands OfferAction off the active path.
        val chooseIdea = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
        )
        assertTrue(chooseIdea is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_SELECTED_IDEA,
            (chooseIdea as BuilderDecision.Ask).purpose
        )
        askAndAnswer(
            AdaptiveBuilderEngine.KEY_SELECTED_IDEA,
            "repetitive support workflows",
            "FREE_TEXT"
        )
        askAndAnswer(
            AdaptiveBuilderEngine.KEY_VALIDATION_APPROACH,
            "Validate with a few potential customers"
        )
        val exploreThenExperiment = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
        )
        assertTrue(exploreThenExperiment is BuilderDecision.OfferAction)
        assertEquals(
            "validation_experiment",
            (exploreThenExperiment as BuilderDecision.OfferAction).actionDraft.kind
        )
        assertTrue(
            exploreThenExperiment.actionDraft.title
                .contains("Small validation experiment", ignoreCase = true)
        )
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(
                store, exploreThenExperiment, t, timeZone = tz
            ).accepted
        )
        assertTrue(
            store.snapshot().turns.any { turn ->
                turn.intentionId == id &&
                    turn.blocks.any {
                        it.title.contains("Small validation experiment", ignoreCase = true)
                    }
            }
        )

        val snap = store.snapshot()
        assertTrue(snap.proposals.none { it.intentionId == id })
        assertTrue(snap.sessions.none { it.intentionId == id })
        assertTrue(
            snap.facts.any {
                it.intentionId == id &&
                    it.key == AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET &&
                    it.status == FactStatus.ACTIVE
            }
        )

        // Conversation 2 — validate path on a fresh discovery with test-quickly mode.
        storeFile = tmp.newFile("oc8-validate.json")
        store = IntentionFlowStore(storeFile)
        AdaptiveBuilderFixtures.seedBusinessDiscovery(store, now)
        t = now
        askAndAnswer(
            AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS,
            "customer support and workflow automation",
            "FREE_TEXT"
        )
        askAndAnswer(
            AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET,
            "About five hours a week"
        )
        askAndAnswer(
            AdaptiveBuilderEngine.KEY_EXPLORATION_MODE,
            "Test one idea quickly"
        )
        askAndAnswer(
            AdaptiveBuilderEngine.KEY_SELECTED_IDEA,
            "repetitive support workflows",
            "FREE_TEXT"
        )
        askAndAnswer(
            AdaptiveBuilderEngine.KEY_VALIDATION_APPROACH,
            "Validate with a few potential customers"
        )
        val experiment = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
        )
        assertTrue(experiment is BuilderDecision.OfferAction)
        assertEquals(
            "validation_experiment",
            (experiment as BuilderDecision.OfferAction).actionDraft.kind
        )
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, experiment, t, timeZone = tz).accepted
        )
        assertTrue(store.snapshot().proposals.none { it.intentionId == id })
        assertTrue(store.snapshot().sessions.none { it.intentionId == id })
    }

    /**
     * Discuss/active path — after Explore ideas, the Choose-an-idea chip projects
     * an Adaptive Ask; AdaptiveAnswerPath answers advance to Small validation
     * experiment OfferAction (same honesty as create/JVM validate-fast).
     */
    @Test
    fun ambition_discussPath_chooseIdeaChip_surfacesValidationOfferAction() {
        AdaptiveBuilderFixtures.seedBusinessDiscovery(store, now)
        val id = AdaptiveBuilderFixtures.BUSINESS_ID
        var t = now

        fun save(key: String, value: String) {
            val ctx = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!
            assertTrue(
                AdaptiveBuilderEngine.applyDecision(
                    store,
                    BuilderDecision.SaveFact(
                        intentionId = id,
                        expectedRevision = ctx.revision,
                        factDraft = FactDraft(
                            factId = "fact-discuss-$key",
                            text = value,
                            key = key,
                            rawUtterance = value,
                            normalizedValue = value
                        ),
                        sourceTurnId = "turn-discuss-$key"
                    ),
                    t,
                    timeZone = tz
                ).accepted
            )
            t += 1
        }

        save(AdaptiveBuilderEngine.KEY_SKILLS_OR_INTERESTS, "workflow automation")
        save(AdaptiveBuilderEngine.KEY_WEEKLY_TIME_BUDGET, "About five hours a week")
        save(AdaptiveBuilderEngine.KEY_EXPLORATION_MODE, "Brainstorm first")

        val explore = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
        )
        assertTrue(explore is BuilderDecision.OfferAction)
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, explore, t, timeZone = tz).accepted
        )
        t += 1

        // Discuss chip — AdaptiveBuilder, not BusinessScenario fixture card.
        assertTrue(
            AdaptiveBuilderEngine.handleAmbitionExplorationChip(
                store = store,
                intentionId = id,
                chip = AdaptiveBuilderEngine.CHIP_CHOOSE_IDEA_TO_VALIDATE,
                nowEpochMs = t,
                timeZone = tz
            )
        )
        t += 5
        val state = store.snapshot().adaptiveBuilderStates.first { it.intentionId == id }
        assertNotNull(state.currentQuestionId)
        val openQ = store.snapshot().openQuestions.first {
            it.id == state.currentQuestionId
        }
        assertEquals(AdaptiveBuilderEngine.KEY_SELECTED_IDEA, openQ.purpose)

        val ideaAnswer = AdaptiveAnswerPath.submit(
            store = store,
            intentionId = id,
            rawValue = "repetitive support workflows",
            modality = "FREE_TEXT",
            nowEpochMs = t,
            timeZone = tz,
            expectedQuestionId = state.currentQuestionId
        )
        assertTrue(ideaAnswer.accepted)
        t += 10

        // advanceAfterAnswer should have projected validation-approach Ask.
        val afterIdea = store.snapshot().adaptiveBuilderStates.first { it.intentionId == id }
        assertNotNull(afterIdea.currentQuestionId)
        val approachQ = store.snapshot().openQuestions.first {
            it.id == afterIdea.currentQuestionId
        }
        assertEquals(AdaptiveBuilderEngine.KEY_VALIDATION_APPROACH, approachQ.purpose)

        val approachAnswer = AdaptiveAnswerPath.submit(
            store = store,
            intentionId = id,
            rawValue = "Validate with a few potential customers",
            modality = "TEXT_CHIP",
            nowEpochMs = t,
            timeZone = tz,
            expectedQuestionId = afterIdea.currentQuestionId
        )
        assertTrue(approachAnswer.accepted)

        assertTrue(
            "Discuss path must surface Small validation experiment OfferAction card",
            store.snapshot().turns.any { turn ->
                turn.intentionId == id &&
                    turn.blocks.any {
                        it.title.contains("Small validation experiment", ignoreCase = true)
                    }
            }
        )
        assertTrue(store.snapshot().proposals.none { it.intentionId == id })
        assertTrue(store.snapshot().sessions.none { it.intentionId == id })
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

    // —— §8.8 / OC2 Conditional process — eligibility → research pause ——
    @Test
    fun scenario8_oc2_spainEligibility_thenResearchPause_noInventedLegalPlan() {
        val id = AdaptiveBuilderFixtures.seedOtherCase(store, "eu_work", now)
        var t = now

        fun next(): BuilderDecision =
            AdaptiveBuilderEngine.suggestNextDecision(
                AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
            )

        fun answerAsk(ask: BuilderDecision.Ask, text: String, key: String, suffix: String) {
            val applied = AdaptiveBuilderEngine.applyDecision(store, ask, t, timeZone = tz)
            assertTrue(applied.reason, applied.accepted)
            t += 1
            val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision
            val ans = AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = ask.questionId,
                    intentionId = id,
                    normalizedValue = text,
                    rawValue = text,
                    modality = "FREE_TEXT",
                    sourceTurnId = "turn-$suffix",
                    answeredAtEpochMs = t,
                    idempotencyKey = "idem-$suffix",
                    expectedRevision = rev
                ),
                factKey = key,
                timeZone = tz
            )
            assertTrue(ans.reason, ans.accepted)
            t += 5
        }

        val citizenship = next()
        assertTrue("expected citizenship Ask, got $citizenship", citizenship is BuilderDecision.Ask)
        assertEquals(AdaptiveBuilderEngine.KEY_CITIZENSHIP, (citizenship as BuilderDecision.Ask).purpose)
        answerAsk(citizenship, "Irish", AdaptiveBuilderEngine.KEY_CITIZENSHIP, "citizenship")

        val employment = next()
        assertTrue(employment is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_EMPLOYMENT_ARRANGEMENT,
            (employment as BuilderDecision.Ask).purpose
        )
        answerAsk(
            employment,
            "Employed",
            AdaptiveBuilderEngine.KEY_EMPLOYMENT_ARRANGEMENT,
            "employment"
        )

        val residence = next()
        assertTrue(residence is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_RESIDENCE_DURATION,
            (residence as BuilderDecision.Ask).purpose
        )
        answerAsk(
            residence,
            "About a month",
            AdaptiveBuilderEngine.KEY_RESIDENCE_DURATION,
            "residence"
        )

        val pause = next()
        assertTrue("expected research Pause, got $pause", pause is BuilderDecision.Pause)
        val pauseDec = pause as BuilderDecision.Pause
        assertTrue(pauseDec.reason.contains("unverified", ignoreCase = true))
        assertTrue(
            pauseDec.reason.contains("research", ignoreCase = true) ||
                pauseDec.reason.contains("sourced", ignoreCase = true)
        )
        assertFalse(pauseDec.reason.contains("NIE", ignoreCase = true))
        assertFalse(pauseDec.reason.contains("visa", ignoreCase = true))

        val applied = AdaptiveBuilderEngine.applyDecision(store, pauseDec, t, timeZone = tz)
        assertTrue(applied.reason, applied.accepted)
        val snap = store.snapshot()
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first { it.id == id }.phase)
        assertTrue(snap.proposals.none { it.intentionId == id })
        assertTrue(snap.sessions.none { it.intentionId == id })
        assertEquals(3, AdaptiveBuilderEngine.activeFacts(snap, id).size)
        assertEquals(
            "paused",
            snap.adaptiveBuilderStates.first { it.intentionId == id }.lifecycleHint
        )
        assertFalse(
            snap.turns.any {
                it.text.contains("you must register", ignoreCase = true) ||
                    it.text.contains("NIE required", ignoreCase = true)
            }
        )
    }

    @Test
    fun oc2_conditionalProcess_resumeAfterPause_reviewPlan_noInventedLaw() {
        val id = AdaptiveBuilderFixtures.seedOtherCase(store, "eu_work", now)
        var t = now

        fun next(): BuilderDecision =
            AdaptiveBuilderEngine.suggestNextDecision(
                AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
            )

        fun answerAsk(ask: BuilderDecision.Ask, text: String, key: String, suffix: String) {
            val applied = AdaptiveBuilderEngine.applyDecision(store, ask, t, timeZone = tz)
            assertTrue(applied.reason, applied.accepted)
            t += 1
            val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision
            val ans = AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = ask.questionId,
                    intentionId = id,
                    normalizedValue = text,
                    rawValue = text,
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-$suffix",
                    answeredAtEpochMs = t,
                    idempotencyKey = "idem-$suffix",
                    expectedRevision = rev
                ),
                factKey = key,
                timeZone = tz
            )
            assertTrue(ans.reason, ans.accepted)
            t += 5
        }

        answerAsk(next() as BuilderDecision.Ask, "Irish", AdaptiveBuilderEngine.KEY_CITIZENSHIP, "c")
        answerAsk(
            next() as BuilderDecision.Ask,
            "Employed",
            AdaptiveBuilderEngine.KEY_EMPLOYMENT_ARRANGEMENT,
            "e"
        )
        answerAsk(
            next() as BuilderDecision.Ask,
            "About a month",
            AdaptiveBuilderEngine.KEY_RESIDENCE_DURATION,
            "r"
        )
        val pause = next() as BuilderDecision.Pause
        assertTrue(AdaptiveBuilderEngine.applyDecision(store, pause, t, timeZone = tz).accepted)
        t += 5

        // Resume via AdaptiveBuilder chip on the active intention (not a fixture id).
        assertTrue(
            AdaptiveBuilderEngine.handleConditionalResearchChip(
                store,
                id,
                AdaptiveBuilderEngine.CHIP_CONTINUE_AFTER_RESEARCH,
                t
            )
        )
        t += 20

        val employerOpen = store.snapshot().openQuestions.firstOrNull {
            it.intentionId == id &&
                it.purpose == AdaptiveBuilderEngine.KEY_EMPLOYER_HANDLING &&
                it.state != OpenQuestionState.ANSWERED
        }
        assertNotNull("expected projected employer Ask after resume", employerOpen)
        val revEmp = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = employerOpen!!.id,
                    intentionId = id,
                    normalizedValue = "Yes — payroll or registration is set up",
                    rawValue = "Yes — payroll or registration is set up",
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-employer",
                    answeredAtEpochMs = t,
                    idempotencyKey = "idem-employer",
                    expectedRevision = revEmp
                ),
                factKey = AdaptiveBuilderEngine.KEY_EMPLOYER_HANDLING,
                timeZone = tz
            ).accepted
        )
        t += 5

        val status = next() as BuilderDecision.Ask
        assertEquals(AdaptiveBuilderEngine.KEY_RESEARCH_STATUS, status.purpose)
        assertTrue(status.whyItMatters.contains("Researching", ignoreCase = true))
        answerAsk(
            status,
            "Researching current requirements",
            AdaptiveBuilderEngine.KEY_RESEARCH_STATUS,
            "status"
        )

        val applicability = next() as BuilderDecision.Ask
        assertEquals(AdaptiveBuilderEngine.KEY_CHECKLIST_APPLICABILITY, applicability.purpose)
        assertTrue(
            applicability.options.any { it.equals("Applies", ignoreCase = true) } &&
                applicability.options.any { it.contains("Already done", ignoreCase = true) } &&
                applicability.options.any { it.contains("Not sure", ignoreCase = true) }
        )
        answerAsk(
            applicability,
            "Not sure",
            AdaptiveBuilderEngine.KEY_CHECKLIST_APPLICABILITY,
            "appl"
        )

        val propose = next()
        assertTrue("expected Review plan Propose, got $propose", propose is BuilderDecision.Propose)
        val prop = propose as BuilderDecision.Propose
        assertTrue(prop.title.contains("Review plan", ignoreCase = true))
        assertTrue(prop.summary.contains("Applies", ignoreCase = true))
        assertFalse(prop.summary.contains("you must register", ignoreCase = true))
        assertFalse(prop.summary.contains("NIE required", ignoreCase = true))
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, prop, t, timeZone = tz).accepted
        )
        val snap = store.snapshot()
        assertTrue(snap.proposals.any { it.intentionId == id && it.status == ProposalStatus.PENDING })
        assertTrue(snap.sessions.none { it.intentionId == id })
        assertTrue(
            snap.turns.any {
                it.text.contains("Review plan", ignoreCase = true) &&
                    it.intentionId == id
            }
        )
    }

    // —— OC3 Marathon — first phase ask, no invented 8-month plan ——
    @Test
    fun oc3_marathon_firstPhaseAsk_thenPause_noInventedPlan() {
        val id = AdaptiveBuilderFixtures.seedOtherCase(store, "marathon", now)
        val first = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!, now, tz
        )
        assertTrue(first is BuilderDecision.Ask)
        assertEquals(AdaptiveBuilderEngine.KEY_RACE_STATUS, (first as BuilderDecision.Ask).purpose)
        AdaptiveBuilderEngine.applyDecision(store, first, now, timeZone = tz)
        val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 1, tz)!!.revision
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = first.questionId,
                    intentionId = id,
                    normalizedValue = "Event still to choose",
                    rawValue = "Event still to choose",
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-race",
                    answeredAtEpochMs = now + 1,
                    idempotencyKey = "idem-race",
                    expectedRevision = rev
                ),
                factKey = AdaptiveBuilderEngine.KEY_RACE_STATUS,
                timeZone = tz
            ).accepted
        )
        val logAsk = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 2, tz)!!, now + 2, tz
        )
        assertTrue(logAsk is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_ACTIVITY_LOG_METHOD,
            (logAsk as BuilderDecision.Ask).purpose
        )
        assertTrue(logAsk.options.any { it.contains("Log runs manually", ignoreCase = true) })
        assertTrue(logAsk.options.any { it.contains("Choose race", ignoreCase = true) })
        AdaptiveBuilderEngine.applyDecision(store, logAsk, now + 2, timeZone = tz)
        val rev2 = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 3, tz)!!.revision
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = logAsk.questionId,
                    intentionId = id,
                    normalizedValue = "Log runs manually",
                    rawValue = "Log runs manually",
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-log",
                    answeredAtEpochMs = now + 3,
                    idempotencyKey = "idem-log",
                    expectedRevision = rev2
                ),
                factKey = AdaptiveBuilderEngine.KEY_ACTIVITY_LOG_METHOD,
                timeZone = tz
            ).accepted
        )
        val pause = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 4, tz)!!, now + 4, tz
        )
        assertTrue(pause is BuilderDecision.Pause)
        assertTrue((pause as BuilderDecision.Pause).reason.contains("eight-month", ignoreCase = true) ||
            pause.reason.contains("No eight-month", ignoreCase = true) ||
            pause.reason.contains("first phase", ignoreCase = true))
        AdaptiveBuilderEngine.applyDecision(store, pause, now + 5, timeZone = tz)
        val snap = store.snapshot()
        assertTrue(snap.proposals.none { it.intentionId == id })
        assertTrue(snap.sessions.none { it.intentionId == id })
        assertFalse(
            snap.turns.any {
                it.text.contains("week 32", ignoreCase = true) ||
                    it.text.contains("taper", ignoreCase = true)
            }
        )
    }

    @Test
    fun oc3_activityReport_correctFact_updatesSameEntry_notSecondRun() {
        val id = AdaptiveBuilderFixtures.seedOtherCase(store, "marathon", now)
        assertTrue(
            AdaptiveBuilderEngine.shouldHandleActivityReport(
                store, id, "I ran for 20 minutes today."
            )
        )
        val save = AdaptiveBuilderEngine.recordOrCorrectActivityReport(
            store = store,
            intentionId = id,
            utterance = "I ran for 20 minutes today.",
            nowEpochMs = now + 10,
            timeZone = tz
        )
        assertTrue(save.reason, save.accepted)
        val prior = store.snapshot().facts.single {
            it.intentionId == id &&
                it.key == AdaptiveBuilderEngine.KEY_ACTIVITY_REPORT &&
                it.status == FactStatus.ACTIVE
        }
        assertEquals("20 minutes", prior.text)
        assertTrue(
            AdaptiveBuilderEngine.shouldHandleActivityReport(
                store, id, "Actually, 25 minutes."
            )
        )
        val correct = AdaptiveBuilderEngine.recordOrCorrectActivityReport(
            store = store,
            intentionId = id,
            utterance = "Actually, 25 minutes.",
            nowEpochMs = now + 20,
            timeZone = tz
        )
        assertTrue(correct.reason, correct.accepted)
        val active = store.snapshot().facts.filter {
            it.intentionId == id && it.key == AdaptiveBuilderEngine.KEY_ACTIVITY_REPORT
        }
        assertEquals(2, active.size)
        assertEquals(FactStatus.CORRECTED, active.first { it.id == prior.id }.status)
        assertEquals(
            FactStatus.ACTIVE,
            active.first { it.id != prior.id }.status
        )
        assertEquals("25 minutes", active.first { it.status == FactStatus.ACTIVE }.text)
        assertEquals(prior.id, active.first { it.status == FactStatus.ACTIVE }.supersedesFactId)
    }

    // —— OC4 Habit — baseline then pause; no cessation-from-task claim ——
    @Test
    fun oc4_smoking_baselineAsk_thenPause_noCessationClaim() {
        val id = AdaptiveBuilderFixtures.seedOtherCase(store, "smoking", now)
        val ask = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!, now, tz
        )
        assertTrue(ask is BuilderDecision.Ask)
        assertEquals(AdaptiveBuilderEngine.KEY_HABIT_BASELINE, (ask as BuilderDecision.Ask).purpose)
        assertTrue(ask.whyItMatters.contains("cessation", ignoreCase = true))
        AdaptiveBuilderEngine.applyDecision(store, ask, now, timeZone = tz)
        val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 1, tz)!!.revision
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = ask.questionId,
                    intentionId = id,
                    normalizedValue = "About eight a day",
                    rawValue = "About eight a day",
                    modality = "FREE_TEXT",
                    sourceTurnId = "turn-baseline",
                    answeredAtEpochMs = now + 1,
                    idempotencyKey = "idem-baseline",
                    expectedRevision = rev
                ),
                factKey = AdaptiveBuilderEngine.KEY_HABIT_BASELINE,
                timeZone = tz
            ).accepted
        )
        val pause = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 2, tz)!!, now + 2, tz
        )
        assertTrue(pause is BuilderDecision.Pause)
        assertTrue((pause as BuilderDecision.Pause).reason.contains("Baseline", ignoreCase = true))
        assertTrue(pause.reason.contains("cessation", ignoreCase = true) ||
            pause.reason.contains("Quit approach", ignoreCase = true))
        AdaptiveBuilderEngine.applyDecision(store, pause, now + 3, timeZone = tz)
        val snap = store.snapshot()
        assertTrue(snap.proposals.none { it.intentionId == id })
        assertTrue(snap.sessions.none { it.intentionId == id })
        assertFalse(
            snap.turns.any { it.text.contains("you have quit", ignoreCase = true) }
        )
    }

    // —— OC5 Usage — measurement honesty pause ——
    @Test
    fun oc5_usage_measurementHonesty_thenPause_noFabricatedShorts() {
        val id = AdaptiveBuilderFixtures.seedOtherCase(store, "app_use", now)
        val ask = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!, now, tz
        )
        assertTrue(ask is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_USAGE_MEASUREMENT,
            (ask as BuilderDecision.Ask).purpose
        )
        assertTrue(ask.whyItMatters.contains("Shorts", ignoreCase = true))
        AdaptiveBuilderEngine.applyDecision(store, ask, now, timeZone = tz)
        val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 1, tz)!!.revision
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = ask.questionId,
                    intentionId = id,
                    normalizedValue = "Track Instagram automatically; log Shorts myself",
                    rawValue = "Track Instagram automatically; log Shorts myself",
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-usage",
                    answeredAtEpochMs = now + 1,
                    idempotencyKey = "idem-usage",
                    expectedRevision = rev
                ),
                factKey = AdaptiveBuilderEngine.KEY_USAGE_MEASUREMENT,
                timeZone = tz
            ).accepted
        )
        val pause = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 2, tz)!!, now + 2, tz
        )
        assertTrue(pause is BuilderDecision.Pause)
        assertTrue((pause as BuilderDecision.Pause).reason.contains("Shorts", ignoreCase = true) ||
            pause.reason.contains("Measurement", ignoreCase = true))
        AdaptiveBuilderEngine.applyDecision(store, pause, now + 3, timeZone = tz)
        assertTrue(store.snapshot().proposals.none { it.intentionId == id })
        assertTrue(store.snapshot().sessions.none { it.intentionId == id })
    }

    // —— OC6 Chess — manual/provider baseline + practice distinct, no invented ratings ——
    @Test
    fun oc6_chess_ratingBaselineAsk_thenPause_noInventedRating() {
        val id = AdaptiveBuilderFixtures.seedOtherCase(store, "chess", now)
        val ask = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now, tz)!!, now, tz
        )
        assertTrue(ask is BuilderDecision.Ask)
        assertEquals(AdaptiveBuilderEngine.KEY_RATING_BASELINE, (ask as BuilderDecision.Ask).purpose)
        assertTrue(ask.whyItMatters.contains("invent", ignoreCase = true))
        AdaptiveBuilderEngine.applyDecision(store, ask, now, timeZone = tz)
        val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 1, tz)!!.revision
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = ask.questionId,
                    intentionId = id,
                    normalizedValue = "Use manual baseline (e.g. 900)",
                    rawValue = "Use manual baseline (e.g. 900)",
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-rating",
                    answeredAtEpochMs = now + 1,
                    idempotencyKey = "idem-rating",
                    expectedRevision = rev
                ),
                factKey = AdaptiveBuilderEngine.KEY_RATING_BASELINE,
                timeZone = tz
            ).accepted
        )
        val practiceAsk = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 2, tz)!!, now + 2, tz
        )
        assertTrue(practiceAsk is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_PRACTICE_PREFERENCE,
            (practiceAsk as BuilderDecision.Ask).purpose
        )
        assertTrue(
            practiceAsk.whyItMatters.contains("distinct", ignoreCase = true) ||
                practiceAsk.whyItMatters.contains("not proof", ignoreCase = true)
        )
        AdaptiveBuilderEngine.applyDecision(store, practiceAsk, now + 2, timeZone = tz)
        val rev2 = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 3, tz)!!.revision
        assertTrue(
            AdaptiveBuilderEngine.answerQuestion(
                store,
                AnswerQuestion(
                    questionId = practiceAsk.questionId,
                    intentionId = id,
                    normalizedValue = "A combination",
                    rawValue = "A combination",
                    modality = "TEXT_CHIP",
                    sourceTurnId = "turn-practice",
                    answeredAtEpochMs = now + 3,
                    idempotencyKey = "idem-practice",
                    expectedRevision = rev2
                ),
                factKey = AdaptiveBuilderEngine.KEY_PRACTICE_PREFERENCE,
                timeZone = tz
            ).accepted
        )
        val pause = AdaptiveBuilderEngine.suggestNextDecision(
            AdaptiveBuilderEngine.projectContext(store.snapshot(), id, now + 4, tz)!!, now + 4, tz
        )
        assertTrue(pause is BuilderDecision.Pause)
        assertTrue(
            (pause as BuilderDecision.Pause).reason.contains("invented", ignoreCase = true) ||
                pause.reason.contains("live rating", ignoreCase = true) ||
                pause.reason.contains("stale", ignoreCase = true)
        )
        AdaptiveBuilderEngine.applyDecision(store, pause, now + 5, timeZone = tz)
        val snap = store.snapshot()
        assertTrue(snap.proposals.none { it.intentionId == id })
        assertTrue(snap.sessions.none { it.intentionId == id })
        assertFalse(
            snap.turns.any {
                it.text.contains("your rating is now", ignoreCase = true) ||
                    it.text.contains("you won", ignoreCase = true)
            }
        )
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first { it.id == id }.phase)
    }

    // —— OC7 Move — lease/notice then waiting pause, no invented secured plan ——
    @Test
    fun oc7_move_leaseAndNotice_thenWaitingPause_noInventedPlan() {
        val id = AdaptiveBuilderFixtures.seedOtherCase(store, "move", now)
        var t = now
        fun next(): BuilderDecision =
            AdaptiveBuilderEngine.suggestNextDecision(
                AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
            )
        fun answerAsk(ask: BuilderDecision.Ask, text: String, key: String, suffix: String) {
            assertTrue(
                AdaptiveBuilderEngine.applyDecision(store, ask, t, timeZone = tz).accepted
            )
            t += 1
            val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision
            assertTrue(
                AdaptiveBuilderEngine.answerQuestion(
                    store,
                    AnswerQuestion(
                        questionId = ask.questionId,
                        intentionId = id,
                        normalizedValue = text,
                        rawValue = text,
                        modality = "FREE_TEXT",
                        sourceTurnId = "turn-$suffix",
                        answeredAtEpochMs = t,
                        idempotencyKey = "idem-$suffix",
                        expectedRevision = rev
                    ),
                    factKey = key,
                    timeZone = tz
                ).accepted
            )
            t += 5
        }

        val lease = next()
        assertTrue(lease is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_LEASE_DEADLINE,
            (lease as BuilderDecision.Ask).purpose
        )
        answerAsk(lease, "Lease ends in six weeks", AdaptiveBuilderEngine.KEY_LEASE_DEADLINE, "lease")

        val housing = next()
        assertTrue(housing is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_HOUSING_REQUIREMENTS,
            (housing as BuilderDecision.Ask).purpose
        )
        answerAsk(
            housing,
            "One-bedroom under 900 euros",
            AdaptiveBuilderEngine.KEY_HOUSING_REQUIREMENTS,
            "housing"
        )

        val notice = next()
        assertTrue(notice is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_NOTICE_STATUS,
            (notice as BuilderDecision.Ask).purpose
        )
        answerAsk(
            notice,
            "Notice not given yet",
            AdaptiveBuilderEngine.KEY_NOTICE_STATUS,
            "notice"
        )

        val pause = next()
        assertTrue("expected waiting Pause, got $pause", pause is BuilderDecision.Pause)
        val pauseDec = pause as BuilderDecision.Pause
        assertTrue(pauseDec.reason.contains("waiting", ignoreCase = true))
        assertTrue(
            pauseDec.reason.contains("invented", ignoreCase = true) ||
                pauseDec.reason.contains("secured", ignoreCase = true) ||
                pauseDec.reason.contains("Waiting on someone", ignoreCase = true)
        )
        assertTrue(
            AdaptiveBuilderEngine.applyDecision(store, pauseDec, t, timeZone = tz).accepted
        )
        val snap = store.snapshot()
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first { it.id == id }.phase)
        assertTrue(snap.proposals.none { it.intentionId == id })
        assertTrue(snap.sessions.none { it.intentionId == id })
        assertEquals(3, AdaptiveBuilderEngine.activeFacts(snap, id).size)
        assertFalse(
            snap.turns.any {
                it.text.contains("viewing booked", ignoreCase = true) ||
                    it.text.contains("you have a flat", ignoreCase = true)
            }
        )
    }

    // —— OC9 Job offer — decide-by date, no auto-accept ——
    @Test
    fun oc9_jobOffer_decideByDate_thenPause_noAutoAccept() {
        val id = AdaptiveBuilderFixtures.seedOtherCase(store, "job_offer", now)
        var t = now
        fun next(): BuilderDecision =
            AdaptiveBuilderEngine.suggestNextDecision(
                AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!, t, tz
            )
        fun answerAsk(ask: BuilderDecision.Ask, value: String, factKey: String) {
            AdaptiveBuilderEngine.applyDecision(store, ask, t, timeZone = tz)
            t += 1
            val rev = AdaptiveBuilderEngine.projectContext(store.snapshot(), id, t, tz)!!.revision
            assertTrue(
                AdaptiveBuilderEngine.answerQuestion(
                    store,
                    AnswerQuestion(
                        questionId = ask.questionId,
                        intentionId = id,
                        normalizedValue = value,
                        rawValue = value,
                        modality = "FREE_TEXT",
                        sourceTurnId = "turn-$factKey-$t",
                        answeredAtEpochMs = t,
                        idempotencyKey = "idem-$factKey-$t",
                        expectedRevision = rev
                    ),
                    factKey = factKey,
                    timeZone = tz
                ).accepted
            )
            t += 1
        }

        val decideAsk = next()
        assertTrue(decideAsk is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_DECIDE_BY_DATE,
            (decideAsk as BuilderDecision.Ask).purpose
        )
        assertTrue(decideAsk.whyItMatters.contains("auto-accept", ignoreCase = true))
        answerAsk(decideAsk, "In ten days", AdaptiveBuilderEngine.KEY_DECIDE_BY_DATE)

        val criteriaAsk = next()
        assertTrue(criteriaAsk is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_DECISION_CRITERIA,
            (criteriaAsk as BuilderDecision.Ask).purpose
        )
        answerAsk(criteriaAsk, "Pay / compensation", AdaptiveBuilderEngine.KEY_DECISION_CRITERIA)

        val altsAsk = next()
        assertTrue(altsAsk is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_DECISION_ALTERNATIVES,
            (altsAsk as BuilderDecision.Ask).purpose
        )
        answerAsk(altsAsk, "Current role / job", AdaptiveBuilderEngine.KEY_DECISION_ALTERNATIVES)

        val unknownsAsk = next()
        assertTrue(unknownsAsk is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_DECISION_UNKNOWNS,
            (unknownsAsk as BuilderDecision.Ask).purpose
        )
        answerAsk(
            unknownsAsk,
            "Remote / hybrid policy",
            AdaptiveBuilderEngine.KEY_DECISION_UNKNOWNS
        )

        val askSource = next()
        assertTrue(askSource is BuilderDecision.Ask)
        assertEquals(
            AdaptiveBuilderEngine.KEY_COMPARISON_REFRESH,
            (askSource as BuilderDecision.Ask).purpose
        )
        assertTrue(askSource.options.any { it.contains("Ask the source", ignoreCase = true) })
        answerAsk(
            askSource,
            DecisionSupport.CHIP_ASK_SOURCE,
            AdaptiveBuilderEngine.KEY_COMPARISON_REFRESH
        )

        val pause = next()
        assertTrue(pause is BuilderDecision.Pause)
        assertTrue(
            (pause as BuilderDecision.Pause).reason.contains("auto-accept", ignoreCase = true) ||
                pause.reason.contains("no auto-accept", ignoreCase = true)
        )
        AdaptiveBuilderEngine.applyDecision(store, pause, t, timeZone = tz)
        val snap = store.snapshot()
        assertTrue(snap.proposals.none { it.intentionId == id })
        assertTrue(snap.sessions.none { it.intentionId == id })
        assertEquals(IntentionPhase.EXPLORING, snap.intentions.first { it.id == id }.phase)
    }
}
