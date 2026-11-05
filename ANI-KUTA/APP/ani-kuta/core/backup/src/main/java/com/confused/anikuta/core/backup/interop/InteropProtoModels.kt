package com.confused.anikuta.core.backup.interop

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

/**
 * ROUND 137 (D-828): the Aniyomi + Anikku backup wire models.
 *
 * Every `@ProtoNumber` below was copied from the upstream sources on
 * 2026-11-04 (Apache-2.0; both repos credited in [InteropFormat]'s KDoc):
 *  - Aniyomi: `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/`
 *  - Anikku:  `app/src/main/java/eu/kanade/tachiyomi/data/backup/models/`
 *    (the manga shape — anime rides BackupManga/BackupChapter).
 *
 * Only the fields ANI-KUTA reads or writes are declared; kotlinx-serialization
 * protobuf SKIPS unknown fields on decode (forward-compatible) and omits
 * default-valued fields on encode (proto3 semantics — the same rules the
 * upstream serializers follow). Changing a number here is a wire break.
 */
object InteropProto {

    // ── Shared leaf messages (identical wire numbers in both shapes) ─────────

    /** `BackupCategory` — @1 name, @2 order, @3 id, @100 flags. */
    @Serializable
    data class BackupCategory(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val order: Long = 0,
        @ProtoNumber(3) val id: Long = 0,
        @ProtoNumber(100) val flags: Long = 0,
    )

    /** `BackupSource` / `BackupAnimeSource` — @1 name, @2 sourceId. */
    @Serializable
    data class BackupSource(
        @ProtoNumber(1) val name: String = "",
        @ProtoNumber(2) val sourceId: Long = 0,
    )

    /**
     * `BackupAnimeTracking` — the tracker row. `syncId` is the TRACKER id
     * (Aniyomi TrackerManager: MAL=1, AniList=2, Kitsu=3, Shikimori=4,
     * Bangumi=5, MangaUpdates=7, Simkl=101, Jellyfin=102); `status` is the
     * tracker-specific int (AniList anime: WATCHING=11, COMPLETED=2,
     * PAUSED=3, DROPPED=4, PLAN_TO_WATCH=15, REWATCHING=16; older backups
     * carry the manga-shaped 1/5/6 — the importer maps both generations).
     */
    @Serializable
    data class BackupTracking(
        @ProtoNumber(1) val syncId: Int = 0,
        @ProtoNumber(2) val libraryId: Long = 0,
        @ProtoNumber(3) val mediaIdInt: Int = 0,
        @ProtoNumber(4) val trackingUrl: String = "",
        @ProtoNumber(5) val title: String = "",
        @ProtoNumber(6) val lastChapterRead: Float = 0f,
        @ProtoNumber(7) val totalChapters: Int = 0,
        @ProtoNumber(8) val score: Float = 0f,
        @ProtoNumber(9) val status: Int = 0,
        @ProtoNumber(10) val startedReadingDate: Long = 0,
        @ProtoNumber(11) val finishedReadingDate: Long = 0,
        @ProtoNumber(12) val private: Boolean = false,
        @ProtoNumber(100) val mediaId: Long = 0,
    )

    // ── ANIYOMI shape (anime data at fields 501+) ─────────────────────────────

    /** `BackupAnime` — episodes carry watch state; history carries the timestamps. */
    @Serializable
    data class AyBackupAnime(
        @ProtoNumber(1) val source: Long = 0,
        @ProtoNumber(2) val url: String = "",
        @ProtoNumber(3) val title: String = "",
        @ProtoNumber(4) val artist: String? = null,
        @ProtoNumber(5) val author: String? = null,
        @ProtoNumber(6) val description: String? = null,
        @ProtoNumber(7) val genre: List<String> = emptyList(),
        @ProtoNumber(8) val status: Int = 0,
        @ProtoNumber(9) val thumbnailUrl: String? = null,
        @ProtoNumber(13) val dateAdded: Long = 0,
        @ProtoNumber(16) val episodes: List<AyBackupEpisode> = emptyList(),
        @ProtoNumber(17) val categories: List<Long> = emptyList(),
        @ProtoNumber(18) val tracking: List<BackupTracking> = emptyList(),
        @ProtoNumber(100) val favorite: Boolean = true,
        @ProtoNumber(104) val history: List<BackupAnimeHistory> = emptyList(),
        @ProtoNumber(106) val lastModifiedAt: Long = 0,
    )

