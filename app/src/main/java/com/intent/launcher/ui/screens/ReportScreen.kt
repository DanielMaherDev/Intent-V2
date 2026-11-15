package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.launcher.engine.ParentRollup
import com.intent.launcher.engine.PathSummary
import com.intent.launcher.engine.ProgressProof
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.HeroCard
import com.intent.launcher.ui.components.HeroTone
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

@Composable
fun ReportScreen(
    pathSummary: PathSummary.View?,
    progressProof: ProgressProof.View?,
    parentRollup: ParentRollup.View? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "Reports", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        Text(
            text = "Path & progress",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede("Reads the same intention store. Every proof row keeps its provenance label.")

        if (parentRollup != null) {
            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle("Child contributions")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${parentRollup.parentTitle} · ${parentRollup.childCount} children · " +
                    "SAID ${parentRollup.saidCount} · OBSERVED ${parentRollup.observedCount} · " +
                    "INFERRED ${parentRollup.inferredCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Ink,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = parentRollup.honestyNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            if (parentRollup.contributions.isEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                EmptyNote(
                    text = if (parentRollup.childCount == 0) {
                        "No local children — empty rollup, not invented."
                    } else {
                        "Children present, but no ACTIVE tracker rows to roll up yet."
                    }
                )
            } else {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, IntentColors.Line, RoundedCornerShape(20.dp))
                        .background(IntentColors.Card, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    parentRollup.contributions.forEach { row ->
                        Text(
                            text = "${row.provenanceLabel} · ${row.childTitle} · ${row.valueText}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IntentColors.Ink
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${row.moduleId} · ${row.sourceLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Muted
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }
        }

        if (pathSummary != null) {
            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle("Path summary")
            Spacer(modifier = Modifier.height(10.dp))
            HeroCard(tone = HeroTone.Accent) {
                SmallLabel(text = pathSummary.phaseLabel, color = IntentColors.Accent)
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = pathSummary.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = pathSummary.statusLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Next · ${pathSummary.nextActionLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink,
                    fontWeight = FontWeight.Medium
                )
                if (pathSummary.nextActionReason.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = pathSummary.nextActionReason,
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Sessions · ${pathSummary.scheduledSessionCount} scheduled · " +
                        "${pathSummary.completedRunCount} completed · " +
                        "${pathSummary.abandonedRunCount} abandoned",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
                if (pathSummary.modules.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    pathSummary.modules.forEach { mod ->
                        Text(
                            text = "${mod.title} · ${mod.activeEntryCount} entries",
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Ink
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pathSummary.honestyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
        }

        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Progress proof")
        Spacer(modifier = Modifier.height(8.dp))
        if (progressProof == null) {
            EmptyNote(text = "No active intention to report on.")
        } else {
            Text(
                text = "SAID ${progressProof.saidCount} · OBSERVED ${progressProof.observedCount} · " +
                    "INFERRED ${progressProof.inferredCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Ink,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = progressProof.honestyNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            if (progressProof.emptyState != null) {
                Spacer(modifier = Modifier.height(12.dp))
                EmptyNote(text = progressProof.emptyState)
            }

            progressProof.dayBuckets.forEach { bucket ->
                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle(bucket.label)
                Spacer(modifier = Modifier.height(8.dp))
                if (bucket.items.isEmpty()) {
                    Text(
                        text = "Nothing recorded in this window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                } else {
                    ProofList(bucket.items)
                }
            }

            progressProof.weekBuckets.forEach { bucket ->
                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle(bucket.label)
                Spacer(modifier = Modifier.height(8.dp))
                if (bucket.items.isEmpty()) {
                    Text(
                        text = "Nothing recorded in this window.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                } else {
                    ProofList(bucket.items)
                }
            }

            if (progressProof.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle("All labeled rows")
                Spacer(modifier = Modifier.height(8.dp))
                ProofList(progressProof.items)
            }
        }
    }
}

@Composable
private fun ProofList(items: List<ProgressProof.LabeledItem>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(20.dp))
            .background(IntentColors.Card, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        items.forEach { item ->
            Text(
                text = "${item.provenanceLabel} · ${item.text}",
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = item.sourceDetail,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}
