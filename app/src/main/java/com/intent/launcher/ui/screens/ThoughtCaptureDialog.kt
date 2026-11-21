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
import com.intent.launcher.ui.theme.IntentColors

/**
 * Quick thought sheet — raw text only. Optional voice is started by the host.
 * No LLM summary on save.
 */
@Composable
fun ThoughtCaptureDialog(
    voiceAvailable: Boolean,
    onDismiss: () -> Unit,
    onSaveText: (String) -> Unit,
    onCaptureVoice: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Capture thought",
                style = MaterialTheme.typography.titleLarge,
                color = IntentColors.Ink
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Saved as your words — no summary. Classify later.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("What’s on your mind?") },
                    minLines = 3
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSaveText(draft) },
                enabled = draft.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            if (voiceAvailable) {
                TextButton(onClick = onCaptureVoice) {
                    Text("Speak")
                }
            }
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
