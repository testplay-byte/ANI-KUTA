package com.confused.anikuta.core.player

import com.confused.anikuta.core.common.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared state holder for the player — used by both the watch page's mini-player
 * and the fullscreen player. This is a plain class (NOT a ViewModel) so it can
 * be owned by the screen-level composable and shared across mode switches
 * without recreation.
 *
 * Per ADR-025: the MPV view is never recreated on mode switches. This state
 * holder is also never recreated — it persists across MINIMIZED ↔ FULLSCREEN
 * transitions.
 *
 * The host (Activity / WatchScreen) pushes MPV events into this holder via
 * the `update*` methods. The UI observes the [StateFlow]s.
 *
 * ## Error handling (ported from old project + stuck-loading fix)
 *
 * Two error methods:
 * - [updateError] — for efEvent (MPV load failure). SUPPRESSED while switching
 *   because the old file's END_FILE fires during a switch — that's not a real
 *   error. This is the old project's behavior.
 * - [setSwitchingError] — for resolve/loadfile failures (no videos, resolve
 *   error, exception). ALWAYS shown — these are real failures that need user
 *   attention. This method clears the switching flag AND sets the error in one
 *   call, so the player never gets stuck in loading when a switch fails.
 *
 * The 30s switching-timeout watchdog (in WatchScreen) is a safety net: if
 * `isSwitching` stays true for 30s, it calls `setSwitchingError("timeout")`.
 *
 * - `httpError` — captured from MPV log messages (HTTP error lines), appended
 *   to the error message for better diagnostics.
 *
 * CORE_RULES §23: All state is reactive (StateFlow) — UI updates automatically.
 * CORE_RULES §20: All updates logged with tag "Anikuta:Core:Player:State".
 */
class PlayerStateHolder {

    companion object {
        private const val TAG = "Anikuta:Core:Player:State"

        /**
         * Task 48: how long a deferred (during-switch) error waits for the
         * switch to complete before surfacing. Must stay well under the 30s
         * switching-timeout watchdog; 8s covers slow first-byte hosts while
         * surfacing a dead swapped link 3-4x sooner than the old 30s wait.
         */
        private const val DEFERRED_ERROR_GRACE_MS = 8_000L
    }

    private val scope = CoroutineScope(SupervisorJob())

    // ── Player mode ──
    private val _playerMode = MutableStateFlow(PlayerMode.MINIMIZED)
    val playerMode: StateFlow<PlayerMode> = _playerMode.asStateFlow()

    // ── Loading / error ──
    private val _loadingState = MutableStateFlow(PlayerLoadingState.READY)
    val loadingState: StateFlow<PlayerLoadingState> = _loadingState.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Episode switching flag (prevents spurious errors during switches) ──
    private val _isSwitching = MutableStateFlow(false)
    val isSwitching: StateFlow<Boolean> = _isSwitching.asStateFlow()

    // ── Episode-switch-specific flag (shows the "Loading episode..." overlay) ──
    // Quality/server switches set isSwitching (for error suppression) but NOT
    // isSwitchingEpisode (no overlay). Episode switches set BOTH. This way,
    // quality switches don't show the "Loading episode..." overlay — they just
    // show the buffering spinner.
    private val _isSwitchingEpisode = MutableStateFlow(false)
    val isSwitchingEpisode: StateFlow<Boolean> = _isSwitchingEpisode.asStateFlow()

    // ── HTTP error captured from MPV logs (appended to efEvent message) ──
    @Volatile
    var httpError: String? = null
        private set

    // ── Auto-retry tracking ──
    // When an error occurs, the player auto-retries the same URL once before
    // showing the error banner. This flag tracks whether the auto-retry has
    // been attempted. Reset when a new video is loaded (setSwitching(true)).
    @Volatile
    var autoRetryAttempted: Boolean = false
        private set

    // ── Task 48: error GENERATION counter ──
    // Increments on every SURFACED error event (updateError with a real
    // message, or setSwitchingError). The watch screen's recovery ladder
    // keys its effect on (errorMessage, errorGeneration) so that a SECOND
    // failure with the IDENTICAL message string (e.g. the same 403 twice in
    // a row from the same URL) still re-triggers the ladder — a plain
    // LaunchedEffect(errorMessage) would not re-run for an unchanged string.
    private val _errorGeneration = MutableStateFlow(0)
    val errorGeneration: StateFlow<Int> = _errorGeneration.asStateFlow()

