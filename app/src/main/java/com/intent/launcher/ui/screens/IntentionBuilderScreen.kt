package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.domain.BuilderDraft
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.engine.ChoiceOptions
import com.intent.launcher.engine.IntentionBuilder
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.NumberWheel
import com.intent.launcher.ui.components.PracticeMinuteChoices
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.theme.IntentColors

@Composable
fun IntentionBuilderScreen(
    draft: BuilderDraft,
    honestyNote: String?,
    onStart: (String) -> Unit,
    onPickShape: (String) -> Unit,
    onAnswerPrompt: (promptId: String, answer: String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var freeText by remember { mutableStateOf(draft.freeText) }
    var answerText by remember { mutableStateOf("") }
    var showOtherField by remember { mutableStateOf(false) }
    var showShapeCorrection by remember { mutableStateOf(false) }
    val prompt = IntentionBuilder.nextPrompt(draft)
    val (step, totalSteps) = IntentionBuilder.progressStep(draft)
    val paperWash = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF7F5EF),
            IntentColors.Paper,
            Color(0xFFF1EEE6)
        )
    )
    val quantityChoices = prompt?.quantityChoices
    var wheelValue by remember(prompt?.id, quantityChoices) {
        mutableIntStateOf(
            quantityChoices?.let { choices ->
                if (PracticeMinuteChoices.DEFAULT in choices) {
                    PracticeMinuteChoices.DEFAULT
                } else {
                    choices.getOrElse(choices.size / 2) { choices.first() }
                }
            } ?: PracticeMinuteChoices.DEFAULT
        )
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
            ScreenTopBar(contextLabel = "Builder", onBack = onBack)
            if (draft.active && draft.phase != BuilderPhase.ENTER_TEXT) {
                Spacer(modifier = Modifier.height(14.dp))
                BuilderProgressDots(current = step, total = totalSteps)
            }
            Spacer(modifier = Modifier.height(23.dp))
            Text(
                text = "Start an intention",
                style = MaterialTheme.typography.displayLarge,
                color = IntentColors.Ink,
                lineHeight = 38.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(10.dp))

            when {
                !draft.active || draft.phase == BuilderPhase.ENTER_TEXT -> {
                    Lede("Tell us what you want to work on. We’ll ask a few useful questions.")
                    Spacer(modifier = Modifier.height(18.dp))
                    OutlinedTextField(
                        value = freeText,
                        onValueChange = { if (it.length <= 60) freeText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        placeholder = {
                            Text("e.g. Speak Spanish more confidently at work")
                        },
                        supportingText = {
                            Text(
                                text = "${freeText.length}/60",
                                modifier = Modifier.fillMaxWidth(),
                                color = IntentColors.Muted
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = IntentColors.Accent,
                            unfocusedBorderColor = IntentColors.Line,
                            focusedContainerColor = Color(0xFFF3F0FA),
                            unfocusedContainerColor = IntentColors.Card
                        ),
                        shape = RoundedCornerShape(16.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    IntentPrimaryButton(
                        text = "Continue",
                        onClick = { onStart(freeText) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    IntentQuietButton(text = "Cancel", onClick = onCancel)
                }

                // Legacy drafts still on PICK_SHAPE — treat as soft confirm, not forced wall.
                draft.phase == BuilderPhase.PICK_SHAPE -> {
                    SoftShapeConfirm(
                        draft = draft,
                        showOptions = true,
                        onPickShape = onPickShape,
                        onKeepSuggested = {
                            onPickShape(draft.suggestedShapeTag)
                        }
                    )
                }

                draft.phase == BuilderPhase.MISSING_FACTS || draft.phase == BuilderPhase.CONFIRM -> {
                    val shapeLabel = IntentionBuilder.shapeDisplayLabel(
                        draft.shapeTag ?: draft.suggestedShapeTag
                    )
                    SoftShapeConfirm(
                        draft = draft,
                        showOptions = showShapeCorrection,
                        onPickShape = {
                            showShapeCorrection = false
                            onPickShape(it)
                        },
                        onKeepSuggested = { showShapeCorrection = false },
                        onRequestChange = { showShapeCorrection = true },
                        compact = !showShapeCorrection
                    )
                    if (!showShapeCorrection) {
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    if (prompt != null && draft.phase == BuilderPhase.MISSING_FACTS) {
                        Lede(
                            if (prompt.quantityChoices != null) {
                                "We’ll build a simple routine around this."
                            } else {
                                "One useful question."
                            }
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = prompt.question,
                            style = MaterialTheme.typography.headlineSmall,
                            color = IntentColors.Ink,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        val choices = prompt.quantityChoices
                        val chipOptions = prompt.choiceOptions
                        if (choices != null && prompt.quantityUnitLabel != null) {
                            NumberWheel(
                                values = choices,
                                selected = wheelValue,
                                onSelectedChange = { wheelValue = it },
                                unitLabel = prompt.quantityUnitLabel,
                                summaryTemplate = prompt.quantitySummaryTemplate
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            IntentPrimaryButton(
                                text = "Continue",
                                onClick = {
                                    val unit = prompt.quantityUnitLabel
                                    onAnswerPrompt(
                                        prompt.id,
                                        "$wheelValue $unit"
                                    )
                                }
                            )
                        } else if (chipOptions != null) {
                            val display = ChoiceOptions.withOther(chipOptions)
                            display.forEach { label ->
                                IntentSecondaryButton(
                                    text = label,
                                    onClick = {
                                        if (ChoiceOptions.isOther(label)) {
                                            showOtherField = true
                                        } else {
                                            showOtherField = false
                                            onAnswerPrompt(prompt.id, label)
                                            answerText = ""
                                        }
                                    }
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            if (showOtherField) {
                                OutlinedTextField(
                                    value = answerText,
                                    onValueChange = { answerText = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                    placeholder = { Text("Type your own answer") },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = IntentColors.Accent,
                                        unfocusedBorderColor = IntentColors.Line
                                    )
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                IntentPrimaryButton(
                                    text = "Continue",
                                    onClick = {
                                        onAnswerPrompt(prompt.id, answerText)
                                        answerText = ""
                                        showOtherField = false
                                    }
                                )
                            }
                        } else {
                            OutlinedTextField(
                                value = answerText,
                                onValueChange = { answerText = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                shape = RoundedCornerShape(16.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = IntentColors.Accent,
                                    unfocusedBorderColor = IntentColors.Line
                                )
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            IntentPrimaryButton(
                                text = "Continue",
                                onClick = {
                                    onAnswerPrompt(prompt.id, answerText)
                                    answerText = ""
                                }
                            )
                        }
                    } else {
                        Lede("Does this look right?")
                        Spacer(modifier = Modifier.height(14.dp))
                        BuilderPanel {
                            Text(
                                text = draft.title.ifBlank { "Your intention" },
                                style = MaterialTheme.typography.titleLarge,
                                color = IntentColors.Ink,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = draft.statement,
                                style = MaterialTheme.typography.bodyMedium,
                                color = IntentColors.Ink,
                                lineHeight = 19.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = shapeLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = IntentColors.Muted
                            )
                            draft.factAnswers.forEach { (_, answer) ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• $answer",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IntentColors.Muted
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            IntentPrimaryButton(
                                text = "Confirm intention",
                                onClick = onCommit
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    IntentQuietButton(text = "Cancel", onClick = onCancel)
                }
            }

            // Keep optional transient notes quiet — never a heuristic wall.
            val softNote = honestyNote?.takeIf {
                it.isNotBlank() &&
                    !it.contains("heuristic", ignoreCase = true) &&
                    !it.contains("LLM", ignoreCase = true) &&
                    !it.contains("SAID", ignoreCase = true)
            }
            if (softNote != null && draft.phase == BuilderPhase.ENTER_TEXT) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = softNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Warm
                )
            }
        }
    }
}

@Composable
private fun SoftShapeConfirm(
    draft: BuilderDraft,
    showOptions: Boolean,
    onPickShape: (String) -> Unit,
    onKeepSuggested: () -> Unit,
    onRequestChange: () -> Unit = {},
    compact: Boolean = false
) {
    val tag = draft.shapeTag ?: draft.suggestedShapeTag
    val label = IntentionBuilder.shapeDisplayLabel(tag)
    if (compact) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "We’ve treated this as a ${label.lowercase()} intention — change?",
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onRequestChange)
                    .padding(vertical = 4.dp)
            )
        }
        return
    }
    if (!showOptions) return
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "What kind of intention is this?",
        style = MaterialTheme.typography.titleMedium,
        color = IntentColors.Ink,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = draft.statement.ifBlank { draft.freeText },
        style = MaterialTheme.typography.bodyMedium,
        color = IntentColors.Muted
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Suggested",
        style = MaterialTheme.typography.labelMedium,
        color = IntentColors.Accent,
        fontWeight = FontWeight.SemiBold
    )
    Spacer(modifier = Modifier.height(8.dp))
    IntentionBuilder.shapeChoices().forEach { (choiceTag, choiceLabel) ->
        val suggested = choiceTag == draft.suggestedShapeTag
        if (suggested) {
            IntentPrimaryButton(
                text = choiceLabel,
                onClick = { onPickShape(choiceTag) }
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
    Text(
        text = "Other options",
        style = MaterialTheme.typography.labelMedium,
        color = IntentColors.Muted
    )
    Spacer(modifier = Modifier.height(8.dp))
    IntentionBuilder.shapeChoices().forEach { (choiceTag, choiceLabel) ->
        if (choiceTag != draft.suggestedShapeTag) {
            IntentSecondaryButton(
                text = choiceLabel,
                onClick = { onPickShape(choiceTag) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
    if (draft.phase == BuilderPhase.PICK_SHAPE) {
        Spacer(modifier = Modifier.height(4.dp))
        IntentQuietButton(text = "Keep suggestion", onClick = onKeepSuggested)
    }
}

@Composable
private fun BuilderProgressDots(current: Int, total: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(total) { index ->
            val active = index < current
            Box(
                modifier = Modifier
                    .size(if (active) 10.dp else 8.dp)
                    .background(
                        color = if (active) IntentColors.Accent else Color(0xFFE4DFD4),
                        shape = CircleShape
                    )
            )
            if (index < total - 1) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 6.dp)
                        .height(1.dp)
                        .weight(1f, fill = false)
                        .fillMaxWidth(0.08f)
                        .background(Color(0xFFE4DFD4))
                )
            }
        }
    }
}

@Composable
private fun BuilderPanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = Color(0x125459C9),
                spotColor = Color(0x125459C9)
            )
            .border(1.dp, IntentColors.Line, RoundedCornerShape(20.dp))
            .background(IntentColors.Card, RoundedCornerShape(20.dp))
            .padding(17.dp),
        content = { content() }
    )
}
