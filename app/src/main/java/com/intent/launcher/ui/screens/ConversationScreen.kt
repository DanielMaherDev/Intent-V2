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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
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
    conversationEyebrow: String = "A conversation about your goal",
    conversationHeadline: String = "Let's find a way that fits.",
    voiceNote: String? = null,
    voiceEnabled: Boolean = false,
    onMicClick: () -> Unit = {},
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
                .padding(horizontal = 19.dp, vertical = 15.dp)
        ) {
            ScreenTopBar(
                contextLabel = intentionTitle.ifBlank { "Conversation" },
                onBack = onBack
            )
            Spacer(modifier = Modifier.height(23.dp))
            SmallLabel(text = conversationEyebrow)
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = conversationHeadline,
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

            if (voiceNote != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = voiceNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
        }

        ComposerBar(
            voiceEnabled = voiceEnabled,
            onMicClick = onMicClick
        )
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
                .background(IntentColors.UserBubble)
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
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = IntentColors.Accent,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ComposerBar(
    voiceEnabled: Boolean,
    onMicClick: () -> Unit
) {
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
                    .border(1.dp, IntentColors.ComposerBorder, RoundedCornerShape(18.dp))
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(start = 13.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (voiceEnabled) {
                        "Speak a chip phrase, or choose above"
                    } else {
                        "Choose a chip above — mic needs permission"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.ComposerHint,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.Outlined.Keyboard,
                    contentDescription = null,
                    tint = IntentColors.ComposerHint,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.size(8.dp))
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (voiceEnabled) IntentColors.Accent else IntentColors.Muted
                    )
                    .clickable(onClick = onMicClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = "Speak",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
