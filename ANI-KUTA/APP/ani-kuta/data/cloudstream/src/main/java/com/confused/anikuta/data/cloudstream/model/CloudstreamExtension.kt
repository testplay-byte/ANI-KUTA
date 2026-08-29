package com.confused.anikuta.data.cloudstream.model

import com.lagradost.cloudstream3.plugins.SitePlugin

/**
 * The UI-facing state model for a CloudStream extension (mirrors the aniyomi
 * AnimeExtension sealed shape so the unified Extensions screen renders both
 * with the same section patterns — doc 23 §5.4).
 *
 * - [Installed] — a .cs3 on disk with a persisted record. Carries the catalog
 *   display metadata (language / iconUrl / isNsfw) captured at install time so
 *   the row renders aniyomi-parity even after its repository is deleted, and
 *   the number of MainAPI providers the plugin registered when loaded (0 =
 *   nothing currently registered — the loader retry path).
 * - [Available] — a plugins.json entry from a saved repo, not installed.
 * - [Errored] — installed but failed to load; carries the real reason
 *   (D-295/D-296 pattern — never silent).
 *
 * Session 2: no enable/disable state — installed plugins are always loaded
 * (the device round killed the row toggle; per-plugin control returns with the
 * future plugin-detail page, doc 23 §7).
 */
sealed class CloudstreamExtension {

    data class Installed(
        val internalName: String,
        val name: String,
        val version: Int,
        val filePath: String,
        val repoUrl: String?,
        val language: String?,
        val iconUrl: String?,
        val isNsfw: Boolean,
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
        val language: String?,
        val iconUrl: String?,
        val isNsfw: Boolean,
        val message: String,
    ) : CloudstreamExtension()
}
