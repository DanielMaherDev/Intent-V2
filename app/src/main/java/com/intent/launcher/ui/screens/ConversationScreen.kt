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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.domain.ConversationTurn
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.Proposal
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.domain.ResponseBlock
import com.intent.launcher.domain.ResponseBlockType
import com.intent.launcher.domain.TrackerEntry
import com.intent.launcher.domain.Intention
import com.intent.launcher.engine.AgentModuleProjection
import com.intent.launcher.engine.CrossIntentionRelationCard
import com.intent.launcher.engine.GatewayStatusCard
import com.intent.launcher.engine.IntentionHierarchy
import com.intent.launcher.engine.PathSummary
import com.intent.launcher.engine.ProgressProof
import com.intent.launcher.ui.components.AgentExplanationCard
import com.intent.launcher.ui.components.AgentModuleMotion
import com.intent.launcher.ui.components.AgentNextForYouCard
import com.intent.launcher.ui.components.AgentNotedBar
import com.intent.launcher.ui.components.AgentOneQuestionCard
import com.intent.launcher.ui.components.AgentProgressCard
import com.intent.launcher.ui.components.AgentProposalCard
import com.intent.launcher.ui.components.AgentRecordedEntryCard
import com.intent.launcher.ui.components.AgentRelatedIntentionsStrip
import com.intent.launcher.ui.components.AgentRelationCard
import com.intent.launcher.ui.components.AgentReportPreviewCard
import com.intent.launcher.ui.components.IntentBrandMark
import com.intent.launcher.ui.components.IntentPill
import com.intent.launcher.ui.components.PillTone
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
    /** GatewayStatus card chip (e.g. STALE Retry) — [blockId] + action index. */
    onGatewayStatusAction: (blockId: String, actionIndex: Int) -> Unit = { _, _ -> },
    /** Cross-intention relation Confirm / Cancel — [blockId] + action index. */
    onRelationAction: (blockId: String, actionIndex: Int) -> Unit = { _, _ -> },
    pendingProposal: Proposal? = null,
    relatedSaidFact: Fact? = null,
    nextAction: NextAction? = null,
    latestTrackerEntry: TrackerEntry? = null,
    pathSummary: PathSummary.View? = null,
    progressProof: ProgressProof.View? = null,
    showReportPreview: Boolean = false,
    intentions: List<Intention> = emptyList(),
    hierarchy: IntentionHierarchy.View? = null,
    onBeginNext: () -> Unit = {},
    onLaterNext: () -> Unit = {},
    onDiscussNext: () -> Unit = {},
    onDeclineProposal: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onEditRecorded: (entryId: String?) -> Unit = {},
    onUndoRecorded: (entryId: String?) -> Unit = {},
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

    val blockTypes = turns.flatMap { it.blocks }.filter { it.active }.map { it.type }.toSet()
    val hasTypedAgentCard = blockTypes.any {
        it == ResponseBlockType.NEXT_ACTION ||
            it == ResponseBlockType.PROPOSAL ||
            it == ResponseBlockType.QUESTION ||
            it == ResponseBlockType.CHOICE_SET ||
            it == ResponseBlockType.RECORDED_ENTRY ||
            it == ResponseBlockType.REPORT_PREVIEW ||
            it == ResponseBlockType.EXPLANATION ||
            it == ResponseBlockType.PROGRESS
    }

    val nextCard = turns.asSequence()
        .flatMap { it.blocks }
        .mapNotNull { AgentModuleProjection.nextForYouFromBlock(it, intentionTitle) }
        .firstOrNull()
        ?: nextAction?.let { AgentModuleProjection.nextForYouFromNextAction(it, intentionTitle) }

    val relationCard = turns.asSequence()
        .flatMap { it.blocks }
        .mapNotNull { CrossIntentionRelationCard.fromBlock(it, intentions) }
        .firstOrNull { it.pending }
        ?: turns.asSequence()
            .flatMap { it.blocks }
            .mapNotNull { CrossIntentionRelationCard.fromBlock(it, intentions) }
            .firstOrNull()

    val relatedStrip = hierarchy?.let { CrossIntentionRelationCard.relatedStrip(it) }

    val proposalCard = if (relationCard?.pending == true) {
        null
    } else {
        turns.asSequence()
            .flatMap { it.blocks }
            .mapNotNull {
                AgentModuleProjection.proposalFromBlock(it, intentionTitle, pendingProposal)
            }
            .firstOrNull()
            ?: pendingProposal?.takeIf { it.status == ProposalStatus.PENDING }?.let {
                AgentModuleProjection.proposalFromPending(it, intentionTitle)
            }
    }

    val noted = if (relationCard != null || proposalCard != null || hasPendingProposal) {
        AgentModuleProjection.notedBarFromSaidFact(relatedSaidFact)
    } else {
        null
    }

    val choiceBlock = turns.asSequence()
        .flatMap { it.blocks }
        .firstOrNull { it.type == ResponseBlockType.CHOICE_SET && it.active }
    val questionBlock = turns.asSequence()
        .flatMap { it.blocks }
        .firstOrNull { it.type == ResponseBlockType.QUESTION && it.active }
    val oneQuestion = choiceBlock?.let { AgentModuleProjection.oneQuestionFromBlock(it) }
        ?: questionBlock?.let { AgentModuleProjection.oneQuestionFromBlock(it) }
        ?: if (!hasTypedAgentCard && chips.isNotEmpty() && nextCard == null && proposalCard == null) {
            AgentModuleProjection.oneQuestionFromChips(
                chips = chips,
                headline = conversationHeadline,
                body = "Your answer helps me suggest the right practice."
            )
        } else {
            null
        }

    val recordedCard = turns.asSequence()
        .flatMap { it.blocks }
        .mapNotNull { AgentModuleProjection.recordedFromBlock(it) }
        .firstOrNull()
        ?: latestTrackerEntry?.let { AgentModuleProjection.recordedFromTrackerEntry(it) }

    val reportCard = turns.asSequence()
        .flatMap { it.blocks }
        .mapNotNull {
            AgentModuleProjection.reportPreviewFromBlock(it, pathSummary, progressProof)
        }
        .firstOrNull()
        ?: if (showReportPreview) {
            AgentModuleProjection.reportPreviewFromStore(pathSummary, progressProof)
        } else {
            null
        }

    val explanationCard = turns.asSequence()
        .flatMap { it.blocks }
        .mapNotNull { AgentModuleProjection.explanationFromBlock(it) }
        .firstOrNull()

    val progressCard = turns.asSequence()
        .flatMap { it.blocks }
        .mapNotNull { AgentModuleProjection.progressFromBlock(it) }
        .firstOrNull()

    val showLegacyFraming = turns.isEmpty() &&
        oneQuestion == null &&
        nextCard == null &&
        proposalCard == null &&
        relationCard == null &&
        relatedStrip == null &&
        recordedCard == null &&
        reportCard == null &&
        explanationCard == null &&
        progressCard == null

    var selectedQuestionIndex by remember { mutableStateOf<Int?>(null) }

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
            ConversationTopBar(
                intentionTitle = intentionTitle,
                onBack = onBack
            )
            Spacer(modifier = Modifier.height(18.dp))

            if (showLegacyFraming) {
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
            }

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
                            onGatewayStatusAction = onGatewayStatusAction
                        )
                    }
                }
            }

            AgentModuleMotion(visible = noted != null) {
                if (noted != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentNotedBar(model = noted)
                }
            }

            AgentModuleMotion(visible = relatedStrip != null && relationCard == null) {
                if (relatedStrip != null && relationCard == null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentRelatedIntentionsStrip(model = relatedStrip)
                }
            }

            AgentModuleMotion(visible = oneQuestion != null) {
                if (oneQuestion != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentOneQuestionCard(
                        model = oneQuestion,
                        selectedIndex = selectedQuestionIndex,
                        onSelect = { index ->
                            selectedQuestionIndex = index
                            val label = oneQuestion.options.getOrNull(index)
                                ?: return@AgentOneQuestionCard
                            val blockId = oneQuestion.blockId
                            if (blockId != null && choiceBlock != null) {
                                onAmbiguousChoice(blockId, index)
                            } else {
                                onChip(label)
                            }
                        },
                        onFreeTextMic = if (voiceEnabled) onMicClick else null
                    )
                }
            }
            if (oneQuestion == null &&
                chips.isNotEmpty() &&
                nextCard == null &&
                proposalCard == null &&
                relationCard == null
            ) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    chips.forEach { chip ->
                        LegacyChoiceRow(text = chip, onClick = { onChip(chip) })
                    }
                }
            }

            AgentModuleMotion(
                visible = nextCard != null &&
                    nextAction?.kind != NextActionKind.REVIEW_PROPOSAL
            ) {
                val card = nextCard
                if (card != null && nextAction?.kind != NextActionKind.REVIEW_PROPOSAL) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentNextForYouCard(
                        model = card,
                        onBegin = onBeginNext,
                        onLater = onLaterNext,
                        onDiscuss = onDiscussNext
                    )
                }
            }

            AgentModuleMotion(visible = relationCard != null) {
                if (relationCard != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentRelationCard(
                        model = relationCard,
                        onConfirm = { onRelationAction(relationCard.blockId, 0) },
                        onCancel = { onRelationAction(relationCard.blockId, 1) }
                    )
                }
            }
            AgentModuleMotion(visible = relationCard == null && proposalCard != null) {
                if (relationCard == null && proposalCard != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentProposalCard(
                        model = proposalCard,
                        onReview = onOpenProposal,
                        onDismiss = onDeclineProposal
                    )
                }
            }
            if (relationCard == null && proposalCard == null && hasPendingProposal) {
                Spacer(modifier = Modifier.height(12.dp))
                LegacyChoiceRow(
                    text = "Open proposal review",
                    onClick = onOpenProposal
                )
            }

            AgentModuleMotion(visible = recordedCard != null) {
                if (recordedCard != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentRecordedEntryCard(
                        model = recordedCard,
                        onEdit = { onEditRecorded(recordedCard.entryId) },
                        onUndo = { onUndoRecorded(recordedCard.entryId) }
                    )
                }
            }

            AgentModuleMotion(visible = reportCard != null) {
                if (reportCard != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentReportPreviewCard(
                        model = reportCard,
                        onOpen = onOpenReports
                    )
                }
            }

            AgentModuleMotion(visible = explanationCard != null) {
                if (explanationCard != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentExplanationCard(
                        model = explanationCard,
                        onAction = onChip
                    )
                }
            }

            AgentModuleMotion(visible = progressCard != null) {
                if (progressCard != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    AgentProgressCard(
                        model = progressCard,
                        onAction = onChip
                    )
                }
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
private fun ConversationTopBar(
    intentionTitle: String,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            tint = IntentColors.Ink,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onBack)
        )
        IntentBrandMark(compact = true)
        if (intentionTitle.isNotBlank()) {
            IntentPill(text = intentionTitle, tone = PillTone.Neutral)
        } else {
            Spacer(modifier = Modifier.width(24.dp))
        }
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
            fontFamily = FontFamily.Serif,
            fontSize = 14.sp,
            color = IntentColors.Ink,
            lineHeight = 19.sp
        )
    }
}

