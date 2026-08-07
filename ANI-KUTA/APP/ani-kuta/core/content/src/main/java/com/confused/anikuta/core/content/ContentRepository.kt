package com.confused.anikuta.core.content

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase

/**
 * Repository for the content identity system.
 *
 * Handles CRUD on:
 * - `content` (main table)
 * - `anilist_detail`, `extension_detail`, `other_source_detail` (detail tables)
 * - `data_source`, `system`, `extension_repo`, `extension` (lookup tables)
 * - `library_category`, `library_item` (library tables)
 *
 * Seeds the lookup tables + Default library category on first launch.
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

    // ── Content CRUD ───────────────────────────────────────────────────────

    fun getContentByMainId(mainId: String): ContentRecord? {
        return contentQueries.getContentByMainId(mainId).executeAsOneOrNull()?.let {
            ContentRecord(
                mainId = it.main_id,
                contentId = it.content_id,
                title = it.title,
                contentType = it.content_type,
                contentFormat = it.content_format,
                description = it.description,
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

    fun getContentByAniListId(anilistId: Int): ContentRecord? {
        return contentQueries.getContentByAniListId(anilistId.toLong()).executeAsOneOrNull()?.let {
            ContentRecord(
                mainId = it.main_id,
                contentId = it.content_id,
                title = it.title,
                contentType = it.content_type,
                contentFormat = it.content_format,
                description = it.description,
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

    fun getContentByExtension(extensionId: Long, animeUrl: String): ContentRecord? {
        return contentQueries.getContentByExtension(extensionId, animeUrl).executeAsOneOrNull()?.let {
            ContentRecord(
                mainId = it.main_id,
                contentId = it.content_id,
                title = it.title,
                contentType = it.content_type,
                contentFormat = it.content_format,
                description = it.description,
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

    fun insertContent(record: ContentRecord) {
        contentQueries.insertContent(
            mainId = record.mainId,
            contentId = record.contentId,
            title = record.title,
            contentType = record.contentType,
            contentFormat = record.contentFormat,
            description = record.description,
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
        Logger.i(TAG) { "Inserted content: mainId=${record.mainId}, title='${record.title}'" }
    }

    fun updateContentSources(
        mainId: String,
        dataSourceId: Long?,
        systemId: Long?,
        extensionRepoId: Long?,
        extensionId: Long?,
        sourceId: Long?,
        animeUrl: String?,
        contentId: String,
    ) {
        contentQueries.updateContentSources(
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
        Logger.i(TAG) { "Updated content sources: mainId=$mainId, new contentId='$contentId'" }
    }

    fun updateDisplaySource(mainId: String, displaySource: String) {
        contentQueries.updateContentDisplaySource(
            displaySource = displaySource,
            updatedAt = System.currentTimeMillis(),
            mainId = mainId,
        )
        Logger.d(TAG) { "Updated displaySource: mainId=$mainId, displaySource=$displaySource" }
    }

    // ── AniList detail ─────────────────────────────────────────────────────

    fun getAniListDetail(mainId: String): AniListDetail? {
        return contentQueries.getAniListDetail(mainId).executeAsOneOrNull()?.let {
            AniListDetail(
                mainId = it.main_id,
                anilistId = it.anilist_id.toInt(),
                idMal = it.id_mal?.toInt(),
                score = it.score?.toInt(),
                episodes = it.episodes?.toInt(),
                season = it.season,
                seasonYear = it.season_year?.toInt(),
                status = it.status,
                genres = it.genres,
                synopsis = it.synopsis,
                coverUrl = it.cover_url,
                bannerUrl = it.banner_url,
                updatedAt = it.updated_at,
            )
        }
    }

    fun upsertAniListDetail(detail: AniListDetail) {
        contentQueries.upsertAniListDetail(
            mainId = detail.mainId,
            anilistId = detail.anilistId.toLong(),
            idMal = detail.idMal?.toLong(),
            score = detail.score?.toLong(),
            episodes = detail.episodes?.toLong(),
            season = detail.season,
            seasonYear = detail.seasonYear?.toLong(),
            status = detail.status,
            genres = detail.genres,
            synopsis = detail.synopsis,
            coverUrl = detail.coverUrl,
            bannerUrl = detail.bannerUrl,
            updatedAt = detail.updatedAt,
        )
    }

    fun deleteAniListDetail(mainId: String) {
        contentQueries.deleteAniListDetail(mainId)
    }

    // ── Extension detail ───────────────────────────────────────────────────

    fun getExtensionDetail(mainId: String): ExtensionDetail? {
        return contentQueries.getExtensionDetail(mainId).executeAsOneOrNull()?.let {
            ExtensionDetail(
                mainId = it.main_id,
                extensionId = it.extension_id,
                sourceId = it.source_id,
                animeUrl = it.anime_url,
                description = it.description,
                genres = it.genres,
                status = it.status,
                author = it.author,
                artist = it.artist,
                thumbnailUrl = it.thumbnail_url,
                updatedAt = it.updated_at,
            )
        }
    }

    fun upsertExtensionDetail(detail: ExtensionDetail) {
        contentQueries.upsertExtensionDetail(
            mainId = detail.mainId,
            extensionId = detail.extensionId,
            sourceId = detail.sourceId,
            animeUrl = detail.animeUrl,
            description = detail.description,
            genres = detail.genres,
            status = detail.status,
            author = detail.author,
            artist = detail.artist,
            thumbnailUrl = detail.thumbnailUrl,
            updatedAt = detail.updatedAt,
        )
    }

    fun deleteExtensionDetail(mainId: String) {
        contentQueries.deleteExtensionDetail(mainId)
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

    // ── Lookup: extension_repo ─────────────────────────────────────────────

    fun getExtensionRepoByUrl(url: String): ExtensionRepo? {
        return contentQueries.getContentExtRepoByUrl(url).executeAsOneOrNull()?.let {
            ExtensionRepo(it.id, it.system_id, it.url, it.display_name)
        }
    }

    fun insertExtensionRepo(systemId: Long, url: String, displayName: String?): Long {
        val now = System.currentTimeMillis()
        contentQueries.insertContentExtRepo(systemId, url, displayName, now)
        return contentQueries.getContentExtRepoByUrl(url).executeAsOneOrNull()?.id
            ?: throw IllegalStateException("Failed to insert extension_repo: $url")
    }

    // ── Lookup: extension ──────────────────────────────────────────────────

    fun getOrCreateExtension(
        systemId: Long,
        repoId: Long?,
        pkgName: String,
        name: String,
        sourceId: Long,
        versionName: String?,
        isNsfw: Boolean,
    ): Long {
        // Try to find existing
        val existing = contentQueries.getContentExtByPkgAndSource(pkgName, sourceId).executeAsOneOrNull()
        if (existing != null) return existing.id

        // Insert new
        val now = System.currentTimeMillis()
        contentQueries.insertContentExt(
            systemId = systemId,
            repoId = repoId,
            pkgName = pkgName,
            name = name,
            sourceId = sourceId,
            versionName = versionName,
            isNsfw = if (isNsfw) 1 else 0,
            createdAt = now,
        )
        return contentQueries.getContentExtByPkgAndSource(pkgName, sourceId).executeAsOneOrNull()?.id
            ?: throw IllegalStateException("Failed to insert extension: $pkgName/$sourceId")
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

    fun getDefaultCategoryCount(): Int {
        return libraryQueries.countDefaultCategoryItems().executeAsOne().toInt()
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
