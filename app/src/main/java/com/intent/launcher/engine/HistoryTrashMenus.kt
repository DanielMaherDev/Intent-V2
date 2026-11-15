package com.intent.launcher.engine

import com.intent.launcher.domain.FlowTransaction
import com.intent.launcher.domain.TrashEntry

/**
 * OS-143 — History and Trash are discoverable from overflow / intention menus,
 * not primary destinations. Presentation helpers stay pure for unit tests.
 */
object HistoryTrashMenus {
    const val MENU_HISTORY = "History"
    const val MENU_TRASH = "Trash"

    const val HISTORY_EMPTY =
        "No local history yet. Actions you take in INTENT are recorded on this device only."

    const val TRASH_EMPTY =
        "Trash is empty. Soft-deleted intentions and sessions appear here until you restore or purge them."

    const val LOCAL_ONLY_NOTE =
        "Local to this phone — INTENT does not sync history or trash to the cloud."

    /** Newest-first rows for the History screen. */
    fun historyRows(
        transactions: List<FlowTransaction>,
        limit: Int = 80
    ): List<HistoryRow> =
        transactions
            .asReversed()
            .take(limit)
            .map { txn ->
                HistoryRow(
                    id = txn.id,
                    summary = txn.summary,
                    occurredAtEpochMs = txn.occurredAtEpochMs,
                    statusLabel = statusLabel(txn),
                    canUndoAsLast = false
                )
            }

    fun statusLabel(txn: FlowTransaction): String =
        when {
            txn.undoOfTransactionId != null -> "Undo record"
            txn.undone -> "Undone"
            !txn.reversible ->
                txn.irreversibleReason?.let { "Irreversible · $it" } ?: "Irreversible"
            else -> "Reversible"
        }

    /**
     * Marks the current last-undoable transaction so the UI can offer Undo
     * without inventing a redo stack.
     */
    fun withUndoTarget(
        rows: List<HistoryRow>,
        lastUndoableId: String?
    ): List<HistoryRow> =
        if (lastUndoableId == null) {
            rows
        } else {
            rows.map { row ->
                if (row.id == lastUndoableId) row.copy(canUndoAsLast = true) else row
            }
        }

    fun trashRows(
        trash: List<TrashEntry>,
        nowEpochMs: Long
    ): List<TrashRow> =
        trash
            .filter { entry ->
                entry.expiresAtEpochMs == null || entry.expiresAtEpochMs >= nowEpochMs
            }
            .sortedByDescending { it.deletedAtEpochMs }
            .map { entry ->
                TrashRow(
                    id = entry.id,
                    title = entry.title,
                    kindLabel = entry.kind.name.lowercase(),
                    pathLabel = entry.pathLabel,
                    retentionLabel = if (entry.expiresAtEpochMs == null) {
                        "Until purge"
                    } else {
                        "Expires if not restored"
                    }
                )
            }

    /** Overflow / intention menu entries — never primary nav destinations. */
    fun overflowEntries(): List<String> = listOf(MENU_HISTORY, MENU_TRASH)
}

data class HistoryRow(
    val id: String,
    val summary: String,
    val occurredAtEpochMs: Long,
    val statusLabel: String,
    val canUndoAsLast: Boolean = false
)

data class TrashRow(
    val id: String,
    val title: String,
    val kindLabel: String,
    val pathLabel: String,
    val retentionLabel: String
)
