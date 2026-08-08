package com.confused.anikuta.feature.debugbubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.debugapi.LocalDebugContext
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

/**
 * The debug panel — expands beside the bubble when tapped (Phase DB-2).
 *
 * Placement is computed from the bubble's offset + the expand direction (D-163):
 * the panel opens toward the side with more space AND extends vertically away
 * from the nearest edge. Panel width = most of the screen width (12dp padding
 * each side); max height = 75% of screen height.
 *
 * The panel has a header (title + close), a horizontally-scrollable tab strip,
 * and the active tab's content. For DB-2, only the Current Screen tab is
 * implemented (reads [LocalDebugContext]); the other 4 tabs are placeholders.
 *
 * @param state The bubble state (reads expanded, offset; writes collapse).
 * @param onDismiss Called when the user taps the close button or the scrim.
 */
@Composable
fun DebugPanel(
    state: DebugBubbleState,
    onDismiss: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val horizontalPaddingPx = with(density) { PANEL_HORIZONTAL_PADDING.toPx() }
    val gapPx = with(density) { PANEL_GAP.toPx() }
    val bubbleSizePx = with(density) { BUBBLE_SIZE.toPx() }
    // System-bar insets — the panel must not overlap the status/nav bars
    // (enableEdgeToEdge means screenHeightDp includes them). D-162 I6.
    val statusBarPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
    val navBarPx = with(density) { WindowInsets.navigationBars.getBottom(density).toFloat() }

    val bubbleOffset = state.offset
    val direction = remember(bubbleOffset, screenWidthPx, screenHeightPx) {
        expandDirectionFor(bubbleOffset.x, bubbleOffset.y, screenWidthPx, screenHeightPx)
    }

    // Panel dimensions in px.
    val panelWidthPx = (screenWidthPx - 2 * horizontalPaddingPx).coerceAtLeast(0f)
    val panelHeightPx = (screenHeightPx * 0.75f).coerceAtLeast(0f)

    // Panel placement: anchored to the bubble, opening toward the chosen side.
    val panelOffset = computePanelOffset(
        bubbleOffset = bubbleOffset,
        direction = direction,
        screenWidthPx = screenWidthPx,
        screenHeightPx = screenHeightPx,
        bubbleSizePx = bubbleSizePx,
        panelWidthPx = panelWidthPx,
        panelHeightPx = panelHeightPx,
        horizontalPaddingPx = horizontalPaddingPx,
        gapPx = gapPx,
        statusBarPx = statusBarPx,
        navBarPx = navBarPx,
    )

    var activeTab by remember { mutableStateOf(DebugTab.SCREEN) }

    AnimatedVisibility(
        visible = state.expanded,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.3f))
            // Intercept ALL pointer events on the scrim — taps dismiss the panel,
            // drags are swallowed (so the bubble underneath can't be dragged
            // while the panel is open, which would cause the panel to follow +
            // potentially flip sides). Sub-agent review IMPORTANT #2.
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .width(with(density) { panelWidthPx.toDp() })
                    .height(with(density) { panelHeightPx.toDp() })
                    .offset {
                        IntOffset(panelOffset.x.toInt(), panelOffset.y.toInt())
                    }
                    .clickable { /* consume taps so the scrim doesn't dismiss */ },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Header ──
                    PanelHeader(onClose = onDismiss)

                    // ── Tab strip ──
                    TabStrip(
                        activeTab = activeTab,
                        onSelect = { activeTab = it },
                    )

                    // ── Tab content ──
                    // No verticalScroll here — tabs that need scrolling (Database,
                    // Console) manage their own LazyColumn/scroll. Non-scroll tabs
                    // (Screen, placeholders) get a padding wrapper.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                    ) {
                        when (activeTab) {
                            DebugTab.SCREEN -> CurrentScreenContent()
                            DebugTab.DATABASE -> com.confused.anikuta.feature.debugbubble.panel.DatabaseTab()
                            DebugTab.CONSOLE -> com.confused.anikuta.feature.debugbubble.panel.ConsoleTab()
                            DebugTab.NETWORK -> com.confused.anikuta.feature.debugbubble.panel.NetworkTab()
                            DebugTab.APP_INFO -> com.confused.anikuta.feature.debugbubble.panel.AppInfoTab()
                        }
                    }
                }
            }
        }
    }
}

