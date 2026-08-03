package com.confused.anikuta.feature.animedetails

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * Temporary WatchKey — carries the video URL + anime title + quality to a
 * placeholder watch screen.
 *
 * Phase 5c will replace this with a proper `:feature:watch` module + a
 * full WatchRequest data class (videoUrl, headers, episodeList, etc.).
 * For now, this is enough to navigate from Details → Watch.
 */
@Serializable
data class WatchKey(
    val videoUrl: String,
    val animeTitle: String,
    val quality: String,
) : NavKey
