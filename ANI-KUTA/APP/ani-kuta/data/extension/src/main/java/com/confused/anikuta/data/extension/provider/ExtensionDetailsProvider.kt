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
        year: Int?,
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
                    seasonYear = year,
                )
            }

        val sAnime = SAnime.create().apply {
            this.url = animeUrl
            this.title = title
            this.thumbnail_url = thumbnailUrl
            // Task 47 (device round 6): seed the search-time year — many
            // CloudStream providers set `year` on SEARCH responses but omit
            // it on load(); without a seed the details page rendered Score
            // but no Year. The source's own parse overwrites it when present
            // (see CloudstreamAnimeSourceBridge.applyOnto — load().year wins,
            // the seed only fills nulls).
            this.year = year
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

        // Task 47: same discipline for the seeded year — a source whose details
        // parse returns a fresh SAnime (or omits year) keeps the search-time
        // seed instead of dropping it.
        if (enrichedAnime.year == null && sAnime.year != null) {
            enrichedAnime.year = sAnime.year
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
