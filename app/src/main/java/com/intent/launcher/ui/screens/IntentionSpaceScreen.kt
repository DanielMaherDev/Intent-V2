package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.Hypothesis
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.ScheduledSession

@Composable
fun IntentionSpaceScreen(
    intention: Intention?,
    facts: List<Fact>,
    hypotheses: List<Hypothesis>,
    sessions: List<ScheduledSession>,
    onWorkThisOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(28.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back to Home") }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = intention?.title ?: "Intention",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = intention?.statement ?: "",
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Phase: ${intention?.phase?.name ?: "—"}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(text = "What you said", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (facts.isEmpty()) {
            Text("No user facts yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            facts.forEach { f ->
                Text("• ${f.text} (${f.provenance.kind.name.lowercase()})", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Inferred", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (hypotheses.isEmpty()) {
            Text("Nothing inferred yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            hypotheses.forEach { h ->
                Text("• ${h.text} (${h.provenance.kind.name.lowercase()})", modifier = Modifier.padding(vertical = 4.dp))
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Schedule", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (sessions.isEmpty()) {
            Text("No approved sessions yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            sessions.forEach { s ->
                val defer = s.deferredUntilEpochMs?.let { " · deferred" } ?: ""
                Text("• ${s.title} (dow ${s.dayOfWeek} ${"%02d:%02d".format(s.hour, s.minute)}$defer)")
            }
        }
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onWorkThisOut, modifier = Modifier.fillMaxWidth()) {
            Text("Work this out")
        }
    }
}
