package com.confused.anikuta

import org.koin.core.module.Module

/**
 * Release counterpart to `:app/src/debug/DebugInit.kt` (Phase DB).
 *
 * Same signature, no-op behavior: returns an empty module list. This lets
 * `:app/src/main` call `debugKoinModules()` unconditionally — in debug builds
 * the debug source set's version returns the real modules; in release builds
 * this version returns empty (no debug-bubble module is on the classpath).
 *
 * The function is NOT an `expect fun` (which would require a common source
 * set); instead, the debug + release source sets each provide their own
 * definition with the same signature. Gradle's source-set variant selection
 * picks the right one per build type.
 */

/** No-op in release builds. */
fun debugKoinModules(): List<Module> = emptyList()

/** Human-readable label for the release build. */
const val DEBUG_BUILD_LABEL = "release"
