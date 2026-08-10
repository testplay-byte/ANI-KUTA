package com.confused.anikuta.core.content.genre

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase

/**
 * Repository for the genre system.
 *
 * Seeds the canonical genre list on first launch, provides CRUD for
 * content-genre associations, and handles genre normalization.
 *
 * CORE_RULES §20: logged with tag "Anikuta:Core:Content:Genre".
 */
class GenreRepository(
    private val database: AnikutaDatabase,
) {
    companion object {
        private const val TAG = "Anikuta:Core:Content:Genre"
    }

    private val queries get() = database.genresQueries

    /** Seed the canonical genre list if not already seeded. Idempotent. */
    fun seedIfEmpty() {
        val count = queries.getAllGenres().executeAsList().size
        if (count > 0) {
            Logger.d(TAG) { "Genres already seeded ($count rows)" }
            return
        }
        Logger.i(TAG) { "Seeding canonical genres (${CanonicalGenres.ALL.size} items)..." }
        CanonicalGenres.ALL.forEachIndexed { index, genre ->
            queries.insertGenre(
                anilistName = genre.anilistName,
                displayName = genre.displayName,
                category = genre.category,
                sortKey = index.toLong(),
                isNsfw = if (genre.isNsfw) 1L else 0L,
            )
        }
        Logger.i(TAG) { "Seeded ${CanonicalGenres.ALL.size} genres" }
    }

    /**
     * Set the genres for a content. Deletes existing associations + inserts new ones.
     * Genres are normalized via [CanonicalGenres.normalizeAll] before storage —
     * unrecognized strings are dropped.
     *
     * @param mainId The content's main_id.
     * @param rawGenres The raw genre strings (from AniList or extension).
     * @param source Where the genres came from ("anilist" or "extension").
     */
    fun setGenresForContent(mainId: String, rawGenres: List<String>, source: String = "anilist") {
        val normalized = CanonicalGenres.normalizeAll(rawGenres)
        if (normalized.isEmpty()) {
            Logger.d(TAG) { "setGenres: no valid genres after normalization (raw=${rawGenres.size})" }
            queries.deleteContentGenres(mainId)
            return
        }

        queries.transaction {
            queries.deleteContentGenres(mainId)
            normalized.forEach { genreName ->
                val genre = queries.getGenreByAnilistName(genreName).executeAsOneOrNull()
                if (genre != null) {
                    queries.insertContentGenre(mainId, genre.id, source)
                }
            }
        }
        Logger.d(TAG) { "setGenres: $mainId → ${normalized.size} genres ($normalized)" }
    }

    /**
     * Set genres from a comma-separated string (for backward compat with the old TEXT columns).
     */
    fun setGenresFromCsv(mainId: String, csvGenres: String?, source: String = "anilist") {
        val normalized = CanonicalGenres.normalizeCsv(csvGenres)
        setGenresForContent(mainId, normalized, source)
    }

    /** Get the canonical genre names for a content. */
    fun getGenresForContent(mainId: String): List<String> {
        return queries.getGenresForContent(mainId).executeAsList()
            .map { it.display_name }
    }

    /** Get all genres grouped by category (for the profile page / filter UI). */
    fun getAllGenresByCategory(): Map<String, List<String>> {
        return queries.getAllGenres().executeAsList()
            .groupBy { it.category }
            .mapValues { (_, genres) -> genres.map { it.display_name } }
    }

    /**
     * Get genre counts across all library contents (for the profile genre graph).
     * Returns a map of genre display name → count, sorted by count descending.
     */
    fun getLibraryGenreCounts(libraryMainIds: Set<String>): List<Pair<String, Int>> {
        if (libraryMainIds.isEmpty()) return emptyList()
        val allCounts = queries.getContentGenreCount().executeAsList()
        // Filter to only library contents.
        val libraryGenreMap = mutableMapOf<Long, Int>()
        queries.getAllContentGenres().executeAsList().forEach { cg ->
            if (cg.main_id in libraryMainIds) {
                libraryGenreMap[cg.genre_id] = (libraryGenreMap[cg.genre_id] ?: 0) + 1
            }
        }
        // Map genre_id → display_name.
        val genreMap = queries.getAllGenres().executeAsList().associate { it.id to it.display_name }
        return libraryGenreMap.entries
            .mapNotNull { (genreId, count) -> genreMap[genreId]?.let { it to count } }
            .sortedByDescending { it.second }
    }
}
