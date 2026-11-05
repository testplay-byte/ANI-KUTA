package com.confused.anikuta.core.backup.interop

import kotlinx.serialization.Serializable

/**
 * ROUND 137 (D-828): the two JSON interop formats — AniList's list
 * import/export document and the CloudStream CS3 backup file.
 *
 * AniList: a `MediaListCollection` — the format `anilist.co`'s list import
 * (and every community export tool) produces. The importer accepts BOTH the
 * `{"data": {"MediaListCollection": …}}` GraphQL wrapper and the bare
 * collection; the exporter writes the bare collection.
 *
 * CloudStream: `{ "datastore": BackupVars, "settings": BackupVars }` where a
 * vars object buckets every DataStore (SharedPreferences) key by value type.
 * The per-content records ride the `_String` bucket as JSON-STRING values:
 *  - `<account>/result_watch_state_data/<id>` → [CsBookmarkedData] (a bookmark),
 *  - `<account>/result_watch_state/<id>` → a boolean (the watched flag),
 *  - `<account>/result_resume_watching/<id>` → [CsResumeWatching].
 * `<account>` is the CS local-account index ("0" by default).
 */
object InteropJson {

    // ── AniList MediaListCollection ───────────────────────────────────────────

    @Serializable
    data class AlRoot(val data: AlMediaListCollection? = null)

    @Serializable
    data class AlMediaListCollection(
        val user: AlUser? = null,
        val lists: List<AlList> = emptyList(),
    )

    @Serializable
    data class AlUser(val id: Long = 0, val name: String = "")

    @Serializable
    data class AlList(
        /** "Watching" | "Completed" | "Paused" | "Dropped" | "Planning" | "Repeating". */
        val name: String = "",
        val entries: List<AlListEntry> = emptyList(),
    )

    @Serializable
    data class AlListEntry(
        val id: Long = 0,
        val mediaId: Long = 0,
        /** CURRENT | PLANNING | COMPLETED | DROPPED | PAUSED | REPEATING. */
        val status: String = "PLANNING",
        /** The RAW AniList score — 0-100 on the POINT_100 scale (our native scale). */
        val score: Double = 0.0,
        val progress: Int = 0,
        val progressVolumes: Int? = null,
        val repeat: Int = 0,
        val private: Boolean = false,
        val notes: String? = null,
        val startedAt: AlFuzzyDate = AlFuzzyDate(),
        val completedAt: AlFuzzyDate = AlFuzzyDate(),
        val updatedAt: Long = 0,
        val createdAt: Long = 0,
        val media: AlMedia? = null,
    )

    /** AniList's FuzzyDateInt decomposed — nulls/0s mean "unknown". */
    @Serializable
    data class AlFuzzyDate(val year: Int? = null, val month: Int? = null, val day: Int? = null)

    @Serializable
    data class AlMedia(
        val id: Long = 0,
        val idMal: Long? = null,
        val title: AlTitle = AlTitle(),
        /** "ANIME" | "MANGA" — the importer takes ANIME only. */
        val type: String = "ANIME",
        val format: String? = null,
        val status: String? = null,
        val episodes: Int? = null,
        val duration: Int? = null,
        val genres: List<String> = emptyList(),
        val description: String? = null,
        val coverImage: AlCover = AlCover(),
        val bannerImage: String? = null,
        val season: String? = null,
        val seasonYear: Int? = null,
        val averageScore: Int? = null,
        val isAdult: Boolean = false,
    )

    @Serializable
    data class AlTitle(val romaji: String? = null, val english: String? = null, val native: String? = null)

    @Serializable
    data class AlCover(val extraLarge: String? = null, val large: String? = null, val medium: String? = null, val color: String? = null)

    // ── CloudStream CS3 backup ────────────────────────────────────────────────

    @Serializable
    data class CsBackupFile(
        val datastore: CsBackupVars = CsBackupVars(),
        val settings: CsBackupVars = CsBackupVars(),
    )

