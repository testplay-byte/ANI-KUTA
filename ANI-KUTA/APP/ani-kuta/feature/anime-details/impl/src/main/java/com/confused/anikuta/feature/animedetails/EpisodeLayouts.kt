package com.confused.anikuta.feature.animedetails

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.LocalCardHeadingColor
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.launch

/**
 * D-555: the THREE non-classic episode-list layouts — GRID (a two-column
 * poster wall), TIMELINE (an air-date schedule spine) and CINEMA (full-bleed
 * banner cards). The user's redesign verdict on the D-554 first draft was
 * explicit: each option must be "a completely different UI design, a
 * completely different layout, a completely different management system …
 * rather than just hiding some features like synopsis or maybe adjusting
 * the shape a little bit" — so these are DIFFERENT STRUCTURES, not row
 * variations, and none of them renders a synopsis (that is their identity).
 *
 * Every renderer takes the SAME resolved [EpisodeDisplayData] (the one
 * rememberEpisodeDisplayData pass — the D-306/D-230 rules live in ONE place)
 * and the SAME [EpisodeRowActions], and is reached ONLY through the
 * EpisodeListEntry dispatcher — the real list and the settings page's live
 * preview both call the dispatcher, so what you tune is what you get.
 *
 * Element-toggle semantics per layout (a layout that never renders a section
 * cannot be talked into rendering it — the D-554 truthfulness rule):
 * - showDatePill → the GRID chip, the TIMELINE node label (off → "EP n"
 *     nodes), the CINEMA scrim chip.
 * - showAudioPills → every layout's availability chips.
 * - showWatchProgress → the bar on the imagery's bottom edge (TIMELINE: on
 *     its card).
 * - dimWatched → grayscale/dim on the imagery + the watched badge/node.
 * - showDownloadControl → the compact [EpisodeDownloadBadge] (the CINEMA
 *     overlay renders it translucent over the image).
 *
 * D-556 (the v1.1.29 device round) refined all three: GRID was RECREATED
 * (title over the image on a scrim, ringed watched check, capsule-chip meta
 * line, hairline border), CINEMA's ghost number became a themed EP badge
 * (the details page's own primary/onPrimary — "the episode numbers should
 * actually be in the theme color"), TIMELINE's date node gained the BLOB
 * merge into its card (same-color organic union — the offset stays, the
 * user explicitly wanted it incorporated, not corrected), and the date/audio
 * TAGS were redesigned as the type-coded capsule chips
 * ([EpisodeDateChip]/[EpisodeAudioChip]) shared by CLASSIC + GRID.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Pure helpers (unit-locked in EpisodeListStyleTest — no Compose needed).
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-556: the shared meta chips — the date capsule + the per-type audio
 * capsules. The v1.1.29 device round: the episode tags "look way too bad …
 * they do not look good in our UI" (the old single outlineVariant surface
 * with dot-separated labels). The redesign: small pill CAPSULES with
 * type-coded colors — the date rides a quiet surface capsule, SUB is
 * primary-tinted, DUB tertiary-tinted, HSUB outline-tinted — so availability
 * reads at a glance and the chips match the app's design language instead of
 * fighting it. Shared by CLASSIC + GRID (the two layouts the user flagged).
 */
