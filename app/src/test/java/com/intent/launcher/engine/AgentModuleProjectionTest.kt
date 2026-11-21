package com.intent.launcher.engine

import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.Proposal
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.Provenance
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.TrackerEntryStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Agent-module Conversation card projections (v0.0.150) — honesty guards.
 */
class AgentModuleProjectionTest {

    @Test
    fun notedBar_onlyFromSaidFacts() {
        val said = Fact(
            id = "f1",
            intentionId = "i1",
            text = "You meet a tutor on Thursdays at 18:00",
            provenance = Provenance(ProvenanceKind.SAID, 1L, "user")
        )
        val observed = said.copy(
            id = "f2",
            provenance = Provenance(ProvenanceKind.OBSERVED, 1L, "usage")
        )
        val bar = AgentModuleProjection.notedBarFromSaidFact(said)
        assertNotNull(bar)
        assertTrue(bar!!.text.startsWith("NOTED"))
        assertNull(AgentModuleProjection.notedBarFromSaidFact(observed))
        assertNull(AgentModuleProjection.notedBarFromSaidFact(null))
    }

    @Test
    fun nextForYou_fromBeginOnly_notReviewProposal() {
        val begin = NextAction(
            kind = NextActionKind.BEGIN,
            label = "Prepare for tomorrow’s tutor session",
            detail = "Review last week’s notes",
            reason = "From your confirmed Thursday session",
            intentionId = "i1",
            beginLabel = "Begin now",
            deferLabel = "Later",
            discussLabel = "Discuss"
        )
        val card = AgentModuleProjection.nextForYouFromNextAction(begin, "Learn Spanish")
        assertNotNull(card)
        assertEquals("Prepare for tomorrow’s tutor session", card!!.title)
        assertEquals("From your confirmed Thursday session", card.provenanceChip)
        assertEquals("Learn Spanish", card.metaSecondary)
        assertFalse(card.fromBlock)

        val review = begin.copy(kind = NextActionKind.REVIEW_PROPOSAL)
        assertNull(AgentModuleProjection.nextForYouFromNextAction(review, "Learn Spanish"))
    }

    @Test
    fun proposalCard_pendingStaysNotYetActive() {
        val proposal = Proposal(
            id = "p1",
            intentionId = "i1",
            title = "Weekly tutor session",
            summary = "Thursday at 18:00",
            proposedDayOfWeek = 4,
            proposedHour = 18,
            proposedMinute = 0,
            status = ProposalStatus.PENDING,
            basedOnFactIds = listOf("f1"),
            provenance = Provenance(ProvenanceKind.INFERRED, 1L, "local")
        )
        val card = AgentModuleProjection.proposalFromPending(proposal, "Learn Spanish")
        assertNotNull(card)
        assertTrue(card!!.honestyNote.contains("NOT YET ACTIVE") ||
            card.honestyNote.contains("approve", ignoreCase = true))
        assertTrue(card.scheduleLine.contains("Thursday"))
        assertTrue(card.scheduleLine.contains("18:00"))
        assertEquals("Review & approve", card.reviewLabel)
        assertEquals("Not now", card.dismissLabel)

        val approved = proposal.copy(status = ProposalStatus.APPROVED)
        assertNull(AgentModuleProjection.proposalFromPending(approved, "Learn Spanish"))
    }

    @Test
    fun oneQuestion_choiceSetKeepsOptions_noInvention() {
        val block = ResponseBlock(
            blockId = "b1",
            type = ResponseBlockType.CHOICE_SET,
            title = "Where will you use Spanish most?",
            body = "Your answer helps me suggest the right practice.",
            sourceLabel = "On-device replies",
            actions = listOf("Work meetings", "Customer conversations", "Everyday conversations")
        )
        val card = AgentModuleProjection.oneQuestionFromBlock(block)
        assertNotNull(card)
        assertEquals(3, card!!.options.size)
        assertEquals("ONE QUESTION", card.provenanceChip)
        assertEquals("Tell me in your own words", card.freeTextHint)
    }

    @Test
    fun recordedEntry_preservesObservedLabel() {
        val entry = TrackerEntry(
            id = "t1",
            intentionId = "i1",
            moduleId = "runs",
            valueText = "20 minutes",
            recordedAtEpochMs = 10L,
            provenance = Provenance(ProvenanceKind.OBSERVED, 10L, "usage_stats"),
            status = TrackerEntryStatus.ACTIVE
        )
        val card = AgentModuleProjection.recordedFromTrackerEntry(entry)
        assertNotNull(card)
        assertTrue(card!!.subtitle.contains("OBSERVED"))
        assertFalse(card.subtitle.contains("REPORTED BY YOU"))
    }

