package com.confused.anikuta

import com.confused.anikuta.core.common.Logger
import com.confused.anikuta.feature.debugbubble.data.DebugLogBuffer
import com.confused.anikuta.feature.debugbubble.di.debugBubbleModule
import org.koin.core.module.Module

/**
 * Debug-only wiring (Phase DB). Lives in `:app/src/debug` — NOT compiled into
 * release builds. Called from [AnikutaApp.onCreate] guarded by
 * `if (BuildConfig.DEBUG)`.
 *
 * For DB-4: registers the debug-bubble Koin module + wires the Logger appender
 * to the [DebugLogBuffer] (10,000-entry ring buffer). Future phases will also
 * wire the OkHttp interceptor here.
 */

/** Koin modules to register in debug builds. */
fun debugKoinModules(): List<Module> = listOf(debugBubbleModule)

/**
 * Wire debug-only integrations that need Koin to be started first.
 * Called AFTER `startKoin { ... modules(debugKoinModules()) }`.
 *
 * DB-4: wires Logger.setAppender(DebugLogBuffer) so every log call (v/d/i/w/e)
 * appends to the in-memory ring buffer (visible in the Console tab).
 */
fun initDebugIntegrations() {
    val buffer = org.koin.core.context.GlobalContext.get().get<DebugLogBuffer>()
    Logger.setAppender(buffer)
}

/** Human-readable label for the debug build (shown in Settings, etc.). */
const val DEBUG_BUILD_LABEL = "debug"
