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
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
//    the big numeral. (ROUND 105 re-aimed the size knob: the aspect is FIXED
//    16:9 and [PlayerEpisodeListDisplay.bannerSize] scales the ITEM — see
//    the banner section below; the round-104 aspect-driven density is
//    retired.)
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
//  page" all render through [PlayerEpisodeListEntry] — what the
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
    /**
     * ROUND 106 (WS-D): the download badge's render state — mapped by the
     * caller from the core download status ("$mainId|$episodeKey" in
     * DownloadManager.episodeDownloadStates). NULL = the badge is hidden
     * (the toggle is off, or the stack has no download support for the row).
     */
    val downloadState: PlayerDownloadRenderState? = null,
)

/**
 * ROUND 106 (WS-D): the download badge's render-only state — the details
 * page's approved EpisodeDownloadBadge contract, generalized for the player
 * (the D-481 render-only doctrine: the callers map their semantics INTO
 * this; the layouts never touch the download engine).
 */
sealed interface PlayerDownloadRenderState {
    /** Nothing downloaded — the plain download glyph. */
    data object NotDownloaded : PlayerDownloadRenderState

    /** Resolving / queued / retrying — the in-flight indeterminate spinner. */
    data object InFlight : PlayerDownloadRenderState

    /** Actively downloading — the determinate ring + the progress numeral. */
    data class Downloading(val progress: Int) : PlayerDownloadRenderState

    /** Paused — the resume glyph. */
    data object Paused : PlayerDownloadRenderState

    /** Errored — the retry glyph. */
    data object Error : PlayerDownloadRenderState

    /** Downloaded — the check; tap plays the offline file. */
    data object Downloaded : PlayerDownloadRenderState
}

/**
 * ROUND 106 (WS-D): the download badge's action bag — the caller wires the
 * engine (enqueue / pause / resume / cancel / retry / play-downloaded);
 * [previewTapAll] is the settings preview's demo-cycle hook (the D-557
 * pattern — ONE tap target for ALL states).
 */
data class PlayerEpisodeDownloadActions(
    val onDownload: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onCancel: () -> Unit,
    val onRetry: () -> Unit,
    val onPlayDownloaded: () -> Unit,
    val previewTapAll: (() -> Unit)? = null,
)

/** The display knobs — the live preference values, collected per screen. */
data class PlayerEpisodeListDisplay(
    val style: PlayerEpisodeListStyle,
    val showSynopsis: Boolean = true,
    val showDatePill: Boolean = true,
    /** ROUND 106 (WS-D): the audio pills' toggle (every style; they wrap). */
    val showAudioPills: Boolean = true,
    val dimWatched: Boolean = true,
    /** ROUND 104: the BANNER's ghost episode number toggle. */
    val showEpisodeNumber: Boolean = true,
    /** ROUND 105: the thin watch-progress bar/underline (DETAILED + TRACKLIST). */
    val showProgressBar: Boolean = true,
    /**
     * ROUND 106 (WS-D): THE DOWNLOAD BUTTON — the dedicated toggle (default
     * off). When on, every layout renders the badge from the row's
     * [PlayerEpisodeRowData.downloadState] + the wired actions.
     */
    val showDownloadButton: Boolean = false,
    /**
     * ROUND 105: the BANNER's ITEM SIZE — 0f (small: ≈66% width, centered,
     * breathing room between cards) … 1f (full-bleed, the classic look).
     * See [bannerWidthFraction] / [bannerVerticalPadding].
     */
    val bannerSize: Float = 1f,
    /** ROUND 105: the BANNER's number corner. */
    val bannerNumberPosition: PlayerBannerNumberPosition = PlayerBannerNumberPosition.TOP_END,
    /** ROUND 105: the BANNER's number treatment (frosted glass / solid). */
    val bannerNumberStyle: PlayerBannerNumberStyle = PlayerBannerNumberStyle.FROSTED,
    /** ROUND 106 (WS-D): the BANNER's currently-playing treatment (the
     * GRID's PLAY/TINT knob, ported). */
    val bannerCurrentStyle: PlayerBannerCurrentStyle = PlayerBannerCurrentStyle.PLAY,
    /** ROUND 105: the GRID's watched treatment (the checkmark, not the dim). */
    val gridWatchedCheckmark: Boolean = true,
    /** ROUND 105: the GRID's currently-playing treatment. */
    val gridCurrentStyle: PlayerGridCurrentStyle = PlayerGridCurrentStyle.PLAY,
    /** ROUND 105: the GRID's bottom-scrim title line. */
    val gridTitles: Boolean = true,
    /**
     * ROUND 105: the TRACKLIST's column-sizing reference — the LIST's widest
     * episode-number text, pre-formatted by the caller (e.g. "24" for a
     * 24-episode list). The row measures it once and sizes the number column
     * exactly, so the digit hugs the left edge with no dead padding and the
     * spine stays list-stable. Default "00" = a two-digit fit.
     */
    val tracklistReferenceNumber: String = "00",
)

