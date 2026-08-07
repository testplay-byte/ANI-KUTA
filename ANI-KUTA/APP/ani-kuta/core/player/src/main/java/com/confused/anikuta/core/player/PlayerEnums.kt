package com.confused.anikuta.core.player

/**
 * Player display mode.
 *
 * ADR-025: single MPV instance. Fullscreen is an overlay swap, not navigation.
 * The player keeps playing when "minimized".
 */
enum class PlayerMode {
    /** Minimized (embedded in the watch screen). */
    MINIMIZED,

    /** Fullscreen (overlay covers the whole screen). */
    FULLSCREEN,
}

/**
 * Player loading state.
 */
enum class PlayerLoadingState {
    READY,
    LOADING,
    ERROR,
}

/**
 * Track type for audio/subtitle selection.
 */
enum class TrackType {
    AUDIO,
    SUBTITLE,
    VIDEO,
}

/**
 * A track (audio or subtitle) from MPV.
 */
data class VideoTrack(
    val id: Int,
    val name: String,
    val lang: String?,
)
