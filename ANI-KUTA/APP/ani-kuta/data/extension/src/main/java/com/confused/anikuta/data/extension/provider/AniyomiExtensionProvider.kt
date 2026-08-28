package com.confused.anikuta.data.extension.provider

import com.confused.anikuta.core.common.ContentType
import com.confused.anikuta.core.providerapi.Source
import com.confused.anikuta.core.providerapi.SourceContent
import com.confused.anikuta.core.providerapi.SourceContentDetails
import com.confused.anikuta.core.providerapi.SourceEpisode
import com.confused.anikuta.core.providerapi.SourceVideo
import com.confused.anikuta.core.providerapi.VideoExtensionProvider
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.AnimeSource
import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The Aniyomi-compatible implementation of [VideoExtensionProvider] (D-302).
 *
 * The app's official facade over the Aniyomi extension ecosystem — previously
 * `:core:provider-api` was scaffolded with zero implementations and every
 * consumer bound directly to the Aniyomi-specific [ExtensionManager].
 * Existing consumers keep working unchanged; NEW consumers depend on
 * [VideoExtensionProvider] so a second ecosystem can be added later without
 * touching feature code.
 *
 * Bridges the Aniyomi `AnimeSource` API into the app-owned provider models —
 * no third-party types cross this boundary.
 */
class AniyomiExtensionProvider(
    private val manager: ExtensionManager,
) : VideoExtensionProvider {

    override val ecosystemId: String = "aniyomi"
    override val displayName: String = "Aniyomi extensions"
    override val supportedContentTypes: Set<ContentType> = setOf(ContentType.VIDEO)

    // ── Content queries ────────────────────────────────────────────────────────

    override fun observeInstalledSources(): Flow<List<Source>> =
        manager.sources.map { registry ->
            registry.values.map { source ->
                source.toDescriptor()
            }.sortedBy { it.name.lowercase() }
        }

    override fun fetchContentList(source: Source, page: Int, query: String?): Flow<List<SourceContent>> = flow {
        // Browse/search live on AnimeCatalogueSource (not the base AnimeSource).
        val catalogue = manager.getSource(source.sourceId.toLongOrNull() ?: return@flow)
            as? AnimeCatalogueSource ?: return@flow
        val result = if (query.isNullOrBlank()) {
            catalogue.getPopularAnime(page)
        } else {
            catalogue.getSearchAnime(page, query, AnimeFilterList())
        }
        emit(result.animes.map { anime ->
            SourceContent(
                sourceKey = source.key,
                externalId = anime.url,
                title = anime.title,
                thumbnailUrl = anime.thumbnail_url,
                url = anime.url,
            )
        })
    }

    override fun fetchContentDetails(content: SourceContent): Flow<SourceContentDetails> = flow {
        val source = resolveSource(content.sourceKey) ?: return@flow
        val anime = SAnime.create().apply { url = content.externalId }
        val details = source.getAnimeDetails(anime)
        emit(
            SourceContentDetails(
                sourceKey = content.sourceKey,
                externalId = content.externalId,
                title = details.title,
                description = details.description,
                genres = details.genre?.split(", ")?.filter { it.isNotBlank() },
                status = details.status.toDisplayString(),
                thumbnailUrl = details.thumbnail_url,
                bannerUrl = details.background_url,
                author = details.author?.takeIf { it.isNotBlank() },
                artist = details.artist?.takeIf { it.isNotBlank() },
            )
        )
    }

    override fun fetchEpisodeList(content: SourceContent): Flow<List<SourceEpisode>> = flow {
        val source = resolveSource(content.sourceKey) ?: return@flow
        val anime = SAnime.create().apply { url = content.externalId }
        val episodes = source.getEpisodeList(anime)
        emit(
            episodes.map { episode ->
                SourceEpisode(
                    contentKey = content.contentKey,
                    externalId = episode.url,
                    number = episode.episode_number.toDouble(),
                    name = episode.name,
                    url = episode.url,
                    thumbnailUrl = episode.preview_url,
                    dateUpload = episode.date_upload.takeIf { it > 0 },
                )
            }
        )
    }

    override fun fetchVideoList(episode: SourceEpisode): Flow<List<SourceVideo>> = flow {
        // contentKey = "<ecosystemId>:<sourceId>:<externalId>" — the source key is
        // the first two segments (ecosystem ids contain no ':', source ids are numeric).
        val sourceKey = episode.contentKey.split(':').take(2).joinToString(":")
        val source = resolveSource(sourceKey) ?: return@flow
        val e = SEpisode.create().apply { url = episode.externalId }
        val videos = source.getVideoList(e)
        emit(
            videos.map { video ->
                SourceVideo(
                    url = video.videoUrl,
                    quality = video.videoTitle,
                    videoUrl = video.videoUrl.takeIf { it.isNotBlank() },
                )
            }
        )
    }

    // ── Lifecycle management (D-302) ───────────────────────────────────────────

    override fun install(pkgName: String) {
        // Resolve the newest Available entry for the package and install it.
        val available = manager.availableExtensions.value.find { it.pkgName == pkgName }
        if (available == null) {
            // Not in a configured repo — trigger an update check; the install can
            // be re-attempted once the repo index is fresh.
            manager.checkForUpdates()
            return
        }
        installScope.launch {
            manager.installExtension(available).collect { }
        }
    }

    override fun uninstall(pkgName: String) {
        // The manager's uninstall takes an AnimeExtension instance — resolve it
        // from the installed list; fall back to the system uninstall by pkgName.
        val installed = manager.installedExtensions.value.find { it.pkgName == pkgName }
        if (installed != null) {
            manager.uninstallExtension(installed)
        } else {
            manager.installer.uninstallApk(pkgName)
        }
    }

    override fun setEnabled(pkgName: String, enabled: Boolean) {
        if (enabled) manager.enableExtension(pkgName) else manager.disableExtension(pkgName)
    }

    override fun checkForUpdates() {
        manager.checkForUpdates(force = true)
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private val installScope = CoroutineScope(Dispatchers.Default)

    private fun AnimeSource.toDescriptor(): Source = Source(
        ecosystemId = this@AniyomiExtensionProvider.ecosystemId,
        sourceId = id.toString(),
        name = name,
        lang = lang.ifBlank { "all" },
    )

    /** Resolve a provider [Source] from its "<ecosystemId>:<sourceId>" key. */
    private fun resolveSource(sourceKey: String): AnimeSource? {
        val id = sourceKey.substringAfterLast(':', "").toLongOrNull() ?: return null
        return manager.getSource(id)
    }
}

private fun Int.toDisplayString(): String = when (this) {
    SAnime.UNKNOWN -> "Unknown"
    SAnime.ONGOING -> "Ongoing"
    SAnime.COMPLETED -> "Completed"
    SAnime.LICENSED -> "Licensed"
    SAnime.PUBLISHING_FINISHED -> "Publishing finished"
    SAnime.CANCELLED -> "Cancelled"
    SAnime.ON_HIATUS -> "On hiatus"
    else -> "Unknown"
}
