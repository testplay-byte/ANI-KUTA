package com.confused.anikuta.core.backup.interop

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream

/**
 * ROUND 137 (D-828): the interop importer — reads the other apps' backup
 * files into the format-neutral [InteropRestorePlan].
 *
 * Detection order:
 *  1. JSON (file starts with `{`) — `datastore`+`settings` → CloudStream;
 *     `data`/`MediaListCollection`/`lists` → AniList.
 *  2. GZIP-protobuf (0x1f8b magic) — decode with the Aniyomi probe first
 *     (modern anime at field 501, legacy anime at fields 3/4/103), then the
 *     Anikku probe (manga-shape at field 501 + anikku markers). A manga-shape
 *     file WITHOUT anikku markers is a Mihon/Tachiyomi MANGA backup →
 *     rejected with a clear message (manga support is a later round).
 *
 * Identity conversion:
 *  - Aniyomi/Anikku entries ride their source ids verbatim (aniyomi-format
 *    source ids resolve whenever the matching extension is installed here).
 *  - CloudStream bookmarks carry only the plugin's `apiName` — the CS-bridged
 *    synthetic id (flag | hash(apiName) & 0xffffffff) is minted the same way
 *    CloudstreamAnimeSourceBridge.idFor does it, so the entry dedupes against
 *    locally-created CS entries once the plugin is installed.
 *  - AniList entries carry the media id (the data_source_ref_id axis).
 */
class InteropImporter {

    class InteropParseException(message: String) : Exception(message)

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Detects the format and parses the whole file. */
    fun parse(bytes: ByteArray, fileNameHint: String?): InteropRestorePlan {
        if (bytes.isEmpty()) throw InteropParseException("The selected file is empty")

        // ── JSON shapes ──
        val first = bytes.first { it != 0x20.toByte() && it != 0x0A.toByte() && it != 0x0D.toByte() && it != 0x09.toByte() }
        if (first == '{'.code.toByte()) {
            val text = bytes.toString(Charsets.UTF_8)
            return runCatching { parseJson(text) }
                .getOrElse { throw InteropParseException("Unrecognized JSON backup: ${it.message}") }
        }

        // ── Protobuf (possibly gzipped) ──
        val proto = if (bytes.size >= 2 && bytes[0] == 0x1f.toByte() && bytes[1] == 0x8b.toByte()) {
            gunzip(bytes)
        } else {
            bytes
        }

        // The Aniyomi probe — modern anime at 501, legacy anime at 3.
        val ay = runCatching {
            ProtoBuf.decodeFromByteArray(InteropProto.AyBackup.serializer(), proto)
        }.getOrNull()
        if (ay != null && (ay.backupAnime.isNotEmpty() || ay.legacyBackupAnime.isNotEmpty())) {
            return parseAniyomi(ay)
        }

        // The Anikku probe — the manga shape at 501 (new) / 1 (legacy mihon).
        val ak = runCatching {
            ProtoBuf.decodeFromByteArray(InteropProto.AkBackup.serializer(), proto)
        }.getOrNull()
        if (ak != null) {
            // The ALWAYS-present discriminator: Anikku writes isLegacy=false
            // (its @Required field 500, emitted because the serializer's
            // default is true) while Mihon/Tachiyomi files OMIT field 500
            // entirely (decoded as our default true). Feature markers + the
            // file hint stay as extra signals.
            val hasAnikkuMarkers = !ak.isLegacy ||
                ak.backupSavedSearches.isNotEmpty() || ak.backupFeeds.isNotEmpty() ||
                ak.backupManga.any { it.backgroundUrl != null || it.seasonNumber != -1.0 || it.customStatus != 0 } ||
                fileNameHint?.endsWith(InteropFormat.ANIKKU.fileExtension, ignoreCase = true) == true
            if (ak.backupManga.isNotEmpty()) {
                if (!hasAnikkuMarkers) {
                    throw InteropParseException(
                        "This looks like a Mihon/Tachiyomi MANGA backup — manga support is coming in a later update. " +
                            "Anikku anime backups are supported.",
                    )
                }
                return parseAnikku(ak)
            }
        }

        // A manga list at field 1 with no anime anywhere = a Mihon/Tachiyomi
        // manga backup — reject it with the SPECIFIC message (manga is a later
        // round), not the generic unrecognized-file one.
        if (ak != null && ak.legacyBackupManga.isNotEmpty()) {
            throw InteropParseException(
                "This looks like a Mihon/Tachiyomi MANGA backup — manga support is coming in a later update. " +
                    "Anikku anime backups are supported.",
            )
        }
        throw InteropParseException(
            "Unrecognized backup file — supported: ANI-KUTA (.anikuta), Aniyomi (.tachibk), " +
                "Anikku (.anikku), AniList (.json), CloudStream (.json)",
        )
    }

