package com.confused.anikuta.core.player

import com.confused.anikuta.core.common.Logger
import `is`.xyz.mpv.MPVLib

/**
 * Observer for MPV events. Translates MPV callbacks into [PlayerStateHolder] updates.
 *
 * The host (WatchScreen/Activity) registers this observer on the MPV view.
 * MPV calls the observer methods on its own thread — the observer just pushes
 * the values into StateFlows (thread-safe).
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Player:Observer".
 */
class PlayerObserver(
    private val stateHolder: PlayerStateHolder,
) : MPVLib.Observer {

    companion object {
        private const val TAG = "Anikuta:Core:Player:Observer"
    }

    override fun observeProperty(property: String, value: String) {
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

    override fun event(eventId: Int) {
        Logger.v(TAG) { "Event: $eventId" }

        when (eventId) {
            // MPV_EVENT_FILE_LOADED — video is ready to play
            11 -> {
                Logger.i(TAG) { "File loaded" }
                stateHolder.updateLoadingState(PlayerLoadingState.READY)
                stateHolder.updateBuffering(false)
            }
            // MPV_EVENT_START_FILE — started loading a file
            6 -> {
                Logger.i(TAG) { "Start file — loading" }
                stateHolder.updateLoadingState(PlayerLoadingState.LOADING)
            }
            // MPV_EVENT_END_FILE — file ended (completed or error)
            7 -> {
                Logger.i(TAG) { "End file" }
            }
            // MPV_EVENT_TRACKS_CHANGED — track list changed
            26 -> {
                Logger.d(TAG) { "Tracks changed" }
                // The host (WatchScreen) will call view.loadTracks() and
                // push the results via stateHolder.updateTracks()
            }
        }
    }
}
