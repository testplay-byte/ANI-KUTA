package com.confused.anikuta.core.csplayer

import android.content.Context
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
    defaultUserAgent: String = CsPlayerDefaults.USER_AGENT,
) {
    companion object {
        internal const val TAG = "Anikuta:CS:Player"
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

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context))
        .build()

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
                }
                Logger.e(TAG, error) { diagnostics }
                _events.tryEmit(CsEngineEvent.PlaybackError(csError, diagnostics))
            }
        })
    }

    // ── Playback control ──────────────────────────────────────────────────────

    /**
     * Loads [link] with its [subtitles] and starts playback.
     *
     * @param startPositionMs resume position; 0 = let ExoPlayer pick the default
     *   position (for live M3U8/DASH that is the live edge — the upstream
     *   `playbackPosition = TIME_UNSET` nuance, research R12-A §5).
     */
    fun start(link: CsVideoLink, subtitles: List<CsSubtitle>, startPositionMs: Long = 0L) {
        Logger.i(TAG) {
            "start: ${link.displayLabel} type=${link.type} url=${link.url.take(96)} " +
                "subs=${subtitles.size} audio=${link.audioTracks.size} resumeMs=$startPositionMs"
        }
        current = CurrentPlayback(link, subtitles)

        val mime = CsMediaTypes.mimeFor(link.type)
        val mediaItem = MediaItem.Builder().setUri(link.url).setMimeType(mime).build()
        val videoSource: MediaSource =
            DefaultMediaSourceFactory(dataSourceFactory.forLink(link)).createMediaSource(mediaItem)

        // Sidecar subtitles — each with its OWN DataSource (per-sub headers),
        // exactly like upstream getSubSources (research R12-A §6).
        val subSources: List<MediaSource> = subtitles.mapNotNull { sub ->
            runCatching {
                val config: SubtitleConfiguration = SubtitleConfiguration.Builder(Uri.parse(sub.url))
                    .setMimeType(sub.mimeType)
                    // Upstream language trick: "_" prefix keeps custom names valid BCP-47-ish.
                    .setLanguage(sub.languageTag ?: "_${sub.name}")
                    .setId(sub.id)
                    .setSelectionFlags(0)
                    .build()
                SingleSampleMediaSource.Factory(dataSourceFactory.forSubtitle(sub))
                    .createMediaSource(config, C.TIME_UNSET)
            }.onFailure {
                Logger.w(TAG, it) { "subtitle source dropped: ${sub.name} (${sub.url.take(64)})" }
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

    /** Re-loads [link] keeping the current position (quality/source switch UX). */
    fun switchLink(link: CsVideoLink, subtitles: List<CsSubtitle>) {
        val keepAt = if (_state.value.durationMs > 0) player.currentPosition else 0L
        Logger.i(TAG) { "switchLink → ${link.displayLabel} keeping position=${keepAt}ms" }
        start(link, subtitles, keepAt)
    }

    fun playPause() {
        player.playWhenReady = !player.playWhenReady
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
            if (group.type != Player.TRACK_TYPE_VIDEO) return@forEachIndexed
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
     */
    fun textTracks(): List<CsTextTrack> {
        val sidecarIds = current?.subtitles?.map { it.id }?.toSet() ?: emptySet()
        val out = mutableListOf<CsTextTrack>()
        player.currentTracks.groups.forEachIndexed { groupIndex, group ->
            if (group.type != Player.TRACK_TYPE_TEXT) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                val format = group.getTrackFormat(trackIndex)
                val id = format.id
                out += CsTextTrack(
                    groupIndex = groupIndex,
                    trackIndex = trackIndex,
                    id = id,
                    name = format.label ?: id ?: "Track ${groupIndex + 1}",
                    language = format.language,
                    embedded = id == null || id !in sidecarIds,
                )
            }
        }
        return out
    }

    /** Picks a specific video format (null = clear override, back to auto/ABR). */
    fun selectVideoTrack(track: CsVideoTrack?) {
        val group = player.currentTracks.groups.getOrNull(track?.groupIndex ?: -1)
        if (track == null || group == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(Player.TRACK_TYPE_VIDEO)
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

    /** Selects a text track (null = subtitles OFF). */
    fun selectTextTrack(track: CsTextTrack?) {
        if (track == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(Player.TRACK_TYPE_TEXT, true)
                .build()
            Logger.i(TAG) { "subtitles OFF" }
            return
        }
        val group = player.currentTracks.groups.getOrNull(track.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearTrackTypeDisabled(Player.TRACK_TYPE_TEXT)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, track.trackIndex))
            .setTrackTypeDisabled(Player.TRACK_TYPE_TEXT, false)
            .build()
        Logger.i(TAG) { "subtitle selected: ${track.name} (embedded=${track.embedded})" }
    }

    /** The id of the currently selected text track (for the sheet's highlight), or null when OFF. */
    fun selectedTextTrackId(): String? {
        player.currentTracks.groups.forEach { group ->
            if (group.type != Player.TRACK_TYPE_TEXT || !group.isSelected) return@forEach
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

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_FAILED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_FAILED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            -> CsPlaybackError.Kind.PARSING

            PlaybackException.ERROR_CODE_DECODING_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_INIT_FAILED,
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
    /** Mobile Chrome UA — the same one the app's network stack identifies with. */
    const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
}
