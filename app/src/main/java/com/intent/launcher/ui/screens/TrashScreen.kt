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
import androidx.compose.ui.unit.dp
import com.intent.launcher.engine.HistoryTrashMenus
import com.intent.launcher.engine.TrashRow
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.theme.IntentColors

/**
 * Recoverable trash (OS-143) — opened from overflow / intention menus,
 * not a primary destination. Reuses SoftDelete / Restore / Purge.
 */
@Composable
fun TrashScreen(
    rows: List<TrashRow>,
    onRestore: (String) -> Unit,
    onPurge: (String) -> Unit,
    note: String? = null,
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
        ScreenTopBar(contextLabel = "Trash", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        Text(
            text = "Trash",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede(
            "${HistoryTrashMenus.LOCAL_ONLY_NOTE} Restore brings items back; purge is permanent."
        )
        if (!note.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Warm
            )
        }
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Recoverable items")
        Spacer(modifier = Modifier.height(12.dp))
        if (rows.isEmpty()) {
            EmptyNote(text = HistoryTrashMenus.TRASH_EMPTY)
        } else {
            rows.forEach { row ->
                TrashRowCard(
                    row = row,
                    onRestore = { onRestore(row.id) },
                    onPurge = { onPurge(row.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun TrashRowCard(
    row: TrashRow,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = row.title,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${row.kindLabel} · ${row.pathLabel} · ${row.retentionLabel}",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(10.dp))
        IntentSecondaryButton(text = "Restore", onClick = onRestore)
        Spacer(modifier = Modifier.height(4.dp))
        IntentQuietButton(text = "Purge permanently", onClick = onPurge)
    }
}