    /**
     * `BackupEpisode` — @6 lastSecondSeen (the resume position in SECONDS),
     * @16 totalSeconds (the episode duration in SECONDS), @4 seen.
     */
    @Serializable
    data class AyBackupEpisode(
        @ProtoNumber(1) val url: String = "",
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(3) val scanlator: String? = null,
        @ProtoNumber(4) val seen: Boolean = false,
        @ProtoNumber(5) val bookmark: Boolean = false,
        @ProtoNumber(6) val lastSecondSeen: Long = 0,
        @ProtoNumber(16) val totalSeconds: Long = 0,
        @ProtoNumber(7) val dateFetch: Long = 0,
        @ProtoNumber(8) val dateUpload: Long = 0,
        @ProtoNumber(9) val episodeNumber: Float = 0f,
        @ProtoNumber(10) val sourceOrder: Long = 0,
    )

    /** `BackupAnimeHistory` — @1 episode url, @2 lastRead (epoch ms), @3 readDuration (ms). */
    @Serializable
    data class BackupAnimeHistory(
        @ProtoNumber(1) val url: String = "",
        @ProtoNumber(2) val lastRead: Long = 0,
        @ProtoNumber(3) val readDuration: Long = 0,
    )

    /**
     * The Aniyomi top-level `Backup`. Field 500 `isLegacy` — WIRE GOTCHA: the
     * upstream detector (`BackupDetector.isLegacyBackup`) classifies a file as
     * LEGACY when `isLegacy` reads true AND field 103 is non-empty, and
     * kotlinx-protobuf OMITS fields equal to their declared default. So the
     * default here is deliberately TRUE: the exporter always passes false,
     * the value differs from the default, and the field is EMITTED — exactly
     * what the upstream creators do (their default is true and they pass
     * false). A false-default here would silently produce legacy-looking
     * files that Aniyomi reads as empty.
     *
     * The legacy 0.x layout carried anime at fields 3/4/103, which this
     * message ALSO declares so legacy files decode in one pass.
     */
    @Serializable
    data class AyBackup(
        @ProtoNumber(1) val backupManga: List<MangaShape> = emptyList(),
        @ProtoNumber(2) val backupCategories: List<BackupCategory> = emptyList(),
        // Legacy 0.x layout (aniyomi): anime + anime categories + anime sources.
        @ProtoNumber(3) val legacyBackupAnime: List<AyBackupAnime> = emptyList(),
        @ProtoNumber(4) val legacyBackupAnimeCategories: List<BackupCategory> = emptyList(),
        @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
        @ProtoNumber(103) val legacyBackupAnimeSources: List<BackupSource> = emptyList(),
        @ProtoNumber(500) val isLegacy: Boolean = true,
        @ProtoNumber(501) val backupAnime: List<AyBackupAnime> = emptyList(),
        @ProtoNumber(502) val backupAnimeCategories: List<BackupCategory> = emptyList(),
        @ProtoNumber(503) val backupAnimeSources: List<BackupSource> = emptyList(),
    )

    // ── MIHON / ANIKKU manga shape (anime rides the manga messages) ───────────

