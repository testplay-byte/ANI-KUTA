package com.confused.anikuta.feature.watch

// ============================================================================
// ROUND 131 (the WatchScreen size plan — Phase 1, D-807): the STATELESS tail
// of the old WatchScreen.kt, extracted verbatim (pure moves, zero behavior
// change): the fullscreen mode host, the shared MPV surface, the round-120
// overlay pass-through, the episode sheet, and the helpers. Visibility went
// private -> internal (same package — every caller unchanged). The stateful
// WatchScreen + MinimizedMode stay in WatchScreen.kt.
// ============================================================================

import android.app.Activity
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star  // Phase 4
import androidx.compose.material.icons.filled.StarBorder  // Phase 4
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.designsystem.component.animateScrollToItemCentered
import com.confused.anikuta.core.designsystem.theme.RobotoFamily
import com.confused.anikuta.core.player.AnikutaMPVView
import com.confused.anikuta.core.player.PlayerInitializer
import com.confused.anikuta.core.player.PlayerLoadingState
import com.confused.anikuta.core.player.PlayerMode
import com.confused.anikuta.core.player.PlayerObserver
import com.confused.anikuta.core.player.PlayerStateHolder
import com.confused.anikuta.core.player.controls.EpisodeSwitchingOverlay
import com.confused.anikuta.core.playbackcache.PlaybackCacheManager
import com.confused.anikuta.core.playbackcache.PlaybackVideoId
import com.confused.anikuta.core.playbackcache.serializeTracks
import com.confused.anikuta.core.playbackcache.serverKeyFromVideoTitle
import com.confused.anikuta.core.videoresolver.ResolvedVideo
import com.confused.anikuta.core.videoresolver.ResolverVideo
import com.confused.anikuta.core.videoresolver.ResolvedVideosRegistry
import com.confused.anikuta.core.watchprogress.WatchProgress
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

