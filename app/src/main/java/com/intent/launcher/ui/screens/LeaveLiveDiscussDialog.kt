package com.intent.launcher.ui.screens

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import com.intent.launcher.ui.theme.IntentColors

/**
 * OS-144 — when Discuss/voice engagement is active, back must not silently hang up.
 * Minimize leaves the Conversation UI (floater / Home remain); End clears the engagement.
 */
@Composable
fun LeaveLiveDiscussDialog(
    onMinimize: () -> Unit,
    onEnd: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Leave Discuss?",
                style = MaterialTheme.typography.titleLarge,
                color = IntentColors.Ink
            )
        },
        text = {
            Text(
                text = "Voice or live Discuss is still active. Minimize keeps it going " +
                    "(Home / companion). End stops this engagement. Back never hangs up silently.",
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
        },
        confirmButton = {
            TextButton(onClick = onMinimize) {
                Text("Minimize")
            }
        },
        dismissButton = {
            TextButton(onClick = onEnd) {
                Text("End")
            }
        }
    )
}
