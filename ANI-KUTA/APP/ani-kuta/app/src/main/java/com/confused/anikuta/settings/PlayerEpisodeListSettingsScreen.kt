package com.confused.anikuta.settings

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.common.EpisodeTitleParser
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeGridRow
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListEntry
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListStyle
import com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeRowData
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  ROUND 102 (WS-G) → ROUND 103 (WS-4) REWORKED: PlayerEpisodeListSettingsScreen
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  The v1.1.59 device round's orders, verbatim intent:
 *  • "The actual episode list on the details page is getting a total of four
 *    custom display options, but the player page one is not getting things
 *    like those… build custom ones for that too… It should also be handled
 *    properly, just like the other one." → THE FOUR PARADIGMS
 *    (Detailed / Compact / Grid / Banner) through the ONE shared renderer
 *    both player stacks draw with.
 *  • "It should properly show the actual live preview of the data. It should
 *    not show random things. It should all show the actual things, just like
 *    how it gets the data for the episode list of the details page." → THE
 *    ACTUAL-DATA PREVIEW: the SAME library-backed loader the details page's
 *    "Episode list" screen uses (a random qualifying series — ≥2 cached
 *    episodes, real titles/dates/audio/imagery; the demo samples only paint
 *    the first frame on an empty library).
 *  • "These changes were not being applied in the live preview" (the sort) →
 *    the preview REORDERS live with the direction, and the watched filter
 *    reflects too (Hide watched → the watched slot is gone; Show watched →
 *    only the watched slot renders — the honest reading of the real list's
 *    behavior).
 *  • "The only sort option which should be given here is ascending or
 *    descending. It does not need to give any other options." → the Sort
 *    card is DIRECTION ONLY (the round-101 sort modes are retired).
 *
 *  The preview's two slots: slot 1 styled as the CURRENTLY PLAYING episode
 *  (the ring/border treatment + a partial progress bar), slot 2 as a WATCHED
 *  one (the dim/grayscale treatment) — every element the player list can
 *  draw, drawn by the player list's own renderer.
 */
