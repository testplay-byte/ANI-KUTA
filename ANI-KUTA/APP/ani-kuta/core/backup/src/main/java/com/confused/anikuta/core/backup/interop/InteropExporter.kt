package com.confused.anikuta.core.backup.interop

import app.cash.sqldelight.db.SqlDriver
import kotlinx.serialization.protobuf.ProtoBuf
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 * ROUND 137 (D-828): the interop EXPORTER — ANI-KUTA's library data rebuilt
 * into the other apps' backup formats.
 *
 * The local data is gathered once from the raw driver (the same
 * PRAGMA-informed approach BackupManager uses) into a [Snapshot]; each
 * format writer then maps the snapshot onto the wire models from
 * [InteropProto] / [InteropJson]. The LOCAL database is never touched —
 * only the file payload is restructured (the round-137 order).
 *
 * Per-format coverage (faithful to what each format can carry):
 *  - ANIYOMI: library entries with a NON-CloudStream extension source
 *    (+ episodes from the episode cache's real URLs — a foreign reader would
 *    turn synthetic URLs into junk episode rows, so entries without cached
 *    episode urls export as uninitialized favorites, exactly how Aniyomi
 *    itself exports entries it never fetched), watch history, AniList
 *    tracking. CloudStream-bridged + sourceless entries are SKIPPED (counted).
 *  - ANIKKU: the same entries riding the Mihon manga wire shape.
 *  - ANILIST: only AniList-linked entries (list status / score / progress).
 *  - CLOUDSTREAM: only CloudStream-bridged entries (bookmarks + watched flags
 *    + resume pointers; fine-grained per-episode positions have no portable
 *    key in the CS3 backup and are documented out).
 */
class InteropExporter(private val driver: SqlDriver) {

    // ── The snapshot ──────────────────────────────────────────────────────────

    data class ExportEntry(
        val mainId: String,
        val title: String,
        val createdAt: Long,
        val extensionId: Long?,
        val systemName: String?,
        val animeUrl: String?,
        val dataSourceType: String?,
        val dataSourceRefId: String?,
        val dataEpisodes: Int?,
        val dataStatus: String?,
        val dataGenres: String?,
        val dataSynopsis: String?,
        val dataCoverUrl: String?,
        val dataBannerUrl: String?,
        val dataSeasonYear: Int?,
        val extStatus: String?,
        val extDescription: String?,
        val extGenres: String?,
        val extThumbnailUrl: String?,
        val extYear: Int?,
    )

    data class ProgressRow(
        val episodeKey: String,
        val position: Long,
        val duration: Long,
        val completed: Boolean,
        val completedAt: Long?,
        val lastWatchedAt: Long,
        val playedTimeMs: Long,
        val userMarkedWatched: Boolean,
    )

    data class EpisodeCacheRow(
        val mainId: String,
        val episodeNumber: Int,
        val title: String?,
        val episodeUrl: String?,
    )

    data class TrackRow(
        val mainId: String,
        val trackerType: String,
        val trackerId: Long,
        val status: String,
        val score: Int?,
        val progress: Int,
        val totalEpisodes: Int?,
        val startedAt: Long?,
        val completedAt: Long?,
    )

    data class Snapshot(
        val entries: List<ExportEntry>,
        /** Category id → name, in DISPLAY ORDER (index = the wire's order slot). */
        val categories: List<Pair<Long, String>>,
        /** mainId → the category ids it sits in. */
        val itemCategories: Map<String, List<Long>>,
        val progressByMain: Map<String, List<ProgressRow>>,
        val episodeCache: List<EpisodeCacheRow>,
        val tracks: List<TrackRow>,
    )

    // ── Public API ────────────────────────────────────────────────────────────

    data class ExportResult(
        val bytes: ByteArray,
        val suggestedFileName: String,
        val entriesWritten: Int,
        val categoriesWritten: Int,
        val episodesWritten: Int,
        val trackingRows: Int,
        val entriesSkipped: Int,
    ) {
        fun toSummary(format: InteropFormat, fileName: String) = InteropExportSummary(
            format = format,
            fileName = fileName,
            entriesWritten = entriesWritten,
            categoriesWritten = categoriesWritten,
            episodesWritten = episodesWritten,
            trackingRows = trackingRows,
            entriesSkipped = entriesSkipped,
        )
    }

