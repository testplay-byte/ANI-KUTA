package com.confused.anikuta.core.playbackcache

import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.flow.Flow

/**
 * Video playback cache preferences (Video Caching settings screen).
 *
 * NotificationPreferences-style var + Flow accessors (SharedPreferences-backed).
 * The storage limit is stored in MB as an Int because :core:preferences has no
 * LongSerializer — 100..2048 MB covers the required 100 MB..2 GB range without
 * one. Bytes conversion happens in [maxCacheBytes].
 */
class PlaybackCachePreferences(private val store: PreferenceStore) {

    /** Master switch — default OFF (round 135: the user wants video caching
     *  opt-in; previously ON per the feature spec). Existing installs that
     *  already wrote the key keep their choice; only the never-touched
     *  default flips. */
    var cacheEnabled: Boolean
        get() = store.getBoolean(KEY_ENABLED, false)
        set(value) = store.putBoolean(KEY_ENABLED, value)

    fun cacheEnabledFlow(): Flow<Boolean> = store.booleanFlow(KEY_ENABLED, false)

    /** Storage limit in MB (100..2048). Default 512 MB. */
    var maxCacheMb: Int
        get() = store.getInt(KEY_MAX_MB, DEFAULT_MAX_MB).coerceIn(MIN_MB, MAX_MB)
        set(value) = store.putInt(KEY_MAX_MB, value.coerceIn(MIN_MB, MAX_MB))

    fun maxCacheMbFlow(): Flow<Int> = store.intFlow(KEY_MAX_MB, DEFAULT_MAX_MB)

    /** The limit in bytes (clamped). */
    val maxCacheBytes: Long
        get() = maxCacheMb.toLong() * 1024L * 1024L

    companion object {
        const val MIN_MB = 100
        const val MAX_MB = 2048
        const val DEFAULT_MAX_MB = 512
        private const val KEY_ENABLED = "pref_pb_cache_enabled"
        private const val KEY_MAX_MB = "pref_pb_cache_max_mb"
    }
}
