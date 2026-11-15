package com.intent.launcher.engine

import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ThoughtCaptureStatus
import com.intent.launcher.domain.SpanishScenario
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
 * Floater / Home quick thought capture — SAID, no LLM summary.
 */
class CaptureThoughtTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_200_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun capture_persistsSaidProvenance_linkedToActiveIntention() {
        SpanishScenario.seed(store, now)
        val result = CaptureThought.capture(
            store = store,
            text = "Remember to ask about verb drills",
            source = CaptureThought.SOURCE_FLOATER,
            nowEpochMs = now + 1,
            thoughtId = "thought-1"
        )
        assertTrue(result.ok)
        assertNotNull(result.thought)
        assertEquals(SpanishScenario.INTENTION_ID, result.thought!!.intentionId)
        assertEquals(ProvenanceKind.SAID, result.thought!!.provenance.kind)
        assertEquals("floater_thought_capture", result.thought!!.provenance.sourceLabel)
        assertEquals(ThoughtCaptureStatus.RAW, result.thought!!.status)
        assertTrue(result.confirmation.contains("raw", ignoreCase = true))
        assertTrue(result.confirmation.contains("no summary", ignoreCase = true))
        assertTrue(store.snapshot().thoughtCaptures.any { it.id == "thought-1" })
    }

    @Test
    fun capture_blankRejected() {
        SpanishScenario.seed(store, now)
        val result = CaptureThought.capture(
            store = store,
            text = "   ",
            source = CaptureThought.SOURCE_HOME,
            nowEpochMs = now + 1
        )
        assertFalse(result.ok)
        assertTrue(store.snapshot().thoughtCaptures.isEmpty())
    }

    @Test
    fun capture_survivesReload() {
        val file = tmp.newFile("persist.json")
        val first = IntentionFlowStore(file)
        SpanishScenario.seed(first, now)
        CaptureThought.capture(
            store = first,
            text = "Buy milk tonight",
            source = CaptureThought.SOURCE_VOICE,
            nowEpochMs = now + 3,
            thoughtId = "thought-voice"
        )
        val second = IntentionFlowStore(file)
        val thought = second.snapshot().thoughtCaptures.single { it.id == "thought-voice" }
        assertEquals("Buy milk tonight", thought.text)
        assertEquals(ProvenanceKind.SAID, thought.provenance.kind)
        assertEquals("voice_thought_capture", thought.provenance.sourceLabel)
        assertEquals(5, second.snapshot().schemaVersion)
    }

    @Test
    fun floatingCompanion_capture_isNotSecondBot() {
        SpanishScenario.seed(store, now)
        val result = FloatingCompanion.onCaptureThought(
            store = store,
            text = "Quick note from bubble",
            nowEpochMs = now + 4
        )
        assertTrue(result.ok)
        assertEquals(CaptureThought.SOURCE_FLOATER, result.thought!!.source)
        assertTrue(result.confirmation.contains("no summary", ignoreCase = true))
    }
}
