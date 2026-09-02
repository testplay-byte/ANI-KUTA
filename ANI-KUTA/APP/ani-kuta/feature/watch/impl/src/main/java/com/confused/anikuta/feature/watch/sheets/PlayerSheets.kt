package com.confused.anikuta.feature.watch

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.player.VideoTrack
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.core.videoresolver.ResolverAudioVersion
import com.confused.anikuta.core.videoresolver.ResolverServer
import com.confused.anikuta.core.videoresolver.ResolverVideo
import org.koin.compose.koinInject

/**
 * Subtitle tracks bottom sheet — shows available subtitle tracks + "Off" option
 * + a "Subtitle Settings" navigation row.
 *
 * The "Off" entry is shown EXACTLY ONCE (not duplicated). The tracks list from
 * [AnikutaMPVView.loadTracks] does NOT include an "Off" entry — this sheet
 * adds it as the first item.
 *
 * @param tracks Available subtitle tracks (from MPV's track-list, NO "Off" entry).
 * @param currentTrackId The currently-selected track ID (-1 = Off).
 * @param onTrackSelected Called when a track is selected (id = -1 for Off).
 * @param onOpenSettings Called when the user taps "Subtitle Settings".
 * @param onDismiss Close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubtitleTracksSheet(
    tracks: List<VideoTrack>,
    currentTrackId: Int,
    onTrackSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onRefreshTracks: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.70f

    // Refresh tracks when the sheet opens — catches cases where tracks were
    // loaded before FILE_LOADED completed, or where the user opens the sheet
    // after external subs have finished downloading.
    LaunchedEffect(Unit) { onRefreshTracks() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(top = 20.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Subtitles",
                    fontFamily = RobotoFamily,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.size(32.dp).clickable(onClick = onDismiss),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 20.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            // ── "Subtitle Settings" navigation row — ALWAYS shown ──
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { onOpenSettings() },
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(32.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                        Text(
                            text = "Subtitle Settings",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── Track list ──
            // CRITICAL: Always render the "Off" entry, even when tracks is empty.
            // The user said: "I don't even see the OFF option in the subtitles."
            // Previously, "Off" was inside the `else` branch — hidden when empty.
            // Now we always show it as the first item, then show the "no subtitles"
            // message OR the actual tracks below it.
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // "Off" option — ALWAYS shown (first entry).
                item(key = "off") {
                    TrackRow(
                        label = "Off",
                        isSelected = currentTrackId <= 0,
                        onClick = {
                            onTrackSelected(-1)
                            onDismiss()
                        },
                    )
                }
                // If no tracks, show a helpful message (below "Off").
                if (tracks.isEmpty()) {
                    item(key = "empty-msg") {
                        Text(
                            text = "No subtitles found in this stream.\nThe extension may not provide external subtitles.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp),
                        )
                    }
                } else {
                    // Actual tracks
                    items(tracks.size) { index ->
                        val track = tracks[index]
                        TrackRow(
                            label = track.name,
                            isSelected = track.id == currentTrackId,
                            onClick = {
                                onTrackSelected(track.id)
                                onDismiss()
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * Quality & Servers bottom sheet — flat list matching the ResolverSheet UI.
 *
 * Per user spec: "What should be shown here is the exact same thing which it
 * would show me after resolving the episodes." This means a FLAT list of all
 * available videos (not an accordion). Each row shows:
 * - Quality label (e.g. "1080p")
 * - Server name + audio version (e.g. "Vidstream · SUB")
 * - Selected state: primary border + check icon
 *
 * The user taps a video → [onQualitySelected] is called → the watch screen
 * re-loads MPV with the new URL.
 *
 * @param servers The full resolved server hierarchy (from ResolvedVideosRegistry).
 * @param currentVideoTitle The videoTitle of the currently-playing video.
 * @param onQualitySelected Called when the user picks a video.
 * @param onDismiss Close the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QualitySheet(
    servers: List<ResolverServer>,
    currentVideoTitle: String,
    onQualitySelected: (ResolverVideo) -> Unit,
    onDismiss: () -> Unit,
    currentServerName: String = "",
    currentAudioVersion: String = "",
    playerPreferences: PlayerPreferences = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.70f
    // Task 55 (round 15 — ADDITIVE): the formatting toggle (shared pref with
    // the CS sheets + the ResolverSheet; read at open).
    var formatted by remember { mutableStateOf(playerPreferences.resolveSheetFormatted) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            // ── Header (Task 60 — round 20 — display-layer only): the
            // "Qualities and Servers" HEADING carries the formatting control
            // — tapping the TITLE TEXT pops the small bordered menu with the
            // Formatted-sources toggle (see [WatchFormattingTitle] at the
            // bottom of this file); the round-19 pill above the title is gone.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WatchFormattingTitle(
                    title = "Qualities and Servers",
                    formatted = formatted,
                    onToggle = {
                        formatted = it
                        playerPreferences.resolveSheetFormatted = it
                    },
                    modifier = Modifier.weight(1f),
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = CircleShape,
                    modifier = Modifier
                        .size(32.dp)
                        .clickable(onClick = onDismiss),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            // Subtitle
            Text(
                text = "Tap a server to expand, then pick a quality.",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // ── Server accordion (same design as ResolverSheet) — or the RAW
            //    flat list when formatting is OFF (Task 55) ──
            if (servers.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No video sources available",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            } else if (!formatted) {
                // Task 55 (raw mode): flat rows — one per video, the raw
                // label + the current highlight.
                RawVideoRows(
                    servers = servers,
                    currentVideoTitle = currentVideoTitle,
                    onQualitySelected = onQualitySelected,
                    onDismiss = onDismiss,
                )
            } else {
                QualityServerAccordion(
                    servers = servers,
                    currentVideoTitle = currentVideoTitle,
                    onPickVideo = { video ->
                        onQualitySelected(video)
                        onDismiss()
                    },
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  RAW flat list (Task 55 / round 15 — formatting OFF)
// ══════════════════════════════════════════════════════════════════════

/**
 * The unformatted view: one row per resolved video — the raw resolver label
 * (server · audio · quality) with the current-video highlight. Tap = pick.
 *
 */
