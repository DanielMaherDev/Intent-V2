package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.ConversationTurn

@Composable
fun ConversationScreen(
    turns: List<ConversationTurn>,
    chips: List<String>,
    onChip: (String) -> Unit,
    onBack: () -> Unit,
    onOpenProposal: () -> Unit,
    hasPendingProposal: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(
            text = "Conversation",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            turns.forEach { turn ->
                val who = if (turn.role == "user") "You" else "INTENT"
                Text(
                    text = "$who: ${turn.text}",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text("Choose a direction", style = MaterialTheme.typography.titleSmall)
        Spacer(modifier = Modifier.height(8.dp))
        chips.forEach { chip ->
            AssistChip(
                onClick = { onChip(chip) },
                label = { Text(chip) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp)
            )
        }
        if (hasPendingProposal) {
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onOpenProposal) {
                Text("Open proposal review")
            }
        }
    }
}
