package com.confused.anikuta.feature.debugbubble

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material.icons.filled.Minimize
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.ui.graphics.Color
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

// ── Fixed debug-bubble colors (same in both light + dark mode, per user) ──
// Pink + yellow mixture, darkened. Background = dark coral/rose (#6B2D2D).
// Cards/elements = lighter sienna (#8B4A3A). Text = cream (#F5E6D3).
// Borders = amber (#D4A574) for clear visibility.
// Console keeps a constant dark bg (separate from these).
private val DebugBgColor = Color(0xFF6B2D2D)       // dark coral (pink+yellow darkened)
private val DebugCardColor = Color(0xFF8B4A3A)      // lighter sienna for cards
private val DebugFgColor = Color(0xFFF5E6D3)        // cream text (light yellow)
private val DebugFgColorVariant = Color(0xFFF5E6D3).copy(alpha = 0.7f)
private val DebugBorderColor = Color(0xFFD4A574)    // amber border
private val DebugSectionLabelColor = Color(0xFFE8C170)  // golden section labels

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
    // When the user taps a "View in DB" button on the Screen tab, this stores
    // the table to open + the search filter. The DatabaseTab reads it on open.
    var pendingDbTable by remember { mutableStateOf<String?>(null) }
    var pendingDbFilter by remember { mutableStateOf("") }

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
                color = DebugBgColor,  // Fixed color (same in both themes)
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 12.dp,
                tonalElevation = 0.dp,  // no tonal elevation — fixed color
                border = androidx.compose.foundation.BorderStroke(1.5.dp, DebugBorderColor),
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
                    // ── Tab strip (Material icons, segmented style) ──
                    // No header (close/minimize/debug text removed per user).
                    // The bubble (outside the panel) handles close (tap when expanded).
                    // Tap-outside minimizes.
                    TabStrip(
                        activeTab = activeTab,
                        onSelect = { activeTab = it },
                    )

                    // ── Tab content ──
                    // NO outer verticalScroll — each tab manages its own scrolling
                    // (Database/Console/Network use LazyColumn; Screen/AppInfo use
                    // their own verticalScroll). Nesting verticalScroll + LazyColumn
                    // causes "infinity maximum height constraints" crash.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        when (activeTab) {
                            DebugTab.SCREEN -> CurrentScreenContent(
                                onViewInDb = { table, filterCol, filterVal ->
                                    // Navigate to the Database tab + open the table
                                    // pre-filtered to the relevant row.
                                    pendingDbTable = table
                                    pendingDbFilter = filterVal
                                    activeTab = DebugTab.DATABASE
                                },
                            )
                            DebugTab.DATABASE -> com.confused.anikuta.feature.debugbubble.panel.DatabaseTab(
                                initialTable = pendingDbTable,
                                initialSearch = pendingDbFilter,
                                onSelectTable = { },
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

    // ── Minimized mini-window (portrait, half-width, draggable, live content) ──
    // Per user: no header/text/buttons. Top strip = drag-and-drop area (tap to
    // expand). Content below = live data (scrollable, no buttons).
    AnimatedVisibility(
        visible = state.panelState == PanelState.MINIMIZED,
        enter = fadeIn() + scaleIn(initialScale = 0.85f),
        exit = fadeOut() + scaleOut(targetScale = 0.85f),
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val miniWidthPx = (screenWidthPx * 0.5f).coerceAtLeast(0f)
            val miniHeightPx = (screenHeightPx * 0.4f).coerceAtLeast(0f)
            Surface(
                color = DebugBgColor,  // Fixed color (same in both themes)
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                tonalElevation = 0.dp,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, DebugBorderColor),
                modifier = Modifier
                    .width(with(density) { miniWidthPx.toDp() })
                    .height(with(density) { miniHeightPx.toDp() })
                    .offset {
                        IntOffset(panelOffset.x.toInt(), panelOffset.y.toInt())
                    },
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // ── Drag handle strip (draggable + tap-to-expand) ──
                    // No text, no buttons. Just a colored strip that:
                    // - tap → expand
                    // - drag → move the mini-window
                    Surface(
                        color = DebugFgColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(24.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { state.expand() })
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragEnd = { },
                                    onDragCancel = { },
                                ) { change, dragAmount ->
                                    change.consume()
                                    state.updateOffset(
                                        clampMiniOffset(
                                            state.offset + dragAmount,
                                            screenWidthPx, screenHeightPx, miniWidthPx, miniHeightPx,
                                            statusBarPx, navBarPx,
                                        ),
                                    )
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Small drag indicator bar.
                            Box(
                                modifier = Modifier
                                    .width(32.dp)
                                    .height(4.dp)
                                    .background(
                                        DebugFgColorVariant.copy(alpha = 0.4f),
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                    // ── Live content (scrollable, no buttons) ──
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        when (activeTab) {
                            DebugTab.SCREEN -> CurrentScreenContent(
                                onViewInDb = { _, _, _ -> state.expand() },
                            )
                            DebugTab.DATABASE -> com.confused.anikuta.feature.debugbubble.panel.DatabaseTab(
                                onSelectTable = { },
                            )
                            DebugTab.CONSOLE -> com.confused.anikuta.feature.debugbubble.panel.ConsoleTab(minimized = true)
                            DebugTab.NETWORK -> com.confused.anikuta.feature.debugbubble.panel.NetworkTab(minimized = true)
                            DebugTab.APP_INFO -> com.confused.anikuta.feature.debugbubble.panel.AppInfoTab()
                        }
                    }
                }
            }
        }
    }
}

