package com.confused.anikuta.core.trackerapi

/**
 * Supported tracker types.
 *
 * The app's internal tracking system (`:core:activity-tracker`) is PRIMARY —
 * it records everything the user does. External trackers (AniList, MAL) are
 * SECONDARY — they receive data relayed from the internal tracker.
 *
 * Adding a new tracker = implement the [Tracker] interface + register in Koin.
 */
enum class TrackerType(val id: String) {
    ANILIST("anilist"),
    MAL("mal"),           // future
    SHIKIMORI("shikimori"), // future
}

/**
 * Watch status for tracker sync.
 * Maps to AniList's status enum + MAL's status enum.
 */
enum class TrackStatus(val value: String) {
    WATCHING("WATCHING"),
    COMPLETED("COMPLETED"),
    PAUSED("PAUSED"),
    DROPPED("DROPPED"),
    PLAN_TO_WATCH("PLAN_TO_WATCH"),
    REWATCHING("REWATCHING"),
}

/**
 * A track entry — the data synced to/from an external tracker.
 *
 * @param contentKey The app's temporary content key.
 * @param trackerId The tracker's ID for this content (e.g., AniList anime ID).
 * @param status Watch status.
 * @param score User's score (0-100, or null if not rated).
 * @param progress Episodes watched.
 * @param totalEpisodes Total episodes (from tracker, nullable).
 * @param updatedAt When this was last synced (epoch millis).
 */
data class TrackEntry(
    val contentKey: String,
    val trackerId: Int,
    val status: TrackStatus = TrackStatus.WATCHING,
    val score: Int? = null,
    val progress: Int = 0,
    val totalEpisodes: Int? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)

/**
 * Login state for a tracker.
 */
sealed interface TrackerLoginState {
    data object LoggedOut : TrackerLoginState
    data class LoggedIn(val username: String) : TrackerLoginState
    data class Error(val message: String) : TrackerLoginState
}

/**
 * Sync state for a tracker.
 */
sealed interface TrackerSyncState {
    data object Idle : TrackerSyncState
    data object Syncing : TrackerSyncState
    data class Success(val syncedAt: Long) : TrackerSyncState
    data class Failed(val message: String) : TrackerSyncState
}
