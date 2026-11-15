package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.domain.BuilderDraft
import com.intent.launcher.domain.BuilderPhase
import com.intent.launcher.engine.IntentionBuilder
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
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
    val prompt = IntentionBuilder.nextPrompt(draft)
    val paperWash = Brush.verticalGradient(
        colors = listOf(
            Color(0xFFF7F5EF),
            IntentColors.Paper,
            Color(0xFFF1EEE6)
        )
    )

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
            Spacer(modifier = Modifier.height(23.dp))
            SmallLabel(text = "New intention", color = IntentColors.Accent)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Start an intention",
                style = MaterialTheme.typography.displayLarge,
                color = IntentColors.Ink,
                lineHeight = 38.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Lede(
                "Describe what you want. INTENT suggests a shape with labeled heuristics — " +
                    "not a live LLM — then asks only a couple of useful questions."
            )
            Spacer(modifier = Modifier.height(18.dp))
            SmallLabel(text = draft.honestyNote.ifBlank { IntentionBuilder.HONESTY })

            when {
                !draft.active || draft.phase == BuilderPhase.ENTER_TEXT -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    BuilderPanel {
                        SectionTitle("What do you want to work on?")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = freeText,
                            onValueChange = { freeText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            placeholder = { Text("e.g. Speak Spanish more confidently at work") }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        IntentPrimaryButton(
                            text = "Continue",
                            onClick = { onStart(freeText) }
                        )
                    }
                }

                draft.phase == BuilderPhase.PICK_SHAPE -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    BuilderPanel {
                        SectionTitle("Suggested shape: ${draft.suggestedShapeTag}")
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = draft.statement,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IntentColors.Muted,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        IntentionBuilder.shapeChoices().forEach { (tag, label) ->
                            IntentSecondaryButton(
                                text = if (tag == draft.suggestedShapeTag) "$label (suggested)" else label,
                                onClick = { onPickShape(tag) },
                                accentOutline = tag == draft.suggestedShapeTag
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }
                }

                draft.phase == BuilderPhase.MISSING_FACTS || draft.phase == BuilderPhase.CONFIRM -> {
                    Spacer(modifier = Modifier.height(16.dp))
                    BuilderPanel {
                        SectionTitle(draft.title.ifBlank { "Your intention" })
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Shape · ${draft.shapeTag ?: draft.suggestedShapeTag}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Muted
                        )
                        if (prompt != null && draft.phase == BuilderPhase.MISSING_FACTS) {
                            Spacer(modifier = Modifier.height(16.dp))
                            SmallLabel(text = "The question now", color = IntentColors.Accent)
                            Spacer(modifier = Modifier.height(7.dp))
                            Text(
                                text = prompt.question,
                                style = MaterialTheme.typography.headlineSmall,
                                color = IntentColors.Ink
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = answerText,
                                onValueChange = { answerText = it },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            IntentPrimaryButton(
                                text = "Save answer",
                                onClick = {
                                    onAnswerPrompt(prompt.id, answerText)
                                    answerText = ""
                                }
                            )
                        } else {
                            Spacer(modifier = Modifier.height(16.dp))
                            SectionTitle("Confirm understanding")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = draft.statement,
                                style = MaterialTheme.typography.bodyMedium,
                                color = IntentColors.Ink,
                                lineHeight = 19.sp
                            )
                            draft.factAnswers.forEach { (_, answer) ->
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "• $answer",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = IntentColors.Muted
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Facts are stored as SAID when you confirm. " +
                                    "No INFERRED commitments are auto-written.",
                                style = MaterialTheme.typography.bodySmall,
                                color = IntentColors.Muted
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IntentPrimaryButton(
                                    text = "Confirm intention",
                                    onClick = onCommit,
                                    modifier = Modifier.weight(1.4f)
                                )
                            }
                        }
                    }
                }
            }

            if (honestyNote != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = honestyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Warm
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            IntentQuietButton(text = "Cancel builder", onClick = onCancel)
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
