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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
// ROUND 109 (D-719): the shared meta pieces — the GRID renders the details
// page's own chips (SUB primary / DUB tertiary / HSUB neutral capsules, the
// quiet date capsule) and the inset progress pill through these, so the two
// grids can never drift.
import com.confused.anikuta.core.designsystem.component.episodelist.EpisodeClassicRow
import com.confused.anikuta.core.designsystem.component.episodelist.EpisodeMetaLine
import com.confused.anikuta.core.designsystem.component.episodelist.EpisodeNumberLabelPlain
import com.confused.anikuta.core.designsystem.component.episodelist.EpisodeWatchProgressBar
import com.confused.anikuta.core.designsystem.component.episodelist.GridThumbnailCorner
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
//  • THE EP TAG IS GONE FROM THE ROW — round 111 (D-727): the classic
//    row IS the details page's CLASSIC row now, whose D-557 verdict
//    ("the tags … are shown on the top left corner of the thumbnail
//    images … the image gets covered way too much") killed the on-image
//    tag: the number lives in the quiet label line above the title. The
//    GRID's number sits in the cell's text block (UNDER_THUMB or
//    BESIDE_DETAILS); the TRACKLIST's number IS its hero; the BANNER keeps
//    its toggleable top-corner ghost numeral (a numeral, not a tag).
//
//  • THE DIM is real now — the v1.1.60 round: "apparently on the details
//    one, it does not have any functionality properly implemented for that,
//    for dimming the watched episodes." The CLASSIC row dims the WHOLE card
//    (the shared row's animated 0.5 alpha) + grayscales the thumbnail —
//    the details page's own treatment, verbatim.
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
//
//  ROUND 107 (D-712): THE ROBUST LAYOUT RULES — the layouts'
//    line-breaking contract + the simplified tag model (doc 89):
//
//  • THE LINE-BREAKING RULES — the GRID titles wrap to TWO lines (ellipsis
//    past the second); the CLASSIC row keeps the details page's own ONE-line
//    title (round 111: the shared row, verbatim); the TRACKLIST keeps its
//    one-line compact identity; the BANNER's scrim name keeps one line;
//    synopses run two lines; THE META LINE NEVER BREAKS (round 111, D-726:
//    the date + SUB/DUB render on ONE line — the space-constrained S/D/H
//    simplification is the meta line's own automatic ladder, so the
//    round-106 "tags are data" contract stands WITHOUT wrapping); and
//    NUMBERS NEVER BREAK — the GRID/BANNER glyphs carry the never-break
//    guard, the TRACKLIST hero's exact-fit column, the big BANNER numeral,
//    the CLASSIC row's number-disc fallback.
//
//  • THE TAG MODEL — [buildRowTags] is the ONE builder for every layout's
//    metadata pills (it replaced the four ad-hoc buildLists that
//    concatenated the labels RAW): deduped case-insensitively (a CS "Sub"
//    scanlator + a "SUB" flavor is ONE pill), normalized to the canonical
//    SUB/DUB/HSUB vocabulary (a containing label like "Kitauji Subs"
//    yields its token — the MPV parse's semantics, shared by the CS path
//    now), ordered date → SUB → DUB → HSUB → others (first-seen, original
//    casing), and NEVER CLIPPED.
// ════════════════════════════════════════════════════════════════════════════

/**
 * The player episode list's four layout paradigms. Stored as a string key in
 * [com.confused.anikuta.core.preferences.PlayerEpisodeListPreferences.rowStyle];
 * ALWAYS resolved through [fromKey] so a stored legacy or unknown value maps
 * to the style the renderer will actually draw (the D-529 lesson).
 *
 * ROUND 111 (D-727): DETAILED is renamed CLASSIC — the row now IS the
 * details page's Classic row (one shared [EpisodeClassicRow] anatomy,
 * "we should make both of them the similar naming, so that it's easier
 * for us"). The stored "DETAILED" strings (the old default included) parse
 * to CLASSIC through the lenient lookup below — no migration needed.
 */
enum class PlayerEpisodeListStyle {
    CLASSIC,
    TRACKLIST,
    GRID,
    BANNER;

