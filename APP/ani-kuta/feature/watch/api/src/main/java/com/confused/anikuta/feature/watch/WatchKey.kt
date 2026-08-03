package com.confused.anikuta.feature.watch

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * Nav3 key for the Watch screen. Carries the video URL + metadata needed
 * to play the video.
 *
 * Phase 5c: This replaces the temporary WatchKey from :feature:anime-details:api.
 */
@Serializable
data class WatchKey(
    val videoUrl: String,
    val animeTitle: String,
    val quality: String,
    val episodeUrl: String = "",
    val episodeNumber: Float = 0f,
    val episodeTitle: String = "",
) : NavKey
