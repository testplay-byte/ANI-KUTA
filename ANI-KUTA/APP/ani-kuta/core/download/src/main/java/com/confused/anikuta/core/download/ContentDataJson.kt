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
 * The [contentId] is the PRIMARY linking mechanism for reinstall recognition
 * (NOT [mainId], which is a random UUID that changes on reinstall). The
 * scanner uses [contentId] to find existing content records via
 * `ContentRepository.getMainEntryByContentId`.
 *
 * Schema is versioned ([schemaVersion]) + parsed with `ignoreUnknownKeys = true`
 * so future schema changes don't break old data.json files on disk.
 *
 * Example (v2):
 * ```json
 * {
 *   "schemaVersion": 2,
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
 *       "episodeNumber": 1.0,
 *       "episodeUrl": "https://anikoto.example.com/anime/123/ep1",
 *       "episodeName": "Episode 1",
 *       "videoUrl": "https://cdn.example.com/video/123-ep1.mp4",
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
        const val CURRENT_SCHEMA_VERSION = 2

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
 * When a download completes, this info is appended to the `episodes` list.
 * When the app is reinstalled + the user selects the same SAF folder, the
 * scanner reads this list + restores the `downloaded_episode` DB rows.
 */
@Serializable
data class DownloadedEpisodeInfo(
    @SerialName("episodeNumber")
    val episodeNumber: Double,
    @SerialName("episodeUrl")
    val episodeUrl: String,
    @SerialName("episodeName")
    val episodeName: String? = null,
    @SerialName("videoUrl")
    val videoUrl: String? = null,
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
