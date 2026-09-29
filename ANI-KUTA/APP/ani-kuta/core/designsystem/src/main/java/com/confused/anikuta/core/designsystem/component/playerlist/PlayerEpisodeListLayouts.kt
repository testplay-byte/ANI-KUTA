package com.confused.anikuta.core.designsystem.component.playerlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.designsystem.theme.RobotoFamily

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-4 / D-699) → ROUND 104 (WS-D / D-703): the PLAYER episode
//  list's FOUR PARADIGMS — the one shared renderer both player stacks AND
//  the settings live preview call.
// ════════════════════════════════════════════════════════════════════════════
//
//  The v1.1.59 round ordered the four-paradigm doctrine (the details page's
//  D-555, player-scoped); the v1.1.60 device round then reworked the SET:
//
//  • THE WATCHED FILTER IS GONE — "the option for watch filter as off, show
//    watched and hide watched is most definitely not good. It should be
//    completely removed." (The pref, both stacks' branches, and the settings
//    card all retired together — the DIM treatment stays.)
//
//  • COMPACT IS REPLACED BY TRACKLIST — "the compact layout, it is just
//    trash… I want you to completely remove it, and instead of this layout I
//    would like you to redesign a completely new, proper, beautiful,
//    good-looking layout from scratch." THE DESIGN: a typographic,
//    number-forward list — the episode NUMBER as the hero element (a fixed
//    ghost-muted numeral column), a hairline spine, the title + pills to its
//    right, never a thumbnail, never a synopsis. A different STRUCTURE, not
//    a variation of one row.
//
//  • THE EP TAG lives ONLY on the DETAILED row — "the tags, the episode tags
//    which show on the top left corner of the thumbnail images. I don't like
//    them, so remove them. It should only be kept in the detailed view." The
//    GRID's number moved into the bottom scrim's title line; the TRACKLIST's
//    number IS its hero; the BANNER keeps its top-end ghost numeral (not a
//    top-left tag) and it is now TOGGLEABLE.
//
//  • THE DIM is real now — the v1.1.60 round: "apparently on the details
//    one, it does not have any functionality properly implemented for that,
//    for dimming the watched episodes." The DETAILED row dims the WHOLE card
//    (graphicsLayer alpha 0.55) + grayscales the thumbnail — the details
//    page's CLASSIC treatment, exactly.
//
//  • THE BANNER gained the controls — "the episode number is not shown
//    properly, it is not customizable… add a density slider too, like I can
//    select what the size of them should be easily, and it would properly
//    show in live view": [PlayerEpisodeListDisplay.showEpisodeNumber] toggles
//    the ghost numeral; [PlayerEpisodeListDisplay.bannerDensity] (0f…1f)
//    drives the aspect (21:9 flat strips → 4:3 tall cards; 0.5 ≈ the classic
//    16:9).
//
//  • SWIPE + the ARRIVAL PULSE — "on the player episodes list there should
//    be the swipe functionality too, exactly like how it is on the details
//    page" (PlayerEpisodeSwipe.kt — the shared gesture; GRID long-presses)
//    and "after scrolling to that area… add the effect of highlighting, like
//    it will highlight that specific episode a bit and then just change the
//    things to the normal ones" (the arrivalPulse token — the page bumps it
//    after a COMPLETED glide; the current entry plays a wash + ring that
//    fades to the normal treatment; a user-interrupted glide never pulses).
//
//  ONE RENDERER, THREE CALLERS (the D-481 one-source-of-truth): the MPV
//  player page, the CS player page, and Settings → Appearance → "Player
//  episode list" all render through [PlayerEpisodeListEntry] — what the
//  preview shows is what the player draws. The stacks keep their own
//  identity/progress/ordinal logic and map it into [PlayerEpisodeRowData];
//  this file renders and nothing else.
// ════════════════════════════════════════════════════════════════════════════

/**
 * The player episode list's four layout paradigms. Stored as a string key in
 * [com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences.rowStyle];
 * ALWAYS resolved through [fromKey] so a stored legacy or unknown value maps
 * to the style the renderer will actually draw (the D-529 lesson).
 */
enum class PlayerEpisodeListStyle {
    DETAILED,
    TRACKLIST,
    GRID,
    BANNER;

