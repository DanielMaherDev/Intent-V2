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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.NextAction
import com.intent.launcher.domain.NextActionKind
import com.intent.launcher.ui.components.DateLabel
import com.intent.launcher.ui.components.GlyphTone
import com.intent.launcher.ui.components.HeroCard
import com.intent.launcher.ui.components.HeroTone
import com.intent.launcher.ui.components.HomeBottomNav
import com.intent.launcher.ui.components.HomeBrandRow
import com.intent.launcher.ui.components.IntentPill
import com.intent.launcher.ui.components.IntentPrimaryButton
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
    onExploreMarathon: (() -> Unit)? = null,
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

            val cardTitle = when (nextAction.kind) {
                NextActionKind.CONTINUE_EXPLORING ->
                    if (intentionTitle.contains("Spanish", ignoreCase = true)) {
                        "Speak Spanish at work"
                    } else {
                        intentionTitle.ifBlank { nextAction.label }
                    }
                else -> nextAction.label
            }
            val cardDetail = when (nextAction.kind) {
                NextActionKind.CONTINUE_EXPLORING ->
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
            lede = nextAction.detail.ifBlank { "Something useful is ready." },
            heroTone = HeroTone.Success,
            smallLabel = "Relevant now",
            pills = listOf(
                intentionTitle.ifBlank { "In motion" } to PillTone.Success
            )
        )
        else -> HomeCopy(
            headline = "Where would you like to begin?",
            lede = "Your space is ready when you are.",
            heroTone = HeroTone.Accent,
            smallLabel = "Your space",
            pills = emptyList()
        )
    }
