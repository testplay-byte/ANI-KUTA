package com.confused.anikuta.data.cloudstream.repo

import com.lagradost.cloudstream3.plugins.SitePlugin

/**
 * Task 62 (round 22 — the plugin ↔ repository LINKAGE fix): the shared
 * identity resolver that decides whether an INSTALLED [CsPluginRecord] and an
 * ONLINE catalog [SitePlugin] are the SAME plugin.
 *
 * The round-22 device report: "the cloud stream extensions or plugins which I
 * installed manually were not linked with the repository. It was showing me
 * that the repository one and the one which I installed were two separate
 * ones … the only difference I saw was the internal name and such". The user
 * then explicitly asked that the app "properly recognize the cloud stream
 * extensions and their repositories even after the repository was added later
 * on".
 *
 * Root cause: every previous comparison keyed on EXACT `internalName` string
 * equality. A manually imported .cs3 derives its internalName from the shared
 * file's name or its manifest (sanitizeFilename(manifest.name)) — a string
 * that need not equal the repo's plugins.json `internalName` (case drift,
 * underscores, a chat-app "(1)" rename, manifest name ≠ repo stem). With no
 * other identity rung, the same plugin rendered as BOTH an installed row AND
 * an available row, and no code path ever back-filled the record's repoUrl.
 *
 * The resolver is a deliberately ORDERED ladder — strongest evidence first,
 * and the fuzzy rungs only fire when both sides actually carry the data:
 *
 *  1. exact `internalName` (the historical behavior);
 *  2. [CsPluginRecord.repoInternalName] — the repo's internalName captured at
 *     link time (the back-fill makes every FUTURE comparison exact);
 *  3. download URL equality (the repo's .cs3 URL is a strong identity — two
 *     different plugins never share it);
 *  4. fileHash equality ("sha256-<hex>" of the .cs3 bytes — identical files
 *     from the same source);
 *  5. NORMALIZED internalName equality (lowercase + digits/letters only —
 *     fixes "Movie_Box" vs "moviebox" / case drift);
 *  6. NORMALIZED display-name equality (record.name vs plugin.name — the
 *     last-resort rung for the manifest-name-derived imports whose internal
 *     names genuinely diverge from the repo's stems; display names are unique
 *     in practice, and an accidental link is recoverable (unlink = delete the
 *     record's repoUrl), while the duplicate-row bug the user reported is not).
 *
 * Pure + side-effect-free — unit-testable without Android.
 */
object CsPluginIdentity {

    /**
     * The normalization used by the fuzzy rungs: lowercase, keep only letters
     * and digits (drops `_`, `-`, `.`, spaces — the sanitizeFilename and
     * chat-rename artifacts). Empty when nothing survives.
     */
    fun normalize(name: String): String = buildString(name.length) {
        for (ch in name.lowercase()) {
            if (ch.isLetterOrDigit()) append(ch)
        }
    }

    /**
     * True when [record] and [plugin] represent the SAME plugin — the ordered
     * ladder from the class KDoc. Never throws; blank/absent fields simply
     * skip their rung.
     */
    fun matches(record: CsPluginRecord, plugin: SitePlugin): Boolean {
        // 1 — exact internalName (the historical, strongest string rung).
        if (record.internalName == plugin.internalName) return true

        // 2 — the repo's internalName captured at link time.
        if (record.repoInternalName != null && record.repoInternalName == plugin.internalName) {
            return true
        }

        // 3 — the .cs3 download URL.
        if (!record.url.isNullOrBlank() && record.url == plugin.url) return true

        // 4 — the .cs3 file hash ("sha256-<hex>").
        if (!record.fileHash.isNullOrBlank() &&
            !plugin.fileHash.isNullOrBlank() &&
            record.fileHash == plugin.fileHash
        ) {
            return true
        }

        // 5 — normalized internalName.
        val recordKey = normalize(record.internalName)
        if (recordKey.isNotEmpty() && recordKey == normalize(plugin.internalName)) {
            return true
        }

        // 6 — normalized display name (both non-blank).
        val recordName = normalize(record.name)
        val pluginName = normalize(plugin.name)
        return recordName.isNotEmpty() && recordName == pluginName
    }

    /**
     * The import-side variant: does an installed record match a FILE being
     * manually imported right now? The file has no SitePlugin yet — its
     * identity is the derived [importInternalName], the manifest's display
     * name, and (when the caller computed it) the file's sha256. Used by
     * [com.confused.anikuta.data.cloudstream.CloudstreamPluginManager.importSharedPlugin]
     * for the "already installed" check + the repo linkage, so a re-import of
     * a linked plugin is recognized EVEN IF the record's identity was
     * rewritten by the linkage back-fill.
     */
    fun matchesImport(
        record: CsPluginRecord,
        importInternalName: String,
        importDisplayName: String?,
        importFileHash: String?,
    ): Boolean {
        // Exact + normalized internal names.
        if (record.internalName == importInternalName) return true
        val importKey = normalize(importInternalName)
        if (importKey.isNotEmpty()) {
            val recordKey = normalize(record.internalName)
            if (recordKey.isNotEmpty() && recordKey == importKey) return true
        }

        // The repo-side name captured at link time (exact + normalized).
        record.repoInternalName?.let { linked ->
            if (linked == importInternalName) return true
            val linkedKey = normalize(linked)
            if (linkedKey.isNotEmpty() && linkedKey == importKey) return true
        }

        // The file hash (raw .cs3 re-shares byte-match the recorded hash).
        if (importFileHash != null && !record.fileHash.isNullOrBlank() &&
            record.fileHash == importFileHash
        ) {
            return true
        }

        // Display names (both non-blank).
        if (!importDisplayName.isNullOrBlank()) {
            val recordName = normalize(record.name)
            val importName = normalize(importDisplayName)
            if (recordName.isNotEmpty() && recordName == importName) return true
        }

        return false
    }
}
