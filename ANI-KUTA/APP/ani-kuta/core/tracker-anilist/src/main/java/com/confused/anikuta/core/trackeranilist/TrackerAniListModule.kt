package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.trackerapi.Tracker
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

val trackerAniListModule = module {
    // D-220: AniListTracker now needs httpClient + json + dispatchers for
    // authenticated GraphQL queries (Viewer + MediaListCollection).
    single { AniListTracker(get(), get(), get(), get()) }

    // Multi-binding: List<Tracker> for TrackSyncManager
    single<List<Tracker>>(named("trackers")) {
        listOf(
            get<AniListTracker>(),
        )
    }

    single { TrackSyncManager(get(named("trackers"))) }
}
