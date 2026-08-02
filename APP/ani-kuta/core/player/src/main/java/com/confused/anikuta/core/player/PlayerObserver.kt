package com.confused.anikuta.core.player

import com.confused.anikuta.core.common.Logger

/**
 * Observer for MPV events. Translates MPV callbacks into [PlayerStateHolder] updates.
 *
 * The host (WatchScreen/Activity) registers this observer's methods as callbacks
 * on the MPV view. MPV calls back on its own thread — the observer just pushes
 * the values into StateFlows (thread-safe).
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
                Logger.i(TAG) { "File loaded" }
                stateHolder.updateLoadingState(PlayerLoadingState.READY)
                stateHolder.updateBuffering(false)
            }
            MPV_EVENT_START_FILE -> {
                Logger.i(TAG) { "Start file — loading" }
                stateHolder.updateLoadingState(PlayerLoadingState.LOADING)
            }
            MPV_EVENT_END_FILE -> {
                Logger.i(TAG) { "End file" }
            }
            MPV_EVENT_TRACKS_CHANGED -> {
                Logger.d(TAG) { "Tracks changed" }
                // The host will call view.loadTracks() and push results
            }
        }
    }
}
