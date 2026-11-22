package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.store.IntentionFlowStore
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
 * §17 case 20 — relation card projection + typed dispatchGateEUtterance path.
 */
class CrossIntentionRelationCardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_200_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow-relation.json"))
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
        assertEquals(11, SCHEMA_VERSION)
    }

    @Test
    fun resolve_matchesExistingTitles_doesNotInvent() {
        val resolved = CrossIntentionLink.resolveFromUtterance(
            store = store,
            utterance = "Gym is part of my Health goal.",
            activeIntentionId = "intention-gym"
        )
        assertTrue(resolved.matched)
        assertEquals("intention-gym", resolved.childIntentionId)
        assertEquals("intention-health", resolved.parentIntentionId)
    }

    @Test
    fun resolve_unknownTitles_asksClarification_noGuess() {
        val resolved = CrossIntentionLink.resolveFromUtterance(
            store = store,
            utterance = "Yoga is part of my Wellness goal."
        )
        assertFalse(resolved.matched)
        assertNull(resolved.childIntentionId)
        assertNull(resolved.parentIntentionId)
        assertTrue(resolved.honestyNote.contains("Clarification", ignoreCase = true))
    }

    @Test
    fun typedFreeText_proposesRelationWithoutSeed() {
        store.apply(
            FlowCommand.SetActiveIntention(
                intentionId = "intention-gym",
                nowEpochMs = now + 2
            )
        )
        val gym = store.snapshot().intentions.first { it.id == "intention-gym" }
        val result = ConversationCoordinator.submitFreeText(
            store = store,
            intention = gym,
            text = "Gym is part of my Health goal.",
            nowEpochMs = now + 3
        )
        assertEquals("Gym is part of my Health goal.", result.appliedChip)
        assertTrue(CrossIntentionLink.isPending(store, "intention-gym"))
        assertNull(
            store.snapshot().intentions.first { it.id == "intention-gym" }.parentIntentionId
        )
        val block = store.snapshot().turns
            .flatMap { it.blocks }
            .first { it.sourceLabel == CrossIntentionLink.SOURCE_LABEL && it.type.name == "PROPOSAL" }
        val card = CrossIntentionRelationCard.fromBlock(block, store.snapshot().intentions)
        assertNotNull(card)
        assertTrue(card!!.pending)
        assertEquals("Gym", card.childTitle)
        assertEquals("Health", card.parentTitle)
        assertTrue(card.honestyNote.contains("dependency graph", ignoreCase = true))
        assertFalse(card.honestyNote.contains("scheduled", ignoreCase = true))
    }

    @Test
    fun confirmAction_setsParent_noDuplicate() {
        CrossIntentionLink.propose(
            store = store,
            childIntentionId = "intention-gym",
            parentIntentionId = "intention-health",
            relationLabel = "Gym is part of my Health goal.",
            nowEpochMs = now + 10
        )
        val block = store.snapshot().turns
            .flatMap { it.blocks }
            .first { it.blockId.startsWith("block-relation-") }
        val confirmed = CrossIntentionRelationCard.onAction(
            store = store,
            block = block,
            actionIndex = 0,
            nowEpochMs = now + 11
        )
        assertNotNull(confirmed)
        assertTrue(confirmed!!.applied)
        assertFalse(CrossIntentionLink.isPending(store, "intention-gym"))
        assertEquals(
            "intention-health",
            store.snapshot().intentions.first { it.id == "intention-gym" }.parentIntentionId
        )
        assertEquals(1, store.snapshot().intentions.count { it.id == "intention-gym" })
        assertEquals(SCHEMA_VERSION, store.snapshot().schemaVersion)
    }

    @Test
    fun cancelAction_clearsPending_noParentSet() {
        CrossIntentionLink.propose(
            store = store,
            childIntentionId = "intention-gym",
            parentIntentionId = "intention-health",
            relationLabel = "Gym is part of my Health goal.",
            nowEpochMs = now + 20
        )
        val block = store.snapshot().turns
            .flatMap { it.blocks }
            .first { it.blockId.startsWith("block-relation-") }
        val cancelled = CrossIntentionRelationCard.onAction(
            store = store,
            block = block,
            actionIndex = 1,
            nowEpochMs = now + 21
        )
        assertNotNull(cancelled)
        assertFalse(cancelled!!.applied)
        assertFalse(CrossIntentionLink.isPending(store, "intention-gym"))
        assertNull(
            store.snapshot().intentions.first { it.id == "intention-gym" }.parentIntentionId
        )
    }

    @Test
    fun relatedStrip_onlyWhenLocalPointersExist() {
        val before = IntentionHierarchy.build(
            store.snapshot(),
            store.snapshot().intentions.first { it.id == "intention-gym" }
        )
        assertNull(CrossIntentionRelationCard.relatedStrip(before))

        store.apply(
            FlowCommand.SetParentIntention(
                intentionId = "intention-gym",
                parentIntentionId = "intention-health",
                nowEpochMs = now + 30
            )
        )
        val after = IntentionHierarchy.build(
            store.snapshot(),
            store.snapshot().intentions.first { it.id == "intention-gym" }
        )
        val strip = CrossIntentionRelationCard.relatedStrip(after)
        assertNotNull(strip)
        assertTrue(strip!!.lines.any { it.contains("Health") })
        assertTrue(strip.honestyNote.contains("local", ignoreCase = true))
    }

    @Test
    fun proposalFromBlock_skipsRelationSource_noScheduleFiction() {
        CrossIntentionLink.propose(
            store = store,
            childIntentionId = "intention-gym",
            parentIntentionId = "intention-health",
            relationLabel = "Gym is part of my Health goal.",
            nowEpochMs = now + 40
        )
        val block = store.snapshot().turns.flatMap { it.blocks }
            .first { it.sourceLabel == CrossIntentionLink.SOURCE_LABEL }
        assertNull(
            AgentModuleProjection.proposalFromBlock(block, "Gym", proposal = null)
        )
    }
}
