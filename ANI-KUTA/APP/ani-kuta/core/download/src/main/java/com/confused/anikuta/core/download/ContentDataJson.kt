package com.confused.anikuta.core.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The `data.json` file written to each content folder.
 *
 * D-240: Schema v2 — added [episodes] list to track downloaded episodes.
 * This allows the app to restore download state after reinstall — when the
 * user selects the same SAF folder, the scanner reads data.json + knows
 * exactly which episodes were downloaded, their quality, server, audio, etc.
 *
 * D-241: Schema v3 — added `episodeKey`, `videoUri`, `subtitleUris` to each
 * entry in [episodes]. `episodeKey` is REQUIRED for the delete flow to find
 * the right entry to remove. Old v2 .data.json files still load (parser is
 * ignoreUnknownKeys=true + every new field has a default); the scanner
 * backfills `episodeKey` from the file name on the next scan.
 *
 * The [contentId] is the PRIMARY linking mechanism for reinstall recognition
 * (NOT [mainId], which is a random UUID that changes on reinstall). The
 * scanner uses [contentId] to find existing content records via
 * `ContentRepository.getMainEntryByContentId`.
 *
 * Schema is versioned ([schemaVersion]) + parsed with `ignoreUnknownKeys = true`
 * so future schema changes don't break old data.json files on disk.
 *
 * Example (v3):
 * ```json
 * {
 *   "schemaVersion": 3,
 *   "mainId": "550e8400-...",
 *   "contentId": "anilist:aniyomi:none:com.aniyomi.anikoto:69023:https://anikoto.example.com/anime/123",
 *   "title": "Jujutsu Kaisen",
 *   "contentType": "anime",
 *   "contentFormat": "video",
 *   "description": "A boy swallows a cursed talisman...",
 *   "dataSourceId": 1,
 *   "systemId": 1,
 *   "extensionRepoId": null,
 *   "extensionId": 69023,
 *   "sourceId": 69023,
 *   "animeUrl": "https://anikoto.example.com/anime/123",
 *   "displaySource": "extension",
 *   "coverUrl": "https://cdn.example.com/covers/123.jpg",
 *   "anilistId": 101522,
 *   "episodes": [
 *     {
 *       "episodeKey": "550e8400-...|00001",
 *       "episodeNumber": 1.0,
 *       "episodeUrl": "https://anikoto.example.com/anime/123/ep1",
 *       "episodeName": "Episode 1",
 *       "videoUrl": "https://cdn.example.com/video/123-ep1.mp4",
 *       "videoUri": "content://com.android.externalstorage.documents/tree/...%2FJujutsu%20Kaisen%2Fepisodes%2FJujutsu%20Kaisen%20-%20E00001.mp4",
 *       "subtitleUris": [
 *         "content://com.android.externalstorage.documents/tree/...%2FJujutsu%20Kaisen%2Fsubtitles%2Fsubtitle_E00001_english_0.vtt"
 *       ],
 *       "quality": "1080p",
 *       "videoServer": "AniKoto",
 *       "audioVariant": "sub",
 *       "downloadedAt": 1786069380000,
 *       "fileSize": 524288000
 *     }
 *   ],
 *   "createdAt": 1786069380000,
 *   "updatedAt": 1786069380000
 * }
 * ```
 */
