package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.csplayer.CsLinkType
import com.confused.anikuta.core.csplayer.CsSubtitle
import com.confused.anikuta.core.csplayer.CsVideoLink
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.DebugPreferences
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.core.preferences.PlayerPreferences
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver
import com.confused.anikuta.data.cloudstream.playback.CloudstreamLinkResolver.CsResolveEvent
import com.confused.anikuta.data.cloudstream.playback.CsSourceMemory
import com.confused.anikuta.feature.cswatch.api.CsSubDubSiblings
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
 * Task 54 (round 14 — UI parity): the sheet renders in the EXACT visual
 * language of the aniyomi extensions' [ResolverSheet] (see [CsSourceListUi]
 * for the shared server/audio/quality accordion).
 *
 * Task 55 (round 15 — the user's device feedback):
 *  - the "via {provider} — tap a server to expand…" hint line and the
 *    "N source(s) · N subtitle track(s)" footer are GONE (explicit request);
 *  - servers group by AUDIO VERSION too (the aniyomi 3-tier Server → Audio →
 *    Quality) — chips in the card header + a per-version label row;
 *  - the "Episode N" header carries the source-FORMATTING popup (a small
 *    DropdownMenu that opens ABOVE the anchor): OFF = a raw flat list of
 *    unformatted entries, tap = play directly. Default ON; shared preference
 *    with the aniyomi sheets (PlayerPreferences.resolveSheetFormatted);
 *  - COMBINED sub/dub mode: when Episode-list settings → Display is set to
 *    Combined and the tapped episode has an opposite-flavor sibling, BOTH
 *    handles resolve in parallel and each stream carries its flavor tag —
 *    the SUB/DUB chips in the cards let the user pick, exactly like aniyomi.
 * CS keeps its own behaviors underneath: progressive rows, the remembered
 * server's accordion auto-EXPANSION (a hint, never an auto-pick), and
 * cancel-on-dismiss.
 *
 * Task 56 (round 16 — device feedback F1): the remembered-server and
 * single-link AUTO-SELECT paths are GONE. Tapping an episode resolves and
 * presents the list — the user always picks the stream; playback never
 * starts on its own from the entry sheet. ("For some plugins" was exactly
 * those two conditions: a remembered server streaming in, or a provider
 * that resolves to a single link.)
 *
 * Task 57 (round 17 — P3 + P4): the dual-handle merge dedups by
 * (url + audio label) so same-URL dual-audio encodes keep BOTH flavors;
 * and with Settings → Debug options' "Copy button" flag ON, the header
 * carries a copy-the-whole-report action (per-row copies live in
 * [CsSourceListUi]) — default OFF, zero visual change otherwise.
 *
 * Task 58 (round 18 — the DOWNLOADS PORT): `downloadMode = true` turns the
 * sheet into the CS download picker — the title reads "Download EP N" (the
 * aniyomi ResolverSheet's download-mode wording), DASH links are filtered
 * out of the pickable list (the download engine supports HTTP + HLS only;
 * they're counted + surfaced like the hidden torrents), and a pick hands
 * the RESOLVED [CsVideoLink] (+ the episode's provider subtitles) to
 * [onDownload] instead of seeding the watch ViewModel — no player, no
 * navigation. The resolve/progressive/dedup/debug behavior is identical.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CsResolveSheet(
    key: CsWatchKey,
    onDismiss: () -> Unit,
    onPlay: (CsWatchKey) -> Unit,
    // Task 58 (round 18 — downloads): when non-null the sheet is a DOWNLOAD
    // picker — picks hand off to this instead of the play path.
    onDownload: ((CsWatchKey, CsVideoLink, List<CsSubtitle>) -> Unit)? = null,
    resolver: CloudstreamLinkResolver = koinInject(),
    sourceMemory: CsSourceMemory = koinInject(),
    viewModel: CsWatchViewModel = koinViewModel(),
    playerPreferences: PlayerPreferences = koinInject(),
    episodeListPreferences: EpisodeListPreferences = koinInject(),
    debugPreferences: DebugPreferences = koinInject(),
) {
    val downloadMode = onDownload != null
    var links by remember { mutableStateOf<List<CsVideoLink>>(emptyList()) }
    var subtitles by remember { mutableStateOf<List<CsSubtitle>>(emptyList()) }
    var hiddenCount by remember { mutableIntStateOf(0) }
    var drmCount by remember { mutableIntStateOf(0) }
    var completed by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var retryTick by remember { mutableIntStateOf(0) }
    var resolveJob by remember { mutableStateOf<Job?>(null) }
    // Task 55: the formatting toggle (read at open; shared with the aniyomi
    // sheets through PlayerPreferences — sheets are recreated per open).
    var formatted by remember { mutableStateOf(playerPreferences.resolveSheetFormatted) }

    // Task 57 (P4): the header-level "copy the whole report" action — OFF
    // unless enabled in Settings → Debug options; live-collected (reactive
    // flow) so toggling the setting while the sheet is open applies immediately.
    val copyEnabled by debugPreferences.resolveCopyButtonFlow()
        .collectAsState(initial = false)
    val copyFeedback = rememberCopyFeedback()

    // Task 55: the data handles to resolve — 1, or 2 in COMBINED sub/dub mode
    // (the tapped episode + its opposite-flavor sibling). Frozen per key.
    val handles = remember(key) {
        val eps = key.parseEpisodeList()
        val combined = episodeListPreferences.subDubMode.get() == "COMBINED"
        CsSubDubSiblings.handlesFor(eps, key.episodeData, combined).also {
            if (it.size > 1) {
                Logger.i(SHEET_TAG) {
                    "combined sub/dub: resolving ${it.size} handles " +
                        it.joinToString { h -> "${h.data.take(24)}→${h.audioTag}" }
                }
            }
        }
    }

    /**
     * Select + hand off. PLAY mode: remember + seed + push the watch screen.
     * DOWNLOAD mode (Task 58): hand the resolved link + subtitles to the
     * caller's enqueue path — no seeding, no navigation, the sheet closes.
     */
    fun pick(link: CsVideoLink) {
        if (downloadMode) {
            Logger.i(SHEET_TAG) {
                "download pick: ${link.displayLabel}" +
                    (link.audioLabel.takeIf { it != "Default" }?.let { " ($it)" } ?: "")
            }
            onDownload?.invoke(key, link, subtitles)
            onDismiss()
            return
        }
        sourceMemory.remember(key.mainId, link.name)
        Logger.i(SHEET_TAG) {
            "picked: ${link.displayLabel}" +
                (link.audioLabel.takeIf { it != "Default" }?.let { " ($it)" } ?: "") +
                " (remembered for mainId=${key.mainId.take(8)}…)"
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
                "handles=${handles.size} data=${key.episodeData.take(64)}"
        }
        // Per-handle latest snapshots — merged into `links` with
        // (url + audio label) dedup (first handle wins; see the Task 57
        // note at the merge below).
        val perHandle = mutableMapOf<Int, List<CsVideoLink>>()
        val perHandleSubs = mutableMapOf<Int, List<CsSubtitle>>()
        val perHandleHidden = IntArray(handles.size)
        val perHandleDrm = IntArray(handles.size)
        val finishedHandles = mutableSetOf<Int>()

        resolveJob = launch {
            handles.forEachIndexed { index, handle ->
                launch {
                    resolver.resolve(key.providerName, handle.data).collect { event ->
                        when (event) {
                            is CsResolveEvent.LinksSnapshot -> {
                                perHandle[index] = event.links.map { link ->
                                    if (handle.audioTag != null) link.copy(audioTag = handle.audioTag) else link
                                }
                                perHandleHidden[index] = event.hiddenTorrentCount
                                perHandleDrm[index] = event.unsupportedDrmCount
                                hiddenCount = perHandleHidden.sum()
                                drmCount = perHandleDrm.sum()
                                // Task 57 (P3 — combined reliability): the merge
                                // dedup key is (url + audio label), NOT the bare
                                // URL. Providers that serve the SAME encode URL
                                // under both the sub and the dub handle used to
                                // lose the ENTIRE second flavor here (url-dedup
                                // dropped its links) — "sometimes the dub
                                // episodes don't resolve". Both flavors now
                                // survive and land in their own accordion groups.
                                val known = mutableSetOf<String>()
                                links = handles.indices.flatMap { i -> perHandle[i].orEmpty() }
                                    .filter { known.add(it.url + "\u0000" + it.audioLabel) }
                                // Task 56: NO remembered-server auto-select here — the
                                // list renders and the user picks (device feedback F1).
                                // The remembered server still auto-EXPANDS its accordion
                                // below (preferredServer) — a hint, not a decision.
                            }
                            is CsResolveEvent.SubtitlesSnapshot -> {
                                perHandleSubs[index] = event.subtitles
                                val knownIds = mutableSetOf<String>()
                                subtitles = handles.indices.flatMap { i -> perHandleSubs[i].orEmpty() }
                                    .filter { knownIds.add(it.id) }
                            }
                            is CsResolveEvent.Completed -> {
                                finishedHandles += index
                                if (finishedHandles.size == handles.size) {
                                    completed = true
                                    Logger.i(SHEET_TAG) {
                                        "resolve done: links=${links.size} subs=${subtitles.size} " +
                                            "handles=${handles.size} in ${event.durationMs}ms"
                                    }
                                    // Task 56: NO single-link auto-select — even a
                                    // one-link result waits for the user's tap (F1).
                                }
                            }
                            is CsResolveEvent.Failed -> {
                                finishedHandles += index
                                Logger.w(SHEET_TAG) {
                                    "handle #${index + 1} resolve failed: ${event.message} " +
                                        "(linksSoFar=${event.linksSoFar})"
                                }
                                // Surface the failure when nothing at all resolved;
                                // a partial failure with links stays usable.
                                if (links.isEmpty()) failure = event.message
                                if (finishedHandles.size == handles.size) {
                                    completed = true
                                }
                            }
                        }
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

    /** Clears the run state so retryTick++ restarts everything fresh. */
    fun resetAndRetry() {
        links = emptyList()
        subtitles = emptyList()
        hiddenCount = 0
        drmCount = 0
        failure = null
        completed = false
        retryTick++
    }

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
            // ABOVE the title, its own clearly-bounded control), then the
            // "Episode N" / "Download EP N" title + close row (plain text —
            // the title is NOT a click target anymore; empty-area taps in
            // the header do nothing).
            CsFormattingToggle(
                formatted = formatted,
                onToggleFormatting = {
                    formatted = it
                    playerPreferences.resolveSheetFormatted = it
                    Logger.i(SHEET_TAG) { "source formatting → ${if (it) "formatted" else "raw"}" }
                },
                modifier = Modifier.padding(top = 14.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (downloadMode) {
                        // Task 58: the aniyomi ResolverSheet's download-mode wording.
                        "Download EP ${com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(key.episodeNumber)}"
                    } else {
                        "Episode ${com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(key.episodeNumber)}"
                    },
                    fontFamily = RobotoFamily,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (copyEnabled) {
                    // Task 57 (P4): copies the WHOLE resolve report (header +
                    // every link's detail block) for bug reports.
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(32.dp)
                            .clickable {
                                copyFeedback(
                                    buildResolveDebugReport(
                                        key.providerName,
                                        key.animeTitle,
                                        key.episodeNumber,
                                        links,
                                    ),
                                    "Report copied",
                                )
                                Logger.i(SHEET_TAG) { "debug report copied: links=${links.size}" }
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

            // ── Content states (ResolverSheet parity wording) ──
            // Task 58 (round 18 — downloads): DASH manifests are NOT
            // downloadable (the engine supports HTTP + HLS; VideoTypeDetector
            // has no DASH path) — filtered out of the PICKABLE list BEFORE the
            // state checks, so an all-DASH resolve lands in the empty card
            // instead of a silent blank accordion. Play mode: pickable == links.
            val pickableLinks = if (downloadMode) {
                links.filter { it.type != CsLinkType.DASH }
            } else links
            when {
                // Error, nothing to show — the aniyomi ResolverSheet Error card.
                failure != null && pickableLinks.isEmpty() -> CsSheetErrorCard(
                    title = "Failed to resolve streams",
                    detail = failure ?: "",
                    onRetry = ::resetAndRetry,
                )

                // Still scanning, nothing yet — the aniyomi Loading card.
                pickableLinks.isEmpty() && !completed -> Box(
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
                pickableLinks.isEmpty() && completed -> Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val dashOnlyCount = if (downloadMode) links.count { it.type == CsLinkType.DASH } else 0
                    Text(
                        text = if (downloadMode) "No downloadable sources" else "No video sources available",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = buildString {
                            append(
                                if (downloadMode && dashOnlyCount > 0) {
                                    "Only DASH streams were found for this episode"
                                } else if (downloadMode) {
                                    "The provider returned no downloadable links"
                                } else {
                                    "The provider returned no playable links"
                                },
                            )
                            failure?.let { append("\n$it") }
                            if (hiddenCount > 0) append("\n$hiddenCount torrent link(s) hidden")
                            if (drmCount > 0) append("\n$drmCount DRM link(s) unsupported")
                            if (downloadMode && dashOnlyCount > 0) {
                                append("\n${dashOnlyCount} DASH stream(s) — stream them instead")
                            }
                        },
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = ::resetAndRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Retry", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                    }
                }

                // ── The source list (live-updating) ──
                else -> {
                    val dashCount = links.size - pickableLinks.size
                    if (formatted) {
                        // Server → AudioVersion → Quality (the aniyomi 3-tier).
                        val servers = remember(pickableLinks) { groupServers(pickableLinks) }
                        // sourceMemory stores the RAW link name (the auto-select
                        // matches raw names); the accordion auto-expansion matches
                        // GROUP names — derive the server part of the memory value.
                        val remembered = remember(key) { sourceMemory.recall(key.mainId) }
                        CsServerAccordion(
                            servers = servers,
                            preferredServer = remembered?.let(::serverNameOf),
                            onPickVideo = { link -> pick(link) },
                        )
                    } else {
                        // Task 55: RAW mode — one row per stream, unformatted
                        // labels, tap = play directly. No collapsing, no chips.
                        CsRawLinkList(
                            links = pickableLinks,
                            onPickVideo = { link -> pick(link) },
                        )
                    }
                    if (dashCount > 0) {
                        Text(
                            text = "$dashCount DASH stream(s) can't be downloaded (stream them instead)",
                            fontFamily = RobotoFamily,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                        )
                    }
                    if (!completed) {
                        // Progressive footer — CS keeps streaming while picking.
                        // (The completed counts footer was removed per the
                        // round-15 device feedback — the user called it noise.)
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
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = detail,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Retry", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
        }
    }
}
