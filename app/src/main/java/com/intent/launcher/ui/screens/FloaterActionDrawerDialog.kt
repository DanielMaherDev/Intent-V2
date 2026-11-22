package com.intent.launcher.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.intent.launcher.engine.FloaterActionDrawer
import com.intent.launcher.engine.FloaterDrawerAction
import com.intent.launcher.ui.theme.IntentColors

/**
 * S-COMPANION — sheet (not a side stack). Five 56 dp rows with 24 dp icons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FloaterActionDrawerDialog(
    onDismiss: () -> Unit,
    onAction: (FloaterDrawerAction) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = IntentColors.Card
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "Companion",
                style = MaterialTheme.typography.titleMedium,
                color = IntentColors.Ink,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            FloaterActionDrawer.items.forEach { item ->
                CompanionSheetRow(
                    icon = iconFor(item.action),
                    label = item.label,
                    onClick = { onAction(item.action) }
                )
            }
        }
    }
}

@Composable
private fun CompanionSheetRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = IntentColors.Ink,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = IntentColors.Ink
        )
    }
}

private fun iconFor(action: FloaterDrawerAction): ImageVector =
    when (action) {
        FloaterDrawerAction.ASK -> Icons.Outlined.ChatBubbleOutline
        FloaterDrawerAction.TODAY -> Icons.Outlined.Home
        FloaterDrawerAction.CHECK_IN -> Icons.Outlined.Schedule
        FloaterDrawerAction.CHECK_OUT -> Icons.Outlined.Flag
        FloaterDrawerAction.CAPTURE -> Icons.Outlined.MicNone
        FloaterDrawerAction.HISTORY -> Icons.Outlined.Schedule
    }