    // ── Task 48: deferred switch-error surfacing ──
    // While isSwitching, updateError() SUPPRESSES errors (the old file's
    // teardown can fire a spurious efEvent). But if the error actually comes
    // from the NEW file failing to load, the user used to wait for the 30s
    // switching-timeout watchdog. The error is now DEFERRED: if the switch
    // completes (switching cleared) within DEFERRED_ERROR_GRACE_MS it was
    // spurious and is dropped; otherwise it is surfaced as a switching
    // error (which also drives the recovery ladder's next step).
    @Volatile
    private var deferredSwitchError: String? = null
    private var deferredErrorJob: kotlinx.coroutines.Job? = null

    // ── Playback state ──
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

    // ── Buffer-ahead time (for seekbar buffer zone) ──
    private val _bufferAheadTime = MutableStateFlow(0)
    val bufferAheadTime: StateFlow<Int> = _bufferAheadTime.asStateFlow()

    // ── "Buffered enough" flag — true when the video has buffered at least 1%
    //    of its duration ahead of the current position. Used to clear the loading
    //    spinner during switches. Once we've buffered even 1%, we know the video
    //    is loading successfully and the spinner can hide.
    @Volatile
    var bufferedEnough: Boolean = false
        private set

    // ── Controls visibility ──
    private val _controlsVisible = MutableStateFlow(false)
    val controlsVisible: StateFlow<Boolean> = _controlsVisible.asStateFlow()

    private val _controlsLocked = MutableStateFlow(false)
    val controlsLocked: StateFlow<Boolean> = _controlsLocked.asStateFlow()

    // ── Tracks ──
    private val _subtitleTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val subtitleTracks: StateFlow<List<VideoTrack>> = _subtitleTracks.asStateFlow()

    private val _audioTracks = MutableStateFlow<List<VideoTrack>>(emptyList())
    val audioTracks: StateFlow<List<VideoTrack>> = _audioTracks.asStateFlow()

    private val _currentSubtitleTrack = MutableStateFlow(-1)
    val currentSubtitleTrack: StateFlow<Int> = _currentSubtitleTrack.asStateFlow()

    private val _currentAudioTrack = MutableStateFlow(-1)
    val currentAudioTrack: StateFlow<Int> = _currentAudioTrack.asStateFlow()

    // ── Playback speed ──
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // ── Current episode state (hoisted from WatchKey so it updates on switch) ──
    // WatchKey is immutable (Nav3 contract) — these fields track the CURRENTLY
    // PLAYING episode so the UI (episode list highlight, "now playing" card,
    // QualitySheet servers) reflects what's actually playing after a switch.
    private val _currentEpisodeUrl = MutableStateFlow("")
    val currentEpisodeUrl: StateFlow<String> = _currentEpisodeUrl.asStateFlow()

    private val _currentEpisodeNumber = MutableStateFlow(0f)
    val currentEpisodeNumber: StateFlow<Float> = _currentEpisodeNumber.asStateFlow()

    private val _currentEpisodeTitle = MutableStateFlow("")
    val currentEpisodeTitle: StateFlow<String> = _currentEpisodeTitle.asStateFlow()

    // The registry key for the CURRENT episode's resolved servers (QualitySheet).
    // Updated on episode switch so the QualitySheet shows the new episode's servers.
    private val _currentResolvedVideosKey = MutableStateFlow("")
    val currentResolvedVideosKey: StateFlow<String> = _currentResolvedVideosKey.asStateFlow()

    // ── Update methods (called by PlayerObserver) ──

    fun updateMode(mode: PlayerMode) {
        Logger.d(TAG) { "Mode → $mode" }
        _playerMode.value = mode
    }

    fun updateLoadingState(state: PlayerLoadingState) {
        Logger.d(TAG) { "LoadingState → $state" }
        _loadingState.value = state
    }

