package com.intent.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.ui.theme.IntentColors

/**
 * Creative numeric input — horizontal scrollable wheel + steppers for quantities
 * (minutes, counts). Matches the INTENT UI redesign number-wheel mockup.
 */
@Composable
fun NumberWheel(
    values: List<Int>,
    selected: Int,
    onSelectedChange: (Int) -> Unit,
    unitLabel: String,
    summaryTemplate: String = "{value} {unit}",
    modifier: Modifier = Modifier
) {
    require(values.isNotEmpty()) { "NumberWheel needs at least one value" }
    val scroll = rememberScrollState()
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)

    LaunchedEffect(selectedIndex) {
        val approxItem = 56
        val target = (selectedIndex * approxItem - 80).coerceAtLeast(0)
        scroll.animateScrollTo(target)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(20.dp))
            .background(IntentColors.Card, RoundedCornerShape(20.dp))
            .padding(vertical = 18.dp, horizontal = 14.dp)
            .semantics {
                contentDescription = "Choose $unitLabel. Selected $selected $unitLabel."
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            values.forEach { value ->
                val isSelected = value == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(48.dp)
                        .clickable { onSelectedChange(value) }
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(if (isSelected) 28.dp else 16.dp)
                            .background(
                                if (isSelected) IntentColors.Accent else IntentColors.Line
                            )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .size(if (isSelected) 44.dp else 36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0x1A5459C9) else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) IntentColors.AccentDark else IntentColors.Muted,
                            fontSize = if (isSelected) 20.sp else 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(IntentColors.Line)
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            StepperButton(
                icon = Icons.Outlined.Remove,
                contentDescription = "Decrease $unitLabel",
                enabled = selectedIndex > 0,
                onClick = {
                    if (selectedIndex > 0) onSelectedChange(values[selectedIndex - 1])
                }
            )
            Text(
                text = summaryTemplate
                    .replace("{value}", selected.toString())
                    .replace("{unit}", unitLabel),
                style = MaterialTheme.typography.titleMedium,
                color = IntentColors.Ink,
                fontWeight = FontWeight.SemiBold
            )
            StepperButton(
                icon = Icons.Outlined.Add,
                contentDescription = "Increase $unitLabel",
                enabled = selectedIndex < values.lastIndex,
                onClick = {
                    if (selectedIndex < values.lastIndex) {
                        onSelectedChange(values[selectedIndex + 1])
                    }
                }
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Scroll or tap to choose",
            style = MaterialTheme.typography.bodySmall,
            color = IntentColors.Muted,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun StepperButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .border(1.dp, IntentColors.Line, CircleShape)
            .background(IntentColors.Paper)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) IntentColors.AccentDark else IntentColors.Muted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/** Common minute choices for practice / training quantity prompts. */
object PracticeMinuteChoices {
    val VALUES: List<Int> = listOf(5, 10, 15, 20, 30, 45, 60)
    const val DEFAULT = 20
    const val UNIT = "minutes per day"
}
