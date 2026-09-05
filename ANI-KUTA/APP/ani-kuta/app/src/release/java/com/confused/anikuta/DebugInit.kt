package com.confused.anikuta

import app.cash.sqldelight.db.SqlDriver
import okhttp3.OkHttpClient
import org.koin.core.module.Module

/**
 * Release counterpart to `:app/src/debug/DebugInit.kt` (Phase DB).
 *
 * Same signatures, minimal behavior. Lets `:app/src/main` call `debugKoinModules()`
 * + `wrapDebugOkHttp()` + `wrapDebugSqlDriver()`
 * unconditionally — in debug builds the debug source set's version does the
 * real work; in release builds these are no-ops.
 *
 * D-429 (round 37 — the latent assembleRelease-break fix): this file
 * PREVIOUSLY registered a `DebugBuildInfo` singleton, importing it from
 * `:feature:debug-bubble` — which is a `debugImplementation`-only dependency,
 * so the RELEASE compile of this file could never resolve that import. It
 * was never caught because main's CI only ever ran `assembleDebug` (the
 * release line hit it in round 33 and removed the bubble entirely — D-409;
 * main KEEPS the bubble per the user's round-37 instruction, so the fix here
 * is the honest one: release registers NOTHING. `DebugBuildInfo` is only
 * consumed by the bubble's App Info tab, which only exists in debug builds
 * where the debug source set's real `debugKoinModules()` registers it.)
 *
 * Task 64 (round 24): `initDebugIntegrations()` is gone from BOTH source sets
 * with the console-logging family (the Logger appender wiring it existed for).
 */

/** Release builds register no debug modules at all. */
fun debugKoinModules(): List<Module> = emptyList()

/** No-op in release builds — returns the client unchanged. */
fun wrapDebugOkHttp(client: OkHttpClient): OkHttpClient = client

/** No-op in release builds — returns the driver unchanged. */
fun wrapDebugSqlDriver(driver: SqlDriver): SqlDriver = driver

/** Human-readable label for the release build. */
const val DEBUG_BUILD_LABEL = "release"