    @Test
    fun interventionProvenanceChip_neverSilentMix() {
        assertTrue(
            AgentModuleProjection.interventionProvenanceChip("OBSERVED · usage", "Check")
                .startsWith("OBSERVED")
        )
        assertTrue(
            AgentModuleProjection.interventionProvenanceChip("INFERRED · model", "Check")
                .startsWith("INFERRED")
        )
        assertTrue(
            AgentModuleProjection.interventionProvenanceChip("something else", "Check")
                .startsWith("INFERRED")
        )
    }

    @Test
    fun reportPreview_emptyStoreStaysEmpty() {
        val card = AgentModuleProjection.reportPreviewFromStore(
            pathSummary = null,
            progressProof = ProgressProof.View(
                intentionId = "i1",
                items = emptyList(),
                saidCount = 0,
                observedCount = 0,
                inferredCount = 0,
                dayBuckets = emptyList(),
                weekBuckets = emptyList(),
                emptyState = "No progress rows yet",
                honestyNote = "Empty — nothing invented."
            )
        )
        assertNotNull(card)
        assertEquals("No progress rows yet", card!!.primaryMetric)
        assertTrue(card.footer.contains("invented") || card.footer.contains("Empty"))
        assertNull(card.trendLine)
    }

    @Test
    fun explanationCard_mapsBlock_skipsEmptyAndInactive() {
        val block = ResponseBlock(
            blockId = "b-expl",
            type = ResponseBlockType.EXPLANATION,
            title = "App-led approach",
            body = "Hold Duolingo + routine option — no lesson-completion claim",
            sourceLabel = "app_led_learning",
            actions = listOf("Open Duolingo", "Set a 15-minute practice")
        )
        val card = AgentModuleProjection.explanationFromBlock(block)
        assertNotNull(card)
        assertEquals("App-led approach", card!!.title)
        assertEquals("Hold Duolingo + routine option — no lesson-completion claim", card.body)
        assertEquals("app_led_learning", card.sourceLabel)
        assertEquals(2, card.actions.size)
        assertEquals("b-expl", card.blockId)

        assertNull(
            AgentModuleProjection.explanationFromBlock(block.copy(active = false))
        )
        assertNull(
            AgentModuleProjection.explanationFromBlock(
                block.copy(title = "", body = "")
            )
        )
        assertNull(
            AgentModuleProjection.explanationFromBlock(
                block.copy(type = ResponseBlockType.PROGRESS)
            )
        )
    }

    @Test
    fun progressCard_preservesProvenance_noInventedMetrics() {
        val observed = ResponseBlock(
            blockId = "b-prog",
            type = ResponseBlockType.PROGRESS,
            title = "Practice feedback",
            body = "Linked transcript + feedback on Learn Spanish",
            sourceLabel = "OBSERVED · usage_stats",
            actions = emptyList()
        )
        val card = AgentModuleProjection.progressFromBlock(observed)
        assertNotNull(card)
        assertEquals("Practice feedback", card!!.title)
        assertEquals("OBSERVED", card.provenanceChip)
        assertTrue(card.honestyNote.contains("invented", ignoreCase = true))
        assertTrue(card.actions.isEmpty())

        val said = AgentModuleProjection.progressFromBlock(
            observed.copy(sourceLabel = "SAID · user")
        )
        assertEquals("SAID", said!!.provenanceChip)

        assertNull(
            AgentModuleProjection.progressFromBlock(observed.copy(active = false))
        )
        assertNull(
            AgentModuleProjection.progressFromBlock(
                observed.copy(title = "  ", body = "")
            )
        )
        assertNull(
            AgentModuleProjection.progressFromBlock(
                observed.copy(type = ResponseBlockType.EXPLANATION)
            )
        )
    }

    @Test
    fun progressProvenanceChip_neverInventedMetricValue() {
        assertEquals(
            "OBSERVED",
            AgentModuleProjection.progressProvenanceChip("OBSERVED · partner")
        )
        assertEquals(
            "SAID",
            AgentModuleProjection.progressProvenanceChip("manual SAID rating")
        )
        assertEquals(
            "INFERRED",
            AgentModuleProjection.progressProvenanceChip("INFERRED · local")
        )
        assertEquals(
            "guided_practice_feedback",
            AgentModuleProjection.progressProvenanceChip("guided_practice_feedback")
        )
        assertEquals("PROGRESS", AgentModuleProjection.progressProvenanceChip(""))
    }
}