// ── Header ──

@Composable
private fun PanelHeader(onClose: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Debug",
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .size(32.dp)
                .clickable(onClick = onClose),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close panel",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

// ── Tab strip ──

@Composable
private fun TabStrip(
    activeTab: DebugTab,
    onSelect: (DebugTab) -> Unit,
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(DebugTab.values().toList()) { tab ->
            val isSelected = tab == activeTab
            val bg = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                color = bg,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onSelect(tab) },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(text = tab.icon, fontSize = 14.sp)
                    Text(
                        text = tab.label,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = fg,
                    )
                }
            }
        }
    }
}

// ── Current Screen tab content ──

@Composable
private fun CurrentScreenContent() {
    val context = LocalDebugContext.current
    if (context == null) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "No screen context",
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "This screen doesn't provide debug context. The generic tabs (Database, Console, Network, App Info) are available.",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = context.screenName,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (context.screenData.isNotEmpty()) {
                SectionLabel("Screen data")
                context.screenData.forEach { (k, v) ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text(
                            text = k,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(100.dp),
                        )
                        Text(
                            text = v,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }
            if (context.relevantTables.isNotEmpty()) {
                SectionLabel("Relevant DB rows")
                context.relevantTables.forEach { ref ->
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { /* DB-3: jump to Database tab pre-filtered */ },
                    ) {
                        Text(
                            text = ref.label,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            if (context.actions.isNotEmpty()) {
                SectionLabel("Quick actions")
                context.actions.forEach { action ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clickable { action.action() },
                    ) {
                        Text(
                            text = action.label,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = RobotoFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
    )
}

// ── Placeholder for tabs not yet implemented ──

@Composable
private fun PlaceholderContent(tabName: String, phase: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = tabName,
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = "Coming in $phase",
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ── Helpers ──

/**
 * Compute the panel's offset (px) within the parent Box, anchored to the bubble
 * + opening toward the chosen side. The panel is always fully on-screen
 * (clamped to the horizontal padding bounds).
 */
private fun computePanelOffset(
    bubbleOffset: Offset,
    direction: ExpandDirection,
    screenWidthPx: Float,
    screenHeightPx: Float,
    bubbleSizePx: Float,
    panelWidthPx: Float,
    panelHeightPx: Float,
    horizontalPaddingPx: Float,
    gapPx: Float,
    statusBarPx: Float,
    navBarPx: Float,
): Offset {
    // Vertical bounds — the panel must not overlap the status/nav bars.
    val minY = statusBarPx.coerceAtLeast(horizontalPaddingPx)
    val maxY = (screenHeightPx - panelHeightPx - navBarPx).coerceAtLeast(minY)

    val x = when (direction) {
        ExpandDirection.RIGHT_DOWN, ExpandDirection.RIGHT_UP -> {
            // Panel opens right of the bubble.
            (bubbleOffset.x + bubbleSizePx + gapPx)
                .coerceAtMost((screenWidthPx - panelWidthPx - horizontalPaddingPx).coerceAtLeast(horizontalPaddingPx))
        }
        ExpandDirection.LEFT_DOWN, ExpandDirection.LEFT_UP -> {
            // Panel opens left of the bubble.
            (bubbleOffset.x - panelWidthPx - gapPx)
                .coerceAtLeast(horizontalPaddingPx)
        }
    }

    val y = when (direction) {
        ExpandDirection.RIGHT_DOWN, ExpandDirection.LEFT_DOWN -> {
            // Panel extends downward from the bubble's top.
            bubbleOffset.y.coerceIn(minY, maxY)
        }
        ExpandDirection.RIGHT_UP, ExpandDirection.LEFT_UP -> {
            // Panel extends upward from the bubble's bottom.
            (bubbleOffset.y + bubbleSizePx - panelHeightPx).coerceIn(minY, maxY)
        }
    }

    return Offset(x, y)
}

// ── Constants ──

private val BUBBLE_SIZE = 48.dp
private val PANEL_HORIZONTAL_PADDING = 12.dp
private val PANEL_GAP = 8.dp
