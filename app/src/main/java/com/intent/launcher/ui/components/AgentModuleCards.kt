package com.intent.launcher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.automirrored.outlined.ShowChart
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.engine.AgentModuleProjection
import com.intent.launcher.ui.theme.IntentColors

private val CardShape = RoundedCornerShape(22.dp)
private val SerifTitle = FontFamily.Serif

@Composable
fun AgentNotedBar(
    model: AgentModuleProjection.NotedBar,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(100.dp))
            .background(IntentColors.SuccessSoft)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(IntentColors.Success, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = model.text,
            style = MaterialTheme.typography.labelMedium,
            color = IntentColors.Success,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.4.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun AgentNextForYouCard(
    model: AgentModuleProjection.NextForYouCard,
    onBegin: () -> Unit,
    onLater: () -> Unit,
    onDiscuss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, CardShape)
            .background(IntentColors.Card, CardShape)
            .padding(18.dp)
    ) {
        Text(
            text = "NEXT FOR YOU",
            style = MaterialTheme.typography.labelMedium,
            color = IntentColors.Success,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
        Spacer(modifier = Modifier.height(10.dp))
        ProvenanceChip(
            text = model.provenanceChip,
            tone = ChipTone.Success
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = model.title,
            fontFamily = SerifTitle,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            color = IntentColors.Ink
        )
        if (model.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = model.body,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Lede,
                lineHeight = 19.sp
            )
        }
        if (model.metaPrimary.isNotBlank()) {
            Spacer(modifier = Modifier.height(14.dp))
            MetaRow(icon = Icons.Outlined.AccessTime, text = model.metaPrimary)
        }
        if (model.metaSecondary.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            MetaRow(
                icon = Icons.Outlined.TrackChanges,
                text = model.metaSecondary,
                showChevron = true
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        IntentPrimaryButton(text = model.beginLabel, onClick = onBegin)
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = model.laterLabel,
                style = MaterialTheme.typography.labelLarge,
                color = IntentColors.AccentDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onLater)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
            Text(
                text = "·",
                style = MaterialTheme.typography.labelLarge,
                color = IntentColors.Muted,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = model.discussLabel,
                style = MaterialTheme.typography.labelLarge,
                color = IntentColors.AccentDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onDiscuss)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
fun AgentOneQuestionCard(
    model: AgentModuleProjection.OneQuestionCard,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onFreeTextMic: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, CardShape)
            .background(IntentColors.Card, CardShape)
            .padding(18.dp)
    ) {
        Text(
            text = model.provenanceChip.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = IntentColors.AccentDark,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = model.title,
            fontFamily = SerifTitle,
            fontWeight = FontWeight.Bold,
            fontSize = 24.sp,
            lineHeight = 28.sp,
            color = IntentColors.Ink
        )
        if (model.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = model.body,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Lede,
                lineHeight = 19.sp
            )
        }
        if (model.options.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.options.forEachIndexed { index, label ->
                    RadioOptionRow(
                        text = label,
                        selected = selectedIndex == index,
                        onClick = { onSelect(index) }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(14.dp))
        FreeTextPromptRow(
            hint = model.freeTextHint,
            onMic = onFreeTextMic
        )
    }
}

@Composable
fun AgentProposalCard(
    model: AgentModuleProjection.ProposalCard,
    onReview: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Warm.copy(alpha = 0.45f), CardShape)
            .background(IntentColors.Card, CardShape)
            .clip(CardShape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IntentColors.WarmSoft)
                .padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "✦",
                color = IntentColors.Warm,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "PROPOSED · NOT YET ACTIVE",
                style = MaterialTheme.typography.labelMedium,
                color = IntentColors.Warm,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.1.sp
            )
        }
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                text = model.title,
                fontFamily = SerifTitle,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 28.sp,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(14.dp))
            MetaRow(icon = Icons.Outlined.CalendarMonth, text = model.scheduleLine)
            DividerLine()
            MetaRow(icon = Icons.Outlined.HourglassBottom, text = model.durationLine)
            DividerLine()
            MetaRow(icon = Icons.Outlined.TrackChanges, text = model.linkedIntentionLine)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = model.honestyNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            IntentPrimaryButton(text = model.reviewLabel, onClick = onReview)
            Spacer(modifier = Modifier.height(8.dp))
            IntentSecondaryButton(
                text = model.dismissLabel,
                onClick = onDismiss,
                accentOutline = true
            )
        }
    }
}