@Composable
internal fun EpisodeDateChip(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier,
    ) {
        Text(
            text = text,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
internal fun EpisodeAudioChip(label: String, modifier: Modifier = Modifier) {
    val accent = when (label.trim().uppercase(java.util.Locale.US)) {
        "SUB" -> MaterialTheme.colorScheme.primary
        "DUB" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant // HSUB + unknowns stay neutral
    }
    val container = when (label.trim().uppercase(java.util.Locale.US)) {
        "HSUB" -> MaterialTheme.colorScheme.outlineVariant
        else -> accent
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container.copy(alpha = 0.16f),
        modifier = modifier,
    ) {
        Text(
            text = label,
            fontFamily = RobotoFamily,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp,
            color = accent,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * D-557: the shared EPISODE NUMBER label — the number OFF the imagery. The
 * v1.1.30 device round: the tags "are shown on the top left corner of the
 * image … the image gets covered way too much, the UI looks bad" (CLASSIC's
 * pill + GRID's pill on the thumbnail corner). The label now sits in the
 * text block above the title: a quiet themed mini-label ("EP 5" in the
 * details page's primary) with the D-317 compound "S-n/E-m" two-shade
 * rendering preserved verbatim. Shared by CLASSIC + GRID.
 */
@Composable
internal fun EpisodeNumberLabel(
    episodeTag: EpisodeTag?,
    epNumText: String,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    if (episodeTag != null && episodeTag.season != null) {
        // D-317: the compound tag — season + episode in two shades of the
        // theme color with the dimmed slash separator (verbatim from the
        // old on-image pill).
        val compoundTag = androidx.compose.ui.text.buildAnnotatedString {
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = accent.copy(alpha = 0.62f),
                    fontWeight = FontWeight.Bold,
                ),
            ) { append("S-${episodeTag.season}") }
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = accent.copy(alpha = 0.40f),
                    fontWeight = FontWeight.Bold,
                ),
            ) { append("/") }
            withStyle(
                androidx.compose.ui.text.SpanStyle(
                    color = accent,
                    fontWeight = FontWeight.ExtraBold,
                ),
            ) { append("E-${episodeTag.number}") }
        }
        Text(
            text = compoundTag,
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            letterSpacing = 0.4.sp,
            modifier = modifier,
            maxLines = 1,
            softWrap = false,
        )
    } else {
        Text(
            text = "EP ${episodeTag?.number ?: epNumText}",
            fontFamily = RobotoFamily,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            color = accent,
            modifier = modifier,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * D-557: the shared WATCH-PROGRESS bar — a rounded INSET pill replacing the
 * square-capped M3 LinearProgressIndicator everywhere. The v1.1.30 device
 * round: "you can improve the watch progress on the cinema one … the
 * timeline one … the grid view one, and also most definitely … the classic
 * one. There is a little bit glitch on the classic one." THE GLITCH'S ROOT
 * CAUSE: the full-width indicator drew square caps over the thumbnail's
 * rounded bottom corners (the wrapper Box is not clipped), so the bar
 * visually overflowed the image. The pill is fully rounded and rides a
 * translucent track; callers INSET it from the imagery's edges via their own
 * padding — YouTube's treatment, glitch-free on any radius.
 */
@Composable
internal fun EpisodeWatchProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    progressColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = Color.White.copy(alpha = 0.30f),
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(progressColor),
        )
    }
}

/** The short date label ("Jan 1") — the GRID chip + the TIMELINE node label. */
internal fun formatShortDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
}

/**
 * The TIMELINE node label: the air date is the schedule's PRIMARY element —
 * but only while the user's date toggle is on and the episode HAS a date;
 * otherwise the node falls back to the episode number (the spine never goes
 * unlabeled).
 */
internal fun timelineNodeLabel(
    showDate: Boolean,
    shortDateText: String?,
    epNumText: String,
): String = if (showDate && !shortDateText.isNullOrBlank()) shortDateText else "EP $epNumText"

/** The CINEMA ghost number: "01"…"99" zero-padded, 100+ honest (and 105 → "105"). */
internal fun ghostEpisodeNumber(num: Float): String {
    val n = num.toInt()
    return if (n in 0..99) String.format(java.util.Locale.US, "%02d", n) else n.toString()
}

/** The watched grayscale matrix (the same treatment the CLASSIC row applies). */
private fun watchedImageFilter(isWatched: Boolean, dimWatched: Boolean): ColorFilter? =
    if (isWatched && dimWatched) {
        ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0.299f, 0.587f, 0.114f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )))
    } else null

// ─────────────────────────────────────────────────────────────────────────────
// The shared swipe-to-toggle gesture.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-555: the Phase WP swipe-to-toggle-watched wrapper, MOVED VERBATIM out of
 * the classic row so CLASSIC, TIMELINE and CINEMA share ONE gesture
 * implementation (GRID long-presses instead — half-width cells cannot
 * swipe). The background icon + the drag/threshold/haptic algebra are the
 * exact proven code; the watched ALPHA stays with each caller (CLASSIC fades
 * the whole card; the new layouts treat the imagery only).
 *
 * The content Box is a NORMAL child (it determines the size); the icon
 * Surface uses matchParentSize to fill the card's footprint behind it — the
 * original "background gone" bug's lesson (fillMaxSize resolves to 0 height
 * in an unbounded wrapper; matchParentSize measures the card first).
 */
