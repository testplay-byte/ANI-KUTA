package com.confused.anikuta.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.updates.UpdateEngine
import com.confused.anikuta.core.updates.UpdateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Updates screen (Phase UP).
 *
 * Observes [UpdateStore.observeAllUpdates] + enriches each entry with the anime
 * title + cover from [ContentRepository]. Groups by New (unacknowledged) +
 * Earlier (acknowledged).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:Updates".
 */
class UpdatesViewModel(
    private val updateStore: UpdateStore,
    private val contentRepository: ContentRepository,
    private val updateEngine: UpdateEngine,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Updates"
    }

    val state: StateFlow<UpdatesUiState> = updateStore.observeAllUpdates(100)
        .map { updates ->
            val enriched = updates.mapNotNull { update -> enrichUpdate(update) }
            val newUpdates = enriched.filter { !it.acknowledged }
            val earlierUpdates = enriched.filter { it.acknowledged }
            UpdatesUiState.Loaded(newUpdates = newUpdates, earlierUpdates = earlierUpdates)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            UpdatesUiState.Loading,
        )

    private val _checking = MutableStateFlow(false)
    val checking: StateFlow<Boolean> = _checking.asStateFlow()

    /** Check for new episodes (pull-to-refresh or "Check now" button). */
    fun checkForUpdates() {
        viewModelScope.launch {
            _checking.value = true
            runCatching {
                val count = updateEngine.checkDueAnime()
                Logger.i(TAG) { "checkForUpdates — $count new episode(s) found" }
            }.onFailure { e ->
                Logger.e(TAG, e) { "checkForUpdates failed: ${e.message}" }
            }
            _checking.value = false
        }
    }

    /** Acknowledge all updates for an anime (user opened the details page). */
    fun acknowledgeUpdates(mainId: String) {
        viewModelScope.launch {
            updateStore.acknowledgeUpdatesByMainId(mainId)
            Logger.d(TAG) { "acknowledgeUpdates: mainId=$mainId" }
        }
    }

    private fun enrichUpdate(update: com.confused.anikuta.core.updates.EpisodeUpdate): UpdateDisplay? {
        val content = contentRepository.getContentByMainId(update.mainId) ?: return null
        val anilistDetail = contentRepository.getAniListDetail(update.mainId)
        val extDetail = contentRepository.getExtensionDetail(update.mainId)
        val coverUrl = anilistDetail?.coverUrl ?: extDetail?.thumbnailUrl
        return UpdateDisplay(
            mainId = update.mainId,
            animeTitle = content.title,
            coverUrl = coverUrl,
            episodeNumber = update.episodeNumber.toInt(),
            episodeTitle = update.episodeTitle,
            audioVariant = update.audioVariant,
            discoveredAt = update.discoveredAt,
            acknowledged = update.acknowledged,
        )
    }
}

sealed interface UpdatesUiState {
    data object Loading : UpdatesUiState
    data class Loaded(
        val newUpdates: List<UpdateDisplay>,
        val earlierUpdates: List<UpdateDisplay>,
    ) : UpdatesUiState
}

data class UpdateDisplay(
    val mainId: String,
    val animeTitle: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val episodeTitle: String?,
    val audioVariant: String,
    val discoveredAt: Long,
    val acknowledged: Boolean,
)
