package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.ShapeProjection
import com.intent.launcher.domain.TrashEntry
import com.intent.launcher.engine.AppConnectionUsage
import com.intent.launcher.engine.IntentionHierarchy
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
    hierarchy: IntentionHierarchy.View? = null,
    onOpenRelatedIntention: ((String) -> Unit)? = null,
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "Intention", onBack = onBack)
        if (hierarchy != null && (hierarchy.crumbs.size > 1 || hierarchy.orphanParent)) {
            Spacer(modifier = Modifier.height(14.dp))
            IntentionBreadcrumb(
                hierarchy = hierarchy,
                onOpenRelatedIntention = onOpenRelatedIntention
            )
        }
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
            Spacer(modifier = Modifier.height(15.dp))
            IntentPrimaryButton(text = projection.hero.heroCta, onClick = onWorkThisOut)
            if (onOpenTracker != null) {
                Spacer(modifier = Modifier.height(8.dp))
                IntentSecondaryButton(text = "Open tracker", onClick = onOpenTracker)
            }
            if (onOpenReports != null) {
                Spacer(modifier = Modifier.height(8.dp))
                IntentSecondaryButton(text = "Path & progress", onClick = onOpenReports)
            }
        }

        if (hierarchy != null && hierarchy.children.isNotEmpty()) {
            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle("Child intentions")
            Spacer(modifier = Modifier.height(9.dp))
            Text(
                text = hierarchy.honestyNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            Spacer(modifier = Modifier.height(12.dp))
            hierarchy.children.forEach { child ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
                        .background(IntentColors.Card, RoundedCornerShape(16.dp))
                        .clickable(enabled = onOpenRelatedIntention != null) {
                            onOpenRelatedIntention?.invoke(child.intentionId)
                        }
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .semantics {
                            contentDescription = "Open child intention ${child.title}"
                        }
                ) {
                    Text(
                        text = child.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "${child.phaseLabel} · local child",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else if (hierarchy != null && hierarchy.crumbs.size > 1) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = hierarchy.honestyNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }

        if (projection.approachSectionTitle != null && projection.approachSteps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle(projection.approachSectionTitle)
            Spacer(modifier = Modifier.height(9.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(start = 7.dp)) {
                projection.approachSteps.forEach { step ->
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

        if (projection.resourceLine != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = projection.resourceLine,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }

        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Apps you hold")
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            text = "Confirm packages for this intention (SAID). No UsageStats auto-ranking. " +
                "Not a classic app grid — the system app drawer stays available.",
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

        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Connections")
        Spacer(modifier = Modifier.height(9.dp))
        Text(
            text = "Which connections this intention uses, and what data would contribute. " +
                "Partner feeds stay empty until OAuth is supported.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (connectionUsages.isEmpty()) {
            EmptyNote(
                text = "No installed connection candidates for this intention. " +
                    "Partner feed unavailable — SAID / OBSERVED only."
            )
        } else {
            connectionUsages.forEach { usage ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
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
                    if (usage.intentions.isEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        EmptyNote(
                            text = usage.emptyNote
                                ?: "No SAID / OBSERVED contribution yet. Partner feed empty."
                        )
                    } else {
                        Spacer(modifier = Modifier.height(8.dp))
                        usage.intentions.forEach { use ->
                            Text(
                                text = use.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = IntentColors.Ink
                            )
                        }
                        usage.emptyNote?.let { note ->
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = note,
                                style = MaterialTheme.typography.bodySmall,
                                color = IntentColors.Muted
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
        if (onOpenConnections != null) {
            Spacer(modifier = Modifier.height(4.dp))
            IntentSecondaryButton(text = "All connections", onClick = onOpenConnections)
        }

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

        if (projection.emptyNote != null) {
            Spacer(modifier = Modifier.height(17.dp))
            EmptyNote(text = projection.emptyNote)
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
private fun IntentionBreadcrumb(
    hierarchy: IntentionHierarchy.View,
    onOpenRelatedIntention: ((String) -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SmallLabel(text = "Path", color = IntentColors.Muted)
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .semantics {
                    contentDescription = hierarchy.crumbs.joinToString(" / ") { it.title }
                },
            verticalAlignment = Alignment.CenterVertically
        ) {
            hierarchy.crumbs.forEachIndexed { index, crumb ->
                if (index > 0) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = null,
                        tint = IntentColors.Muted,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                }
                val clickable = !crumb.isCurrent && onOpenRelatedIntention != null
                Text(
                    text = crumb.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (crumb.isCurrent) IntentColors.Ink else IntentColors.Section,
                    fontWeight = if (crumb.isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                    modifier = Modifier
                        .then(
                            if (clickable) {
                                Modifier.clickable {
                                    onOpenRelatedIntention?.invoke(crumb.intentionId)
                                }
                            } else {
                                Modifier
                            }
                        )
                        .semantics {
                            contentDescription = if (crumb.isCurrent) {
                                "Current intention ${crumb.title}"
                            } else {
                                "Open parent intention ${crumb.title}"
                            }
                        }
                )
                Spacer(modifier = Modifier.width(2.dp))
            }
        }
        if (hierarchy.orphanParent) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hierarchy.honestyNote,
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