/**
 * ROUND 105: the BANNER's number corner — "there should be the option to
 * select where the episode number should show, whether it should show on
 * the top right side or on the top left side." Lenient fromKey (the rowStyle
 * doctrine): unknown values fold to the default TOP_END.
 */
enum class PlayerBannerNumberPosition {
    TOP_START,
    TOP_END;

    companion object {
        fun fromKey(key: String?): PlayerBannerNumberPosition =
            if (key?.trim()?.equals("TOP_START", ignoreCase = true) == true) {
                TOP_START
            } else {
                TOP_END
            }
    }
}

/**
 * ROUND 105: the BANNER's number treatment — "should it be shown in solid
 * themed color, or should it be shown in a frosted theme color?" (FROSTED =
 * the two-copy blurred-glass text, the details page's CINEMA D-559
 * treatment; SOLID = the straight themed numeral, D-558. Both THEMED — the
 * round-104 white ghost is gone.)
 */
enum class PlayerBannerNumberStyle {
    FROSTED,
    SOLID;

    companion object {
        fun fromKey(key: String?): PlayerBannerNumberStyle =
            if (key?.trim()?.equals("SOLID", ignoreCase = true) == true) {
                SOLID
            } else {
                FROSTED
            }
    }
}

/**
 * ROUND 105: the GRID's currently-playing treatment — "the option between
 * showing the play button on the currently playing or rather theme the
 * currently playing episode list or cover image or such in the themed
 * color."
 */
enum class PlayerGridCurrentStyle {
    PLAY,
    TINT;

    companion object {
        fun fromKey(key: String?): PlayerGridCurrentStyle =
            if (key?.trim()?.equals("TINT", ignoreCase = true) == true) {
                TINT
            } else {
                PLAY
            }
    }
}

/**
 * ROUND 106 (WS-D): the BANNER's currently-playing treatment — the GRID's
 * knob, ported per the v1.1.62 order: "the grid view has the ability to
 * select between play button and the themed tint, but the banner does not
 * have it. So I want you to implement it there properly too." Lenient
 * fromKey (the rowStyle doctrine): unknown values fold to PLAY.
 */
enum class PlayerBannerCurrentStyle {
    PLAY,
    TINT;

    companion object {
        fun fromKey(key: String?): PlayerBannerCurrentStyle =
            if (key?.trim()?.equals("TINT", ignoreCase = true) == true) {
                TINT
            } else {
                PLAY
            }
    }
}

/**
 * ROUND 105: the BANNER's item-width fraction from the size knob — 1f =
 * full-bleed (the classic look); 0f = 66% of the row width, centered (the
 * "padding on the left and right sides" the device round described).
 */
fun bannerWidthFraction(size: Float): Float {
    val t = size.coerceIn(0f, 1f)
    return 0.66f + (1f - 0.66f) * t
}

/**
 * ROUND 105: the BANNER's per-item vertical padding from the size knob —
 * 4dp at full size (today's rhythm) growing to 10dp at the smallest (the
 * "padding between each individual episodes themselves").
 */
