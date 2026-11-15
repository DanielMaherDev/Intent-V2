package com.intent.launcher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.ThoughtCapture
import com.intent.launcher.domain.ThoughtDisposition
import com.intent.launcher.engine.EveningThoughtTriage
import com.intent.launcher.ui.theme.IntentColors

/**
 * Evening check-out thought triage — one RAW SAID thought at a time.
 * Disposition is user-picked only (OS-154).
 */
@Composable
fun EveningThoughtTriageSheet(
    thought: ThoughtCapture,
    remainingCount: Int,
    onClassify: (ThoughtDisposition) -> Unit,
    onSkipRest: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onSkipRest,
        title = {
            Text(
                text = "Classify thought",
                style = MaterialTheme.typography.titleLarge,
                color = IntentColors.Ink
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = EveningThoughtTriage.HONESTY,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
                if (remainingCount > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$remainingCount remaining",
                        style = MaterialTheme.typography.labelSmall,
                        color = IntentColors.Muted
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = thought.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = IntentColors.Ink
                )
            }
        },
        confirmButton = {
            Column {
                TextButton(onClick = { onClassify(ThoughtDisposition.TASK) }) {
                    Text("Task")
                }
                TextButton(onClick = { onClassify(ThoughtDisposition.INTENTION) }) {
                    Text("Intention")
                }
                TextButton(onClick = { onClassify(ThoughtDisposition.REMINDER) }) {
                    Text("Reminder")
                }
                TextButton(onClick = { onClassify(ThoughtDisposition.KEEP) }) {
                    Text("Keep")
                }
                TextButton(onClick = { onClassify(ThoughtDisposition.DISCARD) }) {
                    Text("Discard")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onSkipRest) {
                Text("Finish later")
            }
        }
    )
}

/**
 * Optional what-happened step before evening thought triage / Keep·Defer.
 */
@Composable
fun CheckOutWhatHappenedDialog(
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var draft by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "What happened today?",
                style = MaterialTheme.typography.titleLarge,
                color = IntentColors.Ink
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Saved as SAID — your words, no invented summary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Optional — leave blank to use today’s session note") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(draft) }) {
                Text("Continue")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
