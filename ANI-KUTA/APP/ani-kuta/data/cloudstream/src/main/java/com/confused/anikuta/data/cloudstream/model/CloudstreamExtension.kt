package com.confused.anikuta.data.cloudstream.model

import com.lagradost.cloudstream3.plugins.SitePlugin

/**
 * The UI-facing state model for a CloudStream extension (mirrors the aniyomi
 * AnimeExtension sealed shape so the unified Extensions screen can render both
 * with the same section patterns — doc 23 §5.4).
 *
 * - [Installed] — a .cs3 on disk with a persisted record; carries the number of
 *   MainAPI providers the plugin registered when loaded (0 = not currently loaded,
 *   e.g. disabled) and the repo it came from.
 * - [Available] — a plugins.json entry from a saved repo, not installed.
 * - [Errored] — installed but failed to load; carries the real reason
 *   (D-295/D-296 pattern — never silent).
 */
sealed class CloudstreamExtension {

    data class Installed(
        val internalName: String,
        val name: String,
        val version: Int,
        val filePath: String,
        val repoUrl: String?,
        val repoName: String?,
        val isEnabled: Boolean,
        val providerCount: Int,
        val isDisabledByRepo: Boolean = false,
        val availableUpdateVersion: Int? = null,
    ) : CloudstreamExtension()

    data class Available(
        val plugin: SitePlugin,
        val repoUrl: String,
        val repoName: String,
    ) : CloudstreamExtension() {
        val isNsfw: Boolean get() = plugin.tvTypes?.any { it.equals("NSFW", ignoreCase = true) } == true
    }

    data class Errored(
        val internalName: String,
        val name: String,
        val version: Int,
        val filePath: String,
        val message: String,
    ) : CloudstreamExtension()
}
