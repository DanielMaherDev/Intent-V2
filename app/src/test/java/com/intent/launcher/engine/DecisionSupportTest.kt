package com.intent.launcher.engine

import com.intent.launcher.capability.CapabilityRegistry
import com.intent.launcher.domain.FlowCommand
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.store.IntentionFlowStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DecisionSupportTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var store: IntentionFlowStore
    private val registry = CapabilityRegistry.default
    private val now = 1_700_000_000_000L
    private val intentionId = "intention-decision-generic"

    @Before
    fun setUp() {
        store = IntentionFlowStore(tmp.newFile("flow-decision-support.json"))
        store.apply(
            FlowCommand.SeedExploring(
                intentionId = intentionId,
                title = "Decide by a date",
                statement = "Help me decide under a deadline",
                shapeTag = "decision",
                nowEpochMs = now
            )
        )
        store.apply(FlowCommand.SetActiveIntention(intentionId = intentionId, nowEpochMs = now))
    }

    @Test
    fun sourceFact_offersComparisonRefresh_notAutoAccept() {
        val intention = store.snapshot().intentions.first { it.id == intentionId }
        val note = DecisionSupport.ingestSourceFact(
            store,
            intention,
            "The employer says two office days each week.",
            now + 10,
            registry
        )
        assertTrue(note.contains("comparison", ignoreCase = true))
        val snap = store.snapshot()
        assertTrue(
            snap.facts.any {
                it.intentionId == intentionId &&
                    it.key == AdaptiveBuilderEngine.KEY_DECISION_SOURCE_FACT
            }
        )
        assertTrue(
            snap.openQuestions.any {
                it.intentionId == intentionId &&
                    it.purpose == AdaptiveBuilderEngine.KEY_COMPARISON_REFRESH
            }
        )
        assertTrue(
            snap.turns.any {
                it.role == "agent" &&
                    it.text.contains("source-reported", ignoreCase = true) &&
                    it.text.contains("update the comparison", ignoreCase = true)
            }
        )
        assertFalse(
            snap.turns.any {
                it.text.contains("accepted", ignoreCase = true) &&
                    it.text.contains("offer", ignoreCase = true)
            }
        )
        assertEquals(
            IntentionPhase.EXPLORING,
            snap.intentions.first { it.id == intentionId }.phase
        )
    }

    @Test
    fun updateComparisonChip_recordsRefresh_withoutChoosing() {
        val intention = store.snapshot().intentions.first { it.id == intentionId }
        DecisionSupport.ingestSourceFact(
            store, intention, "They said hybrid two days.", now + 10, registry
        )
        DecisionSupport.onChip(
            store, intention, DecisionSupport.CHIP_UPDATE_COMPARISON, now + 20, registry
        )
        assertTrue(
            store.snapshot().facts.any {
                it.key == AdaptiveBuilderEngine.KEY_COMPARISON_REFRESH &&
                    it.normalizedValue == "updated"
            }
        )
        assertTrue(
            store.snapshot().turns.any {
                it.role == "agent" && it.text.contains("No accept/decline", ignoreCase = true)
            }
        )
    }

    @Test
    fun looksLikeSourceReportedFact_genericCues() {
        assertTrue(
            DecisionSupport.looksLikeSourceReportedFact(
                "The employer says two office days each week."
            )
        )
        assertTrue(DecisionSupport.looksLikeSourceReportedFact("Recruiter confirmed remote is hybrid"))
        assertFalse(DecisionSupport.looksLikeSourceReportedFact("Just exploring for now"))
    }
}