    companion object {
        /**
         * The lenient lookup: the retired round-101/103 density keys
         * ("COMPACT", "MINIMAL" — the variations the v1.1.60 round retired)
         * fold into TRACKLIST (the slot's from-scratch replacement); the
         * retired "DETAILED" name (round-111's rename) folds into CLASSIC;
         * unknown, null or blank → CLASSIC (the player list's default look).
         */
        fun fromKey(key: String?): PlayerEpisodeListStyle = when (key?.trim()?.uppercase()) {
            "TRACKLIST", "COMPACT", "MINIMAL" -> TRACKLIST
            "GRID" -> GRID
            "BANNER" -> BANNER
            else -> CLASSIC
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
    /**
     * ROUND 109 (D-719): the SHORT release date ("Oct 12") — the GRID chip's
     * text, the details grid's own shape (the v1.1.65 round: the two grids
     * must read identically — the rows keep the long date, exactly like the
     * details page's classic-vs-grid split).
     */
    val dateTextShort: String? = null,
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
 *
 * ROUND 108 (D-713): [onDelete] joins the bag — the badge's Downloaded menu
 * offers Play / Delete now (the v1.1.64 device round: the compact layouts
 * "do not give the option to select what it should do"), so the player
 * pages can delete a download in place instead of dead-ending at play.
 */
data class PlayerEpisodeDownloadActions(
    val onDownload: () -> Unit,
    val onPause: () -> Unit,
    val onResume: () -> Unit,
    val onCancel: () -> Unit,
    val onRetry: () -> Unit,
    val onPlayDownloaded: () -> Unit,
    val onDelete: () -> Unit = {},
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
    /** ROUND 105: the thin watch-progress bar/underline (CLASSIC + TRACKLIST
     *  + GRID + BANNER — ROUND 108 widened it to the banner, the CINEMA
     *  parity). */
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
    /** ROUND 108 (D-713): the BANNER's watched check mark — the details
     * CINEMA's cinemaWatchedCheckBadge twin (default OFF; the dim/grayscale
     * treatment stays owned by [dimWatched]). */
    val bannerWatchedCheck: Boolean = false,
    /** ROUND 105: the GRID's watched treatment (the checkmark, not the dim). */
    val gridWatchedCheckmark: Boolean = true,
    /** ROUND 105: the GRID's currently-playing treatment. */
    val gridCurrentStyle: PlayerGridCurrentStyle = PlayerGridCurrentStyle.PLAY,
    /**
     * ROUND 108 (D-713): the GRID's title-line mode — OFF / ONE_LINE /
     * TWO_LINES (replaces the round-105 Boolean; the details grid's new knob,
     * shared). The title line only ever renders a REAL English-readable
     * title — [com.confused.anikuta.core.common.gridShowableTitle] gates it
     * ("if the name is not available in English, or it only shows the
     * episode number or such, then it will not be shown").
     */
    val gridTitleMode: com.confused.anikuta.core.common.GridTitleMode =
        com.confused.anikuta.core.common.GridTitleMode.TWO_LINES,
    /**
     * ROUND 110 (D-723): the GRID's number-label placement — UNDER_THUMB
     * (the default — the label's own line under the plate) or BESIDE_DETAILS
     * (the label rides the title's line). The details grid's new knob,
     * shared.
     */
    val gridNumberPosition: com.confused.anikuta.core.common.GridNumberPosition =
        com.confused.anikuta.core.common.GridNumberPosition.UNDER_THUMB,
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

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 107 (WS-2): THE TAG MODEL — ONE builder for every layout's
//  metadata pills ("simplifying the tags"). It replaced the four ad-hoc
//  buildLists that concatenated audioLabels + subDubLabel + flavorLabels
//  RAW: a CS row whose scanlator said "Sub" while its flavors said "SUB"
//  rendered the SAME fact twice, in different casing, in no guaranteed
//  order. The rules (doc 89 §2):
//    R-T1 DEDUPE — case-insensitive; one fact, one pill.
//    R-T2 NORMALIZE — the SUB/DUB/HSUB vocabulary renders canonically;
//      a label that CONTAINS a token ("Kitauji Subs") yields that token
//      (the MPV parse's semantics, shared by the CS scanlator path now).
//    R-T3 ORDER — the date leads, then SUB → DUB → HSUB, then the other
//      labels (first-seen, original casing — no data loss).
//    R-T4 NEVER CLIPPED — the builder returns every tag; the META LINE
//      renders them all on ONE line (round 111: the density ladder
//      simplifies BEFORE anything would ever clip or wrap).
// ════════════════════════════════════════════════════════════════════════════

/** The canonical audio-tag order (the R-T3 sequence). */
private val AUDIO_TOKEN_ORDER = listOf("SUB", "DUB", "HSUB")

/**
 * The row's metadata pills under the tag model. [dateText] is the
 * ALREADY-KNOB-GATED date (null or blank = hidden); [showAudio] gates the
 * audio vocabulary AND the other labels (the shared audio-pills knob — a
 * row with the knob off shows the date alone, never a stranded provider
 * label).
 */
private fun buildRowTags(
    dateText: String?,
    showAudio: Boolean,
    audioLabels: List<String>,
    subDubLabel: String?,
    flavorLabels: List<String>,
): List<String> {
    // SA1-F4 (round-107 audit): a blank date renders no pill — the same
    // guard the labels get (the render-only contract stays robust against
    // any caller).
    val date = dateText?.takeIf { it.isNotBlank() }
    if (!showAudio) return listOfNotNull(date)
    val audio = mutableSetOf<String>()
    val others = LinkedHashMap<String, String>()
    fun classify(raw: String) {
        val label = raw.trim()
        if (label.isEmpty()) return
        val upper = label.uppercase()
        // SA2-F1 (round-107 audit): the MPV parse's EXACT token semantics
        // (WatchScreen's parseAudioAvailability) — HSUB/HARDSUB wins; SUB
        // and DUB collect INDEPENDENTLY, so a merged label like "Sub/Dub"
        // yields BOTH tokens, exactly as the MPV haystack parse would.
        val hsub = upper.contains("HSUB") || upper.contains("HARDSUB")
        val sub = upper.contains("SUB") && !hsub
        val dub = upper.contains("DUB") && !hsub
        if (hsub) audio += "HSUB"
        if (sub) audio += "SUB"
        if (dub) audio += "DUB"
        if (!hsub && !sub && !dub) others.getOrPut(upper) { label }
    }
    audioLabels.forEach { label -> classify(label) }
    subDubLabel?.let { label -> classify(label) }
    flavorLabels.forEach { label -> classify(label) }
    return buildList {
        if (date != null) add(date)
        AUDIO_TOKEN_ORDER.forEach { token -> if (token in audio) add(token) }
        others.values.forEach { label -> add(label) }
    }
}

/**
 * THE DISPATCHER — one episode entry in whichever of the four paradigms
 * [display.style] selects. Row styles (CLASSIC/TRACKLIST) and the BANNER
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
        PlayerEpisodeListStyle.CLASSIC -> SwipeableEntry(
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
                    // ROUND 110 (D-724): the CINEMA's 16dp corner.
                    backgroundShape = RoundedCornerShape(16.dp),
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
//  CLASSIC (round 111, D-727 — born DETAILED, renamed to the shared
//  vocabulary) — THE DETAILS PAGE'S OWN CLASSIC ROW, VERBATIM: the player
//  renders through the same shared EpisodeClassicRow the details page
//  renders through ("utilize the exact same classic layout of the details
//  page on the player page too. So make sure that this is handled properly
//  and make sure to link them together properly"). The imagery-only
//  thumbnail (no EP overlay — the number lives in the quiet label line),
//  the 40dp number-disc fallback, the one-line title, the META LINE (one
//  line, never a break — D-726) with the download badge in the classic
//  slots, the synopsis plate, the inset watch pill, the full-width download
//  bar, and the whole-card animated watched fade. The player's own chrome
//  rides the shared row's parameters: the current-episode tint + ring and
//  the arrival-pulse overlay.
// ════════════════════════════════════════════════════════════════════════════

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
    val dimmed = data.isWatched && display.dimWatched && !isCurrent
    val description = if (display.showSynopsis) data.synopsis?.takeIf { it.isNotBlank() } else null
    val dateText = if (display.showDatePill) data.dateText else null
    val dateChip = dateText?.takeIf { it.isNotBlank() }
    val audioTags = buildRowTags(
        dateText = null,
        showAudio = display.showAudioPills,
        audioLabels = data.audioLabels,
        subDubLabel = data.subDubLabel,
        flavorLabels = data.flavorLabels,
    )
    // ROUND 106 (WS-D): the download badge's gate — the toggle + the row's
    // state + the wired actions together decide it.
    val showDownloadBadge = display.showDownloadButton &&
        data.downloadState != null && downloadActions != null
    EpisodeClassicRow(
        thumbnailUrl = data.thumbnailUrl,
        fallbackNumberText = data.episodeNumberText,
        numberLabel = {
            EpisodeNumberLabelPlain(
                text = "EP ${data.episodeNumberText}",
                modifier = Modifier.padding(bottom = 3.dp),
            )
        },
        displayTitle = data.displayTitle,
        dateText = dateChip,
        audioTags = audioTags,
        synopsis = description,
        pillsRowVisible = dateChip != null || audioTags.isNotEmpty() ||
            (showDownloadBadge && description == null),
        showWatchProgress = display.showProgressBar,
        progressFraction = data.progressFraction,
        downloadControl = {
            if (data.downloadState != null && downloadActions != null) {
                PlayerEpisodeDownloadBadge(
                    state = data.downloadState,
                    actions = downloadActions,
                )
            }
        },
        showDownloadControl = showDownloadBadge,
        downloadProgress = (data.downloadState as? PlayerDownloadRenderState.Downloading)
            ?.progress?.div(100f),
        isWatched = data.isWatched,
        dimWatched = dimmed,
        isCurrent = isCurrent,
        onClick = onClick,
        modifier = modifier,
        overlay = {
            ArrivalPulseOverlay(
                rememberArrivalPulseAlpha(arrivalPulse, data.isCurrent),
                RoundedCornerShape(12.dp),
            )
        },
    )
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
    // ROUND 110 (D-722): THE ROWS' SHARED CAPSULES — the TRACKLIST's pills
    // render the details page's own vocabulary too (the quiet date capsule
    // + the type-coded audio capsules); the old flat-gray Pill() is retired
    // with this, its last caller.
    val audioTags = buildRowTags(
        dateText = null,
        showAudio = display.showAudioPills,
        audioLabels = data.audioLabels,
        subDubLabel = data.subDubLabel,
        flavorLabels = data.flavorLabels,
    )
    val dateChip = dateText?.takeIf { it.isNotBlank() }
    // ROUND 105: the optional synopsis ("there was no option to turn on or
    // show the synopsis or turn off the synopsis. So I need a toggle… in
    // this area") — the same knob the CLASSIC row reads.
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
                        if (dateChip != null || audioTags.isNotEmpty()) {
                            // ROUND 111 (D-726): the META LINE — the date +
                            // the type-coded audio capsules in ONE line
                            // that never breaks (the round-111 rule; the
                            // ladder's S/D/H rung guards the tight cases).
                            EpisodeMetaLine(
                                dateText = dateChip,
                                audioTags = audioTags,
                                modifier = Modifier.fillMaxWidth(),
                            )
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
                    // ── The trailing state glyph — a watched row's quiet
                    //    check. ROUND 111 (D-728): the current row's PLAY
                    //    glyph is GONE — the v1.1.67 device round: "it
                    //    should not show the play button on the currently
                    //    playing episode … It should not show that play
                    //    button at all times." The primary border + the
                    //    tinted surface already carry "playing"; the
                    //    glyph's spot was pure redundancy. ──
                    if (data.isWatched) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Watched",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                // ── The thin watch-progress underline — partial watches only
                //    (ROUND 105: toggleable with the CLASSIC row's knob). ──
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
    // ROUND 108: the options-menu anchor state (Downloaded → Play/Delete;
    // Downloading → Pause/Cancel — the details control's contract, ported).
    var showMenu by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
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
                            // ROUND 108: the two menu states — the tap opens the
                            // options instead of firing the first action blind.
                            is PlayerDownloadRenderState.Downloading -> showMenu = true
                            is PlayerDownloadRenderState.Paused -> actions.onResume()
                            is PlayerDownloadRenderState.Error -> actions.onRetry()
                            is PlayerDownloadRenderState.Downloaded -> showMenu = true
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
                    contentDescription = "Downloaded — tap for options",
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // ROUND 108: THE OPTIONS MENUS — the details badge's exact dropdowns
        // (the classic control's contract): Downloaded → Play / Delete;
        // Downloading → Pause / Cancel. The player pages could only PLAY a
        // finished download before — the delete now lives one tap away, in
        // place, on every layout.
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
        ) {
            if (state is PlayerDownloadRenderState.Downloading) {
                DropdownMenuItem(
                    text = { Text("Pause", fontFamily = RobotoFamily) },
                    onClick = {
                        showMenu = false
                        actions.onPause()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "Cancel",
                            fontFamily = RobotoFamily,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        showMenu = false
                        actions.onCancel()
                    },
                )
            } else if (state is PlayerDownloadRenderState.Downloaded) {
                DropdownMenuItem(
                    text = { Text("Play", fontFamily = RobotoFamily) },
                    onClick = {
                        showMenu = false
                        actions.onPlayDownloaded()
                    },
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            "Delete",
                            fontFamily = RobotoFamily,
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = {
                        showMenu = false
                        actions.onDelete()
                    },
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  GRID — one cell of the two-across poster wall. ROUND 104: the top-left
//  EP badge is GONE ("remove them. It should only be kept in the detailed
//  view") — ROUND 108: the number rides its OWN themed label line under the
//  plate (the details grid's anatomy); the cell long-presses to toggle
//  watched (the details page's GRID parity).
//
//  ROUND 105 (WS-D) — the real knobs arrived (the checkmark toggle, the
//  current-episode PLAY/TINT choice).
//
//  ROUND 109 (D-719/D-720) — THE DETAILS GRID, VERBATIM: the v1.1.65 device
//  round ordered the two grids to "look and feel exactly the same": the
//  16dp PURE plate (no background box), the 22sp quiet number tile, the
//  DECOUPLED watched treatment (the DIM knob owns grayscale + the 0.32
//  overlay; the CHECK knob owns ONLY the bottom-start check bubble — "if I
//  remove the checkmark, then the grayness of it also gets removed, which
//  is not good" is fixed), the inset shared progress pills (the full-bleed
//  3dp edge bars retired), the 13sp/16sp title with the watched 0.55 dim,
//  and the shared chips (the color-coded SUB/DUB capsules + the SHORT-date
//  capsule — the details page's own components, one implementation).
//  CURRENT keeps the player's own treatment (the PLAY disc or the TINT
//  wash + the ring + the arrival pulse).
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
    downloadActions: PlayerEpisodeDownloadActions? = null,
) {
    val isCurrent = data.isCurrent
    // ── ROUND 109 (D-720): THE CHECKMARK DECOUPLING — the v1.1.65 device
    //    round: "there is no option to remove the checkmark from the
    //    watched episodes. If I remove the checkmark, then the grayness of
    //    it also gets removed, which is not good." The round-105 coupling
    //    (one knob = grayscale + check together) is DEAD: the DIM knob
    //    (dimWatched, default on — the same knob the other three styles
    //    read) owns the grayscale + the dim overlay, and the CHECK knob
    //    (gridWatchedCheckmark) owns ONLY the check bubble. Removing the
    //    checkmark keeps the grayness; turning the dim off keeps the check.
    //    The current episode's treatment always wins visually. ──
    val watchedDim = data.isWatched && display.dimWatched && !isCurrent
    val watchedCheck = data.isWatched && display.gridWatchedCheckmark && !isCurrent
    val currentTinted = isCurrent && display.gridCurrentStyle == PlayerGridCurrentStyle.TINT
    val grayscale = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
    // ── ROUND 109 (D-719): THE GRID'S TRUE PARITY — the tag model still
    //    builds the AUDIO pills (deduped, normalized, ordered — the date is
    //    rendered SEPARATELY now, as the details grid's own quiet date
    //    capsule carrying the SHORT date shape). ──
    val audioPills = buildRowTags(
        dateText = null,
        showAudio = display.showAudioPills,
        audioLabels = data.audioLabels,
        subDubLabel = data.subDubLabel,
        flavorLabels = data.flavorLabels,
    )
    val dateChipText = if (display.showDatePill) {
        data.dateTextShort?.takeIf { it.isNotBlank() }
    } else {
        null
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
    //    themed "EP N" + the title on one line, then the single-line meta
    //    (D-726 — the S/D ladder keeps every core tag visible without a
    //    wrap). ──
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onToggleWatched ?: {},
            ),
    ) {
        // ── The image plate — the details grid's PURE plate (ROUND 109:
        //    no background box, nothing covering the imagery except the
        //    treatments + the badge + the inset progress pill; ROUND 110:
        //    the corner rides the shared GridThumbnailCorner — 12dp, ONE
        //    constant both grids clip through). ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(GridThumbnailCorner))
                .then(
                    if (isCurrent) {
                        Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(GridThumbnailCorner),
                        )
                    } else {
                        Modifier
                    },
                ),
        ) {
            if (data.thumbnailUrl != null) {
                AsyncImage(
                    model = data.thumbnailUrl,
                    contentDescription = data.displayTitle,
                    contentScale = ContentScale.Crop,
                    colorFilter = if (watchedDim || currentTinted) grayscale else null,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // The bare plate — the details grid's quiet number tile (no
                // fake imagery, no pill; ROUND 107: numbers never break).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = data.episodeNumberText,
                        fontFamily = RobotoFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        maxLines = 1,
                        softWrap = false,
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

            // ── ROUND 109 (D-719/D-720): the watched treatment — the details
            //    grid's exact look, DECOUPLED: the dim overlay rides the DIM
            //    knob (black 0.32, the details grid's own wash)… ──
            if (watchedDim) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.32f)),
                )
            }
            // ── …and the quiet check bubble rides the CHECK knob — the
            //    details grid's bottom-start bubble (the round-105 centered
            //    34dp disc retired; the bubble keeps the meta legible). ──
            if (watchedCheck) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Watched",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(16.dp),
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
            //    the details grid's placement + its 6dp inset, ROUND 109). ──
            if (display.showDownloadButton && data.downloadState != null &&
                downloadActions != null
            ) {
                PlayerEpisodeDownloadBadge(
                    state = data.downloadState,
                    actions = downloadActions,
                    translucent = true,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }

            // ── ROUND 109 (D-719): THE INSET PROGRESS PILLS — the details
            //    grid's own language (the full-bleed 3dp edge bars retired):
            //    while a download runs, the DETERMINATE tertiary pill takes
            //    the watch pill's spot; otherwise the thin primary watch pill
            //    renders under the showProgressBar knob. ──
            val downloading = data.downloadState is PlayerDownloadRenderState.Downloading
            if (downloading) {
                val progress = (data.downloadState as PlayerDownloadRenderState.Downloading).progress
                EpisodeWatchProgressBar(
                    fraction = progress / 100f,
                    progressColor = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                )
            } else if (data.progressFraction > 0f && !data.isWatched && display.showProgressBar) {
                EpisodeWatchProgressBar(
                    fraction = data.progressFraction,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 9.dp, vertical = 8.dp),
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
        // ── ROUND 110 (D-723): THE TEXT BLOCK — the number-position knob
        //    (the details grid's new arrangement, shared): UNDER_THUMB keeps
        //    today's anatomy (the themed "EP N" line always present, the
        //    gated mode-aware title under it), BESIDE_DETAILS rides the
        //    number label ON the title's line (the compact arrangement the
        //    pre-108 player grid carried). The single-line meta (D-726)
        //    follows below either way — one line, never a break. ──
        val titleLine = if (display.gridTitleMode ==
            com.confused.anikuta.core.common.GridTitleMode.OFF
        ) {
            null
        } else {
            com.confused.anikuta.core.common.gridShowableTitle(data.displayTitle)
        }
        Spacer(Modifier.height(7.dp))
        PlayerGridTitleLine(
            episodeNumberText = data.episodeNumberText,
            titleLine = titleLine,
            gridTitleMode = display.gridTitleMode,
            gridNumberPosition = display.gridNumberPosition,
            dimmed = watchedDim,
        )
        PlayerGridChipsFlow(
            dateChipText = dateChipText,
            audioTags = audioPills,
        )
    }
}

/**
 * ROUND 110 (D-723): the player GRID's title-line arrangements — the
 * details grid's own number-position knob, mirrored. UNDER_THUMB (the
 * default): the themed "EP N" label on its OWN line, the gated mode-aware
 * title under it (today's anatomy). BESIDE_DETAILS: the label rides the
 * title's line — the number in its own primary ExtraBold typography
 * prefixing the title (with the title gated out, the label stands alone).
 */
@Composable
private fun PlayerGridTitleLine(
    episodeNumberText: String,
    titleLine: String?,
    gridTitleMode: com.confused.anikuta.core.common.GridTitleMode,
    gridNumberPosition: com.confused.anikuta.core.common.GridNumberPosition,
    dimmed: Boolean,
) {
    if (gridNumberPosition ==
        com.confused.anikuta.core.common.GridNumberPosition.BESIDE_DETAILS
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
        ) {
            Text(
                text = "EP $episodeNumberText",
                fontFamily = RobotoFamily,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(top = 2.dp),
            )
            if (titleLine != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = titleLine,
                    fontFamily = RobotoFamily,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                        .let { if (dimmed) it.copy(alpha = 0.55f) else it },
                    maxLines = if (gridTitleMode ==
                        com.confused.anikuta.core.common.GridTitleMode.ONE_LINE
                    ) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    } else {
        // UNDER_THUMB — today's anatomy, verbatim.
        Text(
            text = "EP $episodeNumberText",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (titleLine != null) {
            Spacer(Modifier.height(1.dp))
            Text(
                text = titleLine,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
                    .let { if (dimmed) it.copy(alpha = 0.55f) else it },
                maxLines = if (gridTitleMode ==
                    com.confused.anikuta.core.common.GridTitleMode.ONE_LINE
                ) 1 else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

/**
 * ROUND 110 (D-723): the player GRID's chips flow — the details grid's own
 * (4,4) rhythm through the shared components. ROUND 111 (D-726): the
 * block renders the shared [EpisodeMetaLine] — the date capsule (the SHORT
 * date) + the type-coded audio capsules in ONE line that never breaks,
 * with the automatic S/D/H simplification tuned to the half-width cell.
 * Extracted so the cell's two arrangements (UNDER_THUMB / BESIDE_DETAILS)
 * share ONE chips block.
 */
@Composable
private fun PlayerGridChipsFlow(
    dateChipText: String?,
    audioTags: List<String>,
) {
    if (dateChipText == null && audioTags.isEmpty()) return
    Spacer(Modifier.height(5.dp))
    EpisodeMetaLine(
        dateText = dateChipText,
        audioTags = audioTags,
        spacing = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp),
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  BANNER — the banner card (the thumbnail AS the card). ROUND 104: the
//  ghost episode number is TOGGLEABLE (showEpisodeNumber). ROUND 105: the
//  position + style knobs + the size scale; ROUND 106: the current-treatment
//  port + the download badge; ROUND 108: the two-line name + the progress
//  bars + the watched-check toggle.
//
//  ROUND 110 (D-724) — THE CINEMA, VERBATIM: the v1.1.66 device round's
//  parity verdict ("the tags are most definitely not good in the player
//  page") closed by porting the details page's EpisodeCinemaCard anatomy
//  WHOLESALE: the 16dp corners on a plain surfaceVariant plate, the
//  THREE-STOP scrim (transparent → 0.30 @ 45% → 0.85), the black-0.30
//  watched overlay with the optional centered check in the CINEMA's own
//  z-order (UNDER the ghost number), the ZERO-PADDED ghost number on all
//  three number texts + the bare plate, the bottom-left META COLUMN (the
//  16sp two-line name + ONE joined "date · audio · WATCHED" pill in the
//  White-0.18 capsule — the old flat-gray pills FlowRow is gone), the
//  download badge at BottomEnd 12dp, and the INSET progress pills with the
//  CINEMA's showDownloadButton gating (SA2-F8 closed). The banner's own
//  knobs (the number toggle/position/style, the size scale, the current
//  PLAY/TINT treatment, the watched-check toggle) all stand.
// ════════════════════════════════════════════════════════════════════════════

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
    // GRID's knob, ported (the player's own; the CINEMA has no current
    // concept to port).
    val currentTinted = isCurrent && display.bannerCurrentStyle == PlayerBannerCurrentStyle.TINT
    // ROUND 110 (D-724): the ZERO-PADDED ghost number — the details CINEMA's
    // own glyph shape ("01"…"99", 100+ honest), on every number text below.
    val ghostNumber = bannerGhostNumber(data.episodeNumberText)

    Box(
        modifier = modifier
            .fillMaxWidth()
            // ROUND 105: FIXED 16:9 — the size knob scales the item's width
            // (the dispatcher's centered fraction), never the aspect.
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp))
            .then(
                if (isCurrent) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        RoundedCornerShape(16.dp),
                    )
                } else {
                    Modifier
                },
            )
            // D-724: the CINEMA's plain surfaceVariant plate (the old 0.4
            // alpha wash is gone — the CINEMA background, verbatim).
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        // The imagery IS the card.
        if (data.thumbnailUrl != null) {
            AsyncImage(
                model = data.thumbnailUrl,
                contentDescription = data.displayTitle,
                contentScale = ContentScale.Crop,
                colorFilter = if (watchedGray || currentTinted) grayscale else null,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // The bare plate — the CINEMA's ghost numeral (zero-padded, on
            // the quiet outlineVariant wash; the old "EP N" 30sp tag is
            // gone).
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ghostNumber,
                    fontFamily = RobotoFamily,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    // ROUND 107: numbers never break.
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        // ── ROUND 106 (WS-D): the CURRENT-TINT wash (the player's own
        //    current treatment — over the imagery, under the scrim). ──
        if (currentTinted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.50f)),
            )
        }

        // ── The bottom scrim — the CINEMA's THREE-STOP gradient (the
        //    overlaid text's contrast guarantee; the old 2-stop 0.78 wash
        //    is gone). ──
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Color.Black.copy(alpha = 0.30f),
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
        )

        // ── The watched treatment — the CINEMA's z-order and look: the
        //    black-0.30 overlay UNDER the ghost number, with the optional
        //    centered circular check riding the bannerWatchedCheck knob
        //    (the dim/grayscale itself rides dimWatched as always — the
        //    D-720 decoupling). ──
        if (watchedGray) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.30f)),
                contentAlignment = Alignment.Center,
            ) {
                if (display.bannerWatchedCheck) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Watched",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
        }

        // ── THE GHOST NUMBER — toggleable (ROUND 104), positioned + THEMED
        //    (ROUND 105: the CINEMA D-558/D-559 ports), and now ZERO-PADDED
        //    (ROUND 110: the CINEMA's own glyph shape on all three texts). ──
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
                        text = ghostNumber,
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
                        text = ghostNumber,
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
                    text = ghostNumber,
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

        // ── Current: the centered play glyph (the player's own PLAY
        //    treatment; the ported TINT replaces it with the wash above). ──
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

        // ── ROUND 110 (D-724): the download overlay — the CINEMA's
        //    BottomEnd placement (12dp inset; the old
        //    opposite-the-number corner logic is gone — the meta column
        //    owns the bottom-start, so the badge never collides). ──
        if (display.showDownloadButton && data.downloadState != null &&
            downloadActions != null
        ) {
            PlayerEpisodeDownloadBadge(
                state = data.downloadState,
                actions = downloadActions,
                translucent = true,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
            )
        }

        // ── The overlaid meta — the CINEMA's bottom-left column: the name
        //    + ONE joined translucent pill ("date · audio · WATCHED"). ──
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
        ) {
            Text(
                text = data.displayTitle,
                fontFamily = RobotoFamily,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // ROUND 110 (D-722/D-724): the joined pill — the shared TAG
            // MODEL's facts (the LONG date, the normalized audio tokens,
            // the other labels) + the WATCHED fact, folded into ONE
            // White-0.18 capsule exactly like the CINEMA's meta line. The
            // WATCHED fact rides isWatched && !isCurrent — the D-720
            // decoupling: it is a FACT, not the dim treatment (a dim-off
            // watched banner still says WATCHED; the current episode never
            // does).
            val metaChips = buildRowTags(
                dateText = if (display.showDatePill) data.dateText else null,
                showAudio = display.showAudioPills,
                audioLabels = data.audioLabels,
                subDubLabel = data.subDubLabel,
                flavorLabels = data.flavorLabels,
            ).toMutableList()
            if (data.isWatched && !isCurrent) metaChips.add("WATCHED")
            if (metaChips.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White.copy(alpha = 0.18f),
                ) {
                    Text(
                        text = metaChips.joinToString("  ·  "),
                        fontFamily = RobotoFamily,
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

        // ── ROUND 110 (D-724): the progress pills — the CINEMA's INSET
        //    treatment (12/8dp, the shared EpisodeWatchProgressBar; the old
        //    full-bleed 3dp edge bars are gone) with the CINEMA's
        //    showDownloadButton gating (SA2-F8 closed: the knob gates the
        //    download bar too now — the badge and its bar live or die
        //    together). ──
        val downloading = data.downloadState is PlayerDownloadRenderState.Downloading
        if (display.showDownloadButton && downloading) {
            val progress = (data.downloadState as PlayerDownloadRenderState.Downloading).progress
            EpisodeWatchProgressBar(
                fraction = progress / 100f,
                progressColor = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        } else if (display.showProgressBar && data.progressFraction > 0f && !data.isWatched) {
            EpisodeWatchProgressBar(
                fraction = data.progressFraction,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * ROUND 110 (D-724): the banner's ghost number — the details CINEMA's
 * zero-padded glyph shape ("01"…"99", 100+ honest), derived from the
 * pre-formatted number text (a "5.5" keeps its fraction).
 */
private fun bannerGhostNumber(numberText: String): String {
    val dot = numberText.indexOf('.')
    val intPart = if (dot >= 0) numberText.substring(0, dot) else numberText
    val fraction = if (dot >= 0) numberText.substring(dot) else ""
    val n = intPart.toIntOrNull() ?: return numberText
    val padded = if (n in 0..99) {
        String.format(java.util.Locale.US, "%02d", n)
    } else {
        n.toString()
    }
    return padded + fraction
}
