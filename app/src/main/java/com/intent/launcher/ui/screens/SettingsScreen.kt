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
import com.intent.launcher.engine.QuietModeProbe
import com.intent.launcher.engine.ThemePreference
import com.intent.launcher.store.SnapshotExport
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

/**
 * Settings — appearance theme + quiet-mode honesty + snapshot export/import + History +
 * live OpenAI Discuss (key never shown in full). Theme prefs are SharedPreferences only —
 * not store/SCHEMA. Quiet mode never invents a silence Switch while NOT_SUPPORTED.
 */
@Composable
fun SettingsScreen(
    versionName: String,
    schemaVersion: Int,
    onExportSnapshot: () -> Unit,
    onImportSnapshot: () -> Unit = {},
    snapshotImportNote: String? = null,
    onOpenHistory: () -> Unit = {},
    onOpenConnections: () -> Unit = {},
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    onThemePreferenceChange: (ThemePreference) -> Unit = {},
    quietModeStatusLabel: String = "Not supported · quiet_mode",
    quietModeHonestyNote: String = "",
    showQuietSilenceToggle: Boolean = false,
    showOpenDndSettings: Boolean = true,
    onOpenDndSettings: () -> Unit = {},
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
                "snapshot export/import stays on this device. " +
                "Live OpenAI stays off until you opt in with a key on this device."
        )
        Spacer(modifier = Modifier.height(22.dp))

        SectionTitle("Appearance")
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Follow system, or lock light / dark. Dark tokens invert Intent’s " +
                "warm paper + indigo language — not a separate brand.",
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(12.dp))
        ThemePreference.entries.forEach { option ->
            val selected = themePreference == option
            val label = when (option) {
                ThemePreference.SYSTEM -> "System (follow device)"
                ThemePreference.LIGHT -> "Light"
                ThemePreference.DARK -> "Dark"
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (selected) {
                IntentPrimaryButton(
                    text = label,
                    onClick = { onThemePreferenceChange(option) }
                )
            } else {
                IntentSecondaryButton(
                    text = label,
                    onClick = { onThemePreferenceChange(option) }
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        SmallLabel(
            text = "Stored on device only · does not change schema or snapshot data",
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle(QuietModeProbe.SETTINGS_SECTION_TITLE)
        Spacer(modifier = Modifier.height(10.dp))
        SmallLabel(
            text = quietModeStatusLabel,
            color = IntentColors.AccentDark
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = quietModeHonestyNote.ifBlank {
                "INTENT cannot silence all apps. quiet_mode stays not supported — " +
                    "no Settings silence toggle. Open system Do Not Disturb for device quiet."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Muted
        )
        // Honesty: no silence Switch while quiet_mode is NOT_SUPPORTED (showQuietSilenceToggle=false).
        if (showQuietSilenceToggle) {
            Spacer(modifier = Modifier.height(8.dp))
            SmallLabel(
                text = "Silence toggle reserved for a future confirmed quiet adapter — not this build.",
                color = IntentColors.Warm
            )
        }
        if (showOpenDndSettings) {
            Spacer(modifier = Modifier.height(12.dp))
            IntentSecondaryButton(
                text = "Open system Do Not Disturb",
                onClick = onOpenDndSettings
            )
            Spacer(modifier = Modifier.height(6.dp))
            SmallLabel(
                text = "System DND only — INTENT does not claim silence was applied.",
                color = IntentColors.Muted
            )
        }
        Spacer(modifier = Modifier.height(28.dp))

        SectionTitle("Local history")
        Spacer(modifier = Modifier.height(10.dp))
        IntentSecondaryButton(
            text = "Open History",
            onClick = onOpenHistory
        )
        Spacer(modifier = Modifier.height(28.dp))

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
        Text(
            text = SnapshotExport.IMPORT_HONESTY_NOTE,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(12.dp))
        IntentSecondaryButton(
            text = "Import snapshot",
            onClick = onImportSnapshot
        )
        if (!snapshotImportNote.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            SmallLabel(
                text = snapshotImportNote,
                color = IntentColors.Warm
            )
        }
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
