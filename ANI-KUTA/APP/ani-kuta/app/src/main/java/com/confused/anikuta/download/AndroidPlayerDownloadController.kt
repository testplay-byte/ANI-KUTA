package com.confused.anikuta.download

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.content.ContentRepository
import com.confused.anikuta.core.datacache.DataCacheRepository
import com.confused.anikuta.core.download.DownloadContentInfo
import com.confused.anikuta.core.download.DownloadEpisodeInfo
import com.confused.anikuta.core.download.PlayerDownloadController
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.model.SEpisode
import org.koin.core.context.GlobalContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * ROUND 106 (WS-D / D-711): the [PlayerDownloadController]'s :app
 * implementation — MainActivity's proven `handleDownloadEpisode` chain,
 * EXTRACTED into the injectable bridge the player stacks call (the feature
 * modules cannot reach :app's classes; this impl lives where the
 * orchestrator + the content repository already live).
 *
 * The chain is byte-for-byte the details page's: mainId → the content row →
 * the content identity (getContentDetails' cover + the FK fallbacks) → the
 * extension source lookup → the CS-bridged guard → the orchestrator's
 * auto-download engine. The ShowPicker (ASK fallback) branch becomes a
 * BEST-EFFORT pick — the first server's first audio version's first video
 * (the details page's sheet-less auto path has the same behavior; the
 * player has no picker sheet, and the ordered UX is "the download starts").
 */
class AndroidPlayerDownloadController(
    private val orchestrator: DownloadOrchestrator,
    private val contentRepository: ContentRepository,
) : PlayerDownloadController {

    companion object {
        private const val TAG = "Anikuta:PlayerDownload"
    }

    override suspend fun enqueueClassic(
        mainId: String,
        episode: PlayerDownloadController.EpisodeInfo,
    ): PlayerDownloadController.Outcome {
        if (mainId.isBlank()) return PlayerDownloadController.Outcome.NoSource

        return try {
            // 1. The content identity (the details page's exact builder).
            val content = contentRepository.getMainEntryByMainId(mainId)
                ?: return PlayerDownloadController.Outcome.NoSource
            val contentInfo = buildContentInfo(content) {
                contentRepository.getContentDetails(mainId)
            } ?: return PlayerDownloadController.Outcome.NoSource

            // 2. The episode identity (the SEpisode adapter the engine
            //    expects, from the controller's neutral tuple).
            val sEpisode = SEpisode.create().apply {
                url = episode.episodeKey
                name = episode.name ?: ""
                episode_number = episode.episodeNumber
            }

            // 3. The extension source lookup (MainActivity's Koin path).
            val sourceId = content.sourceId
            val source = sourceId?.let {
                GlobalContext.get().get<ExtensionManager>().getSource(it) as?
                    eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
            }

            // 4. The CS-bridged guard — the classic resolver path cannot
            //    serve bridged sources (defense in depth; the player's CS
            //    stack routes through its own flow and never calls this).
            if (source != null && source.isCloudStreamBridged) {
                Logger.w(TAG) { "enqueueClassic — CS-bridged source ${source.name}" }
                return PlayerDownloadController.Outcome.CsBridged
            }

            if (source == null) {
                Logger.w(TAG) { "enqueueClassic — no extension source for mainId=$mainId" }
                return PlayerDownloadController.Outcome.NoSource
            }

            // 5. The description fallback (the metadata cache — the same
            //    lookup handleDownloadEpisode makes; the tuple carries only
            //    the name).
            val effectiveDescription = runCatching {
                GlobalContext.get().get<DataCacheRepository>()
                    .getEpisodeMetadata(mainId)
                    .firstOrNull { it.episodeUrl == episode.episodeKey }
                    ?.description
            }.getOrNull()
            val episodeInfo = DownloadEpisodeInfo(
                episodeKey = episode.episodeKey,
                episodeNumber = episode.episodeNumber,
                // CI run-1 fix: DownloadEpisodeInfo.name is non-null; the
                // controller's neutral tuple carries String? — the empty
                // fallback mirrors MainActivity's `episode.name ?: ""`.
                name = episode.name ?: "",
                description = effectiveDescription,
            )

            // 6. Enqueue through the auto-download engine.
            when (val result = orchestrator.enqueueDownload(
                source = source,
                episode = sEpisode,
                content = contentInfo,
                episodeInfo = episodeInfo,
            )) {
                is EnqueueResult.Success -> {
                    Logger.i(TAG) { "enqueueClassic — enqueued taskId=${result.taskId}" }
                    PlayerDownloadController.Outcome.Started(result.taskId)
                }
                is EnqueueResult.ShowPicker -> {
                    // BEST-EFFORT (the player has no picker sheet): the
                    // first server's first audio version's first video.
                    val video = result.servers.firstOrNull()
                        ?.audioVersions?.firstOrNull()
                        ?.videos?.firstOrNull()
                    if (video != null) {
                        val picked = orchestrator.enqueueSpecific(
                            source = source,
                            episode = sEpisode,
                            content = contentInfo,
                            episodeInfo = episodeInfo,
                            video = video,
                            serverName = result.servers.first().name,
                            audioLabel = "",
                            allServers = result.servers,
                        )
                        if (picked is EnqueueResult.Success) {
                            Logger.i(TAG) { "enqueueClassic — best-effort pick taskId=${picked.taskId}" }
                            PlayerDownloadController.Outcome.Started(picked.taskId)
                        } else {
                            PlayerDownloadController.Outcome.Error("No downloadable source was found")
                        }
                    } else {
                        PlayerDownloadController.Outcome.Error("No video sources available")
                    }
                }
                is EnqueueResult.NoSources -> PlayerDownloadController.Outcome.NoSource
                is EnqueueResult.Error -> PlayerDownloadController.Outcome.Error(result.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "enqueueClassic — exception" }
            PlayerDownloadController.Outcome.Error(e.message ?: "Download failed")
        }
    }

    override suspend fun contentInfoFor(mainId: String): DownloadContentInfo? {
        if (mainId.isBlank()) return null
        return try {
            val content = contentRepository.getMainEntryByMainId(mainId) ?: return null
            buildContentInfo(content) {
                contentRepository.getContentDetails(mainId)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "contentInfoFor — exception" }
            null
        }
    }
}

/**
 * The content-identity builder — MainActivity's handleDownloadEpisode body,
 * factored (the cover + the D-198 FK fallbacks; identical for both the
 * classic enqueue and the CS request path).
 */
internal suspend fun buildContentInfo(
    content: com.confused.anikuta.core.content.ContentRecord,
    detailsLoader: suspend () -> com.confused.anikuta.core.content.ContentDetails?,
): DownloadContentInfo? {
    val details = runCatching { detailsLoader() }.getOrNull()
    val coverUrl = details?.dataCoverUrl ?: details?.extThumbnailUrl
    return DownloadContentInfo(
        mainId = content.mainId,
        contentId = content.contentId,
        title = content.title,
        coverUrl = coverUrl,
        coverColor = null,
        contentFormat = content.contentFormat,
        contentType = content.contentType,
        description = details?.dataSynopsis ?: details?.extDescription,
        dataSourceId = content.dataSourceId,
        systemId = content.systemId,
        extensionRepoId = content.extensionRepoId,
        extensionId = content.extensionId ?: details?.extensionIdLong,
        sourceId = content.sourceId ?: details?.sourceId,
        animeUrl = content.animeUrl ?: details?.animeUrl,
        displaySource = content.displaySource,
        anilistId = details?.anilistId,
    )
}
