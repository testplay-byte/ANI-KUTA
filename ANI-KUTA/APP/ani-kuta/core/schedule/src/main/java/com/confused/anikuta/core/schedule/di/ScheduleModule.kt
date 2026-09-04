package com.confused.anikuta.core.schedule.di

import com.confused.anikuta.core.schedule.ScheduleEngine
import com.confused.anikuta.core.schedule.ScheduleStore
import org.koin.dsl.module

val scheduleModule = module {
    single { ScheduleStore(get()) }
    // D-391 (round 26): the 7th arg — the SmartReleaseScheduler seam. The
    // engine (re-)aims the smart-release one-shots every time it discovers
    // fresh airing times, so the next check lands at the next ACTUAL release.
    single { ScheduleEngine(get(), get(), get(), get(), getOrNull(), get(), getOrNull()) }
}
