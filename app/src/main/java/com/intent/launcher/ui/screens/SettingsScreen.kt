package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.intent.launcher.engine.ReducedMotionPreference
import com.intent.launcher.engine.ThemePreference
import com.intent.launcher.store.SnapshotExport
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

/**
 * Settings — S-SETTINGS target: Appearance, Motion, Assistant, Quiet hours,
 * History, Connected apps. No banned developer jargon on the primary surface.
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
    reducedMotionPreference: ReducedMotionPreference = ReducedMotionPreference.FOLLOW_SYSTEM,
    onReducedMotionPreferenceChange: (ReducedMotionPreference) -> Unit = {},
    animationBudgetStatusLabel: String = "Frame / jank gate unmet · no charts",
    animationBudgetHonestyNote: String = "",
    quietModeStatusLabel: String = "Not supported · quiet_mode",
    quietModeHonestyNote: String = "",
    showQuietSilenceToggle: Boolean = false,
    showOpenDndSettings: Boolean = true,
    onOpenDndSettings: () -> Unit = {},
    liveLlmKeyPresent: Boolean = false,
    liveLlmOptedIn: Boolean = false,
    liveLlmStatusLabel: String = "On-device replies",
    liveLlmHonestyNote: String = "",
    onLiveLlmOptInChange: (Boolean) -> Unit = {},
    onSaveRuntimeApiKey: (String) -> Unit = {},
    onClearRuntimeApiKey: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draftKey by remember { mutableStateOf("") }
    var showMore by remember { mutableStateOf(false) }
    // Unused honesty params kept for call-site compatibility; primary UI omits them (S-SETTINGS).
    @Suppress("UNUSED_VARIABLE")
    val _quietHonesty = quietModeHonestyNote
    @Suppress("UNUSED_VARIABLE")
    val _quietStatus = quietModeStatusLabel
    @Suppress("UNUSED_VARIABLE")
    val _animHonesty = animationBudgetHonestyNote
    @Suppress("UNUSED_VARIABLE")
    val _animStatus = animationBudgetStatusLabel
    @Suppress("UNUSED_VARIABLE")
    val _liveHonesty = liveLlmHonestyNote
    @Suppress("UNUSED_VARIABLE")
    val _liveStatus = liveLlmStatusLabel
    @Suppress("UNUSED_VARIABLE")
    val _schema = schemaVersion

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "Settings", onBack = onBack)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(22.dp))

        SectionTitle("Appearance")
        Spacer(modifier = Modifier.height(10.dp))
        PreferenceGroup {
            ThemePreference.entries.forEachIndexed { index, option ->
                val selected = themePreference == option
                val label = when (option) {
                    ThemePreference.SYSTEM -> "System"
                    ThemePreference.LIGHT -> "Light"
                    ThemePreference.DARK -> "Dark"
                    ThemePreference.HIGH_CONTRAST -> "High contrast"
                }
                PreferenceRow(
                    label = label,
                    selected = selected,
                    onClick = { onThemePreferenceChange(option) },
                    showDivider = index < ThemePreference.entries.lastIndex
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        SectionTitle("Motion")
        Spacer(modifier = Modifier.height(10.dp))
        PreferenceGroup {
            ReducedMotionPreference.entries.forEachIndexed { index, option ->
                val selected = reducedMotionPreference == option
                val label = when (option) {
                    ReducedMotionPreference.FOLLOW_SYSTEM -> "Match this phone"
                    ReducedMotionPreference.PREFER_REDUCED -> "Reduce motion"
                }
                PreferenceRow(
                    label = label,
                    selected = selected,
                    onClick = { onReducedMotionPreferenceChange(option) },
                    showDivider = index < ReducedMotionPreference.entries.lastIndex
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        SectionTitle("Assistant")
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Use a live assistant",
                    style = MaterialTheme.typography.bodyLarge,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Optional. Your key stays on this device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Muted
                )
            }
            Switch(
                checked = liveLlmOptedIn,
                onCheckedChange = onLiveLlmOptInChange,
                enabled = liveLlmKeyPresent || draftKey.isNotBlank() || liveLlmOptedIn
            )
        }
        if (liveLlmOptedIn) {
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = draftKey,
                onValueChange = { draftKey = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Key") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IntentColors.Accent,
                    unfocusedBorderColor = IntentColors.Line,
                    unfocusedContainerColor = IntentColors.Card,
                    focusedContainerColor = IntentColors.Card
                )
            )
            Spacer(modifier = Modifier.height(12.dp))
            IntentPrimaryButton(
                text = "Save",
                onClick = {
                    onSaveRuntimeApiKey(draftKey)
                    draftKey = ""
                }
            )
            if (liveLlmKeyPresent) {
                Spacer(modifier = Modifier.height(8.dp))
                IntentQuietLink(
                    text = "Remove key",
                    onClick = onClearRuntimeApiKey
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        SectionTitle("Quiet hours")
        Spacer(modifier = Modifier.height(10.dp))
        if (showOpenDndSettings) {
            ChevronRow(
                title = "Open Do Not Disturb",
                subtitle = "INTENT cannot silence notifications itself.",
                onClick = onOpenDndSettings
            )
        } else {
            Text(
                text = "INTENT cannot silence notifications itself.",
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        SectionTitle("History")
        Spacer(modifier = Modifier.height(10.dp))
        ChevronRow(
            title = "See history",
            subtitle = null,
            onClick = onOpenHistory
        )

        Spacer(modifier = Modifier.height(28.dp))
        SectionTitle("Connected apps")
        Spacer(modifier = Modifier.height(10.dp))
        ChevronRow(
            title = "Apps you use",
            subtitle = null,
            onClick = onOpenConnections
        )

        Spacer(modifier = Modifier.height(28.dp))
        IntentQuietLink(
            text = if (showMore) "Hide advanced" else "More options",
            onClick = { showMore = !showMore }
        )

        if (showMore) {
            Spacer(modifier = Modifier.height(22.dp))
            SectionTitle("Local snapshot")
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = SnapshotExport.HONESTY_NOTE,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
            Spacer(modifier = Modifier.height(12.dp))
            IntentPrimaryButton(
                text = "Export snapshot",
                onClick = onExportSnapshot
            )
            Spacer(modifier = Modifier.height(10.dp))
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
            if (showQuietSilenceToggle) {
                Spacer(modifier = Modifier.height(14.dp))
                SmallLabel(
                    text = "Silence toggle is not available in this build.",
                    color = IntentColors.Warm
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "INTENT v$versionName",
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Muted
        )
    }
}

@Composable
private fun PreferenceGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp)),
        content = { content() }
    )
}

@Composable
private fun PreferenceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    showDivider: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = IntentColors.Ink,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Selected",
                tint = IntentColors.Accent
            )
        }
    }
    if (showDivider) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .padding(horizontal = 16.dp)
                .background(IntentColors.Line)
        )
    }
}

@Composable
private fun ChevronRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(16.dp))
            .background(IntentColors.Card, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = IntentColors.Ink
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = IntentColors.Muted
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = IntentColors.Muted
        )
    }
}

@Composable
private fun IntentQuietLink(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = IntentColors.AccentDark,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    )
}
