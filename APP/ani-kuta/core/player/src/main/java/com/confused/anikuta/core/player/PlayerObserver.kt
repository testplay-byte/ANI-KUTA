package com.confused.anikuta.core.player

import com.confused.anikuta.core.common.Logger
import `is`.xyz.mpv.MPVLib
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Observer for MPV events. Translates MPV callbacks into [PlayerStateHolder] updates.
 *
 * The host (WatchScreen/Activity) registers this observer's methods as callbacks
 * on the MPV view. MPV calls back on its own thread — the observer just pushes
 * the values into StateFlows (thread-safe).
 *
 * ## Logging strategy (ported from old project)
 *
 * - MPV events: logged at INFO level with human-readable event names.
 * - MPV property changes: only logged at VERBOSE (time-pos fires ~4x/sec, so
 *   we don't log those at DEBUG to avoid log spam).
 * - HTTP errors from MPV logs: captured into [PlayerStateHolder.httpError] and
 *   appended to the next efEvent error message.
 * - FILE_LOADED: clears error + switching flag + sets READY + loads tracks.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Player:Observer".
 */
class PlayerObserver(
    private val stateHolder: PlayerStateHolder,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Player:Observer"

        // MPV event IDs (from mpv/client.h)
        private const val MPV_EVENT_START_FILE = 6
        private const val MPV_EVENT_END_FILE = 7
        private const val MPV_EVENT_FILE_LOADED = 11
        private const val MPV_EVENT_TRACKS_CHANGED = 26
        private const val MPV_EVENT_SHUTDOWN = 0
        private const val MPV_EVENT_LOG_MESSAGE = 1
        private const val MPV_EVENT_IDLE = 12
        private const val MPV_EVENT_TICK = 27

        /** Delay (ms) after sending sub-add/audio-add before reloading tracks.
         *  Without this, loadTracksFromMpv() runs before MPV has registered the
         *  new tracks → external subtitles don't appear in the sheet. */
        private const val EXTERNAL_TRACK_LOAD_DELAY_MS = 300L
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The MPV view — set by the host so we can call loadTracks() on FILE_LOADED.
     * Without this, the subtitle/audio track lists never populate.
     */
    var mpvView: AnikutaMPVView? = null

    /**
     * External subtitle tracks to load on the next FILE_LOADED.
     * Set by the host before calling `loadfile` (from the picked video's tracks).
     * Each entry is (url, lang).
     */
    var pendingSubtitleTracks: List<Pair<String, String>> = emptyList()

    /**
     * External audio tracks to load on the next FILE_LOADED.
     * Each entry is (url, lang).
     */
    var pendingAudioTracks: List<Pair<String, String>> = emptyList()

    /**
     * HTTP headers (MPV `http-header-fields` format) for downloading external
     * tracks. Set by the host from the current video's headers on EVERY video
     * change (quality switch, episode switch) — not just once.
     *
     * CRITICAL: this is set BEFORE `sub-add` so MPV uses the right headers for
     * the HTTPS subtitle download. We restore the video's headers afterward
     * (MPV keeps the last-set http-header-fields, so we just re-set them before
     * the next loadfile from the host).
     */
    var trackHeaders: String = ""

    /**
     * Called when an MPV property changes.
     * The host registers this as the property-change callback.
     *
     * NOTE: time-pos fires ~4x/sec — we log it at VERBOSE only to avoid spam.
     */
    fun onProperty(property: String, value: String) {
        // Only log non-noisy properties at DEBUG. time-pos, demuxer-cache-time
        // are too frequent to log at DEBUG.
        when (property) {
            "time-pos", "demuxer-cache-time" -> {
                Logger.v(TAG) { "Property: $property = $value" }
            }
            else -> {
                Logger.d(TAG) { "Property: $property = $value" }
            }
        }

        when (property) {
            "time-pos" -> {
                value.toIntOrNull()?.let { stateHolder.updatePosition(it) }
            }
            "demuxer-cache-time" -> {
                // Buffer-ahead time — how much of the video is demuxed/cached.
                // Used by the seekbar to render the buffer zone (colored segment).
                value.toIntOrNull()?.let { stateHolder.updateBufferAheadTime(it) }
            }
            "duration" -> {
                value.toIntOrNull()?.let { stateHolder.updateDuration(it) }
            }
            "pause" -> {
                stateHolder.updatePlaying(value != "yes")
                // CRITICAL: When the video starts playing (pause = no), clear
                // buffering. This fixes the quality switch spinner staying visible
                // after the video starts playing. The `paused-for-cache` property
                // may not fire `no` immediately after playback starts.
                if (value != "yes") {
                    stateHolder.updateBuffering(false)
                }
            }
            "paused-for-cache" -> {
                stateHolder.updateBuffering(value == "yes")
            }
            "seeking" -> {
                // Treat seeking as buffering — while MPV is seeking, the video
                // is not playing and the user should see a spinner.
                stateHolder.updateBuffering(value == "yes")
            }
            "sid" -> {
                value.toIntOrNull()?.let { sid ->
                    stateHolder.updateCurrentTracks(sid, stateHolder.currentAudioTrack.value)
                }
            }
            "aid" -> {
                value.toIntOrNull()?.let { aid ->
                    stateHolder.updateCurrentTracks(stateHolder.currentSubtitleTrack.value, aid)
                }
            }
            "speed" -> {
                value.toFloatOrNull()?.let { stateHolder.updatePlaybackSpeed(it) }
            }
            "track-list/count" -> {
                // Track list changed — reload tracks from MPV.
                loadTracksFromMpv()
            }
        }
    }

    /**
     * Called when an MPV event occurs.
     * The host registers this as the event callback.
     *
     * Events are logged at INFO with human-readable names so the user can
     * share logs and we can diagnose issues.
     */
    fun onEvent(eventId: Int) {
        val eventName = eventName(eventId)
        Logger.i(TAG) { "Event: $eventName ($eventId)" }

        when (eventId) {
            MPV_EVENT_FILE_LOADED -> {
                Logger.i(TAG) { "✓ File loaded — clearing error, setting READY, loading tracks" }
                // CRITICAL: Clear switching flag + error state so the "Loading failed"
                // overlay doesn't persist when a new video successfully loads.
                stateHolder.setSwitching(false)
                stateHolder.updateError(null)
                stateHolder.updateLoadingState(PlayerLoadingState.READY)
                stateHolder.updateBuffering(false)
                // Load external tracks (sub-add / audio-add) BEFORE reading the
                // track list — external tracks need to be registered first.
                loadExternalTracks()
                // SAFETY: Also schedule a delayed track reload 5s later. Some
                // formats (especially HLS with separate subtitle tracks) take
                // a moment to fully parse. External subs also need time to
                // download over HTTPS. If the first loadTracksFromMpv() runs
                // before MPV has finished parsing/downloading, the track list
                // is empty. This retry catches that case.
                scope.launch {
                    delay(5_000L)
                    Logger.i(TAG) { "Delayed track reload (5s safety)" }
                    loadTracksFromMpv()
                }
            }
            MPV_EVENT_START_FILE -> {
                Logger.i(TAG) { "Start file — loading" }
                stateHolder.updateLoadingState(PlayerLoadingState.LOADING)
            }
            MPV_EVENT_END_FILE -> {
                // END_FILE fires normally when switching quality/server.
                // Do NOT set ERROR here — only efEvent indicates a real load failure.
                Logger.i(TAG) { "End file (normal — switching or finished)" }
            }
            MPV_EVENT_TRACKS_CHANGED -> {
                Logger.d(TAG) { "Tracks changed — reloading" }
                loadTracksFromMpv()
            }
            MPV_EVENT_IDLE -> {
                Logger.w(TAG) { "MPV idle (no file loaded)" }
            }
            MPV_EVENT_SHUTDOWN -> {
                Logger.w(TAG) { "MPV shutdown" }
            }
            else -> {
                Logger.v(TAG) { "Unhandled event: $eventName ($eventId)" }
            }
        }
    }

    /**
     * Called when MPV reports a log message. The host routes MPV's LogObserver
     * callback to this method.
     *
     * CRITICAL: Capture HTTP errors from MPV logs. When MPV fails to load a URL,
     * it logs an HTTP error line BEFORE firing efEvent. We capture this and store
     * it in [PlayerStateHolder.httpError] so it can be appended to the error
     * message shown to the user.
     *
     * @param prefix MPV log prefix (e.g. "stream", "http", "demux")
     * @param level MPV log level (20=info, 30=warn, 40=error, 10=v, 0=trace)
     * @param text The log message text.
     */
    fun onLogMessage(prefix: String, level: Int, text: String) {
        // Route by severity — matches old project's PlayerObserver.
        when (level) {
            in 40..Int.MAX_VALUE -> Logger.e(TAG) { "MPV [$prefix] ERROR: $text" }
            30 -> Logger.w(TAG) { "MPV [$prefix] WARN: $text" }
            20 -> Logger.i(TAG) { "MPV [$prefix] INFO: $text" }
            else -> Logger.v(TAG) { "MPV [$prefix]: $text" }
        }

        // Capture specific errors for the efEvent message — gives the user
        // a actionable error instead of just "loading failed".
        val lowerText = text.lowercase()
        when {
            // TLS / SSL errors (app-side — usually a cacert.pem or config issue)
            lowerText.contains("mbedtls") || lowerText.contains("tls:") || lowerText.contains("ssl") -> {
                stateHolder.setHttpError("[$prefix] $text")
            }
            // HTTP status errors (source-side — server returned an error)
            lowerText.contains("http error") || lowerText.contains("403") ||
                lowerText.contains("404") || lowerText.contains("500") -> {
                stateHolder.setHttpError("[$prefix] $text")
            }
            // Connection errors (network-side)
            lowerText.contains("connection refused") || lowerText.contains("network unreachable") ||
                lowerText.contains("timed out") || lowerText.contains("failed to open") -> {
                stateHolder.setHttpError("[$prefix] $text")
            }
        }
    }

    /**
     * Called when MPV fires efEvent (error loading file).
     * The host registers this as the efEvent callback.
     *
     * CRITICAL: Only set the error if NOT switching episodes. During a switch,
     * efEvent fires for the OLD file ending — that's not a real error.
     * [PlayerStateHolder.updateError] checks the switching flag and suppresses
     * the error if switching.
     */
    fun onEfEvent(err: String?) {
        Logger.e(TAG) { "efEvent (load failure): $err" }
        stateHolder.updateError(err ?: "Unknown playback error")
    }

    /**
     * Load external subtitle and audio tracks via MPV's `sub-add` / `audio-add`
     * commands.
     *
     * CRITICAL: Must be called AFTER `FILE_LOADED` — sending `sub-add` before
     * the file is loaded causes MPV to silently drop the track.
     *
     * Each `sub-add` triggers an HTTPS download in MPV's native code, so this
     * runs on Dispatchers.IO. After sending all commands, we wait
     * [EXTERNAL_TRACK_LOAD_DELAY_MS] before calling [loadTracksFromMpv] so MPV
     * has time to register the tracks — otherwise the track sheet shows stale
     * data and the external subtitles don't appear until the next event.
     *
     * Ported from the old project's external-track loading logic.
     */
    private fun loadExternalTracks() {
        val subs = pendingSubtitleTracks
        val audios = pendingAudioTracks
        Logger.i(TAG) { "loadExternalTracks: ${subs.size} subs, ${audios.size} audio pending" }
        if (subs.isEmpty() && audios.isEmpty()) {
            // No external tracks — just load internal tracks directly.
            loadTracksFromMpv()
            return
        }

        // Clear pending tracks so they don't get re-added on the next FILE_LOADED
        // (e.g. after a seek that reloads the file). The host re-sets them before
        // each loadfile if needed.
        pendingSubtitleTracks = emptyList()
        pendingAudioTracks = emptyList()

        scope.launch {
            try {
                // Set headers for the track downloads (subtitles often need
                // the same Referer/User-Agent as the video).
                if (trackHeaders.isNotBlank()) {
                    runCatching {
                        MPVLib.setOptionString("http-header-fields", trackHeaders)
                    }
                    Logger.i(TAG) { "Set http-header-fields for ${subs.size} external sub downloads + ${audios.size} audio" }
                } else {
                    Logger.w(TAG) { "trackHeaders is blank — external subs may fail to download (403)" }
                }

                for ((url, lang) in subs) {
                    Logger.i(TAG) { "Sending sub-add: url=${url.take(80)}... lang=$lang" }
                    runCatching {
                        MPVLib.command(arrayOf("sub-add", url, "auto", "", lang))
                    }.onSuccess {
                        Logger.i(TAG) { "sub-add sent OK: ${url.take(60)}..." }
                    }.onFailure {
                        Logger.w(TAG) { "sub-add FAILED for ${url.take(60)}: ${it.message}" }
                    }
                }
                for ((url, lang) in audios) {
                    Logger.i(TAG) { "Sending audio-add: url=${url.take(80)}... lang=$lang" }
                    runCatching {
                        MPVLib.command(arrayOf("audio-add", url, "auto", "", lang))
                    }.onSuccess {
                        Logger.i(TAG) { "audio-add sent OK: ${url.take(60)}..." }
                    }.onFailure {
                        Logger.w(TAG) { "audio-add FAILED for ${url.take(60)}: ${it.message}" }
                    }
                }

                // Wait for MPV to register the tracks before reading the list.
                // 500ms — external subs need time to download (HTTPS) + register.
                delay(500L)
                Logger.i(TAG) { "External tracks sent — loading track list from MPV" }
                loadTracksFromMpv()
            } catch (e: Exception) {
                Logger.e(TAG, e) { "loadExternalTracks failed" }
                loadTracksFromMpv()
            }
        }
    }

    /**
     * Read the current track-list from MPV and push it to the state holder.
     * Safe to call from any thread — StateFlow is thread-safe.
     */
    private fun loadTracksFromMpv() {
        val view = mpvView
        if (view == null) {
            Logger.w(TAG) { "loadTracksFromMpv: mpvView is null — skipping" }
            return
        }
        try {
            val trackCount = view.getTrackCount()
            val (subs, audio) = view.loadTracks()
            stateHolder.updateTracks(subs, audio)
            Logger.i(TAG) { "Tracks loaded: ${subs.size} subs, ${audio.size} audio (MPV track-list/count=$trackCount)" }
            if (subs.isEmpty() && audio.isEmpty() && trackCount == 0) {
                Logger.w(TAG) { "No tracks detected — video may not have embedded subtitles. External subs need sub-add (see loadExternalTracks)." }
            }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to load tracks: ${e.message}" }
        }
    }

    /**
     * Convert an MPV event ID to a human-readable name for logging.
     */
    private fun eventName(id: Int): String = when (id) {
        0 -> "SHUTDOWN"
        1 -> "LOG_MESSAGE"
        2 -> "GET_PROPERTY_REPLY"
        3 -> "SET_PROPERTY_REPLY"
        4 -> "COMMAND_REPLY"
        5 -> "START_FILE_READ"
        6 -> "START_FILE"
        7 -> "END_FILE"
        8 -> "FILE_ERROR"
        9 -> "IDLE"
        10 -> "TICK"
        11 -> "FILE_LOADED"
        12 -> "IDLE"
        13 -> "CACHE_UPDATE"
        14 -> "AUDIO_RECONFIG"
        15 -> "VIDEO_RECONFIG"
        16 -> "SEEK"
        17 -> "PLAYBACK_RESTART"
        18 -> "PROPERTY_CHANGE"
        19 -> "QUEUE_OVERFLOW"
        20 -> "HOOK"
        21 -> "RENDER"
        22 -> "SEND_COMMAND_REPLY"
        26 -> "TRACKS_CHANGED"
        27 -> "TICK"
        else -> "UNKNOWN($id)"
    }
}
