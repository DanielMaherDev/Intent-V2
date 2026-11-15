package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.TriageItem
import com.intent.launcher.domain.TriageItemStatus
import com.intent.launcher.domain.TriageState
import com.intent.launcher.engine.ThoughtTriage
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.components.SoftCard
import com.intent.launcher.ui.theme.IntentColors

@Composable
fun ThoughtTriageScreen(
    triage: TriageState,
    rawThoughtCount: Int,
    honestyNote: String?,
    onStartFromDump: (String) -> Unit,
    onStartFromRaw: () -> Unit,
    onConfirm: (String) -> Unit,
    onReject: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dump by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 19.dp, vertical = 15.dp)
        ) {
            ScreenTopBar(contextLabel = "Triage", onBack = onBack)
            Spacer(modifier = Modifier.height(23.dp))
            Text(
                text = "Triage thoughts",
                style = MaterialTheme.typography.displayLarge,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(10.dp))
            Lede(
                "Dump several goals. INTENT proposes ordered intentions with a first next action. " +
                    "Confirm or reject each — rejected stay as thought evidence only."
            )
            Spacer(modifier = Modifier.height(12.dp))
            SmallLabel(text = ThoughtTriage.HONESTY)

            if (!triage.active || triage.items.none { it.status == TriageItemStatus.PENDING }) {
                Spacer(modifier = Modifier.height(18.dp))
                SectionTitle("Paste multiple goals")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = dump,
                    onValueChange = { dump = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 4,
                    placeholder = {
                        Text("Learn Spanish\nTrain for a marathon\nExplore a business idea")
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
                IntentPrimaryButton(
                    text = "Propose intentions",
                    onClick = { onStartFromDump(dump) }
                )
                if (rawThoughtCount > 0) {
                    Spacer(modifier = Modifier.height(10.dp))
                    IntentSecondaryButton(
                        text = "Triage $rawThoughtCount raw thought${if (rawThoughtCount == 1) "" else "s"}",
                        onClick = onStartFromRaw
                    )
                }
            }

            if (triage.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(22.dp))
                SectionTitle("Proposed intentions")
                Spacer(modifier = Modifier.height(10.dp))
                triage.items.forEach { item ->
                    TriageItemCard(
                        item = item,
                        onConfirm = { onConfirm(item.id) },
                        onReject = { onReject(item.id) }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                IntentQuietButton(text = "Clear triage batch", onClick = onClear)
            }

            if (honestyNote != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = honestyNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Warm
                )
            }
        }
    }
}

@Composable
private fun TriageItemCard(
    item: TriageItem,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    SoftCard {
        SmallLabel(
            text = when (item.status) {
                TriageItemStatus.PENDING -> "Pending · ${item.suggestedShapeTag}"
                TriageItemStatus.CONFIRMED -> "Confirmed"
                TriageItemStatus.REJECTED -> "Rejected · kept as evidence"
            }
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleMedium,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.statement,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Next: ${item.suggestedNextAction}",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = item.honestyNote,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Section
        )
        if (item.status == TriageItemStatus.PENDING) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IntentPrimaryButton(
                    text = "Confirm",
                    onClick = onConfirm,
                    modifier = Modifier.weight(1.4f)
                )
                IntentSecondaryButton(
                    text = "Reject",
                    onClick = onReject,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
