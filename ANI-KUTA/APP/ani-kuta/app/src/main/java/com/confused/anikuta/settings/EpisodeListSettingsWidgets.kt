package com.confused.anikuta.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.settings.search.SettingsHighlightTarget
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
 *
 * ROUND 111 (D-729): [onLabelClick] — the v1.1.67 device round's testing
 * aid: "when I click on the Elements heading at the top, the text at the
 * top, what it will do is that it will switch the grid layout to Three
 * buttons per row." A quiet ripple is the only affordance (the heading
 * stays visually identical — the user knows what it does).
 */
@Composable
internal fun EpisodeSettingsCard(
    label: String,
    onLabelClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier
                .padding(start = 8.dp, bottom = 8.dp)
                .let { base ->
                    if (onLabelClick != null) {
                        base
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onLabelClick)
                    } else {
                        base
                    }
                },
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
    textAlign: TextAlign? = null,
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

// ════════════════════════════════════════════════════════════════════════
//  ROUND 111 (D-729): THE ELEMENTS' GRID OF BUTTONS — the v1.1.67 device
//  round: "having toggles for the elements is most definitely not a good
//  idea, I feel like. So what I am thinking about is a grid layout of
//  buttons which I can click and turn to toggle them on or to toggle them
//  off … we can do two options per row, like on the left and on the right
//  it will show a total of two options … they will be in the button kind
//  of format." NO descriptions ("I don't feel like there will be any need
//  for description for that"), and the card-heading tap toggles the grid
//  between two and three buttons per row (the user's testing aid — "so I
//  can test out how the things will overall look like in the end"). The
//  multi-state knobs (segmented rows, sliders) are NOT on/off toggles —
//  they stay in their own cards/rows.
//
//  ROUND 113 (D-733): THE SEGMENT ANATOMY — the v1.1.69 verdict on the
//  round-112 pills: "the way I wanted the elements to look like were just
//  like how the buttons for the layout are, like each individual button
//  for the layout, the currently selected one … apparently you gave them
//  rounded corner, pill-shaped button-like feel, which is not good." The
//  grid now wears the LAYOUT SELECTOR'S OWN anatomy (SegmentedToggle.kt):
//  ONE shared surfaceVariant container (12dp-rounded, the 4dp inner
//  padding), the elements as 8dp-ROUNDED SEGMENTS — the ON state is the
//  SOLID primary fill with onPrimary ExtraBold (the selected segment's
//  exact look), the OFF state is TRANSPARENT over the container's tint
//  with onSurfaceVariant Medium (the unselected segment's exact look),
//  and the segment-height is the selector's own compact 8dp-vertical
//  padding — no more fat 48dp pills reading "in a list format".
// ════════════════════════════════════════════════════════════════════════

/** One element's on/off state — a grid button's model. */
internal data class ElementToggleEntry(
    val title: String,
    val checked: Boolean,
    val onToggle: () -> Unit,
    /** The optional search anchor (the el_* ids) wrapped around the button. */
    val anchorId: String? = null,
)

/**
 * THE ELEMENTS' GRID — [entries] as equal-width toggle SEGMENTS inside the
 * layout selector's own shared container (ROUND 113, D-733: the
 * SegmentedToggle anatomy — surfaceVariant@0.5, 12dp corners, the 4dp inner
 * padding, the 4dp inter-segment gaps). [columns] per row (2 by default;
 * the heading tap flips the testing aid to 3). The container reflows
 * smoothly (animateContentSize); each segment's state change crossfades its
 * colors. Short rows are padded with spacers so the segments keep the
 * grid's equal widths.
 */
@Composable
internal fun ElementToggleGrid(
    entries: List<ElementToggleEntry>,
    columns: Int,
    highlightAnchor: String? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
                .animateContentSize(animationSpec = tween(260, easing = FastOutSlowInEasing)),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            entries.chunked(columns.coerceAtLeast(1)).forEach { rowEntries ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    rowEntries.forEach { entry ->
                        ElementToggleButton(
                            entry = entry,
                            activeAnchor = highlightAnchor,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns.coerceAtLeast(1) - rowEntries.size) {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * ONE element segment — ROUND 113 (D-733), the v1.1.69 verdict: "the way I
 * wanted the elements to look like were just like how the buttons for the
 * layout are, like each individual button for the layout, the currently
 * selected one." The button IS a layout-selector segment now — the exact
 * SegmentToggle construct: a Box clipped to 8dp corners over the shared
 * container, ON = the SOLID primary fill + onPrimary ExtraBold (the
 * selected segment), OFF = TRANSPARENT (the container's tint shows through)
 * + onSurfaceVariant Medium, the selector's own compact 8dp vertical
 * padding, and the 220ms color crossfade. The round-112 pill
 * (RoundedCornerShape(50), the translucent tints, the M3 clickable
 * Surface's inflated touch minimum) is RETIRED.
 */
@Composable
private fun ElementToggleButton(
    entry: ElementToggleEntry,
    activeAnchor: String?,
    modifier: Modifier = Modifier,
) {
    val checked = entry.checked
    val container by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "element_container",
    )
    val content by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "element_content",
    )
    val button: @Composable () -> Unit = {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(container)
                .clickable { entry.onToggle() },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = entry.title,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = if (checked) FontWeight.ExtraBold else FontWeight.Medium,
                color = content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }
    }
    if (entry.anchorId != null) {
        SettingsHighlightTarget(anchorId = entry.anchorId, activeAnchor = activeAnchor) {
            button()
        }
    } else {
        button()
    }
}
