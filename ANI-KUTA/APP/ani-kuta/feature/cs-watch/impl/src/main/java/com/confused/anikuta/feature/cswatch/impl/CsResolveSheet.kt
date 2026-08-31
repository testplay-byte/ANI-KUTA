package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver.CsResolveEvent
import com.confused.anikuta.data.cloudstream.playback.CsSourceMemory
import com.confused.anikuta.feature.cswatch.api.CsWatchKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

private const val SHEET_TAG = "Anikuta:CS:Sheet"

/**
 * Task 53 / RC-6 — the AnymeX-pattern resolve sheet (round 13).
 *
 * Tapping a CloudStream episode on the details page no longer navigates to a
 * fullscreen "Resolving streams" screen: THIS bottom sheet slides up over the
 * details page, streams sources in progressively (the resolver's snapshots),
 * and hands the FULL pre-resolved list to the watch screen on selection —
 * playback starts instantly with no re-resolve.
 *
 * Task 54 (round 14 — UI parity): the sheet now renders in the EXACT visual
 * language of the aniyomi extensions' [ResolverSheet] (same sheet shape,
 * "Episode N" header + circle close, RobotoFamily typography, collapsible
 * server cards with one-open-at-a-time semantics, FlowRow quality chips with
 * the PlayArrow prefix) so a CS episode tap looks identical to an aniyomi
 * episode tap. CS keeps its own behaviors underneath: progressive rows,
 * remembered-server auto-select, single-link auto-select, cancel-on-dismiss.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CsResolveSheet(
    key: CsWatchKey,
    onDismiss: () -> Unit,
    onPlay: (CsWatchKey) -> Unit,
    resolver: CloudstreamLinkResolver = koinInject(),
    sourceMemory: CsSourceMemory = koinInject(),
    viewModel: CsWatchViewModel = koinViewModel(),
) {
    var links by remember { mutableStateOf<List<CsVideoLink>>(emptyList()) }
    var subtitles by remember { mutableStateOf<List<com.confused.anikuta.core.csplayer.CsSubtitle>>(emptyList()) }
    var hiddenCount by remember { mutableIntStateOf(0) }
    var drmCount by remember { mutableIntStateOf(0) }
    var completed by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var autoPicked by remember { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }
    var resolveJob by remember { mutableStateOf<Job?>(null) }

    /** Select + remember + seed + hand off to the watch screen. */
    fun pick(link: CsVideoLink) {
        sourceMemory.remember(key.mainId, link.name)
        Logger.i(SHEET_TAG) {
            "picked: ${link.displayLabel} (remembered for mainId=${key.mainId.take(8)}…)"
        }
        viewModel.seedResolution(
            CsWatchViewModel.PreResolvedSeed(
                key = key,
                links = links,
                subtitles = subtitles,
                selectedLink = link,
                hiddenTorrentCount = hiddenCount,
                unsupportedDrmCount = drmCount,
            ),
        )
        onPlay(key)
    }

    LaunchedEffect(key, retryTick) {
        Logger.i(SHEET_TAG) {
            "resolve: '${key.animeTitle}' EP ${key.episodeNumber} provider=${key.providerName} " +
                "data=${key.episodeData.take(64)}"
        }
        resolveJob = launch {
            resolver.resolve(key.providerName, key.episodeData).collect { event ->
                when (event) {
                    is CsResolveEvent.LinksSnapshot -> {
                        links = event.links
                        hiddenCount = event.hiddenTorrentCount
                        drmCount = event.unsupportedDrmCount
                        // Remembered-server auto-select — fires the moment the
                        // match streams in (the AnymeX instant feel).
                        if (!autoPicked && !completed) {
                            val remembered = sourceMemory.recall(key.mainId)
                            if (remembered != null) {
                                val match = event.links
                                    .filter { it.name == remembered }
                                    .maxByOrNull { it.quality }
                                if (match != null) {
                                    autoPicked = true
                                    Logger.i(SHEET_TAG) {
                                        "auto-select (remembered '$remembered'): ${match.displayLabel}"
                                    }
                                    pick(match)
                                }
                            }
                        }
                    }
                    is CsResolveEvent.SubtitlesSnapshot -> subtitles = event.subtitles
                    is CsResolveEvent.Completed -> {
                        completed = true
                        Logger.i(SHEET_TAG) {
                            "resolve done: links=${event.linkCount} subs=${event.subtitleCount} " +
                                "hiddenTorrent=${event.hiddenTorrentCount} in ${event.durationMs}ms"
                        }
                        // Single-link result → no decision to make.
                        if (!autoPicked && event.linkCount == 1 && links.isNotEmpty()) {
                            autoPicked = true
                            Logger.i(SHEET_TAG) { "auto-select (single link): ${links.first().displayLabel}" }
                            pick(links.first())
                        }
                    }
                    is CsResolveEvent.Failed -> {
                        completed = true
                        failure = event.message
                        Logger.w(SHEET_TAG) { "resolve failed: ${event.message} (linksSoFar=${event.linksSoFar})" }
                    }
                }
            }
        }
    }

    // Disposal (dismiss, navigation, config change) cancels the resolution.
    DisposableEffect(key) {
        onDispose {
            Logger.i(SHEET_TAG) { "sheet disposed — cancelling resolution" }
            resolveJob?.cancel()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxSheetHeight = screenHeight * 0.70f

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
            // ── Header: "Episode N" left, close button right (ResolverSheet parity) ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Episode ${com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(key.episodeNumber)}",
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
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
            // Provider context line (replaces the aniyomi "Tap a server…" hint
            // with the CS-specific fact the user needs: which provider resolves).
            val ccLabel = when {
                subtitles.isNotEmpty() -> " · ${subtitles.size} subtitle track(s)"
                else -> ""
            }
            Text(
                text = "via ${key.providerName} — tap a server to expand, then pick a quality$ccLabel",
                fontFamily = RobotoFamily,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            // ── Content states (ResolverSheet parity wording) ──
            when {
                // Error, nothing to show — the aniyomi ResolverSheet Error card.
                failure != null && links.isEmpty() -> CsSheetErrorCard(
                    title = "Failed to resolve streams",
                    detail = failure ?: "",
                    onRetry = {
                        links = emptyList()
                        subtitles = emptyList()
                        hiddenCount = 0
                        drmCount = 0
                        failure = null
                        completed = false
                        autoPicked = false
                        retryTick++
                    },
                )

                // Still scanning, nothing yet — the aniyomi Loading card.
                links.isEmpty() && !completed -> Box(
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

                // Completed with nothing playable — the aniyomi empty card.
                links.isEmpty() && completed -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "No video sources available",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append("The provider returned no playable links")
                            append(failure?.let { "\n$it" } ?: "")
                            if (hiddenCount > 0) append("\n$hiddenCount torrent link(s) hidden")
                            if (drmCount > 0) append("\n$drmCount DRM link(s) unsupported")
                        },
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = {
                        links = emptyList()
                        subtitles = emptyList()
                        hiddenCount = 0
                        drmCount = 0
                        failure = null
                        completed = false
                        autoPicked = false
                        retryTick++
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // ── The source list (live-updating, ResolverSheet accordion) ──
                else -> {
                    // Group the flat link list into servers (presentation only —
                    // pick() still hands off the FULL flat list).
                    val servers = remember(links) { groupServers(links) }
                    val remembered = remember(key) { sourceMemory.recall(key.mainId) }
                    CsServerAccordion(
                        servers = servers,
                        preferredServer = remembered,
                        onPickVideo = { link -> pick(link) },
                    )
                    if (!completed) {
                        // Progressive footer — CS keeps streaming while picking.
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "Scanning for more sources…",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        val extra = buildString {
                            if (hiddenCount > 0) append(" · $hiddenCount torrent hidden")
                            if (drmCount > 0) append(" · $drmCount DRM unsupported")
                        }
                        Text(
                            "${links.size} source(s) · ${subtitles.size} subtitle track(s)$extra",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Groups the flat CS link list into server rows for the accordion:
 * `link.name` is the provider's server label; each server card lists its
 * links as quality chips (quality-desc, arrival order for ties). Chip labels
 * disambiguate duplicates within a server with the stream type (HLS/DASH).
 */
private fun groupServers(links: List<CsVideoLink>): List<CsServerGroup> =
    links
        .groupBy { it.name }
        .map { (name, groupLinks) ->
            val sorted = groupLinks.sortedByDescending { it.quality }
            val labels = sorted.map { it.qualityLabel }
            CsServerGroup(
                name = name,
                links = sorted,
                disambiguateType = labels.groupingBy { it }.eachCount().any { it.value > 1 },
            )
        }

/** One accordion server: the label + its links (quality-desc). */
private data class CsServerGroup(
    val name: String,
    val links: List<CsVideoLink>,
    /** True when two links of this server share a quality label — chips then
     *  carry the type badge (HLS/DASH) so rows stay distinguishable. */
    val disambiguateType: Boolean,
)

// ════════════════════════════════════════════════════════════════════════════
//  Server accordion — collapsible cards, one open at a time (ResolverSheet parity)
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CsServerAccordion(
    servers: List<CsServerGroup>,
    preferredServer: String?,
    onPickVideo: (CsVideoLink) -> Unit,
) {
    // Track which server is expanded (only one at a time). null = all collapsed.
    // The remembered server (when present) opens FIRST — the CS "remember
    // server" affordance surfaces visually instead of only auto-picking.
    var expandedServer by remember(servers, preferredServer) {
        mutableStateOf(
            servers.firstOrNull { it.name == preferredServer }?.name
                ?: servers.firstOrNull()?.name,
        )
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(servers, key = { it.name }) { server ->
            val isExpanded = expandedServer == server.name
            CsServerCard(
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
private fun CsServerCard(
    server: CsServerGroup,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onPickVideo: (CsVideoLink) -> Unit,
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
            // ── Header row: server name (left) + quality-count chip + chevron ──
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (server.links.size > 1) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(6.dp),
                        ) {
                            Text(
                                text = "${server.links.size}",
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

            // ── Expanded content: quality chips ──
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    server.links.forEach { link ->
                        val label = if (server.disambiguateType) {
                            "${link.qualityLabel} · ${link.type.badgeLabel()}"
                        } else {
                            link.qualityLabel
                        }
                        CsQualityChip(
                            quality = label,
                            onClick = { onPickVideo(link) },
                        )
                    }
                }
            }
        }
    }
}

/** The aniyomi ResolverSheet's chip: PlayArrow + quality, primaryContainer. */
@Composable
private fun CsQualityChip(
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

/** The error card (Retry) in the ResolverSheet's visual language. */
@Composable
private fun CsSheetErrorCard(
    title: String,
    detail: String,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
        }
    }
}

private fun com.confused.anikuta.core.csplayer.CsLinkType.badgeLabel(): String = when (this) {
    com.confused.anikuta.core.csplayer.CsLinkType.VIDEO -> "VIDEO"
    com.confused.anikuta.core.csplayer.CsLinkType.M3U8 -> "HLS"
    com.confused.anikuta.core.csplayer.CsLinkType.DASH -> "DASH"
}