// ── Helpers ──

/** Clamp the mini-window offset to keep it fully on-screen. */
private fun clampMiniOffset(
    offset: Offset,
    screenWidthPx: Float,
    screenHeightPx: Float,
    miniWidthPx: Float,
    miniHeightPx: Float,
    statusBarPx: Float,
    navBarPx: Float,
): Offset {
    val minX = 0f
    val maxX = (screenWidthPx - miniWidthPx).coerceAtLeast(0f)
    // Allow the mini-window to go to the very bottom (just a small 4dp margin
    // for the nav bar). Per user: "unable to drag it to the very bottom."
    val minY = statusBarPx
    // Allow going to the very bottom — only a tiny 8px margin (not the full nav bar).
    val maxY = (screenHeightPx - miniHeightPx - 8f).coerceAtLeast(minY)
    return Offset(
        x = offset.x.coerceIn(minX, maxX),
        y = offset.y.coerceIn(minY, maxY),
    )
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
            color = DebugFgColor,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onMinimize) {
            Icon(Icons.Filled.Minimize, "Minimize", tint = DebugFgColorVariant)
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, "Close", tint = DebugFgColorVariant)
        }
    }
}

// ── Tab strip (horizontally scrollable, Material icons) ──

@Composable
private fun TabStrip(
    activeTab: DebugTab,
    onSelect: (DebugTab) -> Unit,
) {
    Surface(
        color = DebugCardColor.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(DebugTab.values().toList()) { tab ->
                val isSelected = tab == activeTab
                val bg = if (isSelected) DebugFgColor else Color.Transparent
                val fg = if (isSelected) DebugBgColor else DebugFgColorVariant
                Surface(
                    color = bg,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .clickable { onSelect(tab) },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
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

// ── Current Screen tab content (sectioned, with backgrounds + spacing) ──

@Composable
private fun CurrentScreenContent(
    onViewInDb: (table: String, filterCol: String, filterVal: String) -> Unit,
) {
    val context = LocalDebugContext.current
    val ctx = androidx.compose.ui.platform.LocalContext.current
    if (context == null) {
        EmptyState(
            title = "No screen context",
            desc = "This screen doesn't provide debug context. The generic tabs (Database, Console, Network, App Info) are available.",
        )
    } else {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            SectionCard {
                Text(
                    text = context.screenName,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DebugFgColor,
                )
            }

            // ── Screen data (with generous spacing between items) ──
            if (context.screenData.isNotEmpty()) {
                Text(
                    text = "Screen data",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DebugSectionLabelColor,  // golden section labels
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
                SectionCard {
                    Column {
                        context.screenData.entries.forEachIndexed { idx, (k, v) ->
                            // Tap-to-copy: tapping a data row copies the value.
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp)
                                    .clickable {
                                        val cm = ctx.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                        cm.setPrimaryClip(android.content.ClipData.newPlainText(k, v))
                                    },
                            ) {
                                Text(
                                    text = k,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = DebugFgColorVariant,
                                    modifier = Modifier.width(120.dp),
                                )
                                Text(
                                    text = v,
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = DebugFgColor,
                                )
                            }
                            // Separator line between data items (not after the last).
                            if (idx < context.screenData.size - 1) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(1.dp)
                                        .background(DebugBorderColor.copy(alpha = 0.2f)),
                                )
                            }
                        }
                    }
                }
            }

            // ── Relevant DB rows (clickable → navigates to Database tab) ──
            if (context.relevantTables.isNotEmpty()) {
                Text(
                    text = "Relevant DB rows",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = DebugSectionLabelColor,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
                context.relevantTables.forEach { ref ->
                    Surface(
                        color = DebugCardColor,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DebugBorderColor.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onViewInDb(ref.table, ref.filterColumn, ref.filterValue) },
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Storage,
                                contentDescription = null,
                                tint = DebugFgColor,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = ref.label,
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = DebugFgColor,
                                modifier = Modifier.padding(start = 8.dp).weight(1f),
                            )
                            Text(
                                text = "${ref.table}.${ref.filterColumn}",
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.7f),
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
                    color = DebugSectionLabelColor,
                    modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
                )
                context.actions.forEach { action ->
                    Surface(
                        color = DebugCardColor,
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DebugBorderColor.copy(alpha = 0.3f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { action.action() },
                    ) {
                        Text(
                            text = action.label,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = DebugFgColor,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
    // Card on the fixed-color panel: sienna (lighter than the coral bg).
    Surface(
        color = DebugCardColor,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DebugBorderColor.copy(alpha = 0.5f)),
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
            color = DebugFgColor,
        )
        Text(
            text = desc,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            color = DebugFgColorVariant,
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
