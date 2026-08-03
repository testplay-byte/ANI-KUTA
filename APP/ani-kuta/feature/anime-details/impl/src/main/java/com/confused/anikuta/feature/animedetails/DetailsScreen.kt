package com.confused.anikuta.feature.animedetails

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.designsystem.component.ScrollBlurOverlay
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import org.koin.compose.viewmodel.koinViewModel

/**
 * Details screen — complete UI overhaul matching the old project's design.
 *
 * Layout (one LazyColumn, per old project's DetailContent):
 * 1. DetailBanner — 360dp blurred cover + gradient + 3 action buttons (back,
 *    bookmark, three-dot menu) + cover thumbnail + title + meta row.
 * 2. GenresRow — horizontal scrollable chips.
 * 3. SynopsisSection — collapsible with "Show more/less".
 * 4. InfoSection — key/value table (format, status, season, episodes, score).
 *
 * The 3 top buttons match the old project exactly:
 * - Back (ArrowBack) — left, 40dp black-40%-alpha circle, 22dp white icon.
 * - Bookmark toggle (Bookmark/BookmarkBorder) — right, same styling.
 * - Three-dot menu (MoreHoriz) — right, same styling, opens dropdown.
 *
 * CORE_RULES §22: smooth animations.
 * CORE_RULES §23: reactive state from ViewModel.
 * DESIGN-LANGUAGE.md §2.1: collapsing header behavior (banner is the header).
 * DESIGN-LANGUAGE.md §2.2: scroll blur overlay at the top edge.
 */