@Composable
fun AgentRecordedEntryCard(
    model: AgentModuleProjection.RecordedEntryCard,
    onEdit: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, CardShape)
            .background(IntentColors.Card, CardShape)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(IntentColors.SuccessSoft, CircleShape)
                    .border(1.dp, IntentColors.Success.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = IntentColors.Success,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = model.subtitle.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = IntentColors.Muted,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.0.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = model.title,
            fontFamily = SerifTitle,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(IntentColors.SuccessSoft)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = model.valueLine,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Success,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = model.sourceLabel,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
        )
        Spacer(modifier = Modifier.height(14.dp))
        DividerLine()
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onEdit,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(13.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = IntentColors.Accent,
                    contentColor = Color.White
                )
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(model.editLabel, fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, IntentColors.Accent),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = IntentColors.Accent)
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(model.undoLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun AgentExplanationCard(
    model: AgentModuleProjection.ExplanationCard,
    onAction: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, CardShape)
            .background(IntentColors.Card, CardShape)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = IntentColors.AccentDark,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "EXPLANATION",
                style = MaterialTheme.typography.labelMedium,
                color = IntentColors.AccentDark,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.4.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = model.title,
            fontFamily = SerifTitle,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            color = IntentColors.Ink
        )
        if (model.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = model.body,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Lede,
                lineHeight = 19.sp
            )
        }
        if (model.sourceLabel.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            ProvenanceChip(text = model.sourceLabel, tone = ChipTone.Neutral)
        }
        if (model.actions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.actions.forEachIndexed { index, label ->
                    if (index == 0) {
                        IntentPrimaryButton(text = label, onClick = { onAction(label) })
                    } else {
                        IntentSecondaryButton(
                            text = label,
                            onClick = { onAction(label) },
                            accentOutline = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgentProgressCard(
    model: AgentModuleProjection.ProgressCard,
    onAction: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, CardShape)
            .background(IntentColors.Card, CardShape)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ShowChart,
                    contentDescription = null,
                    tint = IntentColors.Success,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "PROGRESS",
                    style = MaterialTheme.typography.labelMedium,
                    color = IntentColors.Success,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp
                )
            }
            ProvenanceChip(
                text = model.provenanceChip,
                tone = if (model.provenanceChip.equals("OBSERVED", ignoreCase = true) ||
                    model.provenanceChip.equals("SAID", ignoreCase = true)
                ) {
                    ChipTone.Success
                } else {
                    ChipTone.Neutral
                }
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = model.title,
            fontFamily = SerifTitle,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            color = IntentColors.Ink
        )
        if (model.body.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = model.body,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Lede,
                lineHeight = 19.sp
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = model.honestyNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted,
            lineHeight = 17.sp
        )
        if (model.sourceLabel.isNotBlank() &&
            !model.sourceLabel.equals(model.provenanceChip, ignoreCase = true)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = model.sourceLabel,
                style = MaterialTheme.typography.labelSmall,
                color = IntentColors.Muted
            )
        }
        if (model.actions.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.actions.forEachIndexed { index, label ->
                    if (index == 0) {
                        IntentPrimaryButton(text = label, onClick = { onAction(label) })
                    } else {
                        IntentSecondaryButton(
                            text = label,
                            onClick = { onAction(label) },
                            accentOutline = true
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AgentReportPreviewCard(
    model: AgentModuleProjection.ReportPreviewCard,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, CardShape)
            .background(IntentColors.Card, CardShape)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = IntentColors.Muted,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "REPORT PREVIEW",
                    style = MaterialTheme.typography.labelMedium,
                    color = IntentColors.Section,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
            }
            ProvenanceChip(text = model.provenanceChip, tone = ChipTone.Neutral)
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = model.title,
            fontFamily = SerifTitle,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            lineHeight = 26.sp,
            color = IntentColors.Ink
        )
        if (model.rangeLine.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = model.rangeLine,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = model.primaryMetric,
            fontFamily = SerifTitle,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
            lineHeight = 36.sp,
            color = IntentColors.Ink
        )
        if (!model.trendLine.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            ProvenanceChip(text = model.trendLine, tone = ChipTone.Success)
        }
        Spacer(modifier = Modifier.height(14.dp))
        DividerLine()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text(
                text = model.statLeft,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Ink,
                fontWeight = FontWeight.SemiBold
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(IntentColors.Line)
            )
            Text(
                text = model.statRight,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Ink,
                fontWeight = FontWeight.SemiBold
            )
        }
        DividerLine()
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = model.footer,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(14.dp))
        Button(
            onClick = onOpen,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = IntentColors.Accent,
                contentColor = Color.White
            )
        ) {
            Text(
                text = model.openLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null
            )
        }
    }
}

private enum class ChipTone { Neutral, Success }

@Composable
private fun ProvenanceChip(text: String, tone: ChipTone) {
    val (bg, fg) = when (tone) {
        ChipTone.Neutral -> IntentColors.Pill to IntentColors.PillText
        ChipTone.Success -> IntentColors.SuccessSoft to IntentColors.Success
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (tone == ChipTone.Success) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .background(IntentColors.Success, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = fg,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.2.sp
        )
    }
}

@Composable
private fun MetaRow(
    icon: ImageVector,
    text: String,
    showChevron: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IntentColors.Section,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink,
            modifier = Modifier.weight(1f)
        )
        if (showChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = IntentColors.Arrow,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DividerLine() {
    Spacer(modifier = Modifier.height(10.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(IntentColors.Line)
    )
    Spacer(modifier = Modifier.height(10.dp))
}

@Composable
private fun RadioOptionRow(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val border = if (selected) IntentColors.Accent else IntentColors.Line
    val bg = if (selected) IntentColors.AccentSoft else IntentColors.Card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (selected) {
                Icons.Outlined.RadioButtonChecked
            } else {
                Icons.Outlined.RadioButtonUnchecked
            },
            contentDescription = null,
            tint = if (selected) IntentColors.Accent else IntentColors.Muted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = text,
            fontFamily = SerifTitle,
            fontSize = 15.sp,
            color = IntentColors.Ink,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun FreeTextPromptRow(
    hint: String,
    onMic: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.ComposerBorder, RoundedCornerShape(16.dp))
            .background(IntentColors.EmptyFill, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = hint,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.ComposerHint,
            modifier = Modifier.weight(1f)
        )
        if (onMic != null) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(IntentColors.Accent)
                    .clickable(onClick = onMic),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = "Speak",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
