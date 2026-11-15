package com.intent.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intent.launcher.ui.theme.IntentColors

/**
 * Session detail card aligned to the four-core approval weekly mock:
 * SoftCard + glyph header + 2×2 metadata grid. Used by Proposal review and
 * the active-session surface (not Home exploring/begin heroes).
 */
data class SessionMetaCell(
    val icon: ImageVector,
    val label: String,
    val value: String,
    val contentDescription: String? = null
)

@Composable
fun SessionSummaryCard(
    title: String,
    cells: List<SessionMetaCell>,
    modifier: Modifier = Modifier,
    headerIcon: ImageVector = Icons.Outlined.School,
    belowGrid: @Composable ColumnScope.() -> Unit = {}
) {
    SoftCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(IntentColors.SuccessSoft, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = headerIcon,
                    contentDescription = null,
                    tint = IntentColors.Ink,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = IntentColors.Ink,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        SessionMetaGrid(cells = cells)
        belowGrid()
    }
}

@Composable
fun SessionMetaGrid(
    cells: List<SessionMetaCell>,
    modifier: Modifier = Modifier
) {
    require(cells.size == 4) { "SessionMetaGrid expects exactly 4 cells" }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, IntentColors.Line, RoundedCornerShape(12.dp))
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            SessionMetaCellView(
                cell = cells[0],
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(76.dp)
                    .background(IntentColors.Line)
            )
            SessionMetaCellView(
                cell = cells[1],
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
            SessionMetaCellView(
                cell = cells[2],
                modifier = Modifier.weight(1f)
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(76.dp)
                    .background(IntentColors.Line)
            )
            SessionMetaCellView(
                cell = cells[3],
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SessionMetaCellView(
    cell: SessionMetaCell,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(IntentColors.GridCell)
            .then(
                cell.contentDescription?.let { desc ->
                    Modifier.semantics { contentDescription = desc }
                } ?: Modifier
            )
            .padding(horizontal = 11.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = cell.icon,
                contentDescription = null,
                tint = IntentColors.Ink,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = cell.label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = IntentColors.Muted,
                letterSpacing = 0.8.sp
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = cell.value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Bold,
            color = IntentColors.Ink
        )
    }
}
