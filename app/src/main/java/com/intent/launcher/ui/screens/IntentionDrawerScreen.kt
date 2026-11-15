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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.HomeBottomNav
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.OverflowMenuItem
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

/**
 * Intention drawer — accessible list of intentions (not a classic app grid).
 * Tap opens Intention Space; long-press enables reorder (persisted via FlowCommand).
 */
@Composable
fun IntentionDrawerScreen(
    intentions: List<Intention>,
    activeIntentionId: String?,
    onOpenIntention: (String) -> Unit,
    onMoveIntention: (intentionId: String, direction: Int) -> Unit,
    onBackHome: () -> Unit,
    onAgent: () -> Unit,
    /** OS-143 — History / Trash via drawer overflow, not a primary tab. */
    overflowItems: List<OverflowMenuItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    var reorderId by remember { mutableStateOf<String?>(null) }

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
                .padding(horizontal = 19.dp, vertical = 15.dp)
        ) {
            ScreenTopBar(
                contextLabel = "Drawer",
                onBack = onBackHome,
                overflowItems = overflowItems
            )
            Spacer(modifier = Modifier.height(23.dp))
            SmallLabel(text = "Your intentions", color = IntentColors.Accent)
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = "Intention drawer",
                style = MaterialTheme.typography.headlineLarge,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(8.dp))
            Lede(
                "Tap to open Intention Space. Long-press to reorder — order is saved on this device. " +
                    "This is not the system app drawer."
            )
            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle("Active intentions")
            Spacer(modifier = Modifier.height(9.dp))

            if (intentions.isEmpty()) {
                EmptyNote(
                    text = "No intentions yet. Start exploring from Home — the drawer lists what you create."
                )
            } else {
                intentions.forEachIndexed { index, intention ->
                    IntentionDrawerRow(
                        intention = intention,
                        selected = intention.id == activeIntentionId,
                        reorderMode = reorderId == intention.id,
                        canMoveUp = index > 0,
                        canMoveDown = index < intentions.lastIndex,
                        onOpen = { onOpenIntention(intention.id) },
                        onLongPressReorder = {
                            reorderId = if (reorderId == intention.id) null else intention.id
                        },
                        onMoveUp = {
                            onMoveIntention(intention.id, -1)
                        },
                        onMoveDown = {
                            onMoveIntention(intention.id, 1)
                        },
                        onDoneReorder = { reorderId = null }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            if (reorderId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Reorder mode — use Move up / Move down, then Done. Undo will reverse this when that capability is LIVE.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntentionDrawerRow(
    intention: Intention,
    selected: Boolean,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onLongPressReorder: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDoneReorder: () -> Unit
) {
    val phaseLabel = when (intention.phase) {
        IntentionPhase.EXPLORING -> "Exploring"
        IntentionPhase.CLARIFYING -> "Clarifying"
        IntentionPhase.PROPOSAL_PENDING -> "Proposal pending"
        IntentionPhase.ACTIVE -> "Active"
        IntentionPhase.PAUSED -> "Paused"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (selected || reorderMode) IntentColors.Accent else IntentColors.Line,
                RoundedCornerShape(16.dp)
            )
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPressReorder
            )
            .semantics {
                contentDescription =
                    "${intention.title}, $phaseLabel. Tap to open. Long-press to reorder."
            }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = intention.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "$phaseLabel · ${intention.shapeTag}",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
            if (selected) {
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelMedium,
                    color = IntentColors.Accent
                )
            }
        }
        if (reorderMode) {
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
                IntentQuietButton(text = "Done", onClick = onDoneReorder)
            }
        }
    }
}
