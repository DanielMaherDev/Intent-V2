package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.Proposal

@Composable
fun ProposalReviewScreen(
    proposal: Proposal?,
    relatedFacts: List<Fact>,
    onApprove: () -> Unit,
    onEdit: () -> Unit,
    onDecline: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp)
    ) {
        TextButton(onClick = onBack) { Text("Back") }
        Text(
            text = "Proposal review",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        if (proposal == null) {
            Text("No pending proposal.")
            return
        }
        Text(proposal.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(proposal.summary, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Proposed: day ${proposal.proposedDayOfWeek} at %02d:%02d".format(
                proposal.proposedHour,
                proposal.proposedMinute
            ),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Distinct user fact(s)", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        relatedFacts.forEach { f ->
            Text("• ${f.text}", modifier = Modifier.padding(vertical = 4.dp))
        }
        Spacer(modifier = Modifier.height(28.dp))
        Button(onClick = onApprove, modifier = Modifier.fillMaxWidth()) {
            Text("Approve weekly session")
        }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) {
            Text("Edit")
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDecline, modifier = Modifier.fillMaxWidth()) {
            Text("Decline")
        }
    }
}
