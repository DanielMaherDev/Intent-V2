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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.AppConnection
import com.intent.launcher.domain.AppConnectionStatus
import com.intent.launcher.engine.AppConnectionUsage
import com.intent.launcher.engine.PlayStoreDeepLink
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.theme.IntentColors

/**
 * S-APPS — Apps list with honest capability captions.
 * Inventory is PackageManager-backed; partner feeds stay unsupported.
 */
@Composable
fun ConnectionsScreen(
    usages: List<AppConnectionUsage.ConnectionUsage>,
    unlinkNote: String? = null,
    onUnlink: (connectionId: String) -> Unit,
    onOpenPlayStore: (packageName: String, label: String) -> Unit,
    screenTimeBarVisible: Boolean = true,
    onScreenTimeBarVisibleChange: (Boolean) -> Unit = {},
    usageAccessGranted: Boolean = true,
    onAllowUsageAccess: (() -> Unit)? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "", onBack = onBack)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Apps",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Only apps this intention can actually use.",
            style = MaterialTheme.typography.bodyLarge,
            color = IntentColors.Muted
        )
        if (!unlinkNote.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = unlinkNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }

        if (!usageAccessGranted && onAllowUsageAccess != null) {
            Spacer(modifier = Modifier.height(20.dp))
            AppsUsageAccessRow(onAllow = onAllowUsageAccess)
        }

        Spacer(modifier = Modifier.height(20.dp))
        if (usages.isEmpty()) {
            Text(
                text = "Nothing to connect for this intention.",
                style = MaterialTheme.typography.bodyLarge,
                color = IntentColors.Muted
            )
        } else {
            usages.forEach { usage ->
                AppsConnectionRow(
                    usage = usage,
                    onUnlink = { onUnlink(usage.connection.id) },
                    onOpenPlayStore = {
                        onOpenPlayStore(usage.connection.packageName, usage.connection.label)
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        Spacer(modifier = Modifier.height(26.dp))
        Text(
            text = "Home",
            style = MaterialTheme.typography.titleMedium,
            color = IntentColors.Ink,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Top screen-time bar is optional. Without usage access it shows an honest grant path — never invented minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { onScreenTimeBarVisibleChange(!screenTimeBarVisible) }) {
            Text(
                text = if (screenTimeBarVisible) {
                    "Hide screen-time bar"
                } else {
                    "Show screen-time bar"
                },
                color = IntentColors.Accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AppsUsageAccessRow(onAllow: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Phone usage is off",
                style = MaterialTheme.typography.bodyLarge,
                color = IntentColors.Ink,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "INTENT can’t see time in apps.",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        TextButton(onClick = onAllow) {
            Text(
                text = "Allow",
                color = IntentColors.Accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun AppsConnectionRow(
    usage: AppConnectionUsage.ConnectionUsage,
    onUnlink: () -> Unit,
    onOpenPlayStore: () -> Unit
) {
    val connection = usage.connection
    val connected = connection.installed &&
        connection.accountStatus != AppConnectionStatus.DISCONNECTED
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = connection.label,
                style = MaterialTheme.typography.bodyLarge,
                color = IntentColors.Ink,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = appsCapabilityCaption(connection),
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
            if (usage.intentions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = usage.intentions.joinToString(" · ") { it.intentionTitle },
                    style = MaterialTheme.typography.labelSmall,
                    color = IntentColors.Muted
                )
            }
        }
        when {
            !connection.installed -> {
                TextButton(onClick = onOpenPlayStore) {
                    Text(
                        text = "Connect",
                        color = IntentColors.Accent,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            connected || connection.accountStatus == AppConnectionStatus.NOT_SUPPORTED -> {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (connection.accountStatus == AppConnectionStatus.NOT_SUPPORTED) {
                            "Not connected"
                        } else {
                            "Connected"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Muted
                    )
                    if (connection.installed) {
                        TextButton(onClick = onUnlink) {
                            Text(
                                text = "Unlink",
                                color = IntentColors.Muted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
            else -> {
                TextButton(onClick = onUnlink) {
                    Text(
                        text = "Connected",
                        color = IntentColors.Muted,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** DESIGN_SPEC S-APPS captions — exact data honesty. */
internal fun appsCapabilityCaption(connection: AppConnection): String {
    val key = connection.label.lowercase()
    return when {
        key.contains("duolingo") -> "Time open. Not lessons finished."
        key.contains("spotify") -> "What’s playing, if a device is active."
        key.contains("strava") ->
            if (connection.accountStatus == AppConnectionStatus.LINKED) {
                "Activities you grant."
            } else {
                "Activities you grant. Not connected."
            }
        key.contains("chess") -> "Rating, if the feed is current."
        key.contains("instagram") -> "Time open. Not which videos."
        else -> connection.limitNote.ifBlank {
            if (connection.installed) "Installed on this device." else "Not installed."
        }
    }
}
