package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.domain.TrashKind
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * OS-143 — History / Trash are menu destinations with honest empty states;
 * presentation reuses the existing undo / trash store model.
 */
class HistoryTrashMenusTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun overflowEntries_areHistoryAndTrash_notPrimaryDestinations() {
        assertEquals(
            listOf(HistoryTrashMenus.MENU_HISTORY, HistoryTrashMenus.MENU_TRASH),
            HistoryTrashMenus.overflowEntries()
        )
        assertFalse(HistoryTrashMenus.overflowEntries().contains("Home"))
        assertFalse(HistoryTrashMenus.overflowEntries().contains("Intentions"))
        assertFalse(HistoryTrashMenus.overflowEntries().contains("Agent"))
    }

    @Test
    fun historyRows_empty_whenNoTransactions() {
        assertTrue(HistoryTrashMenus.historyRows(store.snapshot().transactions).isEmpty())
        assertTrue(HistoryTrashMenus.HISTORY_EMPTY.contains("device", ignoreCase = true))
        assertTrue(HistoryTrashMenus.LOCAL_ONLY_NOTE.contains("cloud", ignoreCase = true))
    }

    @Test
    fun historyRows_newestFirst_marksLastUndoable() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        SpanishScenario.approve(store, now + 20)
        val snap = store.snapshot()
        val last = CommandReversibility.lastUndoable(snap.transactions)!!
        val rows = HistoryTrashMenus.withUndoTarget(
            HistoryTrashMenus.historyRows(snap.transactions),
            last.id
        )
        assertTrue(rows.isNotEmpty())
        assertEquals(last.id, rows.first { it.canUndoAsLast }.id)
        assertEquals(1, rows.count { it.canUndoAsLast })
        assertTrue(rows.first().occurredAtEpochMs >= rows.last().occurredAtEpochMs)
    }

    @Test
    fun trashRows_empty_andRecoverableAfterSoftDelete() {
        assertTrue(HistoryTrashMenus.trashRows(emptyList(), now).isEmpty())
        assertTrue(HistoryTrashMenus.TRASH_EMPTY.contains("empty", ignoreCase = true))

        SpanishScenario.seed(store, now)
        store.apply(
            FlowCommand.SoftDeleteIntention(
                trashId = "trash-menu-1",
                intentionId = SpanishScenario.INTENTION_ID,
                nowEpochMs = now + 50
            )
        )
        val rows = HistoryTrashMenus.trashRows(store.snapshot().trash, now + 60)
        assertEquals(1, rows.size)
        assertEquals(TrashKind.INTENTION.name.lowercase(), rows.first().kindLabel)
        assertEquals("Until purge", rows.first().retentionLabel)
    }

    @Test
    fun statusLabel_distinguishesUndoneAndIrreversible() {
        SpanishScenario.seed(store, now)
        store.apply(
            FlowCommand.RecordExternalAction(
                transactionId = "ext-1",
                summary = "Opened Duolingo",
                irreversibleReason = "External app open cannot be undone.",
                nowEpochMs = now + 5
            )
        )
        val irreversible = store.snapshot().transactions.last()
        assertTrue(HistoryTrashMenus.statusLabel(irreversible).startsWith("Irreversible"))

        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        SpanishScenario.approve(store, now + 20)
        store.apply(FlowCommand.UndoLastReversible("undo-1", now + 30))
        val undone = store.snapshot().transactions.first { it.undone }
        assertEquals("Undone", HistoryTrashMenus.statusLabel(undone))
        val undoRecord = store.snapshot().transactions.last { it.undoOfTransactionId != null }
        assertEquals("Undo record", HistoryTrashMenus.statusLabel(undoRecord))
    }
}
