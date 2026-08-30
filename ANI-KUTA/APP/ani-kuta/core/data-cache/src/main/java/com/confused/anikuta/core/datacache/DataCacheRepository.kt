package com.confused.anikuta.core.datacache

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.common.parseAudioAvailability
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
    //
    // D-198: getAnimeMetadata / upsertAnimeMetadata / deleteAnimeMetadata REMOVED.
    // The anime_metadata_cache table was absorbed into content_details (data-source
    // axis). Callers now use ContentRepository.getContentDetails(mainId) +
    // ContentDetails.dataSynopsis / dataCoverUrl / dataScore / etc.

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
                sourceName = it.source_name,
                scanlator = it.scanlator,
                isFiller = it.is_filler?.let { b -> b != 0L },
                isRecap = it.is_recap?.let { b -> b != 0L },
                titleJapanese = it.title_japanese,
                titleRomaji = it.title_romaji,
                runtime = it.runtime?.toInt(),
                seasonNumber = it.season_number?.toInt(),
                episodeNumberInSeason = it.episode_number_in_season?.toInt(),
                score = it.score,
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
            sourceName = meta.sourceName,
            scanlator = meta.scanlator,
            isFiller = meta.isFiller?.let { if (it) 1L else 0L },
            isRecap = meta.isRecap?.let { if (it) 1L else 0L },
            titleJapanese = meta.titleJapanese,
            titleRomaji = meta.titleRomaji,
            runtime = meta.runtime?.toLong(),
            seasonNumber = meta.seasonNumber?.toLong(),
            episodeNumberInSeason = meta.episodeNumberInSeason?.toLong(),
            score = meta.score,
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
                    sourceName = meta.sourceName,
                    scanlator = meta.scanlator,
                    isFiller = meta.isFiller?.let { if (it) 1L else 0L },
                    isRecap = meta.isRecap?.let { if (it) 1L else 0L },
                    titleJapanese = meta.titleJapanese,
                    titleRomaji = meta.titleRomaji,
                    runtime = meta.runtime?.toLong(),
                    seasonNumber = meta.seasonNumber?.toLong(),
                    episodeNumberInSeason = meta.episodeNumberInSeason?.toLong(),
                    score = meta.score,
                )
            }
        }
    }

    /**
     * D-285: Per-anime aggregates the Library's RELEASED/audio badges need,
     * computed from ONE batch read of the episode table (only the 4 columns the
     * audio parser + episode counter touch — a fraction of the full row, no
     * description/thumbnail/etc. deserialized).
     *
     * Replaces the per-entry `getEpisodeMetadata(mainId)` loop in the Library's
     * enrichment pass: a 653-item library used to run 653 full-row episode
     * queries per load; now it's ONE lightweight query + in-memory aggregation
     * (identical semantics: releasedCount = episode-row count, audio flags
     * aggregated with the same [parseAudioAvailability] call the old loop used).
     *
     * @return mainId → aggregates. Anime with NO cached episodes are absent.
     */
    fun getAllEpisodeAudioAggregates(): Map<String, EpisodeAudioAggregates> {
        val rows = queries.getAllEpisodeAudioRows().executeAsList()
        if (rows.isEmpty()) return emptyMap()

        val byMainId = rows.groupBy { it.main_id }
        val result = HashMap<String, EpisodeAudioAggregates>(byMainId.size)
        for ((mainId, episodeRows) in byMainId) {
            var hasSub = false
            var hasDub = false
            var hasHsub = false
            var subCount = 0
            var dubCount = 0
            for (row in episodeRows) {
                val audio = parseAudioAvailability(
                    scanlator = row.scanlator,
                    episodeName = row.source_name ?: row.title ?: "",
                )
                if (audio.hasSub) { hasSub = true; subCount++ }
                if (audio.hasDub) { hasDub = true; dubCount++ }
                if (audio.hasHsub) hasHsub = true
            }
            result[mainId] = EpisodeAudioAggregates(
                releasedCount = episodeRows.size,
                hasSub = hasSub,
                hasDub = hasDub,
                hasHsub = hasHsub,
                subCount = subCount,
                dubCount = dubCount,
            )
        }
        Logger.d(TAG) { "getAllEpisodeAudioAggregates: ${result.size} anime from ${rows.size} episode rows" }
        return result
    }

    fun deleteEpisodeMetadata(mainId: String) {
        queries.deleteEpisodeMetadata(mainId)
    }

    /**
     * Task 50 (round 10, Fix F): one-shot startup purge of legacy episode rows
     * that can never resolve. Rows with NULL/blank episode_url (v0.2.68-era
     * writes) restore with url = animeUrl (the SERIES url) — loadLinks on a
     * series URL always fails, so those series showed "no episodes resolve"
     * forever. Idempotent; safe to call on every start. Never touches rows
     * with a non-blank episode_url.
     */
    fun purgeUnresolvableEpisodeRows() {
        // SQLDelight 2.x: DELETE executes directly (same shape as
        // deleteEpisodeMetadata above); COUNT(*) is a Query<Long>.
        val purged = queries.countUnresolvableEpisodes().executeAsOne()
        if (purged > 0L) {
            queries.purgeUnresolvableEpisodes()
            Logger.i(TAG) { "purge: removed $purged unresolvable episode row(s) (null/blank episode_url)" }
        }
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