@Composable
internal fun SwipeToToggleWatched(
    isWatched: Boolean,
    onToggleWatched: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundShape: Shape = RoundedCornerShape(12.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val swipeOffset = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    // D-557: the gesture's pointerInput(Unit) NEVER restarts, so it held the
    // FIRST composition's onToggleWatched lambda forever — a callback that
    // captures the watched VALUE (the settings preview's "!isWatched") wrote
    // the SAME value on every later swipe: toggle once, then dead. The
    // freshest callback is read through rememberUpdatedState at CALL time.
    val currentOnToggleWatched by rememberUpdatedState(onToggleWatched)
    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) {
        configuration.screenWidthDp.dp.toPx()
    }
    val swipeThresholdPx = screenWidthPx * 0.35f // 35% of screen width

    var thresholdCrossed by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Background icon — fades in linearly as the user swipes, full opacity
        // past the threshold.
        val swipeProgress = (kotlin.math.abs(swipeOffset.value) / swipeThresholdPx).coerceIn(0f, 1f)
        val iconAlpha = if (thresholdCrossed) 1f else swipeProgress
        Surface(
            color = if (isWatched) MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            shape = backgroundShape,
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer { this.alpha = iconAlpha },
        ) {
            Box(
                modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
                contentAlignment = if (swipeOffset.value > 0) Alignment.CenterStart
                else Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = if (isWatched) Icons.Filled.VisibilityOff
                    else Icons.Filled.CheckCircle,
                    contentDescription = if (isWatched) "Mark as unwatched" else "Mark as watched",
                    tint = if (isWatched) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary,
                )
            }
        }

        // The content — translates with the swipe; the gesture lives here so
        // the caller's card keeps its own surface/clip/click chain.
        Box(
            modifier = Modifier
                .offset { IntOffset(swipeOffset.value.toInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { thresholdCrossed = false },
                        onDragEnd = {
                            // If past threshold → toggle + haptic. Else smooth spring back.
                            if (kotlin.math.abs(swipeOffset.value) > swipeThresholdPx) {
                                HapticHelper.releaseConfirm(context)
                                currentOnToggleWatched()
                            }
                            coroutineScope.launch {
                                swipeOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = tween(
                                        durationMillis = 300,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                            }
                            thresholdCrossed = false
                        },
                    ) { _, dragAmount ->
                        val newValue = (swipeOffset.value + dragAmount).coerceIn(
                            minimumValue = -swipeThresholdPx * 1.5f, // allow left cancel
                            maximumValue = swipeThresholdPx * 1.5f,   // allow right toggle
                        )
                        coroutineScope.launch {
                            swipeOffset.snapTo(newValue)
                        }
                        // Haptic feedback when crossing the threshold for the first time.
                        if (!thresholdCrossed && kotlin.math.abs(newValue) > swipeThresholdPx) {
                            thresholdCrossed = true
                            HapticHelper.stageCross(context)
                        } else if (thresholdCrossed && kotlin.math.abs(newValue) <= swipeThresholdPx) {
                            thresholdCrossed = false
                        }
                    }
                },
        ) {
            content()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// The compact download badge — GRID/TIMELINE/CINEMA's state icon.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * D-555: the download control's compact sibling for the imagery layouts —
 * the same 8-state contract as [EpisodeDownloadControl] (tap actions match:
 * download/pause/resume/retry/play; the in-flight states cancel), drawn as
 * ONE 32dp circle so it can overlay a poster or a banner. [translucent]
 * renders the CINEMA variant (white-on-scrim over the image).
 */
@Composable
internal fun EpisodeDownloadBadge(
    state: EpisodeDownloadState,
    onDownload: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onPlayDownloaded: () -> Unit,
    modifier: Modifier = Modifier,
    translucent: Boolean = false,
    previewTapAll: (() -> Unit)? = null,
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
                // D-557: the settings preview passes previewTapAll — ONE tap
                // target for ALL states so the demo cycle can walk every
                // state (the user: "if I tap that exact same spinning one
                // again, it does not change to the next state"). Production
                // call sites pass null → the state contract below, unchanged.
                val tap = previewTapAll
                if (tap != null) {
                    tap()
                } else {
                    when (state) {
                        is EpisodeDownloadState.NotDownloaded -> onDownload()
                        is EpisodeDownloadState.Downloading -> onPause()
                        is EpisodeDownloadState.Paused -> onResume()
                        is EpisodeDownloadState.Error -> onRetry()
                        is EpisodeDownloadState.Downloaded -> onPlayDownloaded()
                        // Resolving / Queued / Retrying — the cancellable in-flight states.
                        else -> onCancel()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            is EpisodeDownloadState.NotDownloaded -> Icon(
                imageVector = Icons.Filled.Download,
                contentDescription = "Download",
                tint = fg,
                modifier = Modifier.size(18.dp),
            )
            is EpisodeDownloadState.Downloading -> Box(contentAlignment = Alignment.Center) {
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
            is EpisodeDownloadState.Paused -> Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Resume download",
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            is EpisodeDownloadState.Error -> Icon(
                imageVector = Icons.Filled.Refresh,
                contentDescription = "Retry download",
                tint = accent,
                modifier = Modifier.size(18.dp),
            )
            is EpisodeDownloadState.Downloaded -> Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Downloaded — tap to play",
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
            // Resolving / Queued / Retrying — the in-flight spinner.
            else -> CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = accent,
            )
        }
    }
    // NOTE: onDelete has no gesture room in a 32dp badge — the downloads page
    // keeps that action (the D-555 plan §4). The layouts pass the shared
    // actions bag's other handlers; delete stays where it already lives.
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYOUT 2 — GRID: the two-column poster wall.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * GRID — REDESIGN 2 (D-557; the v1.1.30 device round: "I am apparently not
 * satisfied with the look of the grid view … Its UI is not looking clean…
 * do a complete UI redesign of the grid view"). Still a two-column wall —
 * that is the layout's identity — but the cell is now BORDERLESS and QUIET:
 *
 * - THE IMAGE IS THE CELL: a pure 16:9 plate (16dp radius, no border, no
 *   background box, no scrim, NO EP PILL) — nothing covers the imagery
 *   except the small translucent download badge (top-end), the watched
 *   treatment, and the inset rounded progress pill.
 * - The text lives BELOW in its own block: the themed EP number label
 *   ([EpisodeNumberLabel]), the title (up to two lines, dimmed when
 *   watched), and the capsule-chip meta line (date + audio).
 * - Watched = grayscale + a dim overlay + ONE small translucent check
 *   bubble bottom-start (the ringed badge read as noise; the bubble is
 *   quieter and keeps the meta legible).
 *
 * Long-press toggles the watched state — a half-width cell cannot host the
 * horizontal swipe (the other three layouts keep it).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun EpisodeGridCell(
    episode: SEpisode,
    display: EpisodeDisplayData,
    actions: EpisodeRowActions,
    episodeTag: EpisodeTag?,
    downloadState: EpisodeDownloadState,
    isWatched: Boolean,
    progressFraction: Float,
    style: EpisodeListDisplayStyle,
) {
    val epNumText = formatEpisodeNumber(episode.episode_number)
    val grayscale = watchedImageFilter(isWatched, style.dimWatched)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = actions.onClick,
                onLongClick = actions.onToggleWatched,
            ),
    ) {
        // ── The image plate — pure imagery, nothing covering it ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            if (display.thumbnailUrl != null) {
                AsyncImage(
                    model = display.thumbnailUrl,
                    contentDescription = display.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = grayscale,
                )
            } else {
                // The bare plate — a quiet number (no fake imagery, no pill).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = episodeTag?.number ?: epNumText,
                        fontFamily = RobotoFamily,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    )
                }
            }
            // The watched treatment: grayscale + dim + ONE quiet check bubble.
            if (isWatched && style.dimWatched) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.32f)),
                )
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
            if (style.showDownloadControl) {
                EpisodeDownloadBadge(
                    state = downloadState,
                    onDownload = actions.onDownload,
                    onPause = actions.onPause,
                    onResume = actions.onResume,
                    onCancel = actions.onCancel,
                    onRetry = actions.onRetry,
                    onPlayDownloaded = actions.onPlayDownloaded,
                    translucent = true,
                    previewTapAll = actions.previewTapAll,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                )
            }
            if (style.showWatchProgress && progressFraction > 0f && !isWatched) {
                EpisodeWatchProgressBar(
                    fraction = progressFraction,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 9.dp, vertical = 8.dp),
                )
            }
        }
        // ── The text block — number, title, chips ──
        Spacer(Modifier.height(7.dp))
        EpisodeNumberLabel(
            episodeTag = episodeTag,
            epNumText = epNumText,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = display.title,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = (LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onSurface)
                .let { if (isWatched && style.dimWatched) it.copy(alpha = 0.55f) else it },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        val chips = buildList {
            if (style.showDatePill && display.shortDateText != null) add(display.shortDateText)
            if (style.showAudioPills) addAll(display.audioLabels)
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(5.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                chips.forEachIndexed { idx, chip ->
                    val isDateChip = idx == 0 && style.showDatePill && !display.shortDateText.isNullOrBlank()
                    if (isDateChip) {
                        EpisodeDateChip(text = chip)
                    } else {
                        EpisodeAudioChip(label = chip)
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYOUT 3 — TIMELINE: the air-date schedule spine.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * TIMELINE — the list becomes a SCHEDULE: a continuous vertical spine runs
 * down the left rail, every episode hangs a compact card off its own node,
 * and the node's label is the AIR DATE (the schedule's primary element —
 * "EP n" when the date is absent or the user toggled dates off). The node
 * fills when the episode is watched. The spine spans each row's FULL height
 * and the caller drops the per-item vertical padding for this layout, so
 * the line reads as ONE continuous rail down the list.
 *
 * D-557 — THE BLOB, REBUILT. The D-556 attempt (a same-color neck drawn in
 * the rail reaching toward the card) failed the device round three ways:
 * "it is not symmetric, like at the bottom there is a bit more blob than at
 * the top", "the blob is not feeling like a blob, like a rounded kind of
 * experience", and worst: "the blob does not merge smoothly with the right
 * side. There is a complete cutout between the right side and the left
 * side" — the neck stopped short of the card's left edge (the 8dp spacer +
 * the card's own rounded corner left a visible gap).
 *
 * D-558 — THE THIN GAP. The v1.1.31 device round flipped the merge verdict:
 * with the union shape the card's material now "is kind of way too close to
 * the release date. Like it is expanding towards the release date side a bit
 * too much. So I was hoping for a thin area between those to feel like an
 * actual blob kind of feel." So the card body pulled back 8dp from the bump.
 *
 * D-559 — THE CHICKEN NECK. The v1.1.32 device round rejected the disjoint
 * gap: "instead of making the gap thin, you just outright removed it, like
 * there is no connection between the left release date and the right side
 * content itself … it should be like a chicken neck kind of thing, like a
 * blob kind of effect, both of them being connected but with a thin chicken
 * neck kind of feel." So the shape now carries THREE sub-shapes in ONE
 * outline — the stadium bump, the card body, and a ~10dp-tall NECK bridging
 * them: the connection is back, but the joined area stays thin, so the card
 * no longer "expands towards the release date side" (the D-558 verdict is
 * preserved — the neck is the thin area, now attached).
 */
@Composable
internal fun EpisodeTimelineRow(
    episode: SEpisode,
    display: EpisodeDisplayData,
    actions: EpisodeRowActions,
    episodeTag: EpisodeTag?,
    downloadState: EpisodeDownloadState,
    isWatched: Boolean,
    progressFraction: Float,
    style: EpisodeListDisplayStyle,
) {
    val epNumText = formatEpisodeNumber(episode.episode_number)
    val nodeLabel = timelineNodeLabel(style.showDatePill, display.shortDateText, epNumText)
    val nodeIsDate = style.showDatePill && !display.shortDateText.isNullOrBlank()
    val grayscale = watchedImageFilter(isWatched, style.dimWatched)
    val cardColor = MaterialTheme.colorScheme.surfaceVariant
    // The bump geometry (dp, from the row's top-start) — symmetric around
    // the node(14dp at y=10) + gap(4) + label(~13) group. D-558: the card
    // body starts 8dp RIGHT of the bump. D-559: a thin NECK spans that 8dp
    // gap — connected, but the join stays thin (the chicken-neck feel).
    val bumpLeft = 2.dp
    val bumpTop = 3.dp
    val bumpRight = 44.dp
    val bumpBottom = 49.dp
    val cardLeft = 52.dp
    val nodeCx = (bumpLeft + bumpRight) / 2 // 23dp — the spine's x
    val contentStart = cardLeft + 10.dp     // the card's content clears its own edge
    val blobShape = remember {
        TimelineBlobCardShape(
            bumpLeft = bumpLeft,
            bumpTop = bumpTop,
            bumpRight = bumpRight,
            bumpBottom = bumpBottom,
            cardLeft = cardLeft,
            cornerRadius = 12.dp,
        )
    }
    SwipeToToggleWatched(
        isWatched = isWatched,
        onToggleWatched = actions.onToggleWatched,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).clipToBounds()) {
            // ── THE CARD — full width, the blob IS its background ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(blobShape)
                    .background(cardColor)
                    .clickable(onClick = actions.onClick)
                    .padding(
                        start = contentStart,
                        end = 10.dp,
                        top = 10.dp,
                        bottom = 10.dp,
                    ),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = display.title,
                            fontFamily = RobotoFamily,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                                ?: MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (style.showAudioPills && display.audioLabels.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                display.audioLabels.forEach { label ->
                                    EpisodeAudioChip(label = label)
                                }
                            }
                        }
                    }
                    if (display.thumbnailUrl != null) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(width = 64.dp, height = 38.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        ) {
                            AsyncImage(
                                model = display.thumbnailUrl,
                                contentDescription = display.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                                colorFilter = grayscale,
                            )
                        }
                    }
                    if (style.showDownloadControl) {
                        Spacer(Modifier.width(8.dp))
                        EpisodeDownloadBadge(
                            state = downloadState,
                            onDownload = actions.onDownload,
                            onPause = actions.onPause,
                            onResume = actions.onResume,
                            onCancel = actions.onCancel,
                            onRetry = actions.onRetry,
                            onPlayDownloaded = actions.onPlayDownloaded,
                            previewTapAll = actions.previewTapAll,
                        )
                    }
                }
            }
            // ── THE SPINE — two segments, passing BEHIND the blob ──
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = nodeCx - 1.dp)
                    .width(2.dp)
                    .height(bumpTop)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = nodeCx - 1.dp, y = bumpBottom)
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
            )
            // ── THE NODE + DATE — centered inside the bump ──
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = nodeCx - 7.dp, y = 10.dp)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(
                        if (isWatched) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface,
                    )
                    .then(
                        if (isWatched) Modifier
                        else Modifier.border(
                            width = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = bumpLeft, y = 28.dp)
                    .width(bumpRight - bumpLeft),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = nodeLabel,
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (nodeIsDate) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // The watch-progress pill — below the bump zone, inside the card.
            if (style.showWatchProgress && progressFraction > 0f && !isWatched) {
                EpisodeWatchProgressBar(
                    fraction = progressFraction,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = contentStart, end = 10.dp, bottom = 4.dp),
                )
            }
        }
    }
}

