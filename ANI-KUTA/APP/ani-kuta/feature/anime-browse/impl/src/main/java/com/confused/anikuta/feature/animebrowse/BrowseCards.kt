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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
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
            // D-257: 1dp border (content color @ 50%) so the tag stays crisp
            // against busy cover art (device feedback: "give some border to
            // the rating tags so it is a bit more clear").
            anime.averageScore?.takeIf { it > 0 }?.let { score ->
                Surface(
                    modifier = Modifier.align(Alignment.TopStart),
                    color = badgeColors.scoreContainer,
                    shape = PointedTagShape(PointedSide.END),
                    border = BorderStroke(1.dp, badgeColors.scoreContent.copy(alpha = 0.5f)),
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
            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onBackground,
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
                color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
