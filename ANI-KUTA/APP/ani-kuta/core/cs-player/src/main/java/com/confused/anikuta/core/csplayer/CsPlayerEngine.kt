package com.confused.anikuta.core.csplayer

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaItem.SubtitleConfiguration
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Buffer state mirrored from ExoPlayer (IDLE → BUFFERING → READY → ENDED). */
enum class CsBufferState { IDLE, BUFFERING, READY, ENDED }

/** Observable engine state — the Compose layer renders this directly. */
data class CsEngineState(
    val isPlaying: Boolean = false,
    val playWhenReady: Boolean = false,
    val bufferState: CsBufferState = CsBufferState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val playbackSpeed: Float = 1f,
    /** The currently loaded link's URL (diagnostics + the links sheet highlight). */
    val currentLinkUrl: String? = null,
)

/** Error classification for the error-overlay / next-link fallback decision. */
data class CsPlaybackError(
    val kind: Kind,
    val httpCode: Int? = null,
    val errorCodeName: String? = null,
    val message: String? = null,
) {
    enum class Kind { NETWORK, HTTP, PARSING, DECODING, DRM, UNSPECIFIED }

    val isRetryableElsewhere: Boolean
        get() = kind == Kind.NETWORK || kind == Kind.HTTP || kind == Kind.PARSING
}

/** One selectable video format inside the current stream (an HLS/DASH variant or the single file). */
data class CsVideoTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val height: Int?,
    val label: String,
)

/** One selectable audio track inside the current stream (DASH multi-audio etc.). */
data class CsAudioTrackInfo(
    val groupIndex: Int,
    val trackIndex: Int,
    val id: String?,
    val label: String,
    val language: String?,
)

/** One selectable text track (sidecar subtitle OR embedded). */
data class CsTextTrack(
    val groupIndex: Int,
    val trackIndex: Int,
    val id: String?,
    val name: String,
    val language: String?,
    val embedded: Boolean,
)

/** Engine events the SCREEN must react to (errors → next-link fallback; ended → next episode). */
sealed interface CsEngineEvent {
    data class PlaybackError(
        val error: CsPlaybackError,
        /** Full upstream-style diagnostic line — url/referer/headers/position/duration. */
        val diagnostics: String,
        /** R12-REVIEW F5: the URL the error belongs to — lets the screen reject
         *  STALE errors after a link switch instead of failing the fresh pick. */
        val linkUrl: String?,
    ) : CsEngineEvent

    data object Ended : CsEngineEvent
}

/**
 * The CloudStream playback engine (task 52 / round 12) — a Media3 ExoPlayer
 * host that ports the upstream CS3IPlayer pattern (research R12-A §5):
 *
 *  link → MediaItem(url + mime) → DefaultMediaSourceFactory(per-link OkHttp
 *  DataSource) → sidecar subs as SingleSampleMediaSource → external audio
 *  merged → setMediaSource(position-aware) → prepare.
 *
 * Threading: the engine is created, called and released on the MAIN thread
 * (ExoPlayer's threading rule); the resolver produces its inputs on IO before
 * [start] is ever invoked.
 */
