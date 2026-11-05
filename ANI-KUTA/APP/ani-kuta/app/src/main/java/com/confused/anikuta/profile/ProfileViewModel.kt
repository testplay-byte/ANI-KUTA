package com.confused.anikuta.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.content.genre.GenreRepository
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.preferences.PreferenceStore
import com.confused.anikuta.core.watchprogress.WatchProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * ViewModel for the My Profile screen.
 *
 * ROUND 137 (D-830/D-831 — the device report's tracking overhaul):
 *  - TIME IS REAL now: `watch_progress.played_time_ms` (the cumulative
 *    actually-played counter the players write) is the total-watch-time
 *    source; the per-day numbers come from the `watch_session` ledger
 *    (round 137's per-continuous-streak rows), bucketed by the LOCAL
 *    calendar day. The old model summed episode DURATIONS of completed
 *    episodes — an approximation that ignored both real playback time and
 *    partial watches.
 *  - THE WATCH FLOW IS THE LAST 7 DAYS (today on the far right, six days
 *    back to the left) — the old chart aggregated EVERY week's history into
 *    weekday buckets, which never matched what the user actually did this
 *    week. Each bar is minutes watched; tapping opens a day-detail sheet.
 *  - STREAK uses local-day boundaries and stays alive through "today not
 *    watched YET" (yesterday's activity keeps it) — the old UTC-midnight
 *    math broke both.
 *  - TIME DNA computes its daypart split in ONE place (here) from the same
 *    session ledger; the UI renders it (the old duplicated bucketing showed
 *    the wall-clock period instead of the user's actual peak).
 *
 * D-248's statistic filter (organic, meaningfully-watched episodes only)
 * still governs EPISODE COUNTS — user-marked rows never count there, per
 * the round-24 directive. TIME metrics use the real play counters, which
 * manual marks never inflate.
 */
class ProfileViewModel(
    private val database: AnikutaDatabase,
    private val contentRepository: ContentRepository,
    private val genreRepository: GenreRepository,
    private val preferenceStore: PreferenceStore,
) : ViewModel() {

    companion object {
        private const val TAG = "Anikuta:Feature:Profile"

        /** Task 64 (round 24 — E): the persisted genre-radar filter ("All" or
         *  a category name; remembered across visits). */
        private const val KEY_GENRE_FILTER = "profile_genre_filter"

        private const val ONE_DAY_MS = 24L * 60 * 60 * 1000
    }

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    /**
     * Loads everything. Called on init AND on every screen entry (the
     * ProfileScreen's LaunchedEffect) — the stats were one-shot at VM
     * creation before, so a back-stack return showed stale numbers after a
     * watching session (the report's "inconsistencies going around").
     */
    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Logger.i(TAG) { "Loading profile stats..." }

                // ── Batch identity maps (the N+1 fix): two queries cover every
                // title/cover the page renders. ──
                val titleByMain = HashMap<String, String>()
                val coverByMain = HashMap<String, String?>()
                val anilistIdByMain = HashMap<String, Int?>()
                for (record in contentRepository.getAllMainEntries()) {
                    titleByMain[record.mainId] = record.title
                }
                for (details in database.contentQueries.getAllContentDetails().executeAsList()) {
                    coverByMain[details.main_id] = details.data_cover_url ?: details.ext_thumbnail_url
                    if (details.data_source_type == "anilist") {
                        anilistIdByMain[details.main_id] = details.data_source_ref_id?.toIntOrNull()
                    }
                }

                val libraryItems = database.libraryQueries.getAllLibraryItems().executeAsList()
                val totalAnime = libraryItems.map { it.main_id }.distinct().size
                val libraryMainIds = libraryItems.map { it.main_id }.toSet()

                // ── Task 64 (round 24 — the genre radar category filter) ──────
                val itemsByCategory = mutableMapOf<Long, MutableSet<String>>()
                for (item in libraryItems) {
                    itemsByCategory.getOrPut(item.category_id) { mutableSetOf() }.add(item.main_id)
                }
                val categories = contentRepository.getAllCategories()
                val genreFilterOptions = categories
                    .filter { (itemsByCategory[it.id]?.size ?: 0) > 0 }
                    .map { GenreFilterOption(name = it.name, count = itemsByCategory[it.id]!!.size) }
                val savedGenreFilter = preferenceStore.getString(KEY_GENRE_FILTER, "All")
                val selectedGenreFilter = if (
                    savedGenreFilter == "All" ||
                    genreFilterOptions.any { it.name == savedGenreFilter }
                ) {
                    savedGenreFilter
                } else {
                    preferenceStore.putString(KEY_GENRE_FILTER, "All")
                    "All"
                }

                // ── Watch progress — WITH the real played-time counter this time
                // (the round-127 column the profile never read). ──
                val allProgress = database.watchQueries.getAllWatchProgress().executeAsList().map { row ->
                    WatchProgress(
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
                        playedTimeMs = row.played_time_ms,
                    )
                }
                // D-248 (unchanged): organic, meaningfully-watched episodes only
                // for EPISODE COUNTS. Manual bulk marks (the "2,333 episodes" glitch)
                // never count.
                val countedProgress = allProgress.filter {
                    !it.userMarkedWatched && it.duration > 0 && it.progressFraction >= 0.10f
                }
                val totalEpisodesWatched = countedProgress.count { it.completed }

                // ── The SESSION LEDGER (round 137, D-830): per-continuous-streak
                // rows the players write — the per-day truth. ──
                val sessions = runCatching {
                    database.watchQueries.getAllWatchSessions().executeAsList()
                }.getOrElse { emptyList() }

                val calendar = Calendar.getInstance()

                // ── TOTAL WATCH TIME (real): the cumulative played counters;
                // legacy completed rows without a counter fall back to their
                // episode duration (the best estimate we have for them). ──
                val totalPlayedMs = allProgress.sumOf { row ->
                    if (row.playedTimeMs > 0) {
                        row.playedTimeMs
                    } else if (row.completed && row.duration > 0) {
                        row.duration * 1000L
                    } else {
                        0L
                    }
                }

                // ── LOCAL day keys: both sides of every join below bucket by the
                // device's own midnight (the old heatmap mixed UTC row keys with
                // local grid cells — off by hours; D-831). Day arithmetic goes
                // through Calendar.add — a fixed 24h stride from local midnight
                // drifts an hour across a DST transition and the keys miss
                // (the verifier's P2-4). ──
                fun localDayStart(ms: Long): Long {
                    calendar.timeInMillis = ms
                    calendar.set(Calendar.HOUR_OF_DAY, 0)
                    calendar.set(Calendar.MINUTE, 0)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)
                    return calendar.timeInMillis
                }

                /** [baseDay] (a local-midnight key) shifted by [offsetDays] LOCAL days. */
                fun dayShift(baseDay: Long, offsetDays: Int): Long {
                    calendar.timeInMillis = baseDay
                    calendar.add(Calendar.DAY_OF_MONTH, offsetDays)
                    return calendar.timeInMillis
                }

                // played-ms per LOCAL day (the watch flow + the fallbacks read it).
                val playedMsByDay = HashMap<Long, Long>()
                var sessionMinutesTotal = 0L
                for (session in sessions) {
                    val day = localDayStart(session.started_at)
                    playedMsByDay[day] = (playedMsByDay[day] ?: 0L) + session.played_ms
                    sessionMinutesTotal += session.played_ms
                }

                // Organic episode "touch" days (counts + the streak's activity set).
                val countedByDay = HashMap<Long, MutableList<WatchProgress>>()
                for (progress in countedProgress) {
                    if (progress.lastWatchedAt > 0) {
                        countedByDay.getOrPut(localDayStart(progress.lastWatchedAt)) { mutableListOf() }
                            .add(progress)
                    }
                }

                // ── WATCH FLOW — the LAST 7 DAYS, today rightmost (D-831) ──
                val today = localDayStart(System.currentTimeMillis())
                val hasSessionData = sessionMinutesTotal > 0
                val watchFlowDays = buildList {
                    for (offset in 6 downTo 0) {
                        val dayStart = dayShift(today, -offset)
                        val dayEnd = dayShift(today, -offset + 1)
                        var playedMs = playedMsByDay[dayStart] ?: 0L
                        val touched = countedByDay[dayStart].orEmpty()
                        // Legacy fallback: a day with real episode history but no
                        // session rows (watching from before round 137) still shows
                        // its completed-episode durations — clearly the same unit
                        // the whole chart switches to once sessions start landing.
                        if (!hasSessionData && playedMs == 0L) {
                            playedMs = touched.filter { it.completed }.sumOf { it.duration } * 1000L
                        }
                        val items = touched
                            .sortedByDescending { it.lastWatchedAt }
                            .take(12)
                            .mapNotNull { progress ->
                                val mainId = progress.mainId ?: return@mapNotNull null
                                val epNum = progress.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0
                                // The day detail shows the minutes watched THAT DAY
                                // (an all-time sum overstated rewatched episodes —
                                // the verifier's P3-10).
                                val itemSeconds = sessions
                                    .filter {
                                        it.episode_key == progress.episodeKey &&
                                            it.started_at >= dayStart && it.started_at < dayEnd
                                    }
                                    .sumOf { it.played_ms } / 1000L
                                DayWatchItem(
                                    anilistId = anilistIdByMain[mainId],
                                    title = titleByMain[mainId] ?: "Unknown",
                                    coverUrl = coverByMain[mainId],
                                    episodeThumbnailUrl = getEpisodeThumbnail(mainId, epNum)
                                        ?: coverByMain[mainId],
                                    episodeNumber = epNum,
                                    secondsWatched = itemSeconds,
                                    completed = progress.completed,
                                )
                            }
                        add(
                            WatchFlowDay(
                                dateMs = dayStart,
                                secondsWatched = playedMs / 1000L,
                                episodes = touched.size,
                                items = items,
                                isToday = offset == 0,
                            ),
                        )
                    }
                }

                // ── STREAK (D-831): local-day math; survives "today not watched YET" ──
                val currentStreak = calculateCurrentStreak(
                    activeDays = playedMsByDay.filterValues { it > 0 }.keys +
                        countedByDay.keys,
                    today = today,
                )

                // ── TIME DNA (D-831): ONE bucketing, from the session ledger when
                // it has data (the hours the user actually pressed play), else the
                // organic episodes' timestamps. The UI renders this — no duplicate. ──
                val hourlyMinutes = IntArray(24)
                if (hasSessionData) {
                    for (session in sessions) {
                        calendar.timeInMillis = session.started_at
                        hourlyMinutes[calendar.get(Calendar.HOUR_OF_DAY)] += (session.played_ms / 60_000L).toInt()
                    }
                } else {
                    for (progress in countedProgress) {
                        if (progress.lastWatchedAt > 0) {
                            calendar.timeInMillis = progress.lastWatchedAt
                            hourlyMinutes[calendar.get(Calendar.HOUR_OF_DAY)] += 1
                        }
                    }
                }
                val timeDna = buildTimeDna(hourlyMinutes.toList())

                // ── Genre distribution — backfill from existing anilist_detail.genres first ──
                genreRepository.backfillGenresFromExistingData(database)
                val genreCounts = genreRepository.getLibraryGenreCounts(libraryMainIds)
                val genreDistribution = genreCounts.associate { it.first to it.second }
                val filteredGenreDistribution = if (selectedGenreFilter == "All") {
                    genreDistribution
                } else {
                    val filterCategoryId = categories.firstOrNull { it.name == selectedGenreFilter }?.id
                    val scopeIds = filterCategoryId?.let { itemsByCategory[it] } ?: emptySet()
                    if (scopeIds.isEmpty()) {
                        genreDistribution
                    } else {
                        genreRepository.getLibraryGenreCounts(scopeIds).associate { it.first to it.second }
                    }
                }

                // ── ACTIVITY HEATMAP — minutes per LOCAL day (real intensity);
                // days without session data fall back to their episode counts. ──
                val activityMinutes = HashMap<Long, Long>()
                val spanStart = allProgress.mapNotNull { it.firstWatchedAt }.minOrNull()
                    ?: allProgress.map { it.lastWatchedAt }.minOrNull()
                    ?: System.currentTimeMillis()
                for (session in sessions) {
                    val day = localDayStart(session.started_at)
                    activityMinutes[day] = (activityMinutes[day] ?: 0L) + session.played_ms / 60_000L
                }
                for ((day, rows) in countedByDay) {
                    if (!activityMinutes.containsKey(day)) {
                        activityMinutes[day] = rows.count { it.completed }.toLong()
                    }
                }

                // Avg daily watch time over the WHOLE watching span (the old
                // divide-by-active-days inflated the number — D-831).
                val spanDays = run {
                    var d = localDayStart(spanStart)
                    var n = 1
                    while (d < today && n < 365) {
                        d = dayShift(d, 1)
                        n++
                    }
                    n
                }
                val avgDailyWatchTimeSec = (totalPlayedMs / 1000L) / spanDays
                val avgDailyWatchTime = formatWatchTime(avgDailyWatchTimeSec)

                // "From where to where" (D-831): the watching span for the header.
                val lastWatched = allProgress.maxOfOrNull { it.lastWatchedAt } ?: 0L
                val firstWatched = spanStart.takeIf { it > 0 }

                // Timeline
                val timeline = buildTimeline(countedProgress, titleByMain, coverByMain, anilistIdByMain)

                // AniList username
                val anilistUsername = preferenceStore.getString("anilist_username", "").takeIf { it.isNotBlank() }

                // Recently watched (D-248 organic >20% only — unchanged) with the
                // batch-resolved identities.
                val recentlyWatched = allProgress
                    .filter { !it.userMarkedWatched && it.duration > 0 && it.progressFraction > 0.20f }
                    .sortedByDescending { it.lastWatchedAt }
                    .take(10)
                    .mapNotNull { progress ->
                        val mainId = progress.mainId ?: return@mapNotNull null
                        val epNum = progress.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0
                        RecentlyWatchedItem(
                            anilistId = anilistIdByMain[mainId],
                            title = titleByMain[mainId] ?: "Unknown",
                            coverUrl = coverByMain[mainId],
                            episodeThumbnailUrl = getEpisodeThumbnail(mainId, epNum)
                                ?: coverByMain[mainId],
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
                    watchTimeFormatted = formatWatchTime(totalPlayedMs / 1000L),
                    totalMinutesWatched = (totalPlayedMs / 60_000L).toInt(),
                    firstWatchedAt = firstWatched,
                    lastWatchedAt = lastWatched.takeIf { it > 0 },
                    avgRatingFormatted = formatAvgRating(database),
                    currentStreak = currentStreak,
                    genreDistribution = genreDistribution,
                    filteredGenreDistribution = filteredGenreDistribution,
                    genreFilterOptions = genreFilterOptions,
                    selectedGenreFilter = selectedGenreFilter,
                    watchFlowDays = watchFlowDays,
                    timeDna = timeDna,
                    activityMinutes = activityMinutes,
                    avgDailyWatchTime = avgDailyWatchTime,
                    timeline = timeline,
                    recentlyWatched = recentlyWatched,
                )

                Logger.i(TAG) {
                    "Profile stats loaded: $totalAnime anime, $totalEpisodesWatched episodes, " +
                        "${totalPlayedMs / 60000} min played, ${sessions.size} sessions"
                }
            } catch (e: Exception) {
                Logger.e(TAG, e) { "Failed to load profile stats: ${e.message}" }
            }
        }
    }

    private fun formatAvgRating(database: AnikutaDatabase): String {
        val allRatings = database.ratingsQueries.getAllUserRatings().executeAsList()
        val ratings = allRatings.mapNotNull { it?.rating?.toInt() }
        return if (ratings.isNotEmpty() && ratings.average() > 0) {
            String.format(Locale.US, "%.1f", ratings.average() / 10.0)
        } else {
            "—"
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

    /**
     * D-831: the streak walks LOCAL days from today; "today not watched yet"
     * keeps yesterday's run alive (the day isn't over). Returns 0 only when
     * neither today nor yesterday had activity.
     */
    private fun calculateCurrentStreak(activeDays: Set<Long>, today: Long): Int {
        if (activeDays.isEmpty()) return 0
        val days = activeDays.toHashSet()
        // DST-safe: Calendar steps LOCAL days (a fixed 24h stride drifts across
        // clock changes and ends the walk early — the verifier's P2-4).
        val cal = Calendar.getInstance()
        var check = today
        if (check !in days) {
            cal.timeInMillis = check
            cal.add(Calendar.DAY_OF_MONTH, -1)
            check = cal.timeInMillis // today pending — judge from yesterday
        }
        var streak = 0
        while (check in days) {
            streak++
            cal.timeInMillis = check
            cal.add(Calendar.DAY_OF_MONTH, -1)
            check = cal.timeInMillis
        }
        return streak
    }

    /**
     * D-831: the daypart split — minutes-based when sessions exist (the real
     * "when do I press play"), organic-episode-count based otherwise. The UI
     * renders these four buckets + the peak; no duplicate logic there.
     */
    private fun buildTimeDna(hourlyMinutes: List<Int>): TimeDnaData {
        val morning = (6..11).sumOf { hourlyMinutes[it] }
        val afternoon = (12..17).sumOf { hourlyMinutes[it] }
        val evening = (18..22).sumOf { hourlyMinutes[it] }
        val night = (23..23).sumOf { hourlyMinutes[it] } + (0..5).sumOf { hourlyMinutes[it] }

        val total = morning + afternoon + evening + night
        val peakLabel = when (maxOf(morning, afternoon, evening, night)) {
            morning -> "Morning"
            afternoon -> "Afternoon"
            evening -> "Evening"
            else -> "Night"
        }

        return TimeDnaData(
            morning = morning,
            afternoon = afternoon,
            evening = evening,
            night = night,
            totalMinutes = total,
            peakPeriodLabel = peakLabel,
        )
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

    private fun buildTimeline(
        progress: List<WatchProgress>,
        titleByMain: Map<String, String>,
        coverByMain: Map<String, String?>,
        anilistIdByMain: Map<String, Int?>,
    ): List<TimelineItem> {
        val timeline = mutableListOf<TimelineItem>()

        progress.filter { it.lastWatchedAt > 0 }.sortedByDescending { it.lastWatchedAt }.take(50).forEach { p ->
            val mainId = p.mainId ?: return@forEach
            val epNum = p.episodeKey.substringAfterLast('|').toIntOrNull() ?: 0
            timeline.add(
                TimelineItem(
                    anilistId = anilistIdByMain[mainId],
                    title = titleByMain[mainId] ?: "Unknown",
                    coverUrl = coverByMain[mainId],
                    description = "Watched EP $epNum" + if (p.completed) " (completed)" else "",
                    timestamp = p.lastWatchedAt,
                    type = "watch",
                    rating = null,
                ),
            )
        }

        val ratings = database.ratingsQueries.getAllUserRatings().executeAsList()
        ratings.forEach { rating ->
            val r = rating as? com.confused.anikuta.core.database.User_rating ?: return@forEach
            val mainId = r.main_id ?: return@forEach
            timeline.add(
                TimelineItem(
                    anilistId = anilistIdByMain[mainId],
                    title = titleByMain[mainId] ?: "Unknown",
                    coverUrl = coverByMain[mainId],
                    description = "Rated ${r.rating?.toInt()?.div(10)}/10",
                    timestamp = r.rated_at ?: 0,
                    type = "rating",
                    rating = r.rating?.toInt(),
                ),
            )
        }

        return timeline.sortedByDescending { it.timestamp }
    }

    fun onGenreClick(genre: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val libraryItems = database.libraryQueries.getAllLibraryItems().executeAsList()
            val libraryMainIds = libraryItems.map { it.main_id }.toSet()
            // Task 64 (round 24 — E): the genre sheet respects the active
            // category filter — the sheet lists the anime of that genre WITHIN
            // the selected category's scope ("All" = the whole library).
            val activeFilter = _state.value.selectedGenreFilter
            val scopeMainIds = if (activeFilter == "All") {
                libraryMainIds
            } else {
                val filterCategoryIds = contentRepository.getAllCategories()
                    .filter { it.name == activeFilter }
                    .mapNotNull { it.id }
                    .toSet()
                libraryItems
                    .filter { it.category_id in filterCategoryIds }
                    .map { it.main_id }
                    .toSet()
            }
            val genreAnime = scopeMainIds.mapNotNull { mainId ->
                val details = contentRepository.getContentDetails(mainId) ?: return@mapNotNull null
                if (details.anilistId == null) return@mapNotNull null
                val genres = genreRepository.getGenresForContent(mainId)
                if (genre in genres) {
                    RecentlyWatchedItem(
                        anilistId = details.anilistId,
                        title = contentRepository.getMainEntryByMainId(mainId)?.title ?: "Unknown",
                        coverUrl = details.dataCoverUrl,
                        episodeNumber = 0,
                        progressFraction = 0f,
                        lastWatchedAt = 0,
                    )
                } else null
            }
            _state.value = _state.value.copy(selectedGenre = genre, genreAnime = genreAnime)
        }
    }

    /**
     * Task 64 (round 24 — E): select the genre-radar category filter.
     * Persists the choice ("profile_genre_filter"); the radar re-derives.
     */
    fun onGenreFilterSelect(filter: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val effective = if (
                filter == "All" ||
                _state.value.genreFilterOptions.any { it.name == filter }
            ) filter else "All"
            preferenceStore.putString(KEY_GENRE_FILTER, effective)

            val libraryItems = database.libraryQueries.getAllLibraryItems().executeAsList()
            val fullDistribution = _state.value.genreDistribution
            val filtered = if (effective == "All") {
                fullDistribution
            } else {
                val filterCategoryIds = contentRepository.getAllCategories()
                    .filter { it.name == effective }
                    .mapNotNull { it.id }
                    .toSet()
                val scopeIds = libraryItems
                    .filter { it.category_id in filterCategoryIds }
                    .map { it.main_id }
                    .toSet()
                if (scopeIds.isEmpty()) {
                    fullDistribution
                } else {
                    genreRepository.getLibraryGenreCounts(scopeIds).associate { it.first to it.second }
                }
            }
            _state.value = _state.value.copy(
                selectedGenreFilter = effective,
                filteredGenreDistribution = filtered,
            )
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
    /** The REAL total (played-time counters; legacy completed rows use durations). */
    val watchTimeFormatted: String = "0m",
    /** The total in whole minutes — the watch-flow header uses it. */
    val totalMinutesWatched: Int = 0,
    /** D-831: the watching span ("from where to where") for the header. */
    val firstWatchedAt: Long? = null,
    val lastWatchedAt: Long? = null,
    val avgRatingFormatted: String = "—",
    val currentStreak: Int = 0,
    /** The FULL-library genre distribution — drives the genre chips section
     *  + the section's visibility (Task 64: the section must NEVER disappear
     *  because a filter's scope is empty). */
    val genreDistribution: Map<String, Int> = emptyMap(),
    /** The filter-restricted distribution — what the RADAR draws (Task 64). */
    val filteredGenreDistribution: Map<String, Int> = emptyMap(),
    /** The genre-radar category filter options — ONLY categories with ≥1
     *  library entry (Task 64: empty categories never render). */
    val genreFilterOptions: List<GenreFilterOption> = emptyList(),
    /** The active genre-radar filter ("All" or a category name; Task 64). */
    val selectedGenreFilter: String = "All",
    /**
     * D-831: the LAST 7 DAYS, oldest FIRST (today is the LAST element — the
     * rightmost bar). secondsWatched is real watch time; episodes are the
     * organic touches.
     */
    val watchFlowDays: List<WatchFlowDay> = emptyList(),
    val timeDna: TimeDnaData? = null,
    /** D-831: minutes per LOCAL-midnight day — the heatmap's real intensity. */
    val activityMinutes: Map<Long, Long> = emptyMap(),
    val avgDailyWatchTime: String = "0m",
    val timeline: List<TimelineItem> = emptyList(),
    val recentlyWatched: List<RecentlyWatchedItem> = emptyList(),
    val selectedGenre: String? = null,
    val genreAnime: List<RecentlyWatchedItem> = emptyList(),
)

/**
 * Task 64 (round 24 — E): one selectable option in the genre-radar category
 * filter row — a library category that has at least one entry.
 */
data class GenreFilterOption(
    val name: String,
    val count: Int,
)

/**
 * D-831: the Time DNA daypart split — computed ONCE here from the session
 * ledger (minutes) or the organic episodes (counts); the UI renders it and
 * shows [peakPeriodLabel] in the donut center.
 */
data class TimeDnaData(
    val morning: Int,
    val afternoon: Int,
    val evening: Int,
    val night: Int,
    val totalMinutes: Int,
    val peakPeriodLabel: String,
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
 * D-831: ONE day of the last-7-days watch flow. [minutes] is REAL watch time
 * (sessions; legacy days fall back to completed-episode durations), [items]
 * is the day's organic episode touches (newest first, capped at 12).
 */
data class WatchFlowDay(
    val dateMs: Long,
    /** SECONDS watched that day (sessions; legacy days fall back to durations). */
    val secondsWatched: Long,
    val episodes: Int,
    val items: List<DayWatchItem>,
    val isToday: Boolean,
)

data class DayWatchItem(
    val anilistId: Int?,
    val title: String,
    val coverUrl: String?,
    val episodeThumbnailUrl: String? = null,
    val episodeNumber: Int,
    /** The SECONDS watched of this episode ON THAT DAY (0 when the ledger has no data for it). */
    val secondsWatched: Long = 0,
    val completed: Boolean = false,
)