    private fun gunzip(bytes: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(bytes)).use { gz ->
        val out = ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = gz.read(buf)
            if (n <= 0) break
            out.write(buf, 0, n)
        }
        out.toByteArray()
    }

    // ── JSON dispatch ─────────────────────────────────────────────────────────

    private fun parseJson(text: String): InteropRestorePlan {
        val root = json.parseToJsonElement(text)
        if (root !is JsonObject) throw InteropParseException("Not a JSON backup object")

        // CloudStream: the CS3 backup has datastore + settings vars buckets.
        if (root.containsKey("datastore") && root.containsKey("settings")) {
            return parseCloudstream(root)
        }
        // AniList: either the GraphQL wrapper or the bare MediaListCollection.
        val collection = when {
            root.containsKey("MediaListCollection") -> root
            // The BARE collection ({user, lists}) some export tools write.
            root.containsKey("lists") -> root
            else -> (root["data"] as? JsonObject)?.get("MediaListCollection") as? JsonObject
        }
        if (collection != null && collection.containsKey("lists")) {
            return parseAnilist(collection)
        }
        throw InteropParseException(
            "Unrecognized JSON backup — supported: AniList (MediaListCollection) and CloudStream (CS3 backup)",
        )
    }

    // ── Aniyomi ───────────────────────────────────────────────────────────────

    private fun parseAniyomi(backup: InteropProto.AyBackup): InteropRestorePlan {
        // Modern anime at 501; legacy 0.x anime at 3 (with categories at 4).
        val anime = if (backup.backupAnime.isNotEmpty()) {
            backup.backupAnime to backup.backupAnimeCategories
        } else {
            backup.legacyBackupAnime to backup.legacyBackupAnimeCategories
        }
        val categories = anime.second.ifEmpty { backup.backupCategories }
            .sortedBy { it.order }
            .map { it.name }
        val entries = anime.first.filter { it.favorite || it.episodes.isNotEmpty() || it.tracking.isNotEmpty() }
            .map { entry ->
                InteropEntry(
                    title = entry.title.ifBlank { entry.url },
                    animeUrl = entry.url,
                    sourceId = entry.source,
                    ecosystem = "aniyomi",
                    anilistId = null,
                    coverUrl = entry.thumbnailUrl,
                    bannerUrl = null,
                    synopsis = entry.description,
                    genres = entry.genre,
                    statusText = aniyomiIntToStatusText(entry.status),
                    totalEpisodes = null,
                    seasonYear = null,
                    favorite = entry.favorite,
                    dateAdded = entry.dateAdded,
                    categoryNames = entry.categories.mapNotNull { order ->
                        anime.second.firstOrNull { it.order == order }?.name
                            ?: backup.backupCategories.firstOrNull { it.order == order }?.name
                    }.distinct(),
                    episodes = entry.episodes.map {
                        InteropEpisode(
                            url = it.url,
                            name = it.name,
                            number = it.episodeNumber,
                            seen = it.seen,
                            positionSec = it.lastSecondSeen,
                            durationSec = it.totalSeconds,
                            sourceOrder = it.sourceOrder,
                        )
                    },
                    history = entry.history.map {
                        InteropHistory(url = it.url, lastReadMs = it.lastRead, readDurationMs = it.readDuration)
                    },
                    tracking = entry.tracking.map {
                        InteropTracking(
                            trackerWireId = it.syncId,
                            remoteId = if (it.mediaId != 0L) it.mediaId else it.mediaIdInt.toLong(),
                            title = it.title,
                            url = it.trackingUrl,
                            progress = it.lastChapterRead.toInt(),
                            totalEpisodes = it.totalChapters,
                            score = it.score,
                            statusInt = it.status,
                            startedMs = it.startedReadingDate,
                            finishedMs = it.finishedReadingDate,
                        )
                    },
                )
            }
        val skippedManga = backup.backupManga.size
        return InteropRestorePlan(
            format = InteropFormat.ANIYOMI,
            entries = entries,
            categories = categories,
            warnings = emptyList(),
            skippedManga = skippedManga,
        )
    }

    private fun aniyomiIntToStatusText(status: Int): String? = when (status) {
        1 -> "FINISHED"
        2 -> "RELEASING"
        3 -> "NOT_YET_RELEASED"
        4 -> "CANCELLED"
        5 -> "HIATUS"
        else -> null
    }

    // ── Anikku (the manga shape) ──────────────────────────────────────────────

    private fun parseAnikku(backup: InteropProto.AkBackup): InteropRestorePlan {
        val categories = backup.backupCategories.sortedBy { it.order }.map { it.name }
        val entries = backup.backupManga.map { entry ->
            InteropEntry(
                title = entry.title.ifBlank { entry.url },
                animeUrl = entry.url,
                sourceId = entry.source,
                ecosystem = "aniyomi", // anikku sources are aniyomi-format ids
                anilistId = null,
                coverUrl = entry.thumbnailUrl,
                bannerUrl = entry.backgroundUrl,
                synopsis = entry.description,
                genres = entry.genre,
                statusText = aniyomiIntToStatusText(entry.status),
                totalEpisodes = null,
                seasonYear = null,
                favorite = entry.favorite,
                dateAdded = entry.dateAdded,
                categoryNames = entry.categories.mapNotNull { order ->
                    backup.backupCategories.firstOrNull { it.order == order }?.name
                }.distinct(),
                episodes = entry.episodes.map {
                    InteropEpisode(
                        url = it.url,
                        name = it.name,
                        number = it.chapterNumber,
                        seen = it.read,
                        // Anikku writes the resume SECONDS into the chapter's
                        // lastPageRead slot for anime (the same slot it reads back).
                        positionSec = it.lastPageRead,
                        durationSec = 0,
                        sourceOrder = it.sourceOrder,
                    )
                },
                history = entry.history.map {
                    InteropHistory(url = it.url, lastReadMs = it.lastRead, readDurationMs = it.readDuration)
                },
                tracking = entry.tracking.map {
                    InteropTracking(
                        trackerWireId = it.syncId,
                        remoteId = if (it.mediaId != 0L) it.mediaId else it.mediaIdInt.toLong(),
                        title = it.title,
                        url = it.trackingUrl,
                        progress = it.lastChapterRead.toInt(),
                        totalEpisodes = it.totalChapters,
                        score = it.score,
                        statusInt = it.status,
                        startedMs = it.startedReadingDate,
                        finishedMs = it.finishedReadingDate,
                    )
                },
            )
        }
        return InteropRestorePlan(
            format = InteropFormat.ANIKKU,
            entries = entries,
            categories = categories,
            warnings = emptyList(),
        )
    }

    // ── AniList ───────────────────────────────────────────────────────────────

    private fun parseAnilist(collection: JsonObject): InteropRestorePlan {
        val lists = (collection["lists"] as? kotlinx.serialization.json.JsonArray)
            ?: throw InteropParseException("AniList backup has no lists")
        val entries = mutableListOf<InteropEntry>()
        var skippedManga = 0
        val warnings = mutableListOf<String>()

        for (listEl in lists) {
            val list = listEl as? JsonObject ?: continue
            val listName = (list["name"] as? JsonPrimitive)?.content ?: ""
            val listEntries = list["entries"] as? kotlinx.serialization.json.JsonArray ?: continue
            for (entryEl in listEntries) {
                val entryObj = entryEl as? JsonObject ?: continue
                val media = entryObj["media"] as? JsonObject
                val mediaId = entryObj.long("mediaId") ?: media?.long("id") ?: continue
                val type = media?.string("type") ?: "ANIME"
                if (!type.equals("ANIME", ignoreCase = true)) {
                    skippedManga++
                    continue
                }
                val titleObj = media?.get("title") as? JsonObject
                val title = listOf(
                    titleObj?.string("romaji"),
                    titleObj?.string("english"),
                    titleObj?.string("native"),
                ).firstOrNull { !it.isNullOrBlank() } ?: "AniList $mediaId"
                val statusStr = (entryObj.string("status") ?: "PLANNING").uppercase()
                // AniList writes the RAW score on the USER'S point scale — a
                // POINT_10 donor (0-10) must be scaled to our 0-100 column. A
                // genuine POINT_100 score of ≤10 is rare and near-worthless, so
                // anything ≤10 is treated as a 10-scale value.
                val scoreRaw0 = entryObj.double("score") ?: 0.0
                val scoreRaw = if (scoreRaw0 in 0.01..10.0) scoreRaw0 * 10 else scoreRaw0
                val progressEpisodes = (entryObj.int("progress") ?: 0)
                    .coerceAtLeast(media?.int("progress") ?: 0)
                val cover = media?.get("coverImage") as? JsonObject
                val genres = (media?.get("genres") as? kotlinx.serialization.json.JsonArray)
                    ?.mapNotNull { (it as? JsonPrimitive)?.content }
                    .orEmpty()

                entries += InteropEntry(
                    title = title,
                    animeUrl = null,
                    sourceId = null,
                    ecosystem = null,
                    anilistId = mediaId,
                    coverUrl = cover?.string("extraLarge") ?: cover?.string("large") ?: cover?.string("medium"),
                    bannerUrl = media?.string("bannerImage"),
                    synopsis = media?.string("description")?.let { stripHtml(it) },
                    genres = genres,
                    statusText = media?.string("status"),
                    totalEpisodes = media?.int("episodes"),
                    seasonYear = media?.int("seasonYear"),
                    favorite = true,
                    categoryNames = emptyList(),
                    episodes = emptyList(),
                    history = emptyList(),
                    tracking = listOf(
                        InteropTracking(
                            trackerWireId = 2, // AniList
                            remoteId = mediaId,
                            title = title,
                            url = "https://anilist.co/anime/$mediaId",
                            progress = progressEpisodes,
                            totalEpisodes = media?.int("episodes") ?: 0,
                            score = scoreRaw.toFloat(),
                            // The tracker-native AniList anime status int (11/15/2/3/4/16).
                            statusInt = anilistStatusToInt(statusStr),
                            startedMs = fuzzyToMs(entryObj.get("startedAt")),
                            finishedMs = fuzzyToMs(entryObj.get("completedAt")),
                        ),
                    ),
                )
            }
        }
        if (skippedManga > 0) warnings += "$skippedManga manga list entr${if (skippedManga == 1) "y was" else "ies were"} skipped (manga support comes later)"
        return InteropRestorePlan(
            format = InteropFormat.ANILIST,
            entries = entries,
            categories = emptyList(),
            warnings = warnings,
        )
    }

    private fun anilistStatusToInt(status: String): Int = when (status.uppercase()) {
        "CURRENT" -> 11
        "PLANNING" -> 15
        "COMPLETED" -> 2
        "PAUSED" -> 3
        "DROPPED" -> 4
        "REPEATING" -> 16
        else -> 11
    }

    private fun fuzzyToMs(el: JsonElement?): Long = runCatching {
        val obj = el as? JsonObject ?: return 0L
        val y = (obj["year"] as? JsonPrimitive)?.content?.toIntOrNull() ?: return 0L
        val m = (obj["month"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
        val d = (obj["day"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.clear()
        cal.set(y.coerceIn(1960, 2100), (m - 1).coerceIn(0, 11), d.coerceIn(1, 31), 12, 0, 0)
        cal.timeInMillis
    }.getOrDefault(0L)

    private fun stripHtml(text: String): String =
        text.replace(Regex("<br\\s*/?>"), "\n")
            .replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&").replace("&quot;", "\"")
            .replace("&#039;", "'").replace("&lt;", "<").replace("&gt;", ">")
            .trim()

    // ── CloudStream ───────────────────────────────────────────────────────────

    private fun parseCloudstream(root: JsonObject): InteropRestorePlan {
        val datastore = root["datastore"] as? JsonObject
            ?: throw InteropParseException("CloudStream backup has no datastore block")
        val strings = (datastore["_String"] as? JsonObject)
            ?: throw InteropParseException("CloudStream backup has no string keys (no bookmarks to restore)")

        val resumeByShowId = mutableMapOf<Int, Pair<Long, Int>>() // id → (updateTime, episode)
        for ((key, valueEl) in strings) {
            val segments = key.split("/")
            if (segments.size < 3) continue
            if (segments[1] != InteropJson.CS_KEY_RESUME_WATCHING &&
                segments[1] != InteropJson.CS_KEY_RESUME_WATCHING_LEGACY
            ) {
                continue
            }
            val id = segments.last().toIntOrNull() ?: continue
            val obj = runCatching {
                json.parseToJsonElement((valueEl as? JsonPrimitive)?.content ?: "") as? JsonObject
            }.getOrNull() ?: continue
            val episode = obj.int("episode") ?: 0
            val updateTime = obj.long("updateTime") ?: 0L
            if (episode > 0) resumeByShowId[id] = updateTime to episode
        }

        val entries = mutableListOf<InteropEntry>()
        var missingProvider = 0
        for ((key, valueEl) in strings) {
            val segments = key.split("/")
            if (segments.size < 3) continue
            if (segments[1] != InteropJson.CS_KEY_BOOKMARK_DATA) continue
            val obj = runCatching {
                json.parseToJsonElement((valueEl as? JsonPrimitive)?.content ?: "") as? JsonObject
            }.getOrNull() ?: continue

            val name = obj.string("name") ?: continue
            val url = obj.string("url") ?: continue
            if (url.isBlank()) continue
            val apiName = obj.string("apiName") ?: ""
            if (apiName.isBlank()) {
                missingProvider++
                continue // no identity → cannot dedupe or resolve; skip honestly
            }

            val resume = resumeByShowId[InteropJson.csBookmarkId(url)] ?: resumeByShowId[obj.int("id") ?: -1]
            val cover = obj.string("posterUrl")
            val tags = (obj["tags"] as? kotlinx.serialization.json.JsonArray)
                ?.mapNotNull { (it as? JsonPrimitive)?.content }
                .orEmpty()

            entries += InteropEntry(
                title = name,
                animeUrl = url,
                // The CS-bridged synthetic id — the exact formula the app's own
                // CS bridge uses (flag | hash(providerName) & 0xffffffff), so the
                // imported entry dedupes against locally-created CS entries.
                sourceId = csBridgedId(apiName),
                ecosystem = "cloudstream",
                anilistId = null,
                coverUrl = cover,
                bannerUrl = null,
                synopsis = obj.string("plot"),
                genres = tags,
                statusText = null,
                totalEpisodes = null,
                seasonYear = obj.int("year"),
                favorite = true,
                dateAdded = obj.long("bookmarkedTime") ?: 0L,
                categoryNames = listOf("Default"), // CS bookmarks are the CS library
                episodes = if (resume != null) {
                    listOf(
                        InteropEpisode(
                            url = "",
                            name = "Episode ${resume.second}",
                            number = resume.second.toFloat(),
                            seen = false,
                            positionSec = 0,
                            durationSec = 0,
                        ),
                    )
                } else {
                    emptyList()
                },
                history = emptyList(),
                tracking = emptyList(),
            )
        }

        val warnings = buildList {
            if (missingProvider > 0) {
                add("$missingProvider bookmark${if (missingProvider == 1) "" else "s"} without a provider name skipped")
            }
        }
        return InteropRestorePlan(
            format = InteropFormat.CLOUDSTREAM,
            entries = entries,
            categories = listOf("Default"),
            warnings = warnings,
        )
    }

    /** The CS-bridged synthetic source id — see CloudstreamAnimeSourceBridge.idFor. */
    private fun csBridgedId(providerName: String): Long =
        0x4000_0000_0000_0000L or (providerName.hashCode().toLong() and 0xFFFF_FFFFL)

    // ── JSON element helpers ──────────────────────────────────────────────────

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()
    private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.content?.toDoubleOrNull()
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.let {
        if (it is kotlinx.serialization.json.JsonNull) null else it.content
    }
}
