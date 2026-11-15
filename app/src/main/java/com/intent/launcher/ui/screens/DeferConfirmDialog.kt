package com.intent.launcher.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.launcher.engine.DeferralPreview
import com.intent.launcher.ui.theme.IntentColors

/**
 * Confirm deferral after preview — commit uses [FlowCommand.DeferNextSession].
 */
@Composable
fun DeferConfirmDialog(
    preview: DeferralPreview.Preview,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Defer this session?",
                style = MaterialTheme.typography.titleLarge,
                color = IntentColors.Ink
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = preview.sessionTitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "From ${preview.fromLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Muted
                )
                Text(
                    text = "To ${preview.toLabel}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(10.dp))
                if (preview.affectedSessions.isEmpty()) {
                    Text(
                        text = "No other sessions on this intention.",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                } else {
                    Text(
                        text = "Other sessions (unchanged):",
                        style = MaterialTheme.typography.bodySmall,
                        color = IntentColors.Muted
                    )
                    preview.affectedSessions.forEach { sibling ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "· ${sibling.title} — ${sibling.whenLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Ink
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = preview.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Defer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
