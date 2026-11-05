package com.confused.anikuta.core.backup.interop

/**
 * ROUND 137 (D-828): the backup formats ANI-KUTA can WRITE and READ besides
 * its own `.anikuta` archive.
 *
 * Research provenance (all verified against the live sources on 2026-11-04):
 *
 *  - **ANIYOMI** (`aniyomiorg/aniyomi`, Apache-2.0): a backup is a GZIP'd
 *    kotlinx-serialization PROTOBUF payload (`BackupDecoder.kt` sniffs the
 *    0x1f8b magic, then `ProtoBuf.decodeFromByteArray(Backup.serializer())`).
 *    The schema is `@Serializable` classes with `@ProtoNumber` annotations in
 *    `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/` — anime data
 *    rides fields 501-503 (BackupAnime / categories / sources), `isLegacy`
 *    sits at field 500 (legacy 0.x backups carry anime at fields 3/4/103).
 *  - **ANIKKU** (`komikku-app/anikku`, Apache-2.0): anime rides the MIHON
 *    manga wire shape — `BackupManga` at field 501 with `BackupChapter`
 *    episodes, watch state in `seen` + `lastPageRead`, and the Aniyomi season
 *    fields 500-507. Its new-format top level is `isLegacy @500` +
 *    `backupManga @501` (the manga twin of Aniyomi's anime layout), plus
 *    anikku-specific fields 600 (saved searches) / 610 (feeds) that double as
 *    detection markers.
 *  - **ANILIST**: the list-import/export JSON — a `MediaListCollection`
 *    document (optionally wrapped in `{"data": {...}}`), lists of entries
 *    carrying status/score/progress plus a `media` object.
 *  - **CLOUDSTREAM** (`recloudstream/cloudstream`, Apache-2.0): the CS3 backup
 *    is a JSON file `{ "datastore": BackupVars, "settings": BackupVars }`
 *    where each vars object buckets every DataStore key by value type
 *    (`_Bool`/`_Int`/`_String`/`_Float`/`_Long`/`_StringSet`). Bookmarks are
 *    `result_watch_state_data/<id>` keys holding `BookmarkedData` JSON; the
 *    watched flag is `result_watch_state/<id>`; the resume pointer is
 *    `result_resume_watching/<id>` holding `ResumeWatching` JSON.
 *
 * MIHON/Tachiyomi (pure MANGA backups) are deliberately NOT readable yet —
 * the manga library is a later round; the detector rejects them with a clear
 * message instead of importing manga rows into an anime library.
 */
enum class InteropFormat(
    val displayName: String,
    val fileExtension: String,
    val mime: String,
    /** One line shown under the picker row + in the format dialog. */
    val description: String,
) {
    /** ANI-KUTA's own format — the full-fidelity archive (not an interop target). */
    ANIKUTA(
        displayName = "ANI-KUTA",
        fileExtension = ".anikuta",
        mime = "application/zip",
        description = "The complete archive — library, history, settings, extensions, offline covers",
    ),
    ANIYOMI(
        displayName = "Aniyomi",
        fileExtension = ".tachibk",
        mime = "application/octet-stream",
        description = "Library, categories, watch progress & AniList tracking for Aniyomi-format apps",
    ),
    ANIKKU(
        displayName = "Anikku",
        fileExtension = ".anikku",
        mime = "application/octet-stream",
        description = "The Anikku backup shape (anime riding the Mihon wire format)",
    ),
    ANILIST(
        displayName = "AniList",
        fileExtension = ".json",
        mime = "application/json",
        description = "AniList's MediaListCollection JSON — list status, scores & progress",
    ),
    CLOUDSTREAM(
        displayName = "CloudStream",
        fileExtension = ".json",
        mime = "application/json",
        description = "The CS3 backup JSON — bookmarks, watched flags & resume positions",
    ),
    ;

    companion object {
        val INTEROP_TARGETS = listOf(ANIYOMI, ANIKKU, ANILIST, CLOUDSTREAM)
    }
}
