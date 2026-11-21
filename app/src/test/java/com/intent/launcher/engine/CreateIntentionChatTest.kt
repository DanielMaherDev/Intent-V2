package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.store.IntentionFlowStore
import com.intent.launcher.ui.components.PracticeMinuteChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
}
