package com.confused.anikuta

import app.cash.sqldelight.db.SqlDriver
import com.confused.anikuta.feature.debugbubble.DebugBuildInfo
import okhttp3.OkHttpClient
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Release counterpart to `:app/src/debug/DebugInit.kt` (Phase DB).
 *
 * Same signature, minimal behavior. Lets `:app/src/main` call `debugKoinModules()`
 * + `wrapDebugOkHttp()` + `wrapDebugSqlDriver()`
 * unconditionally — in debug builds the debug source set's version does the
 * real work; in release builds these are minimal/no-ops.
 *
 * Release builds still register a `DebugBuildInfo` (with "release" buildType) —
 * harmless, + the App Info tab is never shown in release (the bubble module
 * isn't on the classpath, so the tab isn't reachable).
 *
 * Task 64 (round 24): `initDebugIntegrations()` is gone from BOTH source sets
 * with the console-logging family (the Logger appender wiring it existed for).
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

/** No-op in release builds — returns the client unchanged. */
fun wrapDebugOkHttp(client: OkHttpClient): OkHttpClient = client

/** No-op in release builds — returns the driver unchanged. */
fun wrapDebugSqlDriver(driver: SqlDriver): SqlDriver = driver

/** Human-readable label for the release build. */
const val DEBUG_BUILD_LABEL = "release"