    /**
     * @param sourceNameResolver a Long source id → display name (the app's
     *   extension catalog; "" is fine when unknown — Aniyomi shows the id).
     * @param providerResolver a CS-bridged id → the plugin's (apiName, mainUrl)
     *   pair (the app's installed-plugin reverse hash; null when the plugin is
     *   absent — the mainUrl lets the bookmark ids strip the site root exactly
     *   like CS's own minting).
     */
    fun export(
        format: InteropFormat,
        snapshot: Snapshot = collectSnapshot(),
        sourceNameResolver: (Long) -> String = { "" },
        providerResolver: (Long) -> Pair<String, String?>? = { null },
    ): ExportResult = when (format) {
        InteropFormat.ANIYOMI -> exportAniyomi(snapshot, sourceNameResolver)
        InteropFormat.ANIKKU -> exportAnikku(snapshot)
        InteropFormat.ANILIST -> exportAnilist(snapshot)
        InteropFormat.CLOUDSTREAM -> exportCloudstream(snapshot, providerResolver)
        InteropFormat.ANIKUTA -> error("ANI-KUTA backups go through BackupManager, not the interop exporter")
    }

    // ── Snapshot collection ───────────────────────────────────────────────────

    fun collectSnapshot(): Snapshot {
        val entries = query(
            """
            SELECT m.main_id, m.title, m.created_at, m.extension_id, m.anime_url,
                   s.name AS system_name,
                   d.data_source_type, d.data_source_ref_id, d.data_episodes, d.data_status,
                   d.data_genres, d.data_synopsis, d.data_cover_url, d.data_banner_url,
                   d.data_season_year, d.ext_status, d.ext_description, d.ext_genres,
                   d.ext_thumbnail_url, d.ext_extra_json
            FROM main_entry m
            JOIN (SELECT main_id, MAX(added_at) AS added_at FROM library_item GROUP BY main_id) li
              ON li.main_id = m.main_id
            LEFT JOIN system s ON m.system_id = s.id
            LEFT JOIN content_details d ON d.main_id = m.main_id
            ORDER BY m.created_at ASC
            """.trimIndent(),
        ) { c ->
            val extraJson = c.getString(19)
            ExportEntry(
                mainId = c.getString(0) ?: "",
                title = c.getString(1) ?: "",
                createdAt = c.getLong(2) ?: 0,
                extensionId = c.getLong(3),
                systemName = c.getString(5),
                animeUrl = c.getString(4),
                dataSourceType = c.getString(6),
                dataSourceRefId = c.getString(7),
                dataEpisodes = c.getLong(8)?.toInt(),
                dataStatus = c.getString(9),
                dataGenres = c.getString(10),
                dataSynopsis = c.getString(11),
                dataCoverUrl = c.getString(12),
                dataBannerUrl = c.getString(13),
                dataSeasonYear = c.getLong(14)?.toInt(),
                extStatus = c.getString(15),
                extDescription = c.getString(16),
                extGenres = c.getString(17),
                extThumbnailUrl = c.getString(18),
                extYear = parseExtraYear(extraJson),
            )
        }

        val categories = query(
            "SELECT id, name FROM library_category ORDER BY display_order ASC, created_at ASC",
        ) { c -> (c.getLong(0) ?: 0L) to (c.getString(1) ?: "") }

        val itemCategories = buildMap {
            query(
                "SELECT main_id, category_id FROM library_item WHERE category_id IS NOT NULL",
            ) { c ->
                val main = c.getString(0) ?: return@query
                val cat = c.getLong(1) ?: return@query
                put(main, (get(main) ?: emptyList()) + cat)
            }
        }

        val progressByMain = buildMap {
            query(
                """
                SELECT main_id, episode_key, position, duration, completed, completed_at,
                       last_watched_at, played_time_ms, user_marked_watched
                FROM watch_progress
                WHERE main_id IS NOT NULL
                """.trimIndent(),
            ) { c ->
                val main = c.getString(0) ?: return@query
                val row = ProgressRow(
                    episodeKey = c.getString(1) ?: "",
                    position = c.getLong(2) ?: 0,
                    duration = c.getLong(3) ?: 0,
                    completed = (c.getLong(4) ?: 0) == 1L,
                    completedAt = c.getLong(5),
                    lastWatchedAt = c.getLong(6) ?: 0,
                    playedTimeMs = c.getLong(7) ?: 0,
                    userMarkedWatched = (c.getLong(8) ?: 0) == 1L,
                )
                put(main, (get(main) ?: emptyList()) + row)
            }
        }

        val episodeCache = query(
            """
            SELECT main_id, episode_number, title, episode_url FROM data_cache_episode
            WHERE episode_url IS NOT NULL AND episode_url <> ''
            """.trimIndent(),
        ) { c ->
            EpisodeCacheRow(
                mainId = c.getString(0) ?: "",
                episodeNumber = (c.getLong(1) ?: 0).toInt(),
                title = c.getString(2),
                episodeUrl = c.getString(3),
            )
        }

        val tracks = query(
            """
            SELECT main_id, tracker_type, tracker_id, status, score, progress, total_episodes,
                   started_at, completed_at
            FROM track_entry
            """.trimIndent(),
        ) { c ->
            TrackRow(
                mainId = c.getString(0) ?: "",
                trackerType = c.getString(1) ?: "",
                trackerId = c.getLong(2) ?: 0,
                status = c.getString(3) ?: "",
                score = c.getLong(4)?.toInt(),
                progress = (c.getLong(5) ?: 0).toInt(),
                totalEpisodes = c.getLong(6)?.toInt(),
                startedAt = c.getLong(7),
                completedAt = c.getLong(8),
            )
        }

        return Snapshot(entries, categories, itemCategories, progressByMain, episodeCache, tracks)
    }