@Composable
fun DetailsScreen(
    animeId: Int,
    onBack: () -> Unit,
    onNavigateToWatch: (videoUrl: String, animeTitle: String, quality: String, episodeUrl: String, episodeNumber: Float, episodeTitle: String, episodeListSerialized: String, videoHeaders: String, resolvedVideosKey: String) -> Unit = { _, _, _, _, _, _, _, _, _ -> },
    viewModel: DetailsViewModel = koinViewModel(),
) {
    BackHandler(enabled = true) { onBack() }

    LaunchedEffect(animeId) {
        viewModel.loadDetails(animeId)
    }

    val state by viewModel.state.collectAsState()
    val linkedSource by viewModel.linkedSource.collectAsState()
    val episodeState by viewModel.episodeState.collectAsState()
    val resolverState by viewModel.resolverState.collectAsState()
    val resolvedVideosKey by viewModel.resolvedVideosKey.collectAsState()
    val availableSources by viewModel.availableSources.collectAsState()
    val manualSearchState by viewModel.manualSearchState.collectAsState()

    var showMenu by remember { mutableStateOf(false) }
    var showManualSearch by remember { mutableStateOf(false) }
    var showResolverSheet by remember { mutableStateOf(false) }
    var currentEpisode by remember { mutableStateOf<eu.kanade.tachiyomi.animesource.model.SEpisode?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when (val s = state) {
            is DetailsState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp)
            }

            is DetailsState.Error -> ErrorState(s.message)

            is DetailsState.Success -> {
                val anime = s.anime
                val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = lazyListState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 90.dp),
                    ) {
                        // ── Banner ──
                        item {
                            DetailBanner(
                                anime = anime,
                                onBack = onBack,
                                saved = false,
                                onToggleSave = {},
                                onMore = { showMenu = true },
                                showMenu = showMenu,
                                onDismissMenu = { showMenu = false },
                            )
                        }

                        // ── Genres ──
                        anime.genres?.takeIf { it.isNotEmpty() }?.let { genres ->
                            item { GenresRow(genres) }
                        }

                        // ── Synopsis ──
                        anime.description?.let { desc ->
                            item { SynopsisSection(desc) }
                        }

                        // ── Episodes section ──
                        item {
                            EpisodesSection(
                                linkedSource = linkedSource,
                                episodeState = episodeState,
                                onOpenSourcePicker = { showManualSearch = true },
                                onUnlinkSource = { viewModel.unlinkSource() },
                                onEpisodeClick = { episode ->
                                    currentEpisode = episode
                                    viewModel.resolveEpisode(episode)
                                    showResolverSheet = true
                                },
                            )
                        }

                        // ── Info ──
                        item {
                            Spacer(Modifier.height(16.dp))
                            InfoSection(anime)
                        }
                    }

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

    // ── Manual search sheet (source selection) ──
    if (showManualSearch) {
        ManualSearchSheet(
            availableSources = availableSources,
            manualSearchState = manualSearchState,
            initialQuery = (state as? DetailsState.Success)?.anime?.displayName ?: "",
            onSearch = { source, query -> viewModel.searchSource(source, query) },
            onLink = { source, sAnime ->
                viewModel.linkSource(source, sAnime)
                showManualSearch = false
                viewModel.clearManualSearch()
            },
            onDismiss = {
                showManualSearch = false
                viewModel.clearManualSearch()
            },
        )
    }

    // ── Resolver sheet (video list) ──
    if (showResolverSheet) {
        ResolverSheet(
            resolverState = resolverState,
            onPickVideo = { video ->
                val anime = (state as? DetailsState.Success)?.anime
                val linked = linkedSource
                val ep = currentEpisode
                if (anime != null && linked != null && ep != null) {
                    // Serialize the episode list for the watch screen.
                    val epListStr = (episodeState as? EpisodeState.Loaded)?.episodes?.joinToString("\n") { e ->
                        "${e.url}|${e.episode_number}|${e.name}"
                    } ?: ""
                    onNavigateToWatch(
                        video.url,
                        anime.displayName,
                        video.quality,
                        ep.url,
                        ep.episode_number,
                        ep.name,
                        epListStr,
                        video.headers,
                        resolvedVideosKey,
                    )
                }
                showResolverSheet = false
                viewModel.clearResolver()
            },
            onDismiss = {
                showResolverSheet = false
                viewModel.clearResolver()
            },
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Banner — 360dp blurred cover + gradient + 3 action buttons + cover/title
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DetailBanner(
    anime: AniListAnime,
    onBack: () -> Unit,
    saved: Boolean,
    onToggleSave: () -> Unit,
    onMore: () -> Unit,
    showMenu: Boolean,
    onDismissMenu: () -> Unit,
) {
    val coverUrl = anime.coverUrl
    val bannerUrl = anime.bannerImage ?: anime.coverUrl

    Box(modifier = Modifier.fillMaxWidth()) {
        // ── Background: blurred banner image + gradient ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp),
        ) {
            if (bannerUrl != null) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(8.dp),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            // Gradient overlay: black 20% → transparent → background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.2f),
                                Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            ),
                        ),
                    ),
            )
        }

        // ── Top action row: back (left) + bookmark + more (right) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                onClick = onBack,
            )
            Row {
                ActionButton(
                    icon = if (saved) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                    contentDescription = if (saved) "Remove from library" else "Add to library",
                    onClick = onToggleSave,
                )
                // Three-dot menu — DropdownMenu is anchored here (next to the button).
                Box {
                    ActionButton(
                        icon = Icons.Filled.MoreHoriz,
                        contentDescription = "More",
                        onClick = onMore,
                    )
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = onDismissMenu,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Refresh", fontFamily = RobotoFamily) },
                            onClick = onDismissMenu,
                        )
                        DropdownMenuItem(
                            text = { Text("Share", fontFamily = RobotoFamily) },
                            onClick = onDismissMenu,
                        )
                    }
                }
            }
        }

        // ── Bottom row: cover thumbnail + title + meta ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = anime.displayName,
                    modifier = Modifier
                        .size(width = 100.dp, height = 150.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = anime.displayName,
                    fontFamily = RobotoFamily,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Meta row: score · status · episode count
                val metaParts = buildList {
                    anime.averageScore?.let { add("\u2605 $it%") }
                    anime.status?.let { add(it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() }) }
                    anime.episodes?.let { add("$it eps") }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" \u00b7 "),
                        fontFamily = RobotoFamily,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Action button — 40dp black-40%-alpha circle, 22dp white icon
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape,
        modifier = Modifier
            .padding(4.dp)
            .size(40.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Genres row — horizontal scrollable chips
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun GenresRow(genres: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        genres.forEach { genre ->
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = genre,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Synopsis — collapsible with "Show more/less"
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SynopsisSection(description: String) {
    var expanded by remember { mutableStateOf(false) }
    val cleanDesc = description.replace(Regex("<[^>]*>"), "")
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(
            text = "Synopsis",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = cleanDesc,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = if (expanded) Int.MAX_VALUE else 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (cleanDesc.length > 100) {
            Text(
                text = if (expanded) "Show less" else "Show more",
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { expanded = !expanded },
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Episodes section — heading + source selector + placeholder
// ════════════════════════════════════════════════════════════════════════════

/**
 * Episodes section — shows the "Episodes" heading with a source selector on the
 * right, and a "not implemented" placeholder below.
 *
 * Per user spec: "at least what you can do for the current time being is that
 * you could show the episodes heading at the top. On the right side of that
 * heading you could show the extension selection option or the source selection
 * option. Below, even inside the episodes list, you could say that the episode
 * list is not implemented yet."
 *
 * The source selector is a placeholder for now — tapping it shows a toast-like
 * message. The actual source linking + episode fetching comes in a later step
 * (needs UnifiedAnime + provider infrastructure).
 */
@Composable
private fun EpisodesSection(
    linkedSource: LinkedSource?,
    episodeState: EpisodeState,
    onOpenSourcePicker: () -> Unit,
    onUnlinkSource: () -> Unit,
    onEpisodeClick: (eu.kanade.tachiyomi.animesource.model.SEpisode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Header: "Episodes" + source selector ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Episodes",
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.weight(1f))
            // Source selector pill — shows linked source name or "No source".
            Surface(
                color = if (linkedSource != null)
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(50),
                modifier = Modifier.clickable { onOpenSourcePicker() },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = linkedSource?.sourceName ?: "No source",
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (linkedSource != null)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = "Select source",
                        tint = if (linkedSource != null)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // ── Episode list / states ──
        when (episodeState) {
            is EpisodeState.Idle -> {
                // No source linked — show placeholder.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.HourglassEmpty,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No source linked",
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Tap the source selector above to search\nand link an extension source.",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            is EpisodeState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            is EpisodeState.Empty -> {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No episodes found on this source.",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            is EpisodeState.Error -> {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Failed to load episodes",
                        fontFamily = RobotoFamily,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = episodeState.message,
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onOpenSourcePicker) {
                        Text("Try another source", fontFamily = RobotoFamily, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            is EpisodeState.Loaded -> {
                // Episode list — each episode is a row.
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    episodeState.episodes.forEach { episode ->
                        EpisodeRow(
                            episode = episode,
                            onClick = { onEpisodeClick(episode) },
                        )
                    }
                    // Unlink button at the bottom.
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onUnlinkSource) {
                        Text(
                            "Unlink source",
                            fontFamily = RobotoFamily,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: eu.kanade.tachiyomi.animesource.model.SEpisode,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Episode number badge
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(width = 44.dp, height = 32.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = formatEpisodeNumber(episode.episode_number),
                        fontFamily = RobotoFamily,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            // Episode title
            Text(
                text = episode.name,
                fontFamily = RobotoFamily,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun formatEpisodeNumber(num: Float): String = when {
    num == num.toInt().toFloat() -> num.toInt().toString()
    else -> num.toString()
}

// ════════════════════════════════════════════════════════════════════════════
//  Info section — key/value table
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun InfoSection(anime: AniListAnime) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(
            text = "Information",
            fontFamily = RobotoFamily,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Season / Year
        if (anime.season != null && anime.seasonYear != null) {
            InfoRow("Season", "${anime.season!!.lowercase().replaceFirstChar { it.uppercase() }} ${anime.seasonYear}")
        } else if (anime.seasonYear != null) {
            InfoRow("Year", anime.seasonYear.toString())
        }

        // Episodes
        anime.episodes?.let { InfoRow("Episodes", it.toString()) }

        // Score
        anime.averageScore?.let { InfoRow("Score", "$it / 100") }

        // Status
        anime.status?.let {
            InfoRow("Status", it.replace("_", " ").lowercase().replaceFirstChar { c -> c.uppercase() })
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Error state
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ErrorState(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Couldn't load anime",
            fontFamily = RobotoFamily,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            fontFamily = RobotoFamily,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
