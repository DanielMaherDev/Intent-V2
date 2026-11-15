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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Spa
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.domain.SessionElapsed
import com.intent.launcher.ui.components.DateLabel
import com.intent.launcher.ui.components.GlyphTone
import com.intent.launcher.ui.components.HeroCard
import com.intent.launcher.ui.components.HeroTone
import com.intent.launcher.ui.components.HomeBottomNav
import com.intent.launcher.ui.components.HomeBrandRow
import com.intent.launcher.ui.components.IntentPill
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.IntentQuietButton
import com.intent.launcher.ui.components.IntentSecondaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.LineRow
import com.intent.launcher.ui.components.PillTone
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    nextAction: NextAction,
    intentionHint: String,
    intentionTitle: String,
    onContinueExploring: () -> Unit,
    onOpenIntention: () -> Unit,
    onReviewProposal: () -> Unit,
    onBegin: () -> Unit,
    onDefer: () -> Unit,
    onDiscuss: () -> Unit,
    onCompleteSession: () -> Unit = {},
    onAbandonSession: () -> Unit = {},
    onOpenDestination: () -> Unit = {},
    destinationLaunchNote: String? = null,
    usageNote: String? = null,
    showGrantUsageAccess: Boolean = false,
    onGrantUsageAccess: () -> Unit = {},
    onExploreMarathon: (() -> Unit)? = null,
    onExploreBusiness: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateLabel = SimpleDateFormat("EEEE · d MMMM", Locale.getDefault())
        .format(Date())
    val (headline, lede, heroTone, smallLabel, pills) = homeCopy(nextAction, intentionTitle)

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
            HomeBrandRow()
            Spacer(modifier = Modifier.height(37.dp))
            DateLabel(dateLabel)
            Spacer(modifier = Modifier.height(11.dp))
            Text(
                text = headline,
                style = MaterialTheme.typography.displayLarge,
                color = IntentColors.Ink,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Lede(lede)
            Spacer(modifier = Modifier.height(26.dp))

            if (nextAction.kind == NextActionKind.ACTIVE_SESSION) {
                ActiveSessionCard(
                    nextAction = nextAction,
                    intentionTitle = intentionTitle,
                    onCompleteSession = onCompleteSession,
                    onAbandonSession = onAbandonSession,
                    onDiscuss = onDiscuss,
                    onOpenDestination = onOpenDestination,
                    destinationLaunchNote = destinationLaunchNote,
                    usageNote = usageNote,
                    showGrantUsageAccess = showGrantUsageAccess,
                    onGrantUsageAccess = onGrantUsageAccess
                )
            } else {
                val cardTitle = when (nextAction.kind) {
                    NextActionKind.CONTINUE_EXPLORING ->
                        if (intentionTitle.contains("Spanish", ignoreCase = true)) {
                            "Speak Spanish at work"
                        } else {
                            intentionTitle.ifBlank { nextAction.label }
                        }
                    else -> nextAction.label
                }
                val cardDetail = when {
                    nextAction.reason.isNotBlank() -> nextAction.reason
                    nextAction.kind == NextActionKind.CONTINUE_EXPLORING ->
                        "We know what matters to you. Let's decide how you want to practise."
                    else -> nextAction.detail
                }

                HeroCard(tone = heroTone) {
                    SmallLabel(
                        text = smallLabel,
                        color = when (heroTone) {
                            HeroTone.Warm -> IntentColors.Warm
                            HeroTone.Success -> IntentColors.Success
                            HeroTone.Accent -> IntentColors.Accent
                        }
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = cardTitle,
                        style = MaterialTheme.typography.headlineSmall,
                        color = IntentColors.Ink
                    )
                    Spacer(modifier = Modifier.height(7.dp))
                    Text(
                        text = cardDetail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = IntentColors.Muted
                    )
                    if (nextAction.reason.isNotBlank() &&
                        nextAction.detail.isNotBlank() &&
                        nextAction.detail != nextAction.reason
                    ) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = nextAction.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Muted
                        )
                    }
                    if (pills.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            pills.forEach { (text, tone) ->
                                IntentPill(text = text, tone = tone)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(15.dp))
                    when (nextAction.kind) {
                        NextActionKind.CONTINUE_EXPLORING -> {
                            IntentPrimaryButton(
                                text = "Continue exploring",
                                onClick = onContinueExploring
                            )
                        }
                        NextActionKind.REVIEW_PROPOSAL -> {
                            IntentPrimaryButton(
                                text = "Review proposal",
                                onClick = onReviewProposal
                            )
                        }
                        NextActionKind.BEGIN -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IntentPrimaryButton(
                                    text = nextAction.beginLabel ?: "Begin",
                                    onClick = onBegin,
                                    modifier = Modifier.weight(1.4f)
                                )
                                IntentSecondaryButton(
                                    text = nextAction.deferLabel ?: "Later",
                                    onClick = onDefer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (nextAction.openDestinationLabel != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Begin also opens ${nextAction.destinationAppLabel ?: "the destination app"} when installed — opening an app is not lesson completion.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IntentColors.Muted
                                )
                            }
                            if (destinationLaunchNote != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = destinationLaunchNote,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = IntentColors.Warm
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            IntentSecondaryButton(
                                text = nextAction.discussLabel ?: "Discuss",
                                onClick = onDiscuss
                            )
                        }
                        NextActionKind.DISCUSS -> {
                            IntentPrimaryButton(text = "Discuss", onClick = onDiscuss)
                        }
                        else -> {
                            IntentPrimaryButton(
                                text = "Open your intention",
                                onClick = onOpenIntention
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle("Your space")
            Spacer(modifier = Modifier.height(9.dp))
            LineRow(
                title = intentionTitle.ifBlank { "Your intention" },
                subtitle = intentionHint.ifBlank { "Open your intention" },
                onClick = onOpenIntention,
                glyphIcon = Icons.Outlined.Spa,
                glyphTone = GlyphTone.Success
            )
            if (onExploreMarathon != null) {
                LineRow(
                    title = "Explore a marathon",
                    subtitle = "Second shape on the same foundation",
                    onClick = onExploreMarathon,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
            if (onExploreBusiness != null) {
                LineRow(
                    title = "Explore a business idea",
                    subtitle = "Third shape — discovery without forced tasks",
                    onClick = onExploreBusiness,
                    glyphIcon = Icons.Outlined.AutoAwesome,
                    glyphTone = GlyphTone.Accent
                )
            }
        }

        HomeBottomNav(
            selected = "home",
            onHome = { },
            onIntentions = onOpenIntention,
            onAgent = onDiscuss
        )
    }
}

private data class HomeCopy(
    val headline: String,
    val lede: String,
    val heroTone: HeroTone,
    val smallLabel: String,
    val pills: List<Pair<String, PillTone>>
)

private fun homeCopy(nextAction: NextAction, intentionTitle: String): HomeCopy =
    when (nextAction.kind) {
        NextActionKind.CONTINUE_EXPLORING -> HomeCopy(
            headline = "Where would you like to begin?",
            lede = "One goal is ready for a little more thought.",
            heroTone = HeroTone.Accent,
            smallLabel = "Still taking shape",
            pills = listOf("Exploring" to PillTone.Accent, "No plan yet" to PillTone.Neutral)
        )
        NextActionKind.REVIEW_PROPOSAL -> HomeCopy(
            headline = "You're finding your rhythm.",
            lede = "One useful decision is waiting for you.",
            heroTone = HeroTone.Warm,
            smallLabel = "Ready for your review",
            pills = listOf("Needs approval" to PillTone.Warm)
        )
        NextActionKind.BEGIN -> HomeCopy(
            headline = "One small step today.",
            lede = nextAction.reason.ifBlank {
                nextAction.detail.ifBlank { "Something useful is ready." }
            },
            heroTone = HeroTone.Success,
            smallLabel = "Relevant now",
            pills = listOf(
                intentionTitle.ifBlank { "In motion" } to PillTone.Success
            )
        )
        NextActionKind.ACTIVE_SESSION -> HomeCopy(
            headline = "You're in a session.",
            lede = "Mark complete when finished, or abandon if you stop.",
            heroTone = HeroTone.Success,
            smallLabel = "In progress",
            pills = emptyList()
        )
        NextActionKind.DISCUSS -> HomeCopy(
            headline = "Not due just yet.",
            lede = nextAction.reason.ifBlank { "Talk it through when you are ready." },
            heroTone = HeroTone.Warm,
            smallLabel = "Upcoming",
            pills = listOf(intentionTitle.ifBlank { "Planned" } to PillTone.Warm)
        )
        else -> HomeCopy(
            headline = "Where would you like to begin?",
            lede = nextAction.reason.ifBlank { "Your space is ready when you are." },
            heroTone = HeroTone.Accent,
            smallLabel = "Your space",
            pills = emptyList()
        )
    }

/**
 * In-progress SessionCard on Home: title, intention label, status, honest local
 * elapsed clock, and always-reachable Complete / Abandon (Discuss stays quiet).
 */
@Composable
private fun ActiveSessionCard(
    nextAction: NextAction,
    intentionTitle: String,
    onCompleteSession: () -> Unit,
    onAbandonSession: () -> Unit,
    onDiscuss: () -> Unit,
    onOpenDestination: () -> Unit = {},
    destinationLaunchNote: String? = null,
    usageNote: String? = null,
    showGrantUsageAccess: Boolean = false,
    onGrantUsageAccess: () -> Unit = {}
) {
    val startedAt = nextAction.startedAtEpochMs
    var nowEpochMs by remember(startedAt) {
        mutableLongStateOf(System.currentTimeMillis())
    }
    LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) {
            nowEpochMs = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val elapsedText = if (startedAt != null) {
        SessionElapsed.format(startedAt, nowEpochMs)
    } else {
        "—"
    }
    val elapsedLabel = if (startedAt != null) {
        SessionElapsed.label(startedAt, nowEpochMs)
    } else {
        "Elapsed unavailable"
    }
    val intentionLabel = intentionTitle.ifBlank { "Your intention" }

    HeroCard(tone = HeroTone.Success) {
        SmallLabel(text = "In progress", color = IntentColors.Success)
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = nextAction.label.ifBlank { "Active session" },
            style = MaterialTheme.typography.headlineSmall,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = intentionLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = IntentColors.Muted
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IntentPill(text = "In progress", tone = PillTone.Success)
            IntentPill(text = intentionLabel, tone = PillTone.Neutral)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "ELAPSED",
            style = MaterialTheme.typography.labelMedium,
            color = IntentColors.Section,
            letterSpacing = 1.1.sp,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = elapsedText,
            modifier = Modifier.semantics { contentDescription = elapsedLabel },
            style = MaterialTheme.typography.displayLarge,
            color = IntentColors.Ink,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "From when you tapped Begin — local clock only.",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
        if (nextAction.destinationAppLabel != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Destination · ${nextAction.destinationAppLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Opening an app is not lesson completion.",
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        if (destinationLaunchNote != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = destinationLaunchNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Warm
            )
        }
        if (usageNote != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = usageNote,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        if (showGrantUsageAccess) {
            Spacer(modifier = Modifier.height(8.dp))
            IntentQuietButton(
                text = "Grant usage access",
                onClick = onGrantUsageAccess
            )
        }
        Spacer(modifier = Modifier.height(15.dp))
        if (nextAction.openDestinationLabel != null) {
            IntentSecondaryButton(
                text = nextAction.openDestinationLabel,
                onClick = onOpenDestination
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (nextAction.completeLabel != null) {
                IntentPrimaryButton(
                    text = nextAction.completeLabel,
                    onClick = onCompleteSession,
                    modifier = Modifier.weight(1.4f)
                )
            }
            if (nextAction.abandonLabel != null) {
                IntentSecondaryButton(
                    text = nextAction.abandonLabel,
                    onClick = onAbandonSession,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        if (nextAction.discussLabel != null) {
            Spacer(modifier = Modifier.height(2.dp))
            IntentQuietButton(
                text = nextAction.discussLabel,
                onClick = onDiscuss
            )
        }
    }
}
