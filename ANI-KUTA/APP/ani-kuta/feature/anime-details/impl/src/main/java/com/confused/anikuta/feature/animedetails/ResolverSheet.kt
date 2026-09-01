package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public  // D-210: resolver Error "Open in WebView" icon
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.DebugPreferences
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.core.videoresolver.ResolvedVideo
import com.confused.anikuta.core.videoresolver.ResolverDebugReport
import com.confused.anikuta.core.videoresolver.ResolverServer
import com.confused.anikuta.core.videoresolver.ResolverVideo
import org.koin.compose.koinInject
import android.widget.Toast

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
 * Task 58 (round 18 — the BOTH-STACKS debug toolkit): the same gated debug
 * affordances the CloudStream resolve lists gained in round 17 (Task 57 / P4),
 * mirrored onto the ANIYOMI entry sheet — both flags default OFF (zero visual
 * change unless enabled in Settings → Debug options):
 *  - "Copy button" ON → a header-level copy-report action (the whole resolve
 *    report via [ResolverDebugReport]) + a small copy icon on every quality
 *    chip / raw row (that one video's detail block);
 *  - "Show sources" ON → the raw URL + header-keys line under each row.
 * The report builder lives in `:core:video-resolver` (pure Kotlin, shared by
 * BOTH aniyomi sheets); the row chrome stays file-local (the replication rule
 * — no cross-stack imports).
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
    // Task 58 (round 18 — the BOTH-STACKS debug toolkit): report context +
    // the shared DebugPreferences gates. Defaults keep every existing call
    // site compiling; the report omits blank context lines.
    sourceName: String = "",
    animeTitle: String = "",
    debugPreferences: DebugPreferences = koinInject(),
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.70f
    // Task 55: the formatting toggle (read at open; shared pref with the CS
    // sheets — both stacks respect the SAME user choice).
    var formatted by remember { mutableStateOf(playerPreferences.resolveSheetFormatted) }

    // Task 58: the debug gates — LIVE-collected (reactive flows) so toggling
    // the Settings → Debug options while this sheet is open applies without
    // reopening. Both default OFF → zero visual change for normal use.
    val debugCopyEnabled by debugPreferences.resolveCopyButtonFlow()
        .collectAsState(initial = false)
    val debugShowSources by debugPreferences.showResolveSourcesFlow()
        .collectAsState(initial = false)
    val copyFeedback = rememberResolverCopyFeedback()

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
            // ── Header: the distinct bordered formatting toggle (Task 59 —
            // ABOVE the title, its own clearly-bounded control; display-layer
            // only), then the "Episode N" title + close row (plain text — the
            // title is NOT a click target anymore; empty-area header taps do
            // nothing). The v0.4.6 design made the whole title row clickable
            // and popped a DropdownMenu over the episode number.
            ResolverFormattingToggle(
                formatted = formatted,
                onToggle = {
                    formatted = it
                    playerPreferences.resolveSheetFormatted = it
                },
                modifier = Modifier.padding(top = 14.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val headerText = if (downloadMode) {
                    "Download EP ${com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(episodeNumber)}"
                } else {
                    "Episode ${com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(episodeNumber)}"
                }
                Text(
                    text = headerText,
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Task 58: the header-level "copy the whole report" action —
                // OFF unless enabled in Settings → Debug options (the mirror
                // of the CS sheet's header action, same 32dp circle slot).
                if (debugCopyEnabled) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(32.dp)
                            .clickable {
                                val servers = (resolverState as? ResolverState.Success)?.servers
                                if (servers != null) {
                                    copyFeedback(
                                        ResolverDebugReport.buildReport(
                                            sourceName = sourceName,
                                            animeTitle = animeTitle,
                                            episodeNumber = episodeNumber,
                                            servers = servers,
                                        ),
                                        "Report copied",
                                    )
                                } else {
                                    copyFeedback("ANI-KUTA resolve report (aniyomi extensions)\n(no resolved videos yet)", "Nothing to copy")
                                }
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy resolve report",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
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
                            onCopyDetails = if (debugCopyEnabled) {
                                { server, audio, video ->
                                    copyFeedback(
                                        ResolverDebugReport.buildVideoDetail(server, audio, video),
                                        "Copied 1 video details",
                                    )
                                }
                            } else null,
                            showSources = debugShowSources,
                        )
                    } else {
                        // Collapsible server accordion
                        ServerAccordion(
                            servers = servers,
                            onPickVideo = pickVideo,
                            onCopyDetails = if (debugCopyEnabled) {
                                { server, audio, video ->
                                    copyFeedback(
                                        ResolverDebugReport.buildVideoDetail(server, audio, video),
                                        "Copied 1 video details",
                                    )
                                }
                            } else null,
                            showSources = debugShowSources,
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
    // Task 58: the gated debug affordances (null = "Copy button" OFF).
    onCopyDetails: ((ResolverServer, com.confused.anikuta.core.videoresolver.ResolverAudioVersion, com.confused.anikuta.core.videoresolver.ResolverVideo) -> Unit)? = null,
    showSources: Boolean = false,
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
                onCopyDetails = onCopyDetails,
                showSources = showSources,
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
    // Task 58: the gated debug affordances passed through to the chips.
    onCopyDetails: ((ResolverServer, com.confused.anikuta.core.videoresolver.ResolverAudioVersion, com.confused.anikuta.core.videoresolver.ResolverVideo) -> Unit)? = null,
    showSources: Boolean = false,
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
                                        // Task 58: per-chip debug copy + the raw source
                                        // line (both gated, default OFF).
                                        onCopyDetails = onCopyDetails?.let { callback ->
                                            { callback(server, av, video) }
                                        },
                                        sourceDetail = if (showSources) {
                                            video.url
                                        } else null,
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
    // Task 58 (round 18): the debug-toolkit params — BOTH null/absent in
    // normal use (Settings → Debug options flags OFF), so the default chip
    // stays byte-identical to the round-15/16 rendering.
    onCopyDetails: (() -> Unit)? = null,
    sourceDetail: String? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(8.dp),
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
                // Task 58: the per-chip copy icon — RIGHT of the label (the CS
                // chip's placement), 16dp, muted (a debug affordance, not an action).
                if (onCopyDetails != null) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "Copy video details",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier
                            .size(16.dp)
                            .clickable(onClick = onCopyDetails),
                    )
                }
            }
            // Task 58: the raw source line under the label (10sp, end-ellipsized —
            // the CS chip's exact presentation).
            if (sourceDetail != null) {
                Text(
                    text = sourceDetail,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp),
                )
            }
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
 * FIRST-number matching keeps "1080p60" at 1080 (digit concat = 108060).
 */
private fun qualitySortKey(label: String): Int {
    if (label.contains("4K", ignoreCase = true)) return 2160
    if (label.contains("8K", ignoreCase = true)) return 4320
    val digits = Regex("\\d+").find(label)?.value?.toIntOrNull() ?: return -1
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
 *
 * Task 58 (round 18): the gated debug affordances — a trailing copy icon +
 * the raw URL line per row (both OFF unless enabled in Settings → Debug
 * options; the default rows stay byte-identical to round 16).
 */
@Composable
private fun RawVideoList(
    servers: List<ResolverServer>,
    onPickVideo: (com.confused.anikuta.core.videoresolver.ResolverVideo, String, String) -> Unit,
    onCopyDetails: ((ResolverServer, com.confused.anikuta.core.videoresolver.ResolverAudioVersion, com.confused.anikuta.core.videoresolver.ResolverVideo) -> Unit)? = null,
    showSources: Boolean = false,
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = label,
                                    fontFamily = RobotoFamily,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                // Task 58: the raw URL line under the label when
                                // "Show sources" is ON (the CS raw list's exact
                                // presentation — 10sp, muted, end-ellipsized).
                                if (showSources) {
                                    Text(
                                        text = video.url,
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                            // Task 58: the per-row copy icon (trailing, 18dp) when
                            // "Copy button" is ON — copies that one video's block.
                            if (onCopyDetails != null) {
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy video details",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onCopyDetails(server, av, video) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Task 58 (round 18): the aniyomi sheets' copy feedback — the repo precedent
 * (LibraryScreen / DetailsScreen / the CS sheet's own helper) is
 * LocalClipboardManager + [AnnotatedString] with a short Toast confirmation.
 * A file-local copy of the cs-watch helper (the replication rule: no
 * cross-stack imports); PlayerSheets.kt keeps its own twin.
 */
@Composable
private fun rememberResolverCopyFeedback(): (String, String) -> Unit {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    return remember(clipboardManager, context) {
        val copy: (String, String) -> Unit = { text, toast ->
            clipboardManager.setText(AnnotatedString(text))
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
        }
        copy
    }
}


// ════════════════════════════════════════════════════════════════════════════
//  Task 59 (round 19 — display-layer only): the formatting toggle pill
// ════════════════════════════════════════════════════════════════════════════

/**
 * The distinct BORDED formatting toggle shown at the TOP of the resolve
 * sheet — ABOVE the "Episode N" title (the v0.4.6 design made the whole
 * title row clickable and popped a DropdownMenu over the episode number;
 * the round-19 device round: "it should be shown at the top, not on top of
 * the episode number itself but above it… give it a distinct border around
 * it"). Tap toggles the formatted/raw view DIRECTLY — no menu.
 *
 * The CS sheets' [CsFormattingToggle] is the same design (the stacks share
 * the preference, not code — the replication rule).
 */
@Composable
private fun ResolverFormattingToggle(
    formatted: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(50),
            )
            .clickable { onToggle(!formatted) }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Default.FormatListBulleted,
            contentDescription = null,
            tint = if (formatted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Formatted sources",
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (formatted) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        if (formatted) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "On",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
