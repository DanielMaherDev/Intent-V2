package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.Evidence
import com.intent.launcher.domain.EvidenceAccuracy
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.Hypothesis
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.theme.IntentColors

/**
 * S-KNOW — facts for the open intention only. No SAID chips / OBSERVED essay.
 */
@Composable
fun WhatWeKnowScreen(
    facts: List<Fact>,
    hypotheses: List<Hypothesis> = emptyList(),
    evidence: List<Evidence> = emptyList(),
    defaultFacts: List<String> = emptyList(),
    onBack: () -> Unit,
    onEditFact: ((factId: String) -> Unit)? = null,
    onAddFact: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val rows = buildKnowledgeRows(facts, hypotheses, evidence, defaultFacts)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "", onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "What we know",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(20.dp))

        if (rows.isEmpty()) {
            Text(
                text = "Nothing confirmed yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = IntentColors.Muted
            )
            if (onAddFact != null) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onAddFact) {
                    Text(
                        text = "Add a fact",
                        color = IntentColors.Accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        } else {
            rows.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.body,
                            style = MaterialTheme.typography.bodyLarge,
                            color = IntentColors.Ink,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = row.source,
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Muted
                        )
                    }
                    if (onEditFact != null && row.editId != null) {
                        TextButton(onClick = { onEditFact(row.editId) }) {
                            Text(
                                text = "Edit",
                                color = IntentColors.Accent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

internal data class KnowledgeRow(
    val body: String,
    val source: String,
    val editId: String? = null
)

/**
 * S-KNOW / Intention Space row builder.
 * When no stored facts / hypotheses / observed evidence exist, falls back to
 * shape `defaultFacts` so See all matches the Intention Space preview
 * (create/confirm shape defaults) instead of “Nothing confirmed yet.”
 */
internal fun buildKnowledgeRows(
    facts: List<Fact>,
    hypotheses: List<Hypothesis>,
    evidence: List<Evidence>,
    defaultFacts: List<String> = emptyList()
): List<KnowledgeRow> {
    val rows = buildList {
        facts.filter { it.status == FactStatus.ACTIVE }.forEach { f ->
            add(KnowledgeRow(body = f.text, source = "You told us", editId = f.id))
        }
        facts.filter { it.status == FactStatus.CORRECTED }.forEach { f ->
            add(KnowledgeRow(body = f.text, source = "Corrected", editId = f.id))
        }
        hypotheses.forEach { h ->
            add(
                KnowledgeRow(
                    body = h.text,
                    source = "Inferred · not confirmed",
                    editId = null
                )
            )
        }
        evidence
            .filter {
                it.accuracy == EvidenceAccuracy.ACTIVE &&
                    it.provenance.kind == ProvenanceKind.OBSERVED
            }
            .forEach { e ->
                val app = e.appLabel?.trim().orEmpty()
                    .ifBlank { e.packageName?.trim().orEmpty() }
                    .ifBlank { "app" }
                add(
                    KnowledgeRow(
                        body = e.summaryText,
                        source = "Observed · $app",
                        editId = null
                    )
                )
            }
    }
    if (rows.isNotEmpty()) return rows
    return defaultFacts.map { text ->
        KnowledgeRow(body = text, source = "You told us", editId = null)
    }
}
