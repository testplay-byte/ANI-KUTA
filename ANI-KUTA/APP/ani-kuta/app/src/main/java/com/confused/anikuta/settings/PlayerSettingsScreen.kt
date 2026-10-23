package com.confused.anikuta.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.feature.download.components.DragReorderableList
import com.confused.anikuta.settings.search.rememberSettingsAnchorScroll
import org.koin.compose.koinInject

/**
 * Phase 2: Player Settings screen.
 *
 * Contains the "Auto-select video" master toggle + 4 collapsible preference
 * sections (priority, quality, audio, server) that mirror the auto-download
 * settings. When auto-select is ON, the player auto-resolves the best video
 * using these preferences instead of showing the manual ResolverSheet.
 *
 * Difference from download settings: no "Don't download" global fallback
 * (playback must always pick something — only "Best effort" or "Ask").
 */
@Composable
fun PlayerSettingsScreen(
    onBack: () -> Unit,
    /** D-558: the search-landing anchor (see SettingsSearchNavigator). */
    highlightAnchor: String? = null,
    preferences: PlayerPreferences = koinInject(),
) {
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // Reactive preference reads.
    val autoSelect by preferences.autoSelectVideo.changes.collectAsState(
        initial = preferences.autoSelectVideo.get(),
    )
    val qualities by preferences.preferredQualities.changes.collectAsState(
        initial = preferences.preferredQualities.get(),
    )
    val qualityFallback by preferences.qualityFallback.changes.collectAsState(
        initial = preferences.qualityFallback.get(),
    )
    val audio by preferences.preferredAudio.changes.collectAsState(
        initial = preferences.preferredAudio.get(),
    )
    val audioFallback by preferences.audioFallback.changes.collectAsState(
        initial = preferences.audioFallback.get(),
    )
    val servers by preferences.preferredServers.changes.collectAsState(
        initial = preferences.preferredServers.get(),
    )
    val serverFallback by preferences.serverFallback.changes.collectAsState(
        initial = preferences.serverFallback.get(),
    )
    val priority by preferences.dimensionPriority.changes.collectAsState(
        initial = preferences.dimensionPriority.get(),
    )
    val globalFallback by preferences.globalFallback.changes.collectAsState(
        initial = preferences.globalFallback.get(),
    )

    // ── Round 120 (D-747/D-748): playback behavior settings ───────────────────
    // The round-120 order: the player needed REAL settings beyond auto-select
    // video. Every knob here has a live consumer in the player (before round
    // 120, autoPlayNext + resumeFromLastPosition existed with zero readers).
    // Non-flow prefs (plain getters) use a 0/1 state that mirrors the write.
    var autoPlayNextState by remember { mutableIntStateOf(if (preferences.autoPlayNext) 1 else 0) }
    var resumeState by remember { mutableIntStateOf(if (preferences.resumeFromLastPosition) 1 else 0) }
    val nextUpCard by preferences.nextUpCardEnabled.changes.collectAsState(
        initial = preferences.nextUpCardEnabled.get(),
    )
    val skipIntro by preferences.skipIntroMode.changes.collectAsState(
        initial = preferences.skipIntroMode.get(),
    )
    val skipOutro by preferences.skipOutroMode.changes.collectAsState(
        initial = preferences.skipOutroMode.get(),
    )

    var expandedSection by remember { mutableIntStateOf(0) }

    // ── D-558: the search-landing scroll + AUTO-EXPAND. The four preference
    // sections only exist while auto-select is ON — an anchor targeting one
    // of them expands its section (so the row is visible) and scrolls to it;
    // with auto-select OFF every non-master anchor lands on the master card.
    // Round 120: the LazyColumn indices shifted +1 (the Playback behavior
    // card is item 0) — anchorIndexFor above carries that; these section IDS
    // (1=Priority … 4=Server) did NOT shift and stay 1..4.
    androidx.compose.runtime.LaunchedEffect(highlightAnchor, autoSelect) {
        when (highlightAnchor) {
            "player_priority" -> if (autoSelect) expandedSection = 1
            "player_quality" -> if (autoSelect) expandedSection = 2
            "player_audio" -> if (autoSelect) expandedSection = 3
            "player_server" -> if (autoSelect) expandedSection = 4
        }
    }
    rememberSettingsAnchorScroll(
        anchor = highlightAnchor,
        anchorIndexFor = { anchor ->
            when {
                anchor == "player_behavior" || anchor == "player_autoplay" ||
                    anchor == "player_resume" || anchor == "player_next_up" ||
                    anchor == "player_skip" || anchor == "player_skip_intro" ||
                    anchor == "player_skip_outro" || anchor == "player" -> 0
                anchor == "player_autoselect" -> 1
                !autoSelect -> 1
                anchor == "player_priority" -> 2
                anchor == "player_quality" -> 3
                anchor == "player_audio" -> 4
                anchor == "player_server" -> 5
                anchor == "player_global_fallback" -> 6
                else -> null
            }
        },
        listState = lazyListState,
    )

    Box(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxWidth()) {
            CollapsingHeader(
                title = "Player",
                collapsed = collapsed,
                onBack = onBack,
            )

            Box(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 110.dp),
                ) {
                    // ── Round 120 (D-747/D-748): Playback behavior ──────────────
                    // The real player settings hub — every row below has a live
                    // consumer in the player (see WatchScreen's round-120 block).
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(
                                    "Playback behavior",
                                    fontFamily = RobotoFamily,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                SettingsSwitchRow(
                                    title = "Auto-play next episode",
                                    subtitle = "Plays the next episode when this one ends",
                                    checked = autoPlayNextState == 1,
                                    onCheckedChange = {
                                        preferences.autoPlayNext = it
                                        autoPlayNextState = if (it) 1 else 0
                                    },
                                )
                                SettingsSwitchRow(
                                    title = "Next-episode card",
                                    subtitle = "Shows the next episode near the end",
                                    checked = nextUpCard,
                                    onCheckedChange = { preferences.nextUpCardEnabled.set(it) },
                                )
                                SettingsSwitchRow(
                                    title = "Resume playback",
                                    subtitle = "Continues episodes from where you left off",
                                    checked = resumeState == 1,
                                    onCheckedChange = {
                                        preferences.resumeFromLastPosition = it
                                        resumeState = if (it) 1 else 0
                                    },
                                )
                                Text(
                                    "Skip intro",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp, bottom = 6.dp),
                                )
                                SegmentedRow(
                                    options = listOf(
                                        "Off" to (skipIntro == "OFF"),
                                        "Button" to (skipIntro == "BUTTON"),
                                        "Auto" to (skipIntro == "AUTO"),
                                    ),
                                ) { idx ->
                                    preferences.skipIntroMode.set(
                                        when (idx) { 0 -> "OFF"; 1 -> "BUTTON"; else -> "AUTO" },
                                    )
                                }
                                Text(
                                    "Skip outro",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                                )
                                SegmentedRow(
                                    options = listOf(
                                        "Off" to (skipOutro == "OFF"),
                                        "Button" to (skipOutro == "BUTTON"),
                                        "Auto" to (skipOutro == "AUTO"),
                                    ),
                                ) { idx ->
                                    preferences.skipOutroMode.set(
                                        when (idx) { 0 -> "OFF"; 1 -> "BUTTON"; else -> "AUTO" },
                                    )
                                }
                                Text(
                                    "Mark intros and outros from the player's intro button",
                                    fontFamily = RobotoFamily,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 10.dp),
                                )
                            }
                        }
                    }

                    // ── Master toggle: Auto-select video ──
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.PlayCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp).padding(end = 4.dp),
                                )
                                Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                                    Text(
                                        "Auto-select video",
                                        fontFamily = RobotoFamily,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "Automatically pick the best server, audio, and quality",
                                        fontFamily = RobotoFamily,
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                Switch(
                                    checked = autoSelect,
                                    onCheckedChange = { preferences.autoSelectVideo.set(it) },
                                )
                            }
                        }
                    }

                    // ── Preference sections (only when auto-select is ON) ──
                    if (autoSelect) {
                        item {
                            CollapsibleSection(
                                title = "Priority order",
                                subtitle = "drag to re-order",
                                isExpanded = expandedSection == 1,
                                onToggle = { expandedSection = if (expandedSection == 1) 0 else 1 },
                            ) {
                                DragReorderableList(
                                    items = priority,
                                    onReorder = { newOrder -> preferences.dimensionPriority.set(newOrder) },
                                )
                            }
                        }

                        item {
                            CollapsibleSection(
                                title = "Preferred quality",
                                subtitle = "drag to re-order",
                                isExpanded = expandedSection == 2,
                                onToggle = { expandedSection = if (expandedSection == 2) 0 else 2 },
                            ) {
                                DragReorderableList(
                                    items = qualities,
                                    onReorder = { newOrder -> preferences.preferredQualities.set(newOrder) },
                                )
                                Spacer(12)
                                FallbackToggle(
                                    label = "If unavailable",
                                    strategy = qualityFallback,
                                    onSelect = { preferences.qualityFallback.set(it) },
                                )
                            }
                        }

                        item {
                            CollapsibleSection(
                                title = "Preferred audio",
                                subtitle = "drag to re-order",
                                isExpanded = expandedSection == 3,
                                onToggle = { expandedSection = if (expandedSection == 3) 0 else 3 },
                            ) {
                                DragReorderableList(
                                    items = audio,
                                    onReorder = { newOrder -> preferences.preferredAudio.set(newOrder) },
                                )
                                Spacer(12)
                                FallbackToggle(
                                    label = "If unavailable",
                                    strategy = audioFallback,
                                    onSelect = { preferences.audioFallback.set(it) },
                                )
                            }
                        }

                        item {
                            CollapsibleSection(
                                title = "Preferred server",
                                subtitle = "drag to re-order",
                                isExpanded = expandedSection == 4,
                                onToggle = { expandedSection = if (expandedSection == 4) 0 else 4 },
                            ) {
                                DragReorderableList(
                                    items = servers,
                                    onReorder = { newOrder -> preferences.preferredServers.set(newOrder) },
                                )
                                Spacer(12)
                                FallbackToggle(
                                    label = "If unavailable",
                                    strategy = serverFallback,
                                    onSelect = { preferences.serverFallback.set(it) },
                                )
                            }
                        }

                        item {
                            PlaybackGlobalFallbackToggle(
                                strategy = globalFallback,
                                onSelect = { preferences.globalFallback.set(it) },
                            )
                        }
                    }
                }

                ScrollBlurOverlay(
                    scrollOffset = {
                        if (lazyListState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                        else lazyListState.firstVisibleItemScrollOffset.toFloat()
                    },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

@Composable
private fun Spacer(height: Int) {
    androidx.compose.foundation.layout.Spacer(Modifier.size(height.dp))
}

/** Round 120: a titled switch row inside the Playback behavior card. */
@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                subtitle,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun CollapsibleSection(
    title: String,
    subtitle: String,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        title, fontFamily = RobotoFamily, fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        subtitle, fontFamily = RobotoFamily, fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp).rotate(if (isExpanded) 90f else 0f),
                    )
                }
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun FallbackToggle(
    label: String,
    strategy: String,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            label, fontFamily = RobotoFamily, fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val options = listOf(
            "Try next" to (strategy == "TRY_NEXT"),
            "Don't" to (strategy == "DONT"),
        )
        SegmentedRow(options = options) { idx ->
            onSelect(if (idx == 0) "TRY_NEXT" else "DONT")
        }
    }
}

/**
 * 2-way global fallback for playback: "Best effort" / "Ask".
 * (No "Don't download" option — playback must always pick something.)
 */
@Composable
private fun PlaybackGlobalFallbackToggle(
    strategy: String,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(
            "If no preferences match",
            fontFamily = RobotoFamily, fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        val options = listOf(
            "Best effort" to (strategy == "BEST_EFFORT"),
            "Ask" to (strategy == "ASK"),
        )
        SegmentedRow(options = options) { idx ->
            onSelect(if (idx == 0) "BEST_EFFORT" else "ASK")
        }
    }
}

@Composable
private fun SegmentedRow(options: List<Pair<String, Boolean>>, onSelect: (Int) -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            options.forEachIndexed { idx, (label, selected) ->
                val bg = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
                val fg = if (selected) MaterialTheme.colorScheme.onPrimary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Surface(
                    color = bg, shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp))
                        .clickable { onSelect(idx) },
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label, fontFamily = RobotoFamily, fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = fg,
                        )
                    }
                }
            }
        }
    }
}
