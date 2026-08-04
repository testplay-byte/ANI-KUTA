package com.confused.anikuta.core.player

import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * Koin module for the player.
 *
 * NOTE: [PlayerStateHolder] is NOT registered here — it's a plain class owned
 * by the screen-level composable via `remember { PlayerStateHolder() }` (per
 * ADR-025 single-MPV-instance pattern). Only one WatchScreen exists at a time,
 * so it needs its own holder instance, not a shared Koin singleton. The old
 * project never registered it in Koin either.
 */
val playerModule = module {
    singleOf(::PlaybackStateStore)
}
