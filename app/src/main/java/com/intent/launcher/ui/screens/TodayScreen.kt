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
import com.intent.launcher.engine.TodayView
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

/**
 * Dedicated Today destination — planned · completed · deferred from the
 * IntentionFlowStore day cycle / occurrences / sessions.
 */
@Composable
fun TodayScreen(
    model: TodayView.Model,
    focusNote: String? = null,
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
        ScreenTopBar(contextLabel = "Today", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        Text(
            text = "Today",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede(
            if (!focusNote.isNullOrBlank()) {
                focusNote
            } else {
                "What is planned, completed, and deferred for ${model.localDate}."
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        SmallLabel(text = model.honestyNote)
        if (!model.focusSummary.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = model.focusSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Ink,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(26.dp))
        TodaySection(
            title = "Planned",
            items = model.planned,
            empty = "Nothing planned for this date from sessions or day cycle."
        )
        Spacer(modifier = Modifier.height(22.dp))
        TodaySection(
            title = "Completed",
            items = model.completed,
            empty = "No completed runs or day-loop occurrences yet today."
        )
        Spacer(modifier = Modifier.height(22.dp))
        TodaySection(
            title = "Deferred",
            items = model.deferred,
            empty = "No deferred sessions right now."
        )
    }
}

@Composable
private fun TodaySection(
    title: String,
    items: List<TodayView.Item>,
    empty: String
) {
    SectionTitle(title)
    Spacer(modifier = Modifier.height(12.dp))
    if (items.isEmpty()) {
        EmptyNote(text = empty)
    } else {
        items.forEach { item ->
            TodayItemCard(item)
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun TodayItemCard(item: TodayView.Item) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        SmallLabel(text = item.statusLabel)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Medium
        )
        if (item.detail.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
    }
}
