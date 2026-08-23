package com.confused.anikuta.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.playbackcache.PlaybackCacheManager
import com.confused.anikuta.core.playbackcache.PlaybackCachePreferences
import com.confused.anikuta.core.playbackcache.PlaybackCacheStore
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Video caching settings screen ViewModel (NotificationsSettingsViewModel pattern).
 *
 * Reads the cache entry list reactively (SQLDelight Flow) for the "Cached episodes"
 * section + the preferences for the toggle + storage-limit slider.
 */
class VideoCachingViewModel(
    private val preferences: PlaybackCachePreferences,
    private val manager: PlaybackCacheManager,
) : ViewModel() {

    val enabled: StateFlow<Boolean> =
        preferences.cacheEnabledFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val maxCacheMb: StateFlow<Int> =
        preferences.maxCacheMbFlow()
            .map { it.coerceIn(PlaybackCachePreferences.MIN_MB, PlaybackCachePreferences.MAX_MB) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), PlaybackCachePreferences.DEFAULT_MAX_MB)

    val entries: StateFlow<List<PlaybackCacheStore.Entry>> =
        manager.observeEntries()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalBytes: StateFlow<Long> =
        manager.observeTotalBytes()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun setEnabled(enabled: Boolean) {
        preferences.cacheEnabled = enabled
    }

    fun setMaxCacheMb(mb: Int) {
        preferences.maxCacheMb = mb
    }

    fun removeEntry(cacheKey: String) {
        manager.removeEntry(cacheKey)
    }

    fun clearAll() {
        manager.clearAll()
    }
}
