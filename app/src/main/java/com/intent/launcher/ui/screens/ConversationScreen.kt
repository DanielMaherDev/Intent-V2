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
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.intent.launcher.engine.AssistantSpeech
import com.intent.launcher.engine.BuilderAnswerCard
import com.intent.launcher.engine.ChoiceOptions
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
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.NumberWheel
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
    discussSourceLabel: String = "On-device replies",
    discussHonestyNote: String? = null,
    /** SAID intention statement shown as find-a-way framing when no user turns yet. */
    intentionStatement: String = "",
    voiceNote: String? = null,
    voiceEnabled: Boolean = false,
    onMicClick: () -> Unit = {},
    onSendFreeText: (String) -> Unit = {},
    freeTextBusy: Boolean = false,
    /** §8.9 — cancel in-flight Discuss / create model wait. */
    onCancelThinking: () -> Unit = {},
    /** In-flight spoken agent reply — Stop / mic / type interrupt via InterruptedVoice. */
    assistantSpeaking: Boolean = false,
    onStopSpeaking: () -> Unit = {},
    onSpeakAloud: () -> Unit = {},
    canSpeakAloud: Boolean = false,
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
    /** In-chat NumberWheel confirm — value + unit label. */
    onBuilderQuantity: (value: Int, unitLabel: String) -> Unit = { _, _ -> },
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

    // Gate D: only the latest active builder card accepts input (historical read-only).
    val builderBlocks = turns.asSequence()
        .flatMap { it.blocks }
        .filter { it.active && BuilderAnswerCard.isBuilderCard(it) }
        .toList()
    val activeBuilderBlock = builderBlocks.lastOrNull()
    val builderQuantity = activeBuilderBlock?.let { block ->
        (BuilderAnswerCard.decode(block) as? BuilderAnswerCard.Decoded.Quantity)?.let {
            block to it
        }
    }
    val activeDecoded = activeBuilderBlock?.let { BuilderAnswerCard.decode(it) }
    val choiceBlock = when (activeDecoded) {
        is BuilderAnswerCard.Decoded.Choice,
        is BuilderAnswerCard.Decoded.MultiChoice,
        is BuilderAnswerCard.Decoded.Weekdays,
        is BuilderAnswerCard.Decoded.Confirm -> activeBuilderBlock
        else -> turns.asSequence()
            .flatMap { it.blocks }
            .filter { it.type == ResponseBlockType.CHOICE_SET && it.active }
            .filterNot { BuilderAnswerCard.isBuilderCard(it) }
            .lastOrNull()
    }
    val questionBlock = when (activeDecoded) {
        is BuilderAnswerCard.Decoded.FreeText,
        is BuilderAnswerCard.Decoded.DateTime -> activeBuilderBlock
        else -> turns.asSequence()
            .flatMap { it.blocks }
            .filter {
                it.type == ResponseBlockType.QUESTION &&
                    it.active &&
                    BuilderAnswerCard.decode(it) !is BuilderAnswerCard.Decoded.Quantity
            }
            .filterNot { BuilderAnswerCard.isBuilderCard(it) }
            .lastOrNull()
    }
    val oneQuestion = if (builderQuantity != null) {
        null
    } else {
        choiceBlock?.let { AgentModuleProjection.oneQuestionFromBlock(it) }
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
        builderQuantity == null &&
        nextCard == null &&
        proposalCard == null &&
        relationCard == null &&
        relatedStrip == null &&
        recordedCard == null &&
        reportCard == null &&
        explanationCard == null &&
        progressCard == null

    var selectedQuestionIndex by remember { mutableStateOf<Int?>(null) }
    var awaitingOtherText by remember { mutableStateOf(false) }
    val quantitySpec = builderQuantity?.second
    var wheelValue by remember(quantitySpec?.promptId, quantitySpec?.defaultValue) {
        mutableIntStateOf(quantitySpec?.defaultValue ?: BuilderAnswerCard.DEFAULT_VALUE)
    }

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
                // S-CHAT — no “heuristics / no model calls” footer chrome.
                val source = discussSourceLabel.trim()
                if (source.isNotEmpty() &&
                    !source.contains("heuristic", ignoreCase = true) &&
                    !source.contains("no model", ignoreCase = true)
                ) {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelMedium,
                        color = IntentColors.AccentDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (discussHonestyNote != null &&
                    !discussHonestyNote.contains("heuristic", ignoreCase = true)
                ) {
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

            AgentModuleMotion(visible = builderQuantity != null) {
                val qtyPair = builderQuantity
                if (qtyPair != null) {
                    val qtyBlock = qtyPair.first
                    val qty = qtyPair.second
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, IntentColors.Line, RoundedCornerShape(22.dp))
                            .background(IntentColors.Card, RoundedCornerShape(22.dp))
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "ONE QUESTION",
                            style = MaterialTheme.typography.labelMedium,
                            color = IntentColors.AccentDark,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.4.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = qtyBlock.title.ifBlank { qty.promptId },
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            lineHeight = 28.sp,
                            color = IntentColors.Ink
                        )
                        if (qtyBlock.body.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = qtyBlock.body,
                                style = MaterialTheme.typography.bodyMedium,
                                color = IntentColors.Lede,
                                lineHeight = 19.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        NumberWheel(
                            values = qty.values,
                            selected = wheelValue,
                            onSelectedChange = { wheelValue = it },
                            unitLabel = qty.unitLabel,
                            summaryTemplate = qty.summaryTemplate
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        IntentPrimaryButton(
                            text = "Continue",
                            onClick = { onBuilderQuantity(wheelValue, qty.unitLabel) }
                        )
                    }
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
                            if (ChoiceOptions.isOther(label)) {
                                awaitingOtherText = true
                                return@AgentOneQuestionCard
                            }
                            awaitingOtherText = false
                            val blockId = oneQuestion.blockId
                            val isBuilderChoice = choiceBlock != null &&
                                BuilderAnswerCard.isBuilderCard(choiceBlock)
                            if (blockId != null && choiceBlock != null && !isBuilderChoice) {
                                onAmbiguousChoice(blockId, index)
                            } else {
                                onChip(label)
                            }
                        },
                        onFreeTextMic = if (voiceEnabled) onMicClick else null
                    )
                    if (awaitingOtherText) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Type your own answer in the composer below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Warm
                        )
                    }
                }
            }
            if (oneQuestion == null &&
                builderQuantity == null &&
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
                        onDismiss = onDeclineProposal,
                        onEdit = onOpenProposal
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

            if (!assistantSpeaking && canSpeakAloud) {
                Spacer(modifier = Modifier.height(12.dp))
                SpeakAloudRow(onClick = onSpeakAloud)
            }

            if (assistantSpeaking) {
                Spacer(modifier = Modifier.height(12.dp))
                SpeakingInterruptBanner(onStop = onStopSpeaking)
            }

            if (voiceNote != null) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = voiceNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
            // Keep in-chat Continue / chips clear of the fixed composer.
            Spacer(modifier = Modifier.height(28.dp))
        }

        // Pinned above composer so §8.9 Cancel is always reachable (not scrolled away).
        if (freeTextBusy && !assistantSpeaking) {
            ThinkingCancelBanner(onCancel = onCancelThinking)
        }

        ComposerBar(
            voiceEnabled = voiceEnabled,
            onMicClick = onMicClick,
            onSendFreeText = onSendFreeText,
            freeTextBusy = freeTextBusy,
            assistantSpeaking = assistantSpeaking,
            onStopSpeaking = onStopSpeaking,
            onCancelThinking = onCancelThinking
        )
    }
}

