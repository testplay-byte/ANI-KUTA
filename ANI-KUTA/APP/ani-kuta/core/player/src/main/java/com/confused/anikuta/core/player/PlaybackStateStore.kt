package com.confused.anikuta.core.player

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Saves the last playback state (speed, subtitle track, audio track) per episode.
 *
 * When the user resumes an episode, the player restores these settings.
 *
 * Uses SharedPreferences (JSON-encoded) — small data, fast access.
 * Watch progress (position/duration) is in the SQLDelight `watch_progress` table
 * via [com.confused.anikuta.core.watchprogress.WatchProgressStore].
 *
 * CORE_RULES §23: Exposes StateFlow for reactive UI updates.
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Player:PlaybackState".
 */
class PlaybackStateStore(
    private val store: PreferenceStore,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Player:PlaybackState"
        private const val KEY_PREFIX = "playback_state_"
        private val json = Json { ignoreUnknownKeys = true }
    }

    private val _states = MutableStateFlow<Map<String, PlaybackState>>(emptyMap())
    val states: StateFlow<Map<String, PlaybackState>> = _states.asStateFlow()

    /**
     * Save the playback state for an episode.
     */
    fun save(episodeKey: String, state: PlaybackState) {
        Logger.d(TAG) { "Saving playback state for $episodeKey: speed=${state.speed}, sid=${state.subtitleTrackId}, aid=${state.audioTrackId}" }
        val key = KEY_PREFIX + episodeKey
        val jsonStr = buildJsonObject {
            put("speed", JsonPrimitive(state.speed))
            put("sid", JsonPrimitive(state.subtitleTrackId))
            put("aid", JsonPrimitive(state.audioTrackId))
        }.toString()
        store.putString(key, jsonStr)
    }

    /**
     * Get the playback state for an episode. Returns null if none saved.
     */
    fun get(episodeKey: String): PlaybackState? {
        val key = KEY_PREFIX + episodeKey
        val jsonStr = store.getString(key, "")
        if (jsonStr.isBlank()) return null

        return try {
            val obj = json.parseToJsonElement(jsonStr) as JsonObject
            PlaybackState(
                speed = obj["speed"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.0f,
                subtitleTrackId = obj["sid"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                audioTrackId = obj["aid"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
            )
        } catch (e: Exception) {
            Logger.w(TAG) { "Failed to parse playback state for $episodeKey: ${e.message}" }
            null
        }
    }

    /**
     * Delete the playback state for an episode.
     */
    fun delete(episodeKey: String) {
        Logger.d(TAG) { "Deleting playback state for $episodeKey" }
        store.putString(KEY_PREFIX + episodeKey, "")
    }
}

/**
 * Saved playback state for an episode.
 */
data class PlaybackState(
    val speed: Float = 1.0f,
    val subtitleTrackId: Int = -1,
    val audioTrackId: Int = -1,
)