/**
 * The TIMELINE card's background shape — ONE outline carrying THREE
 * sub-shapes: the rounded card body, the stadium bump around the node +
 * date, and (D-559) the thin NECK that CONNECTS them. The v1.1.31 round
 * wanted the card to stop "expanding towards the release date side"; the
 * v1.1.32 round rejected the resulting full disconnection ("it should be
 * like a chicken neck … both of them being connected but with a thin
 * chicken neck kind of feel"). The neck is a ~10dp-tall horizontal band,
 * centered on the bump's vertical midline — where the stadium's edge runs
 * tangent-vertical, so the bump-side join reads smooth — and overlaps BOTH
 * bodies by 2dp (an exact-tangent join would leave an antialiasing hairline
 * at the junction; overlapping same-direction sub-paths fill as their union
 * under the nonzero winding rule). The bump's bottom is clamped to the
 * shape's height so a short card never draws the blob outside its bounds.
 */
private class TimelineBlobCardShape(
    private val bumpLeft: Dp,
    private val bumpTop: Dp,
    private val bumpRight: Dp,
    private val bumpBottom: Dp,
    private val cardLeft: Dp,
    private val cornerRadius: Dp,
) : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline = with(density) {
        val path = Path()
        // The card body — its left edge sits RIGHT of the bump (disjoint:
        // the gap between them reads as the screen's background).
        path.addRoundRect(
            RoundRect(
                rect = Rect(
                    left = cardLeft.toPx(),
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                ),
                topLeft = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                topRight = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                bottomRight = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
                bottomLeft = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
            ),
        )
        // The stadium bump — fully rounded (radius = half the height), the
        // user's "rounded kind of experience", symmetric around the node +
        // date group.
        val bumpBottomPx = minOf(bumpBottom.toPx(), size.height - 1f)
        val bumpHeightPx = bumpBottomPx - bumpTop.toPx()
        val stadiumRadius = CornerRadius(bumpHeightPx / 2f, bumpHeightPx / 2f)
        path.addRoundRect(
            RoundRect(
                rect = Rect(
                    left = bumpLeft.toPx(),
                    top = bumpTop.toPx(),
                    right = bumpRight.toPx(),
                    bottom = bumpBottomPx,
                ),
                topLeft = stadiumRadius,
                topRight = stadiumRadius,
                bottomRight = stadiumRadius,
                bottomLeft = stadiumRadius,
            ),
        )
        // THE NECK (D-559) — the thin bridge between the bump and the card:
        // ~10dp tall, centered on the bump's midline, overlapping both bodies
        // by 2dp so no antialiasing hairline survives at either junction.
        val bumpCenterY = (bumpTop.toPx() + bumpBottomPx) / 2f
        val neckHalf = 5.dp.toPx()
        val neckOverlap = 2.dp.toPx()
        val neckTop = (bumpCenterY - neckHalf).coerceAtLeast(0f)
        val neckBottom = minOf(bumpCenterY + neckHalf, size.height)
        val neckRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
        path.addRoundRect(
            RoundRect(
                rect = Rect(
                    left = bumpRight.toPx() - neckOverlap,
                    top = neckTop,
                    right = cardLeft.toPx() + neckOverlap,
                    bottom = maxOf(neckBottom, neckTop + 1f),
                ),
                topLeft = neckRadius,
                topRight = neckRadius,
                bottomRight = neckRadius,
                bottomLeft = neckRadius,
            ),
        )
        Outline.Generic(path)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYOUT 4 — CINEMA: the full-bleed banner cards.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * CINEMA — the thumbnail IS the card: a full-width 16:9 banner with a bottom
 * gradient scrim, a themed ghost EP number (D-558: corner + solid/frosted
 * style are USER choices), the title + a translucent date/audio/WATCHED chip
 * overlaid bottom-start, the download badge floating bottom-end (the
 * translucent variant), and the watch-progress bar on the banner's bottom
 * edge. Immersive, image-first — the exact opposite pole of the CLASSIC row.
 *
 * D-558 — the watched check: the centered circular check became a USER
 * TOGGLE (default OFF; the grayscale/dim treatment itself is untouched).
 *
 * D-559 — the frosted NUMBER is the TEXT ITSELF. The v1.1.32 device round:
 * "By frosted effect, what I meant for you was that the text itself being
 * frosted rather than it getting a frosted effect on it." The plate + veil
 * died; the number now renders as TWO stacked copies of the same glyphs —
 * a blurred halo behind (a real RenderEffect blur on Android 12+; below S
 * the halo is a plain low-alpha under-copy, which still reads as the soft
 * double-exposure frost) and a translucent crisp copy on top with a soft
 * dark shadow for legibility. The imagery shows THROUGH the number — glass,
 * not sticker.
 */
@Composable
internal fun EpisodeCinemaCard(
    episode: SEpisode,
    display: EpisodeDisplayData,
    actions: EpisodeRowActions,
    episodeTag: EpisodeTag?,
    downloadState: EpisodeDownloadState,
    isWatched: Boolean,
    progressFraction: Float,
    style: EpisodeListDisplayStyle,
) {
    val grayscale = watchedImageFilter(isWatched, style.dimWatched)
    SwipeToToggleWatched(
        isWatched = isWatched,
        onToggleWatched = actions.onToggleWatched,
        modifier = Modifier.fillMaxWidth(),
        backgroundShape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = actions.onClick),
        ) {
            // The imagery IS the card.
            if (display.thumbnailUrl != null) {
                AsyncImage(
                    model = display.thumbnailUrl,
                    contentDescription = display.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    colorFilter = grayscale,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = ghostEpisodeNumber(episode.episode_number),
                        fontFamily = RobotoFamily,
                        fontSize = 56.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    )
                }
            }
            // The bottom scrim — the overlaid text's contrast guarantee.
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
            // The watched treatment: the dim overlay stays tied to the
            // dimWatched toggle; the big centered circular CHECK is the D-558
            // USER TOGGLE ("we should give the user the option to turn on or
            // off the check mark on the watched episodes … By default it will
            // be turned off"). Default OFF → only the quiet dim + the
            // grayscale imagery; the WATCHED chip in the meta line still
            // speaks.
            if (isWatched && style.dimWatched) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (style.cinemaWatchedCheckBadge) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = "Watched",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp),
                        )
                    }
                }
            }
            // THE GHOST NUMBER — RESTORED (D-557), CUSTOMIZABLE (D-558). The
            // v1.1.30 device round brought the themed number back; the v1.1.31
            // round adds the two choices the user asked for: WHICH CORNER it
            // lives in ("select where the episode number should be shown. top
            // right corner, or … top left corner?") and its STYLE — SOLID (the
            // themed number straight on the imagery) or FROSTED — the TEXT
            // ITSELF frosted (D-559, the v1.1.32 round: "the text itself
            // should be given the frosted effect").
            val numberCorner = if (style.cinemaNumberAtTopStart) Alignment.TopStart
            else Alignment.TopEnd
            if (style.cinemaNumberFrosted) {
                // FROSTED TEXT (D-559) — NO plate, NO veil. The halo copy
                // behind is blurred on S+ (a plain low-alpha under-copy below
                // S — RenderEffect is a no-op there) and the crisp copy on
                // top is translucent: the frost lives IN the glyphs.
                Box(
                    modifier = Modifier
                        .align(numberCorner)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = ghostEpisodeNumber(episode.episode_number),
                        fontFamily = RobotoFamily,
                        fontSize = 48.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.graphicsLayer {
                            alpha = 0.45f
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                renderEffect = BlurEffect(14f, 14f)
                            }
                        },
                    )
                    Text(
                        text = ghostEpisodeNumber(episode.episode_number),
                        fontFamily = RobotoFamily,
                        fontSize = 48.sp,
                        lineHeight = 56.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.58f),
                        maxLines = 1,
                        softWrap = false,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.45f),
                                blurRadius = 14f,
                                offset = Offset(1f, 1f),
                            ),
                        ),
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            } else {
                Text(
                    text = ghostEpisodeNumber(episode.episode_number),
                    fontFamily = RobotoFamily,
                    fontSize = 56.sp,
                    lineHeight = 56.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
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
            // The overlaid meta — title + one translucent chips pill.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp),
            ) {
                Text(
                    text = display.title,
                    fontFamily = RobotoFamily,
                    fontSize = 16.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val chips = buildList {
                    if (style.showDatePill && display.dateText != null) add(display.dateText)
                    if (style.showAudioPills) addAll(display.audioLabels)
                    if (isWatched && style.dimWatched) add("WATCHED")
                }
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color.White.copy(alpha = 0.18f),
                    ) {
                        Text(
                            text = chips.joinToString("  ·  "),
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
            // The download overlay — the translucent badge variant.
            if (style.showDownloadControl) {
                EpisodeDownloadBadge(
                    state = downloadState,
                    onDownload = actions.onDownload,
                    onPause = actions.onPause,
                    onResume = actions.onResume,
                    onCancel = actions.onCancel,
                    onRetry = actions.onRetry,
                    onPlayDownloaded = actions.onPlayDownloaded,
                    translucent = true,
                    previewTapAll = actions.previewTapAll,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }
            if (style.showWatchProgress && progressFraction > 0f && !isWatched) {
                EpisodeWatchProgressBar(
                    fraction = progressFraction,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}
