package com.confused.anikuta.feature.animedetails

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.confused.anikuta.core.common.HapticHelper
import com.confused.anikuta.core.designsystem.theme.LocalCardDescriptionColor
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
 */

// ─────────────────────────────────────────────────────────────────────────────
// Pure helpers (unit-locked in EpisodeListStyleTest — no Compose needed).
// ─────────────────────────────────────────────────────────────────────────────

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
                                onToggleWatched()
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
                when (state) {
                    is EpisodeDownloadState.NotDownloaded -> onDownload()
                    is EpisodeDownloadState.Downloading -> onPause()
                    is EpisodeDownloadState.Paused -> onResume()
                    is EpisodeDownloadState.Error -> onRetry()
                    is EpisodeDownloadState.Downloaded -> onPlayDownloaded()
                    // Resolving / Queued / Retrying — the cancellable in-flight states.
                    else -> onCancel()
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
 * GRID — a Netflix-style poster wall cell: the 16:9 thumbnail IS the cell,
 * the EP badge overlays TopStart (the CLASSIC pill language), the download
 * badge overlays TopEnd, the watch-progress bar rides the image's bottom
 * edge, and the title + date/audio chips sit BELOW the image (the wall's
 * rhythm). Watched = grayscale + a dim overlay + a centered white check.
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
    val tagNumber = episodeTag?.number ?: epNumText
    val grayscale = watchedImageFilter(isWatched, style.dimWatched)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .combinedClickable(
                onClick = actions.onClick,
                onLongClick = actions.onToggleWatched,
            )
            .padding(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp)),
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
                // The bare cell — a quiet number plate (no fake imagery).
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = tagNumber,
                        fontFamily = RobotoFamily,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (isWatched && style.dimWatched) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Watched",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp),
            ) {
                Text(
                    text = "EP $tagNumber",
                    fontFamily = RobotoFamily,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    maxLines = 1,
                    softWrap = false,
                )
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
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp),
                )
            }
            if (style.showWatchProgress && progressFraction > 0f && !isWatched) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = display.title,
            fontFamily = RobotoFamily,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Bold,
            color = LocalCardHeadingColor.current.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        val chips = buildList {
            if (style.showDatePill && display.shortDateText != null) add(display.shortDateText)
            if (style.showAudioPills) addAll(display.audioLabels)
        }
        if (chips.isNotEmpty()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = chips.joinToString(" · "),
                fontFamily = RobotoFamily,
                fontSize = 10.sp,
                lineHeight = 13.sp,
                fontWeight = FontWeight.Medium,
                color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
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
    val tagNumber = episodeTag?.number ?: epNumText
    val nodeLabel = timelineNodeLabel(style.showDatePill, display.shortDateText, epNumText)
    val nodeIsDate = style.showDatePill && !display.shortDateText.isNullOrBlank()
    val grayscale = watchedImageFilter(isWatched, style.dimWatched)
    SwipeToToggleWatched(
        isWatched = isWatched,
        onToggleWatched = actions.onToggleWatched,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
        ) {
            // ── The rail: spine line + node + label ──
            Box(
                modifier = Modifier
                    .width(60.dp)
                    .fillMaxHeight(),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
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
                    Spacer(Modifier.height(4.dp))
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
            }
            Spacer(Modifier.width(8.dp))
            // ── The card hanging off the spine ──
            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable(onClick = actions.onClick),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
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
                            Text(
                                text = display.audioLabels.joinToString(" · "),
                                fontFamily = RobotoFamily,
                                fontSize = 10.sp,
                                lineHeight = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = LocalCardDescriptionColor.current.takeIf { it != Color.Unspecified }
                                    ?: MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
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
                        )
                    }
                }
                if (style.showWatchProgress && progressFraction > 0f && !isWatched) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LAYOUT 4 — CINEMA: the full-bleed banner cards.
// ─────────────────────────────────────────────────────────────────────────────

/**
 * CINEMA — the thumbnail IS the card: a full-width 16:9 banner with a bottom
 * gradient scrim, the HUGE ghost episode number floating top-end, the title
 * + a translucent date/audio/WATCHED chip overlaid bottom-start, the
 * download badge floating bottom-end (the translucent variant), and the
 * watch-progress bar on the banner's bottom edge. Immersive, image-first —
 * the exact opposite pole of the CLASSIC row.
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
            // The watched treatment: dim + centered check (the imagery, not
            // the text — the chips stay legible).
            if (isWatched && style.dimWatched) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.30f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "Watched",
                        tint = Color.White,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }
            // THE GHOST NUMBER — the banner's signature.
            Text(
                text = ghostEpisodeNumber(episode.episode_number),
                fontFamily = RobotoFamily,
                fontSize = 56.sp,
                lineHeight = 56.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White.copy(alpha = 0.30f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                maxLines = 1,
                softWrap = false,
            )
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
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp),
                )
            }
            if (style.showWatchProgress && progressFraction > 0f && !isWatched) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.25f),
                )
            }
        }
    }
}