fun bannerVerticalPadding(size: Float): Dp {
    val t = size.coerceIn(0f, 1f)
    // CI run-1 fix: pure Float arithmetic, THEN .dp once — the first draft's
    // `(1f - t) * 6.dp` needed the top-level Float.times(Dp) operator
    // extension import (the stdlib's Float.times overloads were the only
    // candidates the compiler could see).
    return (4f + (1f - t) * 6f).dp
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
    // ROUND 106 (WS-D): the download badge's action bag — wired by the
    // caller when the toggle is on (null = the badge stays hidden even if
    // the row carries a state — the preview passes a demo bag).
    downloadActions: PlayerEpisodeDownloadActions? = null,
) {
    when (display.style) {
        PlayerEpisodeListStyle.DETAILED -> SwipeableEntry(
            data = data,
            onToggleWatched = onToggleWatched,
            modifier = modifier,
            verticalPadding = 3.dp,
            backgroundShape = RoundedCornerShape(12.dp),
        ) { entryModifier ->
            PlayerEpisodeRow(data, display, onClick, entryModifier, arrivalPulse, downloadActions)
        }
        PlayerEpisodeListStyle.TRACKLIST -> SwipeableEntry(
            data = data,
            onToggleWatched = onToggleWatched,
            modifier = modifier,
            verticalPadding = 3.dp,
            backgroundShape = RoundedCornerShape(12.dp),
        ) { entryModifier ->
            PlayerTracklistRow(data, display, onClick, entryModifier, arrivalPulse, downloadActions)
        }
        PlayerEpisodeListStyle.GRID ->
            PlayerEpisodeGridCell(data, display, onClick, modifier, onToggleWatched, arrivalPulse, downloadActions)
        // ROUND 105 (WS-D): THE BANNER'S ITEM SIZE — the swipe wrapper
        // narrows to the size's width fraction and CENTERS inside the row
        // ("some padding on the left and right sides"), while its vertical
        // padding grows with the shrink ("padding between each individual
        // episodes themselves"). At size 1f the geometry is byte-identical
        // to the round-104 full-bleed banner.
        PlayerEpisodeListStyle.BANNER -> {
            val sizeFraction = bannerWidthFraction(display.bannerSize)
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                SwipeableEntry(
                    data = data,
                    onToggleWatched = onToggleWatched,
                    modifier = Modifier.fillMaxWidth(sizeFraction),
                    verticalPadding = bannerVerticalPadding(display.bannerSize),
                    backgroundShape = RoundedCornerShape(14.dp),
                ) { entryModifier ->
                    PlayerEpisodeBannerCard(data, display, onClick, entryModifier, arrivalPulse, downloadActions)
                }
            }
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
    // ROUND 106 (WS-D): the per-cell download actions (resolved by the
    // caller from the cell's row data — the stacks own the engine wiring).
    downloadActions: ((PlayerEpisodeRowData) -> PlayerEpisodeDownloadActions?)? = null,
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
            downloadActions = downloadActions?.let { resolver -> resolver(left) },
        )
        if (right != null) {
            PlayerEpisodeListEntry(
                data = right,
                display = display,
                onClick = { onClick(right) },
                modifier = Modifier.weight(1f),
                onToggleWatched = onToggleWatched?.let { cb -> { cb(right) } },
                arrivalPulse = arrivalPulse,
                downloadActions = downloadActions?.let { resolver -> resolver(right) },
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerEpisodeRow(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrivalPulse: Long = 0L,
    downloadActions: PlayerEpisodeDownloadActions? = null,
) {
    val isCurrent = data.isCurrent
    // ROUND 104 (WS-D): the REAL dim — the whole card at 0.55 alpha + the
    // grayscale thumbnail (the details page's CLASSIC treatment; the current
    // row's highlight always wins).
    val dimmed = data.isWatched && display.dimWatched && !isCurrent
    val grayscale = dimmed
    val description = if (!display.showSynopsis) null else data.synopsis
    val dateText = if (display.showDatePill) data.dateText else null
    // ROUND 106 (WS-D): the audio pills respect their own knob now, and the
    // old inert download HINT glyph is RETIRED (the real badge below
    // replaces it — a glyph that looks like a button but does nothing was
    // the lie the round-104 review flagged).
    val audioPills = if (display.showAudioPills) {
        buildList {
            addAll(data.audioLabels)
            if (data.subDubLabel != null) add(data.subDubLabel)
            addAll(data.flavorLabels)
        }
    } else {
        emptyList()
    }
    val pillsVisible = dateText != null || audioPills.isNotEmpty()
    // ROUND 106 (WS-D): THE DOWNLOAD BADGE — the toggle + the row's state +
    // the wired actions together decide it.
    val showDownloadBadge = display.showDownloadButton &&
        data.downloadState != null && downloadActions != null

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
                            // ROUND 106 (WS-D): the pills WRAP — the tags are
                            // data, never clipped ("all the tags are
                            // considered properly and handled properly").
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (dateText != null) {
                                    Pill(dateText)
                                }
                                audioPills.forEach { label -> Pill(label) }
                            }
                        }
                    }
                    // ── ROUND 106 (WS-D): the TRAILING download badge — the
                    //    row's end, beside the title/pills block (always
                    //    visible, synopsis or not; the details page's
                    //    trailing-control placement). ──
                    if (showDownloadBadge && data.downloadState != null && downloadActions != null) {
                        Spacer(Modifier.width(8.dp))
                        PlayerEpisodeDownloadBadge(
                            state = data.downloadState,
                            actions = downloadActions,
                        )
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

                    }
                }
                // ── The thin watch-progress bar — partial watches only (fully
                // watched rows dim instead). ROUND 105: toggleable
                // ("show or hide the progress bar"). ──
                if (data.progressFraction > 0f && !data.isWatched && display.showProgressBar) {
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
//  TRACKLIST (ROUND 104 — replaces COMPACT; ROUND 105 refines) — the
//  typographic, number-forward list: the episode NUMBER as the hero, a
//  hairline spine, the title + pills + an optional two-line synopsis to its
//  right; never a thumbnail.
//
//  ROUND 105 (WS-D) — THE NUMBER COLUMN'S ROOT-CAUSE FIX: the v1.1.61
//  device round: "the episode number was shown on the left side, but there
//  was some padding on the left too. Like there was way too much padding,
//  and then the actual episode number showed. And also the episode number
//  should be made a little bit bigger." The round-104 column was a FIXED
//  52dp with TextAlign.End — a single-digit numeral sat ~39dp into its
//  column before the glyph even started. The column now EXACTLY FITS the
//  list's widest number (the caller's tracklistReferenceNumber, measured
//  once via TextMeasurer) and the digits are LEFT-aligned: the numeral
//  hugs the row's 10dp start padding (the pressed-tracklist typography —
//  the spine stays list-stable), and the number grew 21sp → 24sp.
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerTracklistRow(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrivalPulse: Long = 0L,
    downloadActions: PlayerEpisodeDownloadActions? = null,
) {
    val isCurrent = data.isCurrent
    val dimmed = data.isWatched && display.dimWatched && !isCurrent
    val numberTone = when {
        isCurrent -> MaterialTheme.colorScheme.primary
        dimmed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
    }
    val dateText = if (display.showDatePill) data.dateText else null
    // ROUND 106 (WS-D): the audio pills respect their own knob.
    val pills = buildList {
        if (dateText != null) add(dateText)
        if (display.showAudioPills) {
            addAll(data.audioLabels)
            if (data.subDubLabel != null) add(data.subDubLabel)
            addAll(data.flavorLabels)
        }
    }
    // ROUND 105: the optional synopsis ("there was no option to turn on or
    // show the synopsis or turn off the synopsis. So I need a toggle… in
    // this area") — the same knob the DETAILED row reads.
    val synopsis = if (display.showSynopsis) data.synopsis?.takeIf { it.isNotBlank() } else null

    // ROUND 105: the EXACT-FIT number column — the LIST's widest number
    // (the reference text), measured once in the row's own typography. All
    // rows of one list share the same display → the same column → aligned
    // spines; the digit itself hugs the left edge.
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val numberColumnWidth = remember(textMeasurer, display.tracklistReferenceNumber, density) {
        val measured = textMeasurer.measure(
            text = display.tracklistReferenceNumber,
            style = TextStyle(
                fontFamily = RobotoFamily,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
            ),
        )
        with(density) {
            // +2dp of optical tolerance so the widest numeral never clips.
            measured.size.width.toDp() + 2.dp
        }
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
                        .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                ) {
                    // ── The NUMBER hero — LEFT-aligned in the exact-fit
                    //    column (ROUND 105: no dead left padding; the digit
                    //    starts at the row's 10dp inset). ──
                    Text(
                        text = data.episodeNumberText,
                        fontFamily = RobotoFamily,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = numberTone,
                        textAlign = TextAlign.Start,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = false,
                        modifier = Modifier.width(numberColumnWidth),
                    )
                    Spacer(Modifier.width(12.dp))
                    // ── The hairline spine — the track-list's rail. ──
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(34.dp)
                            .background(
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                            ),
                    )
                    Spacer(Modifier.width(12.dp))
                    // ── The title + pills (+ the optional synopsis). ──
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
                            // ROUND 106 (WS-D): the pills WRAP — the tags are
                            // data, never clipped.
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                pills.forEach { pill -> Pill(pill) }
                            }
                        }
                        if (synopsis != null) {
                            Text(
                                text = synopsis,
                                fontFamily = RobotoFamily,
                                fontSize = 12.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // ── ROUND 106 (WS-D): the TRAILING download badge —
                    //    first in the slot, before the state glyphs (the
                    //    download is an action; the glyphs are states). ──
                    if (display.showDownloadButton && data.downloadState != null &&
                        downloadActions != null
                    ) {
                        Spacer(Modifier.width(8.dp))
                        PlayerEpisodeDownloadBadge(
                            state = data.downloadState,
                            actions = downloadActions,
                        )
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
                // ── The thin watch-progress underline — partial watches only
                //    (ROUND 105: toggleable with the DETAILED row's knob). ──
                if (data.progressFraction > 0f && !data.isWatched && display.showProgressBar) {
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
//  ROUND 106 (WS-D): THE DOWNLOAD BADGE — the details page's approved
//  EpisodeDownloadBadge visual, generalized for the player's four layouts.
//  A 32dp circle: the plain download glyph → the in-flight spinner → the
//  determinate ring + numeral → the resume glyph → the retry glyph → the
//  check. [translucent] switches the over-image language (the white-on-black
//  scrim treatment the GRID + BANNER plates use; the row styles read the
//  themed surface).
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PlayerEpisodeDownloadBadge(
    state: PlayerDownloadRenderState,
    actions: PlayerEpisodeDownloadActions,
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
) {
    val bg = if (translucent) Color.Black.copy(alpha = 0.45f)
    else MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
    val fg = if (translucent) Color.White else MaterialTheme.colorScheme.onSurface
    val accent = if (translucent) Color.White else MaterialTheme.colorScheme.primary
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable {
                // The settings preview passes previewTapAll — ONE tap target
                // for ALL states so the demo cycle can walk every state (the
                // D-557 pattern). Production call sites pass null → the
                // state contract below.
                val tap = actions.previewTapAll
                if (tap != null) {
                    tap()
                } else {
                    when (state) {
                        is PlayerDownloadRenderState.NotDownloaded -> actions.onDownload()
                        is PlayerDownloadRenderState.InFlight -> actions.onCancel()
                        is PlayerDownloadRenderState.Downloading -> actions.onPause()
                        is PlayerDownloadRenderState.Paused -> actions.onResume()
                        is PlayerDownloadRenderState.Error -> actions.onRetry()
                        is PlayerDownloadRenderState.Downloaded -> actions.onPlayDownloaded()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is PlayerDownloadRenderState.NotDownloaded -> Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Download",
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
            is PlayerDownloadRenderState.InFlight -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = accent,
            )
            is PlayerDownloadRenderState.Downloading -> Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { (state.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                    color = accent,
                    trackColor = fg.copy(alpha = 0.15f),
                )
                Text(
                    text = "${state.progress}",
                    fontFamily = RobotoFamily,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            is PlayerDownloadRenderState.Paused -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Resume download",
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            is PlayerDownloadRenderState.Error -> Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Retry download",
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            is PlayerDownloadRenderState.Downloaded -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Downloaded — tap to play",
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
    }
    // NOTE: onDelete has no gesture room in a 32dp badge — the downloads
    // page keeps that action (the D-555 plan §4, carried forward).
}

// ════════════════════════════════════════════════════════════════════════════
//  GRID — one cell of the two-across poster wall. ROUND 104: the top-left
//  EP badge is GONE ("remove them. It should only be kept in the detailed
//  view") — the number rides the bottom scrim's title line instead; the
//  cell long-presses to toggle watched (the details page's GRID parity).
//
//  ROUND 105 (WS-D) — THE REAL KNOBS: "in the grid layout there were not
//  much options there at all… for the watched episode, it only gives the
//  user one option, whether to dim the watched episodes or not. But it
//  should properly give the user one option, which is to show the
//  checkmark on the watched episodes or not… And there should be the same
//  thing for the currently playing episode too… between showing the play
//  button… or rather theme the… cover image… in the themed color":
//    • WATCHED = the CHECKMARK toggle (grayscale + check together — the
//      dim knob no longer applies to the GRID).
//    • CURRENT = PLAY (the disc) or TINT (the grayscale imagery under a
//      themed wash; the ring stays).
//    • The scrim gained the date/audio pills row (the same showDatePill
//      knob the other styles read — the doc-86 noted gap) and its title
//      line is toggleable (gridTitles — the clean image wall).
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun PlayerEpisodeGridCell(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onToggleWatched: (() -> Unit)? = null,
    arrivalPulse: Long = 0L,
    downloadActions: PlayerEpisodeDownloadActions? = null,
) {
    val isCurrent = data.isCurrent
    // ROUND 105: the watched treatment is the CHECKMARK knob (grayscale +
    // the centered check, one unit — the dim knob retired from the GRID);
    // the current episode's treatment always wins visually.
    val watchedGray = data.isWatched && display.gridWatchedCheckmark && !isCurrent
    val currentTinted = isCurrent && display.gridCurrentStyle == PlayerGridCurrentStyle.TINT
    val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    // ROUND 106 (WS-D): the audio pills respect their own knob.
    val pills = buildList {
        if (display.showDatePill && data.dateText != null) add(data.dateText)
        if (display.showAudioPills) {
            addAll(data.audioLabels)
            if (data.subDubLabel != null) add(data.subDubLabel)
            addAll(data.flavorLabels)
        }
    }

    // ── ROUND 106 (WS-D): THE REWORK — "I am not satisfied with the grid UI
    //    at all. Like the things are not managed properly. Like the date
    //    does not get shown properly, the audio versions do not get shown
    //    properly, and also the other details do not get shown properly."
    //    ROOT CAUSE: the old cell crammed EVERYTHING into a bottom scrim on
    //    a HALF-WIDTH image — one non-wrapping pills Row that clipped after
    //    ~2 pills ("Oct 12, 2025" alone ≈ 80dp of a ~170dp cell). THE NEW
    //    ANATOMY (the details page's approved grid, player-flavored): the
    //    image plate carries ONLY the over-image treatments (the current
    //    disc/tint + ring, the watched check, the download badge, the
    //    progress bar), and the TEXT lives BELOW in its own block — the
    //    themed "EP N" + the title on one line, then the chips in a
    //    WRAPPING FlowRow so EVERY tag shows ("all the tags are considered
    //    properly and handled properly"). ──
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleWatched ?: {},
            ),
    ) {
        // ── The image plate — pure imagery + the over-image treatments ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        ) {
            if (data.thumbnailUrl != null) {
                AsyncImage(
                    model = data.thumbnailUrl,
                    contentDescription = data.displayTitle,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (watchedGray || currentTinted) grayscale else null,
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

            // ── ROUND 105: the CURRENT-TINT wash — "if the user has selected
            //    theme, then the whole thumbnail image will be tinted": the
            //    (grayscaled) imagery under a themed wash; the ring stays. ──
            if (currentTinted) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)),
                )
            }

            // ── Watched: the centered check (the toggleable treatment). ──
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

            // ── Current: the centered play glyph (the PLAY style; TINT
            //    replaces it with the themed wash above). ──
            if (isCurrent && display.gridCurrentStyle == PlayerGridCurrentStyle.PLAY) {
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

            // ── ROUND 106 (WS-D): the download badge (top-end, translucent —
            //    the details grid's placement). ──
            if (display.showDownloadButton && data.downloadState != null &&
                downloadActions != null
            ) {
                PlayerEpisodeDownloadBadge(
                    state = data.downloadState,
                    actions = downloadActions,
                    translucent = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp),
                )
            }

            // ── The thin progress bar (partial watches). ──
            if (data.progressFraction > 0f && !data.isWatched && display.showProgressBar) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(data.progressFraction.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
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
        // ── The text block — the EP + title line (gridTitles), then the
        //    wrapping chips (every tag, never clipped). ──
        if (display.gridTitles || pills.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            if (display.gridTitles) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                ) {
                    Text(
                        text = "EP ${data.episodeNumberText}",
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = data.displayTitle,
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (pills.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 2.dp),
                ) {
                    pills.forEach { pill -> Pill(pill) }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  BANNER — the banner card (the thumbnail AS the card). ROUND 104: the
//  ghost episode number is TOGGLEABLE (showEpisodeNumber).
//
//  ROUND 105 (WS-D) — THE OVERHAUL the v1.1.61 round ordered:
//    • THE OVERLAY — "at the bottom left, the name of the current episode
//      should be shown, and above it the details should be shown, and also
//      the details need to be shown in a better-looking tag, just like how
//      they are being shown in the episode list vibe": the pills (the
//      shared Pill() vocabulary — the episode-list look) sit ABOVE the
//      episode NAME now.
//    • THE NUMBER — "there should be the option to select where the
//      episode number should show, whether it should show on the top right
//      side or on the top left side… in what format… solid themed color, or
//      frosted theme color? Because currently it is not showing in any of
//      those formats": the position knob (TOP_START / TOP_END) + the
//      treatment port — VERBATIM — from the details page's CINEMA card
//      (D-558/D-559): SOLID = the straight primary numeral with a soft dark
//      shadow; FROSTED = two stacked copies (a blurred primary halo on S+
//      under a crisp translucent primary copy). Both THEMED — the round-104
//      white ghost is gone.
//    • THE ASPECT IS FIXED at 16:9 — the size knob now scales the ITEM
//      (the dispatcher's centered width fraction), never the height alone.
// ════════════════════════════════════════════════════════════════════════════

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerEpisodeBannerCard(
    data: PlayerEpisodeRowData,
    display: PlayerEpisodeListDisplay,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    arrivalPulse: Long = 0L,
    downloadActions: PlayerEpisodeDownloadActions? = null,
) {
    val isCurrent = data.isCurrent
    val watchedGray = data.isWatched && display.dimWatched && !isCurrent
    val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    // ROUND 106 (WS-D): the BANNER's currently-playing treatment — the
    // GRID's knob, ported: "the grid view has the ability to select between
    // play button and the themed tint, but the banner does not have it. So
    // I want you to implement it there properly too."
    val currentTinted = isCurrent && display.bannerCurrentStyle == PlayerBannerCurrentStyle.TINT

    Box(
        modifier = modifier
            .fillMaxWidth()
            // ROUND 105: FIXED 16:9 — the size knob scales the item's width
            // (the dispatcher's centered fraction), never the aspect.
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
                colorFilter = if (watchedGray || currentTinted) grayscale else null,
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

        // ── ROUND 106 (WS-D): the CURRENT-TINT wash (the port) — the
        //    (grayscaled) imagery under a themed wash; the ring stays. ──
        if (currentTinted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)),
            )
        }

        // ── THE BIG EPISODE NUMBER — toggleable (ROUND 104), positioned
        //    (ROUND 105: top-left / top-right) and THEMED (ROUND 105:
        //    solid / frosted — the CINEMA port; the white ghost is gone). ──
        if (display.showEpisodeNumber) {
            val numberCorner = when (display.bannerNumberPosition) {
                PlayerBannerNumberPosition.TOP_START -> Alignment.TopStart
                PlayerBannerNumberPosition.TOP_END -> Alignment.TopEnd
            }
            if (display.bannerNumberStyle == PlayerBannerNumberStyle.FROSTED) {
                // FROSTED (the CINEMA D-559 treatment, verbatim) — the halo
                // copy behind (blurred on S+; a plain low-alpha under-copy
                // below S) + the crisp translucent copy on top: the frost
                // lives IN the glyphs; the imagery shows through.
                Box(
                    modifier = Modifier
                        .align(numberCorner)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = data.episodeNumberText,
                        fontFamily = RobotoFamily,
                        fontSize = 48.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            alpha = 0.45f
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                                renderEffect = BlurEffect(14f, 14f)
                            }
                        },
                    )
                    Text(
                        text = data.episodeNumberText,
                        fontFamily = RobotoFamily,
                        fontSize = 48.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
                        maxLines = 1,
                        softWrap = false,
                        style = TextStyle(
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.45f),
                                blurRadius = 14f,
                                offset = Offset(1f, 1f),
                            ),
                        ),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                // SOLID (the CINEMA D-558 treatment) — the straight themed
                // numeral with a soft dark shadow for legibility.
                Text(
                    text = data.episodeNumberText,
                    fontFamily = RobotoFamily,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    style = TextStyle(
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.55f),
                            blurRadius = 18f,
                            offset = Offset(2f, 2f),
                        ),
                    ),
                    modifier = Modifier
                        .align(numberCorner)
                        .padding(horizontal = 12.dp, vertical = 2.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        // ── Current: the centered play glyph (the PLAY style; the ported
        //    TINT replaces it with the themed wash above). ──
        if (isCurrent && display.bannerCurrentStyle == PlayerBannerCurrentStyle.PLAY) {
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

        // ── ROUND 106 (WS-D): the download badge — the top corner OPPOSITE
        //    the big number (never colliding with the number-position knob;
        //    top-end when the number is hidden or top-start). ──
        if (display.showDownloadButton && data.downloadState != null &&
            downloadActions != null
        ) {
            // The number hidden → the badge takes its natural top-end.
            val badgeCorner = when {
                !display.showEpisodeNumber -> Alignment.TopEnd
                display.bannerNumberPosition == PlayerBannerNumberPosition.TOP_START -> Alignment.TopEnd
                else -> Alignment.TopStart
            }
            PlayerEpisodeDownloadBadge(
                state = data.downloadState,
                actions = downloadActions,
                translucent = true,
                modifier = Modifier
                    .align(badgeCorner)
                    .padding(6.dp),
            )
        }

        // ── The bottom scrim (ROUND 105: INVERTED) — the DETAILS' pills
        //    ABOVE, the episode NAME at the bottom-left; the pills wear the
        //    shared Pill() vocabulary ("a better-looking tag, just like how
        //    they are being shown in the episode list vibe"). ──
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
                val chips = buildList {
                    if (display.showDatePill && data.dateText != null) add(data.dateText)
                    if (display.showAudioPills) {
                        addAll(data.audioLabels)
                        if (data.subDubLabel != null) add(data.subDubLabel)
                        addAll(data.flavorLabels)
                    }
                }
                // SA2-F4: the watched CHECK rides the chips flow's head —
                // the old top-start chip collided with the download badge
                // (and, pre-existing, with a top-start number); in the flow
                // it never collides with anything and reads with the facts.
                if (watchedGray || chips.isNotEmpty()) {
                    // ROUND 106 (WS-D): the chips WRAP — the tags are data,
                    // never clipped.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (watchedGray) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color.White.copy(alpha = 0.22f),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Watched",
                                    tint = Color.White,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp).size(12.dp),
                                )
                            }
                        }
                        chips.forEach { chip -> Pill(chip) }
                    }
                    Spacer(Modifier.height(5.dp))
                }
                // The NAME — at the very bottom ("at the bottom left, the
                // name of the current episode should be shown").
                Text(
                    text = data.displayTitle,
                    fontFamily = RobotoFamily,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
