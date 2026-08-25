package com.confused.anikuta.feature.animebrowse

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.anilist.model.AniListAnime
import com.confused.anikuta.core.designsystem.badge.PointedSide
import com.confused.anikuta.core.designsystem.badge.PointedTagShape
import com.confused.anikuta.core.designsystem.badge.rememberBadgeColorScheme
import com.confused.anikuta.core.designsystem.theme.Motion
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey

/**
 * D-253: Browse cards — section headers, the anime card carousel, and the
 * continue-watching carousel. Split out of BrowseScreen.kt per CORE_RULES §5
 * (split code into multiple files; one file per responsibility).
 */

/** Section label — 14sp ExtraBold primary (DESIGN-LANGUAGE §5). */
@Composable
internal fun BrowseSectionHeader(title: String) {
    Text(
        text = title,
        fontFamily = RobotoFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.ExtraBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
    )
}

// ── Anime carousel (horizontal card row) ───────────────────────────────────────

@Composable
internal fun AnimeCarousel(
    anime: List<AniListAnime>,
    onClick: (AniListAnime) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(anime, key = { it.id }) { item ->
            BrowseAnimeCard(item, onClick)
        }
    }
}

/**
 * A single anime card.
 *
 * D-253 redesign (user: rating tags were "ugly", covers needed "a bit of
 * borders"):
 * - 2:3 cover (matches the Library card standard) with 12dp corners —
 *   standardized from the old inconsistent 14/18/10dp radii across the page.
 * - Subtle 1dp border (outlineVariant @ 60%) around every cover.
 * - Score badge: amber pointed corner tag flush at the cover's top-start,
 *   unified with the Library's score-badge color language (D-252) — replaces
 *   the old hard-coded black-65% pill with lime text.
 * - Press-scale 0.95 (unchanged — the proven card feedback pattern).
 */
@Composable
private fun BrowseAnimeCard(anime: AniListAnime, onClick: (AniListAnime) -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "cardScale",
    )
    val badgeColors = rememberBadgeColorScheme()
    val coverShape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .width(128.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = { onClick(anime) },
            ),
    ) {
        // Cover — clipped Box (so the flush badge follows the rounded corner)
        // with a subtle border + placeholder tone while the image loads.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(coverShape)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    coverShape,
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            AsyncImage(
                model = anime.coverUrl,
                contentDescription = anime.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Score badge — amber pointed corner tag, flush at top-start.
            // The outer (top-start) corner is clipped to the cover's 12dp
            // corner by the parent Box clip; the inner end is pointed
            // (PointedTagShape) per the D-252 badge language.
            anime.averageScore?.takeIf { it > 0 }?.let { score ->
                Surface(
                    modifier = Modifier.align(Alignment.TopStart),
                    color = badgeColors.scoreContainer,
                    shape = PointedTagShape(PointedSide.END),
                ) {
                    Text(
                        text = "★ $score",
                        fontFamily = RobotoFamily,
                        fontSize = 9.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeColors.scoreContent,
                        // Extra end padding keeps the text clear of the
                        // transparent 45° tip (tip depth ≈ height/2).
                        modifier = Modifier.padding(start = 5.dp, end = 9.dp, top = 1.dp, bottom = 1.dp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        // Title (1 line).
        Text(
            text = anime.displayName,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        // Subtitle: year · status.
        val subtitle = listOfNotNull(
            anime.seasonYear?.toString(),
            anime.status?.lowercase()?.replaceFirstChar { it.uppercase() },
        ).joinToString(" · ")
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ── Continue Watching carousel (D-248 direct-play contract preserved) ─────────

@Composable
internal fun ContinueWatchingCarousel(
    items: List<ContinueWatchingItem>,
    onNavigate: (NavKey) -> Unit,
    onPlay: ((ContinueWatchingItem) -> Unit)? = null,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { "${it.mainId}|${it.episodeNumber}" }) { item ->
            ContinueWatchingCard(item = item, onClick = {
                if (onPlay != null) {
                    onPlay(item)
                } else if (item.anilistId != null) {
                    onNavigate(AnimeDetailsKey.AniList(item.anilistId, autoPlayEpisode = item.episodeNumber))
                } else if (item.sourceId > 0 && item.animeUrl.isNotBlank()) {
                    onNavigate(AnimeDetailsKey.Extension(item.sourceId, item.animeUrl, item.title, null, autoPlayEpisode = item.episodeNumber))
                }
            })
        }
    }
}

/**
 * A continue-watching card (D-253 polish):
 * - 16:9 thumbnail, 12dp corners + the same subtle cover border.
 * - Center play affordance (32dp primary circle + PlayArrow) — makes the
 *   direct-play behavior discoverable.
 * - EP pill top-start (D-215 EP-tag recipe) + 3dp progress bar bottom.
 * - Press-scale feedback (the old card had none).
 */
@Composable
private fun ContinueWatchingCard(
    item: ContinueWatchingItem,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(Motion.DurationShort, easing = FastOutSlowInEasing),
        label = "cwCardScale",
    )
    val thumbShape = RoundedCornerShape(12.dp)

    Column(
        modifier = Modifier
            .width(168.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .size(width = 168.dp, height = 94.dp)
                .clip(thumbShape)
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    thumbShape,
                )
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        ) {
            val imageUrl = item.thumbnailUrl ?: item.coverUrl
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Text(
                    text = item.title.firstOrNull()?.uppercase() ?: "?",
                    fontFamily = RobotoFamily,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            // Center play affordance.
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
            // EP pill (D-215 EP-tag recipe: solid primary).
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopStart).padding(4.dp),
            ) {
                Text(
                    text = "EP ${item.episodeNumber}",
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    lineHeight = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (item.progressFraction > 0f) {
                LinearProgressIndicator(
                    progress = { item.progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.title,
            fontFamily = RobotoFamily,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "EP ${item.episodeNumber}",
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