@Composable
private fun RawVideoRows(
    servers: List<ResolverServer>,
    currentVideoTitle: String,
    onQualitySelected: (ResolverVideo) -> Unit,
    onDismiss: () -> Unit,
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        servers.forEach { server ->
            server.audioVersions.forEach { av ->
                // Task 56 (F5): the key carries the row INDEX — an extension that
                // emits the same URL twice (multi-quality DASH manifests) must
                // never crash with duplicate LazyColumn keys (the round-16
                // device crash: Key "Default|Default|https://…mpd").
                itemsIndexed(
                    av.videos,
                    key = { index, video -> "${server.name}|${av.label}|${video.url}|$index" },
                ) { _, video ->
                    val isCurrent = video.videoTitle == currentVideoTitle
                    val label = buildString {
                        append(server.name)
                        append(" · ")
                        append(video.quality)
                        if (av.label != "Default") append(" · ${av.label}")
                    }
                    Surface(
                        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp),
                        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onQualitySelected(video)
                                onDismiss()
                            },
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isCurrent) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualityServerAccordion(
    servers: List<ResolverServer>,
    currentVideoTitle: String,
    onPickVideo: (ResolverVideo) -> Unit,
) {
    var expandedServer by remember {
        mutableStateOf<String?>(
            servers.firstOrNull { server ->
                server.audioVersions.any { av ->
                    av.videos.any { it.videoTitle == currentVideoTitle }
                }
            }?.name ?: servers.firstOrNull()?.name
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(servers, key = { it.name }) { server ->
            val isExpanded = expandedServer == server.name
            QualityServerCard(
                server = server,
                isExpanded = isExpanded,
                currentVideoTitle = currentVideoTitle,
                onToggle = {
                    expandedServer = if (isExpanded) null else server.name
                },
                onPickVideo = onPickVideo,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QualityServerCard(
    server: ResolverServer,
    isExpanded: Boolean,
    currentVideoTitle: String,
    onToggle: () -> Unit,
    onPickVideo: (ResolverVideo) -> Unit,
) {
    val hasCurrentVideo = server.audioVersions.any { av ->
        av.videos.any { it.videoTitle == currentVideoTitle }
    }

    Surface(
        color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = server.name,
                    fontFamily = RobotoFamily,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val audioChips = server.audioVersions.filter { it.label != "Default" }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    audioChips.reversed().forEach { av ->
                        val isCurrentAudio = av.videos.any { it.videoTitle == currentVideoTitle }
                        Surface(
                            color = if (isCurrentAudio) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = av.label,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCurrentAudio) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    server.audioVersions.forEach { av ->
                        if (server.audioVersions.size > 1) {
                            Text(
                                text = av.label,
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Task 56 (round 16 — F2): highest quality LEFTMOST —
                            // parsed height descending, non-numeric labels
                            // ("Default") last. Mirrors the ResolverSheet sort.
                            av.videos.sortedByDescending { qualitySortKey(it.quality) }
                                .forEach { video ->
                                    val isCurrent = video.videoTitle == currentVideoTitle
                                    QualityChip(
                                        quality = video.quality,
                                        isSelected = isCurrent,
                                        onClick = { onPickVideo(video) },
                                    )
                                }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityChip(
    quality: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = quality,
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                           else MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════
//  Task 56 (round 16): the chip-order sort key (F2 — aniyomi watch side)
// ════════════════════════════════════════════════════════════════════════

/**
 * The quality label → sort key: "1080p" → 1080, "4K" → 2160, "8K" → 4320;
 * anything non-numeric ("Default") → -1 (sorts LAST — the user's "then any
 * other options" tail). Highest key renders leftmost in the FlowRow.
 * (The ResolverSheet keeps its own copy — the replication rule.)
 * FIRST-number matching keeps "1080p60" at 1080 (digit concat = 108060).
 */
private fun qualitySortKey(label: String): Int {
    if (label.contains("4K", ignoreCase = true)) return 2160
    if (label.contains("8K", ignoreCase = true)) return 4320
    val digits = Regex("\\d+").find(label)?.value?.toIntOrNull() ?: return -1
    return if (digits in 100..4320) digits else -1
}


// ════════════════════════════════════════════════════════════════════════════
//  Task 60 (round 20 — display-layer only): the formatting menu on the heading
// ════════════════════════════════════════════════════════════════════════════

/**
 * Task 60 (round 20 — the v0.4.7 device round): the formatting control lives
 * ON the in-player sheet's HEADING — the round-19 design was a standalone
 * "Formatted sources" pill above the title, and the user's spec superseded
 * it: "when I click on the [episode] heading… it should open up a small menu
 * with a distinct border around it; in that I should be able to toggle it on
 * and off."
 *
 *  - the TITLE TEXT is the only touch target (intrinsic width, ellipsized);
 *  - tapping it pops a small menu floating ABOVE the heading (Task 62, round
 *    22 — the user's "it was supposed to show above it … outside the bottom
 *    menu"): a custom PopupPositionProvider sits the menu's BOTTOM edge a
 *    8dp gap ABOVE the heading's TOP edge, so it never covers the heading
 *    and floats fully OUTSIDE the sheet (over the scrim).
 *    A 14dp-cornered, flat surface with a DISTINCT 1dp outline border and ONE
 *    row — "Format sources" + a trailing Switch (exact label + guaranteed
 *    24dp gap between the label and the toggle, per the round-21 spec);
 *  - toggling (the row OR the switch) flips the formatted/raw view and the
 *    menu STAYS OPEN; outside-tap or back dismisses
 *    (PopupProperties(focusable = true)).
 *
 * The same design as the entry sheets' heading components (the stacks share
 * the preference, not code — the replication rule).
 */
@Composable
private fun WatchFormattingTitle(
    title: String,
    formatted: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Task 62 (round 22 — the menu POSITION rework): the round-21
    // Popup(Alignment.BottomStart) sat the menu's BOTTOM edge on the
    // heading's BOTTOM edge — i.e. the menu covered the heading itself
    // (the "Episode N" text) instead of sitting above it. The device spec:
    // "it was supposed to show above it … and it was supposed to show
    // outside the bottom menu". A custom PopupPositionProvider now places
    // the menu's BOTTOM edge a 8dp gap ABOVE the heading's TOP edge — since
    // the heading is only ~16-30dp below the sheet's top edge, the menu
    // floats fully OUTSIDE the sheet, over the scrim (the ModalBottomSheet's
    // dialog window is full-screen MATCH_PARENT, and a Popup is a real
    // TYPE_APPLICATION_SUB_PANEL sub-window of it — it renders above the
    // sheet's content, clipped only to the screen).
    val menuGapPx = with(LocalDensity.current) { 8.dp.roundToPx() }
    // NOTE: PopupPositionProvider is a plain (non-fun) interface in Compose
    // UI 1.10 — a SAM-constructor lambda does NOT compile; an object
    // expression implements it.
    val aboveAnchorPositionProvider = remember(menuGapPx) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset = IntOffset(
                x = anchorBounds.left,
                y = anchorBounds.top - popupContentSize.height - menuGapPx,
            )
        }
    }
    Box(modifier = modifier) {
        // ONLY the title text opens the menu — the user's spec.
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { menuOpen = true },
        )
        // The small menu with the DISTINCT border (shape + flat surface +
        // 1dp outline via Modifier.border — version-proof on material3),
        // floating ABOVE the heading — fully outside the sheet.
        if (menuOpen) {
            Popup(
                popupPositionProvider = aboveAnchorPositionProvider,
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = true),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .widthIn(min = 220.dp)
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = RoundedCornerShape(14.dp),
                        ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(!formatted) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Format sources",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // Round 21: a GUARANTEED 24dp gap between the label and
                        // the toggle (a weight-only spacer collapses to zero
                        // when the row wraps its content) + the weight fills
                        // whatever the 220dp minimum menu width leaves.
                        Spacer(Modifier.width(24.dp))
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = formatted,
                            onCheckedChange = { onToggle(it) },
                        )
                    }
                }
            }
        }
    }
}
