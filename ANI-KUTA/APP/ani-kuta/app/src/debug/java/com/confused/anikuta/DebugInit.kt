package com.confused.anikuta

import com.confused.anikuta.feature.debugbubble.di.debugBubbleModule
import org.koin.core.module.Module

/**
 * Debug-only wiring (Phase DB). Lives in `:app/src/debug` — NOT compiled into
 * release builds. Called from [AnikutaApp.onCreate] guarded by
 * `if (BuildConfig.DEBUG)`.
 *
 * This is the indirection that lets `:app/src/main` reference debug-only code
 * without a compile-time dependency on `:feature:debug-bubble` (which is
 * `debugImplementation`). The `main` source set calls [debugKoinModules] via
 * an `expect fun` (or a plain top-level `fun` in the debug source set with the
 * same signature in a `release` source set returning `emptyList()`).
 *
 * For DB-1: returns the [debugBubbleModule]. Future phases will also wire the
 * Logger appender + OkHttp interceptor here.
 */

/** Koin modules to register in debug builds. */
fun debugKoinModules(): List<Module> = listOf(debugBubbleModule)

/** Human-readable label for the debug build (shown in Settings, etc.). */
const val DEBUG_BUILD_LABEL = "debug"
