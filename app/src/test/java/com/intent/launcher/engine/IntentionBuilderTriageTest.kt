package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.capability.CapabilityStatus
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ThoughtCaptureStatus
import com.intent.launcher.domain.TriageItemStatus
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IntentionBuilderTriageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val registry = CapabilityRegistry.default
    private val now = 1_700_000_000_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow.json"))
    }

    @Test
    fun builder_freeTextToShapeToSaidFacts_onConfirm() {
        val start = IntentionBuilder.start(store, "I want to speak Spanish at work", now, registry)
        assertTrue(start.ok)
        assertTrue(start.note.contains("language", ignoreCase = true))
        assertFalse(start.note.contains("Heuristic", ignoreCase = true))
        assertEquals("language", store.snapshot().builderDraft.suggestedShapeTag)
        // Agent auto-classifies — no forced PICK_SHAPE step.
        assertEquals(BuilderPhase.MISSING_FACTS, store.snapshot().builderDraft.phase)
        assertEquals("language", store.snapshot().builderDraft.shapeTag)

        val prompt1 = IntentionBuilder.nextPrompt(store.snapshot().builderDraft)!!
        assertEquals("practice_minutes", prompt1.id)
        assertTrue(prompt1.quantityChoices != null)
        assertTrue(
            IntentionBuilder.answerPrompt(
                store,
                prompt1.id,
                "20 minutes per day",
                now + 2,
                registry
            )
        )
        val prompt2 = IntentionBuilder.nextPrompt(store.snapshot().builderDraft)!!
        assertTrue(IntentionBuilder.answerPrompt(store, prompt2.id, "I have a tutor", now + 3, registry))
        assertEquals(BuilderPhase.CONFIRM, store.snapshot().builderDraft.phase)

        assertTrue(IntentionBuilder.commit(store, now + 4, intentionId = "intention-built-1", registry))
        val snap = store.snapshot()
        assertFalse(snap.builderDraft.active)
        val intention = snap.intentions.first { it.id == "intention-built-1" }
        assertEquals("language", intention.shapeTag)
        assertEquals("intention-built-1", snap.activeIntentionId)
        val facts = snap.facts.filter { it.intentionId == "intention-built-1" }
        assertTrue(facts.size >= 2)
        assertTrue(facts.all { it.provenance.kind == ProvenanceKind.SAID })
        assertTrue(facts.none { it.provenance.kind == ProvenanceKind.INFERRED })
    }

    @Test
    fun builder_registryLock_refusesStart() {
        val locked = CapabilityRegistry(
            CapabilityRegistry.DEFAULT_ENTRIES.map {
                if (it.id == CapabilityRegistry.ID_BUILD_INTENTION) {
                    it.copy(status = CapabilityStatus.NOT_SUPPORTED)
                } else it
            }
        )
        val start = IntentionBuilder.start(store, "run a marathon", now, locked)
        assertFalse(start.ok)
        assertFalse(store.snapshot().builderDraft.active)
    }

    @Test
    fun triage_confirmCreatesIntention_rejectKeepsRawEvidence() {
        val dump = "Learn Spanish\nTrain for a marathon\nExplore a business idea"
        val result = ThoughtTriage.startFromDump(store, dump, now, registry)
        assertTrue(result.ok)
        assertEquals(3, result.items.size)
        assertTrue(result.note.contains("On-device", ignoreCase = true) || result.note.contains("device", ignoreCase = true) || result.note.isNotBlank())
        assertEquals(3, store.snapshot().thoughtCaptures.count { it.status == ThoughtCaptureStatus.RAW })

        val first = store.snapshot().triage.items.first()
        assertTrue(
            ThoughtTriage.confirm(
                store,
                first.id,
                now + 10,
                intentionId = "intention-from-triage",
                registry = registry
            )
        )
        assertTrue(store.snapshot().intentions.any { it.id == "intention-from-triage" })
        assertEquals(
            ThoughtCaptureStatus.PROMOTED,
            store.snapshot().thoughtCaptures.first { it.id in first.sourceThoughtIds }.status
        )
        assertEquals(
            ProvenanceKind.SAID,
            store.snapshot().facts.first { it.intentionId == "intention-from-triage" }.provenance.kind
        )

        val second = store.snapshot().triage.items.first { it.status == TriageItemStatus.PENDING }
        assertTrue(ThoughtTriage.reject(store, second.id, now + 20, registry))
        assertEquals(TriageItemStatus.REJECTED, store.snapshot().triage.items.first { it.id == second.id }.status)
        assertTrue(
            store.snapshot().thoughtCaptures
                .filter { it.id in second.sourceThoughtIds }
                .all { it.status == ThoughtCaptureStatus.RAW }
        )
    }

    @Test
    fun triage_splitHeuristics_andPersistence() {
        assertEquals(
            listOf("a", "b"),
            ThoughtTriage.splitDump("a\nb")
        )
        assertEquals(
            listOf("learn spanish", "run a marathon"),
            ThoughtTriage.splitDump("learn spanish and run a marathon")
        )
        ThoughtTriage.startFromDump(store, "Spanish lessons\nMarathon base", now, registry)
        val file = tmp.newFile("restart.json")
        file.writeText(store.encode(store.snapshot()))
        val reloaded = IntentionFlowStore(file)
        assertEquals(com.intent.launcher.domain.SCHEMA_VERSION, reloaded.snapshot().schemaVersion)
        assertEquals(
            com.intent.launcher.domain.SCHEMA_VERSION,
            reloaded.snapshot().schemaVersion
        )
        assertTrue(reloaded.snapshot().triage.active)
        assertEquals(2, reloaded.snapshot().triage.items.size)
        assertEquals(2, reloaded.snapshot().thoughtCaptures.size)
    }

    @Test
    fun suggestShape_labelsKnownShapes() {
        assertEquals("language", IntentionBuilder.suggestShape("practice Spanish with Duolingo"))
        assertEquals("marathon", IntentionBuilder.suggestShape("build base for a marathon"))
        assertEquals("business", IntentionBuilder.suggestShape("talk to customers for my startup"))
        assertEquals(
            "app_use",
            IntentionBuilder.suggestShape(
                "Keep Instagram and YouTube Shorts under 45 minutes a day."
            )
        )
        assertEquals(
            "move",
            IntentionBuilder.suggestShape(
                "I need to move from Barcelona to Valencia within six weeks. I rent and haven't found a new place."
            )
        )
        assertEquals(
            "move",
            IntentionBuilder.suggestShape("Relocate next month — lease ends, still looking for a flat")
        )
        assertEquals("other", IntentionBuilder.suggestShape("get better sleep"))
        assertEquals(
            "decision",
            IntentionBuilder.suggestShape(
                "Help me decide whether to accept a job offer. I have ten days."
            )
        )
        assertEquals(
            "decision",
            IntentionBuilder.suggestShape("Help me decide between two options by Friday")
        )
    }
}
