package com.confused.anikuta.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.content.LibraryCategory
import com.confused.anikuta.core.notifications.AudioPref
import com.confused.anikuta.core.notifications.NotificationConfig
import com.confused.anikuta.core.notifications.NotificationConfigStore
import com.confused.anikuta.core.notifications.TriggerState
import com.confused.anikuta.core.preferences.NotificationPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the dedicated Notifications → Library page (Phase NOTIF — UI).
 *
 * Lists every library anime (optionally filtered by category) with its per-anime
 * notification config. Each row has a Switch (enable/disable for that anime) +
 * a tap action that opens an advanced-config sheet (tri-state triggers + audio).
 *
 * Category filter: "All" + every [LibraryCategory] the user has. Selecting a
 * category filters the list to anime in that category.
 */
class NotificationsLibraryViewModel(
    private val configStore: NotificationConfigStore,
    private val contentRepository: ContentRepository,
    private val preferences: NotificationPreferences,
) : ViewModel() {

    private val _categories = MutableStateFlow<List<LibraryCategory>>(emptyList())
    val categories: StateFlow<List<LibraryCategory>> = _categories.asStateFlow()

    private val _selectedCategoryId = MutableStateFlow<Long?>(null) // null = All
    val selectedCategoryId: StateFlow<Long?> = _selectedCategoryId.asStateFlow()

    private val _items = MutableStateFlow<List<AnimeNotifItem>>(emptyList())
    val items: StateFlow<List<AnimeNotifItem>> = _items.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _openItem = MutableStateFlow<AnimeNotifItem?>(null)
    /** The anime whose advanced-config sheet is open (null = closed). */
    val openItem: StateFlow<AnimeNotifItem?> = _openItem.asStateFlow()

    init {
        loadCategories()
        loadLibrary(null)
    }

    private fun loadCategories() {
        viewModelScope.launch {
            _categories.value = contentRepository.getAllCategories()
        }
    }

    fun selectCategory(categoryId: Long?) {
        _selectedCategoryId.value = categoryId
        loadLibrary(categoryId)
    }

    private fun loadLibrary(categoryId: Long?) {
        viewModelScope.launch {
            _loading.value = true
            val mainIds = if (categoryId == null) {
                contentRepository.getLibraryMainIds()
            } else {
                contentRepository.getMainIdsByCategory(categoryId)
            }
            val items = mainIds.map { mainId ->
                val content = contentRepository.getContentByMainId(mainId)
                val anilistDetail = contentRepository.getAniListDetail(mainId)
                val extDetail = contentRepository.getExtensionDetail(mainId)
                AnimeNotifItem(
                    mainId = mainId,
                    title = content?.title ?: "Unknown",
                    coverUrl = anilistDetail?.coverUrl ?: extDetail?.thumbnailUrl,
                    config = configStore.getConfig(mainId),
                )
            }.sortedBy { it.title.lowercase() }
            _items.value = items
            _loading.value = false
        }
    }

    fun openAdvanced(item: AnimeNotifItem) { _openItem.value = item }
    fun closeAdvanced() { _openItem.value = null }

    /**
     * Enable/disable notifications for an anime. When enabling with no existing
     * config, seeds from the current defaults (tri-state triggers + audio pref).
     */
    fun setAnimeEnabled(mainId: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = configStore.getConfig(mainId)
            val base = current ?: NotificationConfig(
                mainId = mainId,
                enabled = true,
                notifyOnSchedule = preferences.defaultNotifyOnSchedule,
                notifyOnWatchable = preferences.defaultNotifyOnWatchable,
                notifyOnImmediate = preferences.defaultNotifyOnImmediate,
                audioPref = preferences.defaultAudioPref,
            )
            configStore.setConfig(base.copy(enabled = enabled))
            refreshItem(mainId)
        }
    }

    /** Apply a transform to an anime's config (used by the advanced-config sheet). */
    fun updateAnimeConfig(mainId: String, transform: (NotificationConfig) -> NotificationConfig) {
        viewModelScope.launch {
            val current = configStore.getConfig(mainId) ?: NotificationConfig(mainId = mainId)
            configStore.setConfig(transform(current))
            refreshItem(mainId)
            // Also refresh the open-item snapshot so the sheet reflects the new state.
            _openItem.value = _openItem.value?.let {
                if (it.mainId == mainId) it.copy(config = configStore.getConfig(mainId)) else it
            }
        }
    }

    private suspend fun refreshItem(mainId: String) {
        val config = configStore.getConfig(mainId)
        _items.value = _items.value.map {
            if (it.mainId == mainId) it.copy(config = config) else it
        }
    }
}

/** A library anime + its notification config (null = not configured / defaults). */
data class AnimeNotifItem(
    val mainId: String,
    val title: String,
    val coverUrl: String?,
    val config: NotificationConfig?,
) {
    /** Effective enabled state: only true when a config exists AND enabled == true. */
    val isEnabled: Boolean get() = config?.enabled == true
}
