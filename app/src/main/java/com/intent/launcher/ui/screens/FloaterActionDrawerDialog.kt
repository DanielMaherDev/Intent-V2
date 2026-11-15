package com.intent.launcher.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import com.intent.launcher.engine.FloaterActionDrawer
import com.intent.launcher.engine.FloaterDrawerAction
import com.intent.launcher.ui.theme.IntentColors

/**
 * Long-press floater menu — Ask / Today / Capture. Same agent; no second bot.
 */
@Composable
fun FloaterActionDrawerDialog(
    onDismiss: () -> Unit,
    onAction: (FloaterDrawerAction) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "INTENT",
                style = MaterialTheme.typography.titleLarge,
                color = IntentColors.Ink
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Same agent as Conversation — pick one.",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
                Spacer(modifier = Modifier.height(8.dp))
                FloaterActionDrawer.items.forEach { item ->
                    TextButton(
                        onClick = { onAction(item.action) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.titleMedium,
                                color = IntentColors.Ink
                            )
                            Text(
                                text = item.detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = IntentColors.Muted
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