    /**
     * Set the error message. Called from efEvent (MPV load failure).
     *
     * CRITICAL: Only sets the error if NOT switching episodes. During a switch
     * (quality change, episode change), efEvent fires for the OLD file ending
     * — that's not a real error. The old project checks `isSwitchingEpisode`
     * before setting the error state.
     */
    fun updateError(message: String?) {
        if (message != null) {
            if (_isSwitching.value) {
                // Task 48: defer instead of dropping — see deferredSwitchError.
                Logger.d(TAG) { "Error deferred (switching): $message" }
                deferredSwitchError = message
                scheduleDeferredErrorSurfacing()
                return
            }
            // Append HTTP error context if available.
            val fullMessage = if (httpError != null) {
                "$message\nHTTP: $httpError"
            } else {
                message
            }
            Logger.w(TAG) { "Error → $fullMessage" }
            _errorMessage.value = fullMessage
            _loadingState.value = PlayerLoadingState.ERROR
            _errorGeneration.value++
        } else {
            Logger.d(TAG) { "Error cleared" }
            _errorMessage.value = null
            if (_loadingState.value == PlayerLoadingState.ERROR) {
                _loadingState.value = PlayerLoadingState.READY
            }
        }
    }

    /**
     * Task 48: starts (or restarts) the deferred-error grace timer. If the
     * switching flag is still up when it expires, the deferred error is real
     * (the new file failed) and is surfaced via [setSwitchingError].
     */
    private fun scheduleDeferredErrorSurfacing() {
        deferredErrorJob?.cancel()
        deferredErrorJob = scope.launch {
            delay(DEFERRED_ERROR_GRACE_MS)
            val deferred = deferredSwitchError
            if (deferred != null && _isSwitching.value) {
                Logger.w(TAG) { "Deferred switch error surfaced after ${DEFERRED_ERROR_GRACE_MS}ms: $deferred" }
                setSwitchingError(deferred)
            }
        }
    }

    /**
     * Task 48: clears a deferred error — called when a switch SUCCEEDS (the
     * switching flag drops / loading reaches READY), meaning the pending
     * error was the old file's spurious teardown event.
     */
    private fun clearDeferredError() {
        deferredSwitchError = null
        deferredErrorJob?.cancel()
        deferredErrorJob = null
    }

    /**
     * Set the switching flag. When true, efEvent errors are suppressed.
     * Cleared on FILE_LOADED (the new file successfully started) or by
     * [setSwitchingError] when a switch fails.
     *
     * For quality/server switches, call this WITHOUT [setSwitchingEpisode] —
     * the overlay won't show, just the buffering spinner.
     */
    fun setSwitching(switching: Boolean) {
        Logger.d(TAG) { "Switching → $switching" }
        _isSwitching.value = switching
        if (switching) {
            // Clear any previous error when starting a switch.
            _errorMessage.value = null
            httpError = null
            _loadingState.value = PlayerLoadingState.LOADING
            // Reset auto-retry flag for the new video.
            autoRetryAttempted = false
            // Reset bufferedEnough — the new video hasn't buffered yet.
            bufferedEnough = false
            // Task 48: a fresh switch drops any stale deferred error — but the
            // switch ITSELF may register a new one via updateError().
            clearDeferredError()
        } else {
            // Task 48: the switch completed successfully — any deferred error
            // was the old file's teardown noise.
            clearDeferredError()
            // Also clear episode-switch flag when clearing switching.
            _isSwitchingEpisode.value = false
        }
    }

    /**
     * Set the episode-switch flag. When true, the EpisodeSwitchingOverlay
     * ("Loading episode...") is shown. Call this IN ADDITION to [setSwitching]
     * for episode switches. Quality/server switches should NOT call this —
     * they just show the buffering spinner.
     */
    fun setSwitchingEpisode(switching: Boolean) {
        Logger.d(TAG) { "SwitchingEpisode → $switching" }
        _isSwitchingEpisode.value = switching
    }

    /**
     * Mark that an auto-retry has been attempted (so we don't retry infinitely).
     */
    fun markAutoRetryAttempted() {
        autoRetryAttempted = true
    }

    /**
     * Clear the error WITHOUT clearing the switching flag (used by auto-retry
     * before re-sending loadfile).
     */
    fun clearErrorForRetry() {
        _errorMessage.value = null
        httpError = null
        _loadingState.value = PlayerLoadingState.LOADING
    }

    /**
     * Set a switching error — clears the switching flag AND shows the error.
     *
     * Use this for REAL failures during a switch: no videos found, resolve
     * error, exception, timeout. Unlike [updateError], this is NEVER suppressed
     * — the user needs to see it and the loading spinner must stop.
     *
     * This fixes the stuck-in-loading regression where `setSwitching(true)` +
     * `updateError()` suppression left the player in a perpetual spinner with
     * no error and no recovery path.
     */
    fun setSwitchingError(message: String) {
        Logger.w(TAG) { "Switching error: $message" }
        _isSwitching.value = false
        _isSwitchingEpisode.value = false
        clearDeferredError()
        val fullMessage = if (httpError != null) {
            "$message\nHTTP: $httpError"
        } else {
            message
        }
        _errorMessage.value = fullMessage
        _loadingState.value = PlayerLoadingState.ERROR
        // Task 48: switching errors drive the recovery ladder too.
        _errorGeneration.value++
    }

