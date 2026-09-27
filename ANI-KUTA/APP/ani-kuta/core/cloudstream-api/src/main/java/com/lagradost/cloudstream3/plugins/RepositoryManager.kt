// CLEAN-ROOM: declarations mirror the CloudStream 3 plugin API surface for binary
// compatibility (interop facts only). All implementations are original ANI-KUTA code.
// No CloudStream source code was copied. See DOCUMENTATION/cloudstream/23-*.md §3.
//
// ROUND 97 (D-663) — Ultima references the repository manager (getRepositories /
// getRepoPlugins) and PluginWrapper (getPlugin / getRepositoryData) for its
// in-provider repository browser. Inert anchor: the app's own repositories
// live in :data:cloudstream / :data:extension, out of the compat library's
// reach by design — these answer the empty shapes so the plugin's repo-driven
// extras quietly show nothing. Documented divergence, doc 79 §4.
package com.lagradost.cloudstream3.plugins

import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData

/** The inert repository-manager anchor (see the file header). */
object RepositoryManager {

    /** The saved repositories (inert: none). */
    val repositories: Array<RepositoryData> = emptyArray()

    /** The plugins one repository offers (inert: none). */
    suspend fun getRepoPlugins(repositoryData: RepositoryData): List<PluginWrapper>? = null
}

/** One repository + plugin pairing (the app-side plugin browser's row model). */
data class PluginWrapper(
    val repository: Repository,
    val repositoryData: RepositoryData,
    val plugin: SitePlugin,
)
