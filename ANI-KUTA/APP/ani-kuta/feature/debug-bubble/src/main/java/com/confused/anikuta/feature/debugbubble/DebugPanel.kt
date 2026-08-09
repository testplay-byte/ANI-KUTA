package com.confused.anikuta.feature.debugbubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
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
 * The debug panel — expands beside the bubble when tapped (Phase DB-2, revised).
 *
 * **Panel state machine:**
 * - CLOSED → not shown.
 * - EXPANDED → full panel with scrim; tap-outside → MINIMIZED.
 * - MINIMIZED → small peek bar; tap the bubble → EXPANDED or CLOSED.
 *
 * **Bubble stays visible:** the panel is positioned beside the bubble, not on
 * top of it. If the bubble's current position would be covered, the bubble is
 * repositioned to the panel's edge (the caller handles this via state).
 *
 * **Solid background:** no transparency — uses `surface` at full opacity.
 *
 * **Tab strip:** Material icons (no emojis) in a segmented-style section.
 *
 * @param state The bubble state.
 * @param onMinimize Called when the user taps outside (scrim) → minimizes.
 */
@Composable
fun DebugPanel(
    state: DebugBubbleState,
    onMinimize: () -> Unit,
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val horizontalPaddingPx = with(density) { PANEL_HORIZONTAL_PADDING.toPx() }
    val gapPx = with(density) { PANEL_GAP.toPx() }
    val bubbleSizePx = with(density) { BUBBLE_SIZE.toPx() }
    val statusBarPx = with(density) { WindowInsets.statusBars.getTop(density).toFloat() }
    val navBarPx = with(density) { WindowInsets.navigationBars.getBottom(density).toFloat() }

    val bubbleOffset = state.offset
    val direction = remember(bubbleOffset, screenWidthPx, screenHeightPx) {
        expandDirectionFor(bubbleOffset.x, bubbleOffset.y, screenWidthPx, screenHeightPx)
    }

    val panelWidthPx = (screenWidthPx - 2 * horizontalPaddingPx).coerceAtLeast(0f)
    val panelHeightPx = (screenHeightPx * 0.75f).coerceAtLeast(0f)

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

    // ── Expanded panel (full) ──
    AnimatedVisibility(
        visible = state.panelState == PanelState.EXPANDED,
        enter = fadeIn() + scaleIn(initialScale = 0.92f),
        exit = fadeOut() + scaleOut(targetScale = 0.92f),
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                // Tap outside → minimize (NOT close). Only the bubble closes.
                detectTapGestures(onTap = { onMinimize() })
            },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(
                color = MaterialTheme.colorScheme.surface,  // SOLID — no transparency
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 12.dp,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .width(with(density) { panelWidthPx.toDp() })
                    .height(with(density) { panelHeightPx.toDp() })
                    .offset {
                        IntOffset(panelOffset.x.toInt(), panelOffset.y.toInt())
                    }
                    .pointerInput(Unit) {
                        // Consume taps on the panel so the scrim doesn't minimize.
                        detectTapGestures(onTap = {})
                    },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Header (title + minimize + close) ──
                    PanelHeader(
                        onMinimize = onMinimize,
                        onClose = { state.close() },
                    )

                    // ── Tab strip (Material icons, segmented style) ──
                    TabStrip(
                        activeTab = activeTab,
                        onSelect = { activeTab = it },
                    )

                    // ── Tab content ──
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        when (activeTab) {
                            DebugTab.SCREEN -> CurrentScreenContent()
                            DebugTab.DATABASE -> com.confused.anikuta.feature.debugbubble.panel.DatabaseTab(
                                onSelectTable = { /* jump to table */ },
                            )
                            DebugTab.CONSOLE -> com.confused.anikuta.feature.debugbubble.panel.ConsoleTab()
                            DebugTab.NETWORK -> com.confused.anikuta.feature.debugbubble.panel.NetworkTab()
                            DebugTab.APP_INFO -> com.confused.anikuta.feature.debugbubble.panel.AppInfoTab()
                        }
                    }
                }
            }
        }
    }

    // ── Minimized peek bar ──
    AnimatedVisibility(
        visible = state.panelState == PanelState.MINIMIZED,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(onTap = { state.expand() })
        }) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .pointerInput(Unit) { detectTapGestures(onTap = { state.expand() }) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = activeTab.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = "Debug — tap to expand",
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                    IconButton(onClick = { state.close() }) {
                        Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ── Header ──

@Composable
private fun PanelHeader(
    onMinimize: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
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
        IconButton(onClick = onMinimize) {
            Icon(Icons.Filled.Minimize, "Minimize", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ── Tab strip (segmented style with Material icons) ──

@Composable
private fun TabStrip(
    activeTab: DebugTab,
    onSelect: (DebugTab) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DebugTab.values().forEach { tab ->
                val isSelected = tab == activeTab
                val bg = if (isSelected) MaterialTheme.colorScheme.primary
                else androidx.compose.ui.graphics.Color.Transparent
                val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onSelect(tab) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = fg,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = tab.label,
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = fg,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

// ── Current Screen tab content (sectioned, with backgrounds) ──

@Composable
private fun CurrentScreenContent() {
    val context = LocalDebugContext.current
    if (context == null) {
        EmptyState(
            title = "No screen context",
            desc = "This screen doesn't provide debug context. The generic tabs (Database, Console, Network, App Info) are available.",
        )
    } else {
        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Screen name header ──
            SectionCard {
                Text(
                    text = context.screenName,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            // ── Screen data ──
            if (context.screenData.isNotEmpty()) {
                Text(
                    text = "Screen data",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                SectionCard {
                    Column {
                        context.screenData.forEach { (k, v) ->
                            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                Text(
                                    text = k,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.width(110.dp),
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
                }
            }

            // ── Relevant DB rows ──
            if (context.relevantTables.isNotEmpty()) {
                Text(
                    text = "Relevant DB rows",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                context.relevantTables.forEach { ref ->
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { /* DB-3: switch to Database tab pre-filtered */ },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = ref.label,
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                            )
                            Text(
                                text = "${ref.table}.${ref.filterColumn}",
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                            )
                        }
                    }
                }
            }

            // ── Quick actions ──
            if (context.actions.isNotEmpty()) {
                Text(
                    text = "Quick actions",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                )
                context.actions.forEach { action ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clickable { action.action() },
                    ) {
                        Text(
                            text = action.label,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.padding(12.dp)) { content() }
    }
}

@Composable
private fun EmptyState(title: String, desc: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = desc,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ── Helpers ──

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
    val minY = statusBarPx.coerceAtLeast(horizontalPaddingPx)
    val maxY = (screenHeightPx - panelHeightPx - navBarPx).coerceAtLeast(minY)

    val x = when (direction) {
        ExpandDirection.RIGHT_DOWN, ExpandDirection.RIGHT_UP -> {
            (bubbleOffset.x + bubbleSizePx + gapPx)
                .coerceAtMost((screenWidthPx - panelWidthPx - horizontalPaddingPx).coerceAtLeast(horizontalPaddingPx))
        }
        ExpandDirection.LEFT_DOWN, ExpandDirection.LEFT_UP -> {
            (bubbleOffset.x - panelWidthPx - gapPx)
                .coerceAtLeast(horizontalPaddingPx)
        }
    }

    val y = when (direction) {
        ExpandDirection.RIGHT_DOWN, ExpandDirection.LEFT_DOWN -> {
            bubbleOffset.y.coerceIn(minY, maxY)
        }
        ExpandDirection.RIGHT_UP, ExpandDirection.LEFT_UP -> {
            (bubbleOffset.y + bubbleSizePx - panelHeightPx).coerceIn(minY, maxY)
        }
    }

    return Offset(x, y)
}

// ── Constants ──

private val BUBBLE_SIZE = 48.dp
private val PANEL_HORIZONTAL_PADDING = 12.dp
private val PANEL_GAP = 8.dp
