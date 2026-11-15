package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.Evidence
import com.intent.launcher.domain.EvidenceAccuracy
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.HeldApp
import com.intent.launcher.domain.Hypothesis
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.ShapeProjection
import com.intent.launcher.domain.TrashEntry
import com.intent.launcher.engine.AppConnectionUsage
import com.intent.launcher.engine.IntentionDetailCoherence
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.FactRow
import com.intent.launcher.ui.components.HeroCard
import com.intent.launcher.ui.components.HeroTone
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

/**
 * Intention detail (OS-142): coherent next action → paths → plan → track → reports → resources.
 * Exploring first viewport stays question-first (four-core Intention exploring). Honesty only —
 * no invented partner data. Drawer / breadcrumb owned by sibling claims.
 */
@Composable
fun IntentionSpaceScreen(
    intention: Intention?,
    facts: List<Fact>,
    hypotheses: List<Hypothesis>,
    sessions: List<ScheduledSession>,
    evidence: List<Evidence> = emptyList(),
    heldApps: List<HeldApp> = emptyList(),
    holdSuggestions: List<DestinationApp> = emptyList(),
    onConfirmHold: (packageName: String, label: String) -> Unit = { _, _ -> },
    onMoveHeldApp: (heldAppId: String, direction: Int) -> Unit = { _, _ -> },
    onReleaseHeldApp: (heldAppId: String) -> Unit = {},
    connectionUsages: List<AppConnectionUsage.ConnectionUsage> = emptyList(),
    onOpenConnections: (() -> Unit)? = null,
    coherence: IntentionDetailCoherence.View? = null,
    nextAction: NextAction? = null,
    onWorkThisOut: () -> Unit,
    onBack: () -> Unit,
    onMoveIntentionToTrash: (() -> Unit)? = null,
    onMoveSessionToTrash: ((String) -> Unit)? = null,
    trashItems: List<TrashEntry> = emptyList(),
    onRestoreTrash: ((String) -> Unit)? = null,
    onPurgeTrash: ((String) -> Unit)? = null,
    undoTrashNote: String? = null,
    onOpenTracker: (() -> Unit)? = null,
    onOpenReports: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val projection = ShapeProjection.project(
        intention = intention,
        facts = facts,
        hypotheses = hypotheses,
        sessions = sessions,
        destination = SessionDestinations.forIntention(intention)
    )
    val heroTone = when (projection.hero.tone) {
        "warm" -> HeroTone.Warm
        "success" -> HeroTone.Success
        else -> HeroTone.Accent
    }
    val labelColor = when (heroTone) {
        HeroTone.Warm -> IntentColors.Warm
        HeroTone.Success -> IntentColors.Success
        HeroTone.Accent -> IntentColors.Section
    }
    val exploringFirstViewport =
        intention?.phase == IntentionPhase.EXPLORING ||
            intention?.phase == IntentionPhase.CLARIFYING ||
            intention == null

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "Intention", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        SmallLabel(text = projection.hero.phaseLabel, color = labelColor)
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = intention?.title ?: "Intention",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede(intention?.statement ?: "")
        Spacer(modifier = Modifier.height(26.dp))

        // 1 — Next action (hero CTA + Home's NextAction reason when present)
        SectionTitle("Next action")
        Spacer(modifier = Modifier.height(9.dp))
        HeroCard(tone = heroTone) {
            SmallLabel(text = projection.hero.heroLabel, color = labelColor)
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = projection.hero.heroTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = projection.hero.heroBody,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
            if (nextAction != null && nextAction.reason.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Recommended now · ${nextAction.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = nextAction.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
            Spacer(modifier = Modifier.height(15.dp))
            IntentPrimaryButton(text = projection.hero.heroCta, onClick = onWorkThisOut)
        }

        // Exploring mock order: knowledge + empty note before deeper sections.
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle(projection.knowledgeSectionTitle)
        Spacer(modifier = Modifier.height(9.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, IntentColors.Line, RoundedCornerShape(20.dp))
                .background(IntentColors.Card, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            if (facts.isEmpty() && hypotheses.isEmpty() && sessions.isEmpty() && evidence.isEmpty()) {
                projection.defaultFacts.forEach { FactRow(text = it) }
            } else {
                facts.forEach { f ->
                    FactRow(text = "Confirmed by you · ${f.text}")
                }
                hypotheses.forEach { h ->
                    FactRow(text = "Inferred · ${h.text}")
                }
                evidence
                    .filter {
                        it.accuracy == EvidenceAccuracy.ACTIVE &&
                            it.provenance.kind == ProvenanceKind.OBSERVED
                    }
                    .forEach { e ->
                        FactRow(text = "Observed · ${e.summaryText}")
                    }
                sessions.forEach { s ->
                    val whenLabel = "dow ${s.dayOfWeek} %02d:%02d".format(s.hour, s.minute)
                    FactRow(text = "${s.title} · $whenLabel")
                    if (onMoveSessionToTrash != null) {
                        IntentQuietButton(
                            text = "Move session to trash",
                            onClick = { onMoveSessionToTrash(s.id) }
                        )
                    }
                }
                if (intention?.phase == IntentionPhase.PROPOSAL_PENDING) {
                    FactRow(text = "Pending · add a weekly commitment", pending = true)
                }
            }
        }

        if (projection.emptyNote != null && exploringFirstViewport) {
            Spacer(modifier = Modifier.height(17.dp))
            EmptyNote(text = projection.emptyNote)
        }

        // 2 — Active paths
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle(coherence?.activePathsTitle ?: "Active paths")
        Spacer(modifier = Modifier.height(9.dp))
        if (coherence == null || coherence.activePathLines.isEmpty()) {
            EmptyNote(
                text = coherence?.activePathsEmptyNote
                    ?: "No child paths yet. This intention is the path — parent/child navigation is separate."
            )
        } else {
            coherence.activePathLines.forEach { line ->
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = line.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = line.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
            }
        }

        // 3 — Plan
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle(coherence?.planTitle ?: projection.approachSectionTitle ?: "Plan")
        Spacer(modifier = Modifier.height(9.dp))
        val planSteps = coherence?.planSteps ?: projection.approachSteps
        if (planSteps.isEmpty()) {
            EmptyNote(
                text = coherence?.planEmptyNote
                    ?: projection.emptyNote
                    ?: "No plan committed yet. Exploring stays useful without invented tasks."
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 7.dp)) {
                planSteps.forEach { step ->
                    Column(modifier = Modifier.padding(start = 17.dp, bottom = 14.dp)) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (step.muted) IntentColors.Muted else IntentColors.Ink
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Muted
                        )
                    }
                }
            }
        }

        // 4 — Track
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle(coherence?.trackTitle ?: "Track")
        Spacer(modifier = Modifier.height(9.dp))
        if (coherence != null && coherence.trackLines.isNotEmpty()) {
            coherence.trackLines.forEach { line ->
                Text(
                    text = "${line.title} · ${line.activeEntryCount} entries",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(6.dp))
            }
        } else {
            EmptyNote(
                text = coherence?.trackEmptyNote
                    ?: "No tracker modules for this shape yet."
            )
        }
        Text(
            text = coherence?.trackHonestyNote
                ?: "Manual SAID entries only — not partner lesson completion.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        if (onOpenTracker != null && (coherence?.canOpenTracker != false)) {
            Spacer(modifier = Modifier.height(10.dp))
            IntentSecondaryButton(text = "Open tracker", onClick = onOpenTracker)
        }

        // 5 — Reports
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle(coherence?.reportsTitle ?: "Reports")
        Spacer(modifier = Modifier.height(9.dp))
        if (coherence != null) {
            Text(
                text = coherence.reportsSummaryLine,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = coherence.reportsProofLine,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = coherence.reportsHonestyNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        } else {
            EmptyNote(text = "Path & progress reads the same intention store when available.")
        }
        if (onOpenReports != null && (coherence?.canOpenReports != false)) {
            Spacer(modifier = Modifier.height(10.dp))
            IntentSecondaryButton(text = "Path & progress", onClick = onOpenReports)
        }

        // 6 — Resources (destination + held apps + connections)
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle(coherence?.resourcesTitle ?: "Resources")
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            text = coherence?.resourcesHonestyNote
                ?: "Opening an app is not lesson completion. Partner OAuth feeds are not supported.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (coherence != null && coherence.resourceLines.isNotEmpty()) {
            coherence.resourceLines.forEach { line ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
                        .background(IntentColors.Card, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = line.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = line.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
            }
        } else if (coherence?.resourcesEmptyNote != null) {
            EmptyNote(text = coherence.resourcesEmptyNote)
        } else if (connectionUsages.isNotEmpty()) {
            connectionUsages.forEach { usage ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
                        .background(IntentColors.Card, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = usage.connection.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (usage.connection.installed) {
                            "Installed · ${usage.connection.accountStatus}"
                        } else {
                            "Not installed · ${usage.connection.accountStatus}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
            }
        }

        // Held-app confirm / reorder stays interactive under Resources.
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Apps you hold",
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Confirm packages for this intention (SAID). No UsageStats auto-ranking.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (heldApps.isEmpty() && holdSuggestions.isEmpty()) {
            EmptyNote(
                text = "No held apps yet. When a shape suggests a destination, confirm it here before treating it as affinity."
            )
        } else {
            heldApps.forEachIndexed { index, held ->
                HeldAppRow(
                    label = held.label,
                    packageName = held.packageName,
                    canMoveUp = index > 0,
                    canMoveDown = index < heldApps.lastIndex,
                    onMoveUp = { onMoveHeldApp(held.id, -1) },
                    onMoveDown = { onMoveHeldApp(held.id, 1) },
                    onRelease = { onReleaseHeldApp(held.id) }
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            holdSuggestions.forEach { suggestion ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
                        .background(IntentColors.Card, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = suggestion.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "Suggested · confirm to hold (SAID) · ${suggestion.packageName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    IntentSecondaryButton(
                        text = "Hold ${suggestion.label}",
                        onClick = { onConfirmHold(suggestion.packageName, suggestion.label) }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }

        if (onOpenConnections != null) {
            Spacer(modifier = Modifier.height(8.dp))
            IntentSecondaryButton(text = "All connections", onClick = onOpenConnections)
        }

        if (onMoveIntentionToTrash != null && intention != null) {
            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle("Control")
            Spacer(modifier = Modifier.height(9.dp))
            IntentSecondaryButton(
                text = "Move intention to trash",
                onClick = onMoveIntentionToTrash
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Soft-delete keeps child facts, proposals, and sessions recoverable until you purge.",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }

        if (trashItems.isNotEmpty()) {
            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle("Trash")
            Spacer(modifier = Modifier.height(9.dp))
            trashItems.forEach { item ->
                Column(modifier = Modifier.padding(bottom = 12.dp)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${item.kind.name.lowercase()} · ${item.pathLabel}" +
                            if (item.expiresAtEpochMs == null) " · until purge" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (onRestoreTrash != null) {
                        IntentSecondaryButton(
                            text = "Restore",
                            onClick = { onRestoreTrash(item.id) }
                        )
                    }
                    if (onPurgeTrash != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        IntentQuietButton(
                            text = "Purge permanently",
                            onClick = { onPurgeTrash(item.id) }
                        )
                    }
                }
            }
        }

        if (undoTrashNote != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = undoTrashNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Warm
            )
        }
    }
}

@Composable
private fun HeldAppRow(
    label: String,
    packageName: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRelease: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = "Held · SAID · $packageName",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canMoveUp) {
                IntentQuietButton(text = "Move up", onClick = onMoveUp)
            }
            if (canMoveDown) {
                IntentQuietButton(text = "Move down", onClick = onMoveDown)
            }
            IntentQuietButton(text = "Release", onClick = onRelease)
        }
    }
}
