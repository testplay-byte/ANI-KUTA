package com.confused.anikuta.core.download

/**
 * D-404 (round 29): the `.data.json` INTEGRITY toolkit — pure logic, no Android,
 * no SAF, no I/O. Everything here is unit-tested ([DataJsonRepairTest]).
 *
 * Born from the round-29 device postmortem: every previous round's "data.json
 * not updated" report traced back to ONE primitive — `openOutputStream(uri, "w")`
 * does NOT truncate on AOSP ExternalStorageProvider (`ParcelFileDescriptor
 * .parseMode("w")` = `MODE_WRITE_ONLY`, no `MODE_TRUNCATE`), so every write that
 * SHRANK the json left the old tail behind → `new-json-head + old-json-tail` →
 * unparseable file → invisible folder (`findContentFolder` skips unparseable
 * jsons) → the last-episode delete skipped every disk phase. The two tools here
 * close that hole from both sides:
 *
 *  1. [salvageCompleteJsonHead] — REPAIR: recovers the first complete top-level
 *     JSON object out of a corrupted (tail-garbage) file. Wired into
 *     [DownloadStorageProvider.readDataJsonIndexed] as the parse-failure
 *     fallback so the app SELF-HEALS the exact corruption v0.4.16 left on the
 *     user's disk (startup scan, folder locate, delete — every read path).
 *  2. [rebuildEpisodesAfterDelete] — PREVENT: the delete flow no longer does a
 *     read-modify-write keyed on episodeKey matching. It REBUILDS the episodes
 *     list from the DB rows (the app's functional truth) and writes that —
 *     no matching, no key drift, no ghost entries, no idempotent no-op holes.
 *  3. [episodesEqual] — VERIFY: the strict post-write check (parse + exact
 *     (key, number) set equality) used by the verified rewrite ladder.
 */
object DataJsonRepair {

    // ── 1. SALVAGE ────────────────────────────────────────────────────────────

