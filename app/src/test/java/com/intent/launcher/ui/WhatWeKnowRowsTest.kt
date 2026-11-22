package com.intent.launcher.ui

import com.intent.launcher.domain.Evidence
import com.intent.launcher.domain.EvidenceAccuracy
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.Hypothesis
import com.intent.launcher.domain.Provenance
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.ui.screens.buildKnowledgeRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatWeKnowRowsTest {

    @Test
    fun activeThenCorrected_sourceLabelsMatchSpec() {
        val rows = buildKnowledgeRows(
            facts = listOf(
                Fact(
                    id = "f1",
                    intentionId = "i1",
                    text = "six months",
                    provenance = Provenance(ProvenanceKind.SAID, 1L, "user"),
                    status = FactStatus.ACTIVE
                ),
                Fact(
                    id = "f0",
                    intentionId = "i1",
                    text = "in about three months",
                    provenance = Provenance(ProvenanceKind.SAID, 1L, "user"),
                    status = FactStatus.CORRECTED
                )
            ),
            hypotheses = listOf(
                Hypothesis(
                    id = "h1",
                    intentionId = "i1",
                    text = "Might need a tutor",
                    provenance = Provenance(ProvenanceKind.INFERRED, 1L, "engine")
                )
            ),
            evidence = emptyList()
        )
        assertEquals("You told us", rows[0].source)
        assertEquals("six months", rows[0].body)
        assertEquals("Corrected", rows[1].source)
        assertTrue(rows[2].source.contains("not confirmed"))
    }

    @Test
    fun observedEvidence_usesAppLabel() {
        val rows = buildKnowledgeRows(
            facts = emptyList(),
            hypotheses = emptyList(),
            evidence = listOf(
                Evidence(
                    id = "e1",
                    intentionId = "i1",
                    sessionRunId = null,
                    packageName = "com.duolingo",
                    appLabel = "Duolingo",
                    foregroundMs = 60_000L,
                    recordedAtEpochMs = 1L,
                    summaryText = "Opened Duolingo for 1 min",
                    provenance = Provenance(ProvenanceKind.OBSERVED, 1L, "usage"),
                    accuracy = EvidenceAccuracy.ACTIVE
                )
            )
        )
        assertEquals(1, rows.size)
        assertEquals("Observed · Duolingo", rows[0].source)
    }

    @Test
    fun emptyStore_fallsBackToDefaultFacts_notEmptyState() {
        val defaults = listOf(
            "Goal · finish a marathon",
            "Timeframe · build base first"
        )
        val rows = buildKnowledgeRows(
            facts = emptyList(),
            hypotheses = emptyList(),
            evidence = emptyList(),
            defaultFacts = defaults
        )
        assertEquals(2, rows.size)
        assertEquals("Goal · finish a marathon", rows[0].body)
        assertEquals("You told us", rows[0].source)
        assertEquals(null, rows[0].editId)
        assertEquals("Timeframe · build base first", rows[1].body)
    }

    @Test
    fun storedFacts_preferOverDefaultFacts() {
        val rows = buildKnowledgeRows(
            facts = listOf(
                Fact(
                    id = "f1",
                    intentionId = "i1",
                    text = "six months",
                    provenance = Provenance(ProvenanceKind.SAID, 1L, "user"),
                    status = FactStatus.ACTIVE
                )
            ),
            hypotheses = emptyList(),
            evidence = emptyList(),
            defaultFacts = listOf("Goal · finish a marathon")
        )
        assertEquals(1, rows.size)
        assertEquals("six months", rows[0].body)
        assertEquals("f1", rows[0].editId)
    }

    @Test
    fun emptyStoreAndNoDefaults_returnsEmpty() {
        val rows = buildKnowledgeRows(
            facts = emptyList(),
            hypotheses = emptyList(),
            evidence = emptyList(),
            defaultFacts = emptyList()
        )
        assertTrue(rows.isEmpty())
    }
}
