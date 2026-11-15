package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.TrackerEntryStatus
import com.intent.launcher.domain.TrackerModuleDef
import com.intent.launcher.ui.components.HeroCard
import com.intent.launcher.ui.components.HeroTone
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

@Composable
fun TrackerScreen(
    intentionTitle: String,
    modules: List<TrackerModuleDef>,
    entries: List<TrackerEntry>,
    honestyNote: String,
    onRecord: (moduleId: String, valueText: String) -> Unit,
    onDelete: (entryId: String) -> Unit,
    onOpenReports: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val active = entries.filter { it.status == TrackerEntryStatus.ACTIVE }
        .sortedByDescending { it.recordedAtEpochMs }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "Tracker", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        SmallLabel(text = intentionTitle.ifBlank { "Intention" }, color = IntentColors.Section)
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = "Tracker modules",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede("Same intention store — manual rows are SAID. No fabricated streaks.")
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = honestyNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(18.dp))
        IntentSecondaryButton(text = "Open progress report", onClick = onOpenReports)

        modules.forEach { module ->
            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle(module.title)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = module.prompt,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
            Spacer(modifier = Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                module.suggestedValues.forEach { value ->
                    IntentSecondaryButton(
                        text = value,
                        onClick = { onRecord(module.id, value) }
                    )
                }
            }
            val moduleEntries = active.filter { it.moduleId == module.id }
            if (moduleEntries.isEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "No entries yet for this module.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                moduleEntries.forEach { entry ->
                    TrackerEntryRow(entry = entry, onDelete = { onDelete(entry.id) })
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun TrackerEntryRow(
    entry: TrackerEntry,
    onDelete: () -> Unit
) {
    HeroCard(tone = HeroTone.Accent) {
        SmallLabel(
            text = provenanceChip(entry.provenance.kind),
            color = IntentColors.Accent
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = entry.valueText,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Source · ${entry.provenance.sourceLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            IntentQuietButton(text = "Delete entry", onClick = onDelete)
        }
    }
}

private fun provenanceChip(kind: ProvenanceKind): String =
    when (kind) {
        ProvenanceKind.SAID -> "SAID"
        ProvenanceKind.OBSERVED -> "OBSERVED"
        ProvenanceKind.INFERRED -> "INFERRED"
    }
