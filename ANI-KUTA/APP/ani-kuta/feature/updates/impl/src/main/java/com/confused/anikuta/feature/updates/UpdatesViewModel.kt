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
            // D-381: dedupe on the display composite (mainId + episodeNumber +
            // audioVariant) as a second guard — the screen keys use the same
            // composite, so a collision here would have been a duplicate-key
            // crash there. First row wins (newest — observeAllUpdates orders
            // by discovered_at DESC).
            val enriched = updates.mapNotNull { update -> enrichUpdate(update) }
                .distinctBy { "${it.mainId}:${it.episodeNumber}:${it.audioVariant}" }
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

    // Task 80-b: an honest one-line message for the refresh control (the
    // OFF-mode refusal). Why not the progress banner: the LiveProgressBanner
    // renders a SPINNER for any text-only progress — a spinner on "updates
    // are turned off" would imply activity that never happens. This is the
    // lightest honest surface; the screen renders it near the refresh
    // control and a tap dismisses it.
    private val _checkMessage = MutableStateFlow<String?>(null)
    val checkMessage: StateFlow<String?> = _checkMessage.asStateFlow()

    /** Dismiss the refresh-control message (the screen calls this on tap). */
    fun clearCheckMessage() {
        _checkMessage.value = null
    }

    /** Clear the progress banner — called when the Updates screen is entered (no auto-refresh).
     *
     * Task 80-b: gated behind the engine's live check state — opening the
     * tab during a BACKGROUND check used to wipe the live in-app banner
     * (the check kept running; its next emission would re-appear only on
     * the next item, leaving the user with no progress feedback in between).
     * While a check is active the banner stays. */
    fun clearProgress() {
        if (!updateEngine.checkActive.value) {
            _checkProgress.value = null
        }
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
        // Task 80-b: OFF-mode honesty — when the user turned updates OFF the
        // periodic worker is cancelled and the engine is not supposed to run;
        // silently running a full check anyway (the old behavior) betrayed
        // the toggle. Refuse with an honest message instead of calling the
        // engine, and keep the "checking" machinery untouched.
        if (updatePreferences?.getMode() == com.confused.anikuta.core.preferences.UpdateMode.OFF) {
            Logger.i(TAG) { "checkForUpdates — refused: updates are turned off" }
            _checkMessage.value = "Updates are turned off — enable them in Settings → Updates"
            return
        }
        viewModelScope.launch {
            _checking.value = true
            // Task 80-b: a real check supersedes any stale refresh message.
            _checkMessage.value = null
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

                val count = updateEngine.checkDueAnime(filterMainIds, trigger = "manual")
                Logger.i(TAG) { "checkForUpdates — $count new episode(s) found" }

                // D-193 v2 + D-391 (round 26): also (re-)schedule the smart-release
                // one-shots for every known future airing, so a manual Check Now
                // still sets up the smart-polling chain.
                try {
                    smartReleaseScheduler?.scheduleUpcomingChecks()
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
