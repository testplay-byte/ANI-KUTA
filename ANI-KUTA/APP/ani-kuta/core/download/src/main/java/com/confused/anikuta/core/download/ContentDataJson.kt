package com.confused.anikuta.core.download

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The `data.json` file written to each content folder.
 *
 * D.1.4 + REVIEW-5 M5 (R1-C2): contains ALL FK columns from the `content` table
 * so the scan-on-startup can fully restore the [ContentRecord] after app-delete
 * + reinstall + same-folder-selection.
 *
 * Schema is versioned ([schemaVersion]) + parsed with `ignoreUnknownKeys = true`
 * so future schema changes don't break old data.json files on disk.
 *
 * Example:
 * ```json
 * {
 *   "schemaVersion": 1,
 *   "mainId": "550e8400-e29b-41d4-a716-446655440000",
 *   "contentId": "anilist:anikuta:https://repo.example.com/index.min.json:com.aniyomi.anikoto:69023:https://anikoto.example.com/anime/123",
 *   "title": "Jujutsu Kaisen",
 *   "contentType": "anime",
 *   "contentFormat": "video",
 *   "description": null,
 *   "dataSourceId": null,
 *   "systemId": null,
 *   "extensionRepoId": null,
 *   "extensionId": null,
 *   "sourceId": 69023,
 *   "animeUrl": "https://anikoto.example.com/anime/123",
 *   "displaySource": "extension",
 *   "coverUrl": "https://cdn.example.com/covers/123.jpg",
 *   "anilistId": 101522,
 *   "createdAt": 1786069380000,
 *   "updatedAt": 1786069380000
 * }
 * ```
 *
 * @param schemaVersion The data.json schema version (bump on breaking changes).
 * @param mainId The stable UUID — primary key. Survives source switches + reinstalls.
 * @param contentId The structured content ID (6-section colon-delimited per
 *   ContentIdGenerator). Changes when sources switch.
 * @param title Human-readable title (for folder name + UI).
 * @param contentType "anime" | "movie" | "series" | "manga" | "novel" | ...
 * @param contentFormat "video" | "images" | "text" | "audio".
 * @param description Optional description.
 * @param dataSourceId FK to `data_source` table (nullable — AniList, TMDB, etc.).
 * @param systemId FK to `system` table (nullable — Aniyomi, CloudStream, etc.).
 * @param extensionRepoId FK to `content_ext_repo` table (nullable).
 * @param extensionId Aniyomi internal source.id (plain INTEGER; NOT a FK post-D-189 — content_ext table is unused).
 * @param sourceId The internal source ID within the extension (nullable).
 * @param animeUrl The content's URL on the source (nullable).
 * @param displaySource "extension" | "anilist" | "tmdb" | ...
 * @param coverUrl Cover image URL (for the Downloads screen thumbnail + cover.jpg).
 * @param anilistId Optional AniList ID (if linked — for metadata sync).
 * @param createdAt Epoch millis when the content was first created.
 * @param updatedAt Epoch millis when the content was last updated.
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
    @SerialName("createdAt")
    val createdAt: Long,
    @SerialName("updatedAt")
    val updatedAt: Long,
) {
    companion object {
        /** The current data.json schema version. Bump on breaking changes. */
        const val CURRENT_SCHEMA_VERSION = 1

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
