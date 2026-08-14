package com.confused.anikuta

import com.confused.anikuta.core.navigation.NavKey
import com.confused.anikuta.core.testapi.AppRouteRegistry
import com.confused.anikuta.feature.animebrowse.AnimeBrowseKey
import com.confused.anikuta.feature.animedetails.AnimeDetailsKey
import com.confused.anikuta.feature.animelibrary.AnimeLibraryKeyImpl
import com.confused.anikuta.feature.animehistory.HistoryKey
import com.confused.anikuta.feature.animesearch.AnimeSearchKey
import com.confused.anikuta.feature.download.DownloadedFilesKey
import com.confused.anikuta.feature.download.DownloadSettingsKey
import com.confused.anikuta.feature.download.DownloadsKey
import com.confused.anikuta.feature.extensionssettings.AutoLinkSettingsKey
import com.confused.anikuta.feature.extensionssettings.ExtensionDetailKey
import com.confused.anikuta.feature.extensionssettings.ExtensionRepoSettingsKey
import com.confused.anikuta.feature.extensionssettings.ExtensionsSettingsKey
import com.confused.anikuta.feature.extensionssettings.SourcePreferencesKey
import com.confused.anikuta.feature.updates.UpdatesKey

/**
 * Debug-only [AppRouteRegistry] impl (D-197 nav hook).
 *
 * Lives in `:app/src/debug` (same package `com.confused.anikuta` as MainActivity) so it can see
 * BOTH the feature-module NavKeys (via imports — `:app` depends on all `:feature:*:api` modules)
 * AND the in-app NavKeys declared at the top of MainActivity.kt (MoreKey, SettingsKey, etc. —
 * same package, no import needed).
 *
 * Route names are lowercase snake_case. Parameterized routes read args from the [args] map
 * (all values are strings — converted to Int/Long as needed). Missing args → null (unknown route).
 *
 * Registered in `:app/src/debug/DebugInit.kt` `debugKoinModules()` as a Koin singleton. The
 * test-controller resolves it via `koin.get<AppRouteRegistry>()` to fulfill `push_route` commands.
 *
 * NOT compiled into release builds (`:app/src/debug` source set only).
 */
class AppRouteRegistryImpl : AppRouteRegistry {

    override fun routeNames(): List<String> = listOf(
        "browse", "library", "search", "more", "profile", "settings",
        "notifications", "notifications_library",
        "updates_settings", "update_categories",
        "appearance", "appearance_general", "episode_settings", "player_settings",
        "downloads", "downloaded_files", "download_settings",
        "extensions_settings", "auto_link_settings", "extension_repo_settings",
        "extension_detail", "source_preferences",
        "history", "updates",
        "anime_details_anilist", "anime_details_extension",
    )

    override fun navKeyFor(route: String, args: Map<String, String>): NavKey? = when (route) {
        "browse" -> AnimeBrowseKey
        "library" -> AnimeLibraryKeyImpl
        "search" -> AnimeSearchKey
        "more" -> MoreKey
        "profile" -> ProfileKey
        "settings" -> SettingsKey
        "notifications" -> NotificationsKey
        "notifications_library" -> NotificationsLibraryKey
        "updates_settings" -> UpdatesSettingsKey
        "update_categories" -> UpdateCategoriesKey
        "appearance" -> AppearanceKey
        "appearance_general" -> AppearanceGeneralKey
        "episode_settings" -> EpisodeSettingsKey
        "player_settings" -> PlayerSettingsKey
        "downloads" -> DownloadsKey
        "downloaded_files" -> DownloadedFilesKey
        "download_settings" -> DownloadSettingsKey
        "extensions_settings" -> ExtensionsSettingsKey
        "auto_link_settings" -> AutoLinkSettingsKey
        "extension_repo_settings" -> ExtensionRepoSettingsKey
        "extension_detail" -> args["pkgName"]?.let { ExtensionDetailKey(it) }
        "source_preferences" -> args["sourceId"]?.toLongOrNull()?.let { SourcePreferencesKey(it) }
        "history" -> HistoryKey
        "updates" -> UpdatesKey
        "anime_details_anilist" -> args["animeId"]?.toIntOrNull()?.let {
            AnimeDetailsKey.AniList(animeId = it)
        }
        "anime_details_extension" -> {
            val sid = args["sourceId"]?.toLongOrNull() ?: return null
            val url = args["animeUrl"] ?: return null
            val title = args["title"] ?: return null
            AnimeDetailsKey.Extension(sourceId = sid, animeUrl = url, title = title)
        }
        else -> null
    }
}
