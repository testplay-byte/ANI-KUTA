package com.confused.anikuta

import org.koin.core.module.Module

/**
 * Release counterpart to `:app/src/debug/DebugInit.kt` (Phase DB).
 *
 * Same signature, no-op behavior. Lets `:app/src/main` call `debugKoinModules()`
 * + `initDebugIntegrations()` unconditionally — in debug builds the debug source
 * set's version does the real work; in release builds this version is a no-op.
 */

/** No-op in release builds. */
fun debugKoinModules(): List<Module> = emptyList()

/** No-op in release builds. */
fun initDebugIntegrations() {
    // No-op — no debug-bubble module on the classpath.
}

/** Human-readable label for the release build. */
const val DEBUG_BUILD_LABEL = "release"