    /**
     * Seed the current-episode state from the WatchKey (called once on init).
     */
    fun seedEpisodeState(url: String, number: Float, title: String, resolvedVideosKey: String) {
        _currentEpisodeUrl.value = url
        _currentEpisodeNumber.value = number
        _currentEpisodeTitle.value = title
        _currentResolvedVideosKey.value = resolvedVideosKey
    }

    /**
     * Update the current-episode state after an episode switch.
     */
    fun updateCurrentEpisode(url: String, number: Float, title: String, resolvedVideosKey: String) {
        Logger.d(TAG) { "Current episode → num=$number url=${url.take(60)}" }
        _currentEpisodeUrl.value = url
        _currentEpisodeNumber.value = number
        _currentEpisodeTitle.value = title
        _currentResolvedVideosKey.value = resolvedVideosKey
    }

    /**
     * Update the resolved-videos key after an episode switch (QualitySheet reads this).
     */
    fun updateResolvedVideosKey(key: String) {
        _currentResolvedVideosKey.value = key
    }

    /**
     * Capture an HTTP error from MPV log messages.
     * Stored in [httpError] and appended to the next efEvent error message.
     */
    fun setHttpError(error: String?) {
        if (error != null) {
            Logger.w(TAG) { "HTTP error captured: $error" }
            httpError = error
        } else {
            httpError = null
        }
    }

    fun updatePlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun updatePosition(position: Int) {
        _position.value = position
    }

    fun updateDuration(duration: Int) {
        _duration.value = duration
    }

    fun updateBuffering(buffering: Boolean) {
        _buffering.value = buffering
    }

    fun updateBufferAheadTime(time: Int) {
        _bufferAheadTime.value = time
        // CRITICAL: Check if we've buffered enough (1% of duration ahead of position).
        // If so, mark bufferedEnough = true. This is used to clear the loading spinner
        // during quality/episode switches. Once we've buffered even 1%, we know the
        // video is loading successfully and the spinner can hide.
        // User: "If the video has buffered 1%, then the loading animation will go away."
        val dur = _duration.value
        val pos = _position.value
        if (dur > 0 && time > pos) {
            val onePercent = (dur * 0.01f).toInt().coerceAtLeast(1)
            if (time - pos >= onePercent) {
                if (!bufferedEnough) {
                    Logger.d(TAG) { "Buffered enough: cache-time=$time, pos=$pos, 1%=$onePercent" }
                    bufferedEnough = true
                }
            }
        }
    }

    fun updateControlsVisible(visible: Boolean) {
        _controlsVisible.value = visible
    }

    fun updateControlsLocked(locked: Boolean) {
        _controlsLocked.value = locked
    }

    fun updateTracks(subtitles: List<VideoTrack>, audio: List<VideoTrack>) {
        Logger.d(TAG) { "Tracks: ${subtitles.size} subs, ${audio.size} audio" }
        _subtitleTracks.value = subtitles
        _audioTracks.value = audio
    }

    fun updateCurrentTracks(subtitleId: Int, audioId: Int) {
        _currentSubtitleTrack.value = subtitleId
        _currentAudioTrack.value = audioId
    }

    fun updatePlaybackSpeed(speed: Float) {
        Logger.d(TAG) { "Speed → $speed" }
        _playbackSpeed.value = speed
    }

    /**
     * Reset all state (when loading a new video).
     */
    fun reset() {
        Logger.d(TAG) { "Reset state" }
        _position.value = 0
        _duration.value = 0
        _isPlaying.value = false
        _buffering.value = false
        _bufferAheadTime.value = 0
        _errorMessage.value = null
        httpError = null
        clearDeferredError()
        _isSwitching.value = false
        _loadingState.value = PlayerLoadingState.READY
        _subtitleTracks.value = emptyList()
        _audioTracks.value = emptyList()
        _currentSubtitleTrack.value = -1
        _currentAudioTrack.value = -1
    }
}