// ════════════════════════════════════════════════════════════════════════════
//  FULLSCREEN MODE — edge-to-edge player + ported FullscreenControls
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun FullscreenMode(
    watchKey: WatchKey,
    stateHolder: PlayerStateHolder,
    mpvView: AnikutaMPVView?,
    initMpv: (AnikutaMPVView) -> Unit,
    onMpvViewCreated: (AnikutaMPVView) -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRelative: (Int) -> Unit,
    onSeekTo: (Int) -> Unit,
    onBack: () -> Unit,
    onQualityClick: () -> Unit = {},
    onSubtitleClick: () -> Unit = {},
    onSpeedClick: () -> Unit = {},
    onSkipForward: () -> Unit = {},
    onRetry: () -> Unit = {},
    onDismissError: () -> Unit = {},
    isSwitchingEpisode: Boolean = false,
    switchingEpisodeTitle: String = "",
    currentSpeed: Float = 1.0f,
    // ── Round 120 overlays (D-746/D-747/D-748) ──
    upNext: UpNextUi? = null,
    onPlayUpNext: () -> Unit = {},
    onDismissUpNext: () -> Unit = {},
    skipPill: SkipPillState? = null,
    onSkipPillAction: () -> Unit = {},
    resumePillSec: Long = -1L,
    onRestartFromStart: () -> Unit = {},
    // ── ROUND 121: episode navigation (the top-right cluster) ──
    onPrevEpisode: () -> Unit = {},
    onNextEpisode: () -> Unit = {},
    onOpenEpisodesSheet: () -> Unit = {},
    // ROUND 121: cancel a stuck episode switch (the overlay's button).
    onCancelSwitch: () -> Unit = {},
    // ROUND 122 (R5): the seek-bar skip-range ribbon.
    skipRanges: com.confused.anikuta.core.player.controls.SkipRanges = com.confused.anikuta.core.player.controls.SkipRanges(),
    // ROUND 131 (D-809): swipe gestures (volume/brightness sides + live seek).
    gestureConfig: com.confused.anikuta.core.player.controls.PlayerGestureConfig =
        com.confused.anikuta.core.player.controls.PlayerGestureConfig(),
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PlayerSurface(
            mpvView = mpvView,
            initMpv = initMpv,
            onMpvViewCreated = onMpvViewCreated,
            modifier = Modifier.fillMaxSize(),
        )

        // Ported FullscreenControls — handles all gestures, overlay, seekbar, etc.
        com.confused.anikuta.core.player.controls.FullscreenControls(
            stateHolder = stateHolder,
            onBack = onBack,
            onTogglePlay = onTogglePlay,
            onSeekRelative = onSeekRelative,
            onSeekTo = onSeekTo,
            onMinimize = onBack,
            onLockToggle = { stateHolder.updateControlsLocked(!stateHolder.controlsLocked.value) },
            onQualityClick = onQualityClick,
            onSubtitleClick = onSubtitleClick,
            onSpeedClick = onSpeedClick,
            // ROUND 121: the episodes browser (the previously dead More button).
            onMoreClick = onOpenEpisodesSheet,
            onPrevEpisode = onPrevEpisode,
            onNextEpisode = onNextEpisode,
            onSkipForward = onSkipForward,
            onRetry = onRetry,
            onDismissError = onDismissError,
            animeTitle = watchKey.animeTitle,
            episodeInfo = if (watchKey.episodeTitle.isNotBlank()) "EP ${formatEpisodeNumber(watchKey.episodeNumber)}" else "",
            qualityInfo = watchKey.quality,
            currentSpeed = currentSpeed,
            skipRanges = skipRanges,
            gestureConfig = gestureConfig,
        )

        // Episode switching overlay — shown over the fullscreen player while a
        // new episode resolves + loads. ONLY shown for episode switches (not
        // quality/server switches).
        if (isSwitchingEpisode) {
            EpisodeSwitchingOverlay(
                episodeTitle = switchingEpisodeTitle.ifBlank { null },
                onCancel = onCancelSwitch,
            )
        }

        // Round 120 overlays: Up Next card, skip pill, resume pill.
        PlayerRound120Overlays(
            upNext = upNext,
            onPlayUpNext = onPlayUpNext,
            onDismissUpNext = onDismissUpNext,
            skipPill = skipPill,
            onSkipPillAction = onSkipPillAction,
            resumePillSec = resumePillSec,
            onRestartFromStart = onRestartFromStart,
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Player surface (shared AndroidView — never recreated)
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun PlayerSurface(
    mpvView: AnikutaMPVView?,
    initMpv: (AnikutaMPVView) -> Unit,
    onMpvViewCreated: (AnikutaMPVView) -> Unit,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx ->
            val layoutId = ctx.resources.getIdentifier("mpv_view", "layout", ctx.packageName)
            val view = (mpvView ?: LayoutInflater.from(ctx).inflate(layoutId, null) as AnikutaMPVView).also { v ->
                if (mpvView == null) {
                    onMpvViewCreated(v)
                    initMpv(v)
                }
            }
            (view.parent as? android.view.ViewGroup)?.removeView(view)
            view
        },
        modifier = modifier,
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 103 (WS-4): the private EpisodeListRow is RETIRED — both player
//  stacks and the settings preview render through the ONE shared four-paradigm
//  dispatcher (:core:designsystem/component/playerlist/PlayerEpisodeListEntry).
// ════════════════════════════════════════════════════════════════════════════

// ════════════════════════════════════════════════════════════════════════════
//  Helpers
// ════════════════════════════════════════════════════════════════════════════

@Composable
internal fun ControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    size: Int = 40,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.4f),
        shape = CircleShape,
        modifier = Modifier
            .size(size.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size((size * 0.55f).dp),
            )
        }
    }
}

internal fun formatEpisodeNumber(num: Float): String {
    return com.confused.anikuta.core.common.EpisodeTitleParser.formatEpisodeNumber(num)
}

// ── Audio availability parsing (shared with details page) ──

