package com.confused.anikuta.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The settings-area leading-icon tile (D-744): the row's leading glyph sits
 * on a **38dp rounded-square tile** tinted `primary` at 12% — the exact
 * recipe the settings search-result rows already wore, now unified across
 * every nav row (More page, Settings hub, About, Appearance, Notifications,
 * the debug door row).
 *
 * Visual contract:
 *  - Tile: 38dp, `RoundedCornerShape(12.dp)`, `primary.copy(alpha = 0.12f)`
 *    — a translucent lime wash that reads as its own surface against the
 *    row's `surfaceVariant@0.4` card without ever competing with it.
 *  - Content: the caller's icon slot, centered — the glyph keeps its own
 *    size and `primary` tint (24dp on nav rows, 20dp on search results).
 *  - [showDot]: the 8dp red notification dot at the tile's top-end corner
 *    (was: the icon's corner — same affordance, now anchored to the tile).
 *
 * Supersedes the D-250 "bare icon" ruling — that ruling was about the
 * per-screen `primaryContainer` chips looking like a DIFFERENT format from
 * the More page, not about tiles per se; D-744 re-introduces the tile BY
 * USER ORDER, unified through this single primitive (one source, zero
 * per-screen variants — the D-250 inconsistency class cannot recur).
 *
 * Use it via [MoreListRow]; call it directly only when a row is hand-rolled
 * (the About update row, the debug door row, the search-result row).
 */
@Composable
fun SettingsIconTile(
    modifier: Modifier = Modifier,
    showDot: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .background(
                        color = Color(0xFFFF5252),
                        shape = CircleShape,
                    ),
            )
        }
    }
}
