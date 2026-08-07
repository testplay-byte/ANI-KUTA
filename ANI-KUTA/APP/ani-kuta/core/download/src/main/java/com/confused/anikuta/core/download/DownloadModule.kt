package com.confused.anikuta.core.download

/**
 * D.0.8: The stub [DownloadManager] has been deleted. This module is a PLACEHOLDER
 * until Phase D.1 replaces it with the full Koin module (engine + storage + queue + DI).
 *
 * No bindings are registered yet — the full download system is wired in D.1's
 * `di/DownloadModule.kt` (which replaces this file).
 *
 * This file exists so the `downloadModule` import in `AnikutaApp.kt` doesn't break
 * the build during the D.0 → D.1 transition.
 */
val downloadModule = org.koin.dsl.module {
    // Intentionally empty — bindings added in Phase D.1.
}