internal data class AudioAvailability(
    val hasSub: Boolean,
    val hasDub: Boolean,
    val hasHsub: Boolean,
) {
    val hasAny: Boolean get() = hasSub || hasDub || hasHsub
    val labels: List<String> get() = buildList {
        if (hasSub) add("SUB")
        if (hasDub) add("DUB")
        if (hasHsub) add("HSUB")
    }
}

internal fun parseAudioAvailability(scanlator: String?, episodeName: String): AudioAvailability {
    val haystack = ((scanlator ?: "") + " " + episodeName).uppercase()
    val hasHsub = haystack.contains("HSUB") || haystack.contains("HARDSUB")
    val hasSub = haystack.contains("SUB") && !hasHsub
    val hasDub = haystack.contains("DUB") && !hasHsub
    return AudioAvailability(hasSub = hasSub, hasDub = hasDub, hasHsub = hasHsub)
}

internal fun formatDate(epochMillis: Long): String {
    if (epochMillis <= 0) return ""
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(epochMillis))
}

/**
 * Builds the standardized `episode_key` for `WatchProgressStore` saves.
 *
 * Phase WP (episode_key standardization — PLAN §1.9): the standard format is
 * `"${mainId}|${padded_5_digit}"` where `mainId` is the content's stable UUID
 * (from `:core:content`) + `padded_5_digit` is the zero-padded episode number.
 * This format is STABLE across extension reinstalls, source URL changes, AND
 * backup/restore — unlike the old `"$sourceId|$epUrl"` format which broke when
 * `sourceId` changed.
 *
 * Fallback: if `mainId` is blank (shouldn't happen — WatchKey is constructed
 * with it), falls back to the old `"$sourceId|$epUrl"`-style key using the
 * episode number instead of the URL (best-effort — logged WARN so the gap is
 * visible). This is a safety net, not the intended path.
 *
 * @param mainId The content's stable `main_id` (UUID).
 * @param episodeNumber The current episode number (from `PlayerStateHolder`).
 */
internal fun buildEpisodeKey(mainId: String, episodeNumber: Float): String {
    if (mainId.isBlank()) {
        com.confused.anikuta.core.common.Logger.w("Anikuta:Feature:Watch") {
            "buildEpisodeKey — mainId is blank; falling back to non-standard key. " +
                "WatchKey should be constructed with mainId. (episodeNumber=$episodeNumber)"
        }
    }
    // Round 120 (D-746): delegated to the single shared builder (the format was
    // hand-rolled in three modules — one drift away from split progress rows).
    return com.confused.anikuta.core.watchprogress.EpisodeKeys.build(mainId, episodeNumber)
}

// ════════════════════════════════════════════════════════════════════════════
//  ROUND 122 (R8): episode-switch server/quality/audio CONTINUITY
//  Mirrors the downloads' pinned re-resolve matcher (app/download/ReResolver)
//  with a wider logged fallback ladder. Terminal fallback is the old
//  first-of-first behavior — the pick can never fail, only degrade.
// ════════════════════════════════════════════════════════════════════════════

/** The continuity pin captured from the episode being left. */
internal data class EpisodePin(val server: String, val audio: String, val quality: String)

/** The continuity pick's result (the video + where it came from, for logging/UI). */
internal data class PinnedPick(
    // ROUND 122-fix: the PLAY target is the resolver's flat ResolvedVideo (it
    // carries the extension `headers` mpv needs) — the structured ResolverVideo
    // row has `videoHeaders` but no `headers`, and the switch code reads
    // `video.headers`. The ladder picks a ResolverVideo row, then maps it back
    // to its flat twin by url (VideoResolver builds both from the same
    // Video.videoUrl, so the match is exact).
    val video: ResolvedVideo,
    val resolverVideo: ResolverVideo?,
    val serverName: String,
    val audioLabel: String,
)

