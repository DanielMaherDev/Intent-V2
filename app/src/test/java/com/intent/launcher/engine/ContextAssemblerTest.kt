package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.SpanishScenario
import com.intent.launcher.store.IntentionFlowStore
import java.time.ZoneOffset
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ContextAssemblerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val now = 1_700_000_200_000L

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow-ctx.json"))
        SpanishScenario.seed(store, now)
        SpanishScenario.onChip(store, "I meet a tutor on Thursdays at six", now + 10)
    }

    @Test
    fun assemble_includesIntentionSaidFactsPendingProposalAndRecentTurns() {
        val snap = store.snapshot()
        val intention = snap.intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val pack = ContextAssembler.assemble(
            snapshot = snap,
            intention = intention,
            registry = CapabilityRegistry.default,
            zoneId = ZoneOffset.UTC,
            nowEpochMs = now + 20
        )
        assertTrue(pack.contains(SpanishScenario.TITLE))
        assertTrue(pack.contains("shapeTag=language") || pack.contains("language"))
        assertTrue(pack.contains("I meet a Spanish tutor on Thursdays at 18:00"))
        assertTrue(pack.contains(SpanishScenario.PROPOSAL_ID))
        assertTrue(pack.contains("Weekly Spanish session"))
        assertTrue(pack.contains("live_llm") || pack.contains(CapabilityRegistry.ID_LIVE_LLM))
        assertTrue(pack.contains("Suggest only") || pack.contains("do not approve", ignoreCase = true))
        assertFalse(pack.contains("sk-"))
        assertFalse(pack.contains("OPENAI_API_KEY"))
    }

    @Test
    fun assemble_doesNotInventMissingFacts() {
        val empty = IntentionFlowStore(tmp.newFile("flow-empty.json"))
        SpanishScenario.seed(empty, now)
        val intention = empty.snapshot().intentions.first { it.id == SpanishScenario.INTENTION_ID }
        val pack = ContextAssembler.assemble(
            snapshot = empty.snapshot(),
            intention = intention,
            registry = CapabilityRegistry.default,
            zoneId = ZoneOffset.UTC,
            nowEpochMs = now + 1
        )
        assertTrue(pack.contains("(none)"))
        assertFalse(pack.contains(SpanishScenario.FACT_TUTOR_ID))
    }
}
