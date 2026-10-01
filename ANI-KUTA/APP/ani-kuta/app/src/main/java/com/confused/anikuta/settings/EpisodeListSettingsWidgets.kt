package com.confused.anikuta.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
// SegmentedToggle lives in THIS package (settings/) — no import needed.

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 110 (D-725): THE SHARED EPISODE-LIST SETTINGS WIDGET KIT — ONE
//  anatomy for BOTH episode-list settings screens (the details page's and
//  the player page's). Until now each screen carried its own private
//  copies of the same five shapes (the card, the switch row, the segmented
//  row, the caption — duplicated per the poster page's old reasoning, and
//  drifting: the details screen still rendered duplicated inner headings
//  the player screen had already removed in round 105). ONE kit now: the
//  section card (the SettingsGroupCard look, single-8dp-gutter form), the
//  switch row, the segmented row, and the quiet caption — the same
//  components, the same rhythm, both pages.
// ════════════════════════════════════════════════════════════════════════════

/**
 * The section card — the SettingsGroupCard look (the primary ExtraBold
 * label, the 12dp-rounded surfaceVariant surface) WITHOUT the 16dp
 * horizontal padding baked into the shared component: both episode-list
 * screens carry the single 8dp gutter (the D-525 rule). The card label IS
 * the section's heading (the round-105 player-screen fix, now both
 * screens' rule — no duplicated inner headings).
 */
@Composable
internal fun EpisodeSettingsCard(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

/**
 * The switch row — title + optional one-line description + the switch
 * (D-532's shape, both screens' vocabulary unified).
 */
@Composable
internal fun SwitchRow(
    title: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    description: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (description != null) {
                Text(
                    text = description,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

/**
 * The segmented elements row — the label + optional description ABOVE the
 * toggle (the "Number style" pattern, the settings vocabulary the user
 * already knows; both screens' forms unified).
 */
@Composable
internal fun SegmentedRow(
    title: String,
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    description: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (description != null) {
            Text(
                text = description,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Box(modifier = Modifier.padding(top = 10.dp)) {
            SegmentedToggle(
                options = options,
                selectedIndex = selectedIndex,
                onSelect = onSelect,
            )
        }
    }
}

/**
 * The quiet caption — the small 12sp onSurfaceVariant explanation line
 * under a control (the gate notes, the footer hints; both screens' inline
 * copies unified). No built-in padding — the caller places it.
 */
@Composable
internal fun Caption(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: androidx.compose.ui.text.style.TextAlign? = null,
) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}