@OptIn(UnstableApi::class)
class CsPlayerEngine(
    context: Context,
    baseClient: OkHttpClient,
    private val defaultUserAgent: String = CsPlayerDefaults.USER_AGENT,
    /**
     * Task 55: preferred subtitle languages (comma-separated) — supplied by the
     * screen from PlayerPreferences (the engine stays preference-free). On the
     * first READY of each load, one matching sidecar/embedded track is
     * auto-selected (the MPV `slang` behavior parity). Default: English.
     */
    private val preferredSubtitleLanguages: () -> String = { "en,eng,english" },
) {
    companion object {
        internal const val TAG = "Anikuta:CS:Player"

        /** Subtitle-specific events get their own tag — the one-filter recipe
         *  (doc cloudstream-v2/02) covers the whole pipeline via Anikuta:CS:*. */
        internal const val SUBS_TAG = "Anikuta:CS:Subs"
    }

    private val dataSourceFactory = CsHttpDataSourceFactory(baseClient, defaultUserAgent)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** What's loaded right now (for link switching + error diagnostics). */
    private data class CurrentPlayback(
        val link: CsVideoLink,
        val subtitles: List<CsSubtitle>,
    )

    private var current: CurrentPlayback? = null
    private var tickerJob: Job? = null

    /** Task 53 / RC-2: one clean-profile retry per link load (see CsHttpDataSourceFactory). */
    private var cleanRetryUsed = false

    /** True once the CURRENT load reached READY — mid-playback errors are NOT clean-retry candidates. */
    private var reachedReady = false

    /** Task 55: one-shot gate for the first-READY preferred-subtitle auto-select. */
    private var autoSubSelectAttempted = false

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context))
        .build()
        .apply {
            // Audio focus: pause competing apps, duck on notifications — the
            // standard media-player contract (upstream sets the same).
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus = */ true,
            )
        }

    // ── State + events ────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(CsEngineState())
    val state: StateFlow<CsEngineState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<CsEngineEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CsEngineEvent> = _events.asSharedFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _state.value = _state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _state.value = _state.value.copy(playWhenReady = playWhenReady)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val mapped = when (playbackState) {
                    Player.STATE_BUFFERING -> CsBufferState.BUFFERING
                    Player.STATE_READY -> CsBufferState.READY
                    Player.STATE_ENDED -> CsBufferState.ENDED
                    else -> CsBufferState.IDLE
                }
                _state.value = _state.value.copy(bufferState = mapped, durationMs = safeDuration())
                if (mapped == CsBufferState.READY) {
                    reachedReady = true
                    // Track census — the subtitle/audio diagnosis baseline.
                    val groups = player.currentTracks.groups
                    var videoGroups = 0
                    var audioGroups = 0
                    var textGroups = 0
                    groups.forEach { g ->
                        when (g.type) {
                            C.TRACK_TYPE_VIDEO -> videoGroups++
                            C.TRACK_TYPE_AUDIO -> audioGroups++
                            C.TRACK_TYPE_TEXT -> textGroups++
                        }
                    }
                    Logger.i(TAG) {
                        "READY: track groups video=$videoGroups audio=$audioGroups text=$textGroups " +
                            "(url=${_state.value.currentLinkUrl?.take(64)})"
                    }
                    // Task 55: MPV `slang` parity — auto-select a preferred-language
                    // subtitle track once per load (only when nothing is selected).
                    maybeAutoSelectPreferredSubtitles()
                }
                if (mapped == CsBufferState.ENDED) {
                    Logger.i(TAG) { "playback ENDED (url=${_state.value.currentLinkUrl})" }
                    _events.tryEmit(CsEngineEvent.Ended)
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                _state.value = _state.value.copy(
                    videoWidth = videoSize.width,
                    videoHeight = videoSize.height,
                )
            }

            override fun onPlaybackParametersChanged(params: androidx.media3.common.PlaybackParameters) {
                _state.value = _state.value.copy(playbackSpeed = params.speed)
            }

            override fun onPlayerError(error: PlaybackException) {
                val playback = current
                val csError = classify(error)
                // Task 53 / RC-2: a 4xx at OPEN time (never reached READY) on the
                // upstream profile → one automatic CLEAN retry (no UA override,
                // referer dropped). Empirically resurrects CDNs like hcdn3
                // (browser UA → 428, referer → 429, clean profile → 206).
                if (playback != null && !cleanRetryUsed && !reachedReady &&
                    csError.kind == CsPlaybackError.Kind.HTTP &&
                    (csError.httpCode ?: 0) in 400..499
                ) {
                    cleanRetryUsed = true
                    val keepAt = if (_state.value.durationMs > 0) player.currentPosition else 0L
                    Logger.w(TAG) {
                        "playerError http=${csError.httpCode} before READY → CLEAN RETRY " +
                            "(client-default UA, referer dropped) '${playback.link.displayLabel}' " +
                            "keeping position=${keepAt}ms"
                    }
                    startInternal(playback.link, playback.subtitles, keepAt, clean = true)
                    return
                }
                // The upstream diagnostic gold standard (GeneratorPlayer.playerError):
                // every field a stream diagnosis needs, on one line.
                val diagnostics = buildString {
                    append("playerError: url=${playback?.link?.url ?: "unknown"}")
                    append(", type=${error::class.java.simpleName}")
                    append(", code=${error.errorCodeName}")
                    csError.httpCode?.let { append(", http=$it") }
                    append(", message=${error.message}")
                    append(", referer=${playback?.link?.referer ?: "none"}")
                    append(", headers=${playback?.link?.headers?.keys ?: "none"}")
                    append(", position=${player.currentPosition}")
                    append(", duration=${if (player.duration == C.TIME_UNSET) "unset" else player.duration}")
                    append(", isPlaying=${player.isPlaying}")
                    append(", linkName=${playback?.link?.displayLabel ?: "none"}")
                    if (cleanRetryUsed) append(", cleanRetry=alreadyTried")
                }
                Logger.e(TAG, error) { diagnostics }
                _events.tryEmit(CsEngineEvent.PlaybackError(csError, diagnostics, playback?.link?.url))
            }
        })
    }

    // ── Playback control ──────────────────────────────────────────────────────

    /**
     * Loads [link] with its [subtitles] and starts playback (upstream header profile).
     *
     * @param startPositionMs resume position; 0 = let ExoPlayer pick the default
     *   position (for live M3U8/DASH that is the live edge — the upstream
     *   `playbackPosition = TIME_UNSET` nuance, research R12-A §5).
     */
    fun start(link: CsVideoLink, subtitles: List<CsSubtitle>, startPositionMs: Long = 0L) {
        startInternal(link, subtitles, startPositionMs, clean = false)
    }

    /** Re-loads [link] keeping the current position (quality/source switch UX). */
    fun switchLink(link: CsVideoLink, subtitles: List<CsSubtitle>) {
        val keepAt = if (_state.value.durationMs > 0) player.currentPosition else 0L
        Logger.i(TAG) { "switchLink → ${link.displayLabel} keeping position=${keepAt}ms" }
        startInternal(link, subtitles, keepAt, clean = false)
    }

    /**
     * The one real loader. [clean] selects the retry request profile (RC-2):
     * no User-Agent override + referer dropped — see CsHttpDataSourceFactory.
     * Every load logs its full outgoing request profile (diagnosability).
     */
    private fun startInternal(
        link: CsVideoLink,
        subtitles: List<CsSubtitle>,
        startPositionMs: Long,
        clean: Boolean,
    ) {
        current = CurrentPlayback(link, subtitles)
        cleanRetryUsed = clean
        reachedReady = false
        autoSubSelectAttempted = false

        val headersOut = if (clean) {
            link.allHeaders.filterKeys { !it.equals("referer", ignoreCase = true) }
        } else {
            link.allHeaders
        }
        val uaOut = when {
            clean -> "client-default"
            link.userAgent != null -> link.userAgent!!.take(48)
            else -> defaultUserAgent.take(48)
        }
        Logger.i(TAG) {
            "start[profile=${if (clean) "clean" else "upstream"}]: ${link.displayLabel} type=${link.type} " +
                "ua=$uaOut headers=${headersOut.entries.joinToString(",") { "${it.key}=${it.value.take(24)}" }} " +
                "url=${link.url.take(96)} subs=${subtitles.size} audio=${link.audioTracks.size} resumeMs=$startPositionMs"
        }

        val mime = CsMediaTypes.mimeFor(link.type)
        val mediaItem = MediaItem.Builder().setUri(link.url).setMimeType(mime).build()
        val videoSource: MediaSource =
            DefaultMediaSourceFactory(
                if (clean) dataSourceFactory.forLinkClean(link) else dataSourceFactory.forLink(link),
            ).createMediaSource(mediaItem)

        // Sidecar subtitles — each with its OWN DataSource (per-sub headers),
        // exactly like upstream getSubSources (research R12-A §6).
        // Task 55: the config now carries a human LABEL (the display name — the
        // v0.4.2 round showed raw URLs because the sheet fell back to the id)
        // and the content-sniffed mime when the resolver detected one.
        Logger.i(SUBS_TAG) {
            "attaching ${subtitles.size} sidecar subtitle(s): " +
                subtitles.joinToString { "${it.displayName}(${(it.sniffedMime ?: it.mimeType).substringAfterLast('/')})" }
        }
        val subSources: List<MediaSource> = subtitles.mapNotNull { sub ->
            runCatching {
                val config: SubtitleConfiguration = SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(sub.sniffedMime ?: sub.mimeType)
                    // Upstream language trick: "_" prefix keeps custom names valid BCP-47-ish.
                    .setLanguage(sub.languageTag ?: "_${sub.displayName}")
                    .setLabel(sub.displayName)
                    .setId(sub.id)
                    .setSelectionFlags(0)
                    .build()
                SingleSampleMediaSource.Factory(dataSourceFactory.forSubtitle(sub))
                    .createMediaSource(config, C.TIME_UNSET)
            }.onFailure {
                Logger.w(SUBS_TAG, it) { "subtitle source dropped: ${sub.displayName} (${sub.url.take(64)})" }
            }.getOrNull()
        }

        // External audio tracks (upstream getAudioSources pattern).
        val audioSources: List<MediaSource> = link.audioTracks.mapNotNull { audio ->
            runCatching {
                val item = MediaItem.Builder().setUri(audio.url).build()
                DefaultMediaSourceFactory(dataSourceFactory.forAudioTrack(audio)).createMediaSource(item)
            }.onFailure {
                Logger.w(TAG, it) { "audio source dropped: ${audio.url.take(64)}" }
            }.getOrNull()
        }

        val merged: MediaSource = when {
            subSources.isEmpty() && audioSources.isEmpty() -> videoSource
            else -> MergingMediaSource(videoSource, *(subSources + audioSources).toTypedArray())
        }

        _state.value = _state.value.copy(currentLinkUrl = link.url)
        player.setMediaSource(merged, if (startPositionMs > 0) startPositionMs else C.TIME_UNSET)
        player.prepare()
        player.playWhenReady = true
        startTicker()
        Logger.d(TAG) { "prepared: mime=$mime subSources=${subSources.size} audioSources=${audioSources.size} merged=${subSources.isNotEmpty() || audioSources.isNotEmpty()}" }
    }

    /**
     * Task 53 / RC-3: hard-stops the engine and clears its media — the watch
     * screen calls this whenever a NEW episode's resolution starts, so no
     * stale content can keep playing under the resolving overlay.
     */
    fun reset() {
        Logger.i(TAG) { "reset: stopping + clearing (had=${current?.link?.displayLabel ?: "nothing"})" }
        cleanRetryUsed = false
        reachedReady = false
        autoSubSelectAttempted = false
        current = null
        tickerJob?.cancel()
        player.stop()
        player.clearMediaItems()
        _state.value = _state.value.copy(
            isPlaying = false,
            playWhenReady = false,
            bufferState = CsBufferState.IDLE,
            positionMs = 0L,
            durationMs = 0L,
            bufferedMs = 0L,
            currentLinkUrl = null,
        )
    }

    fun playPause() {
        player.playWhenReady = !player.playWhenReady
    }

    /** Pauses WITHOUT toggling (episode-switch transitions — the old episode's
     *  audio must not keep playing under the resolving overlay). */
    fun pause() {
        player.playWhenReady = false
    }

    fun seekTo(positionMs: Long) {
        if (_state.value.durationMs > 0) {
            player.seekTo(positionMs.coerceIn(0L, _state.value.durationMs))
        } else {
            player.seekTo(positionMs)
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        player.playbackParameters = androidx.media3.common.PlaybackParameters(speed)
    }

    fun release() {
        Logger.i(TAG) { "release" }
        tickerJob?.cancel()
        scope.cancel()
        player.release()
    }

    // ── Track selection (quality + subtitles) ────────────────────────────────

    /** Video formats inside the current stream — the quality rows of the sheet. */
    fun videoTracks(): List<CsVideoTrack> {
        val out = mutableListOf<CsVideoTrack>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_VIDEO) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                out += CsVideoTrack(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    height = format.height.takeIf { it > 0 },
                    label = format.height.takeIf { it > 0 }?.let { CsQuality.label(it) } ?: "Auto",
                )
            }
        }
        return out
    }

    /**
     * Text tracks = sidecar subtitles (ours, keyed by [CsSubtitle.id]) + tracks
     * embedded in the container. `embedded` drives the sheet's section split.
     * Task 55: the row NAME is the label (sidecar display names / embedded
     * labels) or the language's display name — NEVER the format id (which is
     * a URL for sidecars; that was the v0.4.2 "rows show URLs" bug).
     */
    fun textTracks(): List<CsTextTrack> {
        val sidecarIds = current?.subtitles?.map { it.id }?.toSet() ?: emptySet()
        val out = mutableListOf<CsTextTrack>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val id = format.id
                out += CsTextTrack(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    id = id,
                    name = format.label
                        ?: format.language?.let { CsLanguageNames.display(it) }
                        ?: "Track ${out.size + 1}",
                    language = format.language,
                    embedded = id == null || id !in sidecarIds,
                )
            }
        }
        return out
    }

    /** Audio tracks embedded in the current stream (DASH multi-audio). */
    fun audioTracks(): List<CsAudioTrackInfo> {
        val out = mutableListOf<CsAudioTrackInfo>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_AUDIO) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                out += CsAudioTrackInfo(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    id = format.id,
                    label = format.label ?: format.language ?: "Audio ${out.size + 1}",
                    language = format.language,
                )
            }
        }
        return out
    }

    /** Selects an embedded audio track (null = auto). */
    fun selectAudioTrack(track: CsAudioTrackInfo?) {
        if (track == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .build()
            Logger.i(TAG) { "audio track override cleared (auto)" }
            return
        }
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
        Logger.i(TAG) { "audio track selected: ${track.label} (g${track.groupIndex}/t${track.trackIndex})" }
    }

    /** Picks a specific video format (null = clear override, back to auto/ABR). */
    fun selectVideoTrack(track: CsVideoTrack?) {
        val group = player.currentTracks.groups.getOrNull(track?.groupIndex ?: -1)
        if (track == null || group == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .build()
            Logger.i(TAG) { "video track override cleared (auto)" }
            return
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .build()
        Logger.i(TAG) { "video track selected: ${track.label} (g${track.groupIndex}/t${track.trackIndex})" }
    }

    /** Caps the auto-selection at [height] px (null = uncapped) — the upstream preferred-quality behavior. */
    fun setMaxVideoHeight(height: Int?) {
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setMaxVideoSize(Int.MAX_VALUE, height ?: Int.MAX_VALUE)
            .build()
        Logger.i(TAG) { "maxVideoHeight=$height" }
    }

    /**
     * Selects a text track (null = subtitles OFF).
     *
     * Task 55 — STALE-INDEX FIX: the sheet snapshots [CsTextTrack] at open
     * time; any re-prepare between that and the click shifts group/track
     * indices, and an override built from the SNAPSHOT indices landed on the
     * wrong (or non-text) group — the v0.4.2 "clicked a subtitle, nothing
     * applied" bug. The indices are now re-resolved LIVE from
     * `player.currentTracks` by matching `format.id` first; the snapshot
     * indices are only the fallback when the track has no id.
     */
    fun selectTextTrack(track: CsTextTrack?) {
        if (track == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            Logger.i(SUBS_TAG) { "subtitles OFF" }
            return
        }
        val live = resolveTextTrackIndices(track)
        val group = player.currentTracks.groups.getOrNull(live?.first ?: track.groupIndex)
        val trackIndex = live?.second ?: track.trackIndex
        if (group == null || group.type != C.TRACK_TYPE_TEXT) {
            Logger.w(SUBS_TAG) {
                "track selection REJECTED: snapshot indices stale " +
                    "(g${track.groupIndex}/t${track.trackIndex} id=${track.id?.take(32)} no longer a text group)"
            }
            return
        }
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
        Logger.i(SUBS_TAG) { "subtitle selected: ${track.name} (embedded=${track.embedded})" }
    }

    /**
     * LIVE index resolution for a track: walks `player.currentTracks` and
     * returns (groupIndex, trackIndex) of the text track whose format id
     * matches — null when the id is null or no longer present.
     */
    private fun resolveTextTrackIndices(track: CsTextTrack): Pair<Int, Int>? {
        val wanted = track.id ?: return null
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                if (group.getTrackFormat(trackIndex).id == wanted) return groupIndex to trackIndex
            }
        }
        return null
    }

    /**
     * Task 55 — MPV `slang` parity. On the FIRST READY of each load (and only
     * when no text track is selected yet), auto-selects the first track whose
     * language/name matches the preferred list. Sidecars win over embedded
     * (provider subs are usually better timed than container subs). Never
     * forces a selection when nothing matches.
     */
    private fun maybeAutoSelectPreferredSubtitles() {
        if (autoSubSelectAttempted) return
        autoSubSelectAttempted = true
        if (selectedTextTrackId() != null) return // already selected (reattach/user)
        val preferred = preferredSubtitleLanguages()
        // 1) sidecar subs (ours — languageTag or the provider's lang name).
        current?.subtitles?.firstOrNull { sub ->
            CsLanguageNames.matchesPreferred(sub.languageTag ?: sub.name, preferred)
        }?.let { sub ->
            if (selectTextTrackById(sub.id)) {
                Logger.i(SUBS_TAG) { "auto-selected preferred subtitle (sidecar): ${sub.displayName}" }
                return
            }
        }
        // 2) embedded tracks (language/label match).
        textTracks().firstOrNull { it.embedded && CsLanguageNames.matchesPreferred(it.language, preferred) }?.let {
            selectTextTrack(it)
            Logger.i(SUBS_TAG) { "auto-selected preferred subtitle (embedded): ${it.name}" }
        }
    }

    /**
     * Task 55 — applies the user's subtitle STYLE (the PlayerPreferences values
     * the aniyomi MPV stack uses, mapped onto Media3's [androidx.media3.ui.SubtitleView]).
     * Call at surface creation + live whenever the settings sheet writes.
     */
    fun applySubtitleStyle(view: PlayerView, style: CsSubtitleStyle) {
        val subtitleView = view.subtitleView ?: return
        subtitleView.setApplyEmbeddedStyles(false)
        subtitleView.setApplyEmbeddedFontSizes(false)
        val typeface = Typeface.create(
            Typeface.SANS_SERIF,
            when {
                style.bold && style.italic -> Typeface.BOLD_ITALIC
                style.bold -> Typeface.BOLD
                style.italic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            },
        )
        // Edge: the border setting wins (outline); a shadow offset alone → shadow.
        val edgeType = when {
            style.borderSize > 0 -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
            style.shadowOffset > 0 -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
            else -> CaptionStyleCompat.EDGE_TYPE_NONE
        }
        val caption = CaptionStyleCompat(
            style.textColor,
            style.backgroundColor,
            android.graphics.Color.TRANSPARENT, // windowColor — always transparent
            edgeType,
            style.borderColor,
            typeface,
        )
        subtitleView.setStyle(caption)
        // MPV's sub-font-size (20..100, default 55) → Media3's fractional text
        // size (default 0.0533 of the viewport height).
        subtitleView.setFractionalTextSize(0.0533f * (style.fontSize / 55f))
        // MPV sub-pos (0..100, 100 = flush bottom) → bottom padding fraction.
        subtitleView.setBottomPaddingFraction(((100 - style.position) / 100f) * 0.12f)
    }

    /** Selects a text track by its format id (sidecar subs carry [CsSubtitle.id]); true when found+selected. */
    fun selectTextTrackById(id: String): Boolean {
        val track = textTracks().firstOrNull { it.id == id } ?: return false
        selectTextTrack(track)
        return true
    }

    /** The id of the currently selected text track (for the sheet's highlight), or null when OFF. */
    fun selectedTextTrackId(): String? {
        player.currentTracks.groups.forEach { group ->
            if (group.type != C.TRACK_TYPE_TEXT || !group.isSelected) return@forEach
            for (i in 0 until group.length) {
                if (group.isTrackSelected(i)) return group.getTrackFormat(i).id
            }
        }
        return null
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private fun safeDuration(): Long =
        if (player.duration == C.TIME_UNSET || player.duration < 0) 0L else player.duration

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                val st = _state.value
                _state.value = st.copy(
                    positionMs = player.currentPosition.coerceAtLeast(0L),
                    durationMs = safeDuration(),
                    bufferedMs = player.bufferedPosition.coerceAtLeast(0L),
                )
                delay(200)
            }
        }
    }

    private fun classify(error: PlaybackException): CsPlaybackError {
        // Bounded cause-walk (cause cycles exist in the wild; generateSequence
        // would spin forever on one).
        var cause: Throwable? = error
        var httpCode: Int? = null
        var depth = 0
        while (cause != null && depth < 6) {
            if (cause is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                httpCode = cause.responseCode
                break
            }
            cause = cause.cause
            depth++
        }
        val kind = when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> CsPlaybackError.Kind.HTTP
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            -> CsPlaybackError.Kind.NETWORK

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            -> CsPlaybackError.Kind.PARSING

            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            -> CsPlaybackError.Kind.DECODING

            PlaybackException.ERROR_CODE_DRM_SCHEME_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
            PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
            -> CsPlaybackError.Kind.DRM

            else -> CsPlaybackError.Kind.UNSPECIFIED
        }
        return CsPlaybackError(
            kind = kind,
            httpCode = httpCode,
            errorCodeName = error.errorCodeName,
            message = error.message,
        )
    }
}

/** Engine-wide constants. */
object CsPlayerDefaults {
    /**
     * Desktop Chrome — upstream CloudStream's USER_AGENT constant, byte-parity
     * (their player sends this when a link carries no UA of its own).
     * Task 53 / RC-2: the previous Mobile-Chrome default was the direct cause
     * of the 428 class on CDNs that reject browser UAs.
     */
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36"
}
