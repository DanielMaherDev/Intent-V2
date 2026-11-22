package com.intent.launcher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
 * Offers checklist vs reminder for any small errand (OTHER §1).
 */
@Composable
fun ErrandTodoDialog(
    onDismiss: () -> Unit,
    onSave: (text: String, mode: ErrandCapture.CaptureMode) -> Unit,
    initialText: String = ""
) {
    var draft by remember { mutableStateOf(initialText) }
    var mode by remember { mutableStateOf(ErrandCapture.CaptureMode.CHECKLIST) }
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
                            (preview.reminderNote?.let { " · Due: $it" } ?: ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Ink
                    )
                    if (preview.checklistLabels.size >= 2) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Checklist: ${preview.checklistLabels.joinToString(", ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Ink
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = ErrandCapture.offerBody(preview),
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row {
                        TextButton(
                            onClick = { mode = ErrandCapture.CaptureMode.CHECKLIST }
                        ) {
                            Text(
                                text = if (mode == ErrandCapture.CaptureMode.CHECKLIST) {
                                    "✓ ${ErrandCapture.CHIP_CHECKLIST}"
                                } else {
                                    ErrandCapture.CHIP_CHECKLIST
                                }
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(
                            onClick = { mode = ErrandCapture.CaptureMode.REMINDER }
                        ) {
                            Text(
                                text = if (mode == ErrandCapture.CaptureMode.REMINDER) {
                                    "✓ ${ErrandCapture.CHIP_REMINDER}"
                                } else {
                                    ErrandCapture.CHIP_REMINDER
                                }
                            )
                        }
                    }
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
                onClick = { onSave(draft, mode) },
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
