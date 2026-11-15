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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.domain.ConversationTurn
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.engine.AmbiguousChoiceSetCard
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
    /** Honest Discuss source: local heuristics vs model suggestion. */
    discussSourceLabel: String = "Local heuristics",
    discussHonestyNote: String? = null,
    /** SAID intention statement shown as find-a-way framing when no user turns yet. */
    intentionStatement: String = "",
    voiceNote: String? = null,
    voiceEnabled: Boolean = false,
    onMicClick: () -> Unit = {},
    onSendFreeText: (String) -> Unit = {},
    freeTextBusy: Boolean = false,
    /** AMBIGUOUS CHOICE_SET chip — [blockId] + choice index from the card. */
    onAmbiguousChoice: (blockId: String, choiceIndex: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val paperWash = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF7F5EF),
            IntentColors.Paper,
            Color(0xFFF1EEE6)
        )
    )
    val hasUserTurn = turns.any { it.role == "user" }
    val showFramingStatement = !hasUserTurn && intentionStatement.isNotBlank()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(paperWash)
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = conversationHeadline,
                style = MaterialTheme.typography.headlineLarge,
                color = IntentColors.Ink,
                lineHeight = 34.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = discussSourceLabel,
                style = MaterialTheme.typography.labelMedium,
                color = IntentColors.AccentDark,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.4.sp
            )
            if (discussHonestyNote != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = discussHonestyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
            Spacer(modifier = Modifier.height(18.dp))

            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showFramingStatement) {
                    UserBubble(intentionStatement)
                }
                turns.forEach { turn ->
                    if (turn.role == "user") {
                        UserBubble(turn.text)
                    } else {
                        AgentBubble(
                            text = turn.text,
                            blocks = turn.blocks,
                            onAmbiguousChoice = onAmbiguousChoice
                        )
                    }
                }
            }

            if (chips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            onMicClick = onMicClick,
            onSendFreeText = onSendFreeText,
            freeTextBusy = freeTextBusy
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
            color = IntentColors.Ink,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun AgentBubble(
    text: String,
    blocks: List<ResponseBlock> = emptyList(),
    onAmbiguousChoice: (blockId: String, choiceIndex: Int) -> Unit = { _, _ -> }
) {
    val gap = blocks.firstOrNull { it.type == ResponseBlockType.CAPABILITY_GAP && it.active }
    val question = blocks.firstOrNull { it.type == ResponseBlockType.QUESTION && it.active }
    val choiceSet = blocks.firstOrNull { it.type == ResponseBlockType.CHOICE_SET && it.active }
    val choiceModel = choiceSet?.let { AmbiguousChoiceSetCard.fromBlock(it) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 3.dp,
                shape = RoundedCornerShape(17.dp, 17.dp, 17.dp, 5.dp),
                ambientColor = Color(0x14000000),
                spotColor = Color(0x14000000)
            )
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
        when {
            choiceModel != null -> {
                // GatewayStatus.AMBIGUOUS — first-class CHOICE_SET card with chips.
                Spacer(modifier = Modifier.height(6.dp))
                SmallLabel(text = choiceModel.eyebrow)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = choiceModel.body.ifBlank { text },
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink,
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    choiceModel.choices.forEach { choice ->
                        ChoiceRow(
                            text = choice.label,
                            onClick = { onAmbiguousChoice(choiceModel.blockId, choice.index) }
                        )
                    }
                }
                if (choiceModel.sourceLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = choiceModel.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = IntentColors.Muted
                    )
                }
            }
            gap != null -> {
                Spacer(modifier = Modifier.height(6.dp))
                SmallLabel(text = "CAPABILITY_GAP · ${gap.title}")
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = gap.body.ifBlank { text },
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink,
                    lineHeight = 19.sp
                )
                if (gap.sourceLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = gap.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = IntentColors.Muted
                    )
                }
            }
            question != null -> {
                // Gate D2 — intervention QUESTION always shows OBSERVED vs INFERRED.
                Spacer(modifier = Modifier.height(6.dp))
                SmallLabel(
                    text = interventionProvenanceChip(question.sourceLabel, question.title)
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = question.body.ifBlank { text },
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink,
                    lineHeight = 19.sp
                )
                if (question.sourceLabel.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = question.sourceLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = IntentColors.Muted
                    )
                }
            }
            else -> {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink,
                    lineHeight = 19.sp
                )
            }
        }
    }
}

/** Prefer explicit OBSERVED / INFERRED chip from sourceLabel; never silent mix. */
private fun interventionProvenanceChip(sourceLabel: String, title: String): String {
    val upper = sourceLabel.uppercase()
    val chip = when {
        upper.startsWith("OBSERVED") -> "OBSERVED"
        upper.startsWith("INFERRED") -> "INFERRED"
        upper.contains("OBSERVED") -> "OBSERVED"
        upper.contains("INFERRED") -> "INFERRED"
        else -> "INFERRED"
    }
    val titlePart = title.trim().ifBlank { "Check-in" }
    return "$chip · $titlePart"
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
            .padding(horizontal = 13.dp, vertical = 12.dp),
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
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ComposerBar(
    voiceEnabled: Boolean,
    onMicClick: () -> Unit,
    onSendFreeText: (String) -> Unit,
    freeTextBusy: Boolean
) {
    var draft by remember { mutableStateOf("") }
    fun send() {
        val text = draft.trim()
        if (text.isEmpty() || freeTextBusy) return
        onSendFreeText(text)
        draft = ""
    }
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
                    .shadow(
                        elevation = 2.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color(0x0F000000),
                        spotColor = Color(0x0F000000)
                    )
                    .border(1.dp, IntentColors.ComposerBorder, RoundedCornerShape(22.dp))
                    .background(Color.White, RoundedCornerShape(22.dp))
                    .padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodySmall.copy(color = IntentColors.Ink),
                    cursorBrush = SolidColor(IntentColors.Accent),
                    singleLine = true,
                    enabled = !freeTextBusy,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                text = if (freeTextBusy) {
                                    "Waiting for model…"
                                } else {
                                    "Tell me more, or choose above"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = IntentColors.ComposerHint
                            )
                        }
                        inner()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (draft.isNotBlank() && !freeTextBusy) {
                                IntentColors.Accent
                            } else {
                                IntentColors.Muted
                            }
                        )
                        .clickable(enabled = draft.isNotBlank() && !freeTextBusy, onClick = { send() }),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(22.dp)
                        .background(IntentColors.Line)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .size(36.dp)
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
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
