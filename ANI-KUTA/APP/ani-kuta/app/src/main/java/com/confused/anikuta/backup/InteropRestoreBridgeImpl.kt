package com.confused.anikuta.backup

import com.confused.anikuta.core.backup.interop.InteropEpisode
import com.confused.anikuta.core.backup.interop.InteropHistory
import com.confused.anikuta.core.backup.interop.InteropResolvedEntry
import com.confused.anikuta.core.backup.interop.InteropRestoreBridge
import com.confused.anikuta.core.backup.interop.InteropStatusMaps
import com.confused.anikuta.core.backup.interop.InteropTracking
import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentDetails
import com.confused.anikuta.core.content.ContentResolver
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.database.AnikutaDatabase
import com.confused.anikuta.core.watchprogress.EpisodeKeys

/**
 * ROUND 137 (D-828): the app-layer interop restore bridge — applies the
 * format-neutral [com.confused.anikuta.core.backup.interop.InteropRestorePlan]
 * through the LIVE content system (the same pattern as
 * [ExtensionBackupBridgeImpl] for the extension payload).
 *
 * Identity resolution (dedupe-safe, never clobbers):
 *  - AniList entries → [ContentResolver.resolveOrCreateForAniList] (dedupes by
 *    data_source_ref_id; the metadata rides the data axis).
 *  - aniyomi/anikku entries → [ContentResolver.resolveOrCreateForExtension]
 *    with the backup's source id (dedupes by extension_id + url).
 *  - CloudStream entries → the same extension path with the CS-bridged
 *    synthetic id minted from the bookmark's apiName — the exact formula the
 *    app's own bridge uses, so imported CS entries dedupe against locally
 *    created ones once the plugin is installed.
 *  - An aniyomi entry carrying an AniList tracker row gets the AniList axis
 *    LINKED onto the same mainId ([ContentResolver.linkAniList]) — one entry,
 *    both axes, exactly the app's own cross-source model.
 *
 * Progress writes are raw `INSERT OR REPLACE` rows (the store's save() is
 * player-shaped — completed=false + the 1-minute gate — which would mangle
 * imported WATCHED rows); every field is preserved from the existing row when
 * one exists, and an imported watched row is organic (userMarked stays false
 * so the D-248 profile statistics count it).
 */
