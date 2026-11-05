package com.confused.anikuta.core.backup.interop

/**
 * ROUND 137 (D-828): the format-NEUTRAL restore plan.
 *
 * Every interop reader (Aniyomi protobuf, Anikku manga-shape protobuf,
 * AniList JSON, CloudStream JSON) parses its file into THIS shape; the
 * app-layer [InteropRestoreBridge] then applies it through the live content
 * system (ContentResolver identity + library + watch progress + tracking).
 * Nothing here knows a wire format — the field mapping lives in the readers.
 */
data class InteropEntry(
    val title: String,
    /** The entry's url on its source (null for metadata-only AniList entries). */
    val animeUrl: String?,
    /**
     * The extension source id: the Aniyomi internal source.id for aniyomi /
     * anikku entries, the CS-bridged synthetic id (flag | hash(providerName))
     * for CloudStream entries, null for AniList-only entries.
     */
    val sourceId: Long?,
    /** "aniyomi" | "cloudstream" | null — which extension system the entry belongs to. */
    val ecosystem: String?,
    /** For AniList-format entries: the media id (data_source_ref_id). */
    val anilistId: Long?,
    val coverUrl: String?,
    val bannerUrl: String?,
    val synopsis: String?,
    val genres: List<String>,
    /** The entry's airing status string, mapped to our ext/data status axis. */
    val statusText: String?,
    val totalEpisodes: Int?,
    val seasonYear: Int?,
    val isAdult: Boolean = false,
    /** false = watch history only (no library add). */
    val favorite: Boolean = true,
    val dateAdded: Long = 0,
    /** The library category NAMES this entry belongs to (resolved via [InteropRestoreBridge.ensureCategory]). */
    val categoryNames: List<String> = emptyList(),
    val episodes: List<InteropEpisode> = emptyList(),
    /** History rows keyed by EPISODE URL (the wire's join key). */
    val history: List<InteropHistory> = emptyList(),
    val tracking: List<InteropTracking> = emptyList(),
)

/** One watched/resumable episode of an [InteropEntry]. `url` may be "" (unresolvable). */
data class InteropEpisode(
    val url: String,
    val name: String,
    val number: Float,
    val seen: Boolean,
    val positionSec: Long,
    val durationSec: Long,
    val sourceOrder: Long = 0,
)

/** One history row — the last-read timestamp + the actually-played duration. */
data class InteropHistory(
    val url: String,
    val lastReadMs: Long,
    val readDurationMs: Long,
)

/** One tracker row (import: only AniList rows are applied; the rest are counted). */
data class InteropTracking(
    /** The tracker's wire id (aniyomi syncId): 1=MAL, 2=AniList, 3=Kitsu, 4=Shikimori, 101=Simkl… */
    val trackerWireId: Int,
    /** The tracker's REMOTE media id (AniList media id for AniList rows). */
    val remoteId: Long,
    val title: String,
    val url: String,
    /** Episodes/chapters watched. */
    val progress: Int,
    val totalEpisodes: Int,
    /** The tracker-native score float (AniList: 0-100 raw). */
    val score: Float,
    /** The tracker-native status int (AniList anime: 11/15/2/3/4/16; legacy 1/5/6). */
    val statusInt: Int,
    val startedMs: Long,
    val finishedMs: Long,
)

/** A parsed interop backup — what the bridge applies. */
data class InteropRestorePlan(
    val format: InteropFormat,
    val entries: List<InteropEntry>,
    /** The category names in DISPLAY ORDER (index = the wire's order number). */
    val categories: List<String>,
    val warnings: List<String>,
    /** Mihon/Tachiyomi MANGA entries the user asked us to skip (manga = later round). */
    val skippedManga: Int = 0,
)

/** The restore outcome the UI reports. */
data class InteropRestoreSummary(
    val format: InteropFormat,
    val entriesRead: Int,
    val entriesCreated: Int,
    val entriesMatched: Int,
    val libraryAdded: Int,
    val categoriesAdded: Int,
    val progressRows: Int,
    val trackingRows: Int,
    val skippedManga: Int,
    /** Entries whose source is unknown on this device (they resolve once the extension is installed). */
    val pendingSources: Int,
    val warnings: List<String>,
)

/** The export outcome the UI reports. */
data class InteropExportSummary(
    val format: InteropFormat,
    val fileName: String,
    val entriesWritten: Int,
    val categoriesWritten: Int,
    val episodesWritten: Int,
    val trackingRows: Int,
    /** Library entries that could not ride this format (no matching source/track id). */
    val entriesSkipped: Int,
)

/** The resolve outcome — the mainId + whether the bridge CREATED the identity. */
data class InteropResolvedEntry(val mainId: String, val created: Boolean)

/**
 * The tracker status maps shared by the exporter, the importer and the
 * app-layer bridge (one home, no drift). The AniList anime ints are Aniyomi's
 * wire values (WATCHING=11, PLAN=15, COMPLETED=2, PAUSED=3, DROPPED=4,
 * REPEATING=16); legacy 0.x files wrote the manga-shaped 1/5/6 — both map.
 */
object InteropStatusMaps {
    fun aniyomiAnilistIntToTrackStatus(status: Int): String = when (status) {
        11 -> "WATCHING"
        15 -> "PLAN_TO_WATCH"
        2 -> "COMPLETED"
        3 -> "PAUSED"
        4 -> "DROPPED"
        16 -> "REWATCHING"
        // Legacy 0.x aniyomi wrote the manga-shaped ints.
        1 -> "WATCHING"
        5 -> "PLAN_TO_WATCH"
        6 -> "REWATCHING"
        else -> "WATCHING"
    }

    fun trackStatusToAniyomiAnilistInt(status: String): Int = when (status.trim().uppercase()) {
        "WATCHING" -> 11
        "PLAN_TO_WATCH" -> 15
        "COMPLETED" -> 2
        "PAUSED" -> 3
        "DROPPED" -> 4
        "REWATCHING" -> 16
        else -> 11
    }
}

/**
 * The content-domain restore contract (impl lives in the app module — the
 * same pattern as [com.confused.anikuta.core.backup.ExtensionBackupBridge]):
 * core:backup stays driver-only; the bridge resolves identities through the
 * ContentResolver and writes library/progress/tracking through their stores.
 */
interface InteropRestoreBridge {

    /**
     * Resolves (or creates) the content for [entry] — null only when the
     * entry is unresolvable (blank identity). MUST dedupe: an entry that
     * already exists (same source+url / same anilist id / same CS
     * provider+url) returns the EXISTING mainId with `created = false`.
     */
    fun resolveEntry(entry: InteropEntry): InteropResolvedEntry?

    /** Creates the category when missing; returns its id (null = unresolvable). */
    fun ensureCategory(name: String): Long?

    /** Adds the entry to a category (dedup-safe). Returns true when a row was added. */
    fun addToLibrary(mainId: String, categoryId: Long): Boolean

    /**
     * Upserts the watch progress for one episode of [mainId]: `number` keys the
     * standardized `mainId|%05d` episode key; [history] supplies the timestamp
     * + played duration (null = no timestamp known — totals only).
     */
    fun upsertProgress(mainId: String, episode: InteropEpisode, history: InteropHistory?)

    /** Upserts the tracker cache row + the tracking intent for [mainId]. */
    fun upsertTracking(mainId: String, tracking: InteropTracking)
}
