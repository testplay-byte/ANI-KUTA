package com.confused.anikuta.core.schedule.di

import com.confused.anikuta.core.schedule.ScheduleEngine
import com.confused.anikuta.core.schedule.ScheduleStore
import org.koin.dsl.module

val scheduleModule = module {
    single { ScheduleStore(get()) }
    single { ScheduleEngine(get(), get(), get(), get(), getOrNull(), get()) }
}
