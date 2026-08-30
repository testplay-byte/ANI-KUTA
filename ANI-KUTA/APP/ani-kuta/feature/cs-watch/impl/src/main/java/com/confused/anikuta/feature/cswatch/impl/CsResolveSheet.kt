package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.csplayer.CsLinkType
import com.confused.anikuta.core.csplayer.CsSubtitle
import com.confused.anikuta.core.csplayer.CsVideoLink
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
 * Auto-select rules (the AnymeX "Remember Server" feel):
 *  - the remembered server for this anime (per-mainId, name-sans-quality
 *    match) auto-selects THE MOMENT it streams in;
 *  - a single-link result auto-selects on completion;
 *  - otherwise the user picks; dismissing cancels the resolution.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    var subtitles by remember { mutableStateOf<List<CsSubtitle>>(emptyList()) }
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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp),
        ) {
            // ── Header ──────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Episode ${key.episodeNumber.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = key.animeTitle,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                text = "via ${key.providerName} — pick a source to start watching",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
            Spacer(Modifier.height(14.dp))

            when {
                // ── Error, nothing to show ─────────────────────────────────
                failure != null && links.isEmpty() -> SheetMessageState(
                    title = "Couldn't resolve streams",
                    detail = failure ?: "",
                    actionLabel = "Retry",
                    onAction = {
                        // R13-REVIEW F5: clear the failed attempt's remnants.
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

                // ── Still scanning, nothing yet ────────────────────────────
                links.isEmpty() && !completed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 18.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text("Scanning for video streams…", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "This can take a few seconds",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Completed with nothing playable ────────────────────────
                links.isEmpty() && completed -> SheetMessageState(
                    title = "No playable streams",
                    detail = buildString {
                        append(failure ?: "The provider returned no playable links")
                        if (hiddenCount > 0) append(" · $hiddenCount torrent link(s) hidden")
                        if (drmCount > 0) append(" · $drmCount DRM link(s) unsupported")
                    },
                    actionLabel = "Retry",
                    onAction = {
                        // R13-REVIEW F5: clear the failed attempt's remnants.
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

                // ── The source list (live-updating) ────────────────────────
                else -> LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    items(links, key = { it.url }) { link ->
                        ResolveSourceRow(
                            link = link,
                            subtitleCount = subtitles.size,
                            onClick = { pick(link) },
                        )
                    }
                    if (!completed) {
                        item(key = "scanning-footer") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    "Scanning for more sources…",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        item(key = "done-footer") {
                            val extra = buildString {
                                if (hiddenCount > 0) append(" · $hiddenCount torrent hidden")
                                if (drmCount > 0) append(" · $drmCount DRM unsupported")
                            }
                            Text(
                                "${links.size} source(s) · ${subtitles.size} subtitle track(s)$extra",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One progressive source row: label + type badge + CC badge. */
@Composable
private fun ResolveSourceRow(
    link: CsVideoLink,
    subtitleCount: Int,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = link.displayLabel,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (subtitleCount > 0) {
            CsMiniBadge(text = "CC $subtitleCount")
            Spacer(Modifier.width(6.dp))
        }
        CsMiniBadge(text = link.type.badgeLabel())
    }
}

/** Small rounded badge (type / CC counts). */
@Composable
private fun CsMiniBadge(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

/** The sheet's error / empty state with a Retry action. */
@Composable
private fun SheetMessageState(
    title: String,
    detail: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(
            detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clickable(onClick = onAction)
                .padding(top = 4.dp),
        )
    }
}

private fun CsLinkType.badgeLabel(): String = when (this) {
    CsLinkType.VIDEO -> "VIDEO"
    CsLinkType.M3U8 -> "HLS"
    CsLinkType.DASH -> "DASH"
}
