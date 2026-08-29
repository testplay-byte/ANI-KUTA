package com.confused.anikuta.feature.cloudstreamcontent

import com.confused.anikuta.core.navigation.NavKey
import kotlinx.serialization.Serializable

/**
 * NavKey for the CloudStream CONTENT details screen (session 3, provider
 * execution phase 1 — doc 23 §7 scope).
 *
 * Reached by tapping a CloudStream result in the Search page's results grid.
 * The screen resolves the live provider by [providerName] (a MainAPI.name —
 * only trusted plugins' providers are ever loaded), calls its load(url) and
 * renders everything: poster/banner, description, year, score, status,
 * duration, tags, and the season-grouped episode list (or the single movie
 * entry). Playback (loadLinks) is deliberately the NEXT session — episodes
 * render with an explicit "playback arrives next" note instead of dead taps.
 *
 * [contentUrl] is the provider-relative URL from the SearchResponse.
 */
@Serializable
data class CloudstreamContentDetailsKey(
    val providerName: String,
    val contentUrl: String,
    val title: String,
    val thumbnailUrl: String? = null,
) : NavKey
