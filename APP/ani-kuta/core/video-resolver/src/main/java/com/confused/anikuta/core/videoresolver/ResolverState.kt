package com.confused.anikuta.core.videoresolver

/**
 * State of video resolution.
 */
sealed interface ResolverState {

    /** Initial state — resolution has not started. */
    data object Idle : ResolverState

    /** Resolution is in progress. */
    data class Loading(val message: String = "Resolving video...") : ResolverState

    /** Resolution succeeded — videos are available. */
    data class Success(val videos: List<ResolvedVideo>) : ResolverState

    /** Resolution failed. */
    data class Error(val message: String) : ResolverState
}

/**
 * A resolved video with quality information.
 */
data class ResolvedVideo(
    val url: String,
    val quality: String = "Default",
    val directUrl: String? = null,
)
