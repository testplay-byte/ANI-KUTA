package com.confused.anikuta.feature.animedetails

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public  // D-210: resolver Error "Open in WebView" icon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.core.videoresolver.ResolvedVideo
import com.confused.anikuta.core.videoresolver.ResolverServer
import com.confused.anikuta.core.videoresolver.ResolverVideo
import org.koin.compose.koinInject

/**
 * Resolver bottom sheet — shows resolved videos in a collapsible accordion.
 *
 * Design (ported from old project):
 *  - Header: "Episode N" on the left, close button on the right.
 *  - Server cards (collapsible, one open at a time):
 *    - Collapsed: server name (left) + audio version chips (right) + chevron.
 *    - Expanded: FlowRow of quality chips with PlayArrow icon.
 *  - States: Resolving (spinner + "Resolving video sources…"),
 *    NoSources ("No video sources available"), Error (red text + Retry).
 *
 * The user wants: "Instead of directly showing the entries outright, it probably
 * shows them in a properly formatted order with proper collapsible entries and
 * so forth. Only one server can be opened at a time and such."
 *
 * Task 55 (round 15 — ADDITIVE): the "Episode N" header is now tappable →
 * a small popup menu ABOVE it with the source-FORMATTING toggle (shared
 * PlayerPreferences.resolveSheetFormatted with the CloudStream sheets).
 * OFF = a raw flat list: one row per resolved video (server · audio · quality,
 * unformatted labels), tap = pick directly. Default ON. The formatted path is
 * byte-identical to before.
 *
 * CORE_RULES §22: smooth animations (expand/collapse).
 * CORE_RULES §20: logged with tag "Anikuta:Feature:Details:ResolverSheet".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResolverSheet(
    resolverState: ResolverState,
    episodeNumber: Float = 0f,
    downloadMode: Boolean = false,
    // D-151-fix: onPickVideo now carries serverName + audioLabel so the
    // download path stores the actual resolver server (not the extension
    // name) + the audio version the user picked.
    onPickVideo: (ResolvedVideo, String, String) -> Unit,
    // D-210: called when the user taps "Open in WebView" on the Error state.
    // Null = don't show the button. Opens the source's episode page in a WebView
    // so the user can solve Cloudflare / browse the source manually.
    onOpenInWebView: (() -> Unit)? = null,
    onDismiss: () -> Unit,
    playerPreferences: PlayerPreferences = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.70f
    // Task 55: the formatting toggle (read at open; shared pref with the CS
    // sheets — both stacks respect the SAME user choice).
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
            // ── Header: "Episode N" left, close button right ──
            // Task 55: the title is tappable → the formatting popup (a small
            // DropdownMenu ABOVE the anchor — user spec, not a bottom sheet).
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerText = if (downloadMode) {
                    "Download EP ${com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(episodeNumber)}"
                } else {
                    "Episode ${com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(episodeNumber)}"
                }
                var menuOpen by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    Text(
                        text = headerText,
                        fontFamily = RobotoFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { menuOpen = true },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false },
                        offset = DpOffset(0.dp, (-72).dp),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Formatted sources",
                                    fontFamily = RobotoFamily,
                                    fontWeight = FontWeight.Bold,
                                )
                            },
                            leadingIcon = {
                                if (formatted) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "On",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            onClick = {
                                menuOpen = false
                                formatted = !formatted
                                playerPreferences.resolveSheetFormatted = formatted
                            },
                        )
                    }
                }
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

            // ── Content ──
            when (resolverState) {
                is ResolverState.Idle -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No resolution in progress.",
                            fontFamily = RobotoFamily,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is ResolverState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 3.dp,
                                modifier = Modifier.size(32.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                text = "Resolving video sources…",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                is ResolverState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = "Failed to resolve videos",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = resolverState.message,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        // D-210: "Open in WebView" — if the resolve failure is due to
                        // Cloudflare or a page-load issue, the user can open the source's
                        // episode page in a WebView to solve it manually.
                        if (onOpenInWebView != null) {
                            Spacer(Modifier.height(12.dp))
                            androidx.compose.material3.Button(onClick = onOpenInWebView) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Filled.Public,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Open in WebView",
                                    fontFamily = RobotoFamily,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    }
                }

                is ResolverState.Success -> {
                    val servers = resolverState.servers
                    // Task 55: shared pick adapter — BOTH the raw flat list and the
                    // accordion emit ResolverVideo (structured) but onPickVideo
                    // expects the flat ResolvedVideo (carries subtitleTracks for
                    // WatchKey serialization + the D-151-fix server/audio args).
                    // Find the flat twin by URL; fall back to a converted copy.
                    val pickVideo: (ResolverVideo, String, String) -> Unit =
                        { resolverVideo, serverName, audioLabel ->
                            val flatVideo = resolverState.videos.firstOrNull { it.url == resolverVideo.url }
                            if (flatVideo != null) {
                                onPickVideo(flatVideo, serverName, audioLabel)
                            } else {
                                onPickVideo(
                                    ResolvedVideo(
                                        url = resolverVideo.url,
                                        quality = resolverVideo.quality,
                                        directUrl = resolverVideo.url,
                                        headers = resolverVideo.videoHeaders ?: "",
                                        subtitleTracks = resolverVideo.subtitleTracks,
                                        audioTracks = resolverVideo.audioTracks,
                                    ),
                                    serverName,
                                    audioLabel,
                                )
                            }
                        }
                    if (servers.isEmpty()) {
                        // No servers — show "No video sources available"
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "No video sources available",
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "The extension didn't return any playable videos.\nTry a different episode or source.",
                                    fontFamily = RobotoFamily,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    } else if (!formatted) {
                        // Task 55 (raw mode): a flat list — one row per resolved
                        // video (server · audio · quality), tap = pick directly.
                        // The SAME pickVideo adapter the accordion uses.
                        RawVideoList(
                            servers = servers,
                            onPickVideo = pickVideo,
                        )
                    } else {
                        // Collapsible server accordion
                        ServerAccordion(
                            servers = servers,
                            onPickVideo = pickVideo,
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Server accordion — collapsible cards, one open at a time
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ServerAccordion(
    servers: List<ResolverServer>,
    onPickVideo: (com.confused.anikuta.core.videoresolver.ResolverVideo, String, String) -> Unit,
) {
    // Track which server is expanded (only one at a time). null = all collapsed.
    var expandedServer by remember { mutableStateOf<String?>(servers.firstOrNull()?.name) }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(servers, key = { it.name }) { server ->
            val isExpanded = expandedServer == server.name
            ServerCard(
                server = server,
                isExpanded = isExpanded,
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
private fun ServerCard(
    server: ResolverServer,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onPickVideo: (com.confused.anikuta.core.videoresolver.ResolverVideo, String, String) -> Unit,
) {
    Surface(
        color = if (isExpanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            // ── Header row: server name (left) + audio chips (right) + chevron ──
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
                // Audio version chips + chevron — show just the label (SUB, DUB, HSUB).
                // Skip "Default" — if there's only one audio version and it's "Default",
                // don't show any chips (the server name is enough).
                // Reversed so SUB appears rightmost (matches old project's design).
                val audioChips = server.audioVersions.filter { it.label != "Default" }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    audioChips.reversed().forEach { av ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = av.label,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
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

            // ── Expanded content: quality chips per audio version ──
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
                        // Audio version label (e.g. "SUB", "DUB", "HSUB")
                        if (server.audioVersions.size > 1) {
                            Text(
                                text = av.label,
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // Quality chips
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            // Task 56 (round 16 — F2): highest quality LEFTMOST —
                            // parsed height descending, non-numeric labels
                            // ("Default") last. The extension's emission order
                            // is arbitrary; the display layer sorts.
                            av.videos.sortedByDescending { qualitySortKey(it.quality) }
                                .forEach { video ->
                                    QualityChip(
                                        quality = video.quality,
                                        // D-151-fix: pass server.name + av.label so the
                                        // download path stores the real resolver server +
                                        // audio version the user picked.
                                        onClick = { onPickVideo(video, server.name, av.label) },
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
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = quality,
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Task 56 (round 16): the chip-order sort key (F2 — aniyomi side)
// ════════════════════════════════════════════════════════════════════════════

/**
 * The quality label → sort key: "1080p" → 1080, "4K" → 2160, "8K" → 4320;
 * anything non-numeric ("Default") → -1 (sorts LAST — the user's "then any
 * other options" tail). Highest key renders leftmost in the FlowRow.
 */
private fun qualitySortKey(label: String): Int {
    if (label.contains("4K", ignoreCase = true)) return 2160
    if (label.contains("8K", ignoreCase = true)) return 4320
    val digits = label.filter { it.isDigit() }.toIntOrNull() ?: return -1
    return if (digits in 100..4320) digits else -1
}

// ════════════════════════════════════════════════════════════════════════════
//  RAW flat list (Task 55 / round 15 — formatting OFF)
// ════════════════════════════════════════════════════════════════════════════

/**
 * The unformatted view: one row per resolved video — the raw resolver label
 * (server · audio · quality), tap = pick directly (the SAME 3-arg
 * onPickVideo the accordion uses, so the watch/download paths are identical).
 * TrackRow styling to stay in the sheet's design language.
 */
@Composable
private fun RawVideoList(
    servers: List<ResolverServer>,
    onPickVideo: (com.confused.anikuta.core.videoresolver.ResolverVideo, String, String) -> Unit,
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
                    val label = buildString {
                        append(server.name)
                        append(" · ")
                        append(video.quality)
                        if (av.label != "Default") append(" · ${av.label}")
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPickVideo(video, server.name, av.label) },
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
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontFamily = RobotoFamily,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
