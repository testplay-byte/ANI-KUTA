package com.confused.anikuta.core.content

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.database.AnikutaDatabase
import java.util.UUID

/**
 * Resolves external IDs (anilistId, sourceId+animeUrl) to the internal mainId.
 *
 * - If a main_entry record exists for the given ID → return its mainId.
 * - If not → create a new main_entry + content_details → return the new mainId.
 *
 * The mainId is a stable UUID — assigned once, never changes.
 * The contentId is regenerated whenever sources change (via [updateMainEntrySources]).
 *
 * D-198: migrated to the unified `content_details` table. AniList metadata is
 * written to the data-source axis (`data_*`), extension metadata to the extension
 * axis (`ext_*`). Each axis is independently switchable + unlinkable.
 *
 * CORE_RULES §7: Backend logic — no UI.
 * CORE_RULES §20: Logged with tag "Anikuta:Core:Content:Resolver".
 */
class ContentResolver(
    private val repo: ContentRepository,
    private val database: AnikutaDatabase,
) {

    companion object {
        private const val TAG = "Anikuta:Core:Content:Resolver"
    }

    /**
     * Resolve an AniList entry to a mainId.
     * - If a main_entry record exists for this anilistId → return it.
     * - If not → create a new main_entry + content_details → return new mainId.
     *
     * @param anilistId The AniList anime ID.
     * @param title The anime title (for the main_entry record).
     * @param anilistDetail Optional AniList metadata (mapped to the data-source axis
     *   of content_details). Pass null to skip the detail write.
     * @return The mainId (stable UUID).
     */
    fun resolveOrCreateForAniList(
        anilistId: Int,
        title: String,
        anilistDetail: ContentDetails? = null,
    ): String {
        // Check if content already exists for this anilistId.
        val existing = repo.getMainEntryByAniListId(anilistId)
        if (existing != null) {
            Logger.d(TAG) { "AniList $anilistId → existing mainId=${existing.mainId}" }
            // Update the data-source axis if metadata was provided.
            if (anilistDetail != null) {
                updateDataSourceAxisInTransaction(existing.mainId, anilistDetail)
            }
            return existing.mainId
        }

        // D-240: Fallback — try contentId lookup (handles reinstall case where
        // the scanner restored main_entry from data.json but the anilistId axis
        // wasn't fully restored in content_details).
        val fallbackContentId = ContentIdGenerator.generate(
            dataSource = "anilist",
            system = null,
            repoUrl = null,
            extensionPkg = null,
            sourceId = null,
            animeUrl = null,
        )
        val existingByContentId = repo.getMainEntryByContentId(fallbackContentId)
        if (existingByContentId != null) {
            Logger.d(TAG) { "AniList $anilistId → found via contentId fallback, mainId=${existingByContentId.mainId}" }
            if (anilistDetail != null) {
                updateDataSourceAxisInTransaction(existingByContentId.mainId, anilistDetail)
            }
            return existingByContentId.mainId
        }

        // Create new main_entry record.
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
            dataSourceId = dataSource?.id,
            systemId = null,
            extensionRepoId = null,
            extensionId = null,
            sourceId = null,
            animeUrl = null,
            // D-198: 'anilist' values migrated to axis-level 'data_source'.
            displaySource = "data_source",
            createdAt = now,
            updatedAt = now,
        )
        repo.insertMainEntry(record)

        // Store the AniList detail on the data-source axis of content_details.
        if (anilistDetail != null) {
            upsertContentDetailsForAniList(mainId, anilistDetail, now)
        }

        Logger.i(TAG) {
            "Created content for AniList $anilistId: mainId=$mainId, contentId='$contentId'"
        }
        return mainId
    }

    /**
     * Resolve an extension entry to a mainId.
     * - If a main_entry record exists for this (extensionId, animeUrl) → return it.
     * - If not → create a new main_entry → return new mainId.
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
        val existing = repo.getMainEntryByExtension(extensionId, animeUrl)
        if (existing != null) {
            Logger.d(TAG) { "Extension $extensionId/$animeUrl → existing mainId=${existing.mainId}" }
            return existing.mainId
        }

        // D-240: Fallback — try contentId lookup (handles reinstall case where
        // the scanner restored main_entry from data.json but the extension axis
        // in content_details wasn't fully restored).
        val fallbackContentId = ContentIdGenerator.generate(
            dataSource = null,
            system = systemName,
            repoUrl = repoUrl,
            extensionPkg = extensionPkg,
            sourceId = sourceId,
            animeUrl = animeUrl,
        )
        val existingByContentId = repo.getMainEntryByContentId(fallbackContentId)
        if (existingByContentId != null) {
            Logger.d(TAG) { "Extension $extensionId/$animeUrl → found via contentId fallback, mainId=${existingByContentId.mainId}" }
            return existingByContentId.mainId
        }

        // Create new main_entry record.
        val mainId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val system = repo.getSystemByName(systemName)
        // D-192: content_ext_repo table dropped (dead code). extensionRepoId is always null
        // for now — the column is kept in the main_entry table for future repo-tracking.
        val extensionRepoId: Long? = null

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
        repo.insertMainEntry(record)

        // D-206 FIX: ensure the content_details row exists immediately so the
        // subsequent `updateExtensionAxis` UPDATE (called by DetailsViewModel.
        // resolveContentForExtension right after this returns) actually matches a
        // row and persists ext_thumbnail_url / ext_description / etc.
        //
        // WITHOUT this, the first open from Search would leave ext_thumbnail_url
        // un-persisted (UPDATE matches 0 rows → silent no-op). The cover image
        // would then appear null when the entry is later opened from the Library
        // (LibraryViewModel reads details.extThumbnailUrl → null → coverUrl=null).
        //
        // Mirrors the same guard already used by linkExtensionToExisting (line 312)
        // + updateDataSourceAxisInTransaction (line 457) — the D-198 comments there
        // say "ensure the content_details row exists so the UPDATE doesn't no-op".
        // resolveOrCreateForAniList (line 92-94) also inserts the row via
        // upsertContentDetailsForAniList. This method was the ONLY resolver that
        // didn't, leaving extension-only entries with no content_details row.
        repo.upsertContentDetails(
            ContentDetails(
                mainId = mainId,
                // All fields default to NULL — the ext-axis UPDATE that follows
                // (from DetailsViewModel.resolveContentForExtension) fills the rest.
            ),
        )

        Logger.i(TAG) {
            "Created content for extension: mainId=$mainId, contentId='$contentId', title='$title'"
        }
        return mainId
    }

    /**
     * Link an AniList ID to an existing main_entry (when auto-link matches).
     * Stores the data-source axis on content_details + updates main_entry.dataSourceId.
     * The mainId stays the same.
     *
     * D-198: now calls [updateDataSourceAxis] (partial UPDATE of the data-source
     * fields). Switch flow + content_id regeneration are wrapped in a single
     * transaction (per Review v2-2A Check 6).
     */
    fun linkAniList(
        mainId: String,
        anilistId: Int,
        anilistDetail: ContentDetails? = null,
    ) {
        Logger.i(TAG) { "Linking AniList $anilistId to mainId=$mainId" }

        val now = System.currentTimeMillis()
        val existing = repo.getMainEntryByMainId(mainId) ?: return
        val dataSource = repo.getDataSourceByName("anilist")
        val existingDetails = repo.getContentDetails(mainId)

        // Update the data-source axis (partial UPDATE — ext_* untouched).
        // Wrap the detail-table UPDATE + main_entry.content_id UPDATE in a
        // single transaction (per Review v2-2A Check 6).
        database.transaction {
            // D-198: ensure the content_details row exists (so updateDataSourceAxis doesn't no-op).
            if (existingDetails == null) {
                repo.upsertContentDetails(
                    ContentDetails(
                        mainId = mainId,
                        // All fields default to NULL — the data-axis UPDATE below fills the rest.
                    ),
                )
            }
            if (anilistDetail != null) {
                repo.updateDataSourceAxis(anilistDetail.copy(mainId = mainId, dataUpdatedAt = now))
            }
            val newContentId = ContentIdGenerator.generate(
                dataSource = "anilist",
                system = existing.systemId?.let { "aniyomi" }, // Simplified — could look up the system name.
                repoUrl = null, // Could look up the repo URL from the extension.
                extensionPkg = null, // Could look up from the extension.
                sourceId = existing.sourceId,
                animeUrl = existing.animeUrl,
            )
            repo.updateMainEntrySources(
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
    }

    /**
     * Link an extension entry to an EXISTING main_entry (D-137).
     *
     * Used when the user opens an extension anime that was previously linked
     * to an AniList entry (via auto-link cache). Instead of creating a new
     * main_entry, we update the existing one with the extension info + store
     * the extension metadata on the extension axis of content_details.
     *
     * The mainId stays the same — this is the KEY to cross-source deduplication.
     *
     * D-198: now ALSO calls [updateExtensionAxis] to store the extension metadata.
     *
     * @param mainId The existing content's mainId.
     * @param extensionId The extension's DB ID.
     * @param sourceId The internal source ID within the extension.
     * @param animeUrl The content's URL on the extension.
     * @param title The anime title (updates the main_entry if different).
     */
    fun linkExtensionToExisting(
        mainId: String,
        extensionId: Long,
        sourceId: Long,
        animeUrl: String,
        title: String,
    ) {
        linkExtensionToExisting(
            mainId = mainId,
            extensionId = extensionId,
            sourceId = sourceId,
            animeUrl = animeUrl,
            title = title,
            extensionDetail = null,
        )
    }

    /**
     * Link an extension entry to an EXISTING main_entry, with optional extension
     * metadata to persist on the extension axis of content_details.
     *
     * D-198: also stores the extension metadata via [updateExtensionAxis].
     * Switch flow + content_id regeneration are wrapped in a single transaction
     * (per Review v2-2A Check 6).
     */
    fun linkExtensionToExisting(
        mainId: String,
        extensionId: Long,
        sourceId: Long,
        animeUrl: String,
        title: String,
        extensionDetail: ContentDetails?,
    ) {
        Logger.i(TAG) { "Linking extension to existing content: mainId=$mainId, extensionId=$extensionId" }
        val existing = repo.getMainEntryByMainId(mainId) ?: return
        val system = repo.getSystemByName("aniyomi")
        val now = System.currentTimeMillis()
        val existingDetails = repo.getContentDetails(mainId)

        database.transaction {
            // Regenerate contentId with the extension info.
            val newContentId = ContentIdGenerator.generate(
                dataSource = if (existing.dataSourceId != null) "anilist" else null,
                system = "aniyomi",
                repoUrl = null,
                extensionPkg = null,
                sourceId = sourceId,
                animeUrl = animeUrl,
            )
            repo.updateMainEntrySources(
                mainId = mainId,
                dataSourceId = existing.dataSourceId,
                systemId = existing.systemId ?: system?.id,
                extensionRepoId = existing.extensionRepoId,
                extensionId = extensionId,
                sourceId = sourceId,
                animeUrl = animeUrl,
                contentId = newContentId,
            )
            // Ensure the content_details row exists (so updateExtensionAxis doesn't no-op).
            // D-198: when the row was created by resolveOrCreateForAniList + an AniList
            // upsert, the row already exists. When created by resolveOrCreateForExtension
            // with no extensionDetail, the row is missing — insert a fresh empty row.
            if (existingDetails == null) {
                repo.upsertContentDetails(
                    ContentDetails(
                        mainId = mainId,
                        // All fields default to NULL — the ext-axis UPDATE below fills the rest.
                    ),
                )
            }
            // Store the extension metadata on the ext_* axis (partial UPDATE —
            // data_* fields untouched). The denormalized extension_id + source_id +
            // anime_url columns on main_entry are kept in sync via updateMainEntrySources.
            if (extensionDetail != null) {
                repo.updateExtensionAxis(
                    extensionDetail.copy(
                        mainId = mainId,
                        extensionType = extensionDetail.extensionType ?: "aniyomi",
                        extensionId = extensionDetail.extensionId ?: extensionId.toString(),
                        sourceId = extensionDetail.sourceId ?: sourceId,
                        animeUrl = extensionDetail.animeUrl ?: animeUrl,
                        extUpdatedAt = extensionDetail.extUpdatedAt ?: now,
                    ),
                )
            }
        }
    }

    /**
     * Unlink an AniList ID from a content (when the user unlinks).
     * NULLs the data-source axis on content_details + clears the dataSourceId +
     * regenerates contentId. The mainId stays the same.
     *
     * D-198: now calls [clearDataSourceAxis] (NULL UPDATE — fixes orphan-row bug).
     * Unlink flow + content_id regeneration + display_source update are wrapped
     * in a single transaction (per Review v2-2A Check 6).
     */
    fun unlinkAniList(mainId: String) {
        Logger.i(TAG) { "Unlinking AniList from mainId=$mainId" }

        val existing = repo.getMainEntryByMainId(mainId) ?: return

        database.transaction {
            repo.clearDataSourceAxis(mainId)

            // If the unlinked axis was the preferred display, fall back to extension.
            if (existing.displaySource == "data_source") {
                repo.updateMainEntryDisplaySource(mainId, "extension")
            }

            val newContentId = ContentIdGenerator.generate(
                dataSource = null,
                system = existing.systemId?.let { "aniyomi" },
                repoUrl = null,
                extensionPkg = null,
                sourceId = existing.sourceId,
                animeUrl = existing.animeUrl,
            )
            repo.updateMainEntrySources(
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
    }

    /**
     * D-198 NEW: Unlink an extension entry from a content (mirrors [unlinkAniList]).
     * NULLs the extension axis on content_details + clears the denormalized
     * extensionId/sourceId/animeUrl on main_entry + regenerates contentId.
     * The mainId stays the same.
     *
     * Per Review v2-2A Check 6: unlink flow + content_id regeneration +
     * display_source update are wrapped in a single transaction.
     */
    fun unlinkExtension(mainId: String) {
        Logger.i(TAG) { "Unlinking extension from mainId=$mainId" }

        val existing = repo.getMainEntryByMainId(mainId) ?: return

        database.transaction {
            repo.clearExtensionAxis(mainId)

            // If the unlinked axis was the preferred display, fall back to data-source.
            if (existing.displaySource == "extension") {
                repo.updateMainEntryDisplaySource(mainId, "data_source")
            }

            val newContentId = ContentIdGenerator.generate(
                dataSource = if (existing.dataSourceId != null) "anilist" else null,
                system = null,
                repoUrl = null,
                extensionPkg = null,
                sourceId = null,
                animeUrl = null,
            )
            repo.updateMainEntrySources(
                mainId = mainId,
                dataSourceId = existing.dataSourceId,
                systemId = existing.systemId,
                extensionRepoId = existing.extensionRepoId,
                extensionId = null,
                sourceId = null,
                animeUrl = null,
                contentId = newContentId,
            )
        }
    }

    /**
     * Update the display source (which detail axis's data is shown).
     * The mainId + contentId stay the same.
     */
    fun updateDisplaySource(mainId: String, displaySource: String) {
        repo.updateMainEntryDisplaySource(mainId, displaySource)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Updates the data-source axis on content_details for an existing mainId,
     * wrapped in a single transaction with the content_id regeneration on
     * main_entry. Used by [linkAniList] + the existing-content path of
     * [resolveOrCreateForAniList].
     *
     * D-198: if the content_details row doesn't exist yet (e.g. the main_entry was
     * created by [resolveOrCreateForExtension] without an extension axis), insert
     * a fresh row first via [ContentRepository.upsertContentDetails] (NULL ext_*
     * fields) so the subsequent [ContentRepository.updateDataSourceAxis] UPDATE
     * doesn't no-op.
     */
    private fun updateDataSourceAxisInTransaction(
        mainId: String,
        anilistDetail: ContentDetails,
    ) {
        val now = System.currentTimeMillis()
        val existing = repo.getMainEntryByMainId(mainId) ?: return
        val dataSource = repo.getDataSourceByName("anilist")
        val existingDetails = repo.getContentDetails(mainId)

        database.transaction {
            // Ensure the content_details row exists (so updateDataSourceAxis doesn't no-op).
            if (existingDetails == null) {
                repo.upsertContentDetails(
                    ContentDetails(
                        mainId = mainId,
                        // All fields default to NULL — the data-axis UPDATE below fills the rest.
                    ),
                )
            }
            repo.updateDataSourceAxis(anilistDetail.copy(mainId = mainId, dataUpdatedAt = now))
            val newContentId = ContentIdGenerator.generate(
                dataSource = "anilist",
                system = existing.systemId?.let { "aniyomi" },
                repoUrl = null,
                extensionPkg = null,
                sourceId = existing.sourceId,
                animeUrl = existing.animeUrl,
            )
            repo.updateMainEntrySources(
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
    }

    /**
     * Writes the data-source axis of content_details from the AniList metadata.
     * Used by [resolveOrCreateForAniList] when creating a brand-new main_entry.
     * The extension axis is left NULL (it's a fresh AniList-only row).
     *
     * D-198: uses [ContentRepository.upsertContentDetails] (full INSERT OR REPLACE)
     * since the content_details row is brand-new — there's no ext_* axis to preserve.
     */
    private fun upsertContentDetailsForAniList(
        mainId: String,
        anilistDetail: ContentDetails,
        now: Long,
    ) {
        repo.upsertContentDetails(
            anilistDetail.copy(
                mainId = mainId,
                dataSourceType = anilistDetail.dataSourceType ?: "anilist",
                dataUpdatedAt = anilistDetail.dataUpdatedAt ?: now,
                // Extension axis is fresh — leave all ext_* fields as the caller passed
                // (typically NULL for a new AniList-only row).
            ),
        )
    }
}
