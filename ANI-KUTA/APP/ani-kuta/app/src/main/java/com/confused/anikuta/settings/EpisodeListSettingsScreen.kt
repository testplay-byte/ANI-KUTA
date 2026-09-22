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
import com.confused.anikuta.feature.animedetails.EpisodeListEntry
import com.confused.anikuta.feature.animedetails.EpisodeListRowStyle
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.koin.compose.koinInject

/**
 * D-554 → D-555: the episode-list appearance page — reached from Appearance →
 * "Episode list".
 *
 * # The pattern (the D-477/D-481/D-525 doctrine, applied to lists)
 *
 * The same shape as the notification-poster page: a **stationary live
 * preview** region at the top that NEVER scrolls away, and the options in
 * a LazyColumn below it (the D-525 single 8dp gutter — no stacked card
 * padding). The preview is NOT a mock-up: it renders the SAME
 * [EpisodeListEntry] dispatcher the details page draws, fed from the SAME
 * [EpisodeListPreferences] keys this screen writes (what you tune is exactly
 * what you'll get — D-481 honored literally). One source of truth, zero
 * drift: every flip here re-shapes the preview above AND the real list on
 * the details screen.
 *
 * # D-555: FOUR completely different layouts — the redesign
 *
 * The v1.1.28 device round rejected the first draft's three row variations
 * ("switching between the three available layouts is definitely not good …
 * a complete UI redesign … rather than just hiding some features like
 * synopsis or maybe adjusting the shape a little bit"). The Layout toggle is
 * now the FOUR-way CLASSIC/GRID/TIMELINE/CINEMA selector, each a genuinely
 * different structure (see the EpisodeLayouts.kt header), and the toggle
 * carries a live identity line so the option's personality is stated where
 * the choice is made.
 *
 * # The preview samples (the user's spec: EXACTLY two)
 *
 * TWO static rows — a fresh episode (watch-progress bar at 40%) and a
 * WATCHED episode (the dim + grayscale treatment) — both carrying REAL
 * thumbnail imagery (two generated anime stills embedded as proper
 * `data:image/jpeg;base64,` URIs), release dates, and the audio
 * vocabulary. The D-554 draft's thumbnails rendered EMPTY because its
 * constant was a bare base64 payload without the `data:` scheme prefix —
 * Coil's DataUriFetcher never matched and AsyncImage drew nothing; the fix
 * ships real pixels AND the correct scheme. GRID previews the two samples
 * side-by-side (the wall's actual shape); the other three layouts stack
 * them. Callbacks are no-ops; the rows are inert.
 *
 * NOT part of this page (deliberately — the surfaces coexist): sort /
 * filter / grouping live in the list-settings SHEET on the details page
 * (list SHAPING); this page is list APPEARANCE only.
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
            // LazyColumn: it never scrolls away (the D-525 verdict). The two
            // sample entries are height-capped with an internal scroll so the
            // options below stay reachable even when two CINEMA banners
            // would otherwise push them off a small screen (internal
            // scrolling is not page scrolling — the preview still never
            // leaves the stage).
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                EpisodeListCard(label = "Live preview") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
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
                    // ── the layout — the FOUR-WAY toggle (D-555) ──
                    item {
                        EpisodeListCard(label = "Layout") {
                            LabeledToggleBlock(
                                title = "Layout",
                                description = "Four completely different designs",
                            ) {
                                val names = listOf("Classic", "Grid", "Timeline", "Cinema")
                                SegmentedToggle(
                                    options = names,
                                    selectedIndex = selectedStyle.ordinal,
                                    onSelect = { idx ->
                                        episodeListPrefs.rowStyle.set(
                                            EpisodeListRowStyle.entries[idx].name,
                                        )
                                    },
                                )
                                Text(
                                    text = layoutIdentityLine(selectedStyle),
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
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

                    // ── the honest pointer: list SHAPING lives elsewhere ──
                    item {
                        EpisodeListCard(label = "More") {
                            Text(
                                text = "Sort, filter, and grouping live in the list " +
                                    "settings sheet on any details page — this page " +
                                    "shapes how each episode looks, not which " +
                                    "episodes appear.",
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

/** The selected layout's personality, stated where the choice is made. */
private fun layoutIdentityLine(style: EpisodeListRowStyle): String = when (style) {
    EpisodeListRowStyle.CLASSIC ->
        "Classic — the familiar detailed rows: thumbnail, pills, synopsis, download control."
    EpisodeListRowStyle.GRID ->
        "Grid — a two-column poster wall: big imagery, overlay badges, titles below."
    EpisodeListRowStyle.TIMELINE ->
        "Timeline — a schedule spine: every episode hangs off its air-date node."
    EpisodeListRowStyle.CINEMA ->
        "Cinema — full-bleed banner cards: the image is the card, the meta rides on it."
}

// ─────────────────────────────────────────────────────────────────────────────
// The preview samples — exactly TWO static SEpisodes through the REAL
// dispatcher (the user's spec: "in the live preview, it should only show a
// total of two episodes").
// ─────────────────────────────────────────────────────────────────────────────

/**
 * The two sample rows. Built ONCE per composition (remember) — the
 * dispatcher resolves display data through the SAME production pass the
 * real list uses. Callbacks are no-ops: the samples are appearance
 * fixtures, not playable rows. BOTH carry thumbnails (the user's verdict:
 * "the episodes should be given the proper thumbnail images too … the
 * thumbnail images remain empty") and dates ("the release date and all
 * other stuff like that needs to be shown properly").
 */
