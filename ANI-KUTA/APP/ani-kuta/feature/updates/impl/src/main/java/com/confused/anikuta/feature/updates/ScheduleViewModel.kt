package com.confused.anikuta.feature.updates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.schedule.ScheduleEngine
import com.confused.anikuta.core.schedule.ScheduleEntry
import com.confused.anikuta.core.schedule.ScheduleStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * ViewModel for the Schedule list view (Phase SC — PLAN §5.2).
 *
 * Observes [ScheduleStore.observeUpcoming] + enriches each entry with the anime
 * title + cover from [ContentRepository]. Groups by day.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Feature:Schedule".
 */
class ScheduleViewModel(
    private val scheduleStore: ScheduleStore,
    private val contentRepository: ContentRepository,
    private val scheduleEngine: ScheduleEngine,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Schedule"
    }

    val state: StateFlow<ScheduleUiState> = scheduleStore.observeUpcoming(
        from = System.currentTimeMillis(),
        to = System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000, // 1 year ahead
    ).map { entries ->
        val enriched = entries.mapNotNull { entry -> enrichEntry(entry) }
        val grouped = groupByDay(enriched)
        ScheduleUiState.Loaded(grouped)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        ScheduleUiState.Loading,
    )

    private val _fetching = MutableStateFlow(false)
    val fetching: StateFlow<Boolean> = _fetching.asStateFlow()

    /** Fetch airing schedule from AniList (pull-to-refresh). */
    fun fetchSchedule() {
        viewModelScope.launch {
            _fetching.value = true
            runCatching {
                scheduleEngine.fetchSchedule()
                Logger.i(TAG) { "fetchSchedule — complete" }
            }.onFailure { e ->
                Logger.e(TAG, e) { "fetchSchedule failed: ${e.message}" }
            }
            _fetching.value = false
        }
    }

    private fun enrichEntry(entry: ScheduleEntry): ScheduleDisplay? {
        val content = contentRepository.getContentByMainId(entry.mainId) ?: return null
        val anilistDetail = contentRepository.getAniListDetail(entry.mainId)
        val extDetail = contentRepository.getExtensionDetail(entry.mainId)
        val coverUrl = anilistDetail?.coverUrl ?: extDetail?.thumbnailUrl
        return ScheduleDisplay(
            mainId = entry.mainId,
            animeTitle = content.title,
            coverUrl = coverUrl,
            episodeNumber = entry.episodeNumber.toInt(),
            scheduledAt = entry.scheduledAt,
            actualAt = entry.actualAt,
        )
    }

    private fun groupByDay(entries: List<ScheduleDisplay>): List<ScheduleGroup> {
        val today = java.time.LocalDate.now()
        val grouped = entries.groupBy { entry ->
            java.time.Instant.ofEpochMilli(entry.scheduledAt)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
        }
        return grouped.entries.sortedBy { it.key }.map { (date, dayEntries) ->
            val label = when (date) {
                today -> "Today"
                today.plusDays(1) -> "Tomorrow"
                else -> date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d"))
            }
            ScheduleGroup(label, dayEntries)
        }
    }
}

sealed interface ScheduleUiState {
    data object Loading : ScheduleUiState
    data class Loaded(val groups: List<ScheduleGroup>) : ScheduleUiState
}

data class ScheduleGroup(
    val label: String,
    val entries: List<ScheduleDisplay>,
)

data class ScheduleDisplay(
    val mainId: String,
    val animeTitle: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val scheduledAt: Long,
    val actualAt: Long?,
)
