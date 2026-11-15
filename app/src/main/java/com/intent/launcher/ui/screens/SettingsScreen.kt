package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.launcher.store.SnapshotExport
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

/**
 * Settings — local snapshot export honesty (OS-004 / v0.0.107).
 *
 * Export shares IntentionFlowStore JSON via the system share sheet.
 * Copy is explicit: local device only; not cloud backup; no import yet.
 * App connections stay on their own screen (v0.0.59).
 */
@Composable
fun SettingsScreen(
    versionName: String,
    schemaVersion: Int,
    onExportSnapshot: () -> Unit,
    onOpenConnections: () -> Unit = {},
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
        ScreenTopBar(contextLabel = "Settings", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede(
            "Local device tools only. Connections inventory partners; " +
                "snapshot export shares a file you save yourself."
        )
        Spacer(modifier = Modifier.height(22.dp))
        SectionTitle("App connections")
        Spacer(modifier = Modifier.height(10.dp))
        IntentSecondaryButton(
            text = "Open connections",
            onClick = onOpenConnections
        )
        Spacer(modifier = Modifier.height(28.dp))
        SectionTitle("Local snapshot")
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = SnapshotExport.HONESTY_NOTE,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(16.dp))
        IntentPrimaryButton(
            text = "Export snapshot",
            onClick = onExportSnapshot
        )
        Spacer(modifier = Modifier.height(10.dp))
        SmallLabel(
            text = "Import and restore are not available yet.",
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(28.dp))
        SectionTitle("About")
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "INTENT v$versionName · schema $schemaVersion",
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Data stays on this device until you export it.",
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Muted
        )
    }
}
