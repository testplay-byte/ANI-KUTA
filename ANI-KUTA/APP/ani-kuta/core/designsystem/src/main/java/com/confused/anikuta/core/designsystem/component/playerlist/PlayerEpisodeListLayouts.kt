package com.confused.anikuta.core.designsystem.component.playerlist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-4 / D-699): the PLAYER episode list's FOUR PARADIGMS — the
//  one shared renderer both player stacks AND the settings live preview call.
// ════════════════════════════════════════════════════════════════════════════
//
//  The v1.1.59 device round: "the actual episode list on the details page is
//  getting a total of four custom display options, but the player page one is
//  not getting things like those. So what I want you to do is I want you to
//  build custom ones for that too… It should also be handled properly, just
//  like the other one."
//
//  THE DOCTRINE (the details page's D-555, player-scoped): every style is a
//  CURATED PRESET and a DIFFERENT STRUCTURE, not a variation of one row —
//    • DETAILED — the look the player list has always been: thumbnail + EP
//      tag + title + date/audio pills + synopsis (the richest row).
//    • COMPACT — the dense row: a smaller thumbnail, never a synopsis.
//    • GRID — the two-across poster wall: full-bleed 16:9 cells, EP badge
//      overlays, watched = grayscale + centered check, current = ring + play.
//    • BANNER — the full-bleed banner card: the thumbnail AS the card, a
//      bottom scrim, a ghost episode number, overlaid title + chips.
//
//  ONE RENDERER, THREE CALLERS (the D-481 one-source-of-truth — the exact
//  answer to "it should properly show the actual live preview of the data.
//  It should not show random things"): the MPV player page, the CS player
//  page, and Settings → Appearance → "Player episode list" all render through
//  [PlayerEpisodeListEntry] — what the preview shows is what the player draws.
//  The stacks keep their own identity/progress/ordinal logic and map it into
//  [PlayerEpisodeRowData]; this file renders and nothing else.
//
//  (This supersedes D-688's zero-coupling rule for ROW RENDERING only — the
//  two stacks still never depend on each other; both depend on
//  :core:designsystem, which they already did for ScrollBlurOverlay.)
// ════════════════════════════════════════════════════════════════════════════

/**
 * The player episode list's four layout paradigms. Stored as a string key in
 * [com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences.rowStyle];
 * ALWAYS resolved through [fromKey] so a stored legacy or unknown value maps
 * to the style the renderer will actually draw (the D-529 lesson).
 */
enum class PlayerEpisodeListStyle {
    DETAILED,
    COMPACT,
    GRID,
    BANNER;

    companion object {
        /**
         * The lenient lookup: `"MINIMAL"` (the retired round-101 density
         * variation) folds into COMPACT — the closest rhythm; unknown,
         * null or blank → DETAILED (the player list's default look).
         */
        fun fromKey(key: String?): PlayerEpisodeListStyle = when (key?.trim()?.uppercase()) {
            "COMPACT", "MINIMAL" -> COMPACT
            "GRID" -> GRID
            "BANNER" -> BANNER
            else -> DETAILED
        }
    }
}

/**
 * ONE episode's render-only data — everything the four layouts need, nothing
 * more. The callers own the semantics: the MPV stack maps its SimpleEpisode +
 * metadata (its audio pills from the scanlator parse), the CS stack maps its
 * render rows (its flavor ordinals + sub/dub pills + progress fraction), the
 * settings preview maps the real library episodes it loaded. All text is
 * PRE-FORMATTED by the caller (each stack formats its own dates/numbers).
 */
data class PlayerEpisodeRowData(
    /** The pre-formatted episode number text ("5", "5.5"). */
    val episodeNumberText: String,
    val displayTitle: String,
    val thumbnailUrl: String?,
    /** The pre-formatted release date ("Oct 12, 2025"), or null. */
    val dateText: String?,
    /** Audio pills (SUB / DUB / HSUB) — the MPV stack's parse. */
    val audioLabels: List<String> = emptyList(),
    /** The CS stack's scanlator sub/dub pill text, or null. */
    val subDubLabel: String? = null,
    /** The CS stack's merged-variant pills (["SUB", "DUB"]), or empty. */
    val flavorLabels: List<String> = emptyList(),
    val synopsis: String? = null,
    val isCurrent: Boolean = false,
    val isWatched: Boolean = false,
    /** 0..1 partial-watch fraction (the thin bar; fully-watched rows dim). */
    val progressFraction: Float = 0f,
    /** The MPV row's inert download glyph (the CS stack passes false). */
    val showDownloadHint: Boolean = false,
)

