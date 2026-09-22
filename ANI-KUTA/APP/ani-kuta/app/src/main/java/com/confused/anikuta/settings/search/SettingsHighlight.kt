package com.confused.anikuta.settings.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * D-558 — the search-hit LANDING ANIMATION: "clicking on any of those
 * results leads the user to the appropriate settings page and properly
 * scrolls to the appropriate level and properly highlights it after
 * scrolling there too."
 *
 * Two small building blocks, shared by every settings screen:
 *
 * - [rememberSettingsAnchorScroll] — a LaunchedEffect that scrolls the
 *   screen's LazyColumn to the anchor's item index (each screen owns its
 *   anchor → index map; the maps live next to the items they describe).
 * - [SettingsHighlightTarget] — wraps a row/section; when its anchorId is
 *   the active anchor it PULSES (three soft primary glows) after the scroll
 *   lands. Inactive targets render identically to an unwrapped Box.
 */

/**
 * Scrolls [listState] to the item index the anchor maps to, once per anchor
 * change. [anchorIndexFor] returns the LazyColumn item index for an anchor
 * id, or null when this screen does not host it (or hosts it at a hidden
 * section).
 */
@Composable
fun rememberSettingsAnchorScroll(
    anchor: String?,
    anchorIndexFor: (String) -> Int?,
    listState: LazyListState,
) {
    LaunchedEffect(anchor) {
        val a = anchor ?: return@LaunchedEffect
        val index = anchorIndexFor(a) ?: return@LaunchedEffect
        runCatching { listState.animateScrollToItem(index) }
    }
}

/**
 * The highlight wrapper — see the file KDoc. [activeAnchor] is the screen's
 * current anchor (null = none); a target whose [anchorId] matches pulses
 * three times.
 */
@Composable
fun SettingsHighlightTarget(
    anchorId: String,
    activeAnchor: String?,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val active = activeAnchor == anchorId
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            repeat(3) {
                pulse.snapTo(0f)
                pulse.animateTo(1f, tween(340, easing = FastOutSlowInEasing))
                pulse.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
            }
        } else {
            pulse.snapTo(0f)
        }
    }
    if (!active && pulse.value <= 0f) {
        // The dormant path — literally a Box, zero cost, zero visual delta.
        Box(modifier = modifier, content = content)
        return
    }
    val highlightColor = MaterialTheme.colorScheme.primary
    val glowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .drawBehind {
                val a = pulse.value
                if (a > 0f) {
                    drawRoundRect(
                        color = glowColor.copy(alpha = glowColor.alpha * a),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                    )
                    drawRoundRect(
                        color = highlightColor.copy(alpha = 0.55f * a),
                        topLeft = Offset(0f, 0f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = 2.dp.toPx(), // DrawScope IS a Density
                        ),
                    )
                }
            },
        content = content,
    )
}
