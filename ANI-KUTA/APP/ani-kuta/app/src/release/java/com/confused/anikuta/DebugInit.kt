package com.confused.anikuta

import okhttp3.OkHttpClient
import org.koin.core.module.Module

/**
 * Release counterpart to `:app/src/debug/DebugInit.kt` (Phase DB).
 *
 * Same signature, no-op behavior. Lets `:app/src/main` call `debugKoinModules()`
 * + `initDebugIntegrations()` + `wrapDebugOkHttp()` unconditionally — in debug
 * builds the debug source set's version does the real work; in release builds
 * these are no-ops.
 */

/** No-op in release builds. */
fun debugKoinModules(): List<Module> = emptyList()

/** No-op in release builds. */
fun initDebugIntegrations() {
    // No-op — no debug-bubble module on the classpath.
}

/** No-op in release builds — returns the client unchanged. */
fun wrapDebugOkHttp(client: OkHttpClient): OkHttpClient = client

/** Human-readable label for the release build. */
const val DEBUG_BUILD_LABEL = "release"
