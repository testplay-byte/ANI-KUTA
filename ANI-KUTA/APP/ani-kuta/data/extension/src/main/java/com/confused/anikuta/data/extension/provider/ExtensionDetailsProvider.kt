package com.confused.anikuta.data.extension.provider

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.core.common.model.AnimeDetailsProvider
import com.confused.anikuta.core.common.model.DataSourcePriority
import com.confused.anikuta.core.common.model.EntryMode
import com.confused.anikuta.core.common.model.UnifiedAnime
import com.confused.anikuta.data.extension.manager.ExtensionManager
import eu.kanade.tachiyomi.animesource.AnimeCatalogueSource
import eu.kanade.tachiyomi.animesource.model.SAnime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ExtensionDetailsProvider(
    private val extensionManager: ExtensionManager,
) : AnimeDetailsProvider {

    override val id = "extension"
    override val name = "Extension"

    override suspend fun fetchFromAniList(anilistId: Int): UnifiedAnime? = null

    override suspend fun fetchFromExtension(
        sourceId: Long,
        animeUrl: String,
        title: String,
        thumbnailUrl: String?,
    ): UnifiedAnime? = withContext(Dispatchers.IO) {
        val source = extensionManager.getSource(sourceId) as? AnimeCatalogueSource
            ?: run {
                Logger.w("Extension:Provider") { "Source not found for sourceId=$sourceId" }
                return@withContext UnifiedAnime(
                    title = title,
                    coverUrl = thumbnailUrl,
                    sourceId = sourceId,
                    sourceName = null,
                    animeUrl = animeUrl,
                    entryMode = EntryMode.EXTENSION,
                )
            }

        val sAnime = SAnime.create().apply {
            this.url = animeUrl
            this.title = title
            this.thumbnail_url = thumbnailUrl
            this.initialized = false
        }

        val enrichedAnime = try {
            Logger.i("Extension:Provider") { "Fetching details from ${source.name} for $animeUrl" }
            source.getAnimeDetails(sAnime)
        } catch (e: Throwable) {
            Logger.w("Extension:Provider") { "getAnimeDetails crashed: ${e::class.java.simpleName}: ${e.message}" }
            sAnime
        }

        // D-199: Many extensions only populate thumbnail_url in searchAnimeParse,
        // NOT in animeDetailsParse. The enriched SAnime may have thumbnail_url=null
        // even though the search result had it. Fall back to the stub's thumbnail.
        if (enrichedAnime.thumbnail_url.isNullOrBlank() && !sAnime.thumbnail_url.isNullOrBlank()) {
            enrichedAnime.thumbnail_url = sAnime.thumbnail_url
        }

        enrichedAnime.toUnifiedAnime(sourceId, source.name)
    }

    override suspend fun mergeInto(
        base: UnifiedAnime,
        priority: DataSourcePriority,
    ): UnifiedAnime = base
}

fun SAnime.toUnifiedAnime(sourceId: Long, sourceName: String): UnifiedAnime {
    val statusString = when (status) {
        1 -> "RELEASING"
        2 -> "FINISHED"
        3 -> "LICENSED"
        4 -> "CANCELLED"
        else -> null
    }
    val genreList = genre?.split(", ")?.filter { it.isNotBlank() } ?: emptyList()

    // Task 46 (device round 5): the optional SAnime enrichment channel — the
    // CloudStream bridge is the (currently only) source that populates
    // year/score, so CS details pages now render the Year + Score rows.
    // averageScore is the 0..100 scale the details screen displays ("★ N%",
    // "Score N / 100"); SAnime.score is the 0..10 scale.
    val averageScore = score?.let { kotlin.math.round(it * 10).toInt() }?.takeIf { it in 0..100 }

    return UnifiedAnime(
        title = title,
        coverUrl = thumbnail_url,
        description = description,
        genres = genreList,
        status = statusString,
        author = author,
        artist = artist,
        sourceId = sourceId,
        sourceName = sourceName,
        animeUrl = url,
        entryMode = EntryMode.EXTENSION,
        seasonYear = year,
        averageScore = averageScore,
    )
}
