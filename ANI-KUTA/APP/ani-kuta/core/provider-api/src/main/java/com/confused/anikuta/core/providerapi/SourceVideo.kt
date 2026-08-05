package com.confused.anikuta.core.providerapi

/**
 * A playable video from a source.
 *
 * @param url The playable video URL.
 * @param quality Quality label (e.g., "1080p", "720p", "Default").
 * @param videoUrl Direct video URL (nullable — some sources need resolution).
 */
data class SourceVideo(
    val url: String,
    val quality: String = "Default",
    val videoUrl: String? = null,
)
