package com.confused.anikuta.core.content

import com.confused.anikuta.core.common.Logger
import java.util.UUID

/**
 * Resolves external IDs (anilistId, sourceId+animeUrl) to the internal mainId.
 *
 * - If a content record exists for the given ID → return its mainId.
 * - If not → create a new content record + detail record → return the new mainId.
 *
 * The mainId is a stable UUID — assigned once, never changes.
 * The contentId is regenerated whenever sources change (via [updateContentSources]).
 *
 * CORE_RULES §7: Backend logic — no UI.
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Content:Resolver".
 */
class ContentResolver(
    private val repo: ContentRepository,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Content:Resolver"
    }

    /**
     * Resolve an AniList entry to a mainId.
     * - If a content record exists for this anilistId → return it.
     * - If not → create a new content record + anilist_detail → return new mainId.
     *
     * @param anilistId The AniList anime ID.
     * @param title The anime title (for the content record).
     * @param anilistDetail Optional AniList metadata to store in anilist_detail.
     * @return The mainId (stable UUID).
     */
    fun resolveOrCreateForAniList(
        anilistId: Int,
        title: String,
        anilistDetail: AniListDetail? = null,
    ): String {
        // Check if content already exists for this anilistId.
        val existing = repo.getContentByAniListId(anilistId)
        if (existing != null) {
            Logger.d(TAG) { "AniList $anilistId → existing mainId=${existing.mainId}" }
            // Update the detail if provided.
            if (anilistDetail != null) {
                repo.upsertAniListDetail(anilistDetail.copy(mainId = existing.mainId))
            }
            return existing.mainId
        }

        // Create new content record.
        val mainId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val dataSource = repo.getDataSourceByName("anilist")
        val contentId = ContentIdGenerator.generate(
            dataSource = "anilist",
            system = null,
            repoUrl = null,
            extensionPkg = null,
            sourceId = null,
            animeUrl = null,
        )

        val record = ContentRecord(
            mainId = mainId,
            contentId = contentId,
            title = title,
            contentType = "anime",
            contentFormat = "video",
            description = null,
            dataSourceId = dataSource?.id,
            systemId = null,
            extensionRepoId = null,
            extensionId = null,
            sourceId = null,
            animeUrl = null,
            displaySource = "anilist",
            createdAt = now,
            updatedAt = now,
        )
        repo.insertContent(record)

        // Store the AniList detail.
        if (anilistDetail != null) {
            repo.upsertAniListDetail(anilistDetail.copy(mainId = mainId))
        }

        Logger.i(TAG) {
            "Created content for AniList $anilistId: mainId=$mainId, contentId='$contentId'"
        }
        return mainId
    }

    /**
     * Resolve an extension entry to a mainId.
     * - If a content record exists for this (extensionId, animeUrl) → return it.
     * - If not → create a new content record → return new mainId.
     *
     * @param extensionId The DB ID of the extension (from the `extension` lookup table).
     * @param sourceId The internal source ID within the extension.
     * @param animeUrl The content's URL on the source.
     * @param title The anime title.
     * @param systemName The system name (`aniyomi`, `cloudstream`, etc.).
     * @param repoUrl The extension repo URL (or null if sideloaded).
     * @param extensionPkg The extension package name.
     * @return The mainId (stable UUID).
     */
    fun resolveOrCreateForExtension(
        extensionId: Long,
        sourceId: Long,
        animeUrl: String,
        title: String,
        systemName: String = "aniyomi",
        repoUrl: String? = null,
        extensionPkg: String?,
    ): String {
        // Check if content already exists for this (extensionId, animeUrl).
        val existing = repo.getContentByExtension(extensionId, animeUrl)
        if (existing != null) {
            Logger.d(TAG) { "Extension $extensionId/$animeUrl → existing mainId=${existing.mainId}" }
            return existing.mainId
        }

        // Create new content record.
        val mainId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val system = repo.getSystemByName(systemName)
        val extensionRepoId = repoUrl?.let { url ->
            repo.getExtensionRepoByUrl(url)?.id ?: repo.insertExtensionRepo(
                systemId = system?.id ?: 1L,
                url = url,
                displayName = null,
            )
        }

        val contentId = ContentIdGenerator.generate(
            dataSource = null,
            system = systemName,
            repoUrl = repoUrl,
            extensionPkg = extensionPkg,
            sourceId = sourceId,
            animeUrl = animeUrl,
        )

        val record = ContentRecord(
            mainId = mainId,
            contentId = contentId,
            title = title,
            contentType = "anime",
            contentFormat = "video",
            description = null,
            dataSourceId = null,
            systemId = system?.id,
            extensionRepoId = extensionRepoId,
            extensionId = extensionId,
            sourceId = sourceId,
            animeUrl = animeUrl,
            displaySource = "extension",
            createdAt = now,
            updatedAt = now,
        )
        repo.insertContent(record)

        Logger.i(TAG) {
            "Created content for extension: mainId=$mainId, contentId='$contentId', title='$title'"
        }
        return mainId
    }

    /**
     * Link an AniList ID to an existing content (when auto-link matches).
     * Stores the anilist_detail + updates the content record's dataSourceId.
     * The mainId stays the same.
     */
    fun linkAniList(
        mainId: String,
        anilistId: Int,
        anilistDetail: AniListDetail? = null,
    ) {
        Logger.i(TAG) { "Linking AniList $anilistId to mainId=$mainId" }

        // Store the AniList detail.
        if (anilistDetail != null) {
            repo.upsertAniListDetail(anilistDetail.copy(mainId = mainId))
        }

        // Update the content record's dataSourceId + regenerate contentId.
        val existing = repo.getContentByMainId(mainId) ?: return
        val dataSource = repo.getDataSourceByName("anilist")
        val newContentId = ContentIdGenerator.generate(
            dataSource = "anilist",
            system = existing.systemId?.let { "aniyomi" }, // Simplified — could look up the system name.
            repoUrl = null, // Could look up the repo URL from the extension.
            extensionPkg = null, // Could look up from the extension.
            sourceId = existing.sourceId,
            animeUrl = existing.animeUrl,
        )
        repo.updateContentSources(
            mainId = mainId,
            dataSourceId = dataSource?.id,
            systemId = existing.systemId,
            extensionRepoId = existing.extensionRepoId,
            extensionId = existing.extensionId,
            sourceId = existing.sourceId,
            animeUrl = existing.animeUrl,
            contentId = newContentId,
        )
    }

    /**
     * Unlink an AniList ID from a content (when the user unlinks).
     * Deletes the anilist_detail + clears the dataSourceId + regenerates contentId.
     * The mainId stays the same.
     */
    fun unlinkAniList(mainId: String) {
        Logger.i(TAG) { "Unlinking AniList from mainId=$mainId" }
        repo.deleteAniListDetail(mainId)

        val existing = repo.getContentByMainId(mainId) ?: return
        val newContentId = ContentIdGenerator.generate(
            dataSource = null,
            system = existing.systemId?.let { "aniyomi" },
            repoUrl = null,
            extensionPkg = null,
            sourceId = existing.sourceId,
            animeUrl = existing.animeUrl,
        )
        repo.updateContentSources(
            mainId = mainId,
            dataSourceId = null,
            systemId = existing.systemId,
            extensionRepoId = existing.extensionRepoId,
            extensionId = existing.extensionId,
            sourceId = existing.sourceId,
            animeUrl = existing.animeUrl,
            contentId = newContentId,
        )
    }

    /**
     * Update the display source (which detail table's data is shown).
     * The mainId + contentId stay the same.
     */
    fun updateDisplaySource(mainId: String, displaySource: String) {
        repo.updateDisplaySource(mainId, displaySource)
    }
}
