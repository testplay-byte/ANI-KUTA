package com.confused.anikuta

import com.confused.anikuta.feature.debugbubble.DebugBuildInfo
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Release counterpart to `:app/src/debug/DebugInit.kt` (Phase DB).
 *
 * Same signature, minimal behavior. Lets `:app/src/main` call `debugKoinModules()`
 * + `initDebugIntegrations()` + `wrapDebugOkHttp()` unconditionally — in debug
 * builds the debug source set's version does the real work; in release builds
 * these are minimal/no-ops.
 *
 * Release builds still register a `DebugBuildInfo` (with "release" buildType) —
 * harmless, + the App Info tab is never shown in release (the bubble module
 * isn't on the classpath, so the tab isn't reachable).
 */

/** Release builds register only the build-info (harmless). */
fun debugKoinModules(): List<Module> = listOf(
    module {
        single {
            DebugBuildInfo(
                buildType = "release",
                versionName = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toString(),
            )
        }
    },
)

/** No-op in release builds. */
fun initDebugIntegrations() {
    // No-op — no debug-bubble module on the classpath.
}

/** No-op in release builds — returns the client unchanged. */
fun wrapDebugOkHttp(client: OkHttpClient): OkHttpClient = client

/** Human-readable label for the release build. */
const val DEBUG_BUILD_LABEL = "release"
