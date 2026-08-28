package com.confused.anikuta.core.content

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase

/**
 * Repository for the content identity system.
 *
 * Handles CRUD on:
 * - `main_entry` (renamed from `content` per D-198)
 * - `content_details` (merged from anilist_detail + extension_detail + other_source_detail + anime_metadata_cache)
 * - `data_source`, `system` (lookup tables)
 * - `library_category`, `library_item` (library tables)
 *
 * Seeds the lookup tables + Default library category on first launch.
 *
 * D-198: renames — getContentBy* → getMainEntryBy*, insertContent → insertMainEntry,
 * updateContentSources → updateMainEntrySources, updateContentDisplaySource →
 * updateMainEntryDisplaySource, updateContentContentId → updateMainEntryContentId,
 * deleteContent → deleteMainEntry. The `description` column was dropped from
 * main_entry — readers now use [ContentDetails.dataSynopsis] / [ContentDetails.extDescription].
 *
 * CORE_RULES §7: Backend logic — no UI.
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Content:Repo".
 */
class ContentRepository(
    private val database: AnikutaDatabase,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Content:Repo"
        private const val DEFAULT_CATEGORY_NAME = "Default"
    }

    private val contentQueries get() = database.contentQueries
    private val libraryQueries get() = database.libraryQueries

    // ── Seeding ────────────────────────────────────────────────────────────

    /**
     * Seed lookup tables + Default library category on first launch.
     * Idempotent — uses INSERT OR IGNORE.
     */
    fun seedDefaults() {
        val now = System.currentTimeMillis()
        Logger.i(TAG) { "Seeding defaults..." }

        // Data sources
        listOf(
            Triple("anilist", "AniList", "metadata"),
            Triple("tmdb", "TMDB", "metadata"),
            Triple("kitsu", "Kitsu", "metadata"),
            Triple("mal", "MAL", "metadata"),
        ).forEach { (name, displayName, type) ->
            contentQueries.insertDataSource(name, displayName, type, now)
        }

        // Systems
        listOf(
            Triple("aniyomi", "Aniyomi", "eu.kanade.tachiyomi"),
            Triple("cloudstream", "CloudStream", "com.lagradost.cloudstream"),
            Triple("sora", "Sora", "com.sora"),
            Triple("mangayomi", "MangaYomi", "com.mangayomi"),
        ).forEach { (name, displayName, prefix) ->
            contentQueries.insertSystem(name, displayName, prefix, now)
        }

        // Default library category (permanent)
        libraryQueries.insertCategory(
            name = DEFAULT_CATEGORY_NAME,
            displayOrder = 0,
            isPermanent = 1,
            createdAt = now,
        )

        Logger.i(TAG) { "Seeding complete" }
    }

    // ── main_entry CRUD ────────────────────────────────────────────────────

    fun getMainEntryByMainId(mainId: String): ContentRecord? {
        return contentQueries.getMainEntryByMainId(mainId).executeAsOneOrNull()?.let {
            ContentRecord(
                mainId = it.main_id,
                contentId = it.content_id,
                title = it.title,
                contentType = it.content_type,
                contentFormat = it.content_format,
                dataSourceId = it.data_source_id,
                systemId = it.system_id,
                extensionRepoId = it.extension_repo_id,
                extensionId = it.extension_id,
                sourceId = it.source_id,
                animeUrl = it.anime_url,
                displaySource = it.display_source,
                createdAt = it.created_at,
                updatedAt = it.updated_at,
            )
        }
    }

    fun getMainEntryByAniListId(anilistId: Int): ContentRecord? {
        return contentQueries.getMainEntryByAniListId(anilistId.toString()).executeAsOneOrNull()?.let {
            ContentRecord(
                mainId = it.main_id,
                contentId = it.content_id,
                title = it.title,
                contentType = it.content_type,
                contentFormat = it.content_format,
                dataSourceId = it.data_source_id,
                systemId = it.system_id,
                extensionRepoId = it.extension_repo_id,
                extensionId = it.extension_id,
                sourceId = it.source_id,
                animeUrl = it.anime_url,
                displaySource = it.display_source,
                createdAt = it.created_at,
                updatedAt = it.updated_at,
            )
        }
    }

    fun getMainEntryByExtension(extensionId: Long, animeUrl: String): ContentRecord? {
        return contentQueries.getMainEntryByExtension(extensionId, animeUrl).executeAsOneOrNull()?.let {
            ContentRecord(
                mainId = it.main_id,
                contentId = it.content_id,
                title = it.title,
                contentType = it.content_type,
                contentFormat = it.content_format,
                dataSourceId = it.data_source_id,
                systemId = it.system_id,
                extensionRepoId = it.extension_repo_id,
                extensionId = it.extension_id,
                sourceId = it.source_id,
                animeUrl = it.anime_url,
                displaySource = it.display_source,
                createdAt = it.created_at,
                updatedAt = it.updated_at,
            )
        }
    }

    fun getMainEntryByContentId(contentId: String): ContentRecord? {
        return contentQueries.getMainEntryByContentId(contentId).executeAsOneOrNull()?.let {
            ContentRecord(
                mainId = it.main_id,
                contentId = it.content_id,
                title = it.title,
                contentType = it.content_type,
                contentFormat = it.content_format,
                dataSourceId = it.data_source_id,
                systemId = it.system_id,
                extensionRepoId = it.extension_repo_id,
                extensionId = it.extension_id,
                sourceId = it.source_id,
                animeUrl = it.anime_url,
                displaySource = it.display_source,
                createdAt = it.created_at,
                updatedAt = it.updated_at,
            )
        }
    }

    fun insertMainEntry(record: ContentRecord) {
        contentQueries.insertMainEntry(
            mainId = record.mainId,
            contentId = record.contentId,
            title = record.title,
            contentType = record.contentType,
            contentFormat = record.contentFormat,
            dataSourceId = record.dataSourceId,
            systemId = record.systemId,
            extensionRepoId = record.extensionRepoId,
            extensionId = record.extensionId,
            sourceId = record.sourceId,
            animeUrl = record.animeUrl,
            displaySource = record.displaySource,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
        )
        Logger.i(TAG) { "Inserted main_entry: mainId=${record.mainId}, title='${record.title}'" }
    }

    fun updateMainEntrySources(
        mainId: String,
        dataSourceId: Long?,
        systemId: Long?,
        extensionRepoId: Long?,
        extensionId: Long?,
        sourceId: Long?,
        animeUrl: String?,
        contentId: String,
    ) {
        contentQueries.updateMainEntrySources(
            dataSourceId = dataSourceId,
            systemId = systemId,
            extensionRepoId = extensionRepoId,
            extensionId = extensionId,
            sourceId = sourceId,
            animeUrl = animeUrl,
            contentId = contentId,
            updatedAt = System.currentTimeMillis(),
            mainId = mainId,
        )
        Logger.i(TAG) { "Updated main_entry sources: mainId=$mainId, new contentId='$contentId'" }
    }

    fun updateMainEntryContentId(mainId: String, contentId: String) {
        contentQueries.updateMainEntryContentId(
            contentId = contentId,
            updatedAt = System.currentTimeMillis(),
            mainId = mainId,
        )
        Logger.d(TAG) { "Updated main_entry contentId: mainId=$mainId, contentId=$contentId" }
    }

    fun updateMainEntryTitle(mainId: String, title: String) {
        contentQueries.updateMainEntryTitle(
            title = title,
            updatedAt = System.currentTimeMillis(),
            mainId = mainId,
        )
        Logger.d(TAG) { "Updated main_entry title: mainId=$mainId, title=$title" }
    }

    fun updateMainEntryDisplaySource(mainId: String, displaySource: String) {
        contentQueries.updateMainEntryDisplaySource(
            displaySource = displaySource,
            updatedAt = System.currentTimeMillis(),
            mainId = mainId,
        )
        Logger.d(TAG) { "Updated displaySource: mainId=$mainId, displaySource=$displaySource" }
    }

    fun deleteMainEntry(mainId: String) {
        contentQueries.deleteMainEntry(mainId)
        Logger.i(TAG) { "Deleted main_entry: mainId=$mainId" }
    }

    // ── content_details ────────────────────────────────────────────────────
    //
    // D-198: merged from anilist_detail + extension_detail + other_source_detail +
    // anime_metadata_cache. One row per content (1:1 with main_entry). Two axes:
    //   data_* — data-source metadata (AniList now; Kitsu/MAL/TMDB future)
    //   ext_*  — extension metadata (Aniyomi now; CloudStream/Sora/MangaYomi future)
    // Each axis is independently switchable + unlinkable.

    fun getContentDetails(mainId: String): ContentDetails? {
        return contentQueries.getContentDetails(mainId).executeAsOneOrNull()?.let {
            ContentDetails(
                mainId = it.main_id,
                dataSourceType = it.data_source_type,
                dataSourceRefId = it.data_source_ref_id,
                dataScore = it.data_score,
                dataEpisodes = it.data_episodes,
                dataSeason = it.data_season,
                dataSeasonYear = it.data_season_year,
                dataStatus = it.data_status,
                dataGenres = it.data_genres,
                dataSynopsis = it.data_synopsis,
                dataCoverUrl = it.data_cover_url,
                dataBannerUrl = it.data_banner_url,
                dataExtraJson = it.data_extra_json,
                dataUpdatedAt = it.data_updated_at,
                extensionType = it.extension_type,
                extensionId = it.extension_id,
                sourceId = it.source_id,
                animeUrl = it.anime_url,
                extDescription = it.ext_description,
                extGenres = it.ext_genres,
                extStatus = it.ext_status,
                extAuthor = it.ext_author,
                extArtist = it.ext_artist,
                extThumbnailUrl = it.ext_thumbnail_url,
                extExtraJson = it.ext_extra_json,
                extUpdatedAt = it.ext_updated_at,
                coverAccentArgb = it.cover_accent_argb,
            )
        }
    }

    /** Full-row INSERT OR REPLACE — caller must populate all 26 columns. */
    fun upsertContentDetails(detail: ContentDetails) {
        contentQueries.upsertContentDetails(
            mainId = detail.mainId,
            dataSourceType = detail.dataSourceType,
            dataSourceRefId = detail.dataSourceRefId,
            dataScore = detail.dataScore,
            dataEpisodes = detail.dataEpisodes,
            dataSeason = detail.dataSeason,
            dataSeasonYear = detail.dataSeasonYear,
            dataStatus = detail.dataStatus,
            dataGenres = detail.dataGenres,
            dataSynopsis = detail.dataSynopsis,
            dataCoverUrl = detail.dataCoverUrl,
            dataBannerUrl = detail.dataBannerUrl,
            dataExtraJson = detail.dataExtraJson,
            dataUpdatedAt = detail.dataUpdatedAt,
            extensionType = detail.extensionType,
            extensionId = detail.extensionId,
            sourceId = detail.sourceId,
            animeUrl = detail.animeUrl,
            extDescription = detail.extDescription,
            extGenres = detail.extGenres,
            extStatus = detail.extStatus,
            extAuthor = detail.extAuthor,
            extArtist = detail.extArtist,
            extThumbnailUrl = detail.extThumbnailUrl,
            extExtraJson = detail.extExtraJson,
            extUpdatedAt = detail.extUpdatedAt,
            coverAccentArgb = detail.coverAccentArgb,
        )
    }

    /**
     * D-223: Update only the cover accent color (extracted from cover image via Palette API).
     * Used by [CoverColorExtractor] to persist the extracted color without touching other fields.
     */
    fun updateCoverAccent(mainId: String, argb: Long) {
        contentQueries.updateCoverAccentArgb(mainId = mainId, argb = argb)
    }

    /** Partial UPDATE of all data-source fields (extension fields untouched). */
    fun updateDataSourceAxis(detail: ContentDetails) {
        contentQueries.updateDataSourceAxis(
            dataSourceType = detail.dataSourceType,
            dataSourceRefId = detail.dataSourceRefId,
            dataScore = detail.dataScore,
            dataEpisodes = detail.dataEpisodes,
            dataSeason = detail.dataSeason,
            dataSeasonYear = detail.dataSeasonYear,
            dataStatus = detail.dataStatus,
            dataGenres = detail.dataGenres,
            dataSynopsis = detail.dataSynopsis,
            dataCoverUrl = detail.dataCoverUrl,
            dataBannerUrl = detail.dataBannerUrl,
            dataExtraJson = detail.dataExtraJson,
            dataUpdatedAt = detail.dataUpdatedAt,
            mainId = detail.mainId,
        )
    }

    /** Partial UPDATE of all extension fields (data-source fields untouched). */
    fun updateExtensionAxis(detail: ContentDetails) {
        contentQueries.updateExtensionAxis(
            extensionType = detail.extensionType,
            extensionId = detail.extensionId,
            sourceId = detail.sourceId,
            animeUrl = detail.animeUrl,
            extDescription = detail.extDescription,
            extGenres = detail.extGenres,
            extStatus = detail.extStatus,
            extAuthor = detail.extAuthor,
            extArtist = detail.extArtist,
            extThumbnailUrl = detail.extThumbnailUrl,
            extExtraJson = detail.extExtraJson,
            extUpdatedAt = detail.extUpdatedAt,
            mainId = detail.mainId,
        )
    }

    /** NULL all data-source fields (for unlink — fixes orphan-row bug). */
    fun clearDataSourceAxis(mainId: String) {
        contentQueries.clearDataSourceAxis(mainId)
    }

    /** NULL all extension fields (for unlink — fixes orphan-row bug). */
    fun clearExtensionAxis(mainId: String) {
        contentQueries.clearExtensionAxis(mainId)
    }

    /** Hard-delete the content_details row (CASCADE on main_entry handles this normally). */
    fun deleteContentDetails(mainId: String) {
        contentQueries.deleteContentDetails(mainId)
    }

    // ── Lookup: data_source ────────────────────────────────────────────────

    fun getDataSourceByName(name: String): DataSource? {
        return contentQueries.getDataSourceByName(name).executeAsOneOrNull()?.let {
            DataSource(it.id, it.name, it.display_name, it.type)
        }
    }

    // ── Lookup: system ─────────────────────────────────────────────────────

    fun getSystemByName(name: String): SystemInfo? {
        return contentQueries.getSystemByName(name).executeAsOneOrNull()?.let {
            SystemInfo(it.id, it.name, it.display_name, it.package_prefix)
        }
    }

    // ── Library ────────────────────────────────────────────────────────────

    fun getDefaultCategory(): LibraryCategory? {
        return libraryQueries.getDefaultCategory().executeAsOneOrNull()?.let {
            LibraryCategory(
                id = it.id,
                name = it.name,
                displayOrder = it.display_order,
                isPermanent = it.is_permanent == 1L,
                createdAt = it.created_at,
            )
        }
    }

    fun isInLibrary(mainId: String): Boolean {
        return libraryQueries.isInLibrary(mainId).executeAsOne()
    }

    fun addToDefaultCategory(mainId: String) {
        val defaultCat = getDefaultCategory() ?: run {
            Logger.e(TAG) { "Default category not found — did you call seedDefaults()?" }
            return
        }
        val now = System.currentTimeMillis()
        val order = libraryQueries.countDefaultCategoryItems().executeAsOne()
        libraryQueries.addToDefaultCategory(
            mainId = mainId,
            categoryId = defaultCat.id,
            displayOrder = order,
            addedAt = now,
        )
        Logger.i(TAG) { "Added to library (Default): mainId=$mainId" }
    }

    fun removeFromLibrary(mainId: String) {
        libraryQueries.removeFromLibrary(mainId)
        Logger.i(TAG) { "Removed from library: mainId=$mainId" }
    }

    fun getLibraryMainIds(): List<String> {
        return libraryQueries.getLibraryMainIds().executeAsList()
    }

    // ── D-285: batch reads for the Library's batch loader ──────────────────
    //
    // The Library used to issue getMainIdsByCategory/countItemsInCategory per
    // category + getMainEntryByMainId + getContentDetails per entry — ~5×N
    // queries for an N-item library (653 items → ~3,300 queries, 4-5s). These
    // three batch reads cover the entire load in 3 queries total; the caller
    // derives the category filter, counts, and entry list in memory.

    /** One row per library_item — (mainId, categoryId, addedAt), newest first. */
    fun getAllLibraryItems(): List<LibraryItemRecord> {
        return libraryQueries.getAllLibraryItems().executeAsList().map {
            LibraryItemRecord(
                mainId = it.main_id,
                categoryId = it.category_id,
                addedAt = it.added_at,
            )
        }
    }

    /** Every main_entry row that is IN the library, ordered by library add date (newest first). */
    fun getAllLibraryContentRecords(): List<ContentRecord> {
        return contentQueries.getAllLibraryMainEntries().executeAsList().map {
            ContentRecord(
                mainId = it.main_id,
                contentId = it.content_id,
                title = it.title,
                contentType = it.content_type,
                contentFormat = it.content_format,
                dataSourceId = it.data_source_id,
                systemId = it.system_id,
                extensionRepoId = it.extension_repo_id,
                extensionId = it.extension_id,
                sourceId = it.source_id,
                animeUrl = it.anime_url,
                displaySource = it.display_source,
                createdAt = it.created_at,
                updatedAt = it.updated_at,
            )
        }
    }

    /** Every content_details row, keyed by mainId (batch companion to [getContentDetails]). */
    fun getAllContentDetailsMap(): Map<String, ContentDetails> {
        return contentQueries.getAllContentDetails().executeAsList().associate {
            val details = ContentDetails(
                mainId = it.main_id,
                dataSourceType = it.data_source_type,
                dataSourceRefId = it.data_source_ref_id,
                dataScore = it.data_score,
                dataEpisodes = it.data_episodes,
                dataSeason = it.data_season,
                dataSeasonYear = it.data_season_year,
                dataStatus = it.data_status,
                dataGenres = it.data_genres,
                dataSynopsis = it.data_synopsis,
                dataCoverUrl = it.data_cover_url,
                dataBannerUrl = it.data_banner_url,
                dataExtraJson = it.data_extra_json,
                dataUpdatedAt = it.data_updated_at,
                extensionType = it.extension_type,
                extensionId = it.extension_id,
                sourceId = it.source_id,
                animeUrl = it.anime_url,
                extDescription = it.ext_description,
                extGenres = it.ext_genres,
                extStatus = it.ext_status,
                extAuthor = it.ext_author,
                extArtist = it.ext_artist,
                extThumbnailUrl = it.ext_thumbnail_url,
                extExtraJson = it.ext_extra_json,
                extUpdatedAt = it.ext_updated_at,
                coverAccentArgb = it.cover_accent_argb,
            )
            details.mainId to details
        }
    }

    // ── Category management (D-138) ────────────────────────────────────────

    /**
     * Get all library categories, ordered by display_order.
     */
    fun getAllCategories(): List<LibraryCategory> {
        return libraryQueries.getAllCategories().executeAsList().map {
            LibraryCategory(
                id = it.id,
                name = it.name,
                displayOrder = it.display_order,
                isPermanent = it.is_permanent == 1L,
                createdAt = it.created_at,
            )
        }
    }

    /**
     * Create a new user category. Returns the new category's ID.
     * User-created categories are NOT permanent (can be deleted/renamed).
     */
    fun createCategory(name: String): Long {
        val now = System.currentTimeMillis()
        val order = getAllCategories().size
        libraryQueries.insertCategory(
            name = name,
            displayOrder = order.toLong(),
            isPermanent = 0,
            createdAt = now,
        )
        val newCat = libraryQueries.getCategoryByName(name).executeAsOneOrNull()
        Logger.i(TAG) { "Created category: '$name' (id=${newCat?.id})" }
        return newCat?.id ?: -1L
    }

    /**
     * Delete a category. Only non-permanent categories can be deleted.
     * Library items in this category are also deleted (CASCADE).
     */
    fun deleteCategory(categoryId: Long) {
        libraryQueries.deleteCategory(categoryId)
        Logger.i(TAG) { "Deleted category: id=$categoryId" }
    }

    /**
     * Rename a category. Only non-permanent categories can be renamed.
     */
    fun renameCategory(categoryId: Long, newName: String) {
        libraryQueries.renameCategory(newName, categoryId)
        Logger.i(TAG) { "Renamed category: id=$categoryId → '$newName'" }
    }

    /**
     * Add a content to a specific category.
     */
    fun addToCategory(mainId: String, categoryId: Long) {
        val now = System.currentTimeMillis()
        val order = libraryQueries.countItemsInCategory(categoryId).executeAsOne()
        libraryQueries.addToCategory(
            mainId = mainId,
            categoryId = categoryId,
            displayOrder = order,
            addedAt = now,
        )
        Logger.i(TAG) { "Added to category: mainId=$mainId, categoryId=$categoryId" }
    }

    /**
     * Remove a content from a specific category.
     */
    fun removeFromCategory(mainId: String, categoryId: Long) {
        libraryQueries.removeFromCategory(mainId, categoryId)
        Logger.i(TAG) { "Removed from category: mainId=$mainId, categoryId=$categoryId" }
    }

    /**
     * Check if a content is in a specific category.
     */
    fun isInCategory(mainId: String, categoryId: Long): Boolean {
        return libraryQueries.isInCategory(mainId, categoryId).executeAsOne()
    }

    /**
     * Get all categories a content is in (for the category picker popup).
     */
    fun getCategoriesForContent(mainId: String): List<LibraryCategory> {
        return libraryQueries.getCategoriesForContent(mainId).executeAsList().map {
            LibraryCategory(
                id = it.id,
                name = it.name,
                displayOrder = it.display_order,
                isPermanent = it.is_permanent == 1L,
                createdAt = it.created_at,
            )
        }
    }

    /**
     * Get the main_ids of items in a specific category.
     */
    fun getMainIdsByCategory(categoryId: Long): List<String> {
        return libraryQueries.getMainIdsByCategory(categoryId).executeAsList()
    }

    /**
     * Count items in a specific category.
     */
    fun countItemsInCategory(categoryId: Long): Int {
        return libraryQueries.countItemsInCategory(categoryId).executeAsOne().toInt()
    }
}