    /**
     * `BackupManga` (Anikku) — the "episodes" are chapters on the wire; ANIME
     * data hides in here with the season/background fields 500-507.
     */
    @Serializable
    data class MangaShape(
        @ProtoNumber(1) val source: Long = 0,
        @ProtoNumber(2) val url: String = "",
        @ProtoNumber(3) val title: String = "",
        @ProtoNumber(4) val artist: String? = null,
        @ProtoNumber(5) val author: String? = null,
        @ProtoNumber(6) val description: String? = null,
        @ProtoNumber(7) val genre: List<String> = emptyList(),
        @ProtoNumber(8) val status: Int = 0,
        @ProtoNumber(9) val thumbnailUrl: String? = null,
        @ProtoNumber(13) val dateAdded: Long = 0,
        @ProtoNumber(16) val episodes: List<ChapterShape> = emptyList(),
        @ProtoNumber(17) val categories: List<Long> = emptyList(),
        @ProtoNumber(18) val tracking: List<BackupTracking> = emptyList(),
        @ProtoNumber(100) val favorite: Boolean = true,
        @ProtoNumber(104) val history: List<BackupHistory> = emptyList(),
        @ProtoNumber(106) val lastModifiedAt: Long = 0,
        // Anikku/Aniyomi extensions that double as anime markers:
        @ProtoNumber(500) val backgroundUrl: String? = null,
        @ProtoNumber(505) val seasonNumber: Double = -1.0,
        @ProtoNumber(602) val customStatus: Int = 0,
    )

    /**
     * `BackupChapter` (Anikku) — the watch state rides @4 seen + @6
     * lastPageRead (the resume position in SECONDS for anime, "pages" for
     * manga — the same slot Anikku writes lastSecondSeen into).
     */
    @Serializable
    data class ChapterShape(
        @ProtoNumber(1) val url: String = "",
        @ProtoNumber(2) val name: String = "",
        @ProtoNumber(3) val scanlator: String? = null,
        @ProtoNumber(4) val read: Boolean = false,
        @ProtoNumber(5) val bookmark: Boolean = false,
        @ProtoNumber(6) val lastPageRead: Long = 0,
        @ProtoNumber(7) val dateFetch: Long = 0,
        @ProtoNumber(8) val dateUpload: Long = 0,
        @ProtoNumber(9) val chapterNumber: Float = 0f,
        @ProtoNumber(10) val sourceOrder: Long = 0,
    )

    /** `BackupHistory` (manga shape) — @1 chapter url, @2 lastRead, @3 readDuration. */
    @Serializable
    data class BackupHistory(
        @ProtoNumber(1) val url: String = "",
        @ProtoNumber(2) val lastRead: Long = 0,
        @ProtoNumber(3) val readDuration: Long = 0,
    )

    /**
     * The Anikku/mihon top-level `Backup` — the manga twin of [AyBackup].
     * Anikku's new format carries manga at field 501 with `isLegacy @500`
     * written false; legacy mihon carried manga at field 1. The `isLegacy`
     * default mirrors [AyBackup]'s — TRUE here so the exporter's explicit
     * false is actually EMITTED (the kotlinx default-omission gotcha).
     */
    @Serializable
    data class AkBackup(
        @ProtoNumber(1) val legacyBackupManga: List<MangaShape> = emptyList(),
        @ProtoNumber(2) val legacyBackupCategories: List<BackupCategory> = emptyList(),
        @ProtoNumber(101) val backupSources: List<BackupSource> = emptyList(),
        @ProtoNumber(500) val isLegacy: Boolean = true,
        @ProtoNumber(501) val backupManga: List<MangaShape> = emptyList(),
        @ProtoNumber(502) val backupCategories: List<BackupCategory> = emptyList(),
        @ProtoNumber(503) val backupSourcesNew: List<BackupSource> = emptyList(),
        // Anikku-specific markers (also used for detection):
        @ProtoNumber(600) val backupSavedSearches: List<AnikkuMarker> = emptyList(),
        @ProtoNumber(610) val backupFeeds: List<AnikkuMarker> = emptyList(),
    )

    /** A placeholder for the anikku-only lists (we never read their contents). */
    @Serializable
    data class AnikkuMarker(
        @ProtoNumber(1) val id: Long = 0,
    )
}
