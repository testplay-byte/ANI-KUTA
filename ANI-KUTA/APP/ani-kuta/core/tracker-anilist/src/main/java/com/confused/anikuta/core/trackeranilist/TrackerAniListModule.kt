package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.trackerapi.Tracker
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val trackerAniListModule = module {
    // D-220: AniListTracker now needs httpClient + json + dispatchers for
    // authenticated GraphQL queries (Viewer + MediaListCollection).
    single { AniListTracker(get(), get(), get(), get()) }

    // D-242: TrackEntryRepository — local cache for track entries (one row per
    // mainId + trackerType). Used by the TrackSheet + details page badges.
    single { TrackEntryRepository(get()) }

    // ROUND 102 (WS-E — the tracking contract): the user's per-content
    // tracking INTENT (the opt-in flag gating every relay). Separate from the
    // track_entry cache by design — see TrackingStateRepository's KDoc.
    single { TrackingStateRepository(get()) }

    // Multi-binding: List<Tracker> for TrackSyncManager
    single<List<Tracker>>(named("trackers")) {
        listOf(
            get<AniListTracker>(),
        )
    }

    single { TrackSyncManager(get(named("trackers")), get(), get()) }
}