    companion object {
        /**
         * The lenient lookup: the retired round-101/103 density keys
         * ("COMPACT", "MINIMAL" — the variations the v1.1.60 round retired)
         * fold into TRACKLIST (the slot's from-scratch replacement); unknown,
         * null or blank → DETAILED (the player list's default look).
         */
        fun fromKey(key: String?): PlayerEpisodeListStyle = when (key?.trim()?.uppercase()) {
            "TRACKLIST", "COMPACT", "MINIMAL" -> TRACKLIST
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
    /** ROUND 104: the BANNER's ghost episode number toggle. */
    val showEpisodeNumber: Boolean = true,
    /**
     * ROUND 104: the BANNER's density — 0f (21:9 flat strips) … 1f (4:3
     * tall cards); 0.5f ≈ the classic 16:9. See [bannerAspectRatio].
     */
    val bannerDensity: Float = 0.5f,
)

/**
 * The BANNER's aspect ratio from the density knob — a linear blend from the
 * flat 21:9 cinematic strip (density 0) to the tall 4:3 preview card
 * (density 1); the 0.5 default lands at ≈16.5:9 (the classic look).
 */
fun bannerAspectRatio(density: Float): Float {
    val t = density.coerceIn(0f, 1f)
    return (21f / 9f) + ((4f / 3f) - (21f / 9f)) * t
}

/**
 * THE DISPATCHER — one episode entry in whichever of the four paradigms
 * [display.style] selects. Row styles (DETAILED/TRACKLIST) and the BANNER
 * wrap in the shared swipe-to-toggle when [onToggleWatched] is provided
 * (carrying the entry's outer padding); GRID renders ONE CELL of the
 * two-across wall (the callers pair them through [PlayerEpisodeGridRow];
 * cells long-press to toggle).
 *
 * [arrivalPulse] is the page's scroll-arrival token — the current entry
 * plays a highlight wash + ring that fades back to its normal treatment
 * whenever the token changes (a user-interrupted glide never bumps it).
 */
@Composable
fun PlayerEpisodeListEntry(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatched: (() -> Unit)? = null,
    arrivalPulse: Long = 0L,
) {
    when (display.style) {
        PlayerEpisodeListStyle.DETAILED -> SwipeableEntry(
            data = data,
            onToggleWatched = onToggleWatched,
            modifier = modifier,
            verticalPadding = 3.dp,
            backgroundShape = RoundedCornerShape(12.dp),
        ) { entryModifier ->
            PlayerEpisodeRow(data, display, onClick, entryModifier, arrivalPulse)
        }
        PlayerEpisodeListStyle.TRACKLIST -> SwipeableEntry(
            data = data,
            onToggleWatched = onToggleWatched,
            modifier = modifier,
            verticalPadding = 3.dp,
            backgroundShape = RoundedCornerShape(12.dp),
        ) { entryModifier ->
            PlayerTracklistRow(data, display, onClick, entryModifier, arrivalPulse)
        }
        PlayerEpisodeListStyle.GRID ->
            PlayerEpisodeGridCell(data, display, onClick, modifier, onToggleWatched, arrivalPulse)
        PlayerEpisodeListStyle.BANNER -> SwipeableEntry(
            data = data,
            onToggleWatched = onToggleWatched,
            modifier = modifier,
            verticalPadding = 4.dp,
            backgroundShape = RoundedCornerShape(14.dp),
        ) { entryModifier ->
            PlayerEpisodeBannerCard(data, display, onClick, entryModifier, arrivalPulse)
        }
    }
}

/**
 * The row/banner swipe adapter: applies the entry's OUTER padding on the
 * wrapper (so the gesture + the background icon cover exactly the card's
 * visual footprint) and wraps the card in [PlayerEpisodeSwipeToToggle] when
 * a toggle is wired; without one, the padding passes straight through.
 */
@Composable
private fun SwipeableEntry(
    data: PlayerEpisodeRowData,
    onToggleWatched: (() -> Unit)?,
    modifier: Modifier,
    verticalPadding: Dp,
    backgroundShape: androidx.compose.ui.graphics.Shape,
    content: @Composable (Modifier) -> Unit,
) {
    val outer = modifier.padding(horizontal = 10.dp, vertical = verticalPadding)
    if (onToggleWatched != null) {
        PlayerEpisodeSwipeToToggle(
            isWatched = data.isWatched,
            onToggleWatched = onToggleWatched,
            modifier = outer,
            backgroundShape = backgroundShape,
        ) {
            content(Modifier)
        }
    } else {
        content(outer)
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
    onToggleWatched: ((PlayerEpisodeRowData) -> Unit)? = null,
    arrivalPulse: Long = 0L,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PlayerEpisodeListEntry(
            data = left,
            display = display,
            onClick = { onClick(left) },
            modifier = Modifier.weight(1f),
            onToggleWatched = onToggleWatched?.let { cb -> { cb(left) } },
            arrivalPulse = arrivalPulse,
        )
        if (right != null) {
            PlayerEpisodeListEntry(
                data = right,
                display = display,
                onClick = { onClick(right) },
                modifier = Modifier.weight(1f),
                onToggleWatched = onToggleWatched?.let { cb -> { cb(right) } },
                arrivalPulse = arrivalPulse,
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  The ARRIVAL PULSE — the scroll-completion highlight ("it will highlight
//  that specific episode a bit and then just change the things to the normal
//  ones as they will be"). The page bumps a Long token after a COMPLETED
//  glide; the current entry animates a primary wash + ring from full
//  strength down to nothing over ~1.1s, revealing the row's own (current)
//  treatment underneath — the "back to normal" the order describes.
// ════════════════════════════════════════════════════════════════════════════

/** The fading pulse strength for the current entry (0 when idle). */
@Composable
private fun rememberArrivalPulseAlpha(arrivalPulse: Long, isCurrent: Boolean): Float {
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(arrivalPulse) {
        if (arrivalPulse > 0L && isCurrent) {
            pulse.snapTo(1f)
            pulse.animateTo(
                targetValue = 0f,
                animationSpec = tween(1100, easing = FastOutSlowInEasing),
            )
        }
    }
    return pulse.value
}

/** The pulse overlay — the wash + ring, drawn over the entry's own content. */
@Composable
private fun BoxScope.ArrivalPulseOverlay(pulseAlpha: Float, shape: androidx.compose.ui.graphics.Shape) {
    if (pulseAlpha > 0.005f) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { this.alpha = pulseAlpha }
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f), shape)
                .border(
                    2.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    shape,
                ),
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  DETAILED — the player row (the look the player list has always been):
//  thumbnail + the ONLY surviving EP tag + title + pills + synopsis. The
//  watched dim is the REAL one now (whole-card alpha + grayscale thumbnail).
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayerEpisodeRow(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrivalPulse: Long = 0L,
) {
    val isCurrent = data.isCurrent
    // ROUND 104 (WS-D): the REAL dim — the whole card at 0.55 alpha + the
    // grayscale thumbnail (the details page's CLASSIC treatment; the current
    // row's highlight always wins).
    val dimmed = data.isWatched && display.dimWatched && !isCurrent
    val grayscale = dimmed
    val description = if (!display.showSynopsis) null else data.synopsis
    val dateText = if (display.showDatePill) data.dateText else null
    val pillsVisible = dateText != null || data.audioLabels.isNotEmpty() ||
        data.subDubLabel != null || data.flavorLabels.isNotEmpty() ||
        (data.showDownloadHint && description.isNullOrBlank())

    Surface(
        color = when {
            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            dimmed -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { this.alpha = if (dimmed) 0.55f else 1f }
                    .padding(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    // ── Thumbnail with the EP tag overlay (the ONLY style
                    //    that keeps it — the v1.1.60 order) ──
                    if (data.thumbnailUrl != null) {
                        Box(
                            modifier = Modifier.size(width = 120.dp, height = 68.dp),
                        ) {
                            AsyncImage(
                                model = data.thumbnailUrl,
                                contentDescription = data.displayTitle,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop,
                                colorFilter = if (grayscale) {
                                    ColorFilter.colorMatrix(
                                        ColorMatrix().apply { setToSaturation(0f) },
                                    )
                                } else null,
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
            // The scroll-arrival pulse (the current row only).
            val pulseAlpha = rememberArrivalPulseAlpha(arrivalPulse, data.isCurrent)
            ArrivalPulseOverlay(pulseAlpha, RoundedCornerShape(12.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  TRACKLIST (ROUND 104 — replaces COMPACT) — the typographic, number-forward
//  list: the episode NUMBER as the hero (a fixed ghost-muted numeral column),
//  a hairline spine, the title + pills to its right; never a thumbnail, never
//  a synopsis. The number is the identity — big, calm, scannable.
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayerTracklistRow(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrivalPulse: Long = 0L,
) {
    val isCurrent = data.isCurrent
    val dimmed = data.isWatched && display.dimWatched && !isCurrent
    val numberTone = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    val dateText = if (display.showDatePill) data.dateText else null
    val pills = buildList {
        if (dateText != null) add(dateText)
        addAll(data.audioLabels)
        if (data.subDubLabel != null) add(data.subDubLabel)
        addAll(data.flavorLabels)
    }

    Surface(
        color = when {
            isCurrent -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        shape = RoundedCornerShape(12.dp),
        border = if (isCurrent) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { this.alpha = if (dimmed) 0.55f else 1f },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    // ── The NUMBER hero — a fixed-width right-aligned
                    //    numeral, the row's identity (SA2-F3: TextAlign.End
                    //    so "1"/"10"/"100" right-align in the column). ──
                    Text(
                        text = data.episodeNumberText,
                        fontFamily = RobotoFamily,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = numberTone,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(52.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    // ── The hairline spine — the track-list's rail. ──
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(30.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    // ── The title + pills. ──
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = data.displayTitle,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (pills.isNotEmpty()) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                pills.forEach { pill -> Pill(pill) }
                            }
                        }
                    }
                    // ── The trailing state glyph — the current row's play;
                    //    a watched row's quiet check. ──
                    Spacer(Modifier.width(8.dp))
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Playing",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                    } else if (data.isWatched) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Watched",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                // ── The thin watch-progress underline — partial watches only. ──
                if (data.progressFraction > 0f && !data.isWatched) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.dp)
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
            // The scroll-arrival pulse (the current row only).
            val pulseAlpha = rememberArrivalPulseAlpha(arrivalPulse, data.isCurrent)
            ArrivalPulseOverlay(pulseAlpha, RoundedCornerShape(12.dp))
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
//  GRID — one cell of the two-across poster wall. ROUND 104: the top-left
//  EP badge is GONE ("remove them. It should only be kept in the detailed
//  view") — the number rides the bottom scrim's title line instead; the
//  watched/current language stays (grayscale + check / ring + play); the
//  cell long-presses to toggle watched (the details page's GRID parity).
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerEpisodeGridCell(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatched: (() -> Unit)? = null,
    arrivalPulse: Long = 0L,
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleWatched ?: {},
            ),
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

        // ── The bottom scrim: "EP N" + the title (the number's new home —
        //    the top-left badge is retired per the v1.1.60 order). ──
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "EP ${data.episodeNumberText}",
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = data.displayTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // The scroll-arrival pulse (the current cell only).
        val pulseAlpha = rememberArrivalPulseAlpha(arrivalPulse, data.isCurrent)
        if (pulseAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { this.alpha = pulseAlpha }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  BANNER — the full-bleed card (the thumbnail AS the card). ROUND 104: the
//  ghost episode number is TOGGLEABLE (showEpisodeNumber) and the aspect is
//  DENSITY-DRIVEN (bannerDensity: 21:9 flat strips → 4:3 tall cards).
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayerEpisodeBannerCard(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrivalPulse: Long = 0L,
) {
    val isCurrent = data.isCurrent
    val watchedGray = data.isWatched && display.dimWatched && !isCurrent
    val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(bannerAspectRatio(display.bannerDensity))
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

        // ── The GHOST episode number (top-end, huge, translucent) —
        //    TOGGLEABLE since ROUND 104 ("it is not customizable"). ──
        if (display.showEpisodeNumber) {
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
        }

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

        // The scroll-arrival pulse (the current card only).
        val pulseAlpha = rememberArrivalPulseAlpha(arrivalPulse, data.isCurrent)
        if (pulseAlpha > 0.005f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { this.alpha = pulseAlpha }
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)),
            )
        }
    }
}
