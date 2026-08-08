package com.confused.anikuta.feature.debugbubble

/**
 * Build info for the App Info tab (Phase DB-6).
 *
 * Carries BuildConfig values from `:app` into `:feature:debug-bubble` (which
 * can't reference `:app`'s BuildConfig directly). Populated in
 * `:app/src/debug/DebugInit.kt` (debug) + `:app/src/release/DebugInit.kt`
 * (release) — both source sets have access to `BuildConfig`.
 *
 * @property buildType "debug" or "release".
 * @property versionName The version name from BuildConfig.
 * @property versionCode The version code from BuildConfig.
 */
data class DebugBuildInfo(
    val buildType: String,
    val versionName: String,
    val versionCode: String,
)
