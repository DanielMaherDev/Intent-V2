package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
 * Settings — snapshot export + live OpenAI Discuss opt-in (key never shown in full).
 */
@Composable
fun SettingsScreen(
    versionName: String,
    schemaVersion: Int,
    onExportSnapshot: () -> Unit,
    onOpenConnections: () -> Unit = {},
    liveLlmKeyPresent: Boolean = false,
    liveLlmOptedIn: Boolean = false,
    liveLlmStatusLabel: String = "Local heuristics",
    liveLlmHonestyNote: String = "",
    onLiveLlmOptInChange: (Boolean) -> Unit = {},
    onSaveRuntimeApiKey: (String) -> Unit = {},
    onClearRuntimeApiKey: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftKey by remember { mutableStateOf("") }

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
                "snapshot export shares a file you save yourself. " +
                "Live OpenAI stays off until you opt in with a key on this device."
        )
        Spacer(modifier = Modifier.height(22.dp))

        SectionTitle("Live OpenAI Discuss")
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = liveLlmHonestyNote.ifBlank {
                "No key configured. Discuss uses labeled local heuristics."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(8.dp))
        SmallLabel(
            text = "Status · $liveLlmStatusLabel · key ${if (liveLlmKeyPresent) "present" else "missing"}",
            color = IntentColors.AccentDark
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Use live OpenAI",
                style = MaterialTheme.typography.bodyLarge,
                color = IntentColors.Ink,
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = liveLlmOptedIn,
                onCheckedChange = onLiveLlmOptInChange,
                enabled = liveLlmKeyPresent || draftKey.isNotBlank()
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        SmallLabel(
            text = "No network call until this is on and a key is available. " +
                "If a key was ever pasted in chat, rotate it at OpenAI and enter the new one here " +
                "(or via gitignored local.properties) — never paste secrets into chat again.",
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(14.dp))
        OutlinedTextField(
            value = draftKey,
            onValueChange = { draftKey = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Runtime OpenAI API key") },
            placeholder = { Text("Paste rotated key on this device only") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(10.dp))
        IntentPrimaryButton(
            text = "Save key on device",
            onClick = {
                onSaveRuntimeApiKey(draftKey)
                draftKey = ""
            }
        )
        Spacer(modifier = Modifier.height(8.dp))
        IntentSecondaryButton(
            text = "Clear on-device key",
            onClick = onClearRuntimeApiKey
        )

        Spacer(modifier = Modifier.height(28.dp))
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
