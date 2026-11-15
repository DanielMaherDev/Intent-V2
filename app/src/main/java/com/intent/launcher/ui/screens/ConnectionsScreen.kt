package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.AppConnection
import com.intent.launcher.domain.AppConnectionStatus
import com.intent.launcher.engine.AppConnectionUsage
import com.intent.launcher.engine.PlayStoreDeepLink
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

/**
 * Settings → Connections (OS-157–159 + light OS-164) + Home chrome (OS-136).
 * Inventory is PackageManager-backed; partner feeds stay NOT_SUPPORTED.
 */
@Composable
fun ConnectionsScreen(
    usages: List<AppConnectionUsage.ConnectionUsage>,
    unlinkNote: String? = null,
    onUnlink: (connectionId: String) -> Unit,
    onOpenPlayStore: (packageName: String, label: String) -> Unit,
    screenTimeBarVisible: Boolean = true,
    onScreenTimeBarVisibleChange: (Boolean) -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "Connections", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        Text(
            text = "App connections",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede(
            "Installed candidates from PackageManager only — never invented. " +
                "Partner OAuth (Spotify, Chess.com, Strava) is not supported in this build."
        )
        if (!unlinkNote.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = unlinkNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Home chrome")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Top screen-time bar is independent of the floating agent. " +
                "Without usage access it shows an honest grant path — never invented minutes.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(10.dp))
        IntentSecondaryButton(
            text = if (screenTimeBarVisible) {
                "Hide screen-time bar"
            } else {
                "Show screen-time bar"
            },
            onClick = { onScreenTimeBarVisibleChange(!screenTimeBarVisible) }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (screenTimeBarVisible) {
                "Currently shown on Home above the brand."
            } else {
                "Currently hidden. Session timers on the attention strip stay available."
            },
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("Candidates")
        Spacer(modifier = Modifier.height(12.dp))
        if (usages.isEmpty()) {
            EmptyNote(text = "No connection candidates configured.")
        } else {
            usages.forEach { usage ->
                ConnectionRow(
                    usage = usage,
                    onUnlink = { onUnlink(usage.connection.id) },
                    onOpenPlayStore = {
                        onOpenPlayStore(usage.connection.packageName, usage.connection.label)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ConnectionRow(
    usage: AppConnectionUsage.ConnectionUsage,
    onUnlink: () -> Unit,
    onOpenPlayStore: () -> Unit
) {
    val connection = usage.connection
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Text(
            text = connection.label,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(3.dp))
        SmallLabel(
            text = statusLine(connection),
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = connection.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = connection.limitNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(10.dp))
        if (usage.intentions.isEmpty()) {
            EmptyNote(
                text = usage.emptyNote
                    ?: "No intentions use this connection. Partner feed empty — SAID / OBSERVED only."
            )
        } else {
            SmallLabel(text = "Used by", color = IntentColors.Section)
            Spacer(modifier = Modifier.height(6.dp))
            usage.intentions.forEach { use ->
                Text(
                    text = "${use.intentionTitle} — ${use.detail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            usage.emptyNote?.let { note ->
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (!connection.installed) {
            val offer = PlayStoreDeepLink.forPackage(connection.packageName, connection.label)
            IntentSecondaryButton(
                text = "Open on ${offer.sourceLabel}",
                onClick = onOpenPlayStore
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = offer.note,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        } else {
            IntentQuietButton(text = "Unlink", onClick = onUnlink)
        }
    }
}

private fun statusLine(connection: AppConnection): String {
    val install = if (connection.installed) "Installed" else "Not installed"
    val account = when (connection.accountStatus) {
        AppConnectionStatus.DISCONNECTED -> "Disconnected"
        AppConnectionStatus.NOT_SUPPORTED -> "Not supported"
        AppConnectionStatus.LINKED -> "Linked"
    }
    return "$install · $account · ${connection.capabilityKey}"
}
