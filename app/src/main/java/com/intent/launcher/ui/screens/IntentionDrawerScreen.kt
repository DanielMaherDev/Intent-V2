package com.intent.launcher.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.DrawerCardLayout
import com.intent.launcher.domain.DrawerCards
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.HomeBottomNav
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

/**
 * Intention drawer — accessible card grid (OS-139), not a classic app grid / mini-app shelf.
 * Tap opens Intention Space; long-press enables reorder + width / priority chrome.
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
    modifier: Modifier = Modifier
) {
    var reorderId by remember { mutableStateOf<String?>(null) }
    val resolved = remember(intentions, layouts, activeIntentionId) {
        intentions.map { intention ->
            intention to (
                layouts[intention.id]
                    ?: DrawerCards.defaultLayout(intention, activeIntentionId)
                )
        }
    }
    val rows = remember(resolved) { packDrawerRows(resolved) }

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
            ScreenTopBar(contextLabel = "Drawer", onBack = onBackHome)
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
                "Tap to open Intention Space. Long-press a card to reorder, set one- or two-column " +
                    "width, or toggle a priority border. Covers are local shape chrome — not mini-apps."
            )
            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle("Active intentions")
            Spacer(modifier = Modifier.height(9.dp))

            if (intentions.isEmpty()) {
                EmptyNote(
                    text = "No intentions yet. Start exploring from Home — the drawer lists what you create."
                )
            } else {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.forEach { (intention, layout) ->
                            val index = intentions.indexOfFirst { it.id == intention.id }
                            Box(
                                modifier = Modifier.weight(
                                    if (DrawerCards.normalizeSpan(layout.spanColumns) >= 2) 2f else 1f
                                )
                            ) {
                                IntentionDrawerCard(
                                    intention = intention,
                                    layout = layout,
                                    selected = intention.id == activeIntentionId,
                                    reorderMode = reorderId == intention.id,
                                    canMoveUp = index > 0,
                                    canMoveDown = index >= 0 && index < intentions.lastIndex,
                                    onOpen = { onOpenIntention(intention.id) },
                                    onLongPressReorder = {
                                        reorderId =
                                            if (reorderId == intention.id) null else intention.id
                                    },
                                    onMoveUp = { onMoveIntention(intention.id, -1) },
                                    onMoveDown = { onMoveIntention(intention.id, 1) },
                                    onToggleWidth = {
                                        val nextSpan = if (layout.spanColumns >= 2) 1 else 2
                                        onUpdateCardLayout(
                                            intention.id,
                                            nextSpan,
                                            layout.priorityBorder
                                        )
                                    },
                                    onTogglePriority = {
                                        onUpdateCardLayout(
                                            intention.id,
                                            layout.spanColumns,
                                            !layout.priorityBorder
                                        )
                                    },
                                    onDoneReorder = { reorderId = null }
                                )
                            }
                        }
                        if (row.size == 1 &&
                            DrawerCards.normalizeSpan(row.first().second.spanColumns) < 2
                        ) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            if (reorderId != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Card edit — Move up/down, Wider/Narrower, Priority border, then Done. " +
                        "Undo reverses this when that capability is LIVE.",
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

private fun packDrawerRows(
    items: List<Pair<Intention, DrawerCardLayout>>
): List<List<Pair<Intention, DrawerCardLayout>>> {
    val rows = mutableListOf<MutableList<Pair<Intention, DrawerCardLayout>>>()
    var pendingHalf: Pair<Intention, DrawerCardLayout>? = null
    for (item in items) {
        val span = DrawerCards.normalizeSpan(item.second.spanColumns)
        if (span >= 2) {
            val leftover = pendingHalf
            if (leftover != null) {
                rows.add(mutableListOf(leftover))
                pendingHalf = null
            }
            rows.add(mutableListOf(item))
        } else {
            val first = pendingHalf
            if (first == null) {
                pendingHalf = item
            } else {
                rows.add(mutableListOf(first, item))
                pendingHalf = null
            }
        }
    }
    val leftover = pendingHalf
    if (leftover != null) {
        rows.add(mutableListOf(leftover))
    }
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun IntentionDrawerCard(
    intention: Intention,
    layout: DrawerCardLayout,
    selected: Boolean,
    reorderMode: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onOpen: () -> Unit,
    onLongPressReorder: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggleWidth: () -> Unit,
    onTogglePriority: () -> Unit,
    onDoneReorder: () -> Unit
) {
    val phaseLabel = when (intention.phase) {
        IntentionPhase.EXPLORING -> "Exploring"
        IntentionPhase.CLARIFYING -> "Clarifying"
        IntentionPhase.PROPOSAL_PENDING -> "Proposal pending"
        IntentionPhase.ACTIVE -> "Active"
        IntentionPhase.PAUSED -> "Paused"
    }
    val spanLabel = if (layout.spanColumns >= 2) "full width" else "half width"
    val borderColor = when {
        layout.priorityBorder -> IntentColors.Warm
        selected || reorderMode -> IntentColors.Accent
        else -> IntentColors.Line
    }
    val borderWidth = if (layout.priorityBorder) 2.5.dp else 1.dp
    val shape = RoundedCornerShape(16.dp)
    val coverBrush = drawerCoverBrush(
        layout.imageKey.ifBlank { DrawerCards.coverKeyForShape(intention.shapeTag) }
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, shape)
            .clip(shape)
            .background(IntentColors.Card, shape)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = onLongPressReorder
            )
            .semantics {
                val priorityBit = if (layout.priorityBorder) ", priority border" else ""
                contentDescription =
                    "${intention.title}, $phaseLabel, $spanLabel$priorityBit. " +
                        "Tap to open. Long-press to edit card."
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (layout.spanColumns >= 2) Modifier.aspectRatio(2.2f)
                    else Modifier.aspectRatio(1.15f)
                )
                .background(coverBrush)
        ) {
            // Readable title overlay — dark scrim keeps ink contrast on cover chrome.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color(0xCC1A1F1C),
                                Color(0xE61A1F1C)
                            )
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 11.dp)
            ) {
                Column {
                    Text(
                        text = intention.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = "$phaseLabel · ${intention.shapeTag}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD7DDD7),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Text(
                    text = "Open",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                )
            }
            if (layout.priorityBorder) {
                Text(
                    text = "Priority",
                    style = MaterialTheme.typography.labelSmall,
                    color = IntentColors.WarmSoft,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                )
            }
        }

        if (reorderMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (canMoveUp) {
                        IntentQuietButton(text = "Up", onClick = onMoveUp)
                    }
                    if (canMoveDown) {
                        IntentQuietButton(text = "Down", onClick = onMoveDown)
                    }
                    IntentQuietButton(
                        text = if (layout.spanColumns >= 2) "Narrower" else "Wider",
                        onClick = onToggleWidth
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IntentQuietButton(
                        text = if (layout.priorityBorder) "Clear priority" else "Priority",
                        onClick = onTogglePriority
                    )
                    IntentQuietButton(text = "Done", onClick = onDoneReorder)
                }
            }
        }
    }
}

private fun drawerCoverBrush(imageKey: String): Brush =
    when (imageKey.lowercase()) {
        "language" -> Brush.linearGradient(
            colors = listOf(Color(0xFF3F4699), Color(0xFF7A82D4), Color(0xFFC9CBEF))
        )
        "marathon" -> Brush.linearGradient(
            colors = listOf(Color(0xFF2D786A), Color(0xFF5FA894), Color(0xFFD5EBE3))
        )
        "business" -> Brush.linearGradient(
            colors = listOf(Color(0xFF5A4632), Color(0xFFA76531), Color(0xFFF0DCC4))
        )
        "move" -> Brush.linearGradient(
            colors = listOf(Color(0xFF3A4A58), Color(0xFF6B7F90), Color(0xFFD5DDE4))
        )
        else -> Brush.linearGradient(
            colors = listOf(Color(0xFF4146AE), Color(0xFF5459C9), Color(0xFFE9E9FA))
        )
    }
