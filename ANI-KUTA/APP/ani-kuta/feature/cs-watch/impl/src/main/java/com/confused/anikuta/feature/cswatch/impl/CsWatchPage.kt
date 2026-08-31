package com.confused.anikuta.feature.cswatch.impl

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.preferences.EpisodeListPreferences
import com.confused.anikuta.feature.cswatch.api.CsSimpleEpisode
import com.confused.anikuta.feature.cswatch.api.CsSubDubSiblings
import com.confused.anikuta.feature.cswatch.api.CsWatchEpisodeMeta
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Task 54 (round 14) — the CloudStream watch PAGE (minimized mode).
 *
 * The layout mirrors the aniyomi watch page's MinimizedMode EXACTLY:
 * ```
 * ┌─────────────────────────────────┐
 * │  ┌───────────────────────────┐  │  ← Floating pill top bar
 * │  │ ◁ Back  ANI-KUTA          │  │     (collapses on scroll)
 * │  └───────────────────────────┘  │
 * │  ┌───────────────────────────┐  │  ← Player 16:9 (rounded corners)
 * │  │      PlayerView           │  │     + CsMinimizedControls
 * │  │   [resolving/error state] │  │     + phase overlays INSIDE the box
 * │  └───────────────────────────┘  │
 * │  Currently playing episode N   │  ← description section (scrollable)
 * │  Episode title                  │
 * │  [provider] [quality] pills     │
 * │  Synopsis… (show more)          │
 * │  Episodes (12)                  │  ← episode list (lazy rows)
 * │  ┌──┐ EP 1  Title              │
 * │  └──┘                           │
 * └─────────────────────────────────┘
 * ```
 * RESOLVING / FAILED / NO_LINKS render INSIDE the 16:9 player box — the page
 * content below stays visible (the "watch page shows properly" requirement:
 * description + episodes are always reachable even mid-resolution).
 */
