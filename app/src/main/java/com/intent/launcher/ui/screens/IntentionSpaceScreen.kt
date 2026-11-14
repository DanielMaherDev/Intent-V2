package com.intent.launcher.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.intent.launcher.domain.Fact
import com.intent.launcher.domain.Hypothesis
import com.intent.launcher.domain.Intention
import com.intent.launcher.domain.IntentionPhase
import com.intent.launcher.domain.ScheduledSession
import com.intent.launcher.ui.components.EmptyNote
import com.intent.launcher.ui.components.FactRow
import com.intent.launcher.ui.components.HeroCard
import com.intent.launcher.ui.components.HeroTone
import com.intent.launcher.ui.components.IntentPrimaryButton
import com.intent.launcher.ui.components.Lede
import com.intent.launcher.ui.components.ScreenTopBar
import com.intent.launcher.ui.components.SectionTitle
import com.intent.launcher.ui.components.SmallLabel
import com.intent.launcher.ui.theme.IntentColors

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
    val phase = intention?.phase ?: IntentionPhase.EXPLORING
    val (phaseLabel, heroTone, heroLabel, heroTitle, heroBody, heroCta) =
        intentionCopy(phase, intention?.shapeTag)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "Intention", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        SmallLabel(
            text = phaseLabel,
            color = when (heroTone) {
                HeroTone.Warm -> IntentColors.Warm
                HeroTone.Success -> IntentColors.Success
                // JPG exploring label is muted olive, not accent
                HeroTone.Accent -> IntentColors.Section
            }
        )
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = intention?.title ?: "Intention",
            style = MaterialTheme.typography.headlineLarge,
            color = IntentColors.Ink
        )
        Spacer(modifier = Modifier.height(8.dp))
        Lede(intention?.statement ?: "")
        Spacer(modifier = Modifier.height(26.dp))

        HeroCard(tone = heroTone) {
            SmallLabel(
                text = heroLabel,
                color = when (heroTone) {
                    HeroTone.Warm -> IntentColors.Warm
                    HeroTone.Success -> IntentColors.Success
                    HeroTone.Accent -> IntentColors.Section
                }
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = heroTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = heroBody,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
            Spacer(modifier = Modifier.height(15.dp))
            IntentPrimaryButton(text = heroCta, onClick = onWorkThisOut)
        }

        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle("What we know")
        Spacer(modifier = Modifier.height(9.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, IntentColors.Line, RoundedCornerShape(20.dp))
                .background(IntentColors.Card, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            if (facts.isEmpty() && hypotheses.isEmpty() && sessions.isEmpty()) {
                FactRow(text = defaultGoalFact(intention))
                FactRow(text = defaultTimeframeFact(intention))
            } else {
                facts.forEach { f ->
                    FactRow(text = "Confirmed by you · ${f.text}")
                }
                hypotheses.forEach { h ->
                    FactRow(text = "Inferred · ${h.text}")
                }
                sessions.forEach { s ->
                    val whenLabel = "dow ${s.dayOfWeek} %02d:%02d".format(s.hour, s.minute)
                    FactRow(text = "${s.title} · $whenLabel")
                }
                if (phase == IntentionPhase.PROPOSAL_PENDING) {
                    FactRow(text = "Pending · add a weekly commitment", pending = true)
                }
            }
        }

        Spacer(modifier = Modifier.height(17.dp))
        if (sessions.isEmpty() && phase != IntentionPhase.ACTIVE) {
            EmptyNote(
                text = "No tasks have been invented. This intention stays open while you decide on an approach."
            )
        }
    }
}

private data class IntentionCopy(
    val phaseLabel: String,
    val heroTone: HeroTone,
    val heroLabel: String,
    val heroTitle: String,
    val heroBody: String,
    val heroCta: String
)

private fun intentionCopy(phase: IntentionPhase, shapeTag: String?): IntentionCopy =
    when (phase) {
        IntentionPhase.EXPLORING, IntentionPhase.CLARIFYING -> IntentionCopy(
            phaseLabel = "Exploring",
            heroTone = HeroTone.Accent,
            heroLabel = "The question now",
            heroTitle = when (shapeTag) {
                "marathon" -> "How will you train?"
                "business" -> "How will you learn?"
                else -> "How will you practise?"
            },
            heroBody = when (shapeTag) {
                "marathon" ->
                    "We can shape this around base mileage, long runs, or a mix you can keep."
                "business" ->
                    "We can stay in discovery, talk to customers, or add a light research habit — nothing forced."
                else ->
                    "We can make a routine around an app, a tutor, conversations with your agent, or a mix."
            },
            heroCta = "Work this out"
        )
        IntentionPhase.PROPOSAL_PENDING -> IntentionCopy(
            phaseLabel = "Approach in progress",
            heroTone = HeroTone.Warm,
            heroLabel = "Decision needed",
            heroTitle = "Add your session?",
            heroBody = "INTENT will bring it into your week once approved.",
            heroCta = "Review details"
        )
        IntentionPhase.ACTIVE -> IntentionCopy(
            phaseLabel = "In motion",
            heroTone = HeroTone.Success,
            heroLabel = "Next useful step",
            heroTitle = "Prepare for your next session",
            heroBody = "Bring one situation you want to rehearse.",
            heroCta = "Begin preparation"
        )
        IntentionPhase.PAUSED -> IntentionCopy(
            phaseLabel = "Paused",
            heroTone = HeroTone.Accent,
            heroLabel = "When you are ready",
            heroTitle = "Pick this up again?",
            heroBody = "Your facts stay. Nothing is invented while you pause.",
            heroCta = "Work this out"
        )
    }

private fun defaultGoalFact(intention: Intention?): String =
    when (intention?.shapeTag) {
        "marathon" -> "Goal · finish a marathon"
        "business" -> "Goal · explore a business idea"
        else -> "Goal · use Spanish at work"
    }

private fun defaultTimeframeFact(intention: Intention?): String =
    when (intention?.shapeTag) {
        "marathon" -> "Timeframe · build base first"
        "business" -> "Timeframe · stay in discovery"
        else -> "Timeframe · within a year"
    }
