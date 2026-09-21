package com.confused.anikuta.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.BackAction
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.feature.animedetails.EpisodeDownloadState
import com.confused.anikuta.feature.animedetails.EpisodeListDisplayStyle
import com.confused.anikuta.feature.animedetails.EpisodeListRowStyle
import com.confused.anikuta.feature.animedetails.EpisodeRow
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.koin.compose.koinInject

/**
 * D-554: the episode-list appearance page — reached from Appearance →
 * "Episode list" (the row that used to dead-end into a placeholder).
 *
 * # The pattern (the D-477/D-481/D-525 doctrine, applied to rows)
 *
 * The same shape as the notification-poster page: a **stationary live
 * preview** region at the top that NEVER scrolls away, and the options in
 * a LazyColumn below it (the D-525 single 8dp gutter — no stacked card
 * padding). The preview is NOT a mock-up: it renders the SAME [EpisodeRow]
 * the details page draws, fed from the SAME [EpisodeListPreferences] keys
 * this screen writes (what you tune is exactly what you'll get — D-481
 * honored literally). One source of truth, zero drift: every flip here
 * re-shapes the preview above AND the real list on the details screen.
 *
 * # D-523: curated options, not a free-form editor
 *
 * The first draft's customizability is ONE layout style (the three-way
 * DETAILED/COMPACT/MINIMAL toggle — curated presets, the Library
 * `displayMode` prior art) plus six element toggles whose defaults equal
 * today's behavior (the zero-prefs experience is byte-identical to the
 * pre-D-554 list). Element toggles are honored WITHIN the style's frame:
 * a style that never renders a section (MINIMAL has no synopsis) cannot
 * be talked into rendering it — the preview demonstrates the truthfulness
 * live instead of documenting it.
 *
 * # The preview samples
 *
 * Three static rows covering the content shapes the real list meets:
 * a full row (thumbnail + synopsis + date + SUB/DUB pills + a watch-
 * progress bar), a WATCHED row (the dim + grayscale treatment), and a
 * bare row (no thumbnail → the number-disc path; no scanlator → no
 * audio pills). The sample thumbnail is a tiny generated PNG embedded
 * as a data URI (offline, real pixels — the grayscale/dim effects must
 * be visible without network). Callbacks are no-ops; the rows are inert
 * (the swipe gesture springs back — the real row's behavior).
 *
 * NOT part of this page (deliberately — the surfaces coexist): sort /
 * filter / grouping live in the list-settings SHEET on the details page
 * (list SHAPING); this page is row APPEARANCE only.
 */
