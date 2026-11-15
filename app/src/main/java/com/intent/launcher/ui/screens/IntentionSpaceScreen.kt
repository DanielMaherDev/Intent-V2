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
import com.intent.launcher.domain.SessionDestinations
import com.intent.launcher.domain.ShapeProjection
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
    val projection = ShapeProjection.project(
        intention = intention,
        facts = facts,
        hypotheses = hypotheses,
        sessions = sessions,
        destination = SessionDestinations.forIntention(intention)
    )
    val heroTone = when (projection.hero.tone) {
        "warm" -> HeroTone.Warm
        "success" -> HeroTone.Success
        else -> HeroTone.Accent
    }
    val labelColor = when (heroTone) {
        HeroTone.Warm -> IntentColors.Warm
        HeroTone.Success -> IntentColors.Success
        HeroTone.Accent -> IntentColors.Section
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(IntentColors.Paper)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 19.dp, vertical = 15.dp)
    ) {
        ScreenTopBar(contextLabel = "Intention", onBack = onBack)
        Spacer(modifier = Modifier.height(23.dp))
        SmallLabel(text = projection.hero.phaseLabel, color = labelColor)
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
            SmallLabel(text = projection.hero.heroLabel, color = labelColor)
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = projection.hero.heroTitle,
                style = MaterialTheme.typography.headlineSmall,
                color = IntentColors.Ink
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = projection.hero.heroBody,
                style = MaterialTheme.typography.bodyMedium,
                color = IntentColors.Muted
            )
            Spacer(modifier = Modifier.height(15.dp))
            IntentPrimaryButton(text = projection.hero.heroCta, onClick = onWorkThisOut)
        }

        if (projection.approachSectionTitle != null && projection.approachSteps.isNotEmpty()) {
            Spacer(modifier = Modifier.height(26.dp))
            SectionTitle(projection.approachSectionTitle)
            Spacer(modifier = Modifier.height(9.dp))
            Column(modifier = Modifier.fillMaxWidth().padding(start = 7.dp)) {
                projection.approachSteps.forEach { step ->
                    Column(modifier = Modifier.padding(start = 17.dp, bottom = 14.dp)) {
                        Text(
                            text = step.title,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IntentColors.Ink
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = step.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = IntentColors.Muted
                        )
                    }
                }
            }
        }

        if (projection.resourceLine != null) {
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = projection.resourceLine,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }

        Spacer(modifier = Modifier.height(26.dp))
        SectionTitle(projection.knowledgeSectionTitle)
        Spacer(modifier = Modifier.height(9.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, IntentColors.Line, RoundedCornerShape(20.dp))
                .background(IntentColors.Card, RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp, vertical = 4.dp)
        ) {
            if (facts.isEmpty() && hypotheses.isEmpty() && sessions.isEmpty()) {
                projection.defaultFacts.forEach { FactRow(text = it) }
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
                if (intention?.phase == IntentionPhase.PROPOSAL_PENDING) {
                    FactRow(text = "Pending · add a weekly commitment", pending = true)
                }
            }
        }

        if (projection.emptyNote != null) {
            Spacer(modifier = Modifier.height(17.dp))
            EmptyNote(text = projection.emptyNote)
        }
    }
}
