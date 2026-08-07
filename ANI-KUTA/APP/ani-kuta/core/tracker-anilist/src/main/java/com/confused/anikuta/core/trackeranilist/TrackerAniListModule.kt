package com.confused.anikuta.core.trackeranilist

import com.confused.anikuta.core.trackerapi.Tracker
import org.koin.core.qualifier.named
import org.koin.dsl.module

val trackerAniListModule = module {
    single { AniListTracker(get()) }

    // Multi-binding: List<Tracker> for TrackSyncManager
    single<List<Tracker>>(named("trackers")) {
        listOf(
            get<AniListTracker>(),
        )
    }

    single { TrackSyncManager(get(named("trackers"))) }
}
