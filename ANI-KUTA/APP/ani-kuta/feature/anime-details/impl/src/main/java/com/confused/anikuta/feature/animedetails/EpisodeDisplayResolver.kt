package com.confused.anikuta.feature.animedetails

import com.confused.anikuta.core.common.EpisodeTitleParser
import com.confused.anikuta.core.metadata.EpisodeMetadata
import eu.kanade.tachiyomi.animesource.model.SEpisode

/**
 * D-306: Extension-first episode display resolution.
 *
 * Priority order for every episode field (user requirement):
 *
 *   1. **Extension-provided values** ([SEpisode.name] title, [SEpisode.summary]
 *      description, [SEpisode.preview_url] thumbnail) — the source knows its own
 *      content best.
 *   2. **App metadata providers** (AniZip / Jikan / Kitsu / AniList via
 *      [EpisodeMetadata]) — fill the gaps when the extension doesn't provide
 *      a value.
 *
 * "Smartly" means the extension's `name` only counts as a TITLE when it contains
 * a real title beyond the bare episode number — "Episode 5" is NOT a title, but
 * "Episode 5 - The Black Cat" is ([EpisodeTitleParser.parseTitle] decides).
 * A blank extension summary/thumbnail is treated as "not provided".
 *
 * This object is the SINGLE source of truth for the precedence rules — the
 * episode rows, the WatchKey metadata serialization, and the cache-write merges
 * all call it, so the priority can never drift between surfaces.
 */
object EpisodeDisplayResolver {

    /**
     * The extension-provided title for an episode, or null when the extension
     * didn't provide a real title (bare "Episode N", a hash/URL, or blank).
     */
    fun extensionTitle(episode: SEpisode): String? =
        EpisodeTitleParser.parseTitle(episode.name, episode.episode_number)

    /** Extension-provided description, or null when blank/not provided. */
    private fun extensionDescription(episode: SEpisode): String? =
        episode.summary?.takeIf { it.isNotBlank() }

    /** Extension-provided episode thumbnail, or null when blank/not provided. */
    private fun extensionThumbnail(episode: SEpisode): String? =
        episode.preview_url?.takeIf { it.isNotBlank() }

    /**
     * Display title: extension real title → provider title → raw name →
     * "Episode N" fallback.
     */
    fun title(episode: SEpisode, metadata: EpisodeMetadata?): String =
        extensionTitle(episode)
            ?: metadata?.title
            ?: episode.name.ifBlank {
                "Episode ${EpisodeTitleParser.formatEpisodeNumber(episode.episode_number)}"
            }

    /**
     * Display description: extension summary → provider description.
     * Null when neither provides one.
     */
    fun description(episode: SEpisode, metadata: EpisodeMetadata?): String? =
        extensionDescription(episode) ?: metadata?.description

    /**
     * Episode thumbnail: extension `preview_url` → provider thumbnail.
     * Null when neither provides one (the caller applies its own
     * cover/no-image fallback preference — that's a display concern, not a
     * data-priority concern).
     */
    fun thumbnailUrl(episode: SEpisode, metadata: EpisodeMetadata?): String? =
        extensionThumbnail(episode) ?: metadata?.thumbnailUrl
}
