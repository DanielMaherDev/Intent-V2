package com.intent.launcher.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.DrawerCardLayout
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.HomeBottomNav
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.OverflowMenuItem
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.theme.IntentColors

/**
 * S-DRAWER — title list with search + reorder. Cards are surface + status only
 * (no cover images / priority chrome in this version).
 */
@Composable
fun IntentionDrawerScreen(
    intentions: List<Intention>,
    layouts: Map<String, DrawerCardLayout>,
    activeIntentionId: String?,
    onOpenIntention: (String) -> Unit,
    onMoveIntention: (intentionId: String, direction: Int) -> Unit,
    onUpdateCardLayout: (intentionId: String, spanColumns: Int, priorityBorder: Boolean) -> Unit,
    onBackHome: () -> Unit,
    onAgent: () -> Unit,
    /** OS-143 — History / Trash via drawer overflow, not a primary tab. */
    overflowItems: List<OverflowMenuItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var reorderMode by remember { mutableStateOf(false) }
    // layouts / onUpdateCardLayout kept for API stability; S-DRAWER target is one column.
    @Suppress("UNUSED_PARAMETER")
    val unusedLayouts = layouts
    @Suppress("UNUSED_PARAMETER")
    val unusedUpdate = onUpdateCardLayout

    val filtered = remember(intentions, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            intentions
        } else {
            intentions.filter { it.title.contains(q, ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 15.dp)
        ) {
            ScreenTopBar(
                contextLabel = "",
                onBack = onBackHome,
                overflowItems = overflowItems
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Intentions",
                style = MaterialTheme.typography.headlineLarge,
                color = IntentColors.Ink,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Search intentions" },
                placeholder = {
                    Text(
                        text = "Search",
                        color = IntentColors.ComposerHint
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IntentColors.Accent,
                    unfocusedBorderColor = IntentColors.Line,
                    unfocusedContainerColor = IntentColors.Card,
                    focusedContainerColor = IntentColors.Card
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { reorderMode = !reorderMode }) {
                    Text(
                        text = if (reorderMode) "Done" else "Reorder",
                        color = IntentColors.Accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))

            if (intentions.isEmpty()) {
                EmptyNote(
                    text = "No intentions yet. Start exploring from Home — the drawer lists what you create."
                )
            } else if (filtered.isEmpty()) {
                EmptyNote(text = "No titles match “${searchQuery.trim()}”.")
            } else {
                filtered.forEachIndexed { index, intention ->
                    val fullIndex = intentions.indexOfFirst { it.id == intention.id }
                    IntentionDrawerCard(
                        intention = intention,
                        selected = intention.id == activeIntentionId,
                        reorderMode = reorderMode,
                        canMoveUp = fullIndex > 0,
                        canMoveDown = fullIndex >= 0 && fullIndex < intentions.lastIndex,
                        onOpen = { onOpenIntention(intention.id) },
                        onMoveUp = { onMoveIntention(intention.id, -1) },
                        onMoveDown = { onMoveIntention(intention.id, 1) }
                    )
                    if (index < filtered.lastIndex) {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }

        HomeBottomNav(
            selected = "intentions",
            onHome = onBackHome,
            onIntentions = { },
            onAgent = onAgent
        )
    }
}

/** DESIGN_SPEC S-DRAWER status vocabulary. */
internal fun drawerStatusLabel(phase: IntentionPhase): String = when (phase) {
    IntentionPhase.EXPLORING -> "Exploring"
    IntentionPhase.CLARIFYING -> "Planning"
    IntentionPhase.PROPOSAL_PENDING -> "Deciding"
    IntentionPhase.ACTIVE -> "Active"
    IntentionPhase.PAUSED -> "Paused"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntentionDrawerCard(
    intention: Intention,
    selected: Boolean,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val status = drawerStatusLabel(intention.phase)
    val shape = RoundedCornerShape(16.dp)
    val borderColor = if (selected) IntentColors.Accent else IntentColors.Line

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .border(1.dp, borderColor, shape)
            .clip(shape)
            .background(IntentColors.Card, shape)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { /* reorder is the Reorder control */ }
            )
            .padding(16.dp)
            .semantics {
                contentDescription = "${intention.title}, $status. Tap to open."
            }
    ) {
        Text(
            text = intention.title,
            style = MaterialTheme.typography.bodyLarge,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (reorderMode) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (canMoveUp) {
                    IntentQuietButton(text = "Up", onClick = onMoveUp)
                }
                if (canMoveDown) {
                    IntentQuietButton(text = "Down", onClick = onMoveDown)
                }
            }
        }
    }
}
