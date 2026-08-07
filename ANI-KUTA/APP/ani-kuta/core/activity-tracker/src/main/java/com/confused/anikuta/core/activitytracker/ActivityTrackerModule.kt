package com.confused.anikuta.core.activitytracker

import com.confused.anikuta.core.database.AnikutaDatabase
import org.koin.core.qualifier.named
import org.koin.dsl.module

val activityTrackerModule = module {
    single {
        val database = get<AnikutaDatabase>()
        val sessionId = get<String>(named("sessionId"))
        ActivityTracker(database, sessionId)
    }
}