@Composable
private fun AgentBubble(
    text: String,
    blocks: List<ResponseBlock> = emptyList(),
    onGatewayStatusAction: (blockId: String, actionIndex: Int) -> Unit = { _, _ -> }
) {
    val gap = blocks.firstOrNull { it.type == ResponseBlockType.CAPABILITY_GAP && it.active }
    val gatewayModel = GatewayStatusCard.fromTurnBlocks(blocks)
    // CHOICE_SET / QUESTION / PROPOSAL / NEXT_ACTION / RECORDED / REPORT /
    // EXPLANATION / PROGRESS render as first-class Agent module cards below the
    // bubble — keep bubble prose-only for those.
    val moduleTyped = blocks.any {
        it.active && it.type in setOf(
            ResponseBlockType.CHOICE_SET,
            ResponseBlockType.QUESTION,
            ResponseBlockType.PROPOSAL,
            ResponseBlockType.NEXT_ACTION,
            ResponseBlockType.RECORDED_ENTRY,
            ResponseBlockType.REPORT_PREVIEW,
            ResponseBlockType.EXPLANATION,
            ResponseBlockType.PROGRESS
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(IntentColors.Accent, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✦",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 320.dp)
                .border(1.dp, IntentColors.Line, RoundedCornerShape(17.dp, 17.dp, 17.dp, 5.dp))
                .background(IntentColors.Card, RoundedCornerShape(17.dp, 17.dp, 17.dp, 5.dp))
                .padding(14.dp)
        ) {
            when {
                gatewayModel != null -> {
                    SmallLabel(text = gatewayModel.eyebrow)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = gatewayModel.body.ifBlank { text },
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = IntentColors.Ink,
                        lineHeight = 19.sp
                    )
                    if (gatewayModel.actions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            gatewayModel.actions.forEach { action ->
                                LegacyChoiceRow(
                                    text = action.label,
                                    onClick = {
                                        onGatewayStatusAction(gatewayModel.blockId, action.index)
                                    }
                                )
                            }
                        }
                    }
                    if (gatewayModel.sourceLabel.isNotBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = gatewayModel.sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = IntentColors.Muted
                        )
                    }
                }
                gap != null -> {
                    SmallLabel(text = "CAPABILITY_GAP · ${gap.title}")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = gap.body.ifBlank { text },
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
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
                moduleTyped || text.isNotBlank() -> {
                    Text(
                        text = text,
                        fontFamily = FontFamily.Serif,
                        fontSize = 14.sp,
                        color = IntentColors.Ink,
                        lineHeight = 19.sp
                    )
                }
            }
        }
    }

}

@Composable
private fun LegacyChoiceRow(text: String, onClick: () -> Unit) {
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
                                    "Talk to INTENT…"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = IntentColors.ComposerHint
                            )
                        }
                        inner()
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (draft.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (!freeTextBusy) IntentColors.Accent else IntentColors.Muted
                            )
                            .clickable(enabled = !freeTextBusy, onClick = { send() }),
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
                }
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
