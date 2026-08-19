package com.confused.anikuta.feature.animehistory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.watchprogress.WatchProgress
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the History screen (Phase HI).
 *
 * Observes [WatchProgressStore.observeRecent] + enriches each entry with the
 * anime title + cover from [ContentRepository]. Groups by day-bucket
 * (Today / Yesterday / This Week / Earlier).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:History".
 */
class HistoryViewModel(
    private val watchProgressStore: WatchProgressStore,
    private val contentRepository: ContentRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:History"
        private const val LIMIT = 100
    }

    /**
     * The UI state — a list of history entries grouped by day-bucket.
     * Loading while the first emission hasn't arrived yet.
     */
    val state: StateFlow<HistoryUiState> = watchProgressStore.observeRecent(LIMIT)
        .map { progressList ->
            val entries = progressList.mapNotNull { progress -> enrichEntry(progress) }
            val grouped = groupByDay(entries)
            HistoryUiState.Loaded(grouped)
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            HistoryUiState.Loading,
        )

    /** Delete a single history entry (swipe-to-delete on a row). */
    fun deleteEntry(episodeKey: String) {
        viewModelScope.launch {
            runCatching {
                watchProgressStore.delete(episodeKey)
                Logger.i(TAG) { "deleteEntry: episodeKey=$episodeKey" }
            }.onFailure { e ->
                Logger.e(TAG, e) { "deleteEntry failed: ${e.message}" }
            }
        }
    }

    /** Clear ALL watch progress (the "Clear all" action — red destructive dialog). */
    fun clearAll() {
        viewModelScope.launch {
            runCatching {
                watchProgressStore.deleteAll()
                Logger.i(TAG) { "clearAll: all watch progress deleted" }
            }.onFailure { e ->
                Logger.e(TAG, e) { "clearAll failed: ${e.message}" }
            }
        }
    }

    /**
     * Enriches a [WatchProgress] with the anime title + cover URL.
     * Returns null if the content can't be found (deleted from library — skip it).
     */
    private fun enrichEntry(progress: WatchProgress): HistoryEntry? {
        val mainId = progress.mainId ?: return null
        val content = contentRepository.getMainEntryByMainId(mainId) ?: return null
        // D-198: getAniListDetail + getExtensionDetail → getContentDetails.
        val details = contentRepository.getContentDetails(mainId)
        val coverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl
        // Parse the episode number from the standardized episode_key: "${mainId}|${padded_5_digit}"
        val episodeNumber = progress.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0
        return HistoryEntry(
            episodeKey = progress.episodeKey,
            mainId = mainId,
            animeTitle = content.title,
            coverUrl = coverUrl,
            episodeNumber = episodeNumber,
            position = progress.position,
            duration = progress.duration,
            completed = progress.isWatched,
            lastWatchedAt = progress.lastWatchedAt,
            progressFraction = progress.progressFraction,
        )
    }

    /**
     * Groups entries by day-bucket: Today / Yesterday / This Week / Earlier.
     * Uses java.time.LocalDate (fixes the old project's DAY_OF_YEAR + YEAR * 365 leap-year bug).
     */
    private fun groupByDay(entries: List<HistoryEntry>): List<HistoryGroup> {
        val today = java.time.LocalDate.now()
        val yesterday = today.minusDays(1)
        val weekAgo = today.minusDays(7)

        val todayEntries = mutableListOf<HistoryEntry>()
        val yesterdayEntries = mutableListOf<HistoryEntry>()
        val thisWeekEntries = mutableListOf<HistoryEntry>()
        val earlierEntries = mutableListOf<HistoryEntry>()

        for (entry in entries) {
            val entryDate = java.time.Instant.ofEpochMilli(entry.lastWatchedAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
            when {
                entryDate == today -> todayEntries.add(entry)
                entryDate == yesterday -> yesterdayEntries.add(entry)
                entryDate.isAfter(weekAgo) -> thisWeekEntries.add(entry)
                else -> earlierEntries.add(entry)
            }
        }

        val groups = mutableListOf<HistoryGroup>()
        if (todayEntries.isNotEmpty()) groups.add(HistoryGroup("Today", todayEntries))
        if (yesterdayEntries.isNotEmpty()) groups.add(HistoryGroup("Yesterday", yesterdayEntries))
        if (thisWeekEntries.isNotEmpty()) groups.add(HistoryGroup("This Week", thisWeekEntries))
        if (earlierEntries.isNotEmpty()) groups.add(HistoryGroup("Earlier", earlierEntries))
        return groups
    }
}

// ── UI state ──────────────────────────────────────────────────────────────────

sealed interface HistoryUiState {
    data object Loading : HistoryUiState
    data class Loaded(val groups: List<HistoryGroup>) : HistoryUiState
}

data class HistoryGroup(
    val label: String,
    val entries: List<HistoryEntry>,
)

data class HistoryEntry(
    val episodeKey: String,
    val mainId: String,
    val animeTitle: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val position: Long,
    val duration: Long,
    val completed: Boolean,
    val lastWatchedAt: Long,
    val progressFraction: Float,
)
