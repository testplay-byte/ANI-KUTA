package com.confused.anikuta.core.player

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val playerModule = module {
    singleOf(::PlayerStateHolder)
    singleOf(::PlaybackStateStore)
}
