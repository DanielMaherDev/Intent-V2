package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.domain.TrashKind
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

class UndoTrashRestoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun undo_reversesApproveAndDecline() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
        SpanishScenario.approve(store, now + 20)
        var snap = store.snapshot()
        assertEquals(ProposalStatus.APPROVED, snap.proposals.first().status)
        assertEquals(1, snap.sessions.size)
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first().phase)
        assertTrue(CommandReversibility.lastUndoable(snap.transactions)!!.summary.contains("Approved"))

        store.apply(FlowCommand.UndoLastReversible("undo-1", now + 30))
        snap = store.snapshot()
        assertEquals(ProposalStatus.PENDING, snap.proposals.first().status)
        assertTrue(snap.sessions.isEmpty())
        assertEquals(IntentionPhase.PROPOSAL_PENDING, snap.intentions.first().phase)
        assertTrue(snap.transactions.any { it.undoOfTransactionId != null })

        SpanishScenario.decline(store, now + 40)
        snap = store.snapshot()
        assertEquals(ProposalStatus.DECLINED, snap.proposals.first().status)

        store.apply(FlowCommand.UndoLastReversible("undo-2", now + 50))
        snap = store.snapshot()
        assertEquals(ProposalStatus.PENDING, snap.proposals.first().status)
        assertEquals(IntentionPhase.PROPOSAL_PENDING, snap.intentions.first().phase)
    }

    @Test
    fun undo_reversesDefer() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 5)
        SpanishScenario.approve(store, now + 10)
        val deferUntil = now + 3_600_000L
        SpanishScenario.deferSession(store, deferUntil, now + 20)
        assertEquals(deferUntil, store.snapshot().sessions.first().deferredUntilEpochMs)

        store.apply(FlowCommand.UndoLastReversible("undo-defer", now + 30))
        assertNull(store.snapshot().sessions.first().deferredUntilEpochMs)
    }

    @Test
    fun softDeleteIntention_preservesChildren_andRestoreBringsBack() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 5)
        SpanishScenario.approve(store, now + 10)
        val beforeFacts = store.snapshot().facts.size
        assertTrue(beforeFacts >= 1)
        assertEquals(1, store.snapshot().sessions.size)

        store.apply(
            FlowCommand.SoftDeleteIntention(
                trashId = "trash-int-1",
                intentionId = SpanishScenario.INTENTION_ID,
                nowEpochMs = now + 20,
                expiresAtEpochMs = null
            )
        )
        var snap = store.snapshot()
        assertTrue(snap.intentions.none { it.id == SpanishScenario.INTENTION_ID })
        assertTrue(snap.facts.isEmpty())
        assertTrue(snap.sessions.isEmpty())
        assertEquals(1, snap.trash.size)
        assertEquals(TrashKind.INTENTION, snap.trash.first().kind)
        assertEquals(SpanishScenario.TITLE, snap.trash.first().pathLabel)

        store.apply(FlowCommand.RestoreTrashItem("trash-int-1", now + 30))
        snap = store.snapshot()
        assertEquals(1, snap.intentions.size)
        assertEquals(beforeFacts, snap.facts.size)
        assertEquals(1, snap.sessions.size)
        assertTrue(snap.trash.isEmpty())
        assertEquals(IntentionPhase.ACTIVE, snap.intentions.first().phase)
    }

    @Test
    fun softDeleteSession_keepsParentPath_andRestore() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 5)
        SpanishScenario.approve(store, now + 10)
        val sessionId = store.snapshot().sessions.first().id

        store.apply(
            FlowCommand.SoftDeleteSession(
                trashId = "trash-sess-1",
                sessionId = sessionId,
                nowEpochMs = now + 20
            )
        )
        var snap = store.snapshot()
        assertTrue(snap.sessions.isEmpty())
        assertEquals(1, snap.intentions.size)
        assertEquals(TrashKind.SESSION, snap.trash.first().kind)
        assertTrue(snap.trash.first().pathLabel.contains("/"))
        assertEquals(SpanishScenario.INTENTION_ID, snap.trash.first().parentIntentionId)

        store.apply(FlowCommand.RestoreTrashItem("trash-sess-1", now + 30))
        snap = store.snapshot()
        assertEquals(1, snap.sessions.size)
        assertTrue(snap.trash.isEmpty())
    }

    @Test
    fun purge_isIrreversible_andExternalActionSaysSo() {
        SpanishScenario.seed(store, now)
        store.apply(
            FlowCommand.SoftDeleteIntention(
                trashId = "trash-purge",
                intentionId = SpanishScenario.INTENTION_ID,
                nowEpochMs = now + 5
            )
        )
        store.apply(FlowCommand.PurgeTrashItem("trash-purge", now + 10))
        var snap = store.snapshot()
        assertTrue(snap.trash.isEmpty())
        assertTrue(snap.intentions.isEmpty())
        val purgeTxn = snap.transactions.last { it.commandKind == "PurgeTrashItem" }
        assertFalse(purgeTxn.reversible)
        assertNotNull(purgeTxn.irreversibleReason)

        // Undo skips purge and undoes soft-delete before it was purged? Last undoable
        // after purge is SoftDelete (purge is irreversible). Undoing soft-delete restores
        // the pre-delete snapshot which still had the intention (and empty trash).
        store.apply(FlowCommand.UndoLastReversible("undo-after-purge", now + 20))
        snap = store.snapshot()
        // Soft-delete was marked undone; restore from before soft-delete brings intention back.
        assertEquals(1, snap.intentions.size)

        store.apply(
            FlowCommand.RecordExternalAction(
                transactionId = "ext-1",
                summary = "Opened Duolingo",
                irreversibleReason = "Opening another app cannot be undone by INTENT.",
                nowEpochMs = now + 30
            )
        )
        snap = store.snapshot()
        val ext = snap.transactions.last { it.commandKind == "RecordExternalAction" }
        assertFalse(ext.reversible)
        assertTrue(ext.irreversibleReason!!.contains("cannot be undone", ignoreCase = true))
        assertNull(CommandReversibility.lastUndoable(snap.transactions)?.takeIf {
            it.id == ext.id
        })
        // Last undoable is still the prior reversible commit, not the external action.
        val undoable = CommandReversibility.lastUndoable(snap.transactions)
        assertNotNull(undoable)
        assertTrue(undoable!!.reversible)
        assertTrue(undoable.commandKind != "RecordExternalAction")
    }

    @Test
    fun expiredTrash_cannotRestore() {
        SpanishScenario.seed(store, now)
        store.apply(
            FlowCommand.SoftDeleteIntention(
                trashId = "trash-exp",
                intentionId = SpanishScenario.INTENTION_ID,
                nowEpochMs = now + 5,
                expiresAtEpochMs = now + 100
            )
        )
        store.apply(FlowCommand.RestoreTrashItem("trash-exp", now + 200))
        val snap = store.snapshot()
        assertTrue(snap.intentions.isEmpty())
        assertEquals(1, snap.trash.size)
    }

    @Test
    fun capabilityRegistry_gatesUndoAndTrash_andMarksExternalUndoUnsupported() {
        val registry = CapabilityRegistry.default
        assertTrue(registry.canUndoLast())
        assertTrue(registry.canSoftDelete())
        assertTrue(registry.canRestoreTrash())
        assertFalse(registry.canUndoExternalAction())
        assertEquals(
            CapabilityStatus.NOT_SUPPORTED,
            registry.get(CapabilityRegistry.ID_EXTERNAL_ACTION_UNDO)!!.status
        )
        assertTrue(
            registry.get(CapabilityRegistry.ID_EXTERNAL_ACTION_UNDO)!!.limitNote
                .contains("cannot be reversed", ignoreCase = true)
        )

        val locked = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map { entry ->
                if (entry.id == CapabilityRegistry.ID_UNDO_LAST ||
                    entry.id == CapabilityRegistry.ID_TRASH_SOFT_DELETE ||
                    entry.id == CapabilityRegistry.ID_TRASH_RESTORE
                ) {
                    entry.copy(status = CapabilityStatus.NOT_SUPPORTED)
                } else {
                    entry
                }
            }
        )
        assertFalse(locked.canUndoLast())
        assertFalse(locked.canSoftDelete())
        assertFalse(locked.canRestoreTrash())
    }

    @Test
    fun undo_persistsAcrossRestart() {
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 5)
        SpanishScenario.approve(store, now + 10)
        store.apply(FlowCommand.UndoLastReversible("undo-restart", now + 20))

        val restarted = IntentionFlowStore(tmp.root.resolve("flow.json"))
        val snap = restarted.snapshot()
        assertEquals(ProposalStatus.PENDING, snap.proposals.first().status)
        assertTrue(snap.sessions.isEmpty())
        assertTrue(snap.transactions.any { it.undone })
    }
}