@Composable
internal fun CsWatchPage(
    uiState: CsWatchViewModel.CsWatchUiState,
    playerContent: @Composable () -> Unit,
    onBack: () -> Unit,
    onEpisodeSwitch: (CsSimpleEpisode) -> Unit,
    currentEpisodeData: String,
    ratingStore: com.confused.anikuta.core.ratings.RatingStore = koinInject(),
    mainId: String,
) {
    val listState = rememberLazyListState()

    // Task 55: the sub/dub display modes for the page's episode list (the
    // SAME pref as the details page + the episodes sheet — one setting).
    // COMBINED merges sibling rows; SEPARATE adds the Sub | Dub chip row.
    val episodeListPreferences = koinInject<EpisodeListPreferences>()
    val subDubMode = remember { episodeListPreferences.subDubMode.get() }
    val displayEpisodes = remember(uiState.episodes, subDubMode) {
        if (subDubMode == "COMBINED") CsSubDubSiblings.mergeSiblings(uiState.episodes) else uiState.episodes
    }
    val showSubDubSwitcher = subDubMode != "COMBINED" && CsSubDubSiblings.hasBothFlavors(displayEpisodes)
    var subDubFlavor by rememberSaveable { mutableStateOf("SUB") }
    val episodeRows = if (showSubDubSwitcher) {
        displayEpisodes.filter { CsSubDubSiblings.tagOf(it.name) == subDubFlavor }
    } else displayEpisodes

    // Per-episode rating (the aniyomi page's Phase-4 star bar — same store,
    // same keys, CS content rides it identically).
    val ratingScope = rememberCoroutineScope()
    var episodeRating by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(mainId, uiState.episodeNumber) {
        if (mainId.isNotBlank()) {
            val epKey = CsWatchViewModel.episodeKey(mainId, uiState.episodeNumber)
            episodeRating = runCatching { ratingStore.getEpisodeRating(mainId, epKey) }.getOrNull()
        }
    }

    // The pill bar collapses as soon as the content scrolls.
    val collapsed by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 ||
                listState.firstVisibleItemScrollOffset > 200
        }
    }

    // The player sits FLUSH BELOW the status bar (never behind it).
    val statusBarInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    // Header height: 48dp when expanded, 0dp when collapsed (animated).
    val headerHeight by animateDpAsState(
        targetValue = if (collapsed) 0.dp else 48.dp,
        animationSpec = tween(300, easing = FastOutSlowInEasing),
        label = "headerHeight",
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // ── Top bar area — collapses on scroll; the player slides up flush
        //    below the status bar (the aniyomi pattern, replicated). ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight + statusBarInset)
                .clipToBounds(),
        ) {
            if (headerHeight > 0.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                        .graphicsLayer {
                            alpha = if (headerHeight == 0.dp) 0f else 1f
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.4f),
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .clickable(onClick = onBack),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                            Text(
                                text = "ANI-KUTA",
                                fontFamily = RobotoFamily,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.weight(1f),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.size(40.dp))
                        }
                    }
                }
            }
        }

        // ── Player 16:9 (rounded corners) — the phase overlays render inside ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black),
            ) {
                playerContent()
            }
        }

        // ── Scrollable content: description + episode list ──
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "current") {
                    CsCurrentlyPlayingSection(
                        uiState = uiState,
                        episodeRating = episodeRating,
                        onRate = { stars ->
                            // Same blank-mainId guard as the read above — CS-only
                            // content with no mainId must not write ratings under
                            // the shared "" namespace.
                            if (mainId.isNotBlank()) {
                                val epKey = CsWatchViewModel.episodeKey(mainId, uiState.episodeNumber)
                                ratingScope.launch {
                                    if (stars <= 0) {
                                        ratingStore.deleteEpisodeRating(mainId, epKey)
                                    } else {
                                        ratingStore.setEpisodeRating(mainId, epKey, stars * 10)
                                    }
                                    episodeRating = if (stars <= 0) null else stars * 10
                                }
                            }
                        },
                    )
                }

                if (episodeRows.isNotEmpty()) {
                    item(key = "episodes-header") {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "Episodes",
                                    fontFamily = RobotoFamily,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                                Spacer(Modifier.width(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(50),
                                ) {
                                    Text(
                                        text = "${episodeRows.size}",
                                        fontFamily = RobotoFamily,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    // Task 55: the Sub/Dub switcher chips (SEPARATE mode, both
                    // flavors) — the SeasonSelectorRow chip language.
                    if (showSubDubSwitcher) {
                        item(key = "subdub-switcher") {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                            ) {
                                listOf("SUB" to "Sub", "DUB" to "Dub").forEach { (value, label) ->
                                    val isSelected = subDubFlavor == value
                                    Surface(
                                        color = if (isSelected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(50),
                                        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
                                        modifier = Modifier.clickable { subDubFlavor = value },
                                    ) {
                                        Text(
                                            text = label,
                                            fontFamily = RobotoFamily,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                                else MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                    // Lazy episode rows — virtualized (D-230 pattern).
                    items(episodeRows, key = { it.data }) { ep ->
                        val isCurrent = ep.data == currentEpisodeData
                        val epNum = ep.episodeNumber.toInt()
                        val meta = uiState.episodeMetadata[epNum]
                        CsEpisodeListRow(
                            episode = ep,
                            metadata = meta,
                            isCurrent = isCurrent,
                            onClick = {
                                if (!isCurrent) onEpisodeSwitch(ep)
                            },
                        )
                    }
                }
            }

            // ScrollBlurOverlay — the gradient where content meets the player
            // (shared design-system component, same as the aniyomi page).
            com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay(
                scrollOffset = {
                    if (listState.firstVisibleItemIndex > 0) Float.MAX_VALUE
                    else listState.firstVisibleItemScrollOffset.toFloat()
                },
                backgroundColor = MaterialTheme.colorScheme.background,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Currently-playing section (the aniyomi "Currently playing episode N" card)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsCurrentlyPlayingSection(
    uiState: CsWatchViewModel.CsWatchUiState,
    episodeRating: Int?,
    onRate: (Int) -> Unit,
) {
    val currentEpNum = uiState.episodeNumber.toInt()
    val currentMeta = uiState.episodeMetadata[currentEpNum]
    val currentDisplayTitle = currentMeta?.title
        ?: com.confused.anikuta.core.common.EpisodeTitleParser
            .getDisplayTitle(uiState.episodeTitle, uiState.episodeNumber)
    val currentDescription = currentMeta?.description

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Currently playing episode " +
                    com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(uiState.episodeNumber),
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = currentDisplayTitle,
                fontFamily = RobotoFamily,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // Per-episode star rating (below the episode title — aniyomi parity).
            Spacer(Modifier.height(6.dp))
            CsStarRatingBar(
                rating = episodeRating,
                onRate = onRate,
            )
            // Provider + quality + sub/dub pills
            val qualityLabel = uiState.currentLink?.qualityLabel
            val subDub = currentMeta?.scanlator?.takeIf { it.isNotBlank() }
            if (qualityLabel != null || subDub != null) {
                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (qualityLabel != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            Text(
                                text = qualityLabel,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                    if (subDub != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        ) {
                            Text(
                                text = subDub,
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
            // Synopsis with show more / show less
            if (!currentDescription.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                var expanded by remember(currentEpNum) { mutableStateOf(false) }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = currentDescription,
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            lineHeight = 15.sp,
                            fontWeight = FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = if (expanded) Int.MAX_VALUE else 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (currentDescription.length > 60) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (expanded) "Show less" else "Show more",
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { expanded = !expanded },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Episode row (the aniyomi EpisodeListRow design, CS-data driven)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun CsEpisodeListRow(
    episode: CsSimpleEpisode,
    metadata: CsWatchEpisodeMeta?,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val displayTitle = metadata?.title
        ?: com.confused.anikuta.core.common.EpisodeTitleParser
            .getDisplayTitle(episode.name, episode.episodeNumber)
    val epNumText = com.confused.anikuta.core.common.EpisodeTitleParser
        .formatEpisodeNumber(episode.episodeNumber)
    val thumbnailUrl = metadata?.thumbnailUrl
    val description = metadata?.description
    val dateText = if (metadata != null && metadata.airDateMillis > 0) {
        formatDate(metadata.airDateMillis)
    } else null
    val subDub = metadata?.scanlator?.takeIf { it.isNotBlank() }

    Surface(
        color = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                // ── Thumbnail with EP tag overlay ──
                if (thumbnailUrl != null) {
                    Box(
                        modifier = Modifier.size(width = 120.dp, height = 68.dp),
                    ) {
                        AsyncImage(
                            model = thumbnailUrl,
                            contentDescription = displayTitle,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
                        ) {
                            Text(
                                text = "EP $epNumText",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
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
                    // Ep-number box (the aniyomi fallback tile).
                    Surface(
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.size(width = 44.dp, height = 32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = epNumText,
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
                // ── Right column: title + date/sub-dub pills ──
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = displayTitle,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (dateText != null || subDub != null) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (dateText != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ) {
                                    Text(
                                        text = dateText,
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                            if (subDub != null) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                ) {
                                    Text(
                                        text = subDub,
                                        fontFamily = RobotoFamily,
                                        fontSize = 10.sp,
                                        lineHeight = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // ── Synopsis (2 lines) ──
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = description,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        fontWeight = FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** 10 clickable stars, each = 10 points (the aniyomi WatchStarRatingBar replica). */
@Composable
private fun CsStarRatingBar(
    rating: Int?,
    onRate: (Int) -> Unit,
) {
    val currentStars = rating?.let { (it / 10).coerceIn(0, 10) } ?: 0
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..10) {
            Icon(
                imageVector = if (i <= currentStars) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = "Rate $i stars",
                tint = if (i <= currentStars) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(20.dp)
                    .clickable {
                        if (i == currentStars) onRate(0) else onRate(i)
                    },
            )
        }
    }
}

/** "MMM d, yyyy" — the aniyomi page's date format. */
private fun formatDate(epochMillis: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.US)
    return sdf.format(Date(epochMillis))
}
