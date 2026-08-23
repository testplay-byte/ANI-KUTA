package com.confused.anikuta.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.updates.CheckProgress
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
 * ViewModel for the Updates screen (Phase UP + D-193 Phase 8).
 *
 * Observes [UpdateStore.observeAllUpdates] + enriches each entry with the anime
 * title + cover from [ContentRepository]. Groups by New (unacknowledged) +
 * Earlier (acknowledged).
 *
 * D-193 Phase 8:
 * - Live-progress via [UpdateEngine.checkProgress] SharedFlow → [checkProgress] StateFlow.
 * - Batch-type rendering: initial-batch rows show "Episodes 1-N added to library".
 * - Acknowledgment on tap.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:Updates".
 */
class UpdatesViewModel(
    private val updateStore: UpdateStore,
    private val contentRepository: ContentRepository,
    private val updateEngine: UpdateEngine,
    // D-193 v2: needed to resolve selected categories → mainIds in Manual mode,
    // and to schedule smart-release checks after a manual Check Now.
    private val updatePreferences: com.confused.anikuta.core.preferences.UpdatePreferences? = null,
    private val smartReleaseScheduler: com.confused.anikuta.core.updates.SmartReleaseScheduler? = null,
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

    // D-193 Phase 8: Live-progress from UpdateEngine.checkProgress SharedFlow.
    private val _checkProgress = MutableStateFlow<CheckProgress?>(null)
    val checkProgress: StateFlow<CheckProgress?> = _checkProgress.asStateFlow()

    /** Clear the progress banner — called when the Updates screen is entered (no auto-refresh). */
    fun clearProgress() {
        _checkProgress.value = null
    }

    init {
        // Collect checkProgress from the engine.
        viewModelScope.launch {
            updateEngine.checkProgress.collect { progress ->
                if (progress.current >= progress.total && progress.total > 0) {
                    // Terminal — clear the progress after a short delay.
                    _checkProgress.value = progress
                    kotlinx.coroutines.delay(2000)
                    _checkProgress.value = null
                } else {
                    _checkProgress.value = progress
                }
            }
        }
    }

    /** Check for new episodes (pull-to-refresh or "Check now" button). */
    fun checkForUpdates() {
        viewModelScope.launch {
            _checking.value = true
            // D-193 improvement: emit a "checking" progress so the banner shows immediately
            _checkProgress.value = com.confused.anikuta.core.updates.CheckProgress(0, 0, "", "Checking library…", null)
            runCatching {
                // D-193 v2: in Manual mode, filter to selected categories.
                val filterMainIds: Set<String>? = if (updatePreferences?.getMode() ==
                    com.confused.anikuta.core.preferences.UpdateMode.MANUAL
                ) {
                    val selectedCats = updatePreferences.getSelectedCategories()
                    if (selectedCats.isEmpty()) {
                        Logger.i(TAG) { "checkForUpdates — Manual mode, no categories selected — checking all" }
                        null // no filter = check all due (user hasn't picked categories yet)
                    } else {
                        selectedCats.flatMap { catId ->
                            contentRepository.getMainIdsByCategory(catId.toLong())
                        }.toSet()
                    }
                } else {
                    null // AUTO mode — check all due anime
                }

                val count = updateEngine.checkDueAnime(filterMainIds)
                Logger.i(TAG) { "checkForUpdates — $count new episode(s) found" }

                // D-193 v2: also schedule smart-release checks for anime airing within ±1h,
                // so a manual Check Now still sets up the smart-polling chain.
                try {
                    smartReleaseScheduler?.scheduleImminentChecks()
                } catch (e: Exception) {
                    Logger.w(TAG) { "Smart-release scheduling failed (non-fatal): ${e.message}" }
                }

                // If no progress was emitted (no anime due), show a brief "complete" message
                if (_checkProgress.value == null || _checkProgress.value!!.total == 0) {
                    _checkProgress.value = com.confused.anikuta.core.updates.CheckProgress(0, 0, "", "No anime due for check", null)
                    kotlinx.coroutines.delay(2000)
                    _checkProgress.value = null
                }
            }.onFailure { e ->
                Logger.e(TAG, e) { "checkForUpdates failed: ${e.message}" }
                _checkProgress.value = null
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

    /** D-249: Clear ALL updates (the "Clear" button — deletes every row). */
    fun clearAllUpdates() {
        viewModelScope.launch {
            updateStore.deleteAllUpdates()
            Logger.i(TAG) { "clearAllUpdates: all updates cleared" }
        }
    }

    private fun enrichUpdate(update: com.confused.anikuta.core.updates.EpisodeUpdate): UpdateDisplay? {
        val content = contentRepository.getMainEntryByMainId(update.mainId) ?: return null
        // D-198: getAniListDetail + getExtensionDetail → getContentDetails.
        val details = contentRepository.getContentDetails(update.mainId)
        val coverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl
        return UpdateDisplay(
            mainId = update.mainId,
            animeTitle = content.title,
            coverUrl = coverUrl,
            episodeNumber = update.episodeNumber.toInt(),
            episodeTitle = update.episodeTitle,
            audioVariant = update.audioVariant,
            discoveredAt = update.discoveredAt,
            acknowledged = update.acknowledged,
            // D-193 Phase 8: batch type + episode count for initial-batch rendering.
            batchType = update.batchType,
            episodeCount = update.episodeCount?.toInt(),
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
    // D-193 Phase 8: batch type + episode count.
    val batchType: String = "new",
    val episodeCount: Int? = null,
)