@Composable
fun PlayerEpisodeListSettingsScreen(
    onBack: () -> Unit,
    playerListPrefs: PlayerEpisodeListPreferences = koinInject(),
    contentRepository: ContentRepository = koinInject(),
    dataCacheRepository: DataCacheRepository = koinInject(),
) {
    // ── The reactive reads — the SAME prefs both player pages collect. Every
    // write below updates the pref; `changes` re-emits; the preview AND the
    // player lists re-shape from the same emission (no local mirror state,
    // the D-481 drift killer). ──
    val rowStyleKey by playerListPrefs.rowStyle.changes.collectAsState(
        initial = playerListPrefs.rowStyle.get(),
    )
    val style = remember(rowStyleKey) { PlayerEpisodeListStyle.fromKey(rowStyleKey) }
    val showSynopsis by playerListPrefs.showSynopsis.changes.collectAsState(
        initial = playerListPrefs.showSynopsis.get(),
    )
    val showDatePill by playerListPrefs.showDatePill.changes.collectAsState(
        initial = playerListPrefs.showDatePill.get(),
    )
    val dimWatched by playerListPrefs.dimWatched.changes.collectAsState(
        initial = playerListPrefs.dimWatched.get(),
    )
    val watchedFilter by playerListPrefs.watchedFilter.changes.collectAsState(
        initial = playerListPrefs.watchedFilter.get(),
    )
    val sortDescending by playerListPrefs.sortDescending.changes.collectAsState(
        initial = playerListPrefs.sortDescending.get(),
    )
    val display = remember(style, showSynopsis, showDatePill, dimWatched) {
        com.confused.anikuta.core.designsystem.component.playerlist.PlayerEpisodeListDisplay(
            style = style,
            showSynopsis = showSynopsis,
            showDatePill = showDatePill,
            dimWatched = dimWatched,
        )
    }

    // ── ROUND 103 (WS-4): THE ACTUAL-DATA PREVIEW — the details page's
    // loader, verbatim (demo samples first for the instant paint, then a
    // RANDOM qualifying library series with REAL titles/dates/audio/imagery
    // replaces them). ──
    val context = LocalContext.current
    var previewItems by remember {
        mutableStateOf(demoPreviewItems(context.packageName))
    }
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { loadLibraryPreviewItems(contentRepository, dataCacheRepository) }.getOrNull()
        }
        if (loaded != null) previewItems = loaded
    }

    // The preview's slot data — ordered by the LIVE direction ("these
    // changes were not being applied in the live preview" — they are now:
    // flipping the direction visibly reorders the two episodes).
    val orderedItems = remember(previewItems, sortDescending) {
        if (sortDescending) previewItems.reversed() else previewItems
    }
    // The watched filter's honest reflection: Hide watched → the watched
    // slot is gone; Show watched → only the watched slot renders (exactly
    // what the player list would show for these two episodes).
    val currentSlotVisible = watchedFilter != "SHOW"
    val watchedSlotVisible = watchedFilter != "HIDE"
    val currentData = orderedItems.getOrNull(0)?.toPlayerRowData(
        isCurrent = true,
        isWatched = false,
        progressFraction = 0.35f,
    )
    val watchedData = orderedItems.getOrNull(1)?.toPlayerRowData(
        isCurrent = false,
        isWatched = true,
        progressFraction = 0f,
    )

    val isGrid = style == PlayerEpisodeListStyle.GRID
    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // ── The collapse: the FIRST preview row slides up under a clip as the
    // options scroll; the second row stays pinned (the D-556 reading). GRID
    // is exempt (the details page's rule — "already compressed enough"). ──
    var firstRowHeightPx by remember { mutableIntStateOf(0) }
    // SA2-F1 fix (lead-verified): the derived block MUST re-capture isGrid —
    // remember(isGrid) rebuilds the derivedStateOf when the layout switches
    // mid-visit (the stale-capture bug left the exemption dead after a
    // DETAILED→Grid flip).
    val hidePx by remember(isGrid) {
        derivedStateOf {
            if (isGrid) {
                0f
            } else if (lazyListState.firstVisibleItemIndex > 0) {
                firstRowHeightPx.toFloat()
            } else {
                min(
                    lazyListState.firstVisibleItemScrollOffset.toFloat(),
                    firstRowHeightPx.toFloat(),
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Player episode list",
                collapsed = collapsed,
                onBack = onBack,
            )

            // ── THE LIVE PREVIEW — the player's OWN renderer (the shared
            // four-paradigm dispatcher), fed the REAL library episodes. What
            // you tune below is exactly what the player draws. ──
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                PlayerListCard(label = "Live preview") {
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
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateContentSize(
                                    animationSpec = tween(360),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (isGrid) {
                                // The wall pairs its cells two-across — the
                                // preview mirrors the real list's shape. The
                                // slot guards honor the watched filter and
                                // stay null-safe on short item lists.
                                val gridLeft = when {
                                    currentSlotVisible && currentData != null -> currentData
                                    watchedSlotVisible && watchedData != null -> watchedData
                                    else -> null
                                }
                                val gridRight = when {
                                    currentSlotVisible && currentData != null &&
                                        watchedSlotVisible && watchedData != null -> watchedData
                                    else -> null
                                }
                                if (gridLeft != null) {
                                    PlayerEpisodeGridRow(
                                        left = gridLeft,
                                        right = gridRight,
                                        display = display,
                                        onClick = { /* the preview is inert */ },
                                    )
                                }
                            } else {
                                if (currentSlotVisible) {
                                    Box(
                                        modifier = Modifier.onSizeChanged { size ->
                                            firstRowHeightPx = size.height
                                        },
                                    ) {
                                        if (currentData != null) {
                                            PlayerEpisodeListEntry(
                                                data = currentData,
                                                display = display,
                                                onClick = { /* inert */ },
                                            )
                                        }
                                    }
                                }
                                if (watchedSlotVisible && watchedData != null) {
                                    PlayerEpisodeListEntry(
                                        data = watchedData,
                                        display = display,
                                        onClick = { /* inert */ },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── THE SCROLLABLE REGION — the options (the D-525 8dp gutter). ──
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 8.dp,
                        end = 8.dp,
                        bottom = 24.dp,
                    ),
                ) {
                    // ── Layout — THE FOUR PARADIGMS ──
                    item {
                        PlayerListCard(label = "Layout") {
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
                                        options = listOf("Detailed", "Compact", "Grid", "Banner"),
                                        selectedIndex = style.ordinal,
                                        onSelect = { idx ->
                                            playerListPrefs.rowStyle.set(
                                                PlayerEpisodeListStyle.entries[idx].name,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── Elements ──
                    item {
                        PlayerListCard(label = "Elements") {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                PlayerSwitchRow(
                                    title = "Synopsis",
                                    checked = showSynopsis,
                                    onChecked = { playerListPrefs.showSynopsis.set(it) },
                                )
                                PlayerSwitchRow(
                                    title = "Date pill",
                                    checked = showDatePill,
                                    onChecked = { playerListPrefs.showDatePill.set(it) },
                                )
                                PlayerSwitchRow(
                                    title = "Dim watched episodes",
                                    checked = dimWatched,
                                    onChecked = { playerListPrefs.dimWatched.set(it) },
                                )
                            }
                        }
                    }

                    // ── Watched filter ──
                    item {
                        PlayerListCard(label = "Watched filter") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Watched filter",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(modifier = Modifier.padding(top = 10.dp)) {
                                    SegmentedToggle(
                                        options = listOf("Off", "Show watched", "Hide watched"),
                                        selectedIndex = when (watchedFilter) {
                                            "SHOW" -> 1
                                            "HIDE" -> 2
                                            else -> 0
                                        },
                                        onSelect = { idx ->
                                            playerListPrefs.watchedFilter.set(
                                                when (idx) {
                                                    1 -> "SHOW"
                                                    2 -> "HIDE"
                                                    else -> "OFF"
                                                },
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    // ── Sort — DIRECTION ONLY (ROUND 103: "the only sort
                    //    option which should be given here is ascending or
                    //    descending. It does not need to give any other
                    //    options.") ──
                    item {
                        PlayerListCard(label = "Sort") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Sort episodes",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(modifier = Modifier.padding(top = 10.dp)) {
                                    SegmentedToggle(
                                        options = listOf("Ascending", "Descending"),
                                        selectedIndex = if (sortDescending) 1 else 0,
                                        onSelect = { idx ->
                                            playerListPrefs.sortDescending.set(idx == 1)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                // The settings-page scroll blur (the shared design-system
                // overlay — the same top-edge fade every settings page wears).
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

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-4): the preview's mapping — a REAL library PreviewItem into
//  the player renderer's data bundle (the same fields the player stacks map:
//  number, title, thumbnail, date, audio, synopsis).
// ════════════════════════════════════════════════════════════════════════════

private fun PreviewItem.toPlayerRowData(
    isCurrent: Boolean,
    isWatched: Boolean,
    progressFraction: Float,
): PlayerEpisodeRowData {
    val ep = episode
    return PlayerEpisodeRowData(
        episodeNumberText = EpisodeTitleParser.formatEpisodeNumber(ep.episode_number),
        displayTitle = EpisodeTitleParser.getDisplayTitle(ep.name, ep.episode_number),
        thumbnailUrl = ep.preview_url ?: fallbackCoverUrl,
        dateText = if (ep.date_upload > 0) formatDate(ep.date_upload) else null,
        audioLabels = parseAudioLabels(ep.scanlator),
        synopsis = ep.summary,
        isCurrent = isCurrent,
        isWatched = isWatched,
        progressFraction = progressFraction,
    )
}

/** The audio pills — the player stacks' SUB/DUB/HSUB parse, scanlator-only
 *  (the reconstructed scanlator already carries the library's aggregates). */
private fun parseAudioLabels(scanlator: String?): List<String> {
    val haystack = (scanlator ?: "").uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    return buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
}

/** "MMM d, yyyy" — the player pages' date format. */
private fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(epochMillis))
}

// ════════════════════════════════════════════════════════════════════════════
//  The local card + switch shapes (the details settings screen's privates,
//  duplicated per the poster page's established reasoning)
// ════════════════════════════════════════════════════════════════════════════

/** The section card — the SettingsGroupCard look, single-8dp-gutter form. */
@Composable
private fun PlayerListCard(
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

/** The switch row — title + switch (no descriptions; the live preview is the description). */
@Composable
private fun PlayerSwitchRow(
    title: String,
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
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}
