package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HourglassBottom
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.TrackChanges
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
import com.intent.launcher.ui.components.SessionMetaCell
import com.intent.launcher.ui.components.SessionSummaryCard
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
    onKeepDiscussing: () -> Unit = onBack,
    modifier: Modifier = Modifier
) {
    val pending = proposal?.status == ProposalStatus.PENDING
    val days = proposal?.proposedDaysOfWeek?.ifEmpty {
        listOf(proposal.proposedDayOfWeek)
    }.orEmpty()
    val dayLabel = when {
        days.size == 7 -> "Everyday"
        days.isEmpty() -> "—"
        else -> days.sorted().joinToString(", ") { dayName(it) }
    }
    val timeLabel = proposal?.let { "%02d:%02d".format(it.proposedHour, it.proposedMinute) } ?: "—"
    val durationLabel = proposal?.let { "${it.durationMinutes} minutes" } ?: "—"

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
                    text = if (pending) "PROPOSAL · NOT YET ACTIVE" else "Reviewed",
                    color = if (pending) IntentColors.Warm else IntentColors.Accent
                )
                Spacer(modifier = Modifier.height(7.dp))
                Text(
                    text = if (pending) "Ready to begin?" else proposal.title,
                    style = MaterialTheme.typography.displayLarge,
                    color = IntentColors.Ink
                )
                Spacer(modifier = Modifier.height(12.dp))
                Lede(
                    if (pending) "Review your learning routine before activating it."
                    else proposal.summary
                )
                Spacer(modifier = Modifier.height(26.dp))

                SessionSummaryCard(
                    title = proposal.title,
                    headerIcon = Icons.Outlined.School,
                    cells = buildList {
                        if (proposal.methodsSummary.isNotBlank()) {
                            add(
                                SessionMetaCell(
                                    icon = Icons.Outlined.MenuBook,
                                    label = "Methods",
                                    value = proposal.methodsSummary
                                )
                            )
                        }
                        add(
                            SessionMetaCell(
                                icon = Icons.Outlined.CalendarMonth,
                                label = "Practice",
                                value = "$dayLabel · $durationLabel · $timeLabel"
                            )
                        )
                        add(
                            SessionMetaCell(
                                icon = Icons.Outlined.PhoneAndroid,
                                label = "App access",
                                value = proposal.capabilityHonesty.ifBlank {
                                    "Can open linked apps; progress inside apps needs your check-in."
                                }
                            )
                        )
                        add(
                            SessionMetaCell(
                                icon = Icons.Outlined.TrackChanges,
                                label = "Home",
                                value = proposal.homeImpactNote.ifBlank {
                                    "Appears on Home when relevant — only after approval succeeds."
                                }
                            )
                        )
                        add(
                            SessionMetaCell(
                                icon = Icons.Outlined.HourglassBottom,
                                label = "Timezone",
                                value = proposal.timeZoneId.ifBlank { "Device local" }
                            )
                        )
                        add(
                            SessionMetaCell(
                                icon = Icons.Outlined.AccessTime,
                                label = "Check-ins",
                                value = buildString {
                                    append("Morning: ")
                                    append(if (proposal.includeMorningCheckIn) "on" else "off")
                                    append(" · Evening: ")
                                    append(if (proposal.includeEveningReview) "on" else "off")
                                    append(" (independent)")
                                }
                            )
                        )
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))
                Callout(
                    title = "What changes",
                    text = "Nothing is active until approval succeeds at the gateway. " +
                        "Your answers stay saved even if you wait or keep discussing."
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
                        text = "Edit plan",
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
                    IntentPrimaryButton(text = "Start this routine", onClick = onApprove)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IntentQuietButton(
                            text = "Keep discussing",
                            onClick = onKeepDiscussing,
                            modifier = Modifier.weight(1f)
                        )
                        IntentQuietButton(
                            text = "Not now",
                            onClick = onDecline,
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else {
                    IntentQuietButton(text = "Return", onClick = onBack)
                }
            }
        }
    }
}

private fun dayName(isoDay: Int): String =
    listOf("", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
        .getOrElse(isoDay) { "Day $isoDay" }
