package com.intent.launcher.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.ui.theme.IntentColors

enum class HeroTone { Accent, Warm, Success }

@Composable
fun IntentBrandMark(
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "✦",
            color = IntentColors.Accent,
            fontSize = if (compact) 18.sp else 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "INTENT",
            style = MaterialTheme.typography.labelMedium,
            color = IntentColors.Ink,
            letterSpacing = 2.2.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun HomeBrandRow(
    onSettings: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IntentBrandMark()
        Box(
            modifier = Modifier
                .size(34.dp)
                .border(1.dp, IntentColors.Line, CircleShape)
                .clip(CircleShape)
                .background(IntentColors.Card)
                .then(
                    if (onSettings != null) Modifier.clickable(onClick = onSettings) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Text("⋯", color = IntentColors.Ink, fontSize = 18.sp)
        }
    }
}

@Composable
fun ScreenTopBar(
    contextLabel: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "‹",
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = 8.dp),
            color = IntentColors.Ink,
            fontSize = 28.sp,
            fontWeight = FontWeight.Medium
        )
        IntentBrandMark(compact = true)
        Text(
            text = contextLabel,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted
        )
    }
}

@Composable
fun SmallLabel(
    text: String,
    color: Color = IntentColors.Accent,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = color
    )
}

@Composable
fun SectionTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFF59645C),
        letterSpacing = 1.0.sp
    )
}

@Composable
fun DateLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        color = IntentColors.Muted,
        letterSpacing = 1.4.sp
    )
}

@Composable
fun Lede(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.bodyLarge,
        color = Color(0xFF4E5952)
    )
}

enum class PillTone { Neutral, Accent, Warm, Success }

@Composable
fun IntentPill(
    text: String,
    tone: PillTone = PillTone.Neutral,
    modifier: Modifier = Modifier
) {
    val (bg, fg) = when (tone) {
        PillTone.Neutral -> IntentColors.Pill to IntentColors.PillText
        PillTone.Accent -> IntentColors.AccentSoft to IntentColors.AccentDark
        PillTone.Warm -> IntentColors.WarmSoft to IntentColors.Warm
        PillTone.Success -> IntentColors.SuccessSoft to IntentColors.Success
    }
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(100.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        style = MaterialTheme.typography.labelSmall,
        color = fg,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.sp
    )
}

@Composable
fun HeroCard(
    tone: HeroTone = HeroTone.Accent,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val top = when (tone) {
        HeroTone.Accent -> IntentColors.Accent
        HeroTone.Warm -> IntentColors.Warm
        HeroTone.Success -> IntentColors.Success
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, IntentColors.Line, RoundedCornerShape(20.dp))
            .background(IntentColors.Card)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(top)
        )
        Column(
            modifier = Modifier.padding(17.dp),
            content = content
        )
    }
}

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(20.dp))
            .background(IntentColors.Card, RoundedCornerShape(20.dp))
            .padding(17.dp),
        content = content
    )
}

@Composable
fun IntentPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(13.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = IntentColors.Accent,
            contentColor = Color.White
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun IntentSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, IntentColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = IntentColors.Card,
            contentColor = IntentColors.Ink
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun IntentQuietButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TextButton(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = ButtonDefaults.textButtonColors(contentColor = IntentColors.AccentDark)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LineRow(
    glyph: String,
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    showArrow: Boolean = true,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(IntentColors.AccentSoft, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(glyph, color = IntentColors.AccentDark, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = IntentColors.Ink
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
        if (showArrow) {
            Text("›", color = Color(0xFF8E9891), fontSize = 19.sp)
        }
    }
}

@Composable
fun FactRow(
    text: String,
    pending: Boolean = false,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = if (pending) "◌" else "✓",
            color = if (pending) IntentColors.Warm else IntentColors.Success,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF4C5851)
        )
    }
}

@Composable
fun EmptyNote(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.EmptyBorder, RoundedCornerShape(16.dp))
            .background(Color(0xFFFBFAF6), RoundedCornerShape(16.dp))
            .padding(13.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF657067),
            textAlign = TextAlign.Start
        )
    }
}

@Composable
fun Callout(
    text: String,
    title: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(IntentColors.AccentSoft, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF444A84),
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF444A84),
            lineHeight = 18.sp
        )
    }
}

@Composable
fun HomeBottomNav(
    selected: String = "home",
    onHome: () -> Unit,
    onIntentions: () -> Unit,
    onAgent: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(IntentColors.Line)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IntentColors.NavBar)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem("⌂", "Home", selected == "home", onHome)
            NavItem("◎", "Intentions", selected == "intentions", onIntentions)
            NavItem("✦", "Agent", selected == "agent", onAgent)
        }
    }
}

@Composable
private fun NavItem(
    glyph: String,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = glyph,
            fontSize = 18.sp,
            color = if (selected) IntentColors.Accent else Color(0xFF6D766E)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) IntentColors.Accent else Color(0xFF6D766E),
            letterSpacing = 0.sp
        )
    }
}