    /** The per-type key buckets of one CS prefs file (the `_Bool`-style names are the wire). */
    @Serializable
    data class CsBackupVars(
        val _Bool: Map<String, Boolean>? = null,
        val _Int: Map<String, Int>? = null,
        val _String: Map<String, String>? = null,
        val _Float: Map<String, Float>? = null,
        val _Long: Map<String, Long>? = null,
        val _StringSet: Map<String, List<String>>? = null,
    )

    /**
     * CS's `BookmarkedData` — the library entry. Field names are the wire
     * (the upstream class serializes these exact @SerialName names). `rating`
     * is the legacy score slot the current CS still deserializes; ANI-KUTA
     * never writes it (our CS entries carry no score — CS resolves scores
     * from its sync APIs instead).
     */
    @Serializable
    data class CsBookmarkedData(
        val bookmarkedTime: Long = 0,
        val id: Int = 0,
        val latestUpdatedTime: Long = 0,
        val name: String = "",
        val url: String = "",
        val apiName: String = "",
        val type: String? = null,
        val posterUrl: String? = null,
        val year: Int? = null,
        val syncData: Map<String, String>? = null,
        val plot: String? = null,
        val tags: List<String>? = null,
    )

    /** CS's `ResumeWatching` (the `result_resume_watching/<id>` value). */
    @Serializable
    data class CsResumeWatching(
        val parentId: Int = 0,
        val episodeId: Int? = null,
        val episode: Int? = null,
        val season: Int? = null,
        val updateTime: Long = 0,
        val isFromDownload: Boolean = false,
    )

    /** CS's `PosDur` — per-episode position/duration (ms). */
    @Serializable
    data class CsPosDur(val position: Long = 0, val duration: Long = 0)

    // ── Shared helpers ─────────────────────────────────────────────────────────

    /**
     * CS's per-show Int id — mirrors `ResultViewModel2.getLoadResponseIdFromUrl`
     * (`url.replace(api.mainUrl, "").replace("/", "").hashCode()`). When the
     * provider is installed we strip its mainUrl exactly like upstream; a
     * mismatched id only means CS won't auto-highlight the bookmark on open.
     */
    fun csBookmarkId(url: String, providerMainUrl: String? = null): Int = url
        .removePrefix(providerMainUrl.orEmpty())
        .replace("/", "")
        .hashCode()

    /** CS's "Anime" TvType name (the only type we round-trip). */
    const val CS_TYPE_ANIME = "Anime"

    /**
     * The CS library watch state — an INT per show under `result_watch_state`
     * (`WatchType.internalId`: WATCHING=0, COMPLETED=1, ONHOLD=2, DROPPED=3,
     * PLANTOWATCH=4, NONE=5). We only ever write COMPLETED.
     */
    const val CS_WATCH_STATE_COMPLETED = 1

    /** The CS datastore key prefixes we read/write (under the account prefix). */
    const val CS_KEY_BOOKMARK_DATA = "result_watch_state_data"
    const val CS_KEY_WATCH_STATE = "result_watch_state"
    /** The CURRENT resume key (the old `result_resume_watching` was retired). */
    const val CS_KEY_RESUME_WATCHING = "result_resume_watching_2"
    /** The retired key — still accepted on import. */
    const val CS_KEY_RESUME_WATCHING_LEGACY = "result_resume_watching"

    /** AniList list-name → ANI-KUTA TrackStatus value. */
    fun anilistListNameToStatus(name: String): String? = when (name.trim().uppercase()) {
        "WATCHING", "CURRENT" -> "WATCHING"
        "COMPLETED" -> "COMPLETED"
        "PAUSED" -> "PAUSED"
        "DROPPED" -> "DROPPED"
        "PLANNING", "PLAN TO WATCH" -> "PLAN_TO_WATCH"
        "REPEATING", "REWATCHING" -> "REWATCHING"
        else -> null
    }

    /** ANI-KUTA TrackStatus value → AniList list entry status. */
    fun trackStatusToAnilist(status: String): String = when (status.uppercase()) {
        "WATCHING" -> "CURRENT"
        "COMPLETED" -> "COMPLETED"
        "PAUSED" -> "PAUSED"
        "DROPPED" -> "DROPPED"
        "PLAN_TO_WATCH" -> "PLANNING"
        "REWATCHING" -> "REPEATING"
        else -> "PLANNING"
    }
}
