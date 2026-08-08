package com.confused.anikuta.feature.debugbubble.di

import com.confused.anikuta.feature.debugbubble.DebugBubblePreferences
import com.confused.anikuta.feature.debugbubble.data.DebugDatabaseBrowser
import org.koin.dsl.module

/**
 * Koin module for :feature:debug-bubble (Phase DB).
 *
 * Registered in :app/src/debug/DebugInit.kt (debug source set only) — release
 * builds do not include this module.
 */
val debugBubbleModule = module {
    single { DebugBubblePreferences(get()) }
    single { DebugDatabaseBrowser(get()) }  // Context injected via Koin
}
