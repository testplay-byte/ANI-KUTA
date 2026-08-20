package com.confused.anikuta.core.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The `.data.json` file written to each content folder.
 *
 * Schema v1 (D-242 reset — per §30 debug-build schema freedom, we don't worry
 * about migration or backward-compat with old schema versions; the debug workflow
 * is "clear app data → reinstall → fresh DB → test"). This is the canonical
 * schema; there are no v2/v3 predecessors to be compatible with.
 *
 * Contains:
 *  - Content-level identity (mainId, contentId, title, cover, FK fields).
 *  - Per-episode download info ([episodes] list — one entry per downloaded
 *    episode, carrying the episode key, number, name, description, video URI,
 *    subtitle URIs, quality, server, audio variant, download timestamp, file size).
 *
 * The [contentId] is the PRIMARY linking mechanism for reinstall recognition
 * (NOT [mainId], which is a random UUID that changes on reinstall). The scanner
 * uses [contentId] to find existing content records via
 * `ContentRepository.getMainEntryByContentId`.
 *
 * The [episodes] list is the DURABLE source of truth for which episodes are
 * downloaded. The SQLite `downloaded_episode` table is a cache (rebuilt by the
 * scanner from this list on startup). When the user marks an episode for delete,
 * the entry is removed from this list AND the DB row is deleted.
 *
 * Schema is parsed with `ignoreUnknownKeys = true` so adding fields in the
 * future won't break old files (defensive — not needed for debug, but cheap).
 *
 * Example:
 * ```json
 * {
 *   "schemaVersion": 1,
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
 *       "episodeKey": "https://anikoto.example.com/anime/123/ep1",
 *       "episodeNumber": 1.0,
 *       "episodeUrl": "https://anikoto.example.com/anime/123/ep1",
 *       "episodeName": "Episode 1 — The Beginning",
 *       "episodeDescription": "Yuji encounters a cursed spirit...",
 *       "videoUrl": "https://cdn.example.com/video/123-ep1.mp4",
 *       "videoUri": "content://com.android.externalstorage.documents/tree/...%2FEpisode%2001.mp4",
 *       "subtitleUris": ["content://.../subtitle_E00001_english_0.vtt"],
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
    /** Per-episode download info — one entry per downloaded episode. */
    @SerialName("episodes")
    val episodes: List<DownloadedEpisodeInfo> = emptyList(),
    @SerialName("createdAt")
    val createdAt: Long,
    @SerialName("updatedAt")
    val updatedAt: Long,
) {
    companion object {
        /** The current data.json schema version. */
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * The JSON parser — `ignoreUnknownKeys = true` so adding fields in the
         * future won't break old files on disk (defensive — cheap insurance).
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
 * Per-episode download info stored in `.data.json`'s `episodes` list.
 *
 * When a download completes, this info is appended (or updated, if the episode
 * was previously downloaded) to the `episodes` list. When the app is reinstalled
 * and the user re-selects the same SAF folder, the scanner reads this list and
 * restores the `downloaded_episode` DB rows — so the user sees their downloads
 * without having to re-download.
 *
 * Field notes:
 *  - [episodeKey] is the stable, unique key used by the rest of the download
 *    stack. It equals `SEpisode.url` (the extension's episode URL) — NOT a
 *    derived `$mainId|$epNumPadded` string. This is CRITICAL: the runtime
 *    lookup (`downloadManager.isEpisodeDownloaded(mainId, episodeKey)`) uses
 *    `SEpisode.url`, so the stored key MUST match.
 *  - [episodeDescription] is the episode synopsis (from `SEpisode.summary`).
 *    Nullable — not all extensions provide it.
 *  - [videoUri] is the `content://` URI of the on-disk video file. After a
 *    reinstall the SAF URI changes (different tree), so the scanner rebuilds
 *    this from the file walk.
 *  - [subtitleUris] is the list of subtitle `content://` URIs (rebuilt on scan).
 */
@Serializable
data class DownloadedEpisodeInfo(
    /**
     * The stable episode key — equals `SEpisode.url` (the extension's episode
     * URL). This is what `downloadManager.isEpisodeDownloaded(mainId, episodeKey)`
     * + `downloadManager.getDownloadedEpisodeUri(mainId, episodeKey)` look up.
     * The scanner MUST preserve this from the existing `.data.json` (not
     * re-derive it from the file name) so the runtime lookup matches.
     */
    @SerialName("episodeKey")
    val episodeKey: String,
    @SerialName("episodeNumber")
    val episodeNumber: Double,
    @SerialName("episodeUrl")
    val episodeUrl: String,
    @SerialName("episodeName")
    val episodeName: String? = null,
    /** Episode synopsis (from `SEpisode.summary`). Nullable — not all extensions provide it. */
    @SerialName("episodeDescription")
    val episodeDescription: String? = null,
    @SerialName("videoUrl")
    val videoUrl: String? = null,
    /** On-disk `content://` URI of the published video (rebuilt on scan). */
    @SerialName("videoUri")
    val videoUri: String? = null,
    /** On-disk `content://` URIs of published subtitle files (rebuilt on scan). */
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