/** Extracts the numeric resolution from a quality label ("1080p" → 1080). */
internal fun qualityNumber(quality: String): Int? =
    Regex("(\\d{3,4})p").find(quality)?.groupValues?.get(1)?.toIntOrNull()

/** Nearest-resolution video (ties → first); null quality targets take the first. */
internal fun nearestQualityVideo(videos: List<ResolverVideo>, targetQuality: String): ResolverVideo? {
    val target = qualityNumber(targetQuality) ?: return videos.firstOrNull()
    return videos.minByOrNull { video ->
        val q = qualityNumber(video.quality)
        if (q == null) Int.MAX_VALUE / 2 else kotlin.math.abs(q - target)
    }
}

internal fun pickPinnedVideo(
    servers: List<com.confused.anikuta.core.videoresolver.ResolverServer>,
    pin: EpisodePin?,
    flatFirst: ResolvedVideo,
    resolved: List<ResolvedVideo>,
): PinnedPick {
    // ROUND 122-fix: map a picked structured ResolverVideo row back to its flat
    // ResolvedVideo twin (same Video.videoUrl on both sides) so the caller keeps
    // its ResolvedVideo-based url/headers flow; the flat first-of-list is the
    // terminal fallback.
    fun playVideo(v: ResolverVideo): ResolvedVideo =
        resolved.firstOrNull { it.url == v.url } ?: flatFirst

    val fallback = PinnedPick(
        video = flatFirst,
        resolverVideo = servers.firstOrNull()?.audioVersions?.firstOrNull()?.videos?.firstOrNull(),
        serverName = "",
        audioLabel = "",
    )
    if (pin == null || (pin.server.isBlank() && pin.audio.isBlank() && pin.quality.isBlank())) {
        return fallback
    }
    // Flatten once: (serverName, audioLabel, video) rows in grouping order.
    val rows = servers.flatMap { s ->
        s.audioVersions.flatMap { av -> av.videos.map { Triple(s.name, av.label, it) } }
    }
    if (rows.isEmpty()) return fallback
    val eq = { a: String, b: String -> a.isNotBlank() && b.isNotBlank() && a.equals(b, ignoreCase = true) }

    // L1 — exact server + audio + quality (the ReResolver parity tier).
    rows.firstOrNull { (s, a, v) -> eq(s, pin.server) && eq(a, pin.audio) && eq(v.quality, pin.quality) }
        ?.let { (s, a, v) ->
            Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L1 exact server+audio+quality → $s/$a/${v.quality}" }
            return PinnedPick(playVideo(v), v, s, a)
        }
    // L2 — same server + same audio, nearest resolution.
    rows.filter { (s, a) -> eq(s, pin.server) && eq(a, pin.audio) }
        .let { same ->
            nearestQualityVideo(same.map { it.third }, pin.quality)?.let { v ->
                val hit = same.first { it.third === v || it.third.url == v.url }
                Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L2 same server+audio, nearest quality → ${hit.first}/${hit.second}/${v.quality}" }
                return PinnedPick(playVideo(v), v, hit.first, hit.second)
            }
        }
    // L3 — same server (prefer the same audio flavor when it survived), nearest resolution.
    rows.filter { (s, _) -> eq(s, pin.server) }
        .let { sameServer ->
            if (sameServer.isNotEmpty()) {
                val preferred = sameServer.filter { (_, a, _) -> eq(a, pin.audio) }
                    .ifEmpty { sameServer }
                nearestQualityVideo(preferred.map { it.third }, pin.quality)?.let { v ->
                    val hit = preferred.first { it.third.url == v.url }
                    Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L3 same server → ${hit.first}/${hit.second}/${v.quality}" }
                    return PinnedPick(playVideo(v), v, hit.first, hit.second)
                }
            }
        }
    // L4 — same audio flavor on any server (language kept, hoster changed).
    rows.filter { (_, a, _) -> eq(a, pin.audio) }
        .let { sameAudio ->
            nearestQualityVideo(sameAudio.map { it.third }, pin.quality)?.let { v ->
                val hit = sameAudio.first { it.third.url == v.url }
                Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L4 same audio → ${hit.first}/${hit.second}/${v.quality}" }
                return PinnedPick(playVideo(v), v, hit.first, hit.second)
            }
        }
    // L5 — the hoster renamed across episodes: fuzzy server match (prefix/
    // contains), smallest name difference, same audio preferred, nearest quality.
    if (pin.server.isNotBlank()) {
        val fuzzy = rows.filter { (s, _, _) ->
            s.startsWith(pin.server, ignoreCase = true) || s.contains(pin.server, ignoreCase = true)
        }.minByOrNull { (s, _, _) -> kotlin.math.abs(s.length - pin.server.length) }
        if (fuzzy != null) {
            val pool = rows.filter { (s, _, _) -> s == fuzzy.first }
            val preferred = pool.filter { (_, a, _) -> eq(a, pin.audio) }.ifEmpty { pool }
            nearestQualityVideo(preferred.map { it.third }, pin.quality)?.let { v ->
                val hit = preferred.first { it.third.url == v.url }
                Logger.i("Anikuta:Feature:Watch") { "Watch continuity: L5 fuzzy server '${pin.server}'→'${hit.first}' ${hit.second}/${v.quality}" }
                return PinnedPick(playVideo(v), v, hit.first, hit.second)
            }
        }
    }
    Logger.i("Anikuta:Feature:Watch") { "Watch continuity: no match (L6) — first-of-list fallback" }
    return fallback
}

