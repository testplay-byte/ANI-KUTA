package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.trackerapi.TrackStatus

/**
 * D-242: Maps between the app's canonical [TrackStatus] enum + AniList's
 * GraphQL `MediaListStatus` enum.
 *
 * AniList's enum values differ from our canonical names:
 *  - `CURRENT` (not WATCHING)
 *  - `PLANNING` (not PLAN_TO_WATCH)
 *  - `REPEATING` (not REWATCHING) — AniList's actual enum value.
 *  - `COMPLETED`, `PAUSED`, `DROPPED` match.
 *
 * This mapper is used at the AniList API boundary:
 *  - [toAniList] when pushing (syncEntry → SaveMediaListEntry mutation).
 *  - [fromAniList] when pulling (fetchEntry → MediaList query).
 */
object AniListStatusMapper {

    /** Converts the app's [TrackStatus] to AniList's GraphQL enum string. */
    fun toAniList(status: TrackStatus): String = when (status) {
        TrackStatus.WATCHING -> "CURRENT"
        TrackStatus.PLAN_TO_WATCH -> "PLANNING"
        TrackStatus.COMPLETED -> "COMPLETED"
        TrackStatus.PAUSED -> "PAUSED"
        TrackStatus.DROPPED -> "DROPPED"
        TrackStatus.REWATCHING -> "REPEATING"   // D-242-fix: AniList's actual enum is REPEATING, not REWATCHING
    }

    /**
     * Converts AniList's GraphQL enum string to the app's [TrackStatus].
     * Returns [TrackStatus.WATCHING] for unknown values (defensive — shouldn't
     * happen with valid AniList responses).
     */
    fun fromAniList(status: String?): TrackStatus = when (status) {
        "CURRENT" -> TrackStatus.WATCHING
        "PLANNING" -> TrackStatus.PLAN_TO_WATCH
        "COMPLETED" -> TrackStatus.COMPLETED
        "PAUSED" -> TrackStatus.PAUSED
        "DROPPED" -> TrackStatus.DROPPED
        "REPEATING" -> TrackStatus.REWATCHING   // D-242-fix: AniList's actual enum is REPEATING
        else -> TrackStatus.WATCHING
    }
}
