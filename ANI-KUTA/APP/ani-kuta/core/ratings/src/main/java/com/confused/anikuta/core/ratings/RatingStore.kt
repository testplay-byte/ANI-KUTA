package com.confused.anikuta.core.ratings

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * Store for per-anime + per-episode user ratings (Phase TR — PLAN §1.6, §1.10).
 *
 * Rating scale: 0-100 (AniList-native; displayed as 0-10 with one decimal).
 * Both tables key off main_id (stable UUID for backup/restore).
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Ratings".
 */
class RatingStore(
    private val database: AnikutaDatabase,
) {
    companion object {
        private const val TAG = "Anikuta:Core:Ratings"
    }

    // ── Per-anime rating ──

    /** Set the per-anime rating (0-100). */
    suspend fun setAnimeRating(mainId: String, rating: Int) = withContext(Dispatchers.IO) {
        val clamped = rating.coerceIn(0, 100)
        val now = System.currentTimeMillis()
        database.ratingsQueries.upsertUserRating(mainId, clamped.toLong(), now)
        Logger.i(TAG) { "setAnimeRating: mainId=$mainId rating=$clamped" }
    }

    /** Get the per-anime rating (null if unrated). */
    suspend fun getAnimeRating(mainId: String): Int? = withContext(Dispatchers.IO) {
        database.ratingsQueries.getUserRating(mainId).executeAsOneOrNull()?.rating?.toInt()
    }

    /** Observe the per-anime rating (reactive). */
    fun observeAnimeRating(mainId: String): Flow<Int?> =
        database.ratingsQueries.getUserRating(mainId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.rating?.toInt() }

    /** Delete the per-anime rating. */
    suspend fun deleteAnimeRating(mainId: String) = withContext(Dispatchers.IO) {
        database.ratingsQueries.deleteUserRating(mainId)
        Logger.d(TAG) { "deleteAnimeRating: mainId=$mainId" }
    }

    // ── Per-episode rating ──

    /** Set the per-episode rating (0-100). */
    suspend fun setEpisodeRating(mainId: String, episodeKey: String, rating: Int) = withContext(Dispatchers.IO) {
        val clamped = rating.coerceIn(0, 100)
        val now = System.currentTimeMillis()
        database.ratingsQueries.upsertUserEpisodeRating(mainId, episodeKey, clamped.toLong(), now)
        Logger.i(TAG) { "setEpisodeRating: mainId=$mainId episodeKey=$episodeKey rating=$clamped" }
    }

    /** Get the per-episode rating (null if unrated). */
    suspend fun getEpisodeRating(mainId: String, episodeKey: String): Int? = withContext(Dispatchers.IO) {
        database.ratingsQueries.getUserEpisodeRating(mainId, episodeKey).executeAsOneOrNull()?.rating?.toInt()
    }

    /** Delete the per-episode rating. */
    suspend fun deleteEpisodeRating(mainId: String, episodeKey: String) = withContext(Dispatchers.IO) {
        database.ratingsQueries.deleteUserEpisodeRating(mainId, episodeKey)
        Logger.d(TAG) { "deleteEpisodeRating: mainId=$mainId episodeKey=$episodeKey" }
    }
}
