package com.intent.launcher.engine

import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.SCHEMA_VERSION
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.domain.ThoughtCaptureStatus
import com.intent.launcher.domain.ThoughtDisposition
import com.intent.launcher.store.IntentionFlowStore
import java.util.TimeZone
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
 * OS-154 — evening thought triage: user-picked dispositions + check-out entry.
 */
class EveningThoughtTriageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val tz = TimeZone.getTimeZone("UTC")
    private val seedAt = 1_700_049_600_000L // Wed 15 Nov 2023 12:00 UTC
    private val thu0900 = 1_700_125_200_000L
    private val thu1830 = 1_700_159_400_000L
    private val now = 1_700_200_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    private fun seedSpanishWithRawThought(text: String, thoughtId: String = "thought-evening-1") {
        SpanishScenario.seed(store, seedAt)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", seedAt + 1)
        SpanishScenario.approve(store, seedAt + 2)
        CaptureThought.capture(
            store = store,
            text = text,
            source = CaptureThought.SOURCE_HOME,
            nowEpochMs = now,
            thoughtId = thoughtId
        )
    }

    @Test
    fun listRawForEvening_returnsUnclassifiedRawOnly() {
        seedSpanishWithRawThought("Buy milk tonight")
        CaptureThought.capture(
            store = store,
            text = "Learn Portuguese",
            source = CaptureThought.SOURCE_FLOATER,
            nowEpochMs = now + 1,
            thoughtId = "thought-evening-2"
        )
        EveningThoughtTriage.classify(
            store, "thought-evening-1", ThoughtDisposition.KEEP, now + 2, tz
        )
        val raw = EveningThoughtTriage.listRawForEvening(store)
        assertEquals(1, raw.size)
        assertEquals("thought-evening-2", raw.first().id)
    }

    @Test
    fun classify_task_createsSaidFactAndSessionStub() {
        seedSpanishWithRawThought("Buy milk tonight")
        val result = EveningThoughtTriage.classify(
            store = store,
            thoughtId = "thought-evening-1",
            disposition = ThoughtDisposition.TASK,
            nowEpochMs = now + 10,
            timeZone = tz
        )
        assertTrue(result.ok)
        assertEquals(ThoughtDisposition.TASK, result.disposition)
        val thought = store.snapshot().thoughtCaptures.single { it.id == "thought-evening-1" }
        assertEquals(ThoughtCaptureStatus.PROMOTED, thought.status)
        assertEquals(ThoughtDisposition.TASK, thought.disposition)
        val fact = store.snapshot().facts.first {
            it.provenance.sourceLabel == "evening_thought_task"
        }
        assertEquals(ProvenanceKind.SAID, fact.provenance.kind)
        assertTrue(fact.text.contains("Buy milk tonight"))
        assertTrue(store.snapshot().sessions.any { it.id == "session-task-thought-evening-1" })
        assertTrue(result.confirmation.contains("Task", ignoreCase = true))
    }

    @Test
    fun classify_intention_seedsExploringSaid() {
        seedSpanishWithRawThought("Learn Portuguese for travel")
        val result = EveningThoughtTriage.classify(
            store, "thought-evening-1", ThoughtDisposition.INTENTION, now + 20, tz
        )
        assertTrue(result.ok)
        val thought = store.snapshot().thoughtCaptures.single { it.id == "thought-evening-1" }
        assertEquals(ThoughtCaptureStatus.PROMOTED, thought.status)
        assertEquals(ThoughtDisposition.INTENTION, thought.disposition)
        val intention = store.snapshot().intentions.first {
            it.id == "intention-evening-thought-evening-1"
        }
        assertEquals("Learn Portuguese for travel", intention.statement)
        val seed = store.snapshot().facts.first {
            it.provenance.sourceLabel == "evening_thought_intention"
        }
        assertEquals(ProvenanceKind.SAID, seed.provenance.kind)
    }

    @Test
    fun classify_reminder_writesSaidNote_noInventedAlarm() {
        seedSpanishWithRawThought("Call the landlord tomorrow")
        val result = EveningThoughtTriage.classify(
            store, "thought-evening-1", ThoughtDisposition.REMINDER, now + 30, tz
        )
        assertTrue(result.ok)
        val fact = store.snapshot().facts.first {
            it.provenance.sourceLabel == "evening_thought_reminder"
        }
        assertTrue(fact.text.startsWith("Reminder:"))
        assertEquals(ProvenanceKind.SAID, fact.provenance.kind)
        assertTrue(result.confirmation.contains("no alarm", ignoreCase = true))
    }

    @Test
    fun classify_keep_leavesRawEvidence() {
        seedSpanishWithRawThought("Maybe useful later")
        val result = EveningThoughtTriage.classify(
            store, "thought-evening-1", ThoughtDisposition.KEEP, now + 40, tz
        )
        assertTrue(result.ok)
        val thought = store.snapshot().thoughtCaptures.single { it.id == "thought-evening-1" }
        assertEquals(ThoughtCaptureStatus.RAW, thought.status)
        assertEquals(ThoughtDisposition.KEEP, thought.disposition)
        assertTrue(EveningThoughtTriage.listRawForEvening(store).isEmpty())
    }

    @Test
    fun classify_discard_marksDiscarded() {
        seedSpanishWithRawThought("Noise")
        val result = EveningThoughtTriage.classify(
            store, "thought-evening-1", ThoughtDisposition.DISCARD, now + 50, tz
        )
        assertTrue(result.ok)
        val thought = store.snapshot().thoughtCaptures.single { it.id == "thought-evening-1" }
        assertEquals(ThoughtCaptureStatus.DISCARDED, thought.status)
        assertEquals(ThoughtDisposition.DISCARD, thought.disposition)
    }

    @Test
    fun classify_rejectsAlreadyClassified_andMissing() {
        seedSpanishWithRawThought("Once")
        assertTrue(
            EveningThoughtTriage.classify(
                store, "thought-evening-1", ThoughtDisposition.KEEP, now + 60, tz
            ).ok
        )
        assertFalse(
            EveningThoughtTriage.classify(
                store, "thought-evening-1", ThoughtDisposition.TASK, now + 61, tz
            ).ok
        )
        assertFalse(
            EveningThoughtTriage.classify(
                store, "missing", ThoughtDisposition.DISCARD, now + 62, tz
            ).ok
        )
    }

    @Test
    fun checkOut_entry_surfacesAfterCheckIn_andSurvivesClassifyReload() {
        seedSpanishWithRawThought("Evening note")
        val localDate = DailyCycle.localDateString(thu0900, tz)
        store.apply(
            FlowCommand.ConfirmDailyCheckIn(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = localDate,
                focusSummary = "Focus: Speak Spanish",
                nowEpochMs = thu0900
            )
        )
        val evening = NextActionEngine.recommend(store.snapshot(), thu1830, tz)
        assertEquals(NextActionKind.CHECK_OUT, evening.kind)
        assertNotNull(EveningThoughtTriage.nextRaw(store))

        EveningThoughtTriage.classify(
            store, "thought-evening-1", ThoughtDisposition.TASK, thu1830, tz
        )
        store.apply(
            FlowCommand.CompleteDailyCheckOut(
                intentionId = SpanishScenario.INTENTION_ID,
                localDate = DailyCycle.localDateString(thu1830, tz),
                whatHappened = "Practiced speaking",
                factId = "fact-checkout-test",
                nowEpochMs = thu1830
            )
        )
        assertTrue(store.snapshot().dayCycle.checkedOutFor(DailyCycle.localDateString(thu1830, tz)))
        assertNull(EveningThoughtTriage.nextRaw(store))

        val reloaded = IntentionFlowStore(java.io.File(tmp.root, "flow.json"))
        val thought = reloaded.snapshot().thoughtCaptures.single { it.id == "thought-evening-1" }
        assertEquals(ThoughtDisposition.TASK, thought.disposition)
        assertEquals(SCHEMA_VERSION, reloaded.snapshot().schemaVersion)
        assertEquals(7, SCHEMA_VERSION)
    }

    @Test
    fun dispositions_areUserPickedOnly_noAutoInvent() {
        // Engine never suggests a disposition — only applies explicit user choice.
        seedSpanishWithRawThought("Ambiguous thought")
        val raw = EveningThoughtTriage.nextRaw(store)
        assertNotNull(raw)
        assertNull(raw!!.disposition)
        assertEquals(ThoughtCaptureStatus.RAW, raw.status)
    }
}