// ════════════════════════════════════════════════════════════════════════════
//  Round 120 (D-746/D-747/D-748): the player overlays + the skip editor
// ════════════════════════════════════════════════════════════════════════════

/** The Up Next bundle handed to both player modes (built once at the call site). */
internal data class UpNextUi(
    val episode: SimpleEpisode,
    val label: String,
    val title: String,
    val thumbnailUrl: String?,
    val coverFallbackUrl: String,
    /** Null = no countdown (auto-play off); 0 = about to advance. */
    val countdown: Int?,
)

/** Which skip pill is showing (computed per position tick). */
internal sealed interface SkipPillState {
    data class Intro(val endSec: Int) : SkipPillState

    /** Round 121: [endSec] = AniSkip's ed end (seek there); null = the manual
     *  start-only outro (the pill jumps to the next episode). */
    data class Outro(val endSec: Int?) : SkipPillState
}

/**
 * The round-120 overlay layer over the player surface: the Up Next card
 * (bottom-end), the intro/outro skip pill (above it), and the resume
 * affordance (bottom-center). Pass-through for touches outside its children.
 */
@Composable
internal fun PlayerRound120Overlays(
    upNext: UpNextUi?,
    onPlayUpNext: () -> Unit,
    onDismissUpNext: () -> Unit,
    skipPill: SkipPillState?,
    onSkipPillAction: () -> Unit,
    resumePillSec: Long,
    onRestartFromStart: () -> Unit,
    // ROUND 122 (R4): the portrait (minimized) player hosts the card OUTSIDE
    // the 16:9 box now (full-width inset above the episode list) — the old
    // fullscreen-tuned geometry clipped it there.
    showUpNextCard: Boolean = true,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (showUpNextCard) {
            com.confused.anikuta.core.designsystem.component.player.UpNextCard(
                visible = upNext != null,
                episodeLabel = upNext?.label ?: "",
                episodeTitle = upNext?.title ?: "",
                thumbnailUrl = upNext?.thumbnailUrl,
                coverFallbackUrl = upNext?.coverFallbackUrl,
                countdownSecondsLeft = upNext?.countdown,
                countdownTotalSeconds = com.confused.anikuta.core.watchprogress.PlaybackResumePolicy.UP_NEXT_COUNTDOWN_SECONDS,
                onPlay = onPlayUpNext,
                onDismiss = onDismissUpNext,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    // ROUND 122 (R4): the card is fillMaxWidth now — cap it to
                    // its designed width here; the bottom nudge (76→84dp) is
                    // the user's "move the card a little up in landscape".
                    .widthIn(max = 300.dp)
                    .padding(end = 14.dp, bottom = 84.dp),
            )
        }

        skipPill?.let { pill ->
            SkipPill(
                label = when (pill) {
                    is SkipPillState.Intro -> "Skip intro"
                    // ROUND 121-fix: Outro became a data class — needs `is`.
                    is SkipPillState.Outro -> "Skip outro"
                },
                onClick = onSkipPillAction,
                // Sit above the Up Next card when both are showing (fullscreen
                // only — the portrait card no longer lives in this Box, so the
                // old 232dp stack offset is gone with it).
                bottomPadding = if (showUpNextCard && upNext != null) 240.dp else 76.dp,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }

        if (resumePillSec >= 0) {
            ResumePill(
                positionSec = resumePillSec,
                onRestart = onRestartFromStart,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp),
            )
        }
    }
}