/** The display knobs — the live preference values, collected per screen. */
data class PlayerEpisodeListDisplay(
    val style: PlayerEpisodeListStyle,
    val showSynopsis: Boolean = true,
    val showDatePill: Boolean = true,
    val dimWatched: Boolean = true,
)

/**
 * THE DISPATCHER — one episode entry in whichever of the four paradigms
 * [display.style] selects. Row styles (DETAILED/COMPACT) render a padded
 * card row; GRID renders ONE CELL of the two-across wall (the callers pair
 * them through [PlayerEpisodeGridRow]); BANNER renders the full-bleed card.
 */
@Composable
fun PlayerEpisodeListEntry(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (display.style) {
        PlayerEpisodeListStyle.DETAILED ->
            PlayerEpisodeRow(data, display, compact = false, onClick, modifier)
        PlayerEpisodeListStyle.COMPACT ->
            PlayerEpisodeRow(data, display, compact = true, onClick, modifier)
        PlayerEpisodeListStyle.GRID ->
            PlayerEpisodeGridCell(data, display, onClick, modifier)
        PlayerEpisodeListStyle.BANNER ->
            PlayerEpisodeBannerCard(data, display, onClick, modifier)
    }
}

/**
 * One ROW of the GRID's two-across wall: [left] always renders; [right] may
 * be null (an odd count's last row — a spacer keeps the geometry honest).
 * The stacks chunk their display lists into pairs and call this per chunk.
 */
