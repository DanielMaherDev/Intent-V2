package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.util.TimeZone

/**
 * 0.0.146 create-intention UX: cold start stays blank; builder commit persists;
 * language chips target the active user intention — never hardcode intention-spanish.
 */
class CreateIntentionUxFixesTest {

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
    fun coldStart_emptyInstall_hasNoSpanishSeed() {
        // IntentApp no longer calls ensureParentChildSeeded / ensureSeeded.
        val snap = store.snapshot()
        assertTrue(snap.intentions.isEmpty())
        assertNull(snap.activeIntentionId)
        assertFalse(snap.intentions.any { it.id == SpanishScenario.INTENTION_ID })

        val next = NextActionEngine.recommend(snap, now, TimeZone.getTimeZone("UTC"), registry)
        assertEquals(NextActionKind.NONE, next.kind)
    }

    @Test
    fun builderCommit_persistsActiveIntention_withoutSpanishFixture() {
        assertTrue(IntentionBuilder.start(store, "Speak Spanish more at work", now, registry).ok)
        assertTrue(IntentionBuilder.setShape(store, "language", now + 1, registry))
        val p1 = IntentionBuilder.nextPrompt(store.snapshot().builderDraft)!!
        assertTrue(IntentionBuilder.answerPrompt(store, p1.id, "Everyday conversations", now + 2, registry))
        val p2 = IntentionBuilder.nextPrompt(store.snapshot().builderDraft)!!
        assertTrue(IntentionBuilder.answerPrompt(store, p2.id, "I have a tutor", now + 3, registry))
        assertEquals(BuilderPhase.CONFIRM, store.snapshot().builderDraft.phase)

        assertTrue(
            IntentionBuilder.commit(
                store,
                now + 4,
                intentionId = "intention-user-spanish",
                registry = registry
            )
        )
        val snap = store.snapshot()
        assertEquals("intention-user-spanish", snap.activeIntentionId)
        assertTrue(snap.intentions.any { it.id == "intention-user-spanish" })
        assertFalse(snap.intentions.any { it.id == SpanishScenario.INTENTION_ID })
        assertEquals("language", snap.intentions.first { it.id == "intention-user-spanish" }.shapeTag)
    }

    @Test
    fun languageChip_mutatesActiveUserIntention_notSpanishFixture() {
        assertTrue(IntentionBuilder.start(store, "Speak Spanish more at work", now, registry).ok)
        assertTrue(IntentionBuilder.setShape(store, "language", now + 1, registry))
        val p1 = IntentionBuilder.nextPrompt(store.snapshot().builderDraft)!!
        assertTrue(IntentionBuilder.answerPrompt(store, p1.id, "Everyday conversations", now + 2, registry))
        val p2 = IntentionBuilder.nextPrompt(store.snapshot().builderDraft)!!
        assertTrue(IntentionBuilder.answerPrompt(store, p2.id, "I have a tutor", now + 3, registry))
        assertTrue(
            IntentionBuilder.commit(
                store,
                now + 4,
                intentionId = "intention-user-spanish",
                registry = registry
            )
        )
        val intention = store.snapshot().intentions.first { it.id == "intention-user-spanish" }

        ConversationCoordinator.submitChip(
            store = store,
            intention = intention,
            chip = "I meet a tutor on Thursdays at six",
            nowEpochMs = now + 10,
            registry = registry
        )

        val snap = store.snapshot()
        assertFalse(snap.intentions.any { it.id == SpanishScenario.INTENTION_ID })
        assertTrue(snap.facts.any { it.intentionId == "intention-user-spanish" })
        assertTrue(snap.proposals.any { it.intentionId == "intention-user-spanish" })
        assertFalse(snap.facts.any { it.intentionId == SpanishScenario.INTENTION_ID })
        assertFalse(snap.proposals.any { it.intentionId == SpanishScenario.INTENTION_ID })
    }

    @Test
    fun otherShape_hasEmptyChipCatalog_noSpanishScaffold() {
        val other = Intention(
            id = "intention-other-1",
            title = "Something else",
            statement = "A custom goal",
            phase = com.intent.launcher.domain.IntentionPhase.EXPLORING,
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            shapeTag = "other"
        )
        assertTrue(
            ConversationCoordinator.chipsForIntention(other, registry).isEmpty()
        )
        assertTrue(
            ConversationCoordinator.chipsForIntention(null, registry).isEmpty()
        )
    }
}
