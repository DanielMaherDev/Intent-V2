package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.ApproachStatus
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.OpenQuestionState
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore
import com.intent.launcher.ui.components.PracticeMinuteChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * F1–F4: chat-first create, in-chat quantity wheel integers, chips+Other.
 */
class CreateIntentionChatTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val registry = CapabilityRegistry.default
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun begin_opensConversationSession_withGreeting() {
        val begin = CreateIntentionChat.begin(store, now, registry)
        assertTrue(begin.ok)
        assertEquals(CreateIntentionChat.CONVERSATION_ROUTE, begin.navigateTo)
        assertEquals(CreateIntentionChat.SESSION_INTENTION_ID, begin.intentionId)
        assertEquals(
            CreateIntentionChat.SESSION_INTENTION_ID,
            store.snapshot().activeIntentionId
        )
        val greet = store.snapshot().turns.firstOrNull {
            it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID && it.role == "agent"
        }
        assertNotNull(greet)
        assertTrue(greet!!.text.contains("What would you like to work on"))
    }

    @Test
    fun description_projectsInChatQuantityWheel_withEveryInteger() {
        CreateIntentionChat.begin(store, now, registry)
        val result = CreateIntentionChat.submitUtterance(
            store = store,
            text = "I want to learn Spanish",
            nowEpochMs = now + 10,
            registry = registry
        )
        assertTrue(result.ok)
        assertEquals(BuilderPhase.MISSING_FACTS, store.snapshot().builderDraft.phase)

        val qtyBlock = store.snapshot().turns
            .flatMap { it.blocks }
            .mapNotNull { BuilderAnswerCard.decode(it) as? BuilderAnswerCard.Decoded.Quantity }
            .lastOrNull()
        assertNotNull(qtyBlock)
        assertEquals(1, qtyBlock!!.min)
        assertEquals(120, qtyBlock.max)
        assertEquals(120, qtyBlock.values.size)
        assertTrue(qtyBlock.values.containsAll(listOf(1, 17, 23, 47, 99, 120)))
        assertFalse(qtyBlock.values == listOf(5, 10, 15, 20, 30, 45, 60))
    }

    @Test
    fun afterQuantity_priorWheelDeactivated_onlyChoiceCardActive() {
        CreateIntentionChat.begin(store, now, registry)
        CreateIntentionChat.submitUtterance(
            store, "Speak Spanish at work", now + 10, registry
        )
        CreateIntentionChat.submitQuantity(
            store, 24, "minutes per day", now + 20, registry
        )
        val builderBlocks = store.snapshot().turns
            .filter { it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID }
            .flatMap { it.blocks }
            .filter { BuilderAnswerCard.isBuilderCard(it) }
        val qty = builderBlocks.filter {
            BuilderAnswerCard.decode(it.copy(active = true)) is BuilderAnswerCard.Decoded.Quantity ||
                it.sourceLabel.contains("|quantity|")
        }
        // Historical quantity cards must be inactive (AVD stale wheel).
        assertTrue(qty.isNotEmpty())
        assertTrue(qty.all { !it.active })
        val active = builderBlocks.filter { it.active }
        assertEquals(1, active.size)
        assertTrue(BuilderAnswerCard.decode(active.single()) is BuilderAnswerCard.Decoded.Choice)
        // No duplicate tutor question prose turn — only the card turn.
        val tutorMentions = store.snapshot().turns.count {
            it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID &&
                it.text.contains("tutor, class, or app", ignoreCase = true)
        }
        assertEquals(1, tutorMentions)
    }

    @Test
    fun beginAgain_promotesPriorCreate_noTranscriptResidue() {
        CreateIntentionChat.begin(store, now, registry)
        CreateIntentionChat.submitUtterance(
            store, "Speak Spanish at work", now + 10, registry
        )
        CreateIntentionChat.submitQuantity(
            store, 20, "minutes per day", now + 20, registry
        )
        CreateIntentionChat.submitChoice(
            store, "Learning app", now + 30, registry
        )
        // Confirm commit so shell has durable facts under intention-creating.
        CreateIntentionChat.submitChoice(
            store, "Confirm intention", now + 40, registry
        )
        assertTrue(
            store.snapshot().intentions.any { it.id == CreateIntentionChat.SESSION_INTENTION_ID }
        )

        val second = CreateIntentionChat.begin(store, now + 100, registry)
        assertTrue(second.ok)
        val snap = store.snapshot()
        // Prior Spanish shell remapped off intention-creating.
        assertTrue(snap.intentions.any { it.id.startsWith("intention-") && it.id != CreateIntentionChat.SESSION_INTENTION_ID })
        val createTurns = snap.turns.filter { it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID }
        assertTrue(createTurns.all {
            it.text.contains("What would you like to work on", ignoreCase = true) ||
                it.blocks.isEmpty()
        })
        assertFalse(createTurns.any { it.text.contains("Spanish", ignoreCase = true) })
        assertFalse(
            snap.facts.any { it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID }
        )
    }

    @Test
    fun quantityWheel_acceptsNonPresetInteger_thenChoiceWithOther() {
        CreateIntentionChat.begin(store, now, registry)
        CreateIntentionChat.submitUtterance(
            store, "Speak Spanish at work", now + 10, registry
        )
        val qty = CreateIntentionChat.submitQuantity(
            store = store,
            value = 23,
            unitLabel = PracticeMinuteChoices.UNIT,
            nowEpochMs = now + 20,
            registry = registry
        )
        assertTrue(qty.ok)
        assertEquals(
            "23 ${PracticeMinuteChoices.UNIT}",
            store.snapshot().builderDraft.factAnswers["practice_minutes"]
        )

        val choiceBlock = store.snapshot().turns
            .flatMap { it.blocks }
            .lastOrNull { it.type == ResponseBlockType.CHOICE_SET && it.active }
        assertNotNull(choiceBlock)
        assertTrue(choiceBlock!!.actions.any { ChoiceOptions.isOther(it) })
        assertTrue(choiceBlock.actions.any { it.contains("Tutor", ignoreCase = true) })
    }

    @Test
    fun otherPath_acceptsFreeText_thenConfirmCommits() {
        CreateIntentionChat.begin(store, now, registry)
        CreateIntentionChat.submitUtterance(
            store, "Speak Spanish at work", now + 10, registry
        )
        CreateIntentionChat.submitQuantity(
            store, 20, PracticeMinuteChoices.UNIT, now + 20, registry
        )
        val otherTap = CreateIntentionChat.submitChoice(
            store, ChoiceOptions.OTHER, now + 30, registry
        )
        assertTrue(otherTap.ok)
        assertTrue(otherTap.honestyNote!!.contains("Other", ignoreCase = true))

        val free = CreateIntentionChat.submitUtterance(
            store, "Weekly conversation club", now + 40, registry
        )
        assertTrue(free.ok)
        assertEquals(BuilderPhase.CONFIRM, store.snapshot().builderDraft.phase)

        val commit = CreateIntentionChat.submitChoice(
            store, "Confirm intention", now + 50, registry
        )
        assertTrue(commit.ok)
        assertEquals(
            CreateIntentionChat.SESSION_INTENTION_ID,
            commit.committedIntentionId
        )
        assertFalse(store.snapshot().builderDraft.active)
        assertTrue(
            store.snapshot().intentions.any { it.id == CreateIntentionChat.SESSION_INTENTION_ID }
        )
        assertTrue(
            store.snapshot().facts.any {
                it.intentionId == CreateIntentionChat.SESSION_INTENTION_ID &&
                    it.text.contains("conversation club", ignoreCase = true)
            }
        )
    }

    @Test
    fun practiceMinuteChoices_areContiguousWholeIntegers() {
        assertEquals(120, PracticeMinuteChoices.VALUES.size)
        assertEquals(1, PracticeMinuteChoices.VALUES.first())
        assertEquals(120, PracticeMinuteChoices.VALUES.last())
        assertEquals(
            PracticeMinuteChoices.VALUES,
            (PracticeMinuteChoices.MIN..PracticeMinuteChoices.MAX).toList()
        )
        // Sparse presets alone are not enough (F3).
        assertTrue(PracticeMinuteChoices.VALUES.contains(17))
        assertTrue(PracticeMinuteChoices.VALUES.contains(42))
    }

    @Test
    fun choiceOptions_withOther_isIdempotent() {
        val once = ChoiceOptions.withOther(listOf("A", "B"))
        assertEquals(listOf("A", "B", ChoiceOptions.OTHER), once)
        assertEquals(once, ChoiceOptions.withOther(once))
        assertEquals(
            ChoiceOptions.Selection.NeedsFreeText,
            ChoiceOptions.resolveSelection("Other", once)
        )
        assertEquals(
            ChoiceOptions.Selection.FreeText("Weekly club"),
            ChoiceOptions.resolveSelection("Weekly club", once)
        )
        assertEquals(
            ChoiceOptions.Selection.Chosen("A"),
            ChoiceOptions.resolveSelection("a", once)
        )
    }

    @Test
    fun confirm_languageCreate_withRelativeMove_bridgesAnchoredMoveEstimate() {
        CreateIntentionChat.begin(store, now, registry)
        CreateIntentionChat.submitUtterance(
            store,
            "I want to speak spanish at work. Im moving to Spain in about three months",
            now + 10,
            registry
        )
        CreateIntentionChat.submitQuantity(
            store, 20, PracticeMinuteChoices.UNIT, now + 20, registry
        )
        CreateIntentionChat.submitChoice(
            store, "Not yet", now + 30, registry
        )
        val commit = CreateIntentionChat.submitChoice(
            store, "Confirm intention", now + 40, registry
        )
        assertTrue(commit.ok)
        val id = CreateIntentionChat.SESSION_INTENTION_ID
        val move = store.snapshot().facts.firstOrNull {
            it.intentionId == id &&
                it.key == AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE &&
                it.status == FactStatus.ACTIVE
        }
        assertNotNull(move)
        assertNotNull(move!!.estimatedEpochMs)
        assertTrue(move.approximate)

        // §8.1 — after ~30 days, policy asks reconfirm (not language again).
        val later = now + java.util.concurrent.TimeUnit.DAYS.toMillis(30)
        assertTrue(
            AdaptiveBuilderEngine.projectPendingAsk(store, id, later)
        )
        val ask = store.snapshot().openQuestions.firstOrNull {
            it.intentionId == id && it.purpose == "reconfirm_move_estimate"
        }
        assertNotNull(ask)
        assertFalse(
            store.snapshot().openQuestions.any {
                it.intentionId == id &&
                    it.purpose == AdaptiveBuilderEngine.KEY_TARGET_LANGUAGE &&
                    it.state == OpenQuestionState.ASKED
            }
        )

        // §8.5 — free-text relative correction via reconfirm path (offline).
        val qid = ask!!.id
        // Mark current question on adaptive state for AdaptiveAnswerPath.
        val st = store.snapshot().adaptiveBuilderStates.first { it.intentionId == id }
        store.apply(
            com.intent.launcher.domain.FlowCommand.SetAdaptiveBuilderState(
                state = st.copy(currentQuestionId = qid),
                nowEpochMs = later + 1
            )
        )
        val corrected = AdaptiveAnswerPath.submit(
            store = store,
            intentionId = id,
            rawValue = "in about six months",
            modality = "FREE_TEXT",
            nowEpochMs = later + 2
        )
        assertTrue(corrected.honestyNote, corrected.accepted)
        val history = AdaptiveBuilderEngine.correctionHistory(
            store.snapshot(),
            AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE,
            id
        )
        assertEquals(2, history.size)
        assertEquals(FactStatus.CORRECTED, history.first { it.id == move.id }.status)
        assertEquals(
            FactStatus.ACTIVE,
            history.first { it.status == FactStatus.ACTIVE }.status
        )
        assertTrue(
            history.first { it.status == FactStatus.ACTIVE }.text.contains("six", ignoreCase = true)
        )
    }

    @Test
    fun confirm_languageCreate_bridgesToPendingPropose_withoutFixtures() {
        CreateIntentionChat.begin(store, now, registry)
        CreateIntentionChat.submitUtterance(
            store, "I want to learn Spanish", now + 10, registry
        )
        CreateIntentionChat.submitQuantity(
            store, 20, PracticeMinuteChoices.UNIT, now + 20, registry
        )
        CreateIntentionChat.submitChoice(
            store, "Learning app", now + 30, registry
        )
        val commit = CreateIntentionChat.submitChoice(
            store, "Confirm intention", now + 40, registry
        )
        assertTrue(commit.ok)
        assertNotNull(commit.pendingProposalId)
        assertTrue(commit.honestyNote!!.contains("review proposal", ignoreCase = true))

        val snap = store.snapshot()
        val id = CreateIntentionChat.SESSION_INTENTION_ID
        // Remapped engine keys (not only draft prompt ids).
        assertTrue(
            snap.facts.any {
                it.intentionId == id &&
                    it.key == AdaptiveBuilderEngine.KEY_PRACTICE_DURATION &&
                    it.text.contains("20")
            }
        )
        assertTrue(
            snap.facts.any {
                it.intentionId == id &&
                    it.key == AdaptiveBuilderEngine.KEY_PRACTICE_FREQUENCY &&
                    it.text.equals("Everyday", ignoreCase = true)
            }
        )
        assertTrue(
            snap.approaches.any {
                it.intentionId == id &&
                    it.id == "approach-learning-app" &&
                    it.status == ApproachStatus.SELECTED
            }
        )
        val pending = snap.proposals.firstOrNull {
            it.intentionId == id && it.status == ProposalStatus.PENDING
        }
        assertNotNull(pending)
        assertEquals(commit.pendingProposalId, pending!!.id)
        assertTrue(snap.sessions.isEmpty())
        assertEquals(
            IntentionPhase.PROPOSAL_PENDING,
            snap.intentions.first { it.id == id }.phase
        )
        assertTrue(
            snap.turns.any { turn ->
                turn.intentionId == id &&
                    turn.blocks.any { it.type == ResponseBlockType.PROPOSAL && it.active }
            }
        )
    }

    @Test
    fun isCreateInProgress_trueUntilAdaptiveBridgeAfterConfirm() {
        CreateIntentionChat.begin(store, now, registry)
        assertTrue(CreateIntentionChat.isCreateInProgress(store))
        CreateIntentionChat.submitUtterance(
            store,
            "I want to speak spanish. Im moving to Paris in about three months",
            now + 10,
            registry
        )
        assertTrue(CreateIntentionChat.isCreateInProgress(store))
        CreateIntentionChat.submitQuantity(
            store, 20, PracticeMinuteChoices.UNIT, now + 20, registry
        )
        CreateIntentionChat.submitChoice(store, "Not yet", now + 30, registry)
        CreateIntentionChat.submitChoice(store, "Confirm intention", now + 40, registry)
        assertFalse(
            "After Confirm, adaptive state exists — Discuss/CorrectFact owns the shell",
            CreateIntentionChat.isCreateInProgress(store)
        )
        assertTrue(CreateIntentionChat.isCreateSessionActive(store))
    }

    @Test
    fun correctMoveEstimate_afterConfirm_updatesActiveHorizonOffline() {
        CreateIntentionChat.begin(store, now, registry)
        CreateIntentionChat.submitUtterance(
            store,
            "I want to speak spanish. Im moving to Paris in about three months",
            now + 10,
            registry
        )
        CreateIntentionChat.submitQuantity(
            store, 20, PracticeMinuteChoices.UNIT, now + 20, registry
        )
        CreateIntentionChat.submitChoice(store, "Not yet", now + 30, registry)
        CreateIntentionChat.submitChoice(store, "Confirm intention", now + 40, registry)
        val id = CreateIntentionChat.SESSION_INTENTION_ID
        val prior = store.snapshot().facts.first {
            it.intentionId == id &&
                it.key == AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE &&
                it.status == FactStatus.ACTIVE
        }
        assertTrue(prior.text.contains("three", ignoreCase = true))
        val result = AdaptiveBuilderEngine.correctMoveEstimate(
            store = store,
            intentionId = id,
            newUtterance = "actually six months",
            nowEpochMs = now + 50
        )
        assertTrue(result.reason, result.accepted)
        val active = store.snapshot().facts.first {
            it.intentionId == id &&
                it.key == AdaptiveBuilderEngine.KEY_MOVE_ESTIMATE &&
                it.status == FactStatus.ACTIVE
        }
        assertTrue(active.text.contains("six", ignoreCase = true))
        assertFalse(active.text.contains("three", ignoreCase = true))
        assertEquals(
            FactStatus.CORRECTED,
            store.snapshot().facts.first { it.id == prior.id }.status
        )
    }

    @Test
    fun approachIdsForMethod_mapsCreateChips() {
        assertEquals(
            listOf("approach-learning-app"),
            CreateIntentionChat.approachIdsForMethod("Learning app")
        )
        assertEquals(
            listOf("approach-offline"),
            CreateIntentionChat.approachIdsForMethod("Tutor or class")
        )
        assertEquals(
            listOf("approach-ai-practice"),
            CreateIntentionChat.approachIdsForMethod("Self-study")
        )
        assertEquals(
            listOf("approach-other"),
            CreateIntentionChat.approachIdsForMethod("Weekly conversation club")
        )
        assertEquals(
            listOf("approach-other"),
            CreateIntentionChat.approachIdsForMethod("Not yet")
        )
        assertEquals(
            listOf("approach-other"),
            CreateIntentionChat.approachIdsForMethod(null)
        )
    }

    @Test
    fun otherFreeText_confirm_stillReachesPropose() {
        CreateIntentionChat.begin(store, now, registry)
        CreateIntentionChat.submitUtterance(
            store, "Speak Spanish at work", now + 10, registry
        )
        CreateIntentionChat.submitQuantity(
            store, 20, PracticeMinuteChoices.UNIT, now + 20, registry
        )
        CreateIntentionChat.submitChoice(
            store, ChoiceOptions.OTHER, now + 30, registry
        )
        CreateIntentionChat.submitUtterance(
            store, "Weekly conversation club", now + 40, registry
        )
        val commit = CreateIntentionChat.submitChoice(
            store, "Confirm intention", now + 50, registry
        )
        assertTrue(commit.ok)
        assertNotNull(commit.pendingProposalId)
        assertTrue(
            store.snapshot().approaches.any {
                it.id == "approach-other" && it.status == ApproachStatus.SELECTED
            }
        )
        assertNull(
            store.snapshot().sessions.firstOrNull()
        )
    }
}