/** The Aniyomi-style skip affordance — a compact glass pill at the bottom-end. */
@Composable
internal fun SkipPill(
    label: String,
    onClick: () -> Unit,
    bottomPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(50),
        modifier = modifier
            .padding(end = 14.dp, bottom = bottomPadding)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                label,
                color = Color.White,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/**
 * The D-746 resume affordance — "Resumed · mm:ss [Restart]", bottom-center for
 * a few seconds after a resume seek. Non-blocking (playback already resumed);
 * Restart is the escape hatch for "I wanted it fresh".
 */
@Composable
internal fun ResumePill(
    positionSec: Long,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(50),
        modifier = modifier.clip(RoundedCornerShape(50)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                "Resumed · ${formatPillTime(positionSec)}",
                color = Color.White,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "Restart",
                color = MaterialTheme.colorScheme.primary,
                fontFamily = RobotoFamily,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onRestart)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * The D-748 intro/outro marking sheet — stamps the CURRENT playback position as
 * the intro start/end or the outro start for this series (per-series memory:
 * openings share a length within a season, so one marking covers the rewatch).
 * Persisted through PlayerPreferences (round 120 scoped the database OUT).
 */
// ════════════════════════════════════════════════════════════════════════════
//  ROUND 122 (R7): PlayerEpisodesSheet — the fullscreen More button's episode
//  browser, REBUILT as a RIGHT-SIDE DRAWER (the device round: "instead of the
//  bottom-up menu, a left-right side menu should appear… from the right side…
//  not more than 40% of the screen"). Same content: the episode list (tap to
//  switch without leaving fullscreen) + the skip-timestamps entry.
// ════════════════════════════════════════════════════════════════════════════
@Composable
internal fun PlayerEpisodesSheet(
    episodes: List<SimpleEpisode>,
    currentUrl: String,
    onEpisodeSelect: (SimpleEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    val sorted = remember(episodes) { episodes.sortedBy { it.episodeNumber } }
    // The 40% cap: 40% of the real screen width, hard-capped so ultra-wide
    // landscape screens don't grow a ribbon-wide drawer.
    val configuration = LocalConfiguration.current
    val panelMaxWidth = with(androidx.compose.ui.platform.LocalDensity.current) {
        ((configuration.screenWidthDp * 0.4f).dp).coerceAtMost(340.dp)
    }
    val listState = rememberLazyListState()
    // Open ON the current episode — a 300-episode list must not open at ep 1.
    // ROUND 124 (R5): CENTER on it (the device round's ask) — an instant prime
    // jump first (the effect can run before the first layout lays items out),
    // then the shared centered settle; the helper no-ops on out-of-range
    // indices and clamps at the content edges, so the very first / very last
    // episode simply rests at its edge (never forced past it, no glitch).
    androidx.compose.runtime.LaunchedEffect(sorted, currentUrl) {
        val idx = sorted.indexOfFirst { it.url == currentUrl }
        if (idx > 0) listState.scrollToItem(idx)
        if (idx >= 0) listState.animateScrollToItemCentered(idx)
    }
    BackHandler(onBack = onDismiss)
    Box(modifier = Modifier.fillMaxSize()) {
        // The scrim — tap to close (no ripple; a quiet dismissal surface).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        )
        // The panel — slides in from the right edge (the MutableTransitionState
        // makes the ENTER animation run on composition; the composable is
        // conditionally mounted by the caller).
        val enterState = remember {
            MutableTransitionState(false).apply { targetState = true }
        }
        AnimatedVisibility(
            visibleState = enterState,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(260, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = panelMaxWidth)
                    .fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Episodes",
                                fontFamily = RobotoFamily,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                "${sorted.size} episodes — tap to play",
                                fontFamily = RobotoFamily,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // ROUND 124 (R5): the "Skip timestamps" entry is REMOVED
                        // outright (the device round: "why do we need this
                        // option at all? … completely remove it outright").
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 10.dp, vertical = 4.dp,
                        ),
                    ) {
                        items(
                            count = sorted.size,
                            key = { i -> sorted[i].url },
                        ) { i ->
                            val ep = sorted[i]
                            val isCurrent = ep.url == currentUrl
                            Surface(
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp),
                                onClick = { onEpisodeSelect(ep) },
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                                ) {
                                    Text(
                                        formatEpisodeNumber(ep.episodeNumber),
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        modifier = Modifier.width(46.dp),
                                    )
                                    Text(
                                        com.confused.anikuta.core.common.EpisodeTitleParser
                                            .getDisplayTitle(ep.name, ep.episodeNumber),
                                        fontFamily = RobotoFamily,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrent) FontWeight.Medium else FontWeight.Normal,
                                        color = if (isCurrent) {
                                            MaterialTheme.colorScheme.onBackground
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (isCurrent) {
                                        Text(
                                            "NOW",
                                            fontFamily = RobotoFamily,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** mm:ss (or h:mm:ss for the long ones) — the pills' compact time format. */
internal fun formatPillTime(totalSec: Long): String {
    val s = totalSec.coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) {
        String.format("%d:%02d:%02d", h, m, sec)
    } else {
        String.format("%d:%02d", m, sec)
    }
}

/**
 * Builds the video-caching identity for the currently playing video.
 *
 * Video caching (test-feature branch): the cache key must be STABLE across sessions
 * for the same server+audio+quality pick — extension localhost proxy URLs change
 * every resolve (D-066), so identity comes from mainId + episodeNumber + sourceId +
 * the ResolverVideo.videoTitle's "server|audio|quality" prefix (videoTitle is the
 * codebase's documented stable-identity string — see ResolverTypes.kt).
 *
 * Returns null when a stable identity can't be derived (no videoTitle / blank
 * mainId) → caching is skipped for that playback (fail-open, play direct).
 */
internal fun buildCacheId(
    mainId: String,
    animeTitle: String,
    episodeNumber: Float,
    episodeTitle: String,
    sourceId: Long,
    videoTitle: String,
): PlaybackVideoId? {
    if (mainId.isBlank()) return null
    val serverKey = serverKeyFromVideoTitle(videoTitle)
    if (serverKey.isBlank()) return null
    return PlaybackVideoId(
        mainId = mainId,
        animeTitle = animeTitle,
        episodeNumber = episodeNumber,
        episodeTitle = episodeTitle,
        sourceId = sourceId,
        serverKey = serverKey,
        quality = serverKey.substringAfterLast('|'),
    )
}

// ════════════════════════════════════════════════════════════════════════════
//  Phase 4: Star Rating Bar (TEMPORARY — for testing)
// ════════════════════════════════════════════════════════════════════════════

/**
 * A row of 10 clickable stars for per-episode rating. Each star = 10 points.
 * Tapping a star sets the rating. Tapping the same star again clears it.
 */
@Composable
internal fun WatchStarRatingBar(
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