@Composable
private fun ThinkingCancelBanner(onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Waiting for the model…",
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Cancel",
            style = MaterialTheme.typography.labelLarge,
            color = IntentColors.Accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ThinkingCancelBanner(onCancel: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Waiting for the model…",
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "Cancel",
            style = MaterialTheme.typography.labelLarge,
            color = IntentColors.Accent,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(horizontal = 8.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun ConversationTopBar(
    intentionTitle: String,
    onBack: () -> Unit
) {
    // S-CHAT — back + intention title (bodyMedium, one line). No gear / turn count / pill.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            tint = IntentColors.Ink,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onBack)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = intentionTitle.ifBlank { "Conversation" },
            style = MaterialTheme.typography.bodyLarge,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
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
                .clip(RoundedCornerShape(16.dp))
                .background(IntentColors.AccentSoft)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink,
            lineHeight = 20.sp
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

    val prose = scrubAgentProse(text)
    // S-CHAT — no floating badge / avatar chip over the thread.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .widthIn(max = 320.dp)
                .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
                .background(IntentColors.Card, RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            when {
                gatewayModel != null -> {
                    SmallLabel(text = gatewayModel.eyebrow)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = scrubAgentProse(gatewayModel.body.ifBlank { prose }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink,
                        lineHeight = 20.sp
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
                    SmallLabel(text = gap.title.ifBlank { "Limit" })
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = scrubAgentProse(gap.body.ifBlank { prose }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink,
                        lineHeight = 20.sp
                    )
                    if (gap.sourceLabel.isNotBlank() &&
                        !gap.sourceLabel.contains("heuristic", ignoreCase = true)
                    ) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = gap.sourceLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = IntentColors.Muted
                        )
                    }
                }
                moduleTyped || prose.isNotBlank() -> {
                    Text(
                        text = prose,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Ink,
                        lineHeight = 20.sp
                    )
                }
            }
        }
    }

}

/** Drop CASE- ids and similar developer prefixes from agent prose (S-CHAT). */
internal fun scrubAgentProse(raw: String): String =
    raw.trim()
        .replace(Regex("""(?i)^CASE-\d+\s*[—\-–:]\s*"""), "")
        .trim()

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
private fun SpeakAloudRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, IntentColors.Line, RoundedCornerShape(13.dp))
            .background(IntentColors.Card)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.VolumeUp,
            contentDescription = null,
            tint = IntentColors.AccentDark,
            modifier = Modifier.size(20.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Speak aloud",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Ink,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Play the latest reply, then Stop or speak to interrupt",
                style = MaterialTheme.typography.labelSmall,
                color = IntentColors.Muted
            )
        }
    }
}

