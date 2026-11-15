package com.intent.launcher.engine

import com.intent.launcher.domain.MarathonScenario
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.FlowCommand
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
 * GatewayStatus Conversation cards beyond AMBIGUOUS — cancel / stale / retry (JVM).
 */
class GatewayStatusCardTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_100_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow-gateway-cards.json"))
    }

    @Test
    fun schemaStaysTen() {
        assertEquals(10, SCHEMA_VERSION)
        MarathonScenario.seed(store, now)
        assertEquals(SCHEMA_VERSION, store.snapshot().schemaVersion)
    }

    @Test
    fun cancelTurn_projectsCancelledCard() {
        MarathonScenario.seed(store, now)
        val turnId = "turn-thinking-card"
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
        val block = cancel.blocks.first { it.type == ResponseBlockType.ERROR }
        val card = GatewayStatusCard.fromBlock(block)
        assertNotNull(card)
        assertEquals(GatewayStatus.CANCELLED.name, card!!.statusLabel)
        assertTrue(card.eyebrow.startsWith("CANCELLED"))
        assertEquals(GatewayStatusCard.SOURCE_CANCEL, card.sourceLabel)
        assertTrue(card.actions.isEmpty())
    }

    @Test
    fun staleRevision_projectsCard_withRetryChip_andRetryApplies() {
        MarathonScenario.seed(store, now)
        val rev0 = CommandGateway.revisionOf(store.snapshot())
        // Bump revision so expectedRevision becomes stale.
        store.apply(
            FlowCommand.AddConversationTurn(
                turnId = "turn-bump-rev",
                intentionId = MarathonScenario.INTENTION_ID,
                role = "user",
                text = "side note",
                nowEpochMs = now + 5
            )
        )
        assertTrue(CommandGateway.revisionOf(store.snapshot()) > rev0)

        val stale = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = "turn-stale-1",
            commandId = "cmd-stale-1",
            entryId = "trk-stale-1",
            intentionId = MarathonScenario.INTENTION_ID,
            moduleId = "mileage",
            valueText = "20 minutes",
            nowEpochMs = now + 10,
            expectedRevision = rev0
        )
        assertEquals(GatewayStatus.STALE_REVISION, stale.status)
        assertTrue(store.snapshot().trackerEntries.none { it.id == "trk-stale-1" })

        val block = stale.blocks.first()
        val card = GatewayStatusCard.fromBlock(block)
        assertNotNull(card)
        assertEquals(GatewayStatus.STALE_REVISION.name, card!!.statusLabel)
        assertEquals(GatewayStatusCard.SOURCE_STALE, card.sourceLabel)
        assertEquals(listOf(GatewayStatusCard.ACTION_RETRY), card.actions.map { it.label })
        assertNotNull(GatewayStatusCard.decodeTrackerRetry(block.linkedObjectIds.first()))

        val applied = GatewayStatusCard.onAction(
            store = store,
            block = block,
            actionIndex = 0,
            nowEpochMs = now + 20
        )
        assertNotNull(applied)
        assertEquals(GatewayStatus.APPLIED, applied!!.status)
        assertEquals(
            1,
            store.snapshot().trackerEntries.count {
                it.id == "trk-stale-1" && it.status == TrackerEntryStatus.ACTIVE
            }
        )
    }

    @Test
    fun idempotentRetry_projectsRetryCard_noDuplicate() {
        MarathonScenario.seed(store, now)
        val first = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = "turn-retry-card",
            commandId = "cmd-retry-card",
            entryId = "trk-idem-card",
            intentionId = MarathonScenario.INTENTION_ID,
            moduleId = "mileage",
            valueText = "20 minutes",
            nowEpochMs = now + 10
        )
        assertEquals(GatewayStatus.APPLIED, first.status)
        assertTrue(first.blocks.isEmpty())

        val second = CommandGateway.recordTrackerEntry(
            store = store,
            turnId = "turn-retry-card",
            commandId = "cmd-retry-card",
            entryId = "trk-idem-card",
            intentionId = MarathonScenario.INTENTION_ID,
            moduleId = "mileage",
            valueText = "20 minutes",
            nowEpochMs = now + 20
        )
        assertEquals(GatewayStatus.APPLIED, second.status)
        assertTrue(second.message!!.contains("Idempotent", ignoreCase = true))
        val block = second.blocks.first()
        val card = GatewayStatusCard.fromBlock(block)
        assertNotNull(card)
        assertEquals("RETRY", card!!.statusLabel)
        assertEquals(GatewayStatusCard.SOURCE_IDEMPOTENT, card.sourceLabel)
        assertEquals(
            1,
            store.snapshot().trackerEntries.count {
                it.id == "trk-idem-card" && it.status == TrackerEntryStatus.ACTIVE
            }
        )
        // SCHEMA stamp unchanged
        assertEquals(SCHEMA_VERSION, store.snapshot().schemaVersion)
        assertFalse(card.actions.isNotEmpty())
    }
}