@Composable
fun EpisodeListSettingsScreen(
    onBack: () -> Unit,
    episodeListPrefs: EpisodeListPreferences = koinInject(),
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

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            CollapsingHeader(
                title = "Episode list",
                collapsed = collapsed,
                actions = { BackAction(onBack) },
            )

            // ── THE STATIONARY REGION — the live preview. Sits OUTSIDE the
            // LazyColumn: it never scrolls away (the D-525 verdict). The rows
            // area is height-capped with an internal scroll so the options
            // below stay reachable even when three detailed rows would
            // otherwise push them off a small screen (internal scrolling is
            // not page scrolling — the preview still never leaves the stage).
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                EpisodeListCard(label = "Live preview") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        EpisodePreviewSamples(style = style)
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
                    // ── the layout style — the THREE-WAY toggle ──
                    item {
                        EpisodeListCard(label = "Layout") {
                            LabeledToggleBlock(
                                title = "Row style",
                                description = "How much each episode row carries",
                            ) {
                                val names = listOf("Detailed", "Compact", "Minimal")
                                SegmentedToggle(
                                    options = names,
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

                    // ── the elements — switches with ONE-LINE descriptions ──
                    item {
                        EpisodeListCard(label = "Elements") {
                            EpisodeListSwitchRow(
                                title = "Synopsis",
                                description = "The two-line description (detailed rows)",
                                checked = showSynopsis,
                                onChecked = { episodeListPrefs.showSynopsis.set(it) },
                            )
                            EpisodeListSwitchRow(
                                title = "Date pill",
                                description = "The release-date chip",
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
                                description = "The bar on the thumbnail's edge",
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
                                description = "The per-episode download control",
                                checked = showDownloadControl,
                                onChecked = { episodeListPrefs.showDownloadControl.set(it) },
                            )
                        }
                    }

                    // ── the honest pointer: list SHAPING lives elsewhere ──
                    item {
                        EpisodeListCard(label = "More") {
                            Text(
                                text = "Sort, filter, and grouping live in the list " +
                                    "settings sheet on any details page — this page " +
                                    "shapes how each row looks, not which rows appear.",
                                fontFamily = RobotoFamily,
                                fontSize = 13.sp,
                                lineHeight = 18.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
// The preview samples — three static SEpisodes through the REAL renderer.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-554: the sample rows. Built ONCE per composition (remember) — the
 * [EpisodeRow] reads its own prefs (the thumbnail fallback) via Koin and
 * takes everything else from [style] + these inert episodes. Callbacks
 * are no-ops: the samples are appearance fixtures, not playable rows.
 */
@Composable
private fun EpisodePreviewSamples(style: EpisodeListDisplayStyle) {
    // Row 1 — the FULL row: thumbnail (the embedded sunset PNG), synopsis,
    // date, SUB/DUB pills (from the scanlator vocabulary), a watch-progress
    // bar at 40%, and the download control.
    val full = remember {
        SEpisode.create().apply {
            url = "preview://sample-1"
            name = "The Journey Begins"
            summary = "A quiet morning is interrupted when the first gate opens " +
                "over the harbor, and everything the crew trained for finally matters."
            scanlator = "SubsPlease DUB"
            date_upload = 1735689600000L // Jan 1, 2025 — a stable sample date
            episode_number = 1f
            preview_url = PREVIEW_THUMB_URI
        }
    }
    // Row 2 — the WATCHED row: no summary (the pills-row download variant),
    // HSUB-only pills, the dim + grayscale treatment when "Dim watched" is on.
    val watched = remember {
        SEpisode.create().apply {
            url = "preview://sample-2"
            name = "Echoes of the Past"
            summary = null
            scanlator = "HSUB"
            date_upload = 1736294400000L // Jan 8, 2025
            episode_number = 2f
            preview_url = PREVIEW_THUMB_URI
        }
    }
    // Row 3 — the BARE row: no thumbnail (the number-disc path MINIMAL
    // forces), no scanlator (no audio pills), no summary — number + title.
    val bare = remember {
        SEpisode.create().apply {
            url = "preview://sample-3"
            name = "Into the Depths"
            summary = null
            scanlator = null
            date_upload = 0L
            episode_number = 3f
            preview_url = null
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        EpisodeRow(
            episode = full,
            metadata = null,
            onClick = {},
            downloadState = EpisodeDownloadState.NotDownloaded,
            isWatched = false,
            progressFraction = 0.4f,
            onToggleWatched = {},
            style = style,
        )
        EpisodeRow(
            episode = watched,
            metadata = null,
            onClick = {},
            downloadState = EpisodeDownloadState.NotDownloaded,
            isWatched = true,
            progressFraction = 0f,
            onToggleWatched = {},
            style = style,
        )
        EpisodeRow(
            episode = bare,
            metadata = null,
            onClick = {},
            downloadState = EpisodeDownloadState.NotDownloaded,
            isWatched = false,
            progressFraction = 0f,
            onToggleWatched = {},
            style = style,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Local card + row shapes — the poster page's PosterCard/PosterSwitchRow look,
// duplicated here as privates (the poster's are file-private; sharing would
// widen their visibility for no reuse value beyond these two pages).
// ─────────────────────────────────────────────────────────────────────────────

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

/**
 * The toggle block — the poster page's SegmentedOptionBlock shape
 * (title + one-line description + the control slot).
 */
@Composable
private fun LabeledToggleBlock(
    title: String,
    description: String,
    toggle: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = description,
            fontFamily = RobotoFamily,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(modifier = Modifier.padding(top = 10.dp)) {
            toggle()
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

/**
 * D-554: the sample thumbnail — a 120×68 sunset-gradient PNG (generated at
 * round 66, 3.5 KB) embedded as a data URI. Coil's data-URI fetcher renders
 * it offline, so the preview's thumbnail slot, the watch-progress bar, and
 * the watched dim/grayscale treatment all show REAL pixels with zero
 * network dependency. Kept tiny on purpose: it lives in the APK forever.
 */
private const val PREVIEW_THUMB_URI = "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"
