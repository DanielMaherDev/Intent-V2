package com.intent.launcher.engine

import com.intent.launcher.domain.ErrandStatus
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.SessionRunStatus
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrashKind
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Gate E exit 2 — migrate a real exported legacy dump (schema 0) through
 * IntentionFlowStore.migrate(), preserving tasks, tracker, history, home order, deleted.
 */
class LegacyExportMigrationTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun migrateRealLegacyExport_preservesTasksTrackerHistoryHomeOrderDeleted() {
        val raw = readFixture("fixtures/legacy-export-schema0.json")
        assertTrue(raw.contains("\"schemaVersion\": 0") || raw.contains("\"schemaVersion\":0"))
        val file = tmp.newFile("legacy-export.json")
        file.writeText(raw)

        val store = IntentionFlowStore(file)
        val snap = store.snapshot()
        assertEquals(SCHEMA_VERSION, snap.schemaVersion)

        // Tasks / completed session runs
        val completed = snap.sessionRuns.filter { it.status == SessionRunStatus.COMPLETED }
        assertEquals(1, completed.size)
        assertEquals("run-legacy-tutor-done", completed.single().id)
        assertEquals(1, snap.sessions.size)
        assertEquals("session-legacy-tutor", snap.sessions.single().id)

        // Tracker (active + soft-deleted)
        assertEquals(3, snap.trackerEntries.size)
        assertEquals(
            TrackerEntryStatus.ACTIVE,
            snap.trackerEntries.first { it.id == "trk-legacy-practice" }.status
        )
        assertEquals(
            TrackerEntryStatus.DELETED,
            snap.trackerEntries.first { it.id == "trk-legacy-deleted" }.status
        )

        // Conversation history
        assertEquals(2, snap.turns.size)
        assertTrue(snap.turns.any { it.role == "user" && it.text.contains("tutor") })

        // Home / drawer order
        assertEquals(
            listOf(
                "intention-legacy-spanish",
                "intention-legacy-health",
                "intention-legacy-run"
            ),
            snap.intentionOrder
        )

        // Deleted items in trash
        assertEquals(1, snap.trash.size)
        assertEquals(TrashKind.INTENTION, snap.trash.single().kind)
        assertEquals("intention-legacy-old-biz", snap.trash.single().entityId)

        // Completed errand (task-like) + parent/child pointer
        assertEquals(ErrandStatus.DONE, snap.errandTodos.single().status)
        assertEquals(
            "intention-legacy-health",
            snap.intentions.first { it.id == "intention-legacy-run" }.parentIntentionId
        )

        // New collections default empty (stamp migration)
        assertTrue(snap.occurrences.isEmpty())
        assertTrue(snap.dailyContexts.isEmpty())

        // Restart after migration
        val reopened = IntentionFlowStore(file).snapshot()
        assertEquals(SCHEMA_VERSION, reopened.schemaVersion)
        assertEquals(snap.intentionOrder, reopened.intentionOrder)
        assertEquals(snap.trackerEntries.map { it.id }, reopened.trackerEntries.map { it.id })
        assertEquals(snap.trash.map { it.id }, reopened.trash.map { it.id })
        assertEquals(snap.turns.map { it.id }, reopened.turns.map { it.id })
        assertEquals(
            SessionRunStatus.COMPLETED,
            reopened.sessionRuns.single().status
        )
    }

    @Test
    fun migrate_writesCurrentSchemaOnPersist() {
        val raw = readFixture("fixtures/legacy-export-schema0.json")
        val file = tmp.newFile("legacy-persist.json")
        file.writeText(raw)
        val store = IntentionFlowStore(file)
        // Touch store so migrate + persist rewrite the file at current schema.
        store.apply(
            com.intent.launcher.domain.FlowCommand.SetActiveIntention(
                intentionId = "intention-legacy-spanish",
                nowEpochMs = 1_700_000_000_000L
            )
        )
        val onDisk = file.readText()
        assertTrue(onDisk.contains("\"schemaVersion\": $SCHEMA_VERSION") ||
            onDisk.contains("\"schemaVersion\":$SCHEMA_VERSION"))
        assertNotNull(store.snapshot().activeIntentionId)
    }

    private fun readFixture(path: String): String {
        val stream = javaClass.classLoader.getResourceAsStream(path)
            ?: error("Missing test fixture: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
