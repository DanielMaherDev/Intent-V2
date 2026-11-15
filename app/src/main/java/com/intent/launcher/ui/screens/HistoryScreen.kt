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
import com.intent.launcher.engine.HistoryRow
import com.intent.launcher.engine.HistoryTrashMenus
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.theme.IntentColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local transaction history (OS-143) — opened from overflow / intention menus,
 * not a primary Home tab. Reuses the existing undo model.
 */
@Composable
fun HistoryScreen(
    rows: List<HistoryRow>,
    onUndoLast: (() -> Unit)?,
    undoNote: String? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeFmt = SimpleDateFormat("EEE · HH:mm", Locale.getDefault())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "History", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        Text(
            text = "History",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede(HistoryTrashMenus.LOCAL_ONLY_NOTE)
        if (!undoNote.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = undoNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Warm
            )
        }
        if (onUndoLast != null) {
            Spacer(modifier = Modifier.height(16.dp))
            IntentPrimaryButton(text = "Undo last reversible", onClick = onUndoLast)
        }
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Local commitments")
        Spacer(modifier = Modifier.height(12.dp))
        if (rows.isEmpty()) {
            EmptyNote(text = HistoryTrashMenus.HISTORY_EMPTY)
        } else {
            rows.forEach { row ->
                HistoryRowCard(row = row, timeLabel = timeFmt.format(Date(row.occurredAtEpochMs)))
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun HistoryRowCard(row: HistoryRow, timeLabel: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = row.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$timeLabel · ${row.statusLabel}" +
                if (row.canUndoAsLast) " · next undo" else "",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    }
}
