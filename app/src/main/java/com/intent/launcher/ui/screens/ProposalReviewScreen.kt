package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.Proposal
import com.intent.launcher.domain.ProposalStatus
import com.intent.launcher.ui.components.Callout
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.LineRow
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.components.SoftCard
import com.intent.launcher.ui.theme.IntentColors

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
    val pending = proposal?.status == ProposalStatus.PENDING
    val dayName = proposal?.let { dayName(it.proposedDayOfWeek) } ?: "—"
    val timeLabel = proposal?.let { "%02d:%02d".format(it.proposedHour, it.proposedMinute) } ?: "—"

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
            ScreenTopBar(contextLabel = "Review", onBack = onBack)
            Spacer(modifier = Modifier.height(23.dp))

            if (proposal == null) {
                SmallLabel(text = "No commitment yet")
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = "First, choose a direction.",
                    style = MaterialTheme.typography.displayLarge,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(12.dp))
                Lede("INTENT can help compare approaches before it proposes a routine.")
                Spacer(modifier = Modifier.height(26.dp))
                SoftCard {
                    SectionTitle("Possible approaches")
                    Spacer(modifier = Modifier.height(4.dp))
                    LineRow(
                        title = "Specialist app",
                        subtitle = "Structured lessons; INTENT manages the habit",
                        showArrow = false,
                        glyphIcon = Icons.Outlined.Widgets
                    )
                    LineRow(
                        title = "Tutor or conversation partner",
                        subtitle = "Real speaking practice with a person",
                        showArrow = false,
                        glyphIcon = Icons.Outlined.School
                    )
                    LineRow(
                        title = "Practise with your agent",
                        subtitle = "Conversations tailored to your level",
                        showArrow = false,
                        glyphIcon = Icons.Outlined.AutoAwesome
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Callout(text = "Choosing an approach will not create a schedule until you approve one.")
            } else {
                SmallLabel(
                    text = if (pending) "Your approval required" else "Reviewed",
                    color = if (pending) IntentColors.Section else IntentColors.Accent
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = if (pending) "Make this part of your week?" else proposal.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(12.dp))
                Lede(
                    if (pending) "You can change the details now or later."
                    else proposal.summary
                )
                Spacer(modifier = Modifier.height(26.dp))

                SoftCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(IntentColors.SuccessSoft, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.School,
                                contentDescription = null,
                                tint = IntentColors.Ink,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = proposal.title,
                            style = MaterialTheme.typography.headlineSmall,
                            color = IntentColors.Ink
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    ApprovalGrid(
                        day = "Every $dayName",
                        time = timeLabel,
                        duration = "60 minutes",
                        source = "You told INTENT"
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                Callout(
                    title = "What changes",
                    text = "This session can appear on Home when it is relevant. INTENT can ask how it went afterward. No tutor or calendar service will be contacted."
                )

                if (relatedFacts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    SectionTitle("Based on your facts")
                    relatedFacts.forEach { f ->
                        Text(
                            text = "• ${f.text}",
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Muted,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }

                if (pending) {
                    Spacer(modifier = Modifier.height(16.dp))
                    IntentSecondaryButton(
                        text = "Edit the details",
                        onClick = onEdit,
                        accentOutline = true
                    )
                }
            }
        }

        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(IntentColors.Line)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(IntentColors.NavBar)
                    .padding(horizontal = 17.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                if (proposal == null) {
                    IntentPrimaryButton(text = "Discuss the options", onClick = onBack)
                    IntentQuietButton(text = "Return to intention", onClick = onBack)
                } else if (pending) {
                    IntentPrimaryButton(text = "Approve weekly session", onClick = onApprove)
                    IntentQuietButton(text = "Not now", onClick = onDecline)
                } else {
                    IntentQuietButton(text = "Return", onClick = onBack)
                }
            }
        }
    }
}

@Composable
private fun ApprovalGrid(
    day: String,
    time: String,
    duration: String,
    source: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            GridCell(
                icon = Icons.Outlined.CalendarMonth,
                label = "Day",
                value = day,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(72.dp)
                    .background(IntentColors.Line)
            )
            GridCell(
                icon = Icons.Outlined.AccessTime,
                label = "Time",
                value = time,
                modifier = Modifier.weight(1f)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(IntentColors.Line)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            GridCell(
                icon = Icons.Outlined.HourglassBottom,
                label = "Duration",
                value = duration,
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(72.dp)
                    .background(IntentColors.Line)
            )
            GridCell(
                icon = Icons.Outlined.PersonOutline,
                label = "Source",
                value = source,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun GridCell(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(IntentColors.GridCell)
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = IntentColors.Ink,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = IntentColors.Muted,
                letterSpacing = 0.8.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = IntentColors.Ink
        )
    }
}

private fun dayName(isoDay: Int): String =
    listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        .getOrElse(isoDay) { "Day $isoDay" }
