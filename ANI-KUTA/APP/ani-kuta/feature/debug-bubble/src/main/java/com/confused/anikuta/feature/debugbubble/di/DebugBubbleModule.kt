package com.confused.anikuta.feature.debugbubble.di

import com.confused.anikuta.feature.debugbubble.DebugBubblePreferences
import com.confused.anikuta.feature.debugbubble.data.DebugDatabaseBrowser
import com.confused.anikuta.feature.debugbubble.data.DebugDbStats
import com.confused.anikuta.feature.debugbubble.data.DebugNetworkStats
import org.koin.dsl.module

/**
 * Koin module for :feature:debug-bubble (Phase DB).
 *
 * Registered in :app/src/debug/DebugInit.kt (debug source set only) — release
 * builds do not include this module.
 *
 * Task 64 (round 24): the DebugLogBuffer provision is gone with the console
 * family — the Screen/Database/Network/App-info tabs are unaffected.
 */
val debugBubbleModule = module {
    single { DebugBubblePreferences(get()) }
    single { DebugDatabaseBrowser(get()) }  // Context injected via Koin
    single { DebugNetworkStats() }  // OkHttp interceptor (wired via wrapDebugOkHttp)
    single { DebugDbStats() }  // DB read+write tracker (wired via wrapDebugSqlDriver)
}
