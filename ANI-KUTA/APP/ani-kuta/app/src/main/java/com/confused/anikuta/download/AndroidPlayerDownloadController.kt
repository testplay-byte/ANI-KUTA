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
 * ROUND 109 (D-721): THE AUTO-PICK IS DEAD here. The old `enqueueClassic`
 * resolved and auto-picked — the auto-download engine's choice, or (on its
 * ShowPicker fallback) a BEST-EFFORT first-video grab. The v1.1.65 device
 * round: "it automatically selects one of the video streams and starts
 * downloading it automatically, which is not a good idea… how it gets
 * handled on the details page." The contract is now the details page's
 * OWN flow: [resolveForPicker] hands the FULL server hierarchy up (the page
 * shows the picker sheet) and [enqueuePicked] runs the video the USER
 * picked. Nothing enqueues without an explicit pick.
 */
class AndroidPlayerDownloadController(
    private val orchestrator: DownloadOrchestrator,
    private val contentRepository: ContentRepository,
) : PlayerDownloadController {

    companion object {
        private const val TAG = "Anikuta:PlayerDownload"
    }

    /**
     * The shared prelude — the content row + the SEpisode adapter + the
     * extension source + the CS-bridged guard (the details page's exact
     * chain, steps 1-4 of the old enqueueClassic).
     */
    private suspend fun prelude(
        mainId: String,
        episode: PlayerDownloadController.EpisodeInfo,
    ): Prelude? {
        if (mainId.isBlank()) return null

        // 1. The content identity (the details page's exact builder).
        val content = contentRepository.getMainEntryByMainId(mainId) ?: return null

        // 2. The episode identity (the SEpisode adapter the engine
        //    expects, from the controller's neutral tuple).
        val sEpisode = SEpisode.create().apply {
            url = episode.episodeKey
            name = episode.name ?: ""
            episode_number = episode.episodeNumber
        }

        // 3. The extension source lookup (MainActivity's Koin path).
        val source = content.sourceId?.let {
            GlobalContext.get().get<ExtensionManager>().getSource(it) as?
                eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
        }

        // 4. The CS-bridged guard — the classic resolver path cannot
        //    serve bridged sources (defense in depth; the player's CS
        //    stack routes through its own flow and never calls this).
        if (source != null && source.isCloudStreamBridged) {
            Logger.w(TAG) { "prelude — CS-bridged source ${source.name}" }
            return Prelude.Bridged
        }

        if (source == null) {
            Logger.w(TAG) { "prelude — no extension source for mainId=$mainId" }
            return null
        }

        return Prelude.Ready(content, sEpisode, source)
    }

    /** [prelude]'s result — the null + the bridged + the ready triple. */
    private sealed interface Prelude {
        /** No content row or no extension source — nothing to serve. */
        data object Bridged : Prelude

        data class Ready(
            val content: com.confused.anikuta.core.content.ContentRecord,
            val sEpisode: SEpisode,
            val source: eu.kanade.tachiyomi.animesource.online.AnimeHttpSource,
        ) : Prelude
    }

    override suspend fun resolveForPicker(
        mainId: String,
        episode: PlayerDownloadController.EpisodeInfo,
    ): PlayerDownloadController.Outcome {
        return try {
            when (val p = prelude(mainId, episode)) {
                null -> PlayerDownloadController.Outcome.NoSource
                Prelude.Bridged -> PlayerDownloadController.Outcome.CsBridged
                is Prelude.Ready -> {
                    // ROUND 109 (D-721): resolve ONLY — the full server
                    // hierarchy rides up to the page's picker sheet; NO
                    // download starts here (the user picks).
                    val servers = orchestrator.resolveServers(p.source, p.sEpisode)
                    if (servers.isEmpty()) {
                        Logger.w(TAG) { "resolveForPicker — no servers resolved" }
                        PlayerDownloadController.Outcome.Error("No video sources available")
                    } else {
                        Logger.i(TAG) { "resolveForPicker — ${servers.size} server(s) for the picker" }
                        PlayerDownloadController.Outcome.PickerReady(servers)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "resolveForPicker — exception" }
            PlayerDownloadController.Outcome.Error(e.message ?: "Resolve failed")
        }
    }

    override suspend fun enqueuePicked(
        mainId: String,
        episode: PlayerDownloadController.EpisodeInfo,
        video: com.confused.anikuta.core.videoresolver.ResolverVideo,
        serverName: String,
        audioLabel: String,
    ): PlayerDownloadController.Outcome {
        return try {
            when (val p = prelude(mainId, episode)) {
                null -> PlayerDownloadController.Outcome.NoSource
                Prelude.Bridged -> PlayerDownloadController.Outcome.CsBridged
                is Prelude.Ready -> {
                    val contentInfo = buildContentInfo(p.content) {
                        contentRepository.getContentDetails(mainId)
                    } ?: return PlayerDownloadController.Outcome.NoSource

                    // The description fallback (the metadata cache — the
                    // same lookup handleDownloadEpisode makes; the tuple
                    // carries only the name).
                    val effectiveDescription = runCatching {
                        GlobalContext.get().get<DataCacheRepository>()
                            .getEpisodeMetadata(mainId)
                            .firstOrNull { it.episodeUrl == episode.episodeKey }
                            ?.description
                    }.getOrNull()
                    val episodeInfo = DownloadEpisodeInfo(
                        episodeKey = episode.episodeKey,
                        episodeNumber = episode.episodeNumber,
                        // DownloadEpisodeInfo.name is non-null; the
                        // controller's neutral tuple carries String? — the
                        // empty fallback mirrors MainActivity's
                        // `episode.name ?: ""`.
                        name = episode.name ?: "",
                        description = effectiveDescription,
                    )

                    // The USER'S pick — enqueueSpecific (the details page's
                    // handleDownloadSpecificVideo chain, verbatim).
                    when (val result = orchestrator.enqueueSpecific(
                        source = p.source,
                        episode = p.sEpisode,
                        content = contentInfo,
                        episodeInfo = episodeInfo,
                        video = video,
                        serverName = serverName,
                        audioLabel = audioLabel,
                    )) {
                        is EnqueueResult.Success -> {
                            Logger.i(TAG) {
                                "enqueuePicked — enqueued taskId=${result.taskId} " +
                                    "(server=$serverName, audio=$audioLabel)"
                            }
                            PlayerDownloadController.Outcome.Started(result.taskId)
                        }
                        // enqueueSpecific never returns ShowPicker (a manual
                        // pick is already specific) — the honest branch.
                        is EnqueueResult.ShowPicker ->
                            PlayerDownloadController.Outcome.Error("No downloadable source was found")
                        is EnqueueResult.NoSources -> PlayerDownloadController.Outcome.NoSource
                        is EnqueueResult.Error ->
                            PlayerDownloadController.Outcome.Error(result.message)
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logger.e(TAG, e) { "enqueuePicked — exception" }
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
