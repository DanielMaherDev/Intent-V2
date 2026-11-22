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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.Approach
import com.intent.launcher.domain.DestinationApp
import com.intent.launcher.domain.Evidence
import com.intent.launcher.domain.EvidenceAccuracy
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.FactStatus
import com.intent.launcher.domain.HeldApp
import com.intent.launcher.domain.Hypothesis
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.OpenQuestion
import com.intent.launcher.domain.ProvenanceKind
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.ShapeProjection
import com.intent.launcher.domain.TrashEntry
import com.intent.launcher.engine.AppConnectionUsage
import com.intent.launcher.engine.IntentionHierarchy
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.HeroCard
import com.intent.launcher.ui.components.HeroTone
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.OverflowMenuItem
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.theme.IntentColors

/**
 * S-INTENTION — status overline, Summary / Next / What we know / Apps.
 * Discuss + Move to trash live in the overflow sheet (not permanent rows).
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
    hierarchy: IntentionHierarchy.View? = null,
    onOpenRelatedIntention: ((String) -> Unit)? = null,
    onWorkThisOut: () -> Unit,
    onOpenWhatWeKnow: (() -> Unit)? = null,
    onBack: () -> Unit,
    onMoveIntentionToTrash: (() -> Unit)? = null,
    onMoveSessionToTrash: ((String) -> Unit)? = null,
    trashItems: List<TrashEntry> = emptyList(),
    onRestoreTrash: ((String) -> Unit)? = null,
    onPurgeTrash: ((String) -> Unit)? = null,
    undoTrashNote: String? = null,
    onOpenTracker: (() -> Unit)? = null,
    onOpenReports: (() -> Unit)? = null,
    approaches: List<Approach> = emptyList(),
    openQuestions: List<OpenQuestion> = emptyList(),
    modifier: Modifier = Modifier
) {
    val projection = ShapeProjection.project(
        intention = intention,
        facts = facts,
        hypotheses = hypotheses,
        sessions = sessions,
        destination = SessionDestinations.forIntention(intention),
        approaches = approaches,
        openQuestions = openQuestions
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
    val statusWord = intention?.phase?.let { drawerStatusLabel(it) } ?: "Exploring"
    var confirmTrash by remember { mutableStateOf(false) }
    var showAppsManage by remember { mutableStateOf(false) }

    val overflow = buildList {
        add(OverflowMenuItem("Discuss") { onWorkThisOut() })
        if (onMoveIntentionToTrash != null && intention != null) {
            add(OverflowMenuItem("Move to trash") { confirmTrash = true })
        }
    }

    if (confirmTrash && onMoveIntentionToTrash != null) {
        AlertDialog(
            onDismissRequest = { confirmTrash = false },
            title = { Text("Move to trash?") },
            text = {
                Text(
                    "Soft-delete keeps facts, proposals, and sessions recoverable until you purge."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmTrash = false
                        onMoveIntentionToTrash()
                    }
                ) { Text("Move to trash", color = IntentColors.Ink) }
            },
            dismissButton = {
                TextButton(onClick = { confirmTrash = false }) {
                    Text("Cancel", color = IntentColors.Accent)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(
            contextLabel = "",
            onBack = onBack,
            overflowItems = overflow
        )
        if (hierarchy != null && (hierarchy.crumbs.size > 1 || hierarchy.orphanParent)) {
            Spacer(modifier = Modifier.height(14.dp))
            IntentionBreadcrumb(
                hierarchy = hierarchy,
                onOpenRelatedIntention = onOpenRelatedIntention
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = statusWord,
            style = MaterialTheme.typography.labelMedium,
            color = IntentColors.Muted,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = intention?.title ?: "Intention",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Summary")
        Spacer(modifier = Modifier.height(9.dp))
        val summary = intention?.statement?.trim().orEmpty()
        Text(
            text = if (summary.isNotEmpty()) {
                summary
            } else {
                "We’re still working out what this is."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = IntentColors.Ink
        )

        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Next")
        Spacer(modifier = Modifier.height(9.dp))
        HeroCard(tone = heroTone) {
            Text(
                text = projection.hero.heroTitle.ifBlank { "No step yet" },
                style = MaterialTheme.typography.titleMedium,
                color = IntentColors.Ink,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = projection.hero.heroBody.ifBlank {
                    "We haven’t worked out a next step yet."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
            Spacer(modifier = Modifier.height(15.dp))
            IntentPrimaryButton(
                text = projection.hero.heroCta.ifBlank { "Work it out" },
                onClick = onWorkThisOut
            )
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
                ) {
                    Text(
                        text = child.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = child.phaseLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
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

        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("What we know")
        Spacer(modifier = Modifier.height(9.dp))
        val activeFacts = facts.filter { it.status == FactStatus.ACTIVE }
        val correctedFacts = facts.filter { it.status == FactStatus.CORRECTED }
        val knowledgeRows = buildList {
            activeFacts.forEach { add("You told us" to it.text) }
            correctedFacts.forEach { add("Corrected" to it.text) }
            hypotheses.forEach { add("Inferred · not confirmed" to it.text) }
            evidence
                .filter {
                    it.accuracy == EvidenceAccuracy.ACTIVE &&
                        it.provenance.kind == ProvenanceKind.OBSERVED
                }
                .forEach { add("Observed" to it.summaryText) }
            sessions.forEach { s ->
                val whenLabel = "dow ${s.dayOfWeek} %02d:%02d".format(s.hour, s.minute)
                add("Scheduled" to "${s.title} · $whenLabel")
            }
        }
        if (knowledgeRows.isEmpty() && projection.defaultFacts.isEmpty()) {
            Text(
                text = "Nothing confirmed yet.",
                style = MaterialTheme.typography.bodyLarge,
                color = IntentColors.Muted
            )
        } else {
            val rows = if (knowledgeRows.isEmpty()) {
                projection.defaultFacts.map { "You told us" to it }
            } else {
                knowledgeRows
            }
            rows.take(3).forEach { (source, body) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    Text(
                        text = body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = IntentColors.Ink,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = source,
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
            }
            if (rows.size > 3 || onOpenWhatWeKnow != null) {
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(
                    onClick = {
                        if (onOpenWhatWeKnow != null) {
                            onOpenWhatWeKnow()
                        }
                    }
                ) {
                    Text(
                        text = "See all",
                        color = IntentColors.Accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Apps")
        Spacer(modifier = Modifier.height(9.dp))
        val appsLine = when {
            heldApps.isNotEmpty() ->
                heldApps.joinToString(" · ") { "${it.label} · time open only" }
            projection.resourceLine != null ->
                projection.resourceLine!!.replace("Destination · ", "")
                    .replace(" — opening an app is not lesson completion.", " · time open only")
            else -> "None connected."
        }
        Text(
            text = appsLine,
            style = MaterialTheme.typography.bodyLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { showAppsManage = !showAppsManage }) {
            Text(
                text = if (showAppsManage) "Hide manage" else "Manage",
                color = IntentColors.Accent,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (showAppsManage) {
            Spacer(modifier = Modifier.height(12.dp))
            if (heldApps.isEmpty() && holdSuggestions.isEmpty()) {
                EmptyNote(
                    text = "No apps held for this intention yet. Confirm a suggestion below if one fits."
                )
            }
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
                        text = "Suggested · time open only · ${suggestion.packageName}",
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
            if (onOpenConnections != null) {
                Spacer(modifier = Modifier.height(4.dp))
                IntentSecondaryButton(text = "All connections", onClick = onOpenConnections)
            }
            if (connectionUsages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                connectionUsages.forEach { usage ->
                    Text(
                        text = "${usage.connection.label} · " +
                            if (usage.connection.installed) "Installed" else "Not installed",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
            }
        }

        if (projection.emptyNote != null) {
            Spacer(modifier = Modifier.height(17.dp))
            EmptyNote(text = projection.emptyNote)
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
        // Silence unused when projection label unused in hero tone path.
        @Suppress("UNUSED_EXPRESSION")
        labelColor
    }
}

@Composable
private fun IntentionBreadcrumb(
    hierarchy: IntentionHierarchy.View,
    onOpenRelatedIntention: ((String) -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Path",
            style = MaterialTheme.typography.labelMedium,
            color = IntentColors.Muted
        )
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
            text = "Held · time open only · $packageName",
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
