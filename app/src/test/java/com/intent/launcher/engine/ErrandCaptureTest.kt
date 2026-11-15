package com.intent.launcher.engine

import com.intent.launcher.domain.ErrandScenario
import com.intent.launcher.domain.ErrandStatus
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ErrandCaptureTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("errands.json"))
    }

    @Test
    fun parse_buyMilkTonight_splitsFirstActionAndReminder() {
        val parsed = ErrandCapture.parse(ErrandScenario.RAW_TEXT)
        assertEquals(ErrandCapture.Kind.QUICK_TODO, parsed.kind)
        assertEquals(ErrandScenario.RAW_TEXT, parsed.title)
        assertEquals(ErrandScenario.EXPECTED_FIRST_ACTION, parsed.firstAction)
        assertEquals(ErrandScenario.EXPECTED_REMINDER, parsed.reminderNote)
    }

    @Test
    fun classify_buyMilk_isQuickTodo_learnSpanish_isIntentionCandidate() {
        assertEquals(
            ErrandCapture.Kind.QUICK_TODO,
            ErrandCapture.classify("Buy milk tonight")
        )
        assertEquals(
            ErrandCapture.Kind.QUICK_TODO,
            ErrandCapture.classify("Pick up dry cleaning tomorrow")
        )
        assertEquals(
            ErrandCapture.Kind.INTENTION_CANDIDATE,
            ErrandCapture.classify("Learn Spanish for work")
        )
        assertEquals(
            ErrandCapture.Kind.INTENTION_CANDIDATE,
            ErrandCapture.classify("Train for a marathon in eight months")
        )
    }

    @Test
    fun case01_buyMilkTonight_capturesErrandWithoutSeedingIntention() {
        val before = store.snapshot()
        assertTrue(before.intentions.isEmpty())
        assertTrue(before.errandTodos.isEmpty())

        val result = ErrandScenario.captureBuyMilkTonight(store, now)
        assertTrue(result.ok)
        assertTrue(result.intentionCountUnchanged)
        assertNotNull(result.todo)

        val snap = store.snapshot()
        assertTrue(
            "CASE-01 must not create an Intention",
            snap.intentions.isEmpty()
        )
        assertNull(snap.activeIntentionId)
        assertEquals(1, snap.errandTodos.size)

        val todo = snap.errandTodos.first()
        assertEquals(ErrandScenario.TODO_ID, todo.id)
        assertEquals(ErrandScenario.RAW_TEXT, todo.title)
        assertEquals(ErrandScenario.EXPECTED_FIRST_ACTION, todo.firstAction)
        assertEquals(ErrandScenario.EXPECTED_REMINDER, todo.reminderNote)
        assertEquals(ErrandStatus.OPEN, todo.status)
        assertEquals(ProvenanceKind.SAID, todo.provenance.kind)
        assertTrue(result.confirmation.contains("quick to-do", ignoreCase = true))
        assertTrue(result.confirmation.contains("Not added to exploring", ignoreCase = true))
    }

    @Test
    fun case01_alongsideSpanish_doesNotForceExploringOrTouchIntention() {
        SpanishScenario.seed(store, now)
        val intentionCount = store.snapshot().intentions.size
        val activeId = store.snapshot().activeIntentionId
        val phase = store.snapshot().intentions.first().phase

        val result = ErrandScenario.captureBuyMilkTonight(store, now + 10)
        assertTrue(result.ok)
        assertTrue(result.intentionCountUnchanged)

        val snap = store.snapshot()
        assertEquals(intentionCount, snap.intentions.size)
        assertEquals(activeId, snap.activeIntentionId)
        assertEquals(phase, snap.intentions.first().phase)
        assertEquals(1, snap.errandTodos.size)
        assertFalse(snap.errandTodos.any { it.id.contains("intention") })
    }

    @Test
    fun completeAndReopen_persistAcrossRestart() {
        ErrandScenario.captureBuyMilkTonight(store, now)
        assertTrue(ErrandScenario.complete(store, now + 20))
        var snap = store.snapshot()
        assertEquals(ErrandStatus.DONE, snap.errandTodos.first().status)
        assertEquals(now + 20, snap.errandTodos.first().completedAtEpochMs)

        assertTrue(ErrandCapture.reopen(store, ErrandScenario.TODO_ID, now + 30))
        snap = store.snapshot()
        assertEquals(ErrandStatus.OPEN, snap.errandTodos.first().status)
        assertNull(snap.errandTodos.first().completedAtEpochMs)

        val restarted = IntentionFlowStore(File(tmp.root, "errands.json"))
        val again = restarted.snapshot()
        assertEquals(1, again.errandTodos.size)
        assertEquals(ErrandScenario.RAW_TEXT, again.errandTodos.first().title)
        assertEquals(ErrandStatus.OPEN, again.errandTodos.first().status)
        assertEquals(7, again.schemaVersion)
    }

    @Test
    fun emptyText_rejected() {
        val result = ErrandCapture.capture(store, "   ", now)
        assertFalse(result.ok)
        assertTrue(store.snapshot().errandTodos.isEmpty())
    }
}
