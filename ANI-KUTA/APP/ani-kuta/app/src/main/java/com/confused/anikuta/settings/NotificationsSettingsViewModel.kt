package com.confused.anikuta.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.notifications.NotificationConfig
import com.confused.anikuta.core.notifications.NotificationConfigStore
import com.confused.anikuta.core.preferences.NotificationPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Notifications settings screen (Phase NOTIF — UI).
 *
 * Three concerns:
 * 1. **Global master toggle** — the kill switch ([NotificationPreferences.notificationsEnabled]).
 * 2. **Default trigger + audio prefs** — seeded into per-anime config when a user enables
 *    notifications for a new anime.
 * 3. **Per-anime config** — the library list, each with its own [NotificationConfig]
 *    (loaded once, refreshed locally on edit). Tapping a row opens a detail sheet.
 *
 * CORE_RULES §20: logged via the underlying stores (no local tag needed — writes are
 * logged in NotificationConfigStore).
 */
class NotificationsSettingsViewModel(
    private val preferences: NotificationPreferences,
    private val configStore: NotificationConfigStore,
    private val contentRepository: ContentRepository,
) : ViewModel() {

    /** Global master kill switch. */
    val masterEnabled: StateFlow<Boolean> =
        preferences.notificationsEnabledFlow()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    /** Default trigger + audio prefs (applied when enabling a new anime). */
    data class Defaults(
        val onSchedule: Boolean,
        val onWatchable: Boolean,
        val onImmediate: Boolean,
        val sub: Boolean,
        val dub: Boolean,
    )

    val defaults: StateFlow<Defaults> = combine(
        preferences.defaultNotifyOnScheduleFlow(),
        preferences.defaultNotifyOnWatchableFlow(),
        preferences.defaultNotifyOnImmediateFlow(),
        preferences.defaultNotifySubFlow(),
        preferences.defaultNotifyDubFlow(),
    ) { schedule, watchable, immediate, sub, dub ->
        Defaults(schedule, watchable, immediate, sub, dub)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        Defaults(onSchedule = false, onWatchable = true, onImmediate = false, sub = true, dub = false),
    )

    private val _libraryItems = MutableStateFlow<List<AnimeNotifItem>>(emptyList())
    val libraryItems: StateFlow<List<AnimeNotifItem>> = _libraryItems.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init {
        loadLibrary()
    }

    private fun loadLibrary() {
        viewModelScope.launch {
            _loading.value = true
            val mainIds = contentRepository.getLibraryMainIds()
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
            _libraryItems.value = items
            _loading.value = false
        }
    }

    // ── Global master toggle ───────────────────────────────────────────────

    fun setMasterEnabled(enabled: Boolean) {
        preferences.notificationsEnabled = enabled
    }

    // ── Default prefs ───────────────────────────────────────────────────────

    fun setDefaultOnSchedule(v: Boolean) { preferences.defaultNotifyOnSchedule = v }
    fun setDefaultOnWatchable(v: Boolean) { preferences.defaultNotifyOnWatchable = v }
    fun setDefaultOnImmediate(v: Boolean) { preferences.defaultNotifyOnImmediate = v }
    fun setDefaultSub(v: Boolean) { preferences.defaultNotifySub = v }
    fun setDefaultDub(v: Boolean) { preferences.defaultNotifyDub = v }

    // ── Per-anime config ─────────────────────────────────────────────────────

    /**
     * Enable/disable notifications for an anime. When enabling with no existing
     * config, seeds from the current [defaults]. Existing configs keep their
     * trigger/audio settings; only the [NotificationConfig.enabled] flag flips.
     */
    fun setAnimeEnabled(mainId: String, enabled: Boolean) {
        viewModelScope.launch {
            val current = configStore.getConfig(mainId)
            val d = defaults.value
            val base = current ?: NotificationConfig(
                mainId = mainId,
                enabled = true,
                notifyOnSchedule = d.onSchedule,
                notifyOnWatchable = d.onWatchable,
                notifyOnImmediate = d.onImmediate,
                notifySub = d.sub,
                notifyDub = d.dub,
            )
            configStore.setConfig(base.copy(enabled = enabled))
            refreshItem(mainId)
        }
    }

    /** Apply an arbitrary transform to an anime's config (used by the detail sheet). */
    fun updateAnimeConfig(mainId: String, transform: (NotificationConfig) -> NotificationConfig) {
        viewModelScope.launch {
            val current = configStore.getConfig(mainId) ?: NotificationConfig(mainId = mainId)
            configStore.setConfig(transform(current))
            refreshItem(mainId)
        }
    }

    private suspend fun refreshItem(mainId: String) {
        val config = configStore.getConfig(mainId)
        _libraryItems.value = _libraryItems.value.map {
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