@Composable
private fun SpeakingInterruptBanner(onStop: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .border(1.dp, IntentColors.Accent, RoundedCornerShape(13.dp))
            .background(IntentColors.AccentSoft)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SmallLabel(text = "SPEAKING · INTERRUPT")
        Text(
            text = AssistantSpeech.SPEAKING_BANNER,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = AssistantSpeech.HONESTY_NOTE,
            style = MaterialTheme.typography.labelSmall,
            color = IntentColors.Muted
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(IntentColors.Ink)
                .clickable(onClick = onStop)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Stop,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Stop speaking",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ComposerBar(
    voiceEnabled: Boolean,
    onMicClick: () -> Unit,
    onSendFreeText: (String) -> Unit,
    freeTextBusy: Boolean,
    assistantSpeaking: Boolean,
    onStopSpeaking: () -> Unit,
    onCancelThinking: () -> Unit = {}
) {
    var draft by remember { mutableStateOf("") }
    fun send() {
        val text = draft.trim()
        if (text.isEmpty()) return
        if (freeTextBusy && !assistantSpeaking) return
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
                    .height(48.dp)
                    .border(1.dp, IntentColors.ComposerBorder, RoundedCornerShape(24.dp))
                    .background(Color.White, RoundedCornerShape(24.dp))
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = IntentColors.Ink),
                    cursorBrush = SolidColor(IntentColors.Accent),
                    singleLine = true,
                    enabled = !freeTextBusy || assistantSpeaking,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send() }),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text(
                                text = when {
                                    assistantSpeaking -> "Type to interrupt…"
                                    freeTextBusy -> "Waiting…"
                                    else -> "Message"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = IntentColors.ComposerHint
                            )
                        }
                        inner()
                    }
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            if (draft.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(
                            if (!freeTextBusy || assistantSpeaking) {
                                IntentColors.Accent
                            } else {
                                IntentColors.Muted
                            }
                        )
                        .clickable(
                            enabled = !freeTextBusy || assistantSpeaking,
                            onClick = { send() }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (assistantSpeaking) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(IntentColors.Ink)
                        .clickable(onClick = onStopSpeaking),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Stop,
                        contentDescription = "Stop speaking",
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
                        when {
                            assistantSpeaking -> IntentColors.AccentDark
                            voiceEnabled -> IntentColors.Accent
                            else -> IntentColors.Muted
                        }
                    )
                    .clickable(onClick = onMicClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = if (assistantSpeaking) {
                        "Speak to interrupt"
                    } else {
                        "Speak"
                    },
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
