package com.confused.anikuta.core.testapi

import com.confused.anikuta.core.navigation.NavKey

/**
 * Maps string route names to NavKey instances, so the test-controller can navigate by
 * name without a direct dependency on every feature module's NavKey class (which would
 * create a circular dep — `:core:test-controller` → `:feature:*:api` → `:core:*` is fine,
 * but the in-app NavKeys like `MoreKey`/`SettingsKey` live in `:app` and aren't visible to
 * `:core:test-controller` at all).
 *
 * Implemented in `:app/src/debug` (which can see ALL NavKeys — both the feature-module ones
 * via `:app`'s dependencies AND the in-app ones declared in `MainActivity.kt`). Registered
 * as a Koin singleton in `debugKoinModules()`. The test-controller fetches it via Koin.
 *
 * Supported routes (see `AppRouteRegistryImpl` in `:app/src/debug` for the full list):
 *   "browse" | "library" | "search" | "more" | "settings" | "profile" |
 *   "notifications" | "notifications_library" | "updates_settings" | "update_categories" |
 *   "appearance" | "appearance_general" | "episode_settings" | "player_settings" |
 *   "downloads" | "downloaded_files" | "download_settings" |
 *   "extensions_settings" | "auto_link_settings" | "extension_repo_settings" |
 *   "history" | "updates" | "anime_details" (args: animeId | sourceId+animeUrl) |
 *   "watch" (args: serialized WatchKey — advanced)
 *
 * D-197 nav hook.
 */
interface AppRouteRegistry {
    /** All supported route names (for the agent's `get_device_info` / help). */
    fun routeNames(): List<String>

    /**
     * Build a NavKey for [route], using [args] for parameterized routes.
     * @return null if the route is unknown or required args are missing.
     */
    fun navKeyFor(route: String, args: Map<String, String> = emptyMap()): NavKey?
}
