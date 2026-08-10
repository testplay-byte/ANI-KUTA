package com.confused.anikuta.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.content.genre.GenreRepository
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.preferences.PreferenceStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * ViewModel for the My Profile screen.
 *
 * Computes all stats from the database:
 * - Quick stats: total anime, watch time, mean score, streak
 * - Watch flow: episodes watched by day of week (Mon-Sun)
 * - Time DNA: preferred watch time (morning/afternoon/evening/night)
 * - Genre distribution: from GenreRepository
 * - Activity heatmap: 365-day watch activity
 * - Timeline: user's activity feed
 */
class ProfileViewModel(
    private val database: AnikutaDatabase,
    private val contentRepository: ContentRepository,
    private val genreRepository: GenreRepository,
    private val preferenceStore: PreferenceStore,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Profile"
    }

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        loadStats()
    }

    fun loadStats() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Logger.i(TAG) { "Loading profile stats..." }

                val libraryItems = database.libraryQueries.getAllLibraryItems().executeAsList()
                val totalAnime = libraryItems.map { it.main_id }.distinct().size
                val libraryMainIds = libraryItems.map { it.main_id }.toSet()

                // Watch progress
                val allProgress = database.watchQueries.getAllWatchProgress().executeAsList().map { row ->
                    com.confused.anikuta.core.watchprogress.WatchProgress(
                        episodeKey = row.episode_key,
                        mainId = row.main_id,
                        position = row.position,
                        duration = row.duration,
                        completed = row.completed == 1L,
                        completedAt = row.completed_at,
                        lastWatchedAt = row.last_watched_at,
                        watchCount = row.watch_count?.toInt() ?: 0,
                        firstWatchedAt = row.first_watched_at,
                        autoMarkSuppressed = row.auto_mark_suppressed == 1L,
                        userMarkedWatched = row.user_marked_watched == 1L,
                    )
                }
                val totalEpisodesWatched = allProgress.count { it.completed }
                val totalWatchTimeSec = allProgress.filter { it.completed }.sumOf { it.duration }
                val watchTimeFormatted = formatWatchTime(totalWatchTimeSec)

                // Average rating
                val allRatings = database.ratingsQueries.getAllUserRatings().executeAsList()
                val avgRating = if (allRatings.isNotEmpty()) {
                    allRatings.mapNotNull { it?.rating?.toInt() }.let { ratings ->
                        if (ratings.isNotEmpty()) ratings.average() else 0.0
                    }
                } else 0.0
                val avgRatingFormatted = if (avgRating > 0) String.format("%.1f", avgRating / 10.0) else "—"

                // Genre distribution — backfill from existing anilist_detail.genres first
                genreRepository.backfillGenresFromExistingData(database)
                val genreCounts = genreRepository.getLibraryGenreCounts(libraryMainIds)
                val genreDistribution = genreCounts.associate { it.first to it.second }

                // Current streak
                val currentStreak = calculateCurrentStreak(allProgress)

                // Watch flow by day of week (Mon=0, Sun=6) + per-day detail for sidebar
                val watchFlowByDay = List(7) { 0 }.toMutableList()
                val watchFlowDurationByDay = List(7) { 0L }.toMutableList()
                // 7 buckets; each holds (progress) entries for that weekday
                val dayBuckets = List(7) { mutableListOf<com.confused.anikuta.core.watchprogress.WatchProgress>() }
                val calendar = Calendar.getInstance()
                allProgress.forEach { progress ->
                    if (progress.lastWatchedAt > 0) {
                        calendar.timeInMillis = progress.lastWatchedAt
                        val dayOfWeek = (calendar.get(Calendar.DAY_OF_WEEK) - 2 + 7) % 7 // Mon=0, Sun=6
                        watchFlowByDay[dayOfWeek] = watchFlowByDay[dayOfWeek] + 1
                        watchFlowDurationByDay[dayOfWeek] = watchFlowDurationByDay[dayOfWeek] + progress.duration
                        dayBuckets[dayOfWeek].add(progress)
                    }
                }
                // Build per-day summary (newest-first within each day, capped at 12 items)
                val watchFlowDetail = dayBuckets.mapIndexed { dayIdx, bucket ->
                    val items = bucket
                        .sortedByDescending { it.lastWatchedAt }
                        .take(12)
                        .mapNotNull { progress ->
                            val mid = progress.mainId ?: return@mapNotNull null
                            val content = contentRepository.getContentByMainId(mid) ?: return@mapNotNull null
                            val anilistDetail = contentRepository.getAniListDetail(mid)
                            val epNum = progress.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0
                            DayWatchItem(
                                anilistId = anilistDetail?.anilistId,
                                title = content.title,
                                coverUrl = anilistDetail?.coverUrl,
                                episodeThumbnailUrl = getEpisodeThumbnail(mid, epNum) ?: anilistDetail?.coverUrl,
                                episodeNumber = epNum,
                            )
                        }
                    DayWatchSummary(
                        count = watchFlowByDay[dayIdx],
                        totalDurationSec = watchFlowDurationByDay[dayIdx],
                        items = items,
                    )
                }

                // Time DNA (hourly distribution)
                val hourlyCounts = IntArray(24)
                allProgress.forEach { progress ->
                    if (progress.lastWatchedAt > 0) {
                        calendar.timeInMillis = progress.lastWatchedAt
                        val hour = calendar.get(Calendar.HOUR_OF_DAY)
                        hourlyCounts[hour]++
                    }
                }
                val timeDna = buildTimeDna(hourlyCounts.toList(), allProgress.size)

                // Activity heatmap data
                val activityData = buildActivityData(allProgress)

                // Avg daily watch time
                val avgDailyWatchTimeSec = if (activityData.isNotEmpty()) {
                    val activeDays = activityData.size
                    totalWatchTimeSec / activeDays.coerceAtLeast(1)
                } else 0
                val avgDailyWatchTime = formatWatchTime(avgDailyWatchTimeSec)

                // Timeline
                val timeline = buildTimeline(allProgress, allRatings)

                // AniList username
                val anilistUsername = preferenceStore.getString("anilist_username", "").takeIf { it.isNotBlank() }

                // Recently watched (for genre sheet + Recently Watched card)
                val recentlyWatched = allProgress
                    .sortedByDescending { it.lastWatchedAt }
                    .take(10)
                    .mapNotNull { progress ->
                        val mid = progress.mainId ?: return@mapNotNull null
                        val content = contentRepository.getContentByMainId(mid) ?: return@mapNotNull null
                        val anilistDetail = contentRepository.getAniListDetail(mid)
                        val epNum = progress.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0
                        RecentlyWatchedItem(
                            anilistId = anilistDetail?.anilistId,
                            title = content.title,
                            coverUrl = anilistDetail?.coverUrl,
                            episodeThumbnailUrl = getEpisodeThumbnail(mid, epNum) ?: anilistDetail?.coverUrl,
                            episodeNumber = epNum,
                            progressFraction = progress.progressFraction,
                            lastWatchedAt = progress.lastWatchedAt,
                        )
                    }

                _state.value = ProfileUiState(
                    displayName = preferenceStore.getString("profile_display_name", "Anime Fan"),
                    avatarUrl = preferenceStore.getString("profile_avatar_url", "").takeIf { it.isNotBlank() },
                    anilistUsername = anilistUsername,
                    totalAnime = totalAnime,
                    totalEpisodesWatched = totalEpisodesWatched,
                    watchTimeFormatted = watchTimeFormatted,
                    avgRatingFormatted = avgRatingFormatted,
                    currentStreak = currentStreak,
                    genreDistribution = genreDistribution,
                    watchFlowByDay = watchFlowByDay.toList(),
                    watchFlowDetail = watchFlowDetail,
                    timeDna = timeDna,
                    activityData = activityData,
                    avgDailyWatchTime = avgDailyWatchTime,
                    timeline = timeline,
                    recentlyWatched = recentlyWatched,
                )

                Logger.i(TAG) { "Profile stats loaded: $totalAnime anime, $totalEpisodesWatched episodes, ${genreCounts.size} genres" }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed to load profile stats: ${e.message}" }
            }
        }
    }

    private fun formatWatchTime(seconds: Long): String {
        if (seconds <= 0) return "0m"
        val days = seconds / 86400
        val hours = (seconds % 86400) / 3600
        val mins = (seconds % 3600) / 60
        return when {
            days > 0 -> "${days}d ${hours}h"
            hours > 0 -> "${hours}h ${mins}m"
            else -> "${mins}m"
        }
    }

    private fun calculateCurrentStreak(progress: List<com.confused.anikuta.core.watchprogress.WatchProgress>): Int {
        if (progress.isEmpty()) return 0
        val oneDayMs = 24 * 60 * 60 * 1000L
        val todayStart = (System.currentTimeMillis() / oneDayMs) * oneDayMs
        val activeDays = progress.map { it.lastWatchedAt / oneDayMs }.toSet().sortedDescending()
        var streak = 0
        var checkDay = todayStart / oneDayMs
        for (day in activeDays) {
            if (day == checkDay) { streak++; checkDay-- }
            else if (day < checkDay) break
        }
        return streak
    }

    private fun buildTimeDna(hourlyCounts: List<Int>, totalSessions: Int): TimeDnaData {
        // Find the peak time period
        val morning = (6..11).sumOf { hourlyCounts[it] }
        val afternoon = (12..17).sumOf { hourlyCounts[it] }
        val evening = (18..22).sumOf { hourlyCounts[it] }
        val night = (23..23).sumOf { hourlyCounts[it] } + (0..5).sumOf { hourlyCounts[it] }

        val preferredTimeLabel = when (maxOf(morning, afternoon, evening, night)) {
            morning -> "Morning"
            afternoon -> "Afternoon"
            evening -> "Evening"
            else -> "Night"
        }

        return TimeDnaData(
            hourlyCounts = hourlyCounts,
            preferredTimeLabel = preferredTimeLabel,
            totalSessions = totalSessions,
        )
    }

    private fun buildActivityData(progress: List<com.confused.anikuta.core.watchprogress.WatchProgress>): Map<Long, Int> {
        val oneDayMs = 24 * 60 * 60 * 1000L
        return progress
            .filter { it.lastWatchedAt > 0 }
            .groupBy { (it.lastWatchedAt / oneDayMs) * oneDayMs }
            .mapValues { it.value.size }
    }

    private fun buildTimeline(
        progress: List<com.confused.anikuta.core.watchprogress.WatchProgress>,
        ratings: List<Any>,
    ): List<TimelineItem> {
        val timeline = mutableListOf<TimelineItem>()

        // Watch events
        progress.filter { it.lastWatchedAt > 0 }.sortedByDescending { it.lastWatchedAt }.take(50).forEach { p ->
            val mid = p.mainId ?: return@forEach
            val content = contentRepository.getContentByMainId(mid) ?: return@forEach
            val anilistDetail = contentRepository.getAniListDetail(mid)
            val epNum = p.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0
            timeline.add(TimelineItem(
                anilistId = anilistDetail?.anilistId,
                title = content.title,
                coverUrl = anilistDetail?.coverUrl,
                description = "Watched EP $epNum" + if (p.completed) " (completed)" else "",
                timestamp = p.lastWatchedAt,
                type = "watch",
                rating = null,
            ))
        }

        // Rating events
        ratings.forEach { rating ->
            val r = rating as? com.confused.anikuta.core.database.User_rating ?: return@forEach
            val mid = r.main_id ?: return@forEach
            val content = contentRepository.getContentByMainId(mid) ?: return@forEach
            val anilistDetail = contentRepository.getAniListDetail(mid)
            timeline.add(TimelineItem(
                anilistId = anilistDetail?.anilistId,
                title = content.title,
                coverUrl = anilistDetail?.coverUrl,
                description = "Rated ${r.rating?.toInt()?.div(10)}/10",
                timestamp = r.rated_at ?: 0,
                type = "rating",
                rating = r.rating?.toInt(),
            ))
        }

        return timeline.sortedByDescending { it.timestamp }
    }

    /**
     * Look up the per-episode thumbnail URL from the data_cache_episode table.
     * Returns null if not cached (caller falls back to the anime cover).
     */
    private fun getEpisodeThumbnail(mainId: String, episodeNumber: Int): String? {
        if (episodeNumber <= 0) return null
        return try {
            database.dataCacheQueries
                .getEpisodeMetadataByNumber(mainId, episodeNumber.toDouble())
                .executeAsOneOrNull()
                ?.thumbnail_url
        } catch (e: Exception) {
            null
        }
    }

    fun onGenreClick(genre: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val libraryItems = database.libraryQueries.getAllLibraryItems().executeAsList()
            val libraryMainIds = libraryItems.map { it.main_id }.toSet()
            val genreAnime = libraryMainIds.mapNotNull { mid ->
                val content = contentRepository.getContentByMainId(mid) ?: return@mapNotNull null
                val anilistDetail = contentRepository.getAniListDetail(mid) ?: return@mapNotNull null
                val genres = genreRepository.getGenresForContent(mid)
                if (genre in genres) {
                    RecentlyWatchedItem(
                        anilistId = anilistDetail.anilistId,
                        title = content.title,
                        coverUrl = anilistDetail.coverUrl,
                        episodeNumber = 0,
                        progressFraction = 0f,
                        lastWatchedAt = 0,
                    )
                } else null
            }
            _state.value = _state.value.copy(selectedGenre = genre, genreAnime = genreAnime)
        }
    }

    fun clearGenreSelection() {
        _state.value = _state.value.copy(selectedGenre = null, genreAnime = emptyList())
    }

    fun updateDisplayName(name: String) {
        preferenceStore.putString("profile_display_name", name)
        _state.value = _state.value.copy(displayName = name)
    }

    fun updateAvatarUrl(url: String) {
        preferenceStore.putString("profile_avatar_url", url)
        _state.value = _state.value.copy(avatarUrl = url.takeIf { it.isNotBlank() })
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  Data Models
// ════════════════════════════════════════════════════════════════════════════

data class ProfileUiState(
    val displayName: String = "Anime Fan",
    val avatarUrl: String? = null,
    val anilistUsername: String? = null,
    val totalAnime: Int = 0,
    val totalEpisodesWatched: Int = 0,
    val watchTimeFormatted: String = "0m",
    val avgRatingFormatted: String = "—",
    val currentStreak: Int = 0,
    val genreDistribution: Map<String, Int> = emptyMap(),
    val watchFlowByDay: List<Int> = listOf(0, 0, 0, 0, 0, 0, 0),
    val watchFlowDetail: List<DayWatchSummary> = emptyList(),
    val timeDna: TimeDnaData? = null,
    val activityData: Map<Long, Int> = emptyMap(),
    val avgDailyWatchTime: String = "0m",
    val timeline: List<TimelineItem> = emptyList(),
    val recentlyWatched: List<RecentlyWatchedItem> = emptyList(),
    val selectedGenre: String? = null,
    val genreAnime: List<RecentlyWatchedItem> = emptyList(),
)

data class TimeDnaData(
    val hourlyCounts: List<Int>,
    val preferredTimeLabel: String,
    val totalSessions: Int,
)

data class TimelineItem(
    val anilistId: Int?,
    val title: String,
    val coverUrl: String?,
    val description: String,
    val timestamp: Long,
    val type: String,
    val rating: Int?,
)

data class RecentlyWatchedItem(
    val anilistId: Int?,
    val title: String,
    val coverUrl: String?,
    val episodeThumbnailUrl: String? = null,
    val episodeNumber: Int,
    val progressFraction: Float,
    val lastWatchedAt: Long,
)

/**
 * Per-day-of-week watch summary for the Watch Flow sidebar.
 * - [count]: total episodes watched that weekday (across all weeks).
 * - [totalDurationSec]: total watch duration that weekday (sum of completed-episode durations).
 * - [items]: the most-recent anime watched that weekday (newest-first, capped at 12).
 */
data class DayWatchSummary(
    val count: Int,
    val totalDurationSec: Long,
    val items: List<DayWatchItem>,
)

data class DayWatchItem(
    val anilistId: Int?,
    val title: String,
    val coverUrl: String?,
    val episodeThumbnailUrl: String? = null,
    val episodeNumber: Int,
)
