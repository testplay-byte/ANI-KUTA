package com.confused.anikuta.feature.debugbubble.di

import com.confused.anikuta.feature.debugbubble.DebugBubblePreferences
import org.koin.dsl.module

/**
 * Koin module for :feature:debug-bubble (Phase DB).
 *
 * Provides [DebugBubblePreferences]. Future phases will add DebugDatabaseBrowser,
 * DebugLogBuffer, DebugNetworkStats here.
 *
 * Registered in :app/src/debug/DebugInit.kt (debug source set only) — release
 * builds do not include this module.
 */
val debugBubbleModule = module {
    single { DebugBubblePreferences(get()) }
}
