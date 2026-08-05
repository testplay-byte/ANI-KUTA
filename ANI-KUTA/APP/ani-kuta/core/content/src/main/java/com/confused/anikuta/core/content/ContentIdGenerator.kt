package com.confused.anikuta.core.content

/**
 * The structured Content ID — a deterministic string generated from source fields.
 *
 * Format (6 sections, colon-delimited):
 * ```
 * {dataSource}:{system}:{repoUrl|none}:{extensionPkg|none}:{sourceId|none}:{animeUrl|none}
 * ```
 *
 * D-135: Uses the FULL repo URL (not the DB ID) per user request — the URL is
 * essential for backup/restore + retrieving more extension IDs from the repo.
 *
 * The Content ID changes when the user switches sources. It's used for:
 * - Quick identification (debugging, logging).
 * - Overlapping detection (two records with the same Content ID = duplicates).
 *
 * NOT shown in the UI — internal tracking only.
 *
 * CORE_RULES §7: Pure logic — no I/O, no UI.
 */
object ContentIdGenerator {

    /**
     * Generate the structured Content ID string.
     *
     * @param dataSource The metadata/data source name (`anilist`, `tmdb`, etc.) or null.
     * @param system The extension system name (`aniyomi`, `cloudstream`, etc.) or null.
     * @param repoUrl The extension repository URL (full, ends with index.min.json) or null.
     * @param extensionPkg The extension package name (`com.aniyomi.anikoto`) or null.
     * @param sourceId The internal source ID within the extension (e.g. 69023) or null.
     * @param animeUrl The content's URL on the source, or null.
     */
    fun generate(
        dataSource: String?,
        system: String?,
        repoUrl: String?,
        extensionPkg: String?,
        sourceId: Long?,
        animeUrl: String?,
    ): String {
        return listOf(
            dataSource ?: "none",
            system ?: "none",
            repoUrl ?: "none",
            extensionPkg ?: "none",
            sourceId?.toString() ?: "none",
            animeUrl ?: "none",
        ).joinToString(":")
    }
}
