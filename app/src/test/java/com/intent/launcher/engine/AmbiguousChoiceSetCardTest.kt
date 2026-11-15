package com.intent.launcher.engine

import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SCHEMA_VERSION
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

/**
 * §17 case 33 / 21 — AMBIGUOUS CHOICE_SET Conversation card path (JVM).
 * Asserts card projection + chip selection without Compose.
 */
class AmbiguousChoiceSetCardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun schemaStaysTen_stampOnly() {
        assertEquals(10, SCHEMA_VERSION)
        SpanishScenario.seed(store, now)
        assertEquals(SCHEMA_VERSION, store.snapshot().schemaVersion)
    }

    @Test
    fun case33_ambiguousYes_projectsChoiceSetCard_withProposalChips() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        MarathonScenario.seed(store, now + 20)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", now + 30)

        val result = CommandGateway.approveProposal(
            store = store,
            turnId = "turn-yes-card",
            commandId = "cmd-yes-card",
            sessionId = "session-should-not-apply",
            nowEpochMs = now + 40,
            utterance = "Yes"
        )
        assertEquals(GatewayStatus.AMBIGUOUS, result.status)

        val block = result.blocks.first { it.type == ResponseBlockType.CHOICE_SET }
        val card = AmbiguousChoiceSetCard.fromBlock(block)
        assertNotNull(card)
        assertEquals(AmbiguousChoiceSetCard.STATUS_LABEL, card!!.statusLabel)
        assertTrue(card.eyebrow.startsWith("AMBIGUOUS"))
        assertTrue(card.eyebrow.contains("Which proposal"))
        assertEquals(AmbiguousChoiceSetCard.SOURCE_APPROVAL, card.sourceLabel)
        assertEquals(2, card.choices.size)
        assertTrue(card.choices.any { it.linkedObjectId == SpanishScenario.PROPOSAL_ID })
        assertTrue(card.choices.any { it.linkedObjectId == MarathonScenario.PROPOSAL_ID })
        assertTrue(card.choices.any { it.label == "Weekly Spanish session" })
        assertTrue(card.choices.any { it.label == "Sunday long run" })
        assertEquals(
            2,
            store.snapshot().proposals.count { it.status == ProposalStatus.PENDING }
        )
    }

    @Test
    fun case33_choiceChip_approvesOnlySelectedProposal() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        MarathonScenario.seed(store, now + 20)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", now + 30)

        val ambiguous = CommandGateway.approveProposal(
            store = store,
            turnId = "turn-yes-2",
            commandId = "cmd-yes-2",
            sessionId = "session-unresolved",
            nowEpochMs = now + 40,
            utterance = "Yes"
        )
        assertEquals(GatewayStatus.AMBIGUOUS, ambiguous.status)
        val block = ambiguous.blocks.first { it.type == ResponseBlockType.CHOICE_SET }
        val card = AmbiguousChoiceSetCard.fromBlock(block)!!
        val spanishIndex = card.choices.indexOfFirst {
            it.linkedObjectId == SpanishScenario.PROPOSAL_ID
        }
        assertTrue(spanishIndex >= 0)

        val selected = AmbiguousChoiceSetCard.select(
            store = store,
            block = block,
            choiceIndex = spanishIndex,
            nowEpochMs = now + 50,
            sessionIdForProposal = { id ->
                when (id) {
                    SpanishScenario.PROPOSAL_ID -> SpanishScenario.SESSION_ID
                    MarathonScenario.PROPOSAL_ID -> MarathonScenario.SESSION_ID
                    else -> "session-$id"
                }
            }
        )
        assertNotNull(selected)
        assertEquals(GatewayStatus.APPLIED, selected!!.status)
        assertEquals(
            ProposalStatus.APPROVED,
            store.snapshot().proposals.first { it.id == SpanishScenario.PROPOSAL_ID }.status
        )
        assertEquals(
            ProposalStatus.PENDING,
            store.snapshot().proposals.first { it.id == MarathonScenario.PROPOSAL_ID }.status
        )
    }

    @Test
    fun case21_ambiguousTracker_projectsChoiceSetCard_andChipAppliesOnce() {
        SpanishScenario.seed(store, now)
        MarathonScenario.seed(store, now + 1)
        val before = store.snapshot().trackerEntries.size

        val ambiguous = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = "turn-trk-amb",
            commandId = "cmd-trk-amb",
            entryId = "trk-amb-card",
            intentionId = null,
            moduleId = null,
            valueText = "Log 20 minutes",
            nowEpochMs = now + 100
        )
        assertEquals(GatewayStatus.AMBIGUOUS, ambiguous.status)
        assertEquals(before, store.snapshot().trackerEntries.size)

        val block = ambiguous.blocks.first { it.type == ResponseBlockType.CHOICE_SET }
        val card = AmbiguousChoiceSetCard.fromBlock(block)
        assertNotNull(card)
        assertTrue(card!!.eyebrow.startsWith("AMBIGUOUS"))
        assertEquals(AmbiguousChoiceSetCard.SOURCE_SCOPE, card.sourceLabel)
        assertTrue(card.choices.size >= 2)
        card.choices.forEach { choice ->
            assertNotNull(AmbiguousChoiceSetCard.decodeTrackerLink(choice.linkedObjectId))
        }

        val marathonIndex = card.choices.indexOfFirst {
            val decoded = AmbiguousChoiceSetCard.decodeTrackerLink(it.linkedObjectId)
            decoded?.first == MarathonScenario.INTENTION_ID
        }
        assertTrue(marathonIndex >= 0)

        val applied = AmbiguousChoiceSetCard.select(
            store = store,
            block = block,
            choiceIndex = marathonIndex,
            nowEpochMs = now + 200
        )
        assertNotNull(applied)
        assertEquals(GatewayStatus.APPLIED, applied!!.status)
        val entries = store.snapshot().trackerEntries.filter {
            it.status == TrackerEntryStatus.ACTIVE
        }
        assertEquals(1, entries.size)
        assertEquals(MarathonScenario.INTENTION_ID, entries.first().intentionId)
        assertFalse(entries.any { it.intentionId == SpanishScenario.INTENTION_ID })
    }

    @Test
    fun pendingAmbiguousChoiceSets_listsActiveBlocks() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        MarathonScenario.seed(store, now + 20)
        MarathonScenario.onChip(store, "Long runs feel best on Sundays", now + 30)
        CommandGateway.approveProposal(
            store = store,
            turnId = "turn-list",
            commandId = "cmd-list",
            sessionId = "x",
            nowEpochMs = now + 40,
            utterance = "Yes"
        )
        val pending = AmbiguousChoiceSetCard.pendingAmbiguousChoiceSets(store)
        assertEquals(1, pending.size)
        assertEquals(ResponseBlockType.CHOICE_SET, pending.first().type)
        assertTrue(pending.first().active)
    }
}
