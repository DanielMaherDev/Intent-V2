package com.intent.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.ScreenTimeBar
import com.intent.launcher.ui.theme.IntentColors

/**
 * OS-136 dense top screen-time / focus chrome — above Home brand.
 * Independent of floating-agent overlay position and state.
 */
@Composable
fun ScreenTimeTopBar(
    content: ScreenTimeBar.Content,
    onGrantUsageAccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (content) {
        is ScreenTimeBar.Content.Hidden -> Unit
        is ScreenTimeBar.Content.NeedsPermission -> {
            ScreenTimeBarRow(
                text = content.text,
                contentDescription = content.contentDescription,
                actionLabel = if (content.canOpenSettings) "Grant" else null,
                onAction = if (content.canOpenSettings) onGrantUsageAccess else null,
                modifier = modifier
            )
        }
        is ScreenTimeBar.Content.FocusPackageToday -> {
            ScreenTimeBarRow(
                text = content.text,
                contentDescription = content.contentDescription,
                modifier = modifier
            )
        }
        is ScreenTimeBar.Content.DeviceForegroundToday -> {
            ScreenTimeBarRow(
                text = content.text,
                contentDescription = content.contentDescription,
                modifier = modifier
            )
        }
        is ScreenTimeBar.Content.GrantedNoData -> {
            ScreenTimeBarRow(
                text = content.text,
                contentDescription = content.contentDescription,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun ScreenTimeBarRow(
    text: String,
    contentDescription: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(12.dp))
            .background(IntentColors.Card, RoundedCornerShape(12.dp))
            .semantics { this.contentDescription = contentDescription }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Ink,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (actionLabel != null && onAction != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.labelMedium,
                color = IntentColors.AccentDark,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}
