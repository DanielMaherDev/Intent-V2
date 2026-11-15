package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.ChessScenario
import com.intent.launcher.domain.EuWorkSpainScenario
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.QuitSmokingScenario
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.domain.TrackerEntryStatus
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
 * Gate E §17 breadth — named must-haves 19, 21, 23, 26–29 plus remaining gaps
 * 2, 5, 17, 20, 24, 31–34. Domain + restart proofs; UI under Project media/gate-e/.
 */
class GateEScenarioSuiteTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var storeFile: File
    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        storeFile = tmp.newFile("gate-e-flow.json")
        store = IntentionFlowStore(storeFile)
    }

    // —— 19 Legal research ——
    @Test
    fun case19_legalResearch_conditionalChecklist_noInventedLaw() {
        EuWorkSpainScenario.seed(store, now)
        EuWorkSpainScenario.onChip(store, "Build a sourced research checklist", now + 10)
        var snap = store.snapshot()
        assertTrue(snap.proposals.none { it.id == EuWorkSpainScenario.PROPOSAL_ID })
        assertTrue(
            snap.turns.any {
                it.text.contains("will not invent", ignoreCase = true) ||
                    it.text.contains("Checklist stays conditional", ignoreCase = true)
            }
        )

        EuWorkSpainScenario.onChip(store, "I am an EU citizen", now + 20)
        EuWorkSpainScenario.onChip(store, "I already live in Spain", now + 30)
        EuWorkSpainScenario.onChip(store, "I plan to work for more than three months", now + 40)
        EuWorkSpainScenario.onChip(store, "I have not gathered documents yet", now + 50)
        EuWorkSpainScenario.onChip(store, "Build a sourced research checklist", now + 60)
        snap = store.snapshot()
        val proposal = snap.proposals.first { it.id == EuWorkSpainScenario.PROPOSAL_ID }
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertTrue(proposal.summary.contains("Not legal advice", ignoreCase = true))
        assertFalse(proposal.summary.contains("you must register", ignoreCase = true))
        EuWorkSpainScenario.CHECKLIST_RESEARCH_QUESTIONS.forEach { q ->
            assertTrue(q.contains("verify", ignoreCase = true) || q.contains("Confirm", ignoreCase = true))
            assertFalse(q.contains("Article ", ignoreCase = false))
        }
        assertEquals(
            CapabilityStatus.NOT_SUPPORTED,
            CapabilityRegistry.default.get(CapabilityRegistry.ID_WEB_RESEARCH)?.status
        )
    }

    // —— 21 Ambiguous multi-target ——
    @Test
    fun case21_ambiguousDuration_asksScope_noArbitraryWrite() {
        SpanishScenario.seed(store, now)
        MarathonScenario.seed(store, now + 1)
        val before = store.snapshot().trackerEntries.size
        val result = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = "turn-amb-1",
            commandId = "cmd-amb-1",
            entryId = "trk-amb-1",
            intentionId = null,
            moduleId = null,
            valueText = "Log 20 minutes",
            nowEpochMs = now + 100
        )
        assertEquals(GatewayStatus.AMBIGUOUS, result.status)
        assertTrue(result.blocks.any { it.type == ResponseBlockType.CHOICE_SET })
        assertTrue(result.message!!.contains("arbitrarily", ignoreCase = true))
        assertEquals(before, store.snapshot().trackerEntries.size)
        assertTrue(store.snapshot().trackerEntries.none { it.id == "trk-amb-1" })
    }

    @Test
    fun case21_explicitTarget_appliesOnce() {
        MarathonScenario.seed(store, now)
        val result = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = "turn-clear-1",
            commandId = "cmd-clear-1",
            entryId = "trk-clear-1",
            intentionId = MarathonScenario.INTENTION_ID,
            moduleId = "mileage",
            valueText = "Log 20 minutes",
            nowEpochMs = now + 50
        )
        assertEquals(GatewayStatus.APPLIED, result.status)
        assertEquals(1, store.snapshot().trackerEntries.count { it.id == "trk-clear-1" })
    }

    // —— 23 Stale connected data ——
    @Test
    fun case23_staleChessRating_notShownAsCurrent() {
        ChessScenario.seed(store, now)
        ChessScenario.onChip(store, "My rapid rating is about 900", now + 10)
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-chess-old",
                intentionId = ChessScenario.INTENTION_ID,
                moduleId = "rating_self_report",
                valueText = "About 900 (baseline)",
                nowEpochMs = now + 20,
                eventAtEpochMs = now - StaleConnectedSource.DEFAULT_STALE_AFTER_MS - 60_000L
            )
        )
        val view = StaleConnectedSource.evaluateChessRating(
            snapshot = store.snapshot(),
            intentionId = ChessScenario.INTENTION_ID,
            nowEpochMs = now + 30,
            registry = StaleConnectedSource.staleChessRegistry(),
            importedAtEpochMs = now - StaleConnectedSource.DEFAULT_STALE_AFTER_MS - 60_000L
        )
        assertTrue(view.isStale)
        assertTrue(view.label.contains("STALE", ignoreCase = true))
        assertFalse(view.label.startsWith("CURRENT"))
        assertTrue(view.actions.any { it.contains("manual", ignoreCase = true) })
        assertEquals("STALE", view.block.sourceLabel)
        assertEquals(CapabilityStatus.STALE, view.status)
    }

    @Test
    fun case23_unsupportedChess_manualFallback() {
        ChessScenario.seed(store, now)
        val view = StaleConnectedSource.evaluateChessRating(
            snapshot = store.snapshot(),
            intentionId = ChessScenario.INTENTION_ID,
            nowEpochMs = now + 1
        )
        assertTrue(view.isStale)
        assertEquals(CapabilityStatus.NOT_SUPPORTED, view.status)
        assertTrue(view.honestyNote.contains("manual", ignoreCase = true))
    }

    // —— 26 Cancelled agent turn ——
    @Test
    fun case26_cancelTurn_refusesLateMutation() {
        MarathonScenario.seed(store, now)
        val turnId = "turn-thinking-1"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = turnId,
                intentionId = MarathonScenario.INTENTION_ID,
                role = "user",
                text = "Log 20 minutes",
                nowEpochMs = now + 1
            )
        )
        val cancel = CommandGateway.cancelTurn(
            store = store,
            turnId = turnId,
            intentionId = MarathonScenario.INTENTION_ID,
            nowEpochMs = now + 2
        )
        assertEquals(GatewayStatus.CANCELLED, cancel.status)
        assertTrue(AgentTurnLifecycle.isCancelled(store.snapshot(), turnId))

        val late = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = turnId,
            commandId = "cmd-late-1",
            entryId = "trk-late-1",
            intentionId = MarathonScenario.INTENTION_ID,
            moduleId = "mileage",
            valueText = "20 minutes",
            nowEpochMs = now + 3
        )
        assertEquals(GatewayStatus.CANCELLED, late.status)
        assertTrue(store.snapshot().trackerEntries.none { it.id == "trk-late-1" })
    }

    // —— 27 Network retry / idempotency ——
    @Test
    fun case27_retrySameEntryId_noDuplicate() {
        MarathonScenario.seed(store, now)
        val first = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = "turn-retry-1",
            commandId = "cmd-retry-1",
            entryId = "trk-idem-1",
            intentionId = MarathonScenario.INTENTION_ID,
            moduleId = "mileage",
            valueText = "20 minutes",
            nowEpochMs = now + 10
        )
        assertEquals(GatewayStatus.APPLIED, first.status)
        val second = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = "turn-retry-1",
            commandId = "cmd-retry-1",
            entryId = "trk-idem-1",
            intentionId = MarathonScenario.INTENTION_ID,
            moduleId = "mileage",
            valueText = "20 minutes",
            nowEpochMs = now + 20
        )
        assertEquals(GatewayStatus.APPLIED, second.status)
        assertTrue(second.message!!.contains("Idempotent", ignoreCase = true))
        val active = store.snapshot().trackerEntries.filter {
            it.id == "trk-idem-1" && it.status == TrackerEntryStatus.ACTIVE
        }
        assertEquals(1, active.size)
    }

    // —— 28 Process death mid-proposal ——
    @Test
    fun case28_processDeath_recoversPendingProposal_noAutoApprove() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        val before = store.snapshot()
        assertEquals(
            ProposalStatus.PENDING,
            before.proposals.first { it.id == SpanishScenario.PROPOSAL_ID }.status
        )
        assertEquals(
            IntentionPhase.PROPOSAL_PENDING,
            before.intentions.first { it.id == SpanishScenario.INTENTION_ID }.phase
        )
        // Simulate process death: drop in-memory store, reopen same file.
        val recovery = AgentTurnLifecycle.recoverPendingProposal(
            fileStoreFactory = { IntentionFlowStore(it) },
            file = storeFile,
            proposalId = SpanishScenario.PROPOSAL_ID
        )
        assertTrue(recovery.recovered)
        assertEquals(ProposalStatus.PENDING, recovery.status)
        assertEquals(IntentionPhase.PROPOSAL_PENDING.name, recovery.phaseLabel)
        assertTrue(recovery.honestyNote.contains("not auto-approved", ignoreCase = true))

        val reopened = IntentionFlowStore(storeFile).snapshot()
        assertEquals(SCHEMA_VERSION, reopened.schemaVersion)
        assertEquals(
            ProposalStatus.PENDING,
            reopened.proposals.first { it.id == SpanishScenario.PROPOSAL_ID }.status
        )
        assertFalse(
            reopened.proposals.any {
                it.id == SpanishScenario.PROPOSAL_ID && it.status == ProposalStatus.APPROVED
            }
        )
    }

    // —— 29 Parent rollup ——
    @Test
    fun case29_parentRollup_labelsChildren_noDoubleCount() {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = "intention-health",
                title = "Health",
                statement = "Parent for running and cessation children",
                shapeTag = "other",
                nowEpochMs = now
            )
        )
        MarathonScenario.seed(store, now + 1)
        QuitSmokingScenario.seed(store, now + 2)
        store.apply(
            FlowCommand.SetParentIntention(
                intentionId = MarathonScenario.INTENTION_ID,
                parentIntentionId = "intention-health",
                nowEpochMs = now + 3
            )
        )
        store.apply(
            FlowCommand.SetParentIntention(
                intentionId = QuitSmokingScenario.INTENTION_ID,
                parentIntentionId = "intention-health",
                nowEpochMs = now + 4
            )
        )
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-run-1",
                intentionId = MarathonScenario.INTENTION_ID,
                moduleId = "mileage",
                valueText = "Easy 5 km",
                nowEpochMs = now + 5,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user"
            )
        )
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-smoke-1",
                intentionId = QuitSmokingScenario.INTENTION_ID,
                moduleId = "cigarettes_today",
                valueText = "3 cigarettes",
                nowEpochMs = now + 6,
                provenanceKind = ProvenanceKind.SAID,
                sourceLabel = "user"
            )
        )
        // Parent-owned summary row must not be rolled into child contributions.
        store.apply(
            FlowCommand.RecordTrackerEntry(
                entryId = "trk-parent-summary",
                intentionId = "intention-health",
                moduleId = "general_log",
                valueText = "Easy 5 km (parent copy — must not double-count)",
                nowEpochMs = now + 7
            )
        )
        // Corrected child row excluded from ACTIVE rollup.
        store.apply(
            FlowCommand.CorrectTrackerEntry(
                priorEntryId = "trk-smoke-1",
                newEntryId = "trk-smoke-1b",
                valueText = "2 cigarettes",
                nowEpochMs = now + 8
            )
        )
        val rollup = ParentRollup.build(store.snapshot(), "intention-health")
        assertNotNull(rollup)
        assertEquals(2, rollup!!.childCount)
        assertEquals(2, rollup.contributions.size)
        assertEquals(1, rollup.contributions.count { it.entryId == "trk-run-1" })
        assertEquals(1, rollup.contributions.count { it.entryId == "trk-smoke-1b" })
        assertTrue(rollup.contributions.none { it.entryId == "trk-smoke-1" })
        assertTrue(rollup.contributions.none { it.entryId == "trk-parent-summary" })
        assertTrue(rollup.contributions.any { it.childTitle.contains("marathon", ignoreCase = true) })
        assertTrue(rollup.honestyNote.contains("once", ignoreCase = true))
        assertTrue(rollup.saidCount >= 2)
    }

    @Test
    fun gateway_policyAndContractsVersions_documented() {
        assertEquals("intent.policy.v1", CommandGateway.POLICY_VERSION)
        assertEquals("intent.contracts.v1", CommandGateway.CONTRACTS_VERSION)
        assertEquals(10, SCHEMA_VERSION)
    }

    // —— 2 Vague health goal ——
    @Test
    fun case02_vagueHealth_exploringPlusNextQuestion_noSettledPlan() {
        val result = VagueHealthGoal.open(
            store = store,
            utterance = "I want to be healthier.",
            nowEpochMs = now
        )
        assertEquals(IntentionPhase.EXPLORING, result.phase)
        assertFalse(result.settledPlanPresented)
        assertTrue(result.nextQuestion.contains("sleep", ignoreCase = true))
        val snap = store.snapshot()
        val intention = snap.intentions.first { it.id == VagueHealthGoal.INTENTION_ID }
        assertEquals(IntentionPhase.EXPLORING, intention.phase)
        assertTrue(snap.turns.any { it.text == VagueHealthGoal.NEXT_QUESTION })
        assertTrue(
            snap.proposals.none {
                it.intentionId == VagueHealthGoal.INTENTION_ID &&
                    (it.status == ProposalStatus.PENDING || it.status == ProposalStatus.APPROVED)
            }
        )
        assertFalse(
            snap.turns.any {
                it.role == "agent" &&
                    it.text.contains("here is your complete health plan", ignoreCase = true)
            }
        )
    }

    // —— 5 App-led learning ——
    @Test
    fun case05_duolingoApproach_confirmedHold_noLessonCompletionClaim() {
        val result = AppDurationTask.confirmDuolingoApproach(
            store = store,
            nowEpochMs = now,
            utterance = "I'll practise with Duolingo"
        )
        assertTrue(result.approachConfirmed)
        assertEquals(SessionDestinations.SPANISH_PACKAGE, result.heldPackage)
        assertFalse(result.claimedLessonCompletion)
        assertTrue(
            store.snapshot().facts.any {
                it.id == AppDurationTask.APPROACH_FACT_ID &&
                    it.text.contains("not lesson completion", ignoreCase = true)
            }
        )
        SpanishScenario.onChip(
            store,
            "Track my Duolingo lesson completion automatically",
            now + 50
        )
        assertTrue(
            store.snapshot().turns.any {
                it.role == "agent" &&
                    (it.text.contains("not supported", ignoreCase = true) ||
                        it.text.contains("lesson", ignoreCase = true))
            }
        )
    }

    // —— 17 Physical constraint ——
    @Test
    fun case17_physicalConstraint_updatesConstraintAndRescheduleProposal() {
        MarathonScenario.seed(store, now)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", now + 10)
        MarathonScenario.approve(store, now + 20)
        val result = PhysicalConstraintChange.apply(
            store = store,
            nowEpochMs = now + 30,
            utterance = "I cannot run this week."
        )
        assertTrue(result.constraintRecorded)
        assertEquals(PhysicalConstraintChange.PROPOSAL_ID, result.proposalId)
        assertFalse(result.highIntensityLeftUnchanged)
        assertTrue(PhysicalConstraintChange.proposalIsEasyWeek(store))
        assertNotNull(result.deferredSessionId)
        val proposal = store.snapshot().proposals.first { it.id == PhysicalConstraintChange.PROPOSAL_ID }
        assertEquals(ProposalStatus.PENDING, proposal.status)
        assertTrue(proposal.summary.contains("not speed", ignoreCase = true))
        assertFalse(proposal.summary.contains("high-intensity intervals as planned", ignoreCase = true))
    }

    // —— 20 Cross-intention link ——
    @Test
    fun case20_crossIntentionLink_exactRelationProposal_noDuplicateOrGuess() {
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = "intention-health",
                title = "Health",
                statement = "Parent health goal",
                shapeTag = "other",
                nowEpochMs = now
            )
        )
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = "intention-gym",
                title = "Gym",
                statement = "Strength training",
                shapeTag = "other",
                nowEpochMs = now + 1
            )
        )
        val proposed = CrossIntentionLink.propose(
            store = store,
            childIntentionId = "intention-gym",
            parentIntentionId = "intention-health",
            relationLabel = "Gym is part of my Health goal.",
            nowEpochMs = now + 2
        )
        assertTrue(proposed.pending)
        assertFalse(proposed.applied)
        assertTrue(CrossIntentionLink.isPending(store, "intention-gym"))
        assertEquals(
            null,
            store.snapshot().intentions.first { it.id == "intention-gym" }.parentIntentionId
        )
        val confirmed = CrossIntentionLink.confirm(
            store = store,
            childIntentionId = "intention-gym",
            parentIntentionId = "intention-health",
            nowEpochMs = now + 3
        )
        assertTrue(confirmed.applied)
        assertEquals(
            "intention-health",
            store.snapshot().intentions.first { it.id == "intention-gym" }.parentIntentionId
        )
        assertEquals(1, store.snapshot().intentions.count { it.id == "intention-gym" })
        assertEquals(1, store.snapshot().intentions.count { it.id == "intention-health" })
    }

    // —— 24 Long-term adjustment ——
    @Test
    fun case24_tutorDayChange_correctedFactAndScheduleProposal_noSilentRewrite() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        SpanishScenario.approve(store, now + 20)
        val beforeDay = store.snapshot().sessions.first { it.id == SpanishScenario.SESSION_ID }.dayOfWeek
        assertEquals(4, beforeDay)
        val result = LongTermAdjustment.applyTutorTuesday(
            store = store,
            nowEpochMs = now + 30,
            utterance = "Tutor is now on Tuesdays."
        )
        assertEquals(LongTermAdjustment.CORRECTED_FACT_ID, result.correctedFactId)
        assertEquals(LongTermAdjustment.SCHEDULE_CHANGE_PROPOSAL_ID, result.proposalId)
        assertFalse(result.sessionSilentlyRewritten)
        assertEquals(
            4,
            store.snapshot().sessions.first { it.id == SpanishScenario.SESSION_ID }.dayOfWeek
        )
        val change = store.snapshot().proposals.first {
            it.id == LongTermAdjustment.SCHEDULE_CHANGE_PROPOSAL_ID
        }
        assertEquals(ProposalStatus.PENDING, change.status)
        assertEquals(LongTermAdjustment.TUESDAY, change.proposedDayOfWeek)
        assertTrue(
            store.snapshot().facts.any {
                it.id == LongTermAdjustment.CORRECTED_FACT_ID &&
                    it.text.contains("Tuesdays", ignoreCase = true)
            }
        )
    }

    // —— 31 Guided practice ——
    @Test
    fun case31_guidedPractice_roleObjectiveTranscriptOnSpanishIntention() {
        val result = GuidedPractice.start(
            store = store,
            nowEpochMs = now,
            utterance = "Let's practise a customer call in Spanish."
        )
        assertEquals(SpanishScenario.INTENTION_ID, result.intentionId)
        assertTrue(result.linkedToSpanishIntention)
        assertFalse(result.separateBotCreated)
        assertTrue(result.transcriptTurnIds.isNotEmpty())
        val snap = store.snapshot()
        assertTrue(snap.facts.any { it.id == GuidedPractice.ROLE_FACT_ID })
        assertTrue(snap.facts.any { it.id == GuidedPractice.OBJECTIVE_FACT_ID })
        assertTrue(
            snap.turns.any {
                it.id == result.feedbackTurnId &&
                    it.intentionId == SpanishScenario.INTENTION_ID
            }
        )
        assertTrue(
            snap.intentions.none {
                it.id != SpanishScenario.INTENTION_ID &&
                    it.title.contains("customer call", ignoreCase = true)
            }
        )
    }

    // —— 32 App-duration task ——
    @Test
    fun case32_appDuration_fifteenMinuteCriterion_softPauseResume_noLessonClaim() {
        val result = AppDurationTask.startFifteenMinutePractice(
            store = store,
            nowEpochMs = now,
            utterance = "Practise in Duolingo for 15 minutes."
        )
        assertEquals(15, result.criterionMinutes)
        assertNotNull(result.runId)
        assertTrue(result.softPaused)
        assertTrue(result.resumed)
        assertFalse(result.claimedLessonCompletion)
        assertTrue(
            store.snapshot().facts.any {
                it.id == AppDurationTask.DURATION_FACT_ID &&
                    it.text.contains("15", ignoreCase = false)
            }
        )
        val run = store.snapshot().sessionRuns.first { it.id == result.runId }
        assertEquals(SessionDestinations.SPANISH_PACKAGE, run.affinityPackageName)
        assertEquals(SessionRunStatus.IN_PROGRESS, run.status)
        assertTrue(
            store.snapshot().turns.none {
                it.text.contains("lesson was completed", ignoreCase = true)
            }
        )
    }

    // —— 33 Spoken approval ambiguity ——
    @Test
    fun case33_spokenYes_withTwoPending_asksClarification_approvesNeither() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        MarathonScenario.seed(store, now + 20)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", now + 30)
        assertEquals(
            2,
            store.snapshot().proposals.count { it.status == ProposalStatus.PENDING }
        )
        val result = CommandGateway.approveProposal(
            store = store,
            turnId = "turn-yes-1",
            commandId = "cmd-yes-1",
            sessionId = "session-should-not-apply",
            nowEpochMs = now + 40,
            utterance = "Yes"
        )
        assertEquals(GatewayStatus.AMBIGUOUS, result.status)
        assertTrue(result.blocks.any { it.type == ResponseBlockType.CHOICE_SET })
        assertTrue(result.message!!.contains("arbitrarily", ignoreCase = true))
        assertEquals(
            2,
            store.snapshot().proposals.count { it.status == ProposalStatus.PENDING }
        )
        assertTrue(
            store.snapshot().proposals.none { it.status == ProposalStatus.APPROVED }
        )
    }

    @Test
    fun case33_explicitProposalId_approvesOnlyThatOne() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        MarathonScenario.seed(store, now + 20)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", now + 30)
        val result = CommandGateway.approveProposal(
            store = store,
            turnId = "turn-yes-2",
            commandId = "cmd-yes-2",
            sessionId = SpanishScenario.SESSION_ID,
            nowEpochMs = now + 40,
            utterance = "Yes",
            explicitProposalId = SpanishScenario.PROPOSAL_ID
        )
        assertEquals(GatewayStatus.APPLIED, result.status)
        assertEquals(
            ProposalStatus.APPROVED,
            store.snapshot().proposals.first { it.id == SpanishScenario.PROPOSAL_ID }.status
        )
        assertEquals(
            ProposalStatus.PENDING,
            store.snapshot().proposals.first { it.id == MarathonScenario.PROPOSAL_ID }.status
        )
    }

    // —— 34 Interrupted voice ——
    @Test
    fun case34_interruptedVoice_cancelsSpeech_oneOrderedTurn_refusesLateDuplicate() {
        MarathonScenario.seed(store, now)
        val speakingTurnId = "turn-agent-speaking-1"
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = speakingTurnId,
                intentionId = MarathonScenario.INTENTION_ID,
                role = "agent",
                text = "I was in the middle of recommending a long tempo run…",
                nowEpochMs = now + 1,
                modality = "VOICE_TTS"
            )
        )
        val result = InterruptedVoice.bargeIn(
            store = store,
            intentionId = MarathonScenario.INTENTION_ID,
            speakingTurnId = speakingTurnId,
            userTranscript = "Wait — soft easy jog only today",
            nowEpochMs = now + 2
        )
        assertEquals(speakingTurnId, result.cancelledTurnId)
        assertTrue(result.lateDuplicateRefused)
        assertTrue(AgentTurnLifecycle.isCancelled(store.snapshot(), speakingTurnId))
        val turns = store.snapshot().turns.sortedBy { it.atEpochMs }
        val userIdx = turns.indexOfFirst { it.id == result.newUserTurnId }
        val cancelIdx = turns.indexOfFirst {
            it.id == AgentTurnLifecycle.cancelMarkerTurnId(speakingTurnId)
        }
        assertTrue(userIdx >= 0)
        assertTrue(cancelIdx >= 0)
        assertTrue(turns[userIdx].atEpochMs >= turns[cancelIdx].atEpochMs)
        assertTrue(
            store.snapshot().trackerEntries.none { it.id == "trk-late-voice-$speakingTurnId" }
        )
    }
}
