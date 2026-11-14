package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.domain.ConversationTurn
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

@Composable
fun ConversationScreen(
    turns: List<ConversationTurn>,
    chips: List<String>,
    intentionTitle: String,
    onChip: (String) -> Unit,
    onBack: () -> Unit,
    onOpenProposal: () -> Unit,
    hasPendingProposal: Boolean,
    modifier: Modifier = Modifier
) {
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
                .padding(horizontal = 19.dp, vertical = 18.dp)
        ) {
            ScreenTopBar(
                contextLabel = intentionTitle.ifBlank { "Conversation" },
                onBack = onBack
            )
            Spacer(modifier = Modifier.height(23.dp))
            SmallLabel(text = "A conversation about your goal")
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = "Let's find a way that fits.",
                style = MaterialTheme.typography.headlineMedium,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(17.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                turns.forEach { turn ->
                    if (turn.role == "user") {
                        UserBubble(turn.text)
                    } else {
                        AgentBubble(turn.text)
                    }
                }
            }

            if (chips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    chips.forEach { chip ->
                        ChoiceRow(text = chip, onClick = { onChip(chip) })
                    }
                }
            }

            if (hasPendingProposal) {
                Spacer(modifier = Modifier.height(12.dp))
                IntentQuietButton(
                    text = "Open proposal review",
                    onClick = onOpenProposal
                )
            }
        }

        ComposerBar()
    }
}

@Composable
private fun UserBubble(text: String) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Text(
            text = text,
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(17.dp, 17.dp, 5.dp, 17.dp))
                .background(IntentColors.AccentSoft)
                .padding(horizontal = 13.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink
        )
    }
}

@Composable
private fun AgentBubble(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(17.dp, 17.dp, 17.dp, 5.dp))
            .background(IntentColors.Card, RoundedCornerShape(17.dp, 17.dp, 17.dp, 5.dp))
            .padding(14.dp)
    ) {
        Text(
            text = "✦ INTENT",
            style = MaterialTheme.typography.labelMedium,
            color = IntentColors.AccentDark,
            letterSpacing = 0.6.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun ChoiceRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, IntentColors.Line, RoundedCornerShape(13.dp))
            .background(IntentColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Ink,
            modifier = Modifier.weight(1f)
        )
        Text("›", color = IntentColors.Accent, fontSize = 18.sp)
    }
}

@Composable
private fun ComposerBar() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(IntentColors.Line)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IntentColors.NavBar)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .border(1.dp, Color(0xFFCDD1C9), RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(horizontal = 13.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Tell me more, or choose above",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.ComposerHint,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(IntentColors.Accent),
                contentAlignment = Alignment.Center
            ) {
                Text("◉", color = Color.White, fontSize = 16.sp)
            }
        }
    }
}
