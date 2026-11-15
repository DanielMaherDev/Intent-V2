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
import com.intent.launcher.engine.ErrandCapture
import com.intent.launcher.ui.theme.IntentColors

/**
 * Quick errand sheet — lightweight to-do, not Intention builder.
 * Parses first action + optional reminder (e.g. “Buy milk tonight”).
 */
@Composable
fun ErrandTodoDialog(
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val preview = remember(draft) {
        val t = draft.trim()
        if (t.isEmpty()) null else runCatching { ErrandCapture.parse(t) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Quick to-do",
                style = MaterialTheme.typography.titleLarge,
                color = IntentColors.Ink
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Small errand with an optional reminder — not an exploring intention.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buy milk tonight") },
                    minLines = 2
                )
                if (preview != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "First action: ${preview.firstAction}" +
                            (preview.reminderNote?.let { " · Reminder: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Ink
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = preview.honestyLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(draft) },
                enabled = draft.isNotBlank()
            ) {
                Text("Save to-do")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