@Serializable
data class ContentDataJson(
    @SerialName("schemaVersion")
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    @SerialName("mainId")
    val mainId: String,
    @SerialName("contentId")
    val contentId: String,
    @SerialName("title")
    val title: String,
    @SerialName("contentType")
    val contentType: String = "anime",
    @SerialName("contentFormat")
    val contentFormat: String = "video",
    @SerialName("description")
    val description: String? = null,
    @SerialName("dataSourceId")
    val dataSourceId: Long? = null,
    @SerialName("systemId")
    val systemId: Long? = null,
    @SerialName("extensionRepoId")
    val extensionRepoId: Long? = null,
    @SerialName("extensionId")
    val extensionId: Long? = null,
    @SerialName("sourceId")
    val sourceId: Long? = null,
    @SerialName("animeUrl")
    val animeUrl: String? = null,
    @SerialName("displaySource")
    val displaySource: String = "extension",
    @SerialName("coverUrl")
    val coverUrl: String? = null,
    @SerialName("anilistId")
    val anilistId: Int? = null,
    /** D-240: List of downloaded episodes for this content. */
    @SerialName("episodes")
    val episodes: List<DownloadedEpisodeInfo> = emptyList(),
    @SerialName("createdAt")
    val createdAt: Long,
    @SerialName("updatedAt")
    val updatedAt: Long,
) {
    companion object {
        /** The current data.json schema version. Bump on breaking changes. */
        const val CURRENT_SCHEMA_VERSION = 3

        /**
         * The JSON parser — `ignoreUnknownKeys = true` so future schema changes
         * (adding new fields) don't break old data.json files on disk.
         */
        val json: Json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            prettyPrint = true
        }

        /** Parses a data.json string into a [ContentDataJson]. */
        fun parse(jsonString: String): ContentDataJson? =
            try {
                json.decodeFromString<ContentDataJson>(jsonString)
            } catch (e: Exception) {
                null
            }

        /** Serializes a [ContentDataJson] into a pretty-printed JSON string. */
        fun stringify(data: ContentDataJson): String =
            json.encodeToString(data)
    }
}

/**
 * D-240: Per-episode download info stored in data.json.
 *
 * When a download completes, this info is appended (or updated, if the episode
 * was previously downloaded) to the `episodes` list of the content's
 * `.data.json` file. When the app is reinstalled and the user re-selects the
 * same SAF folder, the scanner reads this list and restores the
 * `downloaded_episode` DB rows — so the user sees their downloads without
 * having to re-download.
 *
 * D-241 (this commit): added [episodeKey] + [videoUri] + [subtitleUris].
 * - [episodeKey] is the stable, unique key (`$mainId|$epNumPadded`) used by
 *   the rest of the download stack — without it, the delete flow can't find
 *   the right entry to remove from the list.
 * - [videoUri] is the `content://` URI of the on-disk video file. After a
 *   reinstall the SAF URI changes (different tree), so the scanner rebuilds
 *   this from the file walk. It's stored for diagnostic / inspection purposes.
 * - [subtitleUris] is the list of subtitle `content://` URIs (rebuilt on scan,
 *   same caveat).
 *
 * Schema is forward-compat: `ignoreUnknownKeys = true` on the parser, and
 * every field added since v1 has a default. v1 .data.json files (no `episodes`
 * list at all) load with `episodes = emptyList()`; v2 files written before
 * D-241 load with `episodeKey = null`/`videoUri = null`/`subtitleUris = null`
 * and the scanner backfills them on the next scan.
 */
@Serializable
data class DownloadedEpisodeInfo(
    /**
     * D-241: The stable episode key (`$mainId|$epNumPadded`) — same value as
     * `DownloadedEpisode.episode.episodeKey`. REQUIRED for delete matching.
     *
     * Nullable for forward-compat with v2 files written before D-241 — the
     * scanner derives it from the video file name when missing.
     */
    @SerialName("episodeKey")
    val episodeKey: String? = null,
    @SerialName("episodeNumber")
    val episodeNumber: Double,
    @SerialName("episodeUrl")
    val episodeUrl: String,
    @SerialName("episodeName")
    val episodeName: String? = null,
    @SerialName("videoUrl")
    val videoUrl: String? = null,
    /** D-241: On-disk `content://` URI of the published video (rebuilt on scan). */
    @SerialName("videoUri")
    val videoUri: String? = null,
    /** D-241: On-disk `content://` URIs of published subtitle files (rebuilt on scan). */
    @SerialName("subtitleUris")
    val subtitleUris: List<String> = emptyList(),
    @SerialName("quality")
    val quality: String? = null,
    @SerialName("videoServer")
    val videoServer: String? = null,
    @SerialName("audioVariant")
    val audioVariant: String? = null,
    @SerialName("downloadedAt")
    val downloadedAt: Long,
    @SerialName("fileSize")
    val fileSize: Long? = null,
)
