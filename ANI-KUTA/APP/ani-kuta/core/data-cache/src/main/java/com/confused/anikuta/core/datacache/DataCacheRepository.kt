package com.confused.anikuta.core.datacache

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase

/**
 * Repository for the local data cache (Phase D).
 *
 * Handles CRUD on:
 * - `anime_metadata_cache` — anime metadata by mainId.
 * - `episode_metadata_cache` — episode metadata by mainId + episodeNumber.
 * - `browse_cache` — browse page sections (6-hour expiry, homepage only).
 *
 * All data persists across restarts (stored in SQLite via SQLDelight).
 * Metadata never expires — the user manually refreshes.
 *
 * CORE_RULES §20: Logged with tag "Anikuta:Core:DataCache".
 */
class DataCacheRepository(
    private val database: AnikutaDatabase,
) {

    companion object {
        private const val TAG = "Anikuta:Core:DataCache"
        private const val BROWSE_CACHE_TTL_MS = 6 * 60 * 60 * 1000L // 6 hours
    }

    private val queries get() = database.dataCacheQueries

    // ── Anime metadata ─────────────────────────────────────────────────────

    fun getAnimeMetadata(mainId: String): CachedAnimeMetadata? {
        return queries.getAnimeMetadata(mainId).executeAsOneOrNull()?.let {
            CachedAnimeMetadata(
                mainId = it.main_id,
                title = it.title,
                description = it.description,
                coverUrl = it.cover_url,
                bannerUrl = it.banner_url,
                score = it.score?.toInt(),
                episodes = it.episodes?.toInt(),
                season = it.season,
                seasonYear = it.season_year?.toInt(),
                status = it.status,
                genres = it.genres,
                sourceType = it.source_type,
                fetchedAt = it.fetched_at,
            )
        }
    }

    fun upsertAnimeMetadata(meta: CachedAnimeMetadata) {
        queries.upsertAnimeMetadata(
            mainId = meta.mainId,
            title = meta.title,
            description = meta.description,
            coverUrl = meta.coverUrl,
            bannerUrl = meta.bannerUrl,
            score = meta.score?.toLong(),
            episodes = meta.episodes?.toLong(),
            season = meta.season,
            seasonYear = meta.seasonYear?.toLong(),
            status = meta.status,
            genres = meta.genres,
            sourceType = meta.sourceType,
            fetchedAt = meta.fetchedAt,
        )
        Logger.d(TAG) { "Cached anime metadata: ${meta.title} (mainId=${meta.mainId})" }
    }

    fun deleteAnimeMetadata(mainId: String) {
        queries.deleteAnimeMetadata(mainId)
    }

    // ── Episode metadata ───────────────────────────────────────────────────

    fun getEpisodeMetadata(mainId: String): List<CachedEpisodeMetadata> {
        return queries.getEpisodeMetadata(mainId).executeAsList().map {
            CachedEpisodeMetadata(
                mainId = it.main_id,
                episodeNumber = it.episode_number.toFloat(),
                title = it.title,
                description = it.description,
                thumbnailUrl = it.thumbnail_url,
                airDate = it.air_date,
                fetchedAt = it.fetched_at,
                episodeUrl = it.episode_url,
            )
        }
    }

    fun upsertEpisodeMetadata(meta: CachedEpisodeMetadata) {
        queries.upsertEpisodeMetadata(
            mainId = meta.mainId,
            episodeNumber = meta.episodeNumber.toDouble(),
            title = meta.title,
            description = meta.description,
            thumbnailUrl = meta.thumbnailUrl,
            airDate = meta.airDate,
            fetchedAt = meta.fetchedAt,
            episodeUrl = meta.episodeUrl,
        )
    }

    fun upsertEpisodeMetadataBatch(metas: List<CachedEpisodeMetadata>) {
        database.transaction {
            for (meta in metas) {
                queries.upsertEpisodeMetadata(
                    mainId = meta.mainId,
                    episodeNumber = meta.episodeNumber.toDouble(),
                    title = meta.title,
                    description = meta.description,
                    thumbnailUrl = meta.thumbnailUrl,
                    airDate = meta.airDate,
                    fetchedAt = meta.fetchedAt,
                    episodeUrl = meta.episodeUrl,
                )
            }
        }
        Logger.d(TAG) { "Cached ${metas.size} episode metadata entries" }
    }

    fun deleteEpisodeMetadata(mainId: String) {
        queries.deleteEpisodeMetadata(mainId)
    }

    // ── Browse cache ───────────────────────────────────────────────────────

    fun getBrowseCache(sectionKey: String): CachedBrowseSection? {
        return queries.getBrowseCache(sectionKey).executeAsOneOrNull()?.let {
            CachedBrowseSection(
                sectionKey = it.section_key,
                dataJson = it.data_json,
                fetchedAt = it.fetched_at,
                expiresAt = it.expires_at,
            )
        }
    }

    fun upsertBrowseCache(sectionKey: String, dataJson: String) {
        val now = System.currentTimeMillis()
        queries.upsertBrowseCache(
            sectionKey = sectionKey,
            dataJson = dataJson,
            fetchedAt = now,
            expiresAt = now + BROWSE_CACHE_TTL_MS,
        )
        Logger.d(TAG) { "Cached browse section: $sectionKey (expires in ${BROWSE_CACHE_TTL_MS / 3600000}h)" }
    }

    /** Whether the browse cache for a section is expired or missing. */
    fun isBrowseCacheExpired(sectionKey: String): Boolean {
        val cached = getBrowseCache(sectionKey) ?: return true
        return cached.isExpired
    }
}
