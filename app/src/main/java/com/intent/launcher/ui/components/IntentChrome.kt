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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Layers
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.ui.theme.IntentColors

/** One overflow / intention-menu entry (OS-143 History + Trash). */
data class OverflowMenuItem(
    val label: String,
    val onClick: () -> Unit
)

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
            fontSize = if (compact) 18.sp else 22.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = "INTENT",
            style = MaterialTheme.typography.labelMedium,
            color = IntentColors.Ink,
            letterSpacing = 2.4.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
fun HomeBrandRow(
    onSettings: (() -> Unit)? = null,
    overflowItems: List<OverflowMenuItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IntentBrandMark()
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (overflowItems.isNotEmpty()) {
                OverflowMenuButton(
                    items = overflowItems,
                    contentDescription = "More — History and Trash"
                )
            }
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
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = IntentColors.Ink,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun ScreenTopBar(
    contextLabel: String,
    onBack: () -> Unit,
    overflowItems: List<OverflowMenuItem> = emptyList(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = "Back",
            tint = IntentColors.Ink,
            modifier = Modifier
                .size(24.dp)
                .clickable(onClick = onBack)
        )
        IntentBrandMark(compact = true)
        if (overflowItems.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = contextLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = IntentColors.Muted
                )
                OverflowMenuButton(
                    items = overflowItems,
                    contentDescription = "Intention menu — History and Trash"
                )
            }
        } else {
            Text(
                text = contextLabel,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.Muted
            )
        }
    }
}

@Composable
fun OverflowMenuButton(
    items: List<OverflowMenuItem>,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .border(1.dp, IntentColors.Line, CircleShape)
                .clip(CircleShape)
                .background(IntentColors.Card)
                .semantics { this.contentDescription = contentDescription }
                .clickable { expanded = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.MoreVert,
                contentDescription = contentDescription,
                tint = IntentColors.Ink,
                modifier = Modifier.size(18.dp)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = IntentColors.Ink
                        )
                    },
                    onClick = {
                        expanded = false
                        item.onClick()
                    }
                )
            }
        }
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
        color = color,
        letterSpacing = 1.6.sp
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
        color = IntentColors.Section,
        letterSpacing = 1.1.sp
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
        letterSpacing = 1.5.sp
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
        color = IntentColors.Lede,
        lineHeight = 21.sp
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
            .padding(horizontal = 9.dp, vertical = 5.dp),
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
    modifier: Modifier = Modifier,
    accentOutline: Boolean = false
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(
            1.dp,
            if (accentOutline) IntentColors.Accent else IntentColors.Line
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = IntentColors.Card,
            contentColor = if (accentOutline) IntentColors.Accent else IntentColors.Ink
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

enum class GlyphTone { Accent, Success }

@Composable
fun LineRow(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)? = null,
    showArrow: Boolean = true,
    glyph: String? = null,
    glyphIcon: ImageVector? = null,
    glyphTone: GlyphTone = GlyphTone.Accent,
    modifier: Modifier = Modifier
) {
    val bg = when (glyphTone) {
        GlyphTone.Accent -> IntentColors.AccentSoft
        GlyphTone.Success -> IntentColors.SuccessSoft
    }
    val fg = when (glyphTone) {
        GlyphTone.Accent -> IntentColors.AccentDark
        GlyphTone.Success -> IntentColors.Success
    }
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
                .background(bg, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            when {
                glyphIcon != null -> Icon(
                    imageVector = glyphIcon,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(15.dp)
                )
                glyph != null -> Text(glyph, color = fg, fontSize = 14.sp)
            }
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
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = IntentColors.Arrow,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
fun FactRow(
    text: String,
    pending: Boolean = false,
    modifier: Modifier = Modifier
) {
    val parts = text.split(" · ", limit = 2)
    val labeled = parts.size == 2
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (pending) IntentColors.WarmSoft else IntentColors.SuccessSoft,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (pending) {
                Text(
                    text = "◌",
                    color = IntentColors.Warm,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 11.sp
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = null,
                    tint = IntentColors.Success,
                    modifier = Modifier.size(13.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(9.dp))
        if (labeled) {
            Text(
                text = buildAnnotatedString {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = IntentColors.Ink
                        )
                    ) {
                        append(parts[0])
                    }
                    append(" · ")
                    append(parts[1])
                },
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.FactText
            )
        } else {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = IntentColors.FactText
            )
        }
    }
}

@Composable
fun EmptyNote(
    text: String,
    modifier: Modifier = Modifier
) {
    val borderColor = IntentColors.EmptyBorder
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val stroke = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )
                drawRoundRect(
                    color = borderColor,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = stroke
                )
            }
            .background(IntentColors.EmptyFill, RoundedCornerShape(16.dp))
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
                color = IntentColors.CalloutText,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.CalloutText,
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
                .height(60.dp)
                .background(IntentColors.NavBar)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavItem(
                icon = Icons.Outlined.Home,
                label = "Home",
                selected = selected == "home",
                onClick = onHome
            )
            NavItem(
                icon = Icons.Outlined.Layers,
                label = "Intentions",
                selected = selected == "intentions",
                onClick = onIntentions
            )
            NavItem(
                icon = Icons.Outlined.AutoAwesome,
                label = "Agent",
                selected = selected == "agent",
                onClick = onAgent
            )
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val color = if (selected) IntentColors.Accent else IntentColors.NavIdle
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            letterSpacing = 0.sp,
            fontSize = 10.sp
        )
    }
}
