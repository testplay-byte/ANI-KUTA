package com.confused.anikuta.settings

import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.confused.anikuta.core.designsystem.component.CollapsingHeader
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences
import org.koin.compose.koinInject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * ══════════════════════════════════════════════════════════════════════════
 *  ROUND 102 (WS-G): PlayerEpisodeListSettingsScreen — the PLAYER page's
 *  episode-list customization, as a DEDICATED SETTINGS PAGE
 * ══════════════════════════════════════════════════════════════════════════
 *
 *  The user's order: "Just like how there is the option to customize the
 *  episodes list of the details page… there should be a proper,
 *  well-formatted page, exactly the same style and overall experience for
 *  the episodes player page." The round-101 in-player sheets (the gear on
 *  the Episodes header) are retired — this page (Settings → Appearance →
 *  "Player episode list") is the customization's new home, and the SAME
 *  [PlayerEpisodeListPreferences] keys drive BOTH player stacks (the MPV
 *  page + the CS page) and this preview.
 *
 *  # The pattern (the details page's EpisodeListSettingsScreen, player-scoped)
 *
 *  The same shape: a **live preview** at the top (two rows — slot 1 styled
 *  as the CURRENTLY PLAYING episode, slot 2 as a watched one carrying the
 *  dim treatment) and the options in a LazyColumn below (the D-525 single
 *  8dp gutter). The preview is NOT a mock-up of a style: it renders the
 *  SAME row anatomy the player draws (thumbnail + EP tag + title surface +
 *  audio pill + synopsis + date pill), fed from the SAME preference keys
 *  this page writes — what you tune is exactly what the player shows
 *  (the D-481 one-source-of-truth rule). Every flip re-shapes the preview
 *  above AND both player pages, live.
 *
 *  # The collapse
 *
 *  The preview slides up under a clip as the options scroll (the details
 *  page's D-556 reading: "the first episode list is hidden, and the second
 *  one will remain"); scrolling back restores it. The two-phase snap of the
 *  details page is deliberately NOT replicated here — the player page's
 *  options list is short (four cards), so the simple scroll-linked slide is
 *  the proportionate experience.
 *
 *  # The option set (everything the player list respects)
 *
 *  - **Row style** — Detailed / Compact / Minimal (COMPACT: the smaller
 *    thumbnail, never a synopsis; MINIMAL: number + title only).
 *  - **Elements** — the synopsis + date-pill toggles + dim-watched.
 *  - **Watched filter** — Off / Show watched / Hide watched (the three-state
 *    the player list filters by).
 *  - **Sort** — Episode / Upload date / Alphabetical, ascending or
 *    descending.
 */
@Composable
fun PlayerEpisodeListSettingsScreen(
    onBack: () -> Unit,
    playerListPrefs: PlayerEpisodeListPreferences = koinInject(),
) {
    // ── The reactive reads — the SAME prefs both player pages collect. Every
    // write below updates the pref; `changes` re-emits; the preview AND the
    // player lists re-shape from the same emission (no local mirror state,
    // the D-481 drift killer).
    val rowStyle by playerListPrefs.rowStyle.changes.collectAsState(
        initial = playerListPrefs.rowStyle.get(),
    )
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
    val sortMode by playerListPrefs.sortMode.changes.collectAsState(
        initial = playerListPrefs.sortMode.get(),
    )
    val sortDescending by playerListPrefs.sortDescending.changes.collectAsState(
        initial = playerListPrefs.sortDescending.get(),
    )

    val lazyListState = rememberLazyListState()
    val collapsed = lazyListState.firstVisibleItemScrollOffset > 20 ||
        lazyListState.firstVisibleItemIndex > 0

    // ── The collapse: the FIRST preview row slides up under a clip as the
    // options scroll; the second row stays pinned (the D-556 reading). The
    // slide is a simple linear map of the scroll offset — proportionate for
    // a four-card options list. ──
    var firstRowHeightPx by remember { mutableIntStateOf(0) }
    val hidePx by remember {
        derivedStateOf {
            if (lazyListState.firstVisibleItemIndex > 0) {
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

            // ── THE LIVE PREVIEW — the two slots. The clip+shift layout
            // collapses the first row as the options scroll; animateContentSize
            // makes style switches glide (the details page's language). ──
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
                                .animateContentSize()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Box(
                                modifier = Modifier.onSizeChanged { size ->
                                    firstRowHeightPx = size.height
                                },
                            ) {
                                PlayerPreviewRow(
                                    slot = PlayerPreviewSlot.CURRENT,
                                    rowStyle = rowStyle,
                                    showSynopsis = showSynopsis,
                                    showDatePill = showDatePill,
                                    dimWatched = dimWatched,
                                )
                            }
                            PlayerPreviewRow(
                                slot = PlayerPreviewSlot.WATCHED,
                                rowStyle = rowStyle,
                                showSynopsis = showSynopsis,
                                showDatePill = showDatePill,
                                dimWatched = dimWatched,
                            )
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
                    // ── Row style ──
                    item {
                        PlayerListCard(label = "Row style") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Row style",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(modifier = Modifier.padding(top = 10.dp)) {
                                    SegmentedToggle(
                                        options = listOf("Detailed", "Compact", "Minimal"),
                                        selectedIndex = when (rowStyle) {
                                            "COMPACT" -> 1
                                            "MINIMAL" -> 2
                                            else -> 0
                                        },
                                        onSelect = { idx ->
                                            playerListPrefs.rowStyle.set(
                                                when (idx) {
                                                    1 -> "COMPACT"
                                                    2 -> "MINIMAL"
                                                    else -> "DETAILED"
                                                },
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

                    // ── Sort ──
                    item {
                        PlayerListCard(label = "Sort") {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    text = "Sort by",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                                Box(modifier = Modifier.padding(top = 10.dp)) {
                                    SegmentedToggle(
                                        options = listOf("Episode", "Upload date", "Alphabetical"),
                                        selectedIndex = when (sortMode) {
                                            "UPLOAD_DATE" -> 1
                                            "ALPHABETICAL" -> 2
                                            else -> 0
                                        },
                                        onSelect = { idx ->
                                            playerListPrefs.sortMode.set(
                                                when (idx) {
                                                    1 -> "UPLOAD_DATE"
                                                    2 -> "ALPHABETICAL"
                                                    else -> "EPISODE"
                                                },
                                            )
                                        },
                                    )
                                }
                                Text(
                                    text = "Direction",
                                    fontFamily = RobotoFamily,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                                )
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

// ══════════════════════════════════════════════════════════════════════════
//  The preview slots — the player row's anatomy, self-contained
// ══════════════════════════════════════════════════════════════════════════

/** The two preview slots: the currently-playing look + the watched look. */
private enum class PlayerPreviewSlot { CURRENT, WATCHED }

/**
 * One preview row — the SAME anatomy the player's EpisodeListRow draws
 * (thumbnail + EP tag + title surface + audio pill + synopsis + date pill),
 * duplicated here as a private (the player's is file-private; sharing would
 * widen its visibility for no reuse value beyond these two surfaces — the
 * poster page's established reasoning). Offline demo content; the style
 * inputs are the LIVE preference values, so the preview IS what the player
 * will draw.
 */
@Composable
private fun PlayerPreviewRow(
    slot: PlayerPreviewSlot,
    rowStyle: String,
    showSynopsis: Boolean,
    showDatePill: Boolean,
    dimWatched: Boolean,
) {
    val isCurrent = slot == PlayerPreviewSlot.CURRENT
    val isWatched = slot == PlayerPreviewSlot.WATCHED
    val compact = rowStyle == "COMPACT"
    val minimal = rowStyle == "MINIMAL"

    // The watched dim — alpha toward the background; the current-episode
    // highlight always wins (the player's exact rule).
    val rowAlpha = if (isWatched && dimWatched && !isCurrent) 0.5f else 1f

    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary,
        ) else null,
        modifier = Modifier
            .fillMaxWidth()
            .alpha(rowAlpha),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                // ── Thumbnail with the EP tag (MINIMAL drops it) ──
                if (!minimal) {
                    Box(
                        modifier = if (compact) Modifier.size(width = 84.dp, height = 48.dp)
                        else Modifier.size(width = 120.dp, height = 68.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                // A quiet placeholder art stand-in: the number
                                // disc reads clearly at any style.
                                Text(
                                    text = if (isCurrent) "▶" else "✓",
                                    fontSize = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        ) {
                            Text(
                                text = if (isCurrent) "EP 5" else "EP 4",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                } else {
                    Surface(
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.size(width = 44.dp, height = 32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isCurrent) "5" else "4",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isCurrent) MaterialTheme.colorScheme.onPrimary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }

                // ── Right column: title + pills ──
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (isCurrent) "The Journey Begins" else "Whispers of the Past",
                            fontFamily = RobotoFamily,
                            fontSize = if (compact || minimal) 13.sp else 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Row(
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (!minimal) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f),
                            ) {
                                Text(
                                    text = "SUB",
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (!minimal && showDatePill) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            ) {
                                Text(
                                    text = if (isCurrent) "Oct 12, 2025" else "Oct 5, 2025",
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                }
            }

            // ── Synopsis (DETAILED only, toggle-gated) ──
            if (!compact && !minimal && showSynopsis) {
                Text(
                    text = "A chance meeting sets the story in motion — the first steps " +
                        "of a journey neither of them expected.",
                    fontFamily = RobotoFamily,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
                )
            }
        }
    }
}

// ══════════════════════════════════════════════════════════════════════════
//  The local card + switch shapes (the details settings screen's privates,
//  duplicated per the poster page's established reasoning)
// ══════════════════════════════════════════════════════════════════════════

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
