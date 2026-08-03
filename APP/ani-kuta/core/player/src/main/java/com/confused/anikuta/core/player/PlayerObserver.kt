package com.confused.anikuta.core.player

import com.confused.anikuta.core.common.Logger

/**
 * Observer for MPV events. Translates MPV callbacks into [PlayerStateHolder] updates.
 *
 * The host (WatchScreen/Activity) registers this observer's methods as callbacks
 * on the MPV view. MPV calls back on its own thread — the observer just pushes
 * the values into StateFlows (thread-safe).
 *
 * CRITICAL (loading-failed overlay fix): The previous version did not clear the
 * error state on FILE_LOADED. When a video failed once (e.g. 403), then the user
 * picked a different quality/server, the new video would load + play audio, but
 * the "Loading failed" overlay stayed on screen because the error state was
 * never cleared. Now [onEvent] for FILE_LOADED clears the error + sets READY.
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
    }

    /**
     * The MPV view — set by the host so we can call loadTracks() on FILE_LOADED.
     * Without this, the subtitle/audio track lists never populate.
     */
    var mpvView: AnikutaMPVView? = null

    /**
     * Called when an MPV property changes.
     * The host registers this as the property-change callback.
     */
    fun onProperty(property: String, value: String) {
        Logger.v(TAG) { "Property: $property = $value" }

        when (property) {
            "time-pos" -> {
                value.toIntOrNull()?.let { stateHolder.updatePosition(it) }
            }
            "duration" -> {
                value.toIntOrNull()?.let { stateHolder.updateDuration(it) }
            }
            "pause" -> {
                stateHolder.updatePlaying(value != "yes")
            }
            "paused-for-cache" -> {
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
                // This fires when sub-add/audio-add commands complete, or when
                // a new file's tracks are discovered.
                loadTracksFromMpv()
            }
        }
    }

    /**
     * Called when an MPV event occurs.
     * The host registers this as the event callback.
     */
    fun onEvent(eventId: Int) {
        Logger.v(TAG) { "Event: $eventId" }

        when (eventId) {
            MPV_EVENT_FILE_LOADED -> {
                Logger.i(TAG) { "File loaded — clearing error, setting READY" }
                // CRITICAL: Clear any previous error state so the "Loading failed"
                // overlay doesn't persist when a new video successfully loads.
                stateHolder.updateError(null)
                stateHolder.updateLoadingState(PlayerLoadingState.READY)
                stateHolder.updateBuffering(false)
                // Load tracks now that the file is loaded.
                loadTracksFromMpv()
            }
            MPV_EVENT_START_FILE -> {
                Logger.i(TAG) { "Start file — loading" }
                stateHolder.updateLoadingState(PlayerLoadingState.LOADING)
            }
            MPV_EVENT_END_FILE -> {
                Logger.i(TAG) { "End file" }
                // Don't set ERROR here — END_FILE fires normally when the user
                // switches quality/server. Only the efEvent callback (error
                // loading file) should set the error state.
            }
            MPV_EVENT_TRACKS_CHANGED -> {
                Logger.d(TAG) { "Tracks changed" }
                loadTracksFromMpv()
            }
        }
    }

    /**
     * Read the current track-list from MPV and push it to the state holder.
     * Safe to call from any thread — StateFlow is thread-safe.
     */
    private fun loadTracksFromMpv() {
        val view = mpvView ?: return
        try {
            val (subs, audio) = view.loadTracks()
            stateHolder.updateTracks(subs, audio)
            Logger.d(TAG) { "Loaded ${subs.size} sub tracks, ${audio.size} audio tracks" }
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to load tracks: ${e.message}" }
        }
    }
}
