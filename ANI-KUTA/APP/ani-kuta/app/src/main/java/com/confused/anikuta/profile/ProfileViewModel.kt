package com.confused.anikuta.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.content.genre.GenreRepository
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.core.ratings.RatingStore
import com.confused.anikuta.core.watchprogress.WatchProgressStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the My Profile screen.
 *
 * Computes stats from the database:
 * - Total anime in library (library_item count)
 * - Total episodes watched (watch_progress where completed=1)
 * - Total watch time (sum of duration from watch_progress)
 * - Average rating given (user_rating average)
 * - Top genre (from content_genre via GenreRepository)
 * - Current streak (consecutive days with watch_progress entries)
 * - Genre distribution (genre → count, from GenreRepository)
 * - Recently watched (last 10 watch_progress entries, enriched with content data)
 * - Top rated (user_rating sorted by rating desc)
 * - Activity heatmap data (activity_event or watch_progress by day)
 */
class ProfileViewModel(
    private val database: AnikutaDatabase,
    private val contentRepository: ContentRepository,
    private val genreRepository: GenreRepository,
    private val ratingStore: RatingStore,
    private val watchProgressStore: WatchProgressStore,
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

                // Library count
                val libraryItems = database.libraryQueries.getAllLibraryItems().executeAsList()
                val totalAnime = libraryItems.map { it.main_id }.distinct().size
                val libraryMainIds = libraryItems.map { it.main_id }.toSet()

                // Watch progress stats
                val allProgress = watchProgressStore.getAllWatchProgress()
                val totalEpisodesWatched = allProgress.count { it.completed }
                val totalWatchTimeSec = allProgress.sumOf { it.duration * (if (it.completed) 1 else 0) }
                val watchTimeHours = totalWatchTimeSec / 3600
                val watchTimeMins = (totalWatchTimeSec % 3600) / 60
                val watchTimeFormatted = if (watchTimeHours > 0) "${watchTimeHours}h ${watchTimeMins}m" else "${watchTimeMins}m"

                // Average rating
                val allRatings = ratingStore.getAllUserRatings()
                val avgRating = if (allRatings.isNotEmpty()) {
                    allRatings.mapNotNull { it?.rating?.toInt() ?: it?.rating?.toInt() }.let { ratings ->
                        if (ratings.isNotEmpty()) ratings.average() else 0.0
                    }
                } else 0.0
                val avgRatingFormatted = if (avgRating > 0) String.format("%.1f", avgRating / 10.0) else "—"

                // Genre distribution
                val genreCounts = genreRepository.getLibraryGenreCounts(libraryMainIds)
                val genreDistribution = genreCounts.associate { it.first to it.second }
                val topGenre = genreCounts.firstOrNull()?.first

                // Current streak (consecutive days with watch activity)
                val currentStreak = calculateCurrentStreak(allProgress)

                // Recently watched (last 10, enriched)
                val recentlyWatched = allProgress
                    .sortedByDescending { it.lastWatchedAt }
                    .take(10)
                    .mapNotNull { progress ->
                        val mid = progress.mainId ?: return@mapNotNull null
                        val content = contentRepository.getContentByMainId(mid) ?: return@mapNotNull null
                        val anilistDetail = contentRepository.getAniListDetail(mid)
                        val coverUrl = anilistDetail?.coverUrl
                        val anilistId = anilistDetail?.anilistId
                        val episodeNumber = progress.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0
                        RecentlyWatchedItem(
                            anilistId = anilistId,
                            title = content.title,
                            coverUrl = coverUrl,
                            episodeNumber = episodeNumber,
                            progressFraction = progress.progressFraction,
                            lastWatchedAt = progress.lastWatchedAt,
                        )
                    }

                // Top rated
                val topRated = allRatings.mapNotNull { rating ->
                    val mid = rating?.main_id ?: return@mapNotNull null
                    val content = contentRepository.getContentByMainId(mid) ?: return@mapNotNull null
                    val anilistDetail = contentRepository.getAniListDetail(mid)
                    val anilistId = anilistDetail?.anilistId
                    val coverUrl = anilistDetail?.coverUrl
                    val ratingValue = rating.rating?.toInt() ?: 0
                    if (anilistId != null) {
                        TopRatedItem(
                            anilistId = anilistId,
                            title = content.title,
                            coverUrl = coverUrl,
                            rating = ratingValue,
                        )
                    } else null
                }.sortedByDescending { it.rating }.take(10)

                // Activity heatmap data (watch_progress by day)
                val activityData = buildActivityData(allProgress)

                // AniList username (from preferences)
                val anilistUsername = preferenceStore.getString("anilist_username", null)

                _state.value = ProfileUiState(
                    displayName = "Anime Fan", // TODO: make editable
                    anilistUsername = anilistUsername,
                    totalAnime = totalAnime,
                    totalEpisodesWatched = totalEpisodesWatched,
                    watchTimeFormatted = watchTimeFormatted,
                    avgRatingFormatted = avgRatingFormatted,
                    topGenre = topGenre,
                    currentStreak = currentStreak,
                    genreDistribution = genreDistribution,
                    recentlyWatched = recentlyWatched,
                    topRated = topRated,
                    activityData = activityData,
                )

                Logger.i(TAG) { "Profile stats loaded: $totalAnime anime, $totalEpisodesWatched episodes, ${genreCounts.size} genres" }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed to load profile stats: ${e.message}" }
            }
        }
    }

    private fun calculateCurrentStreak(progress: List<com.confused.anikuta.core.watchprogress.WatchProgress>): Int {
        if (progress.isEmpty()) return 0
        val now = System.currentTimeMillis()
        val oneDayMs = 24 * 60 * 60 * 1000L
        val todayStart = (now / oneDayMs) * oneDayMs

        // Get unique days with watch activity
        val activeDays = progress.map { it.lastWatchedAt / oneDayMs }.toSet().sortedDescending()

        var streak = 0
        var checkDay = todayStart / oneDayMs
        for (day in activeDays) {
            if (day == checkDay) {
                streak++
                checkDay--
            } else if (day < checkDay) {
                break
            }
        }
        return streak
    }

    private fun buildActivityData(progress: List<com.confused.anikuta.core.watchprogress.WatchProgress>): Map<Long, Int> {
        val oneDayMs = 24 * 60 * 60 * 1000L
        return progress
            .filter { it.lastWatchedAt > 0 }
            .groupBy { (it.lastWatchedAt / oneDayMs) * oneDayMs }
            .mapValues { it.value.size }
    }

    // ── Genre click ──

    fun onGenreClick(genre: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val libraryItems = database.libraryQueries.getAllLibraryItems().executeAsList()
            val libraryMainIds = libraryItems.map { it.main_id }.toSet()

            // Get all content with this genre
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

            _state.value = _state.value.copy(
                selectedGenre = genre,
                genreAnime = genreAnime,
            )
        }
    }

    fun clearGenreSelection() {
        _state.value = _state.value.copy(selectedGenre = null, genreAnime = emptyList())
    }
}

data class ProfileUiState(
    val displayName: String = "Anime Fan",
    val anilistUsername: String? = null,
    val totalAnime: Int = 0,
    val totalEpisodesWatched: Int = 0,
    val watchTimeFormatted: String = "0m",
    val avgRatingFormatted: String = "—",
    val topGenre: String? = null,
    val currentStreak: Int = 0,
    val genreDistribution: Map<String, Int> = emptyMap(),
    val recentlyWatched: List<RecentlyWatchedItem> = emptyList(),
    val topRated: List<TopRatedItem> = emptyList(),
    val activityData: Map<Long, Int> = emptyMap(),
    val selectedGenre: String? = null,
    val genreAnime: List<RecentlyWatchedItem> = emptyList(),
)

data class RecentlyWatchedItem(
    val anilistId: Int?,
    val title: String,
    val coverUrl: String?,
    val episodeNumber: Int,
    val progressFraction: Float,
    val lastWatchedAt: Long,
)

data class TopRatedItem(
    val anilistId: Int,
    val title: String,
    val coverUrl: String?,
    val rating: Int,  // 0-100
)