    /**
     * Extracts the first COMPLETE top-level JSON object from [text].
     *
     * Handles the exact corruption shape the non-truncating `"w"` write leaves
     * behind: a file whose head is a complete, valid, pretty-printed document
     * and whose tail is leftover garbage from a previous, longer document.
     *
     * The scan is a balanced-brace walker that respects JSON string literals
     * (quotes + backslash escapes) — braces inside strings never count, and
     * escaped quotes inside strings never terminate them.
     *
     * @return the substring covering the first complete top-level `{ … }`
     *   object (leading whitespace skipped), or `null` when no complete object
     *   exists (clean-empty file, pure garbage, or a head TRUNCATED mid-object).
     *   A clean file returns its whole (trimmed) text — salvage is a no-op.
     */
    fun salvageCompleteJsonHead(text: String): String? {
        if (text.isBlank()) return null
        // Find the opening brace of the first top-level object.
        val start = text.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until text.length) {
            val c = text[i]
            if (inString) {
                when {
                    escaped -> escaped = false // the escaped char is consumed
                    c == '\\' -> escaped = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) {
                        // Balanced — the first complete object ends here.
                        return text.substring(start, i + 1)
                    }
                    if (depth < 0) return null // '}' before '{' — not object-shaped
                }
            }
        }
        // Never balanced — truncated head, nothing salvageable.
        return null
    }

    // ── 2. REBUILD (DB truth) ─────────────────────────────────────────────────

    /**
     * Rebuilds the `.data.json` `episodes` list for a delete, from the DB rows.
     *
     * The round-29 rule (the user's spec): *update the data.json file of that
     * specific content FIRST, then delete the content afterwards.* The rebuilt
     * list is derived from the DB — the same source the UI deletes from — so
     * the durable file and the app can never disagree about what survived:
     *
     *  - one entry per SURVIVING DB row (rows for the anime, minus the row
     *    being deleted — also defensively re-filtered here by
     *    [deletedEpisodeKey] / [deletedEpisodeNumber]);
     *  - metadata is ENRICHED from [existingEpisodes] (the current — possibly
     *    salvaged — `.data.json` entries) matched by key, then by number, so
     *    fields the DB does not carry (videoUrl, videoServer, audioVariant,
     *    episodeUrl) survive the rebuild;
     *  - the DB row's key/number/URIs always WIN over the json entry's (the
     *    runtime lookup `isEpisodeDownloaded(mainId, episodeKey)` uses the DB
     *    key — a drifted json key must never win);
     *  - entries in [existingEpisodes] with NO surviving DB row (ghosts from
     *    any older corruption) are DROPPED — the DB is the truth;
     *  - a `null`/empty [existingEpisodes] (corrupted or missing file) simply
     *    yields fresh entries from the rows — the rebuild HEALS the file.
     *
     * The result is sorted by (episodeNumber, episodeKey) — the same ordering
     * convention `upsertEpisodeInDataJson` writes, so the file stays stable
     * and human-readable.
     */
    fun rebuildEpisodesAfterDelete(
        existingEpisodes: List<DownloadedEpisodeInfo>?,
        survivingDbRows: List<DownloadedEpisode>,
        deletedEpisodeKey: String,
        deletedEpisodeNumber: Double?,
    ): List<DownloadedEpisodeInfo> {
        // Defensive re-filter: the caller already excluded the deleted row, but
        // the in-flight row is still in the DB at Phase-2 time (it dies in
        // Phase 5) — never let it (or a number-twin) back into the durable list.
        val survivors = survivingDbRows.filter { row ->
            row.episode.episodeKey != deletedEpisodeKey &&
                row.episode.episodeNumber.toDouble() != deletedEpisodeNumber
        }
        val existingByKey = HashMap<String, DownloadedEpisodeInfo>()
        val existingByNumber = HashMap<Double, MutableList<DownloadedEpisodeInfo>>()
        existingEpisodes?.forEach { entry ->
            existingByKey[entry.episodeKey] = entry
            existingByNumber.getOrPut(entry.episodeNumber) { mutableListOf() }.add(entry)
        }
        return survivors.map { row ->
            val matched = existingByKey[row.episode.episodeKey]
                ?: existingByNumber[row.episode.episodeNumber.toDouble()]?.firstOrNull()
            val freshKey = row.episode.episodeKey
            DownloadedEpisodeInfo(
                // DB wins on identity fields — the runtime lookups key on these.
                episodeKey = freshKey,
                episodeNumber = row.episode.episodeNumber.toDouble(),
                // Enrichment fields the DB does not carry survive via `matched`.
                episodeUrl = matched?.episodeUrl?.takeIf { it.isNotBlank() } ?: freshKey,
                episodeName = row.episode.name.ifBlank { matched?.episodeName },
                episodeDescription = row.episode.description ?: matched?.episodeDescription,
                videoUrl = matched?.videoUrl,
                videoUri = row.videoUri,
                subtitleUris = row.subtitleUris,
                quality = row.quality ?: matched?.quality,
                videoServer = row.videoServer ?: matched?.videoServer,
                audioVariant = row.videoAudio ?: matched?.audioVariant,
                downloadedAt = row.completedAt,
                fileSize = row.sizeBytes.takeIf { it > 0 } ?: matched?.fileSize,
            )
        }.sortedWith(compareBy({ it.episodeNumber }, { it.episodeKey }))
    }

    // ── 3. VERIFY ─────────────────────────────────────────────────────────────

    /**
     * STRICT post-write verification: do the two episodes lists describe the
     * exact same set — same size, same (key, episodeNumber) pairs?
     *
     * Order-insensitive (the serializer sorts, but verification must not depend
     * on it). Used by the verified rewrite ladder: the re-read must equal the
     * EXPECTED rebuilt list — not merely "the deleted entry is absent" (the
     * round-28 bar) but "the file now says exactly what the DB says".
     */
    fun episodesEqual(
        a: List<DownloadedEpisodeInfo>,
        b: List<DownloadedEpisodeInfo>,
    ): Boolean {
        if (a.size != b.size) return false
        val aPairs = a.map { it.episodeKey to it.episodeNumber }.toSet()
        val bPairs = b.map { it.episodeKey to it.episodeNumber }.toSet()
        return aPairs == bPairs && aPairs.size == a.size // set==set AND no dup keys
    }
}