@Composable
private fun EpisodePreviewSamples(style: EpisodeListDisplayStyle) {
    // Sample 1 — the FRESH episode: watch-progress bar at 40%, SUB · DUB
    // pills (the scanlator vocabulary), a synopsis (the CLASSIC-only section),
    // and the harbor-gate still.
    val fresh = remember {
        SEpisode.create().apply {
            url = "preview://sample-1"
            name = "The Journey Begins"
            summary = "A quiet morning is interrupted when the first gate opens " +
                "over the harbor, and everything the crew trained for finally matters."
            scanlator = "SubsPlease"
            date_upload = 1735689600000L // Jan 1, 2025 — a stable sample date
            episode_number = 1f
            preview_url = PREVIEW_THUMB_1_URI
        }
    }
    // Sample 2 — the WATCHED episode: HSUB pills, the dim + grayscale
    // treatment when "Dim watched" is on, and the rainy-alley still.
    val watched = remember {
        SEpisode.create().apply {
            url = "preview://sample-2"
            name = "Signal in the Rain"
            summary = null
            scanlator = "HSUB"
            date_upload = 1736294400000L // Jan 8, 2025
            episode_number = 2f
            preview_url = PREVIEW_THUMB_2_URI
        }
    }
    if (style.rowStyle == EpisodeListRowStyle.GRID) {
        // The wall pairs its cells two-across — the preview mirrors the real
        // list's shape (the chunking lives at the list level in the details
        // screen; the preview pairs its two samples directly).
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) {
                EpisodeListEntry(
                    episode = fresh,
                    metadata = null,
                    onClick = {},
                    downloadState = EpisodeDownloadState.NotDownloaded,
                    isWatched = false,
                    progressFraction = 0.4f,
                    onToggleWatched = {},
                    style = style,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                EpisodeListEntry(
                    episode = watched,
                    metadata = null,
                    onClick = {},
                    downloadState = EpisodeDownloadState.NotDownloaded,
                    isWatched = true,
                    progressFraction = 0f,
                    onToggleWatched = {},
                    style = style,
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            EpisodeListEntry(
                episode = fresh,
                metadata = null,
                onClick = {},
                downloadState = EpisodeDownloadState.NotDownloaded,
                isWatched = false,
                progressFraction = 0.4f,
                onToggleWatched = {},
                style = style,
            )
            EpisodeListEntry(
                episode = watched,
                metadata = null,
                onClick = {},
                downloadState = EpisodeDownloadState.NotDownloaded,
                isWatched = true,
                progressFraction = 0f,
                onToggleWatched = {},
                style = style,
            )
        }
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
 * D-555: the two sample thumbnails — 448×252 anime stills (generated at this
 * round) embedded as PROPER `data:image/jpeg;base64,` URIs. Coil's
 * data-URI fetcher requires the FULL scheme: the D-554 draft's constant was
 * a bare base64 payload, no fetcher matched, and the preview thumbnails
 * rendered empty — the user's "the thumbnail images remain empty". These
 * render offline (real pixels, zero network) so the preview's thumbnail
 * slots, the watch-progress bar, and the watched dim/grayscale treatment
 * all show on real imagery. 448px wide so the CINEMA banner (which renders
 * near-full width) stays crisp; kept JPEG-q64 on purpose — they live in
 * the APK forever.
 */
private const val PREVIEW_THUMB_1_URI = "data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAwICQoJBwwKCQoNDAwOER0TERAQESMZGxUdKiUsKyklKCguNEI4LjE/MigoOk46P0RHSktKLTdRV1FIVkJJSkf/2wBDAQwNDREPESITEyJHMCgwR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0dHR0f/wAARCAD8AcADASIAAhEBAxEB/8QAGwAAAgMBAQEAAAAAAAAAAAAAAgQBAwUABgf/xABIEAACAQMCAwUEBgkDAgQGAwABAgMABBESIQUxQRMiUWFxFDKBkQZCUqGxwRUjM2JygpLR4UNT8CTxBzREgzVUY2Rzk6Kywv/EABkBAAMBAQEAAAAAAAAAAAAAAAABAgMEBf/EACkRAAICAQQCAwACAQUAAAAAAAABAhEDEiExQRNRBCJhMnEjMzRigbH/2gAMAwEAAhEDEQA/APNtGynBFCBmiyfGhrp3IOxvTEfswGGjL56lsUvQhiDmlIqJrHhazW/bWwKgKSVJzWWRitPhvGpLIFTErqfPBoL66guYf1cQictqYA7N5+tZQlNOnwaTjBq1yJRRGQ7MB6100MkL6ZVwSMjzoQ5RhinoLwytHFMEdNWO8ucA1bk0yFFNCFdV13EIbuWIAgK5ABHSqq0TtWZtU6IrqmuoAiuqa6gRFTXV1MDqiprqQEV1TUUwOqa6pxtQIjFSBRafnUhd6BNkxQvK2mMZPXwHqaaSKxg3uJHuH/24jhR6t/aqTHIsSkhgjgkeDYq62sJ7iMSd2KDOO0kOF+HU/CgylL92K7i4SSNY4raKFVycrksfUmq4hHrJm1ldJxp8ela9vw2GMRu+ts7EsRGOoPiatuYeHJa26I0BlC9/s0Lc8ee/WizLyrhGNbralP8AqDKG1r7mPd6/HlVcyxh/1LMV397nz/tWuIrAxqDJh9eN4MDGrxB8KgWdnI5C9mw057khXx6HPlRYeWnbMbQcZxt44rtGSuOZrY/RBYgW82HblHL3M+h5GkbmzmtpCk8TRsDjvDnQaRyp8MUIwcHpUYNXAYI1LqUHOOWaAjngbfhTNFIAgY2zsN8/lUURGMGuKHTr6ZxnzpFJg11dXUFHV2a7FdQB1dXV1AHV1dXUAdXV1dQB1dXV1AHV1dUUATUVNdQBFdXV1AHV1dXUATXHapBB8qEtUajSmQdxQ1Oaiosqjq7PjU11KwIo0bSc0NdQBpPfpcqi3EYYoNOoHDEeZro7CO4jc202ZFGRG+AW9POs4VZFK8bhlO4NTul9Stm9znjaNyjqVYHBB6VGK1kPtrvfyxrIVx2seMlumRWZIFEjdnnTnbUMGtIzsiUKArsVOK7FXZFEV1TiuphRFdXV1AqIqcV1TQBwFSBRkR9ioAftdR1ctOOmPPnXBfEYzTIbIVcmro49RwPjUxRlmAUEk8gK9DYWkcKLKshXSO0DsP2a497H2jyA+NByZcukTtrAgAyKgaMjKMO5GPFz1P7tXgvcF/YEMjoCWmcAbfujkoqZB7SF1o8dtzihXdpTnmf71r8L4VJet+vYQwrkdmnKlwcrk2/0wjaIJVFxI9y7ANpQ9fWtOy4ZKxIh4dHnDAF+8RgmtRxY2SBQiALtnx+NWHisSyMbNdRCErpGd8Zo36MvLb3MSXhnEo7sM9pF2ercaBpxq8BWfLbJG6RyWzICAzYPkfLbnXpY+LK7RySo65ALZQ/awelGl7aXHEDFlGIfG/8AmptmiyOzzlt7VZxq8DpcofehI1AeVNIba6g0aO9jBt5dwPNW558q9H+h4po0MZEbElgw5Dw/GsritmqTdnKcTDBMwGNWPHx9aWpGji2rZ5m7sRGXMYZ4wPf5aT5/2rOkjKNgjBr10KyJK8T6TcDZ1bBWUfnt1rOv7BLXspY1EsTg6dY3HiD0B/OqTNIZGtmeewKHFP3NqqYeB+2j06mIB7mTyJ8aTIqzqjKyvFRijxUEUjVA1FFUUhkV1TXUxkV1Tir7GzkvblIY9gTuxGyik2krYJNukUojOwVFLMeQApp+F3scTSvDpReZLCtXsLDhcLTQ3DTXBBQLnYZrKlnkb3mzvnBNYeVyf14N/Eor7AexSKMzPHFtkBm3Pwql49IHeB9OldLI0rl3OWJ3qsmrUn2Q4romuoc71OqqUiHEvjiXstchwOgB3oXSIRhkly2d1IxVOTXZpWVRNdXCpq7IoipqKmmANdipxXaSK5rOigcV1Fiup2FEV1dijiK57wzSsaQGDXVrQ2Mc8JZfe8AaSntjGCfA1Cmm6LcGlYtUqd64qfCoqiD0PCSIeHySybxOQjqBufAfGsWQYkYAEDJ2PStCy4hbrbpBc2/aAHx50reKq3TiMKEzkaeWKnFtJ2Vl3iqF66prq6DAiup6wsobrHaXKw43Oocx5Uw3CbYSgHiEQVjgEAnFQ8sU6ZaxyatGTiuxVskLxzPFjJUkHHKhkjeJtMilG54IxV2iKAqQKiiXYHYb0yWiRjOSAaMYLbD4eVTDGZpUj1omo41OcKPWmYLOUvEzoypLnS55EDmfhVHPNpGpwjhZnh7Q5yx2GPq9fmdhWm8WWIbS0MByy52ml6DzA2pmxdo7bEQCyOFhh8tvyG/xpux4b2kxEmVig/ZjxPVqTZ5TbnLYjhnDSsj3VywVgPD3QeYFdxHiq2qIIAAFGDQ8W4oqDs4hsBhQDjPjv08zXlb297TKxMWbkz8hjwA6D7zS53ZUcbk6Rfd3qtIZFUTHnqY7D4dKUub6UFVaZGAUkiIjbpiknlAQpqZ9+QOFHnVBYgYGPlVHXD46Q3Feuq41nkB9+aeg4k5cSSNG51e6R3h6E/3rEBolY0GrwxZ7jhXFmhgLx6iiEBonbcehrUtL63viRKoONwG5qa+eRTuunDEYO29a1nxAmZCciQHYjr61Lic7xSi9jZ4jw5o+9glScxyA8j/mqGMlzbhZ3jRO0AeIZ7reOPPl8q9TaT21xYhCyM6jBz0PjXnuIQi1nSZ4wIpF7OUDnjPP1qIysU4qNI81cq9uJYWyUc+4cjDcs4rNYsU7M8gcjbevW8ZsGkidi2qRF3bHvdc/8868vOCSrnmRvWyZphdipG9Qy/CjI35VbO0DW9uIkKyKpEpz7xzsflQdSYrUURqKVmqRFdU0zY2Mt450d2NPfkPJamUklbKUW3SG5bK1sRGJ8zS41Nhu56DxpY8QljUpDpjjJzpUYo+LTe0XOUyUUBQxG5xWcwOd654/ZXI3kqdRDaViaEkk5NW21rJcNhFJA5nwqyW3EK7746iq1JOhaXVihqKk86iqIo6oqanFMRFcBVscTyZ7NGfHPAziudHjOHRlPmMUxFYBqa4sBXagaadCOxU1wrqqxUMwQLK2lZEBx9bbNccxjS8ecdeYqldjWgrwxWTqkuS4wRgnArlezOlbigQSkCNNzyFXfoy5yMxkauVLAlSdJIqxJ5lUKsrBR0ztTcX0JTXZElpKrlWiII51dDYMzDtF0ihW6lHNyRXe1SAd0kE9Sc1LjMtSgalrbWtrcFXulwPeUnFK3l9as7Lb2oCnbUx3NISSFznAzQEk9aUcSu2EsrqkEzhvqgVWQD4V2KPspOz7TQdHjWtJGW4Cgg5FE8mpstjPkKA5qKfAnuWDHXlWq7cLFpHIsWT7rIWwfWsYNiuLE0pLUOP1LJHUOezyFJ29KDWc5zUYqMUwLlnYDzrZ4XcSX6TWkqLMzRHswxAOfI1g4p7gsgh4nCxcIM7sTsKzmrjsXB7izxtHI0cg0spww8DUgZxmtC6sp7jiEjQxgLIxZSZAQfjUXfCbyyiEs8Y0HYlWzg1rHItre5lKDV7CajJ5ZztWnZjbTGz7ISysNgc7Y9dqzkXl516DgNlJNMjBQY2kwfHbetLOHOvqaixTYiiBy+BGp/ePvH8BWtd3SWVkka96QgIoB3Y+tL20WT20v1FyFPiTk/dWHxW4LvK/2DpQauXicfdUt2zhx42/+xO/Yz3bRpINTH9YzHCqeZHoPvrHlcnug93PIdateVtDJnZjk+dLMapHfjx0Cx3oCarnnSEd7n+FZ0vE27UdngKOY5g/dSc0joUTVz51INZDcQWSeMspCJuRnGWp+C6jmHdJB5YPWhSTG0NqaYjkw2cYHgKVBqxSasylE9FwriMkU6amJUbfCvWXUMN5w9mQZJXOPxH5189tiR3gwyCNjz9a9h9H73VphbOOg/Ks5Ls45wUWdZntYkWc5MZMTjGSR0+7NeU4nbNbXE0J5xuQPSvZ9h2PEJEwcMMqPQ5rL+l1qY7pJyRpcYx8KIvcWLY8jKhQoWxhxqGDn51SQQMeBpmaGNbZZFmVnLspjxyAxg/H8qpmz2jEgLnfCjArQ7o7lVRU12Kk1oitLhPEhZa4ZIhJDKe/vv8ACs9VLEBRknkKgqVOCCD4VEoqSplRbi7RtqeHXIbMjQZPdLAYNJXyWySYRNX72rnQ2cFs9rLJMzsyfUXbHgaVk1Hc58K59CT2Z0621ui0XbRxlI1CgjfFKtI55k1aAjLg7Hxqp1wcZzVpIhtgGuyPCuqKsgnNcDUVxFMVBhiNwSPQ1DyM3vEn1OaGoosmjq6urqACVuhFHVQODRhixqkxNDGFHU/Kjil7InChgRyNcFX62fhV8Cxai/YySIBkjntWTSRqm2LSFXbKoE25A0OKbu3tZNJt4WiOO9vsfhS+KuPBMtmCF2qzsBj9rH6ZocV2KHYgCmDg1GKtxUaaBleKklsYyceFHpoo9KsC6BwOhpMCsQOYmkC5ReZ8KpxWlJbCWHtEeNSR7gGKUa3kVtLKQfMVGtM00MoxXYpiSCOPSDISSNwByqnQScDrypp2JqjlTV7uT6Co0nfY7c/KrAJIW2yD1FaDcVJTs1hRIyAGAUb1Lk+kNRXZl4rgSK0rgwXNuZdGiYcyDgH4UhpFOMrFKNBLMwAAJ2ORvyph7q4mRVmmd1HIMcilQtXxKuV7QEqDvpO9WqIY7G+RGZIY2XJ90Yz8q9RwWchZJIo0QRxk7Dqa8/Z2kEluHW8RJQrMYpBjIHIA9Sa9Bw1AthMeRIVefiRTpHD8jI9LSNDdLWXC6mbp5Af4rynGUWOdVVWU6BqVjnB/zz+Nb3EnYRjSxXG+fQZrzV2e0Yu0jMxO+R06UuGHx43BMzn5UvMwjQsRnoB4mm3GxORXneI3UXbHs3eQ4IDM3dGfCqcqR1KInfTmeYjVlR18fP8A50pbmR4Vx3X1rgRqx41mM4E5NXW8vZuDvpOxFUZwuakczj1oA9Lay9rGQTll2J8fA00lYXDpVLIWjeRl7uEJyPPHX0reQE4wD6VpGWwmhy37H2WTWH7bUNODtjfOfurc4PKsOhzjU5wCD7njWBAxU5B5jFavDWUhVkUuA/IHHMVTOecLPTs5EtrJqzlsH4is/wCkyO8ETFicrjHmKbVlaziZVPdZSfn/AJruOxk2BOM4JqVyca2lR4WZcasgc6qKltz4VoizmncpFGSW5E7Dn40w3DBaRMZNM0o3UKcKP70p5FE9XFjcjK/R113O4AXOACworjh5t5CrTxHHnvRyPcLI0s4clvGlwVaXXMG0nnp51kpSZu4xSAkRkfDDB6Yq/s7VgF1OrEftHO2apdsuSCcHlnwqGZmA1MSByrWmzO0i7U1qjLDNE4cYbSKXZ2PWmLezaeOSQusaIBlmzg0MtsEQMs0bjGTg4qPrdPkr7Va4FSc0LCrWVcHvqTQBcmmBXXVYoUNltx4VdFDFNJp1aM8s0rodWK11MPbMjHGWA64xVOM+RpqV8CaaBxUEU5bSompJFBRue29V3AjLlkwB4Cp1b0PTsLYqKswPGo0iqsmgMVIoxGTyFGIvE09xbGpH2Btmljbspl+o3eDelVi7nByGA2xsoqgOvnU6l8anSuyrfRxySSeZqMUW3jUjB61dkELGze6pPpTlrw2ScA5C/wAVK8uRxV0VzLG4bUWx0JqJ6q+ppDTf2GV4WuX7aQR+AG9Z5XDFeeDjNWygzSloiyk74LULRzA/rFJPjWcG1/Jlyin/ABRYsUQjJ9pVWxy0k0CLbgAyl2Od1Xbb1oW0AZDeoNQCCM1aV9kvbouVbMo5aSYEHuDGdqujviNIKmUjYa+RpZdAbvLqHntTbW1u+TDMFONgQedRPT2XFy6Lls4rlGZoBHIegbasi5tTE5G4IPhT6zXNoCupd/Rqsj4gJNQuow+dwQOVZJTjut0aNwls9mYra/rEn1rghPLettPZbhWQxacciGrhDYwI0UjPqO6sOlPyPihePuzKFrPpyEYqfCpjt2Y7qR4ZrSs70xErIS6jlnc1Ml0O0YjEiMc6ce75U9U7qhaYVdmcsL6tLIflTkdjLk6ozgDPdpk8QAQKsQO3MigjuJA2UOnA6VcHN9GU1BLkus7Mv3mOMKMZBr0trB2doU55kUZA8jWDas7K0naEPsAAvTxzXpLXULVCwYky/ka0V3ued8mmqQpxORREwdM938TWO/shn0sph6EEZ+dbPFXRiO1RgCBy55rBvAkhLo+onds86zludPx9oilykI1BYlOT7wcEfKvIcet9FyZGOouBoUDAXcgAfL769g3D7ptxA24yPOvP/SDhl0GExyZUkSOKJN9yeZNNNcWbyi+aPOQRGaWOPvDJ30rkgelXiAS2bvCn7NmcnGMDbb4D8aOwZRxVSpykmrGfMcvyrZaKLsmQoioRvtgU2yFGzBtrdmt5ZtGVXYNj3TzJ/wCeNBLCI4IpV1jWSAGUDboQR8a9EiIkYjRVCDbA5Vm8ZAYQwxqoIJYnkFHLfwpKVsHGkK8OWY3S9iMuAXAO2oCvV20rRlZUXS2M94bisThFm8kouo3wsREao31hjc+XPIre0r0zWi4EthmEpNOZLl9IP2BitO1SKKJymiYMNtzlcEdKx4xWnYErqIOMod8Zoozm9tzegBexbAOP+9PXa5gOCQyorD1pSxZnt3DZPX76avy3ZqVLAMi5xVI82a+x5Ka/ukZgJiAcnHSkJLmR1Orfw8q17mzSILKzodStkZBPUVYsvA7aDU0QmlAxg5Ynb5UnFc0ehHM6Vbnm/wBZKTksRzOTTpWMwqYGiMoUhy4FLsbcjaOT4vVbozrlAqgee9ZSTZ2xaKZnxlQgUDbx++q1JxyyKPsmJ3wabis5ZrdpImjUIwBDHFVelW2TVukJMZiunfHhVbK4GCDV7yGNyrFWwcZHWhacY7p3qlRDbKAjfZPyouzk6Kakzv8AaqO3k+1TERpbwNSpdGypIbxrtcjH3jViI31gcUDBeWeTZ3Zs9DUGCUKrlSA3I+NNZCjTHnT+8BUHJNCQNi4iPU0RiXHKrlR3YKilieQFWezlIneSRI2QgaCdzQ2lyJJvgVEajpTJs2WFXygZhnQeePGr7jiVvGuLaJCxA1OV6+VIzcQnlm7UsA2nTsOlQpSfCotxiuXYxb2Uk5P1QuM+OKmG0jkaUdox0KSAq86Ra5mYnVIxz41HtEuMdowHLY4pvU+xJwXRoRWirkzRO4x9VhQmGMkdkr4/exRSBEXKyF18QuBXRXIj92XQT+5moXtFv0A1uVxqQjPLNR2Q6Lj40xJcrIB2lzq35aOVEkKTHFvMknkTpP301KuRab4F9JxzqcGrTGVJBHKh0iqsVAYqyOWdSAjn0O9RjyqdJJwKTp8jVo6e1uJmLSDv42woANLi0lP1TtTJVwO9mp75XTlio3xWaTXDL2fRZaRmCEmYhRnqufyoJmGsaVAYHcg5FHHG0kRBc4HTV+VSiKh7kiHxDrUqk7ZTtqkLszORqxv1xipaFlxsDkZ2OafkiuZIgo7LTzCrgYqpLaSM9/TnmAP8VSyIXjdipQEZUMfHblQ6RTmma3IKzIvUANmuzcNliy+ZOKamLQKgEHIOKtjtnkTUhU7+7qwaIa1yofAPPHKhCb7MPWm5ehafZZHZucFyiKRzLVckCJqBkGcb4quKKN1zJPjyplYLLSSkre71PM1GunuxuG3AzaJIsxERU+INejRH9kjUkE9oTsfKvPxqpZm9oLPkDHWt2DV7CmTkh2/Cqi7Zw/JVRRTxKNw6YG4x0zWBOZ0kIiDKRvkIADW7xWaQYVVPQ5A8qwJRdO2DrPkRWcm0zowrYWlkvD3jLJuehpVncsNbFsHO+9MmG6clQpGOfSh9gmwCVfJ6g5pqcVybuMmeQvrA8KkhukAeJAFk8XJLHI9Bj7qfEgkiDxaXU+NHxLidrCzwPCZxg82AB2I22PnvWeYZbcLLbE6WUFl59Ov96tuzKqHI2Z99AAPhnPyIFUz2z8ULWlqVxGBJI/IOc4Cg9frfGqv+oukJmOiIcwoxq8qaXiK2t49mIY9CsFG2DyHX/FLgfPJqmHsslVChvvwMfgBUqyg9/OPIVdHFbXMAeOYBT4j7vWiFpbA73DegTNPyFeM6BBLnRnHnWtBAqR5Dg4Ug5OBvSsKWQwNDkDfUetaKRWiW6sowzZO55+lCyP0ZTgq5NOyXTauc7/8Aaj4nqNtEf3V38OdW2Zj9kduzGNutFxA5tUIVcaQdz61qpHlyheQ8lcoSN9W+rBPXBNZrLhSM9eVbt3IrW0IfA0BsaRtuazWkh7JlLkZPRPzpOR6OOOwjgdc05awhwJI4t197f76m2js+1HaTbHxBGKO9t7h8aJ41iI7uGAyPXrWGTJ0dUMfZXNd8PjjLquuXPLlWfe30c8CwwQLEoOTg5JNXXPCZ4UDnDA+FFb8GkmXUxCjp51ClBbtluM3skZBGagIT0rdm4KYkBUgnrmrE4dbRoO0YFvI1fnj0T4H2ef0kGjQDry9K1ntYGciNQSeWTVV5BHBEqxjU+clgNseFV5LJ8bQugiA7nPqTRMwUZNUnURsgX0FQsE0p2BPrWikkiHFtlgkRts49asRTIcJ3j5VU9sIk1SOoP2QcmgTQxwHYU1K+BONcmlBaMrCR5Y0KNkqzbkVRxFTcXjyhhpY7elVrEemTRtE6Dvgr6jFRX2tstPakikWin/UB8gM1Bt1GQTuOmKvHaRrqVWwfDbNVytK+CIuzB2z40d8gv6KTavn3T8dqgwYHeIHlRqJkbPveu9AySHfB3p2TSNKNbFUzIGY/ZAqGFg5AEbqD91GG4eItDyYbbvjJ+6pklssBEGpR9YEAmse+zev6FmihVv1ZJHmKNRCffXQRyZQf70StZ4OuZkIG2wOaGS7shgJHJtzbVz+FVd+yKrcsMp20XByNgdOM1yzMTl58jwKg0UUvD5IcMWEn7xFWm1tNHae1RIPBjv8AjUuSXKLpvhgdl2kZkWVCBz6YoI2YDCY8+WaehjELobZrYhh3syZB+dDP2+pw0EajljTn76lZL2KcOxVu165+VcZpeXaNRwOQ+kTJGDsScUfaR2+UlKyITzGDTcq2oWn9F/eGCVHwFF2LEZUq3oaYQ27knsJQMHcEEClH4pFDhUgjlKk5LihSk+EDilyydBHWiSLVzfB881WOOSlsvDAw8CnKnBf8OljV3jdHz3lTlSlKa5QJRfDA9iwurtYgD5n+1QbWQd7Rt9rpV8lxw2RV7OeZXJ5bnFX/AKPBj7QXQKeODWfla/l/4XoT4EhaykAqhbP2d6JLWVm09mw+FX9nEmNNwz/zacVVPFkFluGdzz5/jTWRsPGg24ewHekiHq1AFjCsCoGBzFUG3boVPxq1LeTPIAYG+atP2zOS9Ift1te0BViGyM5BxXprOISWkYDjGtiT8K8xbwt7QBjIyN1r1NoumyjGCO83OtIvfk4fkRtcCfE4pBJ3JAB13rz0jTa8iVifHNeg4zJFCJJptkjUsx8gDXzniv0gd3Magxj/AG4zg/zN+QqWrZridI17riBgYpJOzSf7anLf4+NefvuMSTzGFpmWJcl1Rug+rnqSefSsua9ndSiERK3MIMfM0oyZOAduo8qpQKlk9DVmY7u/cy4fUpOOlbI2AA6bV52BmhdHTZ9x8KdXiMi4LBT6sRVtEKS7NRhqxnxzWZxaSFZVXA7QHUzdd6huJSOMBUHoxpKVu3LseZPn+dCQOSZp23EniaMiRgXUgsp5kHYkcjscb+Fb1pxOCaMGbCdNY93Pn1X4/OvFqCpUHcf3piCWWFyyOQfxpOPoan7PoUUOoZBXHTJ50/p7V8mVNyTpxjFeF4Zxd4pFVSEyfdO8bfD6vqK9twqaO7tllQheasjblW8KVtBJJo9FDAY7dlPhzBoOKg+xpj7C/iaZg/8ALEhgTjwqvibYtUzzKjfOPGtEzznH7nk7onQBpxgkZznNIEEqygKSTzPOta7aLsF/VnOpskNud6zHaLfETf11LbPRxpUKyKyY1rjPKgwTjusfhTftMCD/AMqCR11mqZeJXGsGM6AOQFRcn0b1H2Qt5cxqAk7hQORom4pLp7gOep1UI4nPp0lUJ8SKrmuO0RckZ8lxU6faKv0y/wDSznbSVBG+Tq3qXlhdc6iG/GkER3OQPjRGGQAnTnFVoiuCdUnyNQcQNq+FCsM8yNxU395DctqiZ1wOQXmaz9Jzyoo1390tTcI3YKTqju3nGQo+Yqp5pW95jTet42BWMD1FG7Qyrl4MP4qedO66Jq+zNyTzqN6f7GIj3WHxojbW53XXtzFVrSJ8bYiJZAMajj1o1uJztrZh4HetX9FFTGdKorY1Etug8TUXAhs10xkuftAbGo8sXskV4pLlmdLdzTY1sSRsKEifkc7dKsN2qsCI1JHU1Wbxy+o4PkRmrSfSIbXbJT2iPvgEnxNWNcXLrmQkAcsLVYvGzui4zuBTEclvLGCZRG2caW3++k1W7Q01wmUNGynGKEitc2sulWABDeBzj1quSEK5V1GRUrIi/EZWPCu01qLHCCS0efDG1Xq1sY+zkt8rnmWyRQ8rXQ1h/TEwa7fwrZmgsGOIYpBtz/xSp4bOSdKMfDbnQsqfOwnia4M/PlVgml6O/wAzTycIvGO0QHqa0f0XZW8EfthYSP0U5xUyzQX6VHDJ/hgAtzxQk+Vbps+F7/rZiPJarbh1i7Ex3LovQOu+aSzr0N4X0zG1sBgEgeRocVptwo6crPEfLNA3DJlYAlDnwaq8sBeGZngUQFaB4TdhdQi1L+6c0wvA7po9YUA/ZPOpeeC7KWGRlLkHNbtrcC7t0iMgSZeRbk3+aotuFSvJolVl+FNjgj5yrqB4mufLmxvlm8McokmO4gwWVXU7DGGFSXuQulYSqnoq1dBwxowAZuu4XarSk8BKxYKnxcZrneZNmmgQe8kHdeAHGwyCMUCyTyH9mDt0FatuJp76FCq6dQz3gSa0L3hcaqcKIW6NpOPuzXTjaaujlyy0umzKtBP2x0R4yRuOYFenshK1lGcnOtudYggkgvT2UoKbe6wz51u2JJsITv7zVtFnHlV0eZ+n929vZrCcASks23NU3/ErXysksSW3J3Ne6/8AEy5LcQEOfcgVfizE/gBXgtWWXzBNawQukHUE4UnyrqhvdNWIgD9YvktWVWgAdseAo6YiGBYY6daHGGfHh+VHQHnJ/wA6UAEPdHpU0GoAY/Kp1jz+RpDDFey+h8zTOyls9tEW+KnH4H7q8SzYOofZNem+h02i7tVz7spjPoykfiRUT9lR7PqtogFtgmqeKHFvHk47uMfE1daKxt+8wHLrQcTiDRIC49386E6RyuKczy8otwx1K7949cUn2qxk9nEmc/WGqtWe1t9bartV3OxxtWbN7GgIW7DZ55TasnJM9GCaQvPI07FnAGeYUYzVJhjYKNIU9WNXmaxQ4ednOM9xdvTeqvbLQEHs5mHUbChfiNL9s4W8OcFe0xvkDGRUpYoz95DGgO7N0qBxAb9lbFc7DBJql7mbScq4Y+PSj7DuIwUtYgT2+ocgEUj55qEdUyQAc8iTSRW4mIGHbwzVi2iqwFxOE25AZIp1S3ZNt8IskYSP+skBI6cqlYwBlQPhU9lYKue2kJA5aedQbi2VdKiU7+QoT9BXs7HjRIYh76KaFeIxQqdFuHY9ZOnwqpuLyDIihhjHPATOfnTqT6DVFdmnawJeOVS1wo5tyA+NUX85gcRRyQqijku4J86Tl43PJHoOVHXTtn5Ui8isc6j8qmOKV2xyyxrYclkkl1H2lDq3IzSxic5HaL/VVJYdCaj4VuoVwc8siYbW7Dcsn9VB2J+0vzoSTUb1aTM216C7M+I+ddpYdRQ/GjRNZ94D1pi2HQJQxCas/u5o1e4U6TnPgy5phCy+6+PQ1YXmk96TPxrlcjuUP06FlkUJNAysProPxFXCxnb3E1DyNUFZgN9RHkc1IaUfWf5ms3fTNFXZLRSIcOjKfOuDMNgzD40OT1zU0/7GTljzY/OuxUV2aQycCp2qVdhyOKMTP9r7hUtsqkABUgelWrO4+yfVRRrcuPqxn+WocpFqKK0dlGFcgeRxRrJIDtIw/mNWLdN9iP8Apq1bpj9SP+msZN+jRRAWefGO2f8Aqq1J5wmkOTnx3q6GR2I7qf01rWVnNOuQqBfErXLLJvSW4TcYK2YYYc3UsfHOKta6jzkRFfQj+1ehfhT6e6YifDTWRdo8DlXiQEcxpobaf2REJwycCi3oWXUsYUjrzIpn9JysMCRwfJqWMwLY0oP5akSKcbINs77VvDboU4J8mioimvsuZGyR3yefwxW1bqI7ZVXBALV5+0n/AFuoMwOeYY16G2kDWyu7EjJyT4da7cbs835Eao+RfT64M3H7s52E+geiKB+Oa8uv7Qj7IP41p8fuDdXjzHnKXlP8zE1mAfrmPiBXVHgxlzQdQ3T1FTQuM4HifyqhEp7zeo/CioYxgN60VNCOoDzk/wCdKPO+KDrJ6flSAIchXVA5D0ricKT5UDAAyUH7ua1+ATGKdn+yyyf0tmsobN6LTvC9pWXxiYfdUy4HHk+4WyIEZScjGaDiccWhQwyNH50HDnL2sL/ahU/MUPFpHVY2Q4OOnrU9HPpXkMaSwhYs7xnGTzO9UtwqHszIsTaftYq2fiNzqb9ax3PMmlm4ndaCnbHTjGkjIrNs74xl0UiCFiSAGC88f9qz34jGjFY7QnHUt/im/aJApGVwemkUK3Dr9WM/y1kk+9zpfGzEDxGctlYF8sjNWe08RuJVxCF25FdqbNzITsEHotQbmQ8wh/lq7/4onT7kK54oTsiDHgAKDsOIOpDLF6krmmhMQclI/wCkUQuHAOkICeukUXJcJBoXbYotjOV/WxqT4hhV6cKDjOAuPE7VJkdmyzZrrqSSWNY4yI0Hrv60nKY1GKF5jbWowirJJyJPIelUDiCrt7NE3nuDXSWEj79spzVf6NfpMn31tHRW7MZeS/qhpuJWRUf9JJnr3hiqW4jHq/V26oPPc1SeHSj68Z+ND7FKu50f1U1HH7Jcsvo64uRMdo1X0FL8zjlWvCIQoMqLq8c5qH7Nj+yU+HdoWRLZIHib3bM8WUjDK4I8RU+wy56U+M4wFIHkDU6X+y3yo8kivFEzzadnjWwGo4GTzNWCyGOYzUXxZZITg4Eq9PX+xpoZxRrYtEStYpPsVIU5wY2+VUiSTHvCoLSkY1VWlk610NqMHHdX1NGskYOGlwfLNZ2h+ZIqdDeVJ417GsrXRpCdc4Ey/wBRFXjtgpPZ5A/erHCN5fOjAYf6gHxqHiXRazPtGr2wQAyiNc8u/wDlQNeQ7gOB5hTWZp8XFEETq1Lwx7H55dGl7XDjPtK7fuHP4VK38P1pn/oFZvZw53YmpItwdgT8aPFEazSNgFJY9aTxuB0bCmoVA/uuh8lYf3rGLKPd5UQeo8Hplr5H4botJR73d9RVi2+kZaWPHjmsJXJxkk1bHIVbIz8qxlgl7N4/IXo9BBJCp/aoceBr0MfGLGCJIwX2HRa8XHxCUDYqPRQKcg4xOjA91h4FdjXOsE4S1IMmnKlZ6uLjdnI2lmaPzYbUpxi4trmNGhcMQSCcYGKxJeKySYIghBHUJSFzPNKcyFj5U9E5qpERxQhLUhqQKHA1ovqwFSyF1iAZNhjdxWVh2buoxPhirir6YRob3fA+NdEcNLkJ5t90bFtoSXDSLnI93eta7ulg+jdzMCe5DKQcddJ/M156zDJPpIxhsYNMfSW5MP0USIN3rmXRgfZzqP3L99bRg1uzizZFJpHzC/Obll6IoX5ClF94+gpi4JNzITzLGlojnV6gV0LgxfJZQn31+NFQkanAyRgZpiJj931J/Gi5UMf7NfSpbfC+P4UxHLyz470P13+H4UdAPff0FAEr7o9K5/cNcvuj0rmIyoyNzSGC3N/gKd4ecXsY+0dPzGKRG7Hzf8KZt30TxufqsD99J7oceT7V9H37bhNnIN82yfcMVPF5NMMWQMH+9ZP0VnZuExIHwIhLHz5YOR9xFafEpIzZQl5FPPrnrSrazlc/8lGFO2qWRQVGX5H1NJy9xiO6cHoKameDXJIrqdMwBxnO5NZ11dQdvKWMhYMc7edZuDPQhkXsLtgPqr/TUGbyX+kVQLy0G+mQnzoJeIayD4csjNTob6NvIvYwZvJfkK4SSH3UJ9EpReITKcp08FFT+krxwQpyfHHKn45eheWPsbHtBGQhA8cAVDG5HMN8MVmzT3MhHasxxyzQa3G+rB9KpYmQ8qNNpQAQBIp82qoySZyJmpEXFwBgO+PWpEk7DfXjyFNY6E8tjpklP+ufvoGll6zNS4kYD9m5PnRLc6SD2IyPHenpDX+kmRv9wmo7U+NA1yxcsVGT5UJuW2wijHlVafwhy/RlQxXUzBR0ND7SV5SZ+NKSSySHLEmgGo8gaej2J5K4H/ayeZPzrjdkDIZs9N6TCSeH31D61UEjrR44h5Zh3U3aJESc/rwM+QBFWJdHHXxpaVVEcCnJ/WjPyNWhY9tLMNyN6SjFA5yZV20H+8tcbqBeUhPoKzI2Eg7pG4zzq2CL2lm0PHEo5dq+nNaNpbszinJ0hma+DRMqqxyMbkVKXhMQYx5HI770jdaY3ETOrkDmmSPnRLayxGNxKZQw1aHOBjOPnQqfAO1yPxXcbE9oSu+3kKZSS2blMoPmcVi9jKxw6qozz1k560Tq6MQwDYODpOcUqQ03Rt5t84E8Z/mFdiPGQyn0YVjCJzyRj8KkQyf7bfKjT+i1fhsBl+qAfjU5P2PurIEE2dom+VGIrocllHpRpGpM1QQP9PNFq/cFZ6NfKNkc+q5qf+vbcrJ8BipcS1I0AT0WjVjz0ms5IOIHdYpzjc4qwR8S0j9XPg9dNS0WpM0QzH6pq1XbHums1LXifNbe5PjsTVot+K5x7Nc5/wDx1m0jVTaHe2foCKn2iXkM1nzNe2zBbhJIiRkB1xmlpL+dJVTQ5zzOnYf3o0Jg8rNuOeZ5VydvgKZ/WFYwTjAG3jXmkvZiQDqAG+yk07DJczadBkchQTgcqtYjCeU9TBbg3TkkkCTfp1rF+mcxS0sIs7Krv83UfgDV/DkuGusydoMtklqyfpqGjaJXIGLcAb9dZJ2+Iq5QqJxxyasqR5FjqYk9TmqYvr/x1ZqXlqGfWhTbX/GfwFBuFQ5wzHwX+9TQOAdZ6jYfKgCxRhAPACuG7E/AVzHA258hUqAABTEdQD9o3oKOhH7RvQUAcu6D0qAB2gwAMDwrk9welSu7sfPFIAI9wp/eNWiqY/dXyY/nV2QOZxQM+g/Qa4L+0RndWtxJ6HdT+XyrSvADbpn/AHW646r1rD/8Pri3j9oE08au6CNFZgC2dR28elbXF4mFrEFRnw5JwCd+7V41tR5/yf8AVsxp4syy7+7L4/vGlHhCtJl1x6+dBeQzHtWVXOZdxjGNzWbJDLmTKN5ZHnVOJ0457cGgwiXnIo+IqsvDnHbJWd2Mo/02+Vd2cvVG+VLSbavw1E0N7sqn0NFo8z8qyDHJ9hvlRh51HN/jS0hqNPRn69dp0/6oFZWqVwN3IPLGaqmLxLkxO2QeSk486NI9RsF8EAXC8/HlXNMqjvXKfA1hG4Clw0Ryq78+ePvq2ImWMOqtg+IxQo2LWaTXcQ5zM3oDVRvE+qrn1OKU0N4GuwfCq0oTkxk38mdlA9TQ+3zHoo+FUouuZEJ5sAB1wTVPbIS2nJwcbCikK2Oe3T55r/TXG+nIxlR6LS0JLZMsTqM4GGGfjV2LXIBeQHqNtvupbDtk+1z/AO59wqGvJSQCwO/hQT9gkgETSFcZJYAY2peS4hWTBfUNOxGNjRsLcbkuXdAzD3GU7EjrirFu104MZG2NjWeLmN45I0Yam0qMjzG9VFZYiRLcMUHVB/eltY7ZW/Ydo0jOz5I2HKnIuLPbwaIgVQ5AzzFJ+zTDnM2PQ1At35ds3yp6WLUWz3skzBnJOBtt/wA86uPE5hbLFlWVsPnsgWB3HPn05Uv7L3d2kJ8asW0RgodJDpGBg0tLDUEL2YuF5ZOM9ntRre7L2s7rqGQEiB2yR4+VC1pbsBpt5FPqcfeaYjhZEVVjXujAJIyN8+PnRTHaQD38+hUjgmKLnBI3O+c0R4hcEjTZzjfPKry8igB4yPUqKIys2O8q+rgfnTUQbspW+u9WfYpsYG2BXGe8kKDsJlzkMTgdQfwq9XkJwrof/d/zR9pL9iFv/dP96KGmOLJknfrzOwq1Xcd79XjxV1/vWfmZt+wiA/dk/vRGSQgZjgXoCxGaKKUzRW7Ck4PTfSM//wCquj4kRj31H8AH45pOGK51BBLZherHDAfIVcovC4T2q07PGdagafwzWbjE0U5GxHeyNCjdrFGv77DPxwKePEO4qTwJOjcjHIcD45rJsQ0lwhSeNjj9YqR6kPrjBFPQvMsQMVqyBX5I0rD5FsVzSgr4NlIsvOHWHEoO0vHhk7IZWLti5TPgAaxbrg/BkgygkVs/6TMo+/NeiS4kMb9qsRdhgPJbSH/+Ofzrz95NdRawZolGdl9mYZ+R2pYoyuhSkqMr9H2hc6ZrpV6frlJ/Cnbfh9qmC0tweQI7bGarW5nZwZJZB0wNa0YeIKNc0ikgEZ3/ABrujE4skjetioRo7fKkIezJYHDY2PPxr55eXMyyDS2ksMnA7x8yTua9twuaNZ5GW4D6U1YK+deR4zbSwSBJbWdXDMFfRhWGcjB6jG9TkXBlgktUkZZy3v758d6BAAGxt3jVpinAyYsDzP8AiqlzoyRgkk4qToJoT7p82/OjqsD3D1JzQIs5t6VNQMAbmo1Z90Z8+lMCWYKN/gKFc9o2fAVIXByTk+NQP2regoAlPcX0rkGAM8+tQnuD0ohzpDByrb+4AAD5nxoozpOpRpPiRvUFRG2+AGVWGfSpDZ91WbzA2oA07a6luNetFEaISepJ6V7u8QxcNggcs7x4RidySAudq8ZwexvL60dbSylcKcM+Vxn517Hi7k2xbJIMzFcnO2RVYY02cfzJp6VZh3ugSyjPKQ+8DtuaQdhnbHwA/tWzBw4XNrfXEs2lYw7IidSCfurzkkoHU1vQYpp7Iu1+XyxQGXfTzOcY28M0v2vn91VyxQz/ALQk755mpafRumM+0rkYkByM8xtz2+6q5Z5s/qniZSBs2xGaVNjaddXwaq2srUHYn4tmlTC0O2108WzxxyBUIA7U77YxsKW7QR6uygSEaTtrJJ3qr2O2HJiNsetQbO2xybPkaWlj1IOV3jklCKCcgsQx6geVEs8qSJrLEEZKqc45Yqg2kGSSz/MVBs4iO6zf1UUwtGj2yqqkl+9uMKTtQTSsr5iOpT+5ypN+1jiSOEFgueRqotd/Yf8A58aNw2HZZ5kKuCS422QYAz61Ot0hZFa3ORq09nzPrnnWcXuvB6Ey3Pi1IDUt5JOyy0Ucjjc4bl4fnQSKrM2q3XWcdScffSdpPPHOC4do27rjHQ/8zWhLEGbDZ22ypxUSbTNIpNFcHYxTF9KhkI2JIAOPA864smTho8HnmqpoNOCrPj+I1Vo/ef8AqNCkJoiSAIVaCVdQOfeAxRPJdSE9oY2BGG7w3odH7z/1Go7NfF/6jTsKHRMn1rqD4tU+0Rf79v8AFq4QSDom2/IA10mhGhJQth9wHG+xrTcjYj2iAc7i3+GaNpwiI5nRkfOkhCQcc6sWWM84JAPJj/alrdkE1yF1KnaZACF+lLcNgzeRtzl+UJNCJrU79pKfSA1assQfJL//AKcVXDJH2YBTV3m5xg9T5ijcNgo5oNeyzsPK3Ofxo/akLYRJ/QwCoyp2WKUD0Vfzq+LYgpZFj5jVn76NxgJMJFJFvcuASCVULv8AOpkdEQs9teaQNyWX+9DZ3EyJKESUKZXyqqcc+WBUXc6GBlNuEJxuyMDzHjS3HsF+rJAFncg/vFB+dWAQAKv6PmMjHAImjO/Plmia7jXuvFaFfArv+VVPLC81vojhUdpvpyOh570NMVotKLboJpeHziEbsyujYHUkKc02RA05aC4hVVwVwsr5GM5xv99I3stmltIDE7MUYAoz4BxT3D3szbRrIOISzBFAS3lxvgchpNS27LVGrFxi8jcJEvD5tubWmk/fg09FdXAhke94Zb4cYVltCv3gEUlgyvBnhfE3jQ4CTvgk+R0g1vwR3Mtk6Q8OuIyeSTRLInxzprKf9G0TEmFvcMjJw2wgfOTLLMQD6jauuoYW1Ty+y20iDuCzhDavPIxRJf8AF7K4bTZxqgJ2js8D7s5+dd2jcQWSWTXaEZ1SGTRqPgATz8hU7p/g9mZF7JK+M3pmPg0cuR86XjecEFkDAeCEfjTE7kMVF5cPjpJGrAfM0u04zhrgL/DCFrrjwceSja4dM7PKphZh2WfcFOfShCtlaNgDYjA/hNY9jKoac9q8n6o7BR5eVav0j0/oy20LjLeP7hoy/wAGcWP/AHETy0jFY3YbkA4rP4rF2HEZYcY7NtHyGPyrZsoPaOIW0OM65lyPId4/hWPxkl+L3THYmZ+X8Rrnitj0pP7UJk4BPhQr7yAdFNTpJ2LHHoKhQBIceAqhBkdcDPmK7LeGfjU11MAdXirfLNQpzK3PkOlFQj9q3oKAOT3BRdaFc6RvXY8STSGblikctm0b5JaIAbLgZXHX0oIIohGrhASQDk71fwSeSJCsblcoM4CnkT4+tDpKNIh+q7D7/wDNEv4oUH92j3H0MhieymEijZzg/wAiiluNnRE6I2UEzY0jzFXfRdUWzdix1GRwF14+qvSlONFDBISsjEu2Tvt3hW2JfU8jPL/M/wCxK34pa2tldwTMQ8iOikxnmSfLzrzUoAOxZvSm7nQJCN0GtuQOOdJSEDk4O/g1aM6cUVHddgnB5xP8SKgLGTuAvwoeexwPUMKFox9hz5jJpG5YUAOzD4Go6d6Qj+XNAFIGzMP5a7sydxv6qBSANjFHCZWnJOoKqKgJY/lQtIgTLwTgc86V/vQSBkEBEZJ7RthjPu1ErERPmKQZU75BqSgyV/2rof8Atj+9CWRdyt0B/B/mhaQ52gYetC8hMZBiK+eKdAWF4+q3PxiqGaEFQ3bAsMgGLpyosN1Kj1fH50DlRdoHdV/U7HV+8etJjI1RfbkHrCa4yW+M9sw9YTVhVSpKzE7HYPVcbfq1GoN3R7wJ6UUxWQJYP94/0MPyqpltWJPbsCfAEflV+4Rzpj9xtwCDyNEN1GYs7DqaKHZRHbJMWEU7NpUs3eIAA5k5FVdnAeV3j4/4q+5IWzmCoV1BQd/3hRdmcAYXA5AVNbjsVMSdLxPi1d2P/wB5H/XTDRgKxK/VPh4UCpDpGQnLqRT0isY7oGlooQP3YBn5mgfQssXZxHOo9AM7HwoMznkwPpEfzNQY53IJLbHIwgFPYe5eSGPetkPrVNvhZZ8Lga+QGcbVIhuf91x8qCGBn7UFiSJNznGdqTaBJjQOeSqP4kFV25l7IadGNTdF8TQiy3ycfFjVgsYz0Qfyk/nQ5IFFlmuRBmSRQPDIokuolOHlXH8WPzoBZQp7zKPSMVPZQZx2znyUAfgKWtD0Mqtp4VV9bR5MjEagTtmrLi5ge3ZFIZiB7qsevnRWkUKxHte2Da2xh8bZq5jaDmT/ADPmlrRWhi7T2wfKLLjwMVS0iu8RjgmwrZbkNsEbb1aLi0T/AFIxjxOah76yzluzY+OgmjWLQRNNE9vKBb3GdDYLSKOnhmnLG5ihERaymmOhd1kYdBywRVAe3ubd1hCMWUrsMAEim4YiII1cp3VA5jfAqXNXuWoPo0I+LvFbskM81mrHJjEcj/DdiMU1Z8TsZZ1FxG87EbDLQDPmdWPurJEMB2KpnyIFWraWxUns1P8AN/mocoGijP8ADev+I28FkFsbO31g7vcTRyfg+a85Ncu+SYbVc7d0g/i21PWi9iCsIRR4hEY/M5q1beNpQ8sccrDlqRfwAFTCcYhKMmZbXEa24i9jtNQ5uWJY/fihjlg0b2wD/wAW1eiEAl5oFU9I0CAemKKPg1pIdTW7OfFpDW8ZpnNkTQlZpC0czKtrE3ZEE9pz5dOdOfSY5sLTSUIGQdC4GdO351qwcIhSTu2Yw6kEhs7Uj9Nezjs7NIydyxI9AAPxNXka0M8/Fb+TEwOAhTxqDXjAVyM9Tp/715riBzfzkdZG/wD7GteKZ7eVJ4yQ8TB1I8t6b+mXBbGyjju7ISRmfW+nWSvIEbHlzrGP8T0Zusn9nlaokmMcrAKDsKHtph0B+FUs7M5ZuZp0Nsv9qb7C/Oo9qkJwFWqtfitcHGvJpiLfaJSM7f00dvI0kj68ZAHSqVYY5ipUurEptnyoaHY0vuiiHOle0l8fuFSpkdgpc7nFKh2b/ChG8hV30d04wM9RV5CmaTQ2pS+zYxnkP71sWHCbaH6HT36JhwX3DHnq0j8axwAoCrsBsKUn9UhYvtNv1se0+jTKLGRWXnK+DpyT3RVkhtpbeO3ZSHLyBTgA4xvzq/6GxJccEbUCSJmHzAP51sfo+GPQVUMVydx41tjaUUeN8nX5ZbHy++Vo5DFJJIpR27rLnBzvWdJpG4mz/LX0Piv0etGbtnhGZCTgsc+ZO9Y0vALMHaBeef2h/vVOSOvC7iePaRujMR8qrzIeQb516uThNggJaDT5h8/nVD8Psl3WJWH8RH51OtHSkecV5eWdPqwqWMp/1l+DCtxrOyJylsgPmxH50D2sB5RIp8NX9zRrQ9LMKVyFhbPaHtDsCPs0LMGjYBJASCMYH961bi1/WQlEQojsWGR1GKWmm4eG0yJHq8gfyqdRWlibSKOcki+sZoHmBjYCYHPTRinA3DG+si/EiiEFg3uSj4PT1BpFO2TrcIfVagtGbhCWGDERnOPrGnTZWp3DN8HBpeS3i9sSMk6RCTvj7Rocg0lbJCVbSycj4GuiYiJRgMMDk2KtNqn1WHxVTQG1B+rCfVMU9SFpYLMCj/qyO42+ryNdqUAZSXl9uuNp/wDTjPoSKg2rj3UZfSQ0WFA3DBrSXGrpzbPUVHZwdIvvFdNFIlrKXLkYHvEHqKgrNganJ/kFK9wa2JMCaGIQjun8KgKNI75Gw8f7UOnGf1pGRj3P81OtwABJH8UpiLDfx/vGo9uB92JjTqQR52UCrOyQAd2sjXczvaZm923NTGl5likB7xyc1pHujYAVVHI7MRqK+lAC623En5IF+NF7DxE83+VNFm6ux9TVyltPvsB4ClYzP/Rl5jvO3wxUjhc52aR/TVTzMVAPvZ8d6KF2kJBIx5ClbHSERwg/WJPq1EOFIvvJn0OadfCNgAfEVdDGHALM3oDilbHpQivDVH/pm+4VPshTYWZ+JG/301cOIVOmNDv9bJpW8naIroCDb7NKx0kEIHwW7BVUelWKsYXvRB/IJ+ZpL2iVx3mzQCVhyA+VFBY/2ckhASzRQDsAoB+JJpiOCfP/AMOhJHkB+dIwMXPex8qaUADkNj4Ui0i0w3Cne0B9NP5VbGl0TgWR/pFKMxxyHyotRAByTnzpomRoxRXRIHsbE+S5puKG7/8Al5V9M1iLIyyd04puG7mUko5XrgVqjmmrNeOO8UghJvLY1mcflmE8KTxtGOz7pYYzlt/yqz2iXSrFtR55NZvGbiSVrcuckah94NObuJnijpyJlbwSi09pOOyZmiAwc5C5znl5Y8q0/pXL2nB7AdjJHi3bdwcN3E5Uq8Yk+jUzMzfqrsaRq23xnb4mlOITSS8KgDsSFgfHlsoqOEzZq5pvqzzNQdtzU12BnNWAJJPIfE05wK3S44/aQyIZVMgLJjOoDfGPhStaX0VZk+kUciHDKjkH4UyZcMT4vbi241dwInZhJWAU9BnlSyjO2MGtH6SO0n0lu3c5ZmBJ8e6KQNIa4JAPiaOL9qn8Q/GhHKji/bJ/EPxoY0e1juHX6JSx6G0mbTq3x+0FZjhojqkJw66125DJHx5ffUzTP+hZIs90TMw9eddfTNJKFIAWKKONFGcAZJqJboMf1k0vbPUfRbiM9tw6dUikKdrsw5E6QD+VNz8TlZtkceua87YTtHw6FVVCNAO653O5/GjaQk5IGfStY7KjinBSm5GlLfOzYZJD02U0tJNGRvbz8+ucfhSM0zIAy4znwo45pHQZbn0pspRosZkPK3m+BJ/KlZC5J7O1l8zparm06zlFNVPIY1BQAZqTRCxlI2lsifMRkGoL2pXLQFP4gVq1rmVgctQCaR9mY4I5UjQExx8/ZnA8d6EwxP8A+nlJ8gTQPmM5jdl8gdvlR2szzI+o4KjYqSKCip7WDHftpx/J/il3s7M/6Ug+FMvczqQBK3zrva58byE+u9IBBrG26LKvoarazQbiSUVpiZmwcKD/AAiuVi2eQ9BigoyvZnAys7j1So0XA92YN/LWjNLJGRpc/Gq+1J3KrnxxQAni6/cb5ip/6sc4QfRqcSR2YAnY+VWMmEJDMN/GgDLmkmeJo3hYBsZOa4XjjnGRT4dycFyR51Dk9cH4CgLEhfL1Si9rhPNAfhVxVTzUVHZRsN1HyphZ/9k="

private const val PREVIEW_THUMB_2_URI = "data:image/jpeg;base64,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"
