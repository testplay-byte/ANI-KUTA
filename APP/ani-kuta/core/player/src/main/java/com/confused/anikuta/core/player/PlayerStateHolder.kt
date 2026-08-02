package com.confused.anikuta.core.player

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
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
 * CORE_RULES §23: All state is reactive (StateFlow) — UI updates automatically.
 * CORE_RULES §20: All updates logged with tag "Anikuta:Core:Player:State".
 */
class PlayerStateHolder {

    companion object {
        private const val TAG = "Anikuta:Core:Player:State"
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

    // ── Playback state ──
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _position = MutableStateFlow(0)
    val position: StateFlow<Int> = _position.asStateFlow()

    private val _duration = MutableStateFlow(0)
    val duration: StateFlow<Int> = _duration.asStateFlow()

    private val _buffering = MutableStateFlow(false)
    val buffering: StateFlow<Boolean> = _buffering.asStateFlow()

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

    // ── Update methods (called by PlayerObserver) ──

    fun updateMode(mode: PlayerMode) {
        Logger.d(TAG) { "Mode → $mode" }
        _playerMode.value = mode
    }

    fun updateLoadingState(state: PlayerLoadingState) {
        Logger.d(TAG) { "LoadingState → $state" }
        _loadingState.value = state
    }

    fun updateError(message: String?) {
        Logger.w(TAG) { "Error → $message" }
        _errorMessage.value = message
        if (message != null) _loadingState.value = PlayerLoadingState.ERROR
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
        _errorMessage.value = null
        _loadingState.value = PlayerLoadingState.READY
        _subtitleTracks.value = emptyList()
        _audioTracks.value = emptyList()
        _currentSubtitleTrack.value = -1
        _currentAudioTrack.value = -1
    }
}
