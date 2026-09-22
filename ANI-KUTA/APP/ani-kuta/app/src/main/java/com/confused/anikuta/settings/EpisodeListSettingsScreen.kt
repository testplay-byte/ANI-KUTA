package com.confused.anikuta.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.R
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.CachedEpisodeMetadata
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.datacache.EpisodeAudioAggregates
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.metadata.EpisodeMetadata
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.feature.animedetails.EpisodeDownloadState
import com.confused.anikuta.feature.animedetails.EpisodeListDisplayStyle
import com.confused.anikuta.feature.animedetails.EpisodeListEntry
import com.confused.anikuta.feature.animedetails.EpisodeListRowStyle
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

/**
 * D-554 → D-555 → D-556: the episode-list appearance page — reached from
 * Appearance → "Episode list".
 *
 * # The pattern (the D-477/D-481/D-525 doctrine, applied to lists)
 *
 * The same shape as the notification-poster page: a **live preview** region
 * at the top and the options in a LazyColumn below it (the D-525 single 8dp
 * gutter). The preview is NOT a mock-up: it renders the SAME
 * [EpisodeListEntry] dispatcher the details page draws, fed from the SAME
 * [EpisodeListPreferences] keys this screen writes (what you tune is exactly
 * what you'll get — D-481 honored literally). One source of truth, zero
 * drift: every flip here re-shapes the preview above AND the real list on
 * the details screen.
 *
 * # D-556: the preview shows YOUR library (the v1.1.29 device round)
 *
 * The user: "I was hoping for the live previews to show actual live previews
 * from my library … it will pick any random series … should have its episode
 * list loaded … with images … proper titles, proper descriptions, and other
 * details like the available audio versions marked on them, the release date
 * marked on them. If none of that is available … demo data."
 *
 * So the preview's content is loaded on entry ([loadLibraryPreviewEpisodes]):
 * a RANDOM qualifying library series — ≥2 cached episodes, thumbnails
 * preferred — reconstructed EXACTLY like the details screen's cache-restore
 * path (same SEpisode fields, same EpisodeMetadata fields), so titles,
 * descriptions, air dates and thumbnails are the app's real data, and the
 * audio pills come from the app's own per-series audio aggregates (no
 * fabrication: a series that only knows SUB shows SUB). If the library has
 * no qualifying series (fresh install), the demo samples render instead —
 * two episodes with real dates, synopses and the SUB/DUB/HSUB vocabulary.
 *
 * # D-556: the demo thumbnails are DRAWABLE RESOURCES (the image bug's root)
 *
 * The D-555 preview thumbnails STILL rendered empty on device. Root cause,
 * proven this round by unzipping coil-core-android-3.0.4.aar: **Coil 3 has
 * NO DataUriFetcher** — its fetcher registry is Asset/Bitmap/ByteArray/
 * ByteBuffer/ContentUri/Drawable/FileUri/JarFile/ResourceUri. The D-554
 * bare-base64 constant AND the D-555 `data:image/jpeg;base64,` scheme could
 * therefore NEVER match a fetcher — AsyncImage silently drew nothing and the
 * user saw the null-thumbnail number discs. The fix removes the data-URI
 * layer entirely: the two stills ship as real JPEG drawable resources
 * (ep_preview_still_1/2.jpg) referenced through `android.resource://`
 * URIs — ResourceUriFetcher's bread and butter, offline, tiny, and they
 * render with the SAME AsyncImage path the real network thumbnails use.
 *
 * # D-556: the preview is INTERACTIVE
 *
 * The user: "I am able to swipe right or left on the live previews, but
 * apparently their state never changes … I do want the option to manually
 * change it" and "clicking the download button … its state should change to
 * another view … cycle between all the possible states". So the two preview
 * slots carry LIVE local state: slot 1 boots fresh (40% watch-progress bar)
 * and slot 2 boots watched (the dim/grayscale treatment) — the default
 * experience the user described — and swipe-to-toggle (long-press on GRID)
 * flips the watched state, while every tap on the download control/badge
 * advances a state machine through ALL EIGHT download states (nothing →
 * resolving → queued → downloading → paused → downloading → error →
 * retrying → downloaded → nothing). The rows are still not playable — these
 * are appearance demos, not real episodes.
 *
 * # D-556: the preview COLLAPSES as you scroll (the second episode stays)
 *
 * The user: "if the user scrolls the bottom section, then the top live
 * preview section will scroll along with it … only this much that the first
 * episode list is hidden, and the second one will remain there and will
 * always be shown properly. When the user scrolls to the very top again,
 * then both of them will start to show up again." The collapse fraction
 * tracks the options list's scroll (GRID is exempt — "already compressed
 * enough"): the preview's content slides up under a clip so the first
 * episode exits and the second pins at the top; scrolling back restores
 * both. Layout switches animate smoothly ([animateContentSize]) so the
 * options below glide instead of snapping ("the layout section should move
 * down slowly").
 *
 * NOT part of this page (deliberately — the surfaces coexist): sort /
 * filter / grouping live in the list-settings SHEET on the details page
 * (list SHAPING); this page is list APPEARANCE only. The D-555 "More"
 * pointer card is gone per the device round ("most definitely not needed").
 */
