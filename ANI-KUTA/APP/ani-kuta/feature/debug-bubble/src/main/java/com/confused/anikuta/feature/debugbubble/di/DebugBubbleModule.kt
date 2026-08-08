package com.confused.anikuta.feature.debugbubble.di

import com.confused.anikuta.feature.debugbubble.DebugBubblePreferences
import com.confused.anikuta.feature.debugbubble.data.DebugDatabaseBrowser
import com.confused.anikuta.feature.debugbubble.data.DebugLogBuffer
import com.confused.anikuta.feature.debugbubble.data.DebugNetworkStats
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
    single { DebugLogBuffer() }  // 10,000-entry ring buffer (default capacity)
    single { DebugNetworkStats() }  // OkHttp interceptor (wired via wrapDebugOkHttp)
}