@Composable
fun PlayerEpisodeGridRow(
    left: PlayerEpisodeRowData,
    right: PlayerEpisodeRowData?,
    display: PlayerEpisodeListDisplay,
    onClick: (PlayerEpisodeRowData) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayerEpisodeListEntry(left, display, { onClick(left) }, Modifier.weight(1f))
        if (right != null) {
            PlayerEpisodeListEntry(right, display, { onClick(right) }, Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  DETAILED / COMPACT — the player row (the look the player list has always
//  been; COMPACT shrinks the thumbnail and never renders the synopsis)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayerEpisodeRow(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCurrent = data.isCurrent
    // The element knobs within the style's frame: COMPACT never shows the
    // synopsis; the date pill is toggle-gated.
    val description = if (compact || !display.showSynopsis) null else data.synopsis
    val dateText = if (display.showDatePill) data.dateText else null
    val pillsVisible = dateText != null || data.audioLabels.isNotEmpty() ||
        data.subDubLabel != null || data.flavorLabels.isNotEmpty() ||
        (data.showDownloadHint && description.isNullOrBlank())

    Surface(
        color = when {
            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            // The watched dim — the tinted-surface treatment (the current
            // row's highlight always wins).
            data.isWatched && display.dimWatched ->
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary,
        ) else null,
        modifier = modifier
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
                // ── Thumbnail with the EP tag overlay ──
                if (data.thumbnailUrl != null) {
                    Box(
                        modifier = if (compact) Modifier.size(width = 84.dp, height = 48.dp)
                        else Modifier.size(width = 120.dp, height = 68.dp),
                    ) {
                        AsyncImage(
                            model = data.thumbnailUrl,
                            contentDescription = data.displayTitle,
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
                                text = "EP ${data.episodeNumberText}",
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
                    // The number box (the thumbnail-less fallback tile).
                    Surface(
                        color = if (isCurrent) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.size(width = 44.dp, height = 32.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = data.episodeNumberText,
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
                            text = data.displayTitle,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (pillsVisible) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (dateText != null) {
                                Pill(dateText)
                            }
                            data.audioLabels.forEach { label -> Pill(label) }
                            if (data.subDubLabel != null) {
                                Pill(data.subDubLabel)
                            }
                            data.flavorLabels.forEach { label -> Pill(label) }
                            // The download hint rides the pills row when there
                            // is no synopsis (the MPV row's placement).
                            if (data.showDownloadHint && description.isNullOrBlank()) {
                                Spacer(Modifier.weight(1f))
                                Icon(
                                    imageVector = Icons.Filled.Download,
                                    contentDescription = "Download",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
            // ── Synopsis + the download hint at its end ──
            if (!description.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f),
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
                    if (data.showDownloadHint) {
                        Icon(
                            imageVector = Icons.Filled.Download,
                            contentDescription = "Download",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .size(24.dp)
                                .padding(start = 8.dp, bottom = 2.dp),
                        )
                    }
                }
            }
            // ── The thin watch-progress bar — partial watches only (fully
            // watched rows dim instead). ──
            if (data.progressFraction > 0f && !data.isWatched) {
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(data.progressFraction.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }
}

/** One quiet outline pill (the row's date/audio vocabulary). */
@Composable
private fun Pill(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    ) {
        Text(
            text = text,
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

// ════════════════════════════════════════════════════════════════════════════
//  GRID — one cell of the two-across poster wall
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayerEpisodeGridCell(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCurrent = data.isCurrent
    // Watched = grayscale + the centered check (the details page's GRID
    // language); the current episode's ring + play always wins visually.
    val watchedGray = data.isWatched && display.dimWatched && !isCurrent
    val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .then(
                if (isCurrent) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(12.dp),
                    )
                } else {
                    Modifier
                },
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
    ) {
        if (data.thumbnailUrl != null) {
            AsyncImage(
                model = data.thumbnailUrl,
                contentDescription = data.displayTitle,
                contentScale = ContentScale.Crop,
                colorFilter = if (watchedGray) grayscale else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // No thumbnail — the centered number tile keeps the cell honest.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = data.episodeNumberText,
                    fontFamily = RobotoFamily,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── The EP badge (top-start) ──
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopStart).padding(5.dp),
        ) {
            Text(
                text = "EP ${data.episodeNumberText}",
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                maxLines = 1,
                softWrap = false,
            )
        }

        // ── Watched: the centered check ──
        if (watchedGray) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Watched",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ── Current: the centered play glyph ──
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // ── The title scrim (one line, the cell's bottom) ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)),
                    ),
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
        ) {
            Text(
                text = data.displayTitle,
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  BANNER — the full-bleed card (the thumbnail AS the card)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayerEpisodeBannerCard(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isCurrent = data.isCurrent
    val watchedGray = data.isWatched && display.dimWatched && !isCurrent
    val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (isCurrent) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(14.dp),
                    )
                } else {
                    Modifier
                },
            )
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .clickable(onClick = onClick),
    ) {
        if (data.thumbnailUrl != null) {
            AsyncImage(
                model = data.thumbnailUrl,
                contentDescription = data.displayTitle,
                contentScale = ContentScale.Crop,
                colorFilter = if (watchedGray) grayscale else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "EP ${data.episodeNumberText}",
                    fontFamily = RobotoFamily,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ── The GHOST episode number (top-end, huge, translucent) ──
        Text(
            text = data.episodeNumberText,
            fontFamily = RobotoFamily,
            fontSize = 56.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White.copy(alpha = 0.20f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 2.dp, end = 10.dp),
        )

        // ── Watched: the check chip (top-start) ──
        if (watchedGray) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.Black.copy(alpha = 0.5f),
                modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Watched",
                    tint = Color.White,
                    modifier = Modifier.padding(4.dp).size(16.dp),
                )
            }
        }

        // ── Current: the centered play glyph ──
        if (isCurrent) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Playing",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(28.dp),
                )
            }
        }

        // ── The bottom scrim: the title + the translucent chips ──
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f)),
                    ),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = data.displayTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val chips = buildList {
                    if (display.showDatePill && data.dateText != null) add(data.dateText)
                    addAll(data.audioLabels)
                    if (data.subDubLabel != null) add(data.subDubLabel)
                    addAll(data.flavorLabels)
                }
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        chips.forEach { chip ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.18f),
                            ) {
                                Text(
                                    text = chip,
                                    fontFamily = RobotoFamily,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