class InteropRestoreBridgeImpl(
    private val database: AnikutaDatabase,
    private val contentRepository: ContentRepository,
    private val contentResolver: ContentResolver,
) : InteropRestoreBridge {

    companion object {
        private const val TAG = "Anikuta:App:InteropBridge"

        /** The CS-bridged synthetic source id (CloudstreamAnimeSourceBridge's formula). */
        const val CS_SOURCE_ID_FLAG: Long = 0x4000_0000_0000_0000L

        fun csBridgedId(providerName: String): Long =
            CS_SOURCE_ID_FLAG or (providerName.hashCode().toLong() and 0xFFFF_FFFFL)
    }

    override fun resolveEntry(
        entry: com.confused.anikuta.core.backup.interop.InteropEntry,
    ): InteropResolvedEntry? {
        val now = System.currentTimeMillis()
        return runCatching {
            // ── The AniList-format path (metadata-only identity) ──
            val anilistId = entry.anilistId?.takeIf { it > 0 && it <= Int.MAX_VALUE }?.toInt()
            if (anilistId != null && entry.animeUrl.isNullOrBlank()) {
                val before = contentRepository.getMainEntryByAniListId(anilistId)
                val mainId = contentResolver.resolveOrCreateForAniList(
                    anilistId = anilistId,
                    title = entry.title,
                    anilistDetail = anilistDetailFor(entry, now),
                )
                return@runCatching InteropResolvedEntry(mainId, created = before == null)
            }

            // ── The extension path (aniyomi / anikku / cloudstream) ──
            val sourceId = entry.sourceId ?: run {
                Logger.w(TAG) { "resolveEntry: '${entry.title}' has no identity — skipped" }
                return@runCatching null
            }
            val url = entry.animeUrl ?: return@runCatching null
            val systemName = when (entry.ecosystem) {
                "cloudstream" -> "cloudstream"
                else -> "aniyomi" // anikku sources are aniyomi-format ids
            }
            val before = contentRepository.getMainEntryByExtension(sourceId, url)
            val mainId = contentResolver.resolveOrCreateForExtension(
                extensionId = sourceId,
                sourceId = sourceId,
                animeUrl = url,
                title = entry.title,
                systemName = systemName,
                extensionPkg = null,
            )
            val created = before == null

            // Metadata onto the extension axis (null-preserving merges).
            val detail = ContentDetails(
                mainId = mainId,
                extensionType = systemName,
                extensionId = sourceId.toString(),
                sourceId = sourceId,
                animeUrl = url,
                extDescription = entry.synopsis,
                extGenres = entry.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
                extStatus = entry.statusText,
                extThumbnailUrl = entry.coverUrl,
                extUpdatedAt = now,
            )
            contentRepository.updateExtensionAxis(detail)

            // An aniyomi entry with an AniList tracker row gets the data axis
            // LINKED onto the same mainId (one entry, both axes — the app's own
            // cross-source model; a metadata-only AniList twin is never minted).
            val anilistTrack = entry.tracking.firstOrNull { it.trackerWireId == 2 }
            if (anilistTrack != null && anilistTrack.remoteId > 0) {
                runCatching {
                    // The data-axis detail MUST carry the ref id — the AniList
                    // lookup JOINs content_details on data_source_ref_id, so a
                    // null here would leave the link invisible to resolution.
                    contentResolver.linkAniList(
                        mainId = mainId,
                        anilistId = anilistTrack.remoteId.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        anilistDetail = anilistDetailFor(
                            entry.copy(anilistId = anilistTrack.remoteId),
                            now,
                        ),
                    )
                }.onFailure { e ->
                    Logger.w(TAG, e) { "linkAniList failed for $mainId: ${e.message}" }
                }
            }
            InteropResolvedEntry(mainId, created)
        }.getOrElse { e ->
            Logger.e(TAG, e) { "resolveEntry failed for '${entry.title}': ${e.message}" }
            null
        }
    }

    /** The data-axis detail for an AniList link/import (null fields are COALESCEd, never wiped). */
    private fun anilistDetailFor(
        entry: com.confused.anikuta.core.backup.interop.InteropEntry,
        now: Long,
    ): ContentDetails = ContentDetails(
        mainId = "",
        dataSourceType = "anilist",
        dataSourceRefId = entry.anilistId?.toString(),
        dataEpisodes = entry.totalEpisodes?.toLong(),
        dataStatus = entry.statusText,
        dataGenres = entry.genres.takeIf { it.isNotEmpty() }?.joinToString(", "),
        dataSynopsis = entry.synopsis,
        dataCoverUrl = entry.coverUrl,
        dataBannerUrl = entry.bannerUrl,
        dataSeasonYear = entry.seasonYear?.toLong(),
        dataUpdatedAt = now,
    )

    override fun ensureCategory(name: String): Long? = runCatching {
        val existing = contentRepository.getAllCategories().firstOrNull { it.name == name }
        existing?.id ?: contentRepository.createCategory(name).takeIf { it > 0 }
    }.getOrElse { e ->
        Logger.w(TAG, e) { "ensureCategory('$name') failed: ${e.message}" }
        null
    }

    override fun addToLibrary(mainId: String, categoryId: Long): Boolean = runCatching {
        // addToCategory is positional but dedupe-UNSAFE (INSERT without a guard)?
        // The UNIQUE index idx_library_item_unique makes re-adds fail silently —
        // check first so the summary counts stay honest.
        val already = contentRepository.getAllLibraryItems().any {
            it.mainId == mainId && it.categoryId == categoryId
        }
        if (already) return@runCatching false
        contentRepository.addToCategory(mainId, categoryId)
        true
    }.getOrElse { e ->
        Logger.w(TAG, e) { "addToLibrary($mainId, $categoryId) failed: ${e.message}" }
        false
    }

    override fun upsertProgress(
        mainId: String,
        episode: InteropEpisode,
        history: InteropHistory?,
    ) = runCatching {
        val epKey = EpisodeKeys.build(mainId, episode.number)
        if (epKey.startsWith("unknown|")) return@runCatching

        val lastRead = history?.lastReadMs ?: 0L
        val existing = database.watchQueries.getWatchProgress(epKey).executeAsOneOrNull()
        val seen = episode.seen
        val completedAt = when {
            seen && history?.lastReadMs != null && history.lastReadMs > 0 -> history.lastReadMs
            seen && existing?.completed_at != null -> existing.completed_at
            else -> existing?.completed_at
        }
        val lastWatchedAt = maxOf(lastRead, existing?.last_watched_at ?: 0L, 0L)
        val playedMs = maxOf(existing?.played_time_ms ?: 0L, history?.readDurationMs ?: 0L)
        val duration = when {
            episode.durationSec > 0 -> episode.durationSec
            existing != null && existing.duration > 0 -> existing.duration
            else -> 0L
        }
        val watchCount = existing?.watch_count?.toInt() ?: if (seen) 1 else 0
        val firstWatchedAt = existing?.first_watched_at
            ?: completedAt?.takeIf { it > 0 }
            ?: lastRead.takeIf { it > 0 && seen }

        database.watchQueries.upsertWatchProgress(
            episode_key = epKey,
            position = maxOf(episode.positionSec, existing?.position ?: 0L),
            duration = duration,
            completed = if (seen || (existing?.completed ?: 0L) == 1L) 1L else 0L,
            completed_at = completedAt,
            last_watched_at = lastWatchedAt,
            main_id = mainId,
            watch_count = watchCount.toLong(),
            first_watched_at = firstWatchedAt,
            auto_mark_suppressed = existing?.auto_mark_suppressed ?: 0L,
            user_marked_watched = existing?.user_marked_watched ?: 0L,
            played_time_ms = playedMs,
        )
    }.getOrElse { e ->
        Logger.w(TAG, e) { "upsertProgress($mainId, ep ${episode.number}) failed: ${e.message}" }
    }

    override fun upsertTracking(
        mainId: String,
        tracking: InteropTracking,
    ) = runCatching {
        // Only AniList rows apply today (the app's only tracker); the others are
        // preserved in the file and simply not imported.
        if (tracking.trackerWireId != 2 || tracking.remoteId <= 0) return@runCatching
        val now = System.currentTimeMillis()
        database.trackQueries.upsertTrackEntry(
            main_id = mainId,
            tracker_type = "anilist",
            tracker_id = tracking.remoteId,
            status = InteropStatusMaps.aniyomiAnilistIntToTrackStatus(tracking.statusInt),
            score = tracking.score.toInt().coerceIn(0, 100).toLong(),
            progress = tracking.progress.coerceAtLeast(0).toLong(),
            total_episodes = tracking.totalEpisodes.takeIf { it > 0 }?.toLong(),
            list_id = null,
            started_at = tracking.startedMs.takeIf { it > 0 },
            completed_at = tracking.finishedMs.takeIf { it > 0 },
            updated_at = now,
        )
        // The user TRACKED this in the donor app — restore the tracking intent
        // (the round-102 contract: nothing relays unless the intent is set).
        database.trackQueries.upsertTrackingState(
            main_id = mainId,
            tracker_type = "anilist",
            tracked = 1,
            updated_at = now,
        )
    }.getOrElse { e ->
        Logger.w(TAG, e) { "upsertTracking($mainId, wire=${tracking.trackerWireId}) failed: ${e.message}" }
    }
}
