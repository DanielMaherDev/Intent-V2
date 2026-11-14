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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind

@Composable
fun HomeScreen(
    nextAction: NextAction,
    onContinueExploring: () -> Unit,
    onOpenIntention: () -> Unit,
    onReviewProposal: () -> Unit,
    onBegin: () -> Unit,
    onDefer: () -> Unit,
    onDiscuss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp, vertical = 40.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(
                text = "INTENT",
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Where would you like to begin?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(20.dp))
                .padding(24.dp)
        ) {
            Text(
                text = nextAction.label,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = nextAction.detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
            )
            Spacer(modifier = Modifier.height(20.dp))
            when (nextAction.kind) {
                NextActionKind.CONTINUE_EXPLORING -> {
                    Button(onClick = onContinueExploring, modifier = Modifier.fillMaxWidth()) {
                        Text("Continue exploring")
                    }
                    TextButton(onClick = onOpenIntention) {
                        Text("Open your intention")
                    }
                    Text(
                        text = "Learn Spanish · Speak Spanish when ready",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                NextActionKind.REVIEW_PROPOSAL -> {
                    Button(onClick = onReviewProposal, modifier = Modifier.fillMaxWidth()) {
                        Text("Review weekly session")
                    }
                }
                NextActionKind.BEGIN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(onClick = onBegin, modifier = Modifier.weight(1f)) {
                            Text(nextAction.beginLabel ?: "Begin")
                        }
                        OutlinedButton(onClick = onDefer, modifier = Modifier.weight(1f)) {
                            Text(nextAction.deferLabel ?: "Later")
                        }
                        TextButton(onClick = onDiscuss) {
                            Text(nextAction.discussLabel ?: "Discuss")
                        }
                    }
                }
                NextActionKind.DISCUSS -> {
                    Button(onClick = onDiscuss, modifier = Modifier.fillMaxWidth()) {
                        Text("Discuss")
                    }
                }
                else -> {
                    Button(onClick = onOpenIntention, modifier = Modifier.fillMaxWidth()) {
                        Text("Open your intention")
                    }
                }
            }
        }
    }
}
