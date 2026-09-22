package com.confused.anikuta.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * A reusable 3-way (or N-way) segmented toggle — the same visual style as the
 * download settings' "Best effort / Ask / Don't" control (SegmentedRowLocal).
 *
 * Renders a surface-tinted pill containing one segment per option; the selected
 * segment gets the primary background. Used by the Notifications settings for
 * tri-state triggers (On / Silent / Off) and audio pref (Sub / Dub / Both),
 * and by the episode-list appearance page's four-way Layout selector.
 *
 * D-556: the selection indicator now SLIDES — the primary pill is ONE shape
 * drawn behind the segments (drawBehind), whose x-offset animates to the
 * selected segment on a slightly-under-damped spring so the motion reads as
 * physical. The v1.1.29 device round: "when I switch between the four
 * options, it should switch with a smooth animation. Like if I move from the
 * cinema layout to the timeline layout, then the selection should move
 * smoothly to the timeline one." The pill's geometry derives from the REAL
 * segment widths (BoxWithConstraints + the equal-weight split minus the
 * inter-segment gap), so it lands exactly on the segment for any option
 * count, and its height is the row's own height (drawn in the container's
 * coordinate space — no fixed dp, no matchParentSize gymnastics). The label
 * colors crossfade alongside.
 *
 * @param options Label per segment, in display order.
 * @param selectedIndex The currently-selected segment index.
 * @param onSelect Called with the newly-selected index.
 * @param compact D-524: 12sp labels for the dense FIVE-way toggles (the
 *                poster-template picker) — a 9-char label must fit a ~60dp
 *                segment without ellipsis.
 */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
        ) {
            // The segment geometry: equal weights separated by [gap] — the
            // same spacing the segments below use, so the pill and the
            // segments always agree.
            val gap = 4.dp
            val density = LocalDensity.current
            val segmentWidth = (maxWidth - gap * (options.size - 1)) / options.size
            val pillOffset by animateDpAsState(
                targetValue = segmentWidth * selectedIndex + gap * selectedIndex,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
                label = "segmented_toggle_pill_offset",
            )
            val pillOffsetPx = with(density) { pillOffset.toPx() }
            val segmentWidthPx = with(density) { segmentWidth.toPx() }
            val cornerPx = with(density) { 8.dp.toPx() }
            val pillColor = MaterialTheme.colorScheme.primary
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // The pill — ONE rounded rect behind the segments. Drawing
                    // it here (drawBehind) means its height is the Row's own
                    // measured height: no fixed dp, no second measurement.
                    .drawBehind {
                        drawRoundRect(
                            color = pillColor,
                            topLeft = Offset(pillOffsetPx, 0f),
                            size = Size(segmentWidthPx, size.height),
                            cornerRadius = CornerRadius(cornerPx, cornerPx),
                        )
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    options.forEachIndexed { idx, label ->
                        val selected = idx == selectedIndex
                        val fg: Color by animateColorAsState(
                            targetValue = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                            label = "segmented_toggle_label_color",
                        )
                        Box(
                            modifier = Modifier
                                .width(segmentWidth)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSelect(idx) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                fontFamily = RobotoFamily,
                                fontSize = if (compact) 12.sp else 13.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                color = fg,
                                maxLines = 1,
                                modifier = Modifier.padding(vertical = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