    private fun parseExtraYear(json: String?): Int? = runCatching {
        val obj = kotlinx.serialization.json.Json.parseToJsonElement(json ?: return null)
            as? kotlinx.serialization.json.JsonObject ?: return null
        (obj["year"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
            ?: (obj["seasonYear"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toIntOrNull()
    }.getOrNull()

    private fun <T> query(sql: String, map: (app.cash.sqldelight.db.SqlCursor) -> T): List<T> {
        val rows = mutableListOf<T>()
        driver.executeQuery(
            null,
            sql,
            { cursor ->
                while (cursor.next().value) {
                    rows.add(map(cursor))
                }
                app.cash.sqldelight.db.QueryResult.Value(Unit)
            },
            0,
            null,
        )
        return rows
    }

    // ── Mapping helpers ───────────────────────────────────────────────────────

    private fun splitGenres(joined: String?): List<String> =
        joined?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

    /** Aniyomi AnimeStatus ints: UNKNOWN=0, FINISHED=1, RELEASING=2, NOT_YET=3, CANCELLED=4, HIATUS=5. */
    private fun statusToAniyomiInt(status: String?): Int = when (status?.trim()?.uppercase()) {
        "FINISHED", "COMPLETED", "FINISHED_AIRING" -> 1
        "RELEASING", "ONGOING", "AIRING", "CURRENTLY_AIRING" -> 2
        "NOT_YET_RELEASED", "UPCOMING", "NOT_YET_AIRED" -> 3
        "CANCELLED", "CANCELED" -> 4
        "HIATUS" -> 5
        else -> 0
    }

    /**
     * Mihon MangaStatus ints (the Anikku wire): ONGOING=1, COMPLETED=2,
     * CANCELLED=4. HIATUS has NO verified wire value (newer Mihon moved it —
     * an unverified label is worse than none) so it maps to UNKNOWN=0.
     */
    private fun statusToMangaInt(status: String?): Int = when (status?.trim()?.uppercase()) {
        "RELEASING", "ONGOING", "AIRING", "CURRENTLY_AIRING" -> 1
        "FINISHED", "COMPLETED", "FINISHED_AIRING" -> 2
        "CANCELLED", "CANCELED" -> 4
        else -> 0
    }

    /** ANI-KUTA TrackStatus → Aniyomi's AniList anime status ints (the shared map). */
    private fun trackStatusToAniyomiAnilistInt(status: String): Int =
        InteropStatusMaps.trackStatusToAniyomiAnilistInt(status)

    /** True when the (main, episodeNumber) was watched per the same rule the app uses for progress. */
    private fun highestWatchedNumber(rows: List<ProgressRow>): Int {
        var highest = 0
        for (row in rows) {
            val num = row.episodeKey.substringAfterLast('|').toIntOrNull() ?: continue
            val counts = (row.completed && !row.userMarkedWatched) || row.userMarkedWatched
            if (counts && num > highest) highest = num
        }
        return highest
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { raw ->
        GZIPOutputStream(raw).use { it.write(bytes) }
        raw.toByteArray()
    }

    private fun timestamp(): String =
        java.text.SimpleDateFormat("yyyyMMdd_HHmm", java.util.Locale.US)
            .format(java.util.Date(System.currentTimeMillis()))

    // ── ANIYOMI ───────────────────────────────────────────────────────────────

    private fun exportAniyomi(
        snapshot: Snapshot,
        sourceNameResolver: (Long) -> String,
    ): ExportResult {
        val cacheByUrl = snapshot.episodeCache
            .groupBy { it.mainId }
            .mapValues { (_, rows) -> rows.associate { it.episodeNumber to it } }
        val tracksByMain = snapshot.tracks.groupBy { it.mainId }

        var skipped = 0
        var episodeCount = 0
        var trackCount = 0

        val anime = snapshot.entries.mapNotNull { entry ->
            val extId = entry.extensionId
            val isCs = extId != null && extId.isCloudstreamBridged()
            if (extId == null || isCs || entry.animeUrl.isNullOrBlank()) {
                skipped++
                return@mapNotNull null
            }
            val progress = snapshot.progressByMain[entry.mainId].orEmpty().sortedBy {
                it.episodeKey.substringAfterLast('|').toIntOrNull() ?: Int.MAX_VALUE
            }
            val cache = cacheByUrl[entry.mainId].orEmpty()

            val episodes = buildList {
                var order = 0L
                for (row in progress) {
                    val num = row.episodeKey.substringAfterLast('|').toIntOrNull() ?: continue
                    if (num <= 0) continue
                    val cached = cache[num] ?: continue // no real URL → never emit a synthetic one
                    add(
                        InteropProto.AyBackupEpisode(
                            url = cached.episodeUrl ?: continue,
                            name = cached.title ?: "Episode $num",
                            seen = row.completed,
                            lastSecondSeen = row.position,
                            totalSeconds = row.duration,
                            episodeNumber = num.toFloat(),
                            sourceOrder = order++,
                        ),
                    )
                }
            }
            episodeCount += episodes.size

            val history = buildList {
                for (row in progress) {
                    if (!row.completed && row.lastWatchedAt <= 0) continue
                    val num = row.episodeKey.substringAfterLast('|').toIntOrNull() ?: continue
                    val url = cache[num]?.episodeUrl ?: continue
                    add(
                        InteropProto.BackupAnimeHistory(
                            url = url,
                            lastRead = maxOf(row.lastWatchedAt, row.completedAt ?: 0),
                            readDuration = row.playedTimeMs,
                        ),
                    )
                }
            }

            val tracking = tracksByMain[entry.mainId].orEmpty()
                .filter { it.trackerType.equals("anilist", ignoreCase = true) }
            trackCount += tracking.size

            InteropProto.AyBackupAnime(
                source = extId,
                url = entry.animeUrl,
                title = entry.title,
                description = entry.dataSynopsis ?: entry.extDescription,
                genre = splitGenres(entry.dataGenres).ifEmpty { splitGenres(entry.extGenres) },
                status = statusToAniyomiInt(entry.dataStatus ?: entry.extStatus),
                thumbnailUrl = entry.dataCoverUrl ?: entry.extThumbnailUrl,
                dateAdded = entry.createdAt,
                episodes = episodes,
                categories = categoryOrders(entry.mainId, snapshot),
                tracking = tracking.map {
                    InteropProto.BackupTracking(
                        syncId = 2, // AniList (TrackerManager.ANILIST)
                        libraryId = 0,
                        mediaIdInt = it.trackerId.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                        trackingUrl = "https://anilist.co/anime/${it.trackerId}",
                        title = entry.title,
                        lastChapterRead = it.progress.toFloat(),
                        totalChapters = it.totalEpisodes ?: entry.dataEpisodes ?: 0,
                        score = (it.score ?: 0).toFloat(),
                        status = trackStatusToAniyomiAnilistInt(it.status),
                        startedReadingDate = it.startedAt ?: 0,
                        finishedReadingDate = it.completedAt ?: 0,
                        mediaId = it.trackerId,
                    )
                },
                favorite = true,
                history = history,
            )
        }

        val sourceIds = anime.map { it.source }.distinct()
        val backup = InteropProto.AyBackup(
            backupCategories = snapshot.categories.mapIndexed { idx, (_, name) ->
                InteropProto.BackupCategory(name = name, order = idx.toLong())
            },
            backupSources = sourceIds.map {
                InteropProto.BackupSource(name = sourceNameResolver(it), sourceId = it)
            },
            backupAnime = anime,
            backupAnimeCategories = snapshot.categories.mapIndexed { idx, (_, name) ->
                InteropProto.BackupCategory(name = name, order = idx.toLong())
            },
            backupAnimeSources = sourceIds.map {
                InteropProto.BackupSource(name = sourceNameResolver(it), sourceId = it)
            },
            // Field 500 must be EMITTED false — see AyBackup's KDoc wire gotcha.
            isLegacy = false,
        )
        val bytes = gzip(ProtoBuf.encodeToByteArray(InteropProto.AyBackup.serializer(), backup))
        return ExportResult(
            bytes = bytes,
            suggestedFileName = "anikuta-aniyomi-${timestamp()}${InteropFormat.ANIYOMI.fileExtension}",
            entriesWritten = anime.size,
            categoriesWritten = snapshot.categories.size,
            episodesWritten = episodeCount,
            trackingRows = trackCount,
            entriesSkipped = skipped,
        )
    }

    // ── ANIKKU (the manga wire shape) ─────────────────────────────────────────

    private fun exportAnikku(snapshot: Snapshot): ExportResult {
        val cacheByUrl = snapshot.episodeCache
            .groupBy { it.mainId }
            .mapValues { (_, rows) -> rows.associate { it.episodeNumber to it } }
        val tracksByMain = snapshot.tracks.groupBy { it.mainId }

        var skipped = 0
        var episodeCount = 0

        val manga = snapshot.entries.mapNotNull { entry ->
            val extId = entry.extensionId
            val isCs = extId != null && extId.isCloudstreamBridged()
            if (extId == null || isCs || entry.animeUrl.isNullOrBlank()) {
                skipped++
                return@mapNotNull null
            }
            val progress = snapshot.progressByMain[entry.mainId].orEmpty().sortedBy {
                it.episodeKey.substringAfterLast('|').toIntOrNull() ?: Int.MAX_VALUE
            }
            val cache = cacheByUrl[entry.mainId].orEmpty()

            val chapters = buildList {
                var order = 0L
                for (row in progress) {
                    val num = row.episodeKey.substringAfterLast('|').toIntOrNull() ?: continue
                    if (num <= 0) continue
                    val url = cache[num]?.episodeUrl ?: continue
                    add(
                        InteropProto.ChapterShape(
                            url = url,
                            name = cache[num]?.title ?: "Episode $num",
                            read = row.completed,
                            lastPageRead = row.position,
                            chapterNumber = num.toFloat(),
                            sourceOrder = order++,
                        ),
                    )
                }
            }
            episodeCount += chapters.size

            val history = buildList {
                for (row in progress) {
                    if (!row.completed && row.lastWatchedAt <= 0) continue
                    val num = row.episodeKey.substringAfterLast('|').toIntOrNull() ?: continue
                    val url = cache[num]?.episodeUrl ?: continue
                    add(
                        InteropProto.BackupHistory(
                            url = url,
                            lastRead = maxOf(row.lastWatchedAt, row.completedAt ?: 0),
                            readDuration = row.playedTimeMs,
                        ),
                    )
                }
            }

            val tracking = tracksByMain[entry.mainId].orEmpty()
                .filter { it.trackerType.equals("anilist", ignoreCase = true) }

            InteropProto.MangaShape(
                source = extId,
                url = entry.animeUrl,
                title = entry.title,
                description = entry.dataSynopsis ?: entry.extDescription,
                genre = splitGenres(entry.dataGenres).ifEmpty { splitGenres(entry.extGenres) },
                status = statusToMangaInt(entry.dataStatus ?: entry.extStatus),
                thumbnailUrl = entry.dataCoverUrl ?: entry.extThumbnailUrl,
                dateAdded = entry.createdAt,
                episodes = chapters,
                categories = categoryOrders(entry.mainId, snapshot),
                tracking = tracking.map {
                    InteropProto.BackupTracking(
                        syncId = 2,
                        mediaIdInt = it.trackerId.coerceIn(0, Int.MAX_VALUE.toLong()).toInt(),
                        trackingUrl = "https://anilist.co/anime/${it.trackerId}",
                        title = entry.title,
                        lastChapterRead = it.progress.toFloat(),
                        totalChapters = it.totalEpisodes ?: entry.dataEpisodes ?: 0,
                        score = (it.score ?: 0).toFloat(),
                        status = trackStatusToAniyomiAnilistInt(it.status),
                        startedReadingDate = it.startedAt ?: 0,
                        finishedReadingDate = it.completedAt ?: 0,
                        mediaId = it.trackerId,
                    )
                },
                favorite = true,
                history = history,
            )
        }

        val backup = InteropProto.AkBackup(
            backupCategories = snapshot.categories.mapIndexed { idx, (_, name) ->
                InteropProto.BackupCategory(name = name, order = idx.toLong())
            },
            backupSources = manga.map { it.source }.distinct().map {
                InteropProto.BackupSource(sourceId = it)
            },
            backupManga = manga,
            // The isLegacy emission gotcha — see AkBackup's KDoc.
            isLegacy = false,
        )
        val bytes = gzip(ProtoBuf.encodeToByteArray(InteropProto.AkBackup.serializer(), backup))
        return ExportResult(
            bytes = bytes,
            suggestedFileName = "anikuta-anikku-${timestamp()}${InteropFormat.ANIKKU.fileExtension}",
            entriesWritten = manga.size,
            categoriesWritten = snapshot.categories.size,
            episodesWritten = episodeCount,
            trackingRows = manga.sumOf { it.tracking.size },
            entriesSkipped = skipped,
        )
    }

    // ── ANILIST ───────────────────────────────────────────────────────────────

    private fun exportAnilist(snapshot: Snapshot): ExportResult {
        val tracksByMain = snapshot.tracks.groupBy { it.mainId }
        var skipped = 0
        var trackCount = 0

        data class Bucket(val listName: String) {
            val entries = mutableListOf<InteropJson.AlListEntry>()
        }
        val buckets = linkedMapOf(
            "Watching" to Bucket("Watching"),
            "Completed" to Bucket("Completed"),
            "Paused" to Bucket("Paused"),
            "Dropped" to Bucket("Dropped"),
            "Planning" to Bucket("Planning"),
            "Repeating" to Bucket("Repeating"),
        )

        for (entry in snapshot.entries) {
            if (!entry.dataSourceType.equals("anilist", ignoreCase = true)) {
                skipped++
                continue
            }
            val anilistId = entry.dataSourceRefId?.toLongOrNull()
            if (anilistId == null || anilistId <= 0) {
                skipped++
                continue
            }
            val progressRows = snapshot.progressByMain[entry.mainId].orEmpty()
            val highest = highestWatchedNumber(progressRows)
            val track = tracksByMain[entry.mainId].orEmpty()
                .firstOrNull { it.trackerType.equals("anilist", ignoreCase = true) }

            val status = track?.let { InteropJson.trackStatusToAnilist(it.status) }
                ?: if (highest > 0) "CURRENT" else "PLANNING"
            val score = track?.score?.toDouble() ?: 0.0
            val progress = track?.progress?.takeIf { it > 0 } ?: highest
            if (track != null) trackCount++

            val bucket = when (status) {
                "CURRENT" -> buckets["Watching"]!!
                "COMPLETED" -> buckets["Completed"]!!
                "PAUSED" -> buckets["Paused"]!!
                "DROPPED" -> buckets["Dropped"]!!
                "REPEATING" -> buckets["Repeating"]!!
                else -> buckets["Planning"]!!
            }
            bucket.entries += InteropJson.AlListEntry(
                mediaId = anilistId,
                status = status,
                score = score,
                progress = progress,
                repeat = if (status == "REPEATING") 1 else 0,
                notes = null,
                startedAt = track?.startedAt?.let { msFromMillis(it) } ?: InteropJson.AlFuzzyDate(),
                completedAt = track?.completedAt?.let { msFromMillis(it) } ?: InteropJson.AlFuzzyDate(),
                updatedAt = System.currentTimeMillis() / 1000,
                media = InteropJson.AlMedia(
                    id = anilistId,
                    title = InteropJson.AlTitle(romaji = entry.title, english = entry.title),
                    type = "ANIME",
                    status = (entry.dataStatus ?: entry.extStatus)?.trim()?.uppercase()
                        ?.takeIf { it == "RELEASING" || it == "FINISHED" || it == "NOT_YET_RELEASED" }
                        ?: "RELEASING",
                    episodes = entry.dataEpisodes,
                    genres = splitGenres(entry.dataGenres).ifEmpty { splitGenres(entry.extGenres) },
                    description = entry.dataSynopsis ?: entry.extDescription,
                    coverImage = InteropJson.AlCover(
                        extraLarge = entry.dataCoverUrl ?: entry.extThumbnailUrl,
                        large = entry.dataCoverUrl ?: entry.extThumbnailUrl,
                    ),
                    bannerImage = entry.dataBannerUrl,
                    seasonYear = entry.dataSeasonYear ?: entry.extYear,
                ),
            )
        }

        val collection = InteropJson.AlMediaListCollection(
            lists = buckets.values.filter { it.entries.isNotEmpty() }.map {
                InteropJson.AlList(name = it.listName, entries = it.entries)
            },
        )
        val json = kotlinx.serialization.json.Json {
            encodeDefaults = true
            prettyPrint = true
            ignoreUnknownKeys = true
        }
        val bytes = json.encodeToString(InteropJson.AlMediaListCollection.serializer(), collection)
            .toByteArray(Charsets.UTF_8)
        val written = buckets.values.sumOf { it.entries.size }
        return ExportResult(
            bytes = bytes,
            suggestedFileName = "anikuta-anilist-${timestamp()}.json",
            entriesWritten = written,
            categoriesWritten = 0, // AniList has no category concept
            episodesWritten = 0,
            trackingRows = trackCount,
            entriesSkipped = skipped,
        )
    }

    /** A track start/finish epoch-ms → AniList's {year, month, day} (UTC, faithful enough for a list import). */
    private fun msFromMillis(ms: Long): InteropJson.AlFuzzyDate {
        if (ms <= 0) return InteropJson.AlFuzzyDate()
        val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        cal.timeInMillis = ms
        return InteropJson.AlFuzzyDate(
            year = cal.get(java.util.Calendar.YEAR),
            month = cal.get(java.util.Calendar.MONTH) + 1,
            day = cal.get(java.util.Calendar.DAY_OF_MONTH),
        )
    }

    // ── CLOUDSTREAM ───────────────────────────────────────────────────────────

    private fun exportCloudstream(
        snapshot: Snapshot,
        providerResolver: (Long) -> Pair<String, String?>?,
    ): ExportResult {
        val cacheCountByMain = snapshot.episodeCache
            .groupBy { it.mainId }
            .mapValues { (_, rows) -> rows.map { it.episodeNumber }.toSet() }

        var skipped = 0
        val bookmarks = mutableListOf<Pair<String, InteropJson.CsBookmarkedData>>()
        // The library watch state — an INT per show (WatchType.internalId),
        // riding the _Int bucket like upstream's setResultWatchState writes.
        val watchStates = mutableListOf<Pair<String, Int>>()
        // The resume pointers — JSON STRINGS under result_resume_watching_2
        // (the CURRENT upstream key; the retired one is import-only).
        val resumes = mutableListOf<Pair<String, String>>()

        for (entry in snapshot.entries) {
            val extId = entry.extensionId
            if (extId == null || !extId.isCloudstreamBridged() || entry.animeUrl.isNullOrBlank()) {
                skipped++
                continue
            }
            val provider = providerResolver(extId)
            val id = InteropJson.csBookmarkId(entry.animeUrl, provider?.second)
            val progress = snapshot.progressByMain[entry.mainId].orEmpty()
            val latest = progress.maxByOrNull { it.lastWatchedAt }

            bookmarks += "0/${InteropJson.CS_KEY_BOOKMARK_DATA}/$id" to InteropJson.CsBookmarkedData(
                bookmarkedTime = entry.createdAt,
                id = id,
                latestUpdatedTime = latest?.lastWatchedAt ?: entry.createdAt,
                name = entry.title,
                url = entry.animeUrl,
                apiName = provider?.first ?: "",
                type = InteropJson.CS_TYPE_ANIME,
                posterUrl = entry.dataCoverUrl ?: entry.extThumbnailUrl,
                year = entry.dataSeasonYear ?: entry.extYear,
                plot = entry.dataSynopsis ?: entry.extDescription,
                tags = splitGenres(entry.dataGenres).ifEmpty { splitGenres(entry.extGenres) },
            )

            // The watched flag: CS marks a show watched as a whole — all known
            // episodes completed (and at least one known). COMPLETED = 1.
            val known = cacheCountByMain[entry.mainId].orEmpty()
            if (known.isNotEmpty()) {
                val completedNums = progress.filter { it.completed }
                    .mapNotNull { it.episodeKey.substringAfterLast('|').toIntOrNull() }
                    .toSet()
                if (known.all { it in completedNums }) {
                    watchStates += "0/${InteropJson.CS_KEY_WATCH_STATE}/$id" to InteropJson.CS_WATCH_STATE_COMPLETED
                }
            }

            // The resume pointer: the latest touched episode (CS's binge picker),
            // serialized as a JSON STRING like every CS DataStore value.
            val latestWatchedAt = latest?.lastWatchedAt ?: 0L
            val resumeEp = latest?.episodeKey?.substringAfterLast('|')?.toIntOrNull() ?: 0
            if (resumeEp > 0 && latestWatchedAt > 0) {
                val resumeJson = kotlinx.serialization.json.Json { encodeDefaults = true }
                    .encodeToString(
                        InteropJson.CsResumeWatching.serializer(),
                        InteropJson.CsResumeWatching(
                            parentId = id,
                            episode = resumeEp,
                            updateTime = latestWatchedAt,
                        ),
                    )
                resumes += "0/${InteropJson.CS_KEY_RESUME_WATCHING}/$id" to resumeJson
            }
        }

        // The nested bookmark JSON must carry EVERY field the upstream decoder
        // needs — their BookmarkedData has NO defaults, so an ABSENT nullable
        // field can fail the strict parse and silently drop the bookmark. Nulls
        // are written EXPLICITLY (the default explicitNulls=true).
        val bookmarkJson = kotlinx.serialization.json.Json { encodeDefaults = true }
        val bookmarkStrings = bookmarks.associate { (key, data) ->
            key to bookmarkJson.encodeToString(InteropJson.CsBookmarkedData.serializer(), data)
        }

        val file = InteropJson.CsBackupFile(
            datastore = InteropJson.CsBackupVars(
                _Int = if (watchStates.isEmpty()) null else watchStates.toMap(),
                _String = (bookmarkStrings + resumes.toMap()).takeIf { it.isNotEmpty() },
            ),
            settings = InteropJson.CsBackupVars(),
        )
        val json = kotlinx.serialization.json.Json { encodeDefaults = true }
        val bytes = json.encodeToString(InteropJson.CsBackupFile.serializer(), file)
            .toByteArray(Charsets.UTF_8)
        return ExportResult(
            bytes = bytes,
            suggestedFileName = "anikuta-cs3-${'$'}{timestamp()}.json",
            entriesWritten = bookmarks.size,
            categoriesWritten = 0,
            episodesWritten = resumes.size,
            trackingRows = 0,
            entriesSkipped = skipped,
        )
    }

// ── Shared ────────────────────────────────────────────────────────────────

    /** The wire's category-order list for a library item (index = display order). */
    private fun categoryOrders(mainId: String, snapshot: Snapshot): List<Long> {
        val ids = snapshot.itemCategories[mainId].orEmpty()
        if (ids.isEmpty()) return emptyList()
        val orderOf = snapshot.categories.withIndex().associate { (idx, pair) -> pair.first to idx.toLong() }
        return ids.mapNotNull { orderOf[it] }.sorted()
    }

    private fun Long.isCloudstreamBridged(): Boolean =
        (this and 0x4000_0000_0000_0000L) != 0L
}