@Composable
fun EpisodeListSettingsScreen(
    onBack: () -> Unit,
    episodeListPrefs: EpisodeListPreferences = koinInject(),
    contentRepository: ContentRepository = koinInject(),
    dataCacheRepository: DataCacheRepository = koinInject(),
) {
    // ── The reactive reads — the SAME prefs the details screen collects.
    // Every write below updates the pref; `changes` re-emits; the preview
    // AND the details list re-shape from the same emission (no local
    // mirror state, the D-481 drift killer).
    val rowStyleKey by episodeListPrefs.rowStyle.changes.collectAsState(
        initial = episodeListPrefs.rowStyle.get(),
    )
    val showSynopsis by episodeListPrefs.showSynopsis.changes.collectAsState(
        initial = episodeListPrefs.showSynopsis.get(),
    )
    val showDatePill by episodeListPrefs.showDatePill.changes.collectAsState(
        initial = episodeListPrefs.showDatePill.get(),
    )
    val showAudioPills by episodeListPrefs.showAudioPills.changes.collectAsState(
        initial = episodeListPrefs.showAudioPills.get(),
    )
    val showWatchProgress by episodeListPrefs.showWatchProgress.changes.collectAsState(
        initial = episodeListPrefs.showWatchProgress.get(),
    )
    val dimWatched by episodeListPrefs.dimWatched.changes.collectAsState(
        initial = episodeListPrefs.dimWatched.get(),
    )
    val showDownloadControl by episodeListPrefs.showDownloadControl.changes.collectAsState(
        initial = episodeListPrefs.showDownloadControl.get(),
    )
    // D-529 lesson: seed the toggle through the lenient fromKey so the
    // highlighted segment is ALWAYS the style the renderer will draw.
    val selectedStyle = EpisodeListRowStyle.fromKey(rowStyleKey)

    val style = EpisodeListDisplayStyle(
        rowStyle = selectedStyle,
        showSynopsis = showSynopsis,
        showDatePill = showDatePill,
        showAudioPills = showAudioPills,
        showWatchProgress = showWatchProgress,
        dimWatched = dimWatched,
        showDownloadControl = showDownloadControl,
    )

    // ── D-556: the preview's content — demo samples first (instant paint),
    // then a RANDOM qualifying library series replaces them when one exists.
    val context = LocalContext.current
    var previewItems by remember {
        mutableStateOf(demoPreviewItems(context.packageName))
    }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            loadLibraryPreviewItems(contentRepository, dataCacheRepository)
        }
        if (loaded != null) previewItems = loaded
    }

    // ── D-556: the LIVE preview state. Watched defaults: slot 1 fresh,
    // slot 2 watched (the user's spec) — a swipe (or GRID long-press)
    // overrides. The download state advances through the FULL 8-state
    // machine on every tap of the control/badge.
    val watchedOverride = remember { mutableStateMapOf<String, Boolean>() }
    val cycleIndex = remember { mutableStateMapOf<String, Int>() }

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // ── D-556: the scroll-driven collapse. The fraction tracks the options
    // list's own scroll: 0 at the very top, 1 after ~200dp of scrolling
    // (or any first-visible item beyond the first). GRID never collapses
    // (the user: "not for the grid layout because it is already compressed
    // enough").
    val density = LocalDensity.current
    val collapseRangePx = with(density) { 200.dp.toPx() }
    val collapseFraction = if (selectedStyle == EpisodeListRowStyle.GRID) {
        0f
    } else {
        with(lazyListState) {
            when {
                // No scroll yet → both episodes show (also covers an options
                // list that fits entirely: it must NOT collapse on entry).
                firstVisibleItemScrollOffset == 0 && firstVisibleItemIndex == 0 -> 0f
                // Past the first item, or the options are exhausted — the
                // collapse must ALWAYS complete (a short options list may
                // never scroll the full 200dp ramp; reaching the bottom
                // means episode 1 is gone and episode 2 is pinned).
                firstVisibleItemIndex > 0 || !canScrollForward -> 1f
                else -> (firstVisibleItemScrollOffset / collapseRangePx).coerceIn(0f, 1f)
            }
        }
    }
    // The first episode's measured height + the row gap = the exact shift
    // that hides episode 1 and pins episode 2 at the clip's top edge.
    var firstRowHeightPx by remember { mutableStateOf(0) }
    val rowGapPx = with(density) { 8.dp.toPx() }
    val hidePx = collapseFraction * (firstRowHeightPx + rowGapPx)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Episode list",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            // ── THE LIVE PREVIEW — real library data (or the demo samples).
            // The clip+shift layout collapses it as the options scroll; the
            // animateContentSize makes LAYOUT SWITCHES glide ("the layout
            // section should move down slowly").
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                EpisodeListCard(label = "Live preview") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clipToBounds()
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val shift = hidePx.roundToInt()
                                val visible = (placeable.height - shift).coerceAtLeast(1)
                                layout(placeable.width, visible) {
                                    placeable.placeRelative(0, -shift)
                                }
                            },
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    animationSpec = tween(
                                        durationMillis = 360,
                                        easing = FastOutSlowInEasing,
                                    ),
                                ),
                        ) {
                            if (selectedStyle == EpisodeListRowStyle.GRID) {
                                // The wall pairs its cells two-across — the
                                // preview mirrors the real list's shape.
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        PreviewEpisodeSlot(
                                            item = previewItems[0],
                                            slot = 0,
                                            style = style,
                                            watchedOverride = watchedOverride,
                                            cycleIndex = cycleIndex,
                                        )
                                    }
                                    Box(modifier = Modifier.weight(1f)) {
                                        PreviewEpisodeSlot(
                                            item = previewItems[1],
                                            slot = 1,
                                            style = style,
                                            watchedOverride = watchedOverride,
                                            cycleIndex = cycleIndex,
                                        )
                                    }
                                }
                            } else {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier.onSizeChanged { size ->
                                            firstRowHeightPx = size.height
                                        },
                                    ) {
                                        PreviewEpisodeSlot(
                                            item = previewItems[0],
                                            slot = 0,
                                            style = style,
                                            watchedOverride = watchedOverride,
                                            cycleIndex = cycleIndex,
                                        )
                                    }
                                    PreviewEpisodeSlot(
                                        item = previewItems[1],
                                        slot = 1,
                                        style = style,
                                        watchedOverride = watchedOverride,
                                        cycleIndex = cycleIndex,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── THE SCROLLABLE REGION — the options. Same single-gutter
            // contentPadding as the poster page (the D-525 8dp rule).
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    // ── the layout — the FOUR-WAY selector. D-556: no
                    // description lines (the device round: the "four
                    // completely different designs" line and the per-option
                    // identity line are "not needed" / "not good") — the
                    // LIVE PREVIEW is the description; the animated pill
                    // (SegmentedToggle) is the selector.
                    item {
                        EpisodeListCard(label = "Layout") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Layout",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(modifier = Modifier.padding(top = 10.dp)) {
                                    SegmentedToggle(
                                        options = listOf(
                                            "Classic",
                                            "Grid",
                                            "Timeline",
                                            "Cinema",
                                        ),
                                        selectedIndex = selectedStyle.ordinal,
                                        onSelect = { idx ->
                                            episodeListPrefs.rowStyle.set(
                                                EpisodeListRowStyle.entries[idx].name,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── the elements — switches with ONE-LINE descriptions ──
                    item {
                        EpisodeListCard(label = "Elements") {
                            EpisodeListSwitchRow(
                                title = "Synopsis",
                                description = "The two-line description (Classic rows only)",
                                checked = showSynopsis,
                                onChecked = { episodeListPrefs.showSynopsis.set(it) },
                            )
                            EpisodeListSwitchRow(
                                title = "Release date",
                                description = "Shown in every layout where it fits",
                                checked = showDatePill,
                                onChecked = { episodeListPrefs.showDatePill.set(it) },
                            )
                            EpisodeListSwitchRow(
                                title = "Audio pills",
                                description = "SUB · DUB · HSUB availability",
                                checked = showAudioPills,
                                onChecked = { episodeListPrefs.showAudioPills.set(it) },
                            )
                            EpisodeListSwitchRow(
                                title = "Watch progress",
                                description = "The bar on the imagery's edge",
                                checked = showWatchProgress,
                                onChecked = { episodeListPrefs.showWatchProgress.set(it) },
                            )
                            EpisodeListSwitchRow(
                                title = "Dim watched",
                                description = "Fade and grayscale watched episodes",
                                checked = dimWatched,
                                onChecked = { episodeListPrefs.dimWatched.set(it) },
                            )
                            EpisodeListSwitchRow(
                                title = "Download buttons",
                                description = "The control/badge on each episode",
                                checked = showDownloadControl,
                                onChecked = { episodeListPrefs.showDownloadControl.set(it) },
                            )
                        }
                    }
                }
                ScrollBlurOverlay(
                    scrollOffset = { lazyListState.firstVisibleItemScrollOffset.toFloat() },
                    backgroundColor = MaterialTheme.colorScheme.background,
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// D-556: the preview's content plumbing — real library data with a demo
// fallback, plus the LIVE slot state (watched toggle + download cycling).
// ─────────────────────────────────────────────────────────────────────────────

/** One preview slot's content: a REAL episode (library cache) or a demo one. */
private data class PreviewItem(
    val episode: SEpisode,
    val metadata: EpisodeMetadata?,
    val fallbackCoverUrl: String?,
)

/**
 * The full 8-state download machine, in demonstration order: nothing →
 * resolving → queued → downloading (35%) → paused → downloading (72%) →
 * error → retrying → downloaded → (wraps to nothing). Every tap of the
 * preview's download control/badge advances one step (the user: "it should
 * cycle between all the possible states of the download options, like
 * nothing, normal download button, then the downloading one, then the error
 * ones, and any other if there are").
 */
private val downloadStateCycle: List<EpisodeDownloadState> = listOf(
    EpisodeDownloadState.NotDownloaded,
    EpisodeDownloadState.Resolving,
    EpisodeDownloadState.Queued,
    EpisodeDownloadState.Downloading(35),
    EpisodeDownloadState.Paused,
    EpisodeDownloadState.Downloading(72),
    EpisodeDownloadState.Error("Preview failure (simulated)"),
    EpisodeDownloadState.Retrying,
    EpisodeDownloadState.Downloaded,
)

/**
 * ONE preview slot rendered through the REAL dispatcher (the D-481
 * doctrine). Slot 0 boots fresh with the 40% progress fixture; slot 1 boots
 * watched (the dim/grayscale treatment). Swipe (long-press on GRID) flips
 * the watched state; every download tap advances [downloadStateCycle].
 */
@Composable
private fun PreviewEpisodeSlot(
    item: PreviewItem,
    slot: Int,
    style: EpisodeListDisplayStyle,
    watchedOverride: MutableMap<String, Boolean>,
    cycleIndex: MutableMap<String, Int>,
) {
    val defaultWatched = slot == 1
    val url = item.episode.url
    val isWatched = watchedOverride[url] ?: defaultWatched
    val state = downloadStateCycle[cycleIndex[url] ?: 0]
    fun advance() {
        cycleIndex[url] = ((cycleIndex[url] ?: 0) + 1) % downloadStateCycle.size
    }
    EpisodeListEntry(
        episode = item.episode,
        metadata = item.metadata,
        onClick = { /* the preview is inert — no playback from settings */ },
        downloadState = state,
        fallbackCoverUrl = item.fallbackCoverUrl,
        // The cycling taps: EVERY control action advances the machine —
        // download/pause/resume/cancel/retry/play are all "the next state".
        onDownload = { advance() },
        onPause = { advance() },
        onResume = { advance() },
        onCancel = { advance() },
        onRetry = { advance() },
        onDelete = {},
        onPlayDownloaded = { advance() },
        isWatched = isWatched,
        progressFraction = if (slot == 0) 0.4f else 0f,
        onToggleWatched = { watchedOverride[url] = !isWatched },
        style = style,
    )
}

/**
 * The demo fallback (fresh installs / no qualifying library series): TWO
 * episodes with real dates, full synopses, and the SUB/DUB/HSUB vocabulary,
 * riding two REAL anime stills shipped as drawable resources through
 * `android.resource://` URIs (see the screen KDoc for why data URIs can
 * never work on Coil 3 — no DataUriFetcher).
 */
private fun demoPreviewItems(packageName: String): List<PreviewItem> {
    val thumb1 = "android.resource://$packageName/${R.drawable.ep_preview_still_1}"
    val thumb2 = "android.resource://$packageName/${R.drawable.ep_preview_still_2}"
    val fresh = SEpisode.create().apply {
        url = "preview://sample-1"
        name = "The Journey Begins"
        summary = "A quiet morning is interrupted when the first gate opens " +
            "over the harbor, and everything the crew trained for finally matters."
        scanlator = "SUB DUB" // the demo's honest audio vocabulary → SUB · DUB pills
        date_upload = 1735689600000L // Jan 1, 2025 — a stable sample date
        episode_number = 1f
        preview_url = thumb1
    }
    val watched = SEpisode.create().apply {
        url = "preview://sample-2"
        name = "Signal in the Rain"
        summary = "The lantern district goes dark one street at a time, and " +
            "the only witness is a courier who was never supposed to be there."
        scanlator = "HSUB"
        date_upload = 1736294400000L // Jan 8, 2025
        episode_number = 2f
        preview_url = thumb2
    }
    return listOf(
        PreviewItem(episode = fresh, metadata = null, fallbackCoverUrl = null),
        PreviewItem(episode = watched, metadata = null, fallbackCoverUrl = null),
    )
}

/**
 * The REAL preview content: a RANDOM qualifying library series — ≥2 cached
 * episodes, thumbnails preferred, never fabricated. Reads the SAME stores
 * the library and details screens read (SQLDelight, synchronous — call on
 * Dispatchers.IO). Returns null when nothing qualifies → the caller keeps
 * the demo samples.
 *
 * The reconstruction mirrors DetailsViewModel.fetchEpisodes' cache-restore
 * path FIELD FOR FIELD (url/number/name/date/scanlator/summary/preview_url
 * + the full D-190 EpisodeMetadata), so the preview rows are byte-for-byte
 * the rows the details screen would draw for that series.
 */
private fun loadLibraryPreviewItems(
    contentRepository: ContentRepository,
    dataCacheRepository: DataCacheRepository,
): List<PreviewItem>? {
    // ONE batch query for the audio aggregates + the covers map + the
    // library items (newest first — the same reads LibraryViewModel does).
    val aggregates = dataCacheRepository.getAllEpisodeAudioAggregates()
    val detailsById = contentRepository.getAllContentDetailsMap()
    val candidates = contentRepository.getAllLibraryItems()
        .map { it.mainId }
        .distinct()
        // A series qualifies only when its episode list is actually LOADED
        // (the user's spec) — the data-cache table is exactly that evidence.
        .filter { (aggregates[it]?.releasedCount ?: 0) >= 2 }
        // "it will pick any random series" — the order is shuffled per visit.
        .shuffled()

    var firstQualified: List<PreviewItem>? = null
    for (mainId in candidates.take(8)) {
        val episodes = dataCacheRepository.getEpisodeMetadata(mainId)
            .filter { it.episodeNumber > 0f }
            .sortedBy { it.episodeNumber }
        if (episodes.size < 2) continue
        val picked = pickPreviewEpisodes(episodes)
        val cover = detailsById[mainId]?.let { it.dataCoverUrl ?: it.extThumbnailUrl }
        val items = picked.mapIndexed { index, meta ->
            PreviewItem(
                episode = reconstructEpisode(meta, mainId, aggregates[mainId]),
                metadata = reconstructMetadata(meta, mainId),
                // The series cover backs any thumbnail-less episode — REAL
                // imagery even when the provider gave no per-episode stills.
                fallbackCoverUrl = cover,
            )
        }
        // Prefer a candidate whose two preview episodes carry real imagery
        // (episode stills or at least the series cover); a candidate without
        // either is kept as the last resort over the demo data (it still
        // shows the user's REAL titles/dates/audio).
        val hasImagery = items.any { !it.episode.preview_url.isNullOrBlank() || cover != null }
        if (hasImagery) return items
        if (firstQualified == null) firstQualified = items
    }
    return firstQualified
}

/**
 * The two preview episodes: the lowest-numbered ones that carry a thumbnail
 * (thumbnails are the preview's point); when only one episode has a still,
 * pair it with the next-lowest numbered episode; when none do, episodes 1+2
 * (the cover fallback carries the imagery).
 */
internal fun pickPreviewEpisodes(
    episodes: List<CachedEpisodeMetadata>,
): List<CachedEpisodeMetadata> {
    val withThumb = episodes.filter { !it.thumbnailUrl.isNullOrBlank() }
    return when {
        withThumb.size >= 2 -> listOf(withThumb[0], withThumb[1])
        // The size>=2 guard on the pairing branch makes the function total
        // (the caller filters size<2 today, but this is unit-test surface).
        withThumb.size == 1 && episodes.size >= 2 -> listOf(
            withThumb[0],
            episodes.first { it !== withThumb[0] },
        )
        else -> episodes.take(2)
    }
}

/** The SEpisode reconstruction — the details screen's cache-restore, verbatim. */
private fun reconstructEpisode(
    meta: CachedEpisodeMetadata,
    mainId: String,
    aggregates: EpisodeAudioAggregates?,
): SEpisode = SEpisode.create().apply {
    url = meta.episodeUrl ?: "preview://$mainId/${meta.episodeNumber}"
    episode_number = meta.episodeNumber
    name = meta.sourceName ?: meta.title ?: "Episode ${meta.episodeNumber.toInt()}"
    date_upload = meta.airDate ?: 0L
    // Real scanlator when the cache has one; otherwise the app's OWN audio
    // aggregates speak (no fabrication — a series that only knows SUB shows
    // SUB). The tokens ride the same parseAudioAvailability path the
    // details rows use.
    scanlator = meta.scanlator?.takeIf { it.isNotBlank() }
        ?: aggregates?.let { audioScanlatorHint(it) }
    summary = meta.description
    preview_url = meta.thumbnailUrl
}

/** The EpisodeMetadata reconstruction — the D-190 full-field restore, verbatim. */
private fun reconstructMetadata(
    meta: CachedEpisodeMetadata,
    mainId: String,
): EpisodeMetadata = EpisodeMetadata(
    episodeKey = mainId + "|" + String.format("%05d", meta.episodeNumber.toInt()),
    number = meta.episodeNumber.toDouble(),
    title = meta.title,
    thumbnailUrl = meta.thumbnailUrl,
    airDate = meta.airDate,
    description = meta.description,
    isFiller = meta.isFiller,
    isRecap = meta.isRecap,
    titleJapanese = meta.titleJapanese,
    titleRomaji = meta.titleRomaji,
    runtime = meta.runtime,
    seasonNumber = meta.seasonNumber,
    episodeNumberInSeason = meta.episodeNumberInSeason,
    score = meta.score,
)

/**
 * The audio-aggregate → scanlator hint. ONLY used when the cached episode
 * has no scanlator of its own. The encoding respects the row's HSUB-distinct
 * parse: an HSUB presence collapses everything to HSUB (the same lossiness
 * the details page shows for real scanlator strings), otherwise SUB/DUB
 * tokens as the aggregates know them.
 */
private fun audioScanlatorHint(agg: EpisodeAudioAggregates): String? = when {
    agg.hasHsub -> "HSUB"
    agg.hasSub && agg.hasDub -> "SUB DUB"
    agg.hasSub -> "SUB"
    agg.hasDub -> "DUB"
    else -> null
}

// Local card + row shapes — the poster page's PosterCard/PosterSwitchRow look,
// duplicated here as privates (the poster's are file-private; sharing would
// widen their visibility for no reuse value beyond these two pages).

/**
 * The section card — the SettingsGroupCard look (the primary ExtraBold
 * label, the 12dp-rounded surfaceVariant surface) WITHOUT the 16dp
 * horizontal padding baked into the shared component: this screen carries
 * the single 8dp gutter (the D-525 rule).
 */
@Composable
private fun EpisodeListCard(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.primary,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 8.dp, bottom = 8.dp),
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 4.dp),
                content = content,
            )
        }
    }
}

/** The switch row — title + one-line description + the switch (D-532). */
@Composable
private fun EpisodeListSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
